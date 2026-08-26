package com.mobile.novabox.ui.dialog;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Handler;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.documentfile.provider.DocumentFile;

import com.mobile.novabox.R;
import com.mobile.novabox.base.App;
import com.mobile.novabox.data.AppDataManager;
import com.hjq.permissions.OnPermissionCallback;
import com.hjq.permissions.Permission;
import com.hjq.permissions.XXPermissions;

import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * 数据备份与还原弹窗。
 *
 * 流程改为走系统自带的文件选择器（Storage Access Framework）：
 *  - 点击"立即备份"：弹出系统目录选择器（ACTION_OPEN_DOCUMENT_TREE），
 *    用户选好目录后，把数据库 + 配置打包成一个 zip 文件写入该目录。
 *  - 点击"立即恢复"：弹出系统文件选择器（ACTION_OPEN_DOCUMENT），
 *    用户选中之前生成的备份 zip 文件后，读取并恢复其中的数据。
 *
 * Dialog 本身无法直接收到 startActivityForResult 的回调，这里通过
 * BaseDialog.ActivityResultReceiver 接口，由宿主 Activity（MyActivity）
 * 在 onActivityResult 中转发过来。
 */
public class BackupDialog extends BaseDialog implements BaseDialog.ActivityResultReceiver {

    public static final int REQUEST_CODE_PICK_BACKUP_DIR = 9001;
    public static final int REQUEST_CODE_PICK_RESTORE_FILE = 9002;

    private static final String ZIP_ENTRY_SQLITE = "sqlite";
    private static final String ZIP_ENTRY_HAWK = "hawk";

    public BackupDialog(@NonNull @NotNull Context context) {
        super(context);
        setContentView(R.layout.dialog_backup);

        findViewById(R.id.backupNow).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                launchDirectoryPicker();
            }
        });
        findViewById(R.id.restoreNow).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                launchFilePicker();
            }
        });
        findViewById(R.id.storagePermission).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (XXPermissions.isGranted(getContext(), Permission.Group.STORAGE)) {
                    Toast.makeText(getContext(), "已获得存储权限", Toast.LENGTH_SHORT).show();
                } else {
                    XXPermissions.with(getContext())
                            .permission(Permission.Group.STORAGE)
                            .request(new OnPermissionCallback() {
                                @Override
                                public void onGranted(List<String> permissions, boolean all) {
                                    if (all) {
                                        Toast.makeText(getContext(), "已获得存储权限", Toast.LENGTH_SHORT).show();
                                    }
                                }

                                @Override
                                public void onDenied(List<String> permissions, boolean never) {
                                    if (never) {
                                        Toast.makeText(getContext(), "获取存储权限失败,请在系统设置中开启", Toast.LENGTH_SHORT).show();
                                        XXPermissions.startPermissionActivity((Activity) getContext(), permissions);
                                    } else {
                                        Toast.makeText(getContext(), "获取存储权限失败", Toast.LENGTH_SHORT).show();
                                    }
                                }
                            });
                }
            }
        });
        View btnClose = findViewById(R.id.btnBackupClose);
        if (btnClose != null) {
            btnClose.setOnClickListener(v -> dismiss());
        }
    }

    // ────────────────────────────────────────────────────────────────────
    // 系统选择器发起
    // ────────────────────────────────────────────────────────────────────

    /** 唤起系统目录选择器，用户选好目录后备份文件会写入该目录 */
    private void launchDirectoryPicker() {
        Activity activity = hostActivity();
        if (activity == null) return;
        try {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            activity.startActivityForResult(intent, REQUEST_CODE_PICK_BACKUP_DIR);
        } catch (Throwable e) {
            e.printStackTrace();
            Toast.makeText(getContext(), "无法打开系统目录选择器", Toast.LENGTH_SHORT).show();
        }
    }

    /** 唤起系统文件选择器，用户选中备份 zip 文件后开始恢复 */
    private void launchFilePicker() {
        Activity activity = hostActivity();
        if (activity == null) return;
        try {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            activity.startActivityForResult(intent, REQUEST_CODE_PICK_RESTORE_FILE);
        } catch (Throwable e) {
            e.printStackTrace();
            Toast.makeText(getContext(), "无法打开系统文件选择器", Toast.LENGTH_SHORT).show();
        }
    }

    private Activity hostActivity() {
        Context context = getContext();
        while (context instanceof android.content.ContextWrapper && !(context instanceof Activity)) {
            context = ((android.content.ContextWrapper) context).getBaseContext();
        }
        if (context instanceof Activity) {
            return (Activity) context;
        }
        Toast.makeText(getContext(), "当前环境不支持该操作", Toast.LENGTH_SHORT).show();
        return null;
    }

    // ────────────────────────────────────────────────────────────────────
    // 宿主 Activity 转发过来的选择结果
    // ────────────────────────────────────────────────────────────────────

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode != Activity.RESULT_OK || data == null || data.getData() == null) {
            return;
        }
        Uri uri = data.getData();
        if (requestCode == REQUEST_CODE_PICK_BACKUP_DIR) {
            // 尽量持久化目录的读写权限，避免下次访问时权限失效（非必需，失败也不影响本次备份）
            try {
                getContext().getContentResolver().takePersistableUriPermission(uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            } catch (Throwable ignore) {
            }
            backupToDirectory(uri);
        } else if (requestCode == REQUEST_CODE_PICK_RESTORE_FILE) {
            confirmRestore(uri.getLastPathSegment(), () -> restoreFromFile(uri));
        }
    }

    private interface RestoreAction {
        void run();
    }

    /** 恢复会整体覆盖当前数据，先弹二次确认，避免误触导致数据丢失 */
    private void confirmRestore(String name, RestoreAction action) {
        new AlertDialog.Builder(getContext())
                .setTitle("确认恢复")
                .setMessage("将使用所选备份文件覆盖当前数据，恢复后应用会自动重启，确定继续吗？")
                .setPositiveButton("确定恢复", (dialog, which) -> action.run())
                .setNegativeButton("取消", null)
                .show();
    }

    // ────────────────────────────────────────────────────────────────────
    // 备份：数据库 + 配置 → 临时文件 → 打包成 zip → 写入用户选择的目录
    // ────────────────────────────────────────────────────────────────────

    private void backupToDirectory(Uri treeUri) {
        File tmpSqlite = null;
        try {
            DocumentFile dir = DocumentFile.fromTreeUri(getContext(), treeUri);
            if (dir == null || !dir.canWrite()) {
                Toast.makeText(getContext(), "所选目录不可写，请重新选择", Toast.LENGTH_SHORT).show();
                return;
            }

            // 1. 数据库导出到 App 缓存目录的临时文件
            tmpSqlite = new File(getContext().getCacheDir(), "backup_tmp_sqlite");
            if (!AppDataManager.backup(tmpSqlite)) {
                Toast.makeText(getContext(), "DB文件不存在，备份失败!", Toast.LENGTH_SHORT).show();
                return;
            }

            // 2. 配置项（Hawk2 + crypto.KEY_256）序列化为 JSON
            byte[] hawkJson = collectHawkJson().toString().getBytes("UTF-8");

            // 3. 在用户选择的目录下创建 zip 文件
            String fileName = "NovaBox_backup_" + new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date()) + ".zip";
            DocumentFile zipFile = dir.createFile("application/zip", fileName);
            if (zipFile == null) {
                Toast.makeText(getContext(), "创建备份文件失败，请检查目录权限", Toast.LENGTH_SHORT).show();
                return;
            }

            OutputStream os = getContext().getContentResolver().openOutputStream(zipFile.getUri());
            if (os == null) {
                Toast.makeText(getContext(), "无法写入所选目录", Toast.LENGTH_SHORT).show();
                return;
            }
            ZipOutputStream zos = new ZipOutputStream(os);
            try {
                writeZipEntryFromFile(zos, ZIP_ENTRY_SQLITE, tmpSqlite);
                writeZipEntryFromBytes(zos, ZIP_ENTRY_HAWK, hawkJson);
            } finally {
                zos.close();
            }

            Toast.makeText(getContext(), "备份成功！已保存为 " + fileName, Toast.LENGTH_LONG).show();
        } catch (Throwable e) {
            e.printStackTrace();
            Toast.makeText(getContext(), "备份失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        } finally {
            if (tmpSqlite != null && tmpSqlite.exists()) {
                tmpSqlite.delete();
            }
        }
    }

    private JSONObject collectHawkJson() throws Exception {
        JSONObject jsonObject = new JSONObject();
        SharedPreferences sharedPreferences = App.getInstance().getSharedPreferences("Hawk2", Context.MODE_PRIVATE);
        for (String key : sharedPreferences.getAll().keySet()) {
            jsonObject.put(key, sharedPreferences.getString(key, ""));
        }
        sharedPreferences = App.getInstance().getSharedPreferences("crypto.KEY_256", Context.MODE_PRIVATE);
        for (String key : sharedPreferences.getAll().keySet()) {
            jsonObject.put(key, sharedPreferences.getString(key, ""));
        }
        return jsonObject;
    }

    private void writeZipEntryFromFile(ZipOutputStream zos, String entryName, File file) throws IOException {
        zos.putNextEntry(new ZipEntry(entryName));
        InputStream is = new java.io.FileInputStream(file);
        try {
            byte[] buffer = new byte[8192];
            int len;
            while ((len = is.read(buffer)) > 0) {
                zos.write(buffer, 0, len);
            }
        } finally {
            is.close();
            zos.closeEntry();
        }
    }

    private void writeZipEntryFromBytes(ZipOutputStream zos, String entryName, byte[] data) throws IOException {
        zos.putNextEntry(new ZipEntry(entryName));
        zos.write(data);
        zos.closeEntry();
    }

    // ────────────────────────────────────────────────────────────────────
    // 恢复：读取用户选中的 zip 文件 → 解压到临时文件 → 写回数据库 / 配置
    // ────────────────────────────────────────────────────────────────────

    private void restoreFromFile(Uri fileUri) {
        File tmpSqlite = null;
        try {
            ContentResolver resolver = getContext().getContentResolver();
            InputStream in = resolver.openInputStream(fileUri);
            if (in == null) {
                Toast.makeText(getContext(), "无法读取所选文件", Toast.LENGTH_SHORT).show();
                return;
            }

            tmpSqlite = new File(getContext().getCacheDir(), "restore_tmp_sqlite");
            byte[] hawkJsonBytes = null;

            ZipInputStream zis = new ZipInputStream(in);
            try {
                ZipEntry entry;
                boolean sqliteFound = false;
                while ((entry = zis.getNextEntry()) != null) {
                    if (ZIP_ENTRY_SQLITE.equals(entry.getName())) {
                        readZipEntryToFile(zis, tmpSqlite);
                        sqliteFound = true;
                    } else if (ZIP_ENTRY_HAWK.equals(entry.getName())) {
                        hawkJsonBytes = readZipEntryToBytes(zis);
                    }
                    zis.closeEntry();
                }
                if (!sqliteFound || hawkJsonBytes == null) {
                    Toast.makeText(getContext(), "所选文件不是有效的备份文件!", Toast.LENGTH_SHORT).show();
                    return;
                }
            } finally {
                zis.close();
            }

            if (!AppDataManager.restore(tmpSqlite)) {
                Toast.makeText(getContext(), "DB文件恢复失败!", Toast.LENGTH_SHORT).show();
                return;
            }

            String hawkJson = new String(hawkJsonBytes, "UTF-8");
            JSONObject jsonObject = new JSONObject(hawkJson);
            Iterator<String> it = jsonObject.keys();
            SharedPreferences sharedPreferences = App.getInstance().getSharedPreferences("Hawk2", Context.MODE_PRIVATE);
            while (it.hasNext()) {
                String key = it.next();
                String value = jsonObject.getString(key);
                if (key.equals("cipher_key")) {
                    App.getInstance().getSharedPreferences("crypto.KEY_256", Context.MODE_PRIVATE).edit().putString(key, value).commit();
                } else {
                    sharedPreferences.edit().putString(key, value).commit();
                }
            }

            Toast.makeText(getContext(), "恢复成功,即将自动重启应用!", Toast.LENGTH_SHORT).show();
            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    restartApp();
                }
            }, 3000);
        } catch (Throwable e) {
            e.printStackTrace();
            Toast.makeText(getContext(), "恢复失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        } finally {
            if (tmpSqlite != null && tmpSqlite.exists()) {
                tmpSqlite.delete();
            }
        }
    }

    private void readZipEntryToFile(ZipInputStream zis, File dst) throws IOException {
        if (dst.exists()) dst.delete();
        OutputStream os = new java.io.FileOutputStream(dst);
        try {
            byte[] buffer = new byte[8192];
            int len;
            while ((len = zis.read(buffer)) > 0) {
                os.write(buffer, 0, len);
            }
        } finally {
            os.close();
        }
    }

    private byte[] readZipEntryToBytes(ZipInputStream zis) throws IOException {
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int len;
        while ((len = zis.read(buffer)) > 0) {
            bos.write(buffer, 0, len);
        }
        return bos.toByteArray();
    }

    private void restartApp() {
        Context context = getContext();
        if (context != null) {
            Intent i = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
            if (i != null) {
                i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                context.startActivity(i);
                System.exit(0);
            }
        }
    }
}
