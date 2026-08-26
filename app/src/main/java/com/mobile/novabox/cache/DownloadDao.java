package com.mobile.novabox.cache;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface DownloadDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(DownloadEntity entity);

    @Query("select * from download order by createTime desc")
    List<DownloadEntity> getAll();

    @Query("select * from download where status != 4 order by createTime desc")
    List<DownloadEntity> getVisible();

    @Query("select * from download where id = :id")
    DownloadEntity getById(int id);

    @Query("update download set status=:status, errorMsg=:err where id=:id")
    void updateStatus(int id, int status, String err);

    @Query("update download set progress=:progress, downloadedSize=:downloadedSize, totalSize=:totalSize where id=:id")
    void updateProgress(int id, int progress, long downloadedSize, long totalSize);

    @Query("update download set status=2, progress=100, localPath=:path, downloadedSize=:total, totalSize=:total, finishTime=:finish where id=:id")
    void markDone(int id, String path, long total, long finish);

    @Query("update download set status=:status where id=:id")
    void updateStatusOnly(int id, int status);

    @Query("update download set status=3, errorMsg=:err where id=:id")
    void markFailed(int id, String err);

    @Delete
    int delete(DownloadEntity entity);

    @Query("DELETE FROM download WHERE id = :id")
    void deleteById(int id);

    @Query("select count(*) from download")
    int getCount();
}
