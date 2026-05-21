package com.zjw.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * @author ZhangJw
 */
@Data
@Component
@ConfigurationProperties(prefix = "searchapi")
public class SearchApiProperties {
    private String apiKey;
    private String engine = "bing";
    private int numResults = 5;
}