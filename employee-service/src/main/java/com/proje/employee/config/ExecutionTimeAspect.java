package com.proje.employee.config;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class ExecutionTimeAspect {

    private static final Logger log = LoggerFactory.getLogger(ExecutionTimeAspect.class);

    private static final long SLOW_THRESHOLD_MS = 500;

    @Around("execution(* com.proje.employee.service..*(..))")
    public Object measure(ProceedingJoinPoint joinPoint) throws Throwable {
        long startedAt = System.nanoTime();

        try {
            return joinPoint.proceed();
        } finally {
            // finally: istisna firlasa da olcum kaydedilir.
            long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000;
            String method = joinPoint.getSignature().toShortString();

            if (elapsedMs >= SLOW_THRESHOLD_MS) {
                log.warn("SLOW  {} -> {} ms", method, elapsedMs);
            } else {
                log.debug("{} -> {} ms", method, elapsedMs);
            }
        }
    }
}
