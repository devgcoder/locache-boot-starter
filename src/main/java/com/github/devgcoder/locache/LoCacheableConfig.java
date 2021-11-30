package com.github.devgcoder.locache;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;

/**
 * @author duheng
 * @Date 2021/11/19 13:34
 */
@Slf4j
@ComponentScan(basePackages = {"com.github.devgcoder.locache"})
@Configuration
public class LoCacheableConfig implements InitializingBean {

    public static String locacheNodeKey = null;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public void afterPropertiesSet() throws Exception {
        String nowTime = LocalDate.now().format(DateTimeFormatter.ofPattern("MMdd"));
        String nodeKey = "locache:node:" + nowTime;
        Long nodeKeyVersion = stringRedisTemplate.opsForValue().increment(nodeKey);
        stringRedisTemplate.expire(nodeKey, 24, TimeUnit.HOURS);
        locacheNodeKey = nowTime + nodeKeyVersion;
        log.info("locache node key:{}", locacheNodeKey);
        long intervalTime = 30000;
        Timer timer = new Timer();
        timer.scheduleAtFixedRate(new DataClear(), intervalTime, intervalTime);
        log.info("locache data clear thread started");
    }

    public static class DataClear extends TimerTask {

        @Override
        public void run() {
            try {
                log.info("locache data clear start");
                clearLoDataMap();
                log.info("locache data clear end");
            } catch (Exception ex) {
                log.error("locache data clear error", ex.getMessage());
            }
        }
    }

    private static void clearLoDataMap() {
        Iterator<Entry<String, LoCacheableNode>> it = LoCacheableData.loDataMap.entrySet().iterator();
        while (it.hasNext()) {
            Entry<String, LoCacheableNode> entry = it.next();
            LoCacheableNode loCacheableNode = entry.getValue();
            long now = System.currentTimeMillis();
            long expireTime = (loCacheableNode.getExpireTime() + 30 * 1000);
            if (now >= expireTime) {
                log.info("locache loDataMap key:{},expireTime:{},now:{}", entry.getKey(), expireTime, now);
                it.remove();
                log.info("locache loDataMap clear key:{} success", entry.getKey());
            }
        }
    }
}
