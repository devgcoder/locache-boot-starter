package com.github.devgcoder.locache;

import java.lang.annotation.*;

/**
 * @author duheng
 * @Date 2021/11/21 17:45
 */

@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Documented
public @interface LoCacheEvict {

	String cacheNames() default "";      // 缓存大类名称

	String key() default "";             // 缓存小类名称

}
