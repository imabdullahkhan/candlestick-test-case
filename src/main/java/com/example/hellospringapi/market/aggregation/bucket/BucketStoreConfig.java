package com.example.hellospringapi.market.aggregation.bucket;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class BucketStoreConfig {

    @Bean
    @Primary
    @ConditionalOnMissingBean(RedisBucketStore.class)
    public BucketStore inMemoryBucketStore() {
        return new InMemoryBucketStore();
    }
}
