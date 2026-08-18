package com.mobile.novabox.cache;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface LocalVideoDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<LocalVideoEntity> entities);

    @Query("SELECT * FROM localVideo")
    List<LocalVideoEntity> getAll();

    @Query("UPDATE localVideo SET thumbPath = :thumbPath WHERE path = :path")
    void updateThumbPath(String path, String thumbPath);

    @Query("DELETE FROM localVideo")
    void deleteAll();

    /** 全量替换：先清空再插入，用于"重新扫描覆盖旧缓存"的场景，保证不残留已删除的文件记录。 */
    @androidx.room.Transaction
    default void replaceAll(List<LocalVideoEntity> entities) {
        deleteAll();
        insertAll(entities);
    }
}
