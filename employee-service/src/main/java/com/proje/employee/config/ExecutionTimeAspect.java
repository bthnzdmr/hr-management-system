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
        long baslangic = System.nanoTime();

        try {
            return joinPoint.proceed();
        } finally {
            // finally: istisna firlasa da olcum kaydedilir.
            long ms = (System.nanoTime() - baslangic) / 1_000_000;
            String metot = joinPoint.getSignature().toShortString();

            if (ms >= SLOW_THRESHOLD_MS) {
                log.warn("YAVAS  {} -> {} ms", metot, ms);
            } else {
                log.debug("{} -> {} ms", metot, ms);
            }
        }
    }
}
