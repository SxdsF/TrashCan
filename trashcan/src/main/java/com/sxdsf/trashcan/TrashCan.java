package com.sxdsf.trashcan;

import android.support.annotation.NonNull;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * com.sxdsf.trashcan.TrashCan
 *
 * @author 孙博闻
 * @date 2016/11/1 10:57
 * @desc 用于做缓存的主类
 */

public final class TrashCan {

    /**
     * 清理线程的清理频率
     */
    private static final long CLEAR_FREQUENCY = 10000L;
    /**
     * 存放永不过期的cache
     */
    private final Map<String, Cell<?>> mNeverExpiredCache = new HashMap<>();
    /**
     * 存放会过期的cache
     */
    private final Map<String, Cell<?>> mExpiredCache = new HashMap<>();
    /**
     * 永不过期的cache的锁
     */
    private final Lock mNeverExpiredLock = new ReentrantLock(true);
    /**
     * 会过期的cache的锁
     */
    private final Lock mExpiredLock = new ReentrantLock(true);

    private TrashCan() {
        Executors.newScheduledThreadPool(1, new ThreadFactory() {
            @Override
            public Thread newThread(@NonNull Runnable r) {
                return new Thread(r, "trash-can-");
            }
        }).scheduleWithFixedDelay(new Runnable() {
            @Override
            public void run() {
                expire();
            }
        }, CLEAR_FREQUENCY, CLEAR_FREQUENCY, TimeUnit.MILLISECONDS);
    }

    /**
     * 会过期的cache清理
     */
    private void expire() {
        mExpiredLock.lock();
        try {
            //如果为空，说明不用去清理
            if (mExpiredCache.isEmpty()) {
                return;
            }
            Set<Map.Entry<String, Cell<?>>> set = mExpiredCache.entrySet();
            for (Iterator<Map.Entry<String, Cell<?>>> iterator = set.iterator(); iterator.hasNext(); ) {
                Map.Entry<String, Cell<?>> entry = iterator.next();
                if (entry == null) {
                    continue;
                }
                Cell<?> cell = entry.getValue();
                if (cell == null) {
                    continue;
                }
                //如果数据过期了，就清除
                if (cell.isExpired()) {
                    iterator.remove();
                }
            }
        } finally {
            mExpiredLock.unlock();
        }
    }

    public static TrashCan getInstance() {
        return InstanceHolder.INSTANCE;
    }

    /**
     * 向cache中存放，默认是永不过期
     *
     * @param key   key
     * @param value value
     * @param <V>
     */
    public <V> void put(String key, V value) {
        this.put(key, value, TimeUnit.MILLISECONDS, Cell.WILL_NOT_INVALID);
    }

    /**
     * 向cache中存放
     *
     * @param key      key
     * @param value    value
     * @param timeUnit 时间单位
     * @param duration 存放时间
     * @param <V>
     */
    public <V> void put(String key, V value, @NonNull TimeUnit timeUnit, long duration) {
        Cell<V> cell = new Cell<>();
        cell.mSaveTime = System.currentTimeMillis();
        cell.data = value;
        cell.mTimeUnit = timeUnit;
        cell.mDuration = duration;

        //判断是不是永不过期的，分别放在两个不同的map中
        if (cell.neverExpired()) {
            mNeverExpiredLock.lock();
            try {
                mNeverExpiredCache.put(key, cell);
            } finally {
                mNeverExpiredLock.unlock();
            }
        } else {
            mExpiredLock.lock();
            try {
                mExpiredCache.put(key, cell);
            } finally {
                mExpiredLock.unlock();
            }
        }

    }

    /**
     * 从cache中获取，默认取走时清除
     *
     * @param key  key
     * @param type 缓存类型
     * @param <V>
     * @return
     */
    public <V> V get(String key, Type type) {
        //默认是remove
        return this.get(key, type, true);
    }

    /**
     * 从cache中获取
     *
     * @param key      key
     * @param type     缓存类型
     * @param isRemove 是否取走时清除
     * @param <V>
     * @return
     */
    public <V> V get(String key, Type type, boolean isRemove) {
        V value = null;
        switch (type) {
            case STORAGE:
                mNeverExpiredLock.lock();
                try {
                    Cell<?> cell = mNeverExpiredCache.get(key);
                    if (cell != null) {
                        value = (V) cell.data;
                    }
                    if (isRemove) {
                        mNeverExpiredCache.remove(key);
                    }
                } catch (Exception e) {
                    //抛异常强制删除
                    mNeverExpiredCache.remove(key);
                } finally {
                    mNeverExpiredLock.unlock();
                }
                break;
            case CACHE:
                mExpiredLock.lock();
                try {
                    Cell<?> cell = mExpiredCache.get(key);
                    //清除存在延迟，这里取得时候还要判断一下是不是过期了
                    if (cell != null) {
                        if (!cell.isExpired()) {
                            value = (V) cell.data;
                        } else {
                            mExpiredCache.remove(key);
                        }
                    }
                    if (isRemove) {
                        mExpiredCache.remove(key);
                    }
                } catch (Exception e) {
                    //抛异常强制删除
                    mExpiredCache.remove(key);
                } finally {
                    mExpiredLock.unlock();
                }
                break;
        }
        return value;
    }

    /**
     * 清除所有缓存
     */
    public void clear() {
        mNeverExpiredLock.lock();
        try {
            mNeverExpiredCache.clear();
        } finally {
            mNeverExpiredLock.unlock();
        }
        mExpiredLock.lock();
        try {
            mExpiredCache.clear();
        } finally {
            mExpiredLock.unlock();
        }
    }

    private static class InstanceHolder {
        private static final TrashCan INSTANCE = new TrashCan();
    }

    private static class Cell<T> {
        private static final long WILL_NOT_INVALID = -1L;
        /**
         * 存入时间，表明这个存储单元存入缓存的时间
         */
        private long mSaveTime;
        /**
         * 持续时间，表明这个存储单元在缓存中存在的时间
         */
        private long mDuration;
        /**
         * 时间的单元格式，是秒还是毫秒等
         */
        private TimeUnit mTimeUnit;
        /**
         * 此存储单元存储的数据
         */
        private T data;

        /**
         * 判断是不是永不过期的
         *
         * @return
         */
        private boolean neverExpired() {
            return mDuration < 0;
        }

        /**
         * 判断是否过期了
         *
         * @return
         */
        private boolean isExpired() {
            return System.currentTimeMillis() - mSaveTime > mTimeUnit.toMillis(mDuration);
        }
    }

    public enum Type {
        /**
         * 永不过期
         */
        STORAGE,
        /**
         * 会过期
         */
        CACHE
    }
}
