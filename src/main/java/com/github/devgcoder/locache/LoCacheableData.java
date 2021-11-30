package com.github.devgcoder.locache;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author duheng
 * @Date 2021/11/19 14:06
 */
public class LoCacheableData {

    public static final Map<String, LoCacheableNode> loDataMap = new ConcurrentHashMap<>(512);

    public static final Map<String, LoCacheableKey> loLockMap = new ConcurrentHashMap<>(512);

    static class LoCacheableKey extends ConcurrentHashMap<String, String> {

        private String keyVal;

        private AtomicLong keyNumber;

        private int treadNumber;

        LoCacheableKey(String keyVal, int treadNumber) {
            this.keyVal = keyVal;
            this.treadNumber = treadNumber;
            this.keyNumber = new AtomicLong(1);
        }

        public String getLockKey() {
            long lockKeyNumber = keyNumber.getAndIncrement();
            String lockKey = keyVal + ":" + (lockKeyNumber % treadNumber);
            this.putIfAbsent(lockKey, lockKey);
            return this.get(lockKey);
        }

        public String getKeyVal() {
            return keyVal;
        }

        public void setKeyVal(String keyVal) {
            this.keyVal = keyVal;
        }
    }

}
