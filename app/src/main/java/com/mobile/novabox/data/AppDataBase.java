package com.mobile.novabox.data;

import androidx.room.Database;
import androidx.room.RoomDatabase;

import com.mobile.novabox.cache.Cache;
import com.mobile.novabox.cache.CacheDao;
import com.mobile.novabox.cache.VodCollect;
import com.mobile.novabox.cache.VodCollectDao;
import com.mobile.novabox.cache.VodRecord;
import com.mobile.novabox.cache.VodRecordDao;


/**
 * 类描述:
 *
 * @author pj567
 * @since 2020/5/15
 */
@Database(entities = {Cache.class, VodRecord.class, VodCollect.class}, version = 1)
public abstract class AppDataBase extends RoomDatabase {
    public abstract CacheDao getCacheDao();

    public abstract VodRecordDao getVodRecordDao();

    public abstract VodCollectDao getVodCollectDao();
}
