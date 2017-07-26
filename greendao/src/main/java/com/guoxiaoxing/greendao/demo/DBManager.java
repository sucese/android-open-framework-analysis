package com.guoxiaoxing.greendao.demo;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

import org.greenrobot.greendao.query.QueryBuilder;

import java.util.List;

/**
 * For more information, you can visit https://github.com/guoxiaoxing or contact me by
 * guoxiaoxingse@163.com.
 *
 * @author guoxiaoxing
 * @since 2017/7/25 下午4:00
 */
public class DBManager {

    private final static String DB_NAME = "test_db";

    private Context mContext;
    private static volatile DBManager instance;
    private DaoMaster.DevOpenHelper openHelper;

    public static DBManager getInstance(Context context) {
        if (instance == null) {
            synchronized (DBManager.class) {
                if (instance == null) {
                    instance = new DBManager(context);
                }
            }
        }
        return instance;
    }

    private DBManager(Context mContext) {
        this.mContext = mContext;
        openHelper = new DaoMaster.DevOpenHelper(mContext, DB_NAME);
    }

    public SQLiteDatabase getReadableDatabase() {
        if (openHelper == null) {
            openHelper = new DaoMaster.DevOpenHelper(mContext, DB_NAME);
        }
        return openHelper.getReadableDatabase();
    }

    public SQLiteDatabase getWriteableDatabase() {
        if (openHelper == null) {
            openHelper = new DaoMaster.DevOpenHelper(mContext, DB_NAME);
        }
        return openHelper.getWritableDatabase();
    }


    public DaoSession getSession() {
        return new DaoMaster(getWriteableDatabase()).newSession();
    }

    public void insert(User user) {
        DaoSession session = new DaoMaster(getWriteableDatabase()).newSession();
        session.getUserDao().insert(user);
    }

    public void delete(User user) {
        DaoSession session = new DaoMaster(getWriteableDatabase()).newSession();
        session.getUserDao().delete(user);
    }

    public List<User> query(int age) {
        DaoSession session = new DaoMaster(getWriteableDatabase()).newSession();
        QueryBuilder<User> queryBuilder = session.getUserDao().queryBuilder();
        queryBuilder.where(UserDao.Properties.Age.ge(age)).orderAsc(UserDao.Properties.Age);
        return queryBuilder.list();
    }

    public void update(User user) {
        DaoSession session = new DaoMaster(getWriteableDatabase()).newSession();
        session.getUserDao().update(user);
    }

    public List<User> listAllData() {
        DaoSession session = new DaoMaster(getWriteableDatabase()).newSession();
        QueryBuilder<User> queryBuilder = session.getUserDao().queryBuilder();
        return queryBuilder.list();
    }

    public void clear() {
        DaoSession session = new DaoMaster(getWriteableDatabase()).newSession();
        session.getUserDao().deleteAll();
    }
}
