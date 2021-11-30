package com.github.devgcoder.locache;


import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.TimeoutUtils;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.Type;
import java.util.concurrent.TimeUnit;

/**
 * @author duheng
 * @Date 2021/11/19 11:55
 */
@Slf4j
@Order(Integer.MIN_VALUE + 1)
@Aspect
@EnableAspectJAutoProxy(exposeProxy = true, proxyTargetClass = true)
@Component
public class LoCacheableAop {

    /**
     * 用于SpEL表达式解析.
     */
    private static final SpelExpressionParser parser = new SpelExpressionParser();
    /**
     * 用于获取方法参数定义名字.
     */
    private static final DefaultParameterNameDiscoverer nameDiscoverer = new DefaultParameterNameDiscoverer();

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Around(value = "@annotation(LoCacheable)")
    public Object loCache(ProceedingJoinPoint joinPoint) throws Throwable {
        if (null == LoCacheableConfig.locacheNodeKey || LoCacheableConfig.locacheNodeKey.equals("")) {
            return joinPoint.proceed();
        }
        boolean isMapper = false;
        Class clazz = joinPoint.getTarget().getClass();
        String methodName = joinPoint.getSignature().getName();
        Class<?>[] parameterTypes = ((MethodSignature) joinPoint.getSignature()).getParameterTypes();
        Method method = clazz.getMethod(methodName, parameterTypes);
        LoCacheable loCacheable = method.getAnnotation(LoCacheable.class);
        if (null == loCacheable) {
            Type[] types = AopUtils.getTargetClass(joinPoint.getTarget()).getGenericInterfaces();
            String clazzName = types[0].getTypeName();
            clazz = Class.forName(clazzName);
            method = clazz.getMethod(methodName, parameterTypes);
            loCacheable = method.getAnnotation(LoCacheable.class);
            if (null == loCacheable) {
                return joinPoint.proceed(); //执行目标方法
            }
            isMapper = true;
        }
        long timeout = loCacheable.timeout();
        TimeUnit timeUnit = loCacheable.timeUnit();
        String cacheNames = loCacheable.cacheNames();
        if (null == cacheNames || cacheNames.trim().equals("")) {
            return joinPoint.proceed();
        }
        if (!cacheNames.endsWith(":")) {
            cacheNames = (cacheNames + ":");
        }
        String key = loCacheable.key();
        if (null == key || key.trim().equals("")) {
            return joinPoint.proceed();
        }
        String keyVal = cacheNames + generateKeyBySpEL(key, joinPoint, isMapper);
        boolean tempCache = loCacheable.openTempCache();
        if (tempCache) {
            int tempCacheSeconds = loCacheable.tempCacheSeconds();
            LoCacheableNode loCacheableNode = LoCacheableData.loDataMap.get(keyVal);
            if (null != loCacheableNode) {
                long rawTimeout = TimeoutUtils.toMillis(timeout, timeUnit);
                long expireTime = loCacheableNode.getExpireTime();
                long current = System.currentTimeMillis();
                long tempCacheTime = TimeoutUtils.toMillis(tempCacheSeconds, TimeUnit.SECONDS);
                if (current < (expireTime - rawTimeout + tempCacheTime)) {
                    return loCacheableNode.getNodeVal();
                }
            }
        }
        int cacheThreadNumber = loCacheable.cacheThreadNumber();
        LoCacheableData.loLockMap.putIfAbsent(keyVal, new LoCacheableData.LoCacheableKey(keyVal, cacheThreadNumber));
        synchronized (LoCacheableData.loLockMap.get(keyVal).getLockKey()) {
            Object locacheNodeKey = stringRedisTemplate.opsForHash().get(keyVal, LoCacheableConfig.locacheNodeKey);
            if (null != locacheNodeKey && locacheNodeKey.equals("1")) {
                LoCacheableNode loCacheableNode = LoCacheableData.loDataMap.get(keyVal);
                if (null == loCacheableNode) {
                    return joinPoint.proceed();
                }
                return loCacheableNode.getNodeVal();
            }
            long rawTimeout = TimeoutUtils.toMillis(timeout, timeUnit);
            Object object = joinPoint.proceed();
            LoCacheableData.loDataMap.put(keyVal, new LoCacheableNode((System.currentTimeMillis() + rawTimeout), object));
            Boolean hasKey = stringRedisTemplate.hasKey(keyVal);
            stringRedisTemplate.opsForHash().put(keyVal, LoCacheableConfig.locacheNodeKey, "1");
            if (!hasKey) {
                stringRedisTemplate.expire(keyVal, timeout, timeUnit);
            }
            return object;
        }
    }

    @Before(value = "@annotation(LoCacheEvict)")
    public void evictLoCache(JoinPoint joinPoint) throws Throwable {
        boolean isMapper = false;
        Class clazz = joinPoint.getTarget().getClass();
        String methodName = joinPoint.getSignature().getName();
        Class<?>[] parameterTypes = ((MethodSignature) joinPoint.getSignature()).getParameterTypes();
        Method method = clazz.getMethod(methodName, parameterTypes);
        LoCacheEvict loCacheEvict = method.getAnnotation(LoCacheEvict.class);
        if (null == loCacheEvict) {
            Type[] types = AopUtils.getTargetClass(joinPoint.getTarget()).getGenericInterfaces();
            String clazzName = types[0].getTypeName();
            clazz = Class.forName(clazzName);
            method = clazz.getMethod(methodName, parameterTypes);
            loCacheEvict = method.getAnnotation(LoCacheEvict.class);
            if (null == loCacheEvict) {
                return;
            }
            isMapper = true;
        }
        String cacheNames = loCacheEvict.cacheNames();
        if (null == cacheNames || cacheNames.trim().equals("")) {
            return;
        }
        if (!cacheNames.endsWith(":")) {
            cacheNames = (cacheNames + ":");
        }
        String key = loCacheEvict.key();
        if (null == key || key.trim().equals("")) {
            return;
        }
        String keyVal = cacheNames + generateKeyBySpEL(key, joinPoint, isMapper);
        stringRedisTemplate.delete(keyVal);
    }

    public String generateKeyBySpEL(String spELString, JoinPoint joinPoint, boolean isMapper) {
        // 通过joinPoint获取被注解方法
        MethodSignature methodSignature = (MethodSignature) joinPoint.getSignature();
        Method method = methodSignature.getMethod();
        // 使用spring的DefaultParameterNameDiscoverer获取方法形参名数组
        String[] paramNames;
        if (isMapper) {
            paramNames = getMethodParameterNamesByAnnotation(method);
        } else {
            paramNames = nameDiscoverer.getParameterNames(method);
        }
        // 解析过后的Spring表达式对象
        Expression expression = parser.parseExpression(spELString);
        // spring的表达式上下文对象
        EvaluationContext context = new StandardEvaluationContext();
        // 通过joinPoint获取被注解方法的形参
        Object[] args = joinPoint.getArgs();
        // 给上下文赋值
        for (int i = 0; i < args.length; i++) {
            context.setVariable(paramNames[i], args[i]);
        }
        // 表达式从上下文中计算出实际参数值
        /*如:
            @annotation(key="#student.name")
             method(Student student)
             那么就可以解析出方法形参的某属性值，return “xiaoming”;
          */
        return expression.getValue(context).toString();
    }

    public String[] getMethodParameterNamesByAnnotation(Method method) {
        Parameter[] params = method.getParameters();
        String[] paramNames = new String[params.length];
        for (int i = 0; i < params.length; i++) {
            paramNames[i] = params[i].getName();
        }
        return paramNames;
    }


}
