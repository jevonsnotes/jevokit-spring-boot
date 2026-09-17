package io.github.jevonsnotes.jevokit.redis.config;

import cn.hutool.extra.spring.SpringUtil;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RedissonClient;
import org.redisson.codec.JsonJacksonCodec;
import org.redisson.spring.starter.RedissonAutoConfigurationCustomizer;
import org.redisson.spring.starter.RedissonAutoConfigurationV2;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachingConfigurerSupport;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.cache.interceptor.KeyGenerator;
import org.springframework.cache.interceptor.LoggingCacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.*;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import io.github.jevonsnotes.jevokit.redis.cache.GlobalCacheKeyPrefix;
import io.github.jevonsnotes.jevokit.redis.cache.generator.extend.GlobalKeyGenerator;
import io.github.jevonsnotes.jevokit.redis.cache.generator.extend.UserKeyGenerator;
import io.github.jevonsnotes.jevokit.redis.cache.generator.extend.rocketmq.RocketMqV5KeyGenerator;
import io.github.jevonsnotes.jevokit.redis.cache.manager.JevokitRedisCacheManager;
import io.github.jevonsnotes.jevokit.redis.lock.aspect.LockAspect;
import io.github.jevonsnotes.jevokit.redis.token.delegate.TokenDelegate;
import io.github.jevonsnotes.jevokit.redis.token.strategy.DefaultTokenStrategy;
import io.github.jevonsnotes.jevokit.redis.util.RedisLock;

/**
 * redis 增强自动装配：分布式锁（RedisLock + @Lock 切面）、缓存体系（CacheManager 支持
 * "name#expire=秒"过期语法与随机 TTL 防雪崩、全局/用户双 Key 生成器）、token 策略 Bean。
 *
 * <p>关键约束：@AutoConfigureBefore(RedissonAutoConfigurationV2)——必须在 Redisson 官方
 * 装配前注册 redissonAutoConfigurationCustomizer（改 JsonJacksonCodec、关从节点同步检查）
 * 才能生效，次序由注解显式锚定，不依赖字母序。
 */
@Configuration
@AutoConfigureBefore({RedissonAutoConfigurationV2.class})
public class JevokitRedisConfiguration {

    /************************ redis lock *****************************/
    @Bean
    public RedisLock RedisLock(RedissonClient redissonClient) {
        return new RedisLock(redissonClient);
    }

    /**
     * 方法级分布式锁
     *
     * @return
     */
    @Bean
    public LockAspect lockAspect() {
        return new LockAspect();
    }

    /**
     * 自定义redisson编码
     *
     * @return
     */
    @Bean
    public RedissonAutoConfigurationCustomizer redissonAutoConfigurationCustomizer() {
        return configuration -> {
            configuration.setCodec(JsonJacksonCodec.INSTANCE);
            configuration.setCheckLockSyncedSlaves(false);//北中心各节点分别存储不同数据，非主从模式，不需要检查从节点同步状态
        };
    }

    /************************ redis cache *****************************/

    /**
     * 初始化 RedisTemplate
     *
     * @param redisConnectionFactory
     * @return
     */
    @Bean
    public RedisTemplate<Object, Object> redisTemplate(RedisConnectionFactory redisConnectionFactory) {
        RedisTemplate<Object, Object> redisTemplate = new RedisTemplate<>();
        redisTemplate.setConnectionFactory(redisConnectionFactory);

        // 使用Jackson2JsonRedisSerialize 替换默认序列化
        Jackson2JsonRedisSerializer jackson2JsonRedisSerializer = new Jackson2JsonRedisSerializer(getObjectMapper(),Object.class);

//        jackson2JsonRedisSerializer.setObjectMapper();

        // 设置value的序列化规则和 key的序列化规则
        redisTemplate.setValueSerializer(jackson2JsonRedisSerializer);
        redisTemplate.setKeySerializer(new StringRedisSerializer());

        redisTemplate.setHashKeySerializer(jackson2JsonRedisSerializer);
        redisTemplate.setHashValueSerializer(jackson2JsonRedisSerializer);

        redisTemplate.setDefaultSerializer(jackson2JsonRedisSerializer);
        redisTemplate.setEnableDefaultSerializer(true);
        redisTemplate.afterPropertiesSet();
        return redisTemplate;

    }


    private ObjectMapper getObjectMapper() {
        ObjectMapper om = new ObjectMapper();
        om.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
        try {
            Class.forName("com.fasterxml.jackson.databind.jsontype.PolymorphicTypeValidator");
            om.activateDefaultTyping(om.getPolymorphicTypeValidator(), ObjectMapper.DefaultTyping.NON_FINAL);
        } catch (ClassNotFoundException e) {
            om.enableDefaultTyping(ObjectMapper.DefaultTyping.NON_FINAL);
        }
        return om;
    }

    @Bean
    public GlobalCacheKeyPrefix globalCacheKeyPrefix() {
        return new GlobalCacheKeyPrefix();
    }

    /**
     * redisson缓存配置
     * @param globalCacheKeyPrefix
     * @return
     */
    @Bean
    public RedisCacheConfiguration redisCacheConfiguration(GlobalCacheKeyPrefix globalCacheKeyPrefix) {
        RedisCacheConfiguration configuration = RedisCacheConfiguration.defaultCacheConfig();

        Jackson2JsonRedisSerializer<Object> jackson2JsonRedisSerializer = new Jackson2JsonRedisSerializer<Object>(getObjectMapper(),Object.class);

        configuration = configuration.computePrefixWith(globalCacheKeyPrefix)
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(jackson2JsonRedisSerializer));
//				.entryTtl(Duration.ofSeconds(30));//不设置失效时间，默认使用CustomRedisCacheWriter随机数
        return configuration;
    }

    /**
     * redisson 用户级缓存key生成器
     * @return
     */
    @Bean
    public UserKeyGenerator userKeyGenerator() {
        return new UserKeyGenerator();
    }
    /**
     * redisson 用户级缓存key生成器
     * @return
     */
    @Bean
    public RocketMqV5KeyGenerator rocketMqKeyGenerator() {
        return new RocketMqV5KeyGenerator();
    }

    /**
     * redisson 全局缓存key生成器
     * @return
     */
    @Bean
    public GlobalKeyGenerator globalKeyGenerator() {
        return new GlobalKeyGenerator();
    }

    @EnableCaching
    @RequiredArgsConstructor
    public class CacheRedisConfig extends CachingConfigurerSupport {
        private final StringRedisTemplate redisTemplate;
        private final RedisCacheConfiguration redisCacheConfiguration;

        @Override
        public CacheManager cacheManager() {

            RedisConnectionFactory connectionFactory = redisTemplate.getConnectionFactory();
            // 上面实现的缓存读写
//            CustomRedisCacheWriter customRedisCacheWriter = new CustomRedisCacheWriter(connectionFactory);
//            CacheManager cm = new RedisCacheManager(customRedisCacheWriter, redisCacheConfiguration);
            RedisCacheWriter redisCacheWriter = RedisCacheWriter.nonLockingRedisCacheWriter(connectionFactory);
            return new JevokitRedisCacheManager(redisCacheWriter,redisCacheConfiguration);
        }

        /**
         * Redis报错输出日志，降级直连数据库
         * @return LoggingCacheErrorHandler
         */
        @Override
        public CacheErrorHandler errorHandler() {
            return new LoggingCacheErrorHandler();
        }

        /**
         * 默认缓存key生成器
         * @return
         */
        @Override
        public KeyGenerator keyGenerator() {
            return SpringUtil.getBean(GlobalKeyGenerator.class);
        }


        /**
         * 对hash类型的数据操作
         *
         * @param redisTemplate
         * @return
         */
        @Bean
        public HashOperations<String, String, String> hashOperations(StringRedisTemplate redisTemplate) {
            return redisTemplate.opsForHash();
        }

        /**
         * 对redis字符串类型数据操作
         *
         * @param redisTemplate
         * @return
         */
        @Bean
        public ValueOperations<String, String> valueOperations(StringRedisTemplate redisTemplate) {
            return redisTemplate.opsForValue();
        }

        /**
         * 对链表类型的数据操作
         *
         * @param redisTemplate
         * @return
         */
        @Bean
        public ListOperations<String, String> listOperations(StringRedisTemplate redisTemplate) {
            return redisTemplate.opsForList();
        }

        /**
         * 对无序集合类型的数据操作
         *
         * @param redisTemplate
         * @return
         */
        @Bean
        public SetOperations<String, String> setOperations(StringRedisTemplate redisTemplate) {
            return redisTemplate.opsForSet();
        }

        /**
         * 对有序集合类型的数据操作
         *
         * @param redisTemplate
         * @return
         */
        @Bean
        public ZSetOperations<String, String> zSetOperations(StringRedisTemplate redisTemplate) {
            return redisTemplate.opsForZSet();
        }
    }

    /************************ redis token *****************************/
    /**
     * token管理代理
     * @param redisTemplate
     * @return
     */
    @Bean
    @ConditionalOnMissingBean(TokenDelegate.class)
    public TokenDelegate tokenDelegate(RedisTemplate<Object, Object> redisTemplate) {
        return new TokenDelegate(redisTemplate);
    }

    /**
     * 默认token管理策略
     * @param redisTemplate
     * @return
     */
    @Bean
    public DefaultTokenStrategy defaultTokenStrategy(RedisTemplate<Object, Object> redisTemplate) {
        return new DefaultTokenStrategy(redisTemplate);
    }

}
