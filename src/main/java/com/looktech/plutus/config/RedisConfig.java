package com.looktech.plutus.config;

import io.lettuce.core.RedisURI;
import io.lettuce.core.cluster.RedisClusterClient;
import io.lettuce.core.cluster.api.StatefulRedisClusterConnection;
import io.lettuce.core.cluster.api.sync.RedisAdvancedClusterCommands;
import io.lettuce.core.ClientOptions;
import io.lettuce.core.SslOptions;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisClusterConfiguration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.core.env.Environment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.Arrays;

@Slf4j
@Configuration
@EnableCaching
@RequiredArgsConstructor
public class RedisConfig {

    private final Environment environment;

    @Bean
    public RedisConnectionFactory redisConnectionFactory() {
        boolean isClusterMode = Boolean.parseBoolean(environment.getProperty("spring.redis.cluster.enabled", "false"));
        
        if (isClusterMode) {
            return createClusterConnectionFactory();
        } else {
            return createStandaloneConnectionFactory();
        }
    }

    private RedisConnectionFactory createClusterConnectionFactory() {
        String clusterNodes = environment.getProperty("spring.redis.cluster.nodes");
        if (clusterNodes == null || clusterNodes.trim().isEmpty()) {
            throw new IllegalStateException("Redis cluster nodes configuration is missing");
        }
        String password = environment.getProperty("spring.redis.password");
        boolean sslEnabled = Boolean.parseBoolean(environment.getProperty("spring.redis.ssl", "false"));
        
        log.info("Initializing Redis Cluster Connection Factory");
        log.info("Cluster Nodes: {}", clusterNodes);
        log.info("SSL Enabled: {}", sslEnabled);
        
        RedisClusterConfiguration clusterConfig = new RedisClusterConfiguration(Arrays.asList(clusterNodes.split(",")));
        if (password != null && !password.isEmpty()) {
            clusterConfig.setPassword(password);
        }

        LettuceClientConfiguration clientConfig = LettuceClientConfiguration.builder()
                .commandTimeout(Duration.ofMillis(Long.parseLong(environment.getProperty("spring.redis.timeout", "10000"))))
                .build();

        if (sslEnabled) {
            clientConfig = LettuceClientConfiguration.builder()
                    .commandTimeout(Duration.ofMillis(Long.parseLong(environment.getProperty("spring.redis.timeout", "10000"))))
                    .useSsl()
                    .build();
        }
        
        return new LettuceConnectionFactory(clusterConfig, clientConfig);
    }

    private RedisConnectionFactory createStandaloneConnectionFactory() {
        String host = environment.getProperty("spring.redis.host", "localhost");
        int port = Integer.parseInt(environment.getProperty("spring.redis.port", "6379"));
        String password = environment.getProperty("spring.redis.password");
        int database = Integer.parseInt(environment.getProperty("spring.redis.database", "0"));
        boolean sslEnabled = Boolean.parseBoolean(environment.getProperty("spring.redis.ssl", "false"));
        
        log.info("Initializing Redis Standalone Connection Factory");
        log.info("Host: {}, Port: {}, Database: {}, SSL: {}", host, port, database, sslEnabled);
        
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration(host, port);
        config.setDatabase(database);
        if (password != null && !password.isEmpty()) {
            config.setPassword(password);
        }

        LettuceClientConfiguration clientConfig = LettuceClientConfiguration.builder()
                .commandTimeout(Duration.ofMillis(Long.parseLong(environment.getProperty("spring.redis.timeout", "10000"))))
                .build();

        if (sslEnabled) {
            clientConfig = LettuceClientConfiguration.builder()
                    .commandTimeout(Duration.ofMillis(Long.parseLong(environment.getProperty("spring.redis.timeout", "10000"))))
                    .useSsl()
                    .build();
        }
        
        return new LettuceConnectionFactory(config, clientConfig);
    }

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        log.info("Initializing RedisTemplate with connection factory");
        
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new GenericJackson2JsonRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(new GenericJackson2JsonRedisSerializer());
        template.afterPropertiesSet();
        
        return template;
    }

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        log.info("Initializing RedisCacheManager with connection factory");
        
        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(10))
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(new GenericJackson2JsonRedisSerializer()))
                .disableCachingNullValues();

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(config)
                .build();
    }
} 