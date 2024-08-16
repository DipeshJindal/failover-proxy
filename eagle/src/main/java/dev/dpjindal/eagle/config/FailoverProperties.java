package dev.dpjindal.eagle.config;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "failover.proxy")
public class FailoverProperties {
    public volatile Set<String> supported = new HashSet<>();
    public volatile ConcurrentMap<String, List<String>> methodStrategies = new ConcurrentHashMap<>();


    private volatile boolean resetOnContextRefresh = true;
}
