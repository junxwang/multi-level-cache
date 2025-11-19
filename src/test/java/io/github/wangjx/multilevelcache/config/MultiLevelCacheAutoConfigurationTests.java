package io.github.wangjx.multilevelcache.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.assertj.core.api.Assertions;
import io.github.wangjx.multilevelcache.CacheInvalidationService;
import io.github.wangjx.multilevelcache.LockManager;
import io.github.wangjx.multilevelcache.MultiLevelCacheManager;
import io.github.wangjx.multilevelcache.properties.MultiLevelCacheProperties;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;

class MultiLevelCacheAutoConfigurationTests {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withBean(ObjectMapper.class, ObjectMapper::new)
            .withBean(ReactiveRedisConnectionFactory.class,
                    () -> Mockito.mock(ReactiveRedisConnectionFactory.class))
            .withConfiguration(AutoConfigurations.of(MultiLevelCacheReactiveAutoConfiguration.class));

    @Test
    void shouldProvideCacheBeansWhenReactiveRedisFactoryPresent() {
        contextRunner.run(context -> {
            Assertions.assertThat(context).hasSingleBean(MultiLevelCacheProperties.class);
            Assertions.assertThat(context).hasSingleBean(MultiLevelCacheManager.class);
            Assertions.assertThat(context).hasSingleBean(CacheInvalidationService.class);
            Assertions.assertThat(context).hasSingleBean(LockManager.class);
        });
    }

    @Test
    void shouldNotLoadAutoConfigWithoutReactiveRedisFactory() {
        new ApplicationContextRunner()
                .withBean(ObjectMapper.class, ObjectMapper::new)
                .withConfiguration(AutoConfigurations.of(MultiLevelCacheReactiveAutoConfiguration.class))
                .run(context -> Assertions.assertThat(context)
                        .doesNotHaveBean(MultiLevelCacheManager.class));
    }
}

