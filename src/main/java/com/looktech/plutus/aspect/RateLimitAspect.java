package com.looktech.plutus.aspect;

import com.looktech.plutus.annotation.RateLimit;
import com.looktech.plutus.exception.CreditException;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;

@Aspect
@Component
@RequiredArgsConstructor
public class RateLimitAspect {

    private final RedisTemplate<String, Object> redisTemplate;

    @Around("@annotation(com.looktech.plutus.annotation.RateLimit)")
    public Object rateLimit(ProceedingJoinPoint point) throws Throwable {
        MethodSignature signature = (MethodSignature) point.getSignature();
        Method method = signature.getMethod();
        RateLimit rateLimit = method.getAnnotation(RateLimit.class);

        String key = rateLimit.key();
        if (key.isEmpty()) {
            key = method.getDeclaringClass().getName() + ":" + method.getName();
        }

        String redisKey = "rate_limit:" + key;
        Long count = redisTemplate.opsForValue().increment(redisKey);
        if (count == null) {
            throw new CreditException("RATE_LIMIT_EXCEEDED", "Rate limit exceeded");
        }
        if (count == 1) {
            redisTemplate.expire(redisKey, rateLimit.period(), TimeUnit.SECONDS);
        }

        if (count > rateLimit.limit()) {
            throw new CreditException("RATE_LIMIT_EXCEEDED", "Rate limit exceeded");
        }

        return point.proceed();
    }
} 