package dev.dpjindal.eagle.config.util;


import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.bulkhead.ThreadPoolBulkhead;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.timelimiter.TimeLimiter;

import java.util.concurrent.ScheduledExecutorService;

public interface ProxyValidateResilience4jProvider {
    String RESILIENCE4J_NAME = "ProxyServiceValidation";

    ObjectMapper getObjectMapper();

    ScheduledExecutorService getTimeoutExecutorService();

    ThreadPoolBulkhead getThreadBulkhead();

    TimeLimiter getTimeLimiter();

    CircuitBreaker getCircuitBreaker();

}