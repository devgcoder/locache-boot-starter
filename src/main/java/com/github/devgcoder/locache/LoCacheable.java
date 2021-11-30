package com.github.devgcoder.locache;

import java.lang.annotation.*;
import java.util.concurrent.TimeUnit;

/**
 * @author duheng
 * @Date 2021/11/19 11:45
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Documented
public @interface LoCacheable {

	String cacheNames() default "";      // 缓存大类名称

	String key() default "";             // 缓存小类名称

	long timeout() default 30L;

	TimeUnit timeUnit() default TimeUnit.SECONDS;

	boolean openTempCache() default false;

	int tempCacheSeconds() default 10;

	int cacheThreadNumber() default 1;
}
