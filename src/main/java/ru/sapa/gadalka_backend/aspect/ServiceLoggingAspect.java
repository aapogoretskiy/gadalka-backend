package ru.sapa.gadalka_backend.aspect;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

@Slf4j
@Aspect
@Component
public class ServiceLoggingAspect {

    @Around("execution(* ru.sapa.gadalka_backend.service..*(..))")
    public Object logServiceMethod(ProceedingJoinPoint joinPoint) throws Throwable {
        String className = joinPoint.getTarget().getClass().getSimpleName();
        String methodName = joinPoint.getSignature().getName();

        log.debug("[{}] → {}({})", className, methodName, formatArgs(joinPoint.getArgs()));

        long startTime = System.currentTimeMillis();
        try {
            Object result = joinPoint.proceed();
            long duration = System.currentTimeMillis() - startTime;
            log.debug("[{}] ← {}() завершён за {}мс", className, methodName, duration);
            return result;
        } catch (Throwable ex) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("[{}] ✗ {}() завершился с ошибкой за {}мс: {} — {}",
                    className, methodName, duration, ex.getClass().getSimpleName(), ex.getMessage());
            throw ex;
        }
    }

    private String formatArgs(Object[] args) {
        if (args == null || args.length == 0) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < args.length; i++) {
            if (i > 0) sb.append(", ");
            Object arg = args[i];
            if (arg == null) {
                sb.append("null");
            } else if (arg instanceof String || arg instanceof Number || arg instanceof Boolean) {
                // Примитивные типы логируем напрямую, но ограничиваем длину строк
                String str = arg.toString();
                sb.append(str.length() > 60 ? str.substring(0, 60) + "…" : str);
            } else {
                // Для сложных объектов логируем только имя класса, чтобы не утечь данные
                sb.append(arg.getClass().getSimpleName());
            }
        }
        return sb.toString();
    }
}
