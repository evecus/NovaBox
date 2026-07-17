package com.mobile.novabox.cache;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface LocalAudioDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<LocalAudioEntity> entities);

    @Query("SELECT * FROM localAudio")
    List<LocalAudioEntity> getAll();

    @Query("DELETE FROM localAudio")
    void deleteAll();

    /** 全量替换：先清空再插入，用于"重新扫描覆盖旧缓存"的场景，保证不残留已删除的文件记录。 */
    @androidx.room.Transaction
    default void replaceAll(List<LocalAudioEntity> entities) {
        deleteAll();
        insertAll(entities);
    }
}
