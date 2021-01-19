package com.example.openfeign;

import feign.FeignException;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JCircuitBreakerFactory;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JConfigBuilder;
import org.springframework.cloud.client.circuitbreaker.Customizer;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.cloud.openfeign.FeignCircuitBreaker;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;

@SpringBootTest(classes = {FeignClientServiceTest.FeignClientServiceTestConfiguration.class, FeignClientServiceTest.Application.class, FeignClientServiceTest.Resilience4JConfiguration.class, FeignClientServiceTest.FeignClientService.class},
        value = { "spring.application.name=feignclientcircuitbreakertest",
                "logging.level.org.springframework.cloud.openfeign.valid=DEBUG",
                "feign.circuitbreaker.enabled=true" })
class FeignClientServiceTest {

    @Autowired
    private FeignCircuitBreaker.Builder circuitBreakerFeignBuilder;

    @Autowired
    private FeignClientService feignClientService;

    @Test
    void testGetWaetherCallUsesCircuitBreaker() {
        final var weather = feignClientService.getWeather("zhr");
        assertNotNull(weather);
        verify(circuitBreakerFeignBuilder, atLeastOnce()).build();
        verify(circuitBreakerFeignBuilder, atLeastOnce()).build(any());
    }

    @Test
    void testGetEmojisCallUsesCircuitBreaker() {
        final var emojis = feignClientService.getEmojis();
        assertNotNull(emojis);
        verify(circuitBreakerFeignBuilder, atLeastOnce()).build();
        verify(circuitBreakerFeignBuilder, atLeastOnce()).build(any());
    }

    @Configuration
    static class FeignClientServiceTestConfiguration {

        @Bean
        @Primary
        public FeignCircuitBreaker.Builder circuitBreakerFeignBuilder() {
            return Mockito.spy(FeignCircuitBreaker.Builder.class);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @EnableFeignClients
    static class Application {
    }

    @Service
    static class FeignClientService {

        @Autowired
        private MetaWeatherClient metaWeatherClient;
        @Autowired
        private GitHubClient gitHubClient;

        public String getWeather(String query) {
            return metaWeatherClient.getWeather(query);
        }

        public String getEmojis() {
            return gitHubClient.getEmojis();
        }
    }

    @FeignClient(name = "MetaWeather", url = "https://www.metaweather.com")
    protected interface MetaWeatherClient {

        @GetMapping(value = "/api/location/search/?query={query}")
        String getWeather(@PathVariable("query") String query);
    }

    @FeignClient(name = "GitHub", url = "https://api.github.com")
    protected interface GitHubClient {

        @GetMapping(value = "/emojis")
        String getEmojis();
    }

    @Configuration
    static class Resilience4JConfiguration {

        @Bean
        public Customizer<Resilience4JCircuitBreakerFactory> globalCustomConfiguration() {
            CircuitBreakerConfig circuitBreakerConfig = CircuitBreakerConfig.custom()
                    .failureRateThreshold(50)
                    .permittedNumberOfCallsInHalfOpenState(10)
                    .waitDurationInOpenState(Duration.ofSeconds(30))
                    .automaticTransitionFromOpenToHalfOpenEnabled(true)
                    .ignoreExceptions(FeignException.MethodNotAllowed.class)
                    .build();
            TimeLimiterConfig timeLimiterConfig = TimeLimiterConfig.custom()
                    .timeoutDuration(Duration.ofSeconds(10))
                    .build();

            return factory -> factory.configureDefault(id -> new Resilience4JConfigBuilder(id)
                    .timeLimiterConfig(timeLimiterConfig)
                    .circuitBreakerConfig(circuitBreakerConfig)
                    .build());
        }

        @Bean
        public Customizer<Resilience4JCircuitBreakerFactory> accountManagementCustomConfiguration1() {
            CircuitBreakerConfig circuitBreakerConfig = CircuitBreakerConfig.custom()
                    .build();

            return factory -> factory.configure(builder -> builder.circuitBreakerConfig(circuitBreakerConfig).build(), "Github");
        }
    }
}
