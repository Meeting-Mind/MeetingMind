package com.meetingmind.bff.config;

import com.meetingmind.bff.proxy.DownstreamHttpClient;
import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(DownstreamProxyProperties.class)
public class ProxyConfiguration {

    @Bean
    DownstreamHttpClient downstreamHttpClient(
            DownstreamProxyProperties properties, Clock tokenVaultClock) {
        return new DownstreamHttpClient(properties, tokenVaultClock);
    }
}
