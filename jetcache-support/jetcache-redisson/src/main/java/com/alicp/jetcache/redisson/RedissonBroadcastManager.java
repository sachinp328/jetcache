package com.alicp.jetcache.redisson;

import com.alicp.jetcache.CacheConfigException;
import com.alicp.jetcache.CacheManager;
import com.alicp.jetcache.CacheResult;
import com.alicp.jetcache.support.BroadcastManager;
import com.alicp.jetcache.support.CacheMessage;
import com.alicp.jetcache.support.SquashedLogger;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * Created on 2022/7/12.
 *
 * @author <a href="mailto:jeason1914@qq.com">yangyong</a>
 */
public class RedissonBroadcastManager extends BroadcastManager {
    private static final Logger logger = LoggerFactory.getLogger(RedissonBroadcastManager.class);
    private final RedissonCacheConfig<?, ?> config;
    private final String channel;
    private final RedissonClient client;
    private volatile int subscribeId;

    public RedissonBroadcastManager(final CacheManager cacheManager, final RedissonCacheConfig<?, ?> config) {
        super(cacheManager);
        if (config.getBroadcastChannel() == null) {
            throw new CacheConfigException("BroadcastChannel not set");
        }
        this.config = config;
        this.channel = config.getBroadcastChannel();
        this.client = config.getRedissonClient();
    }

    @Override
    public synchronized void startSubscribe() {
        if (this.subscribeId == 0 && Objects.nonNull(this.channel) && !this.channel.isEmpty()) {
            this.subscribeId = this.client.getTopic(this.channel)
                    .addListener(byte[].class, (channel, msg) -> processNotification(msg, this.config.getValueDecoder()));
        }
    }


    @Override
    public synchronized void close() {
        final int id;
        if ((id = this.subscribeId) > 0 && Objects.nonNull(this.channel)) {
            this.subscribeId = 0;
            try {
                this.client.getTopic(this.channel).removeListener(id);
            } catch (Throwable e) {
                logger.warn("unsubscribe {} fail", this.channel, e);
            }
        }
    }

    @Override
    public CacheResult publish(final CacheMessage cacheMessage) {
        try {
            if (Objects.nonNull(this.channel) && Objects.nonNull(cacheMessage)) {
                final byte[] msg = this.config.getValueEncoder().apply(cacheMessage);
                this.client.getTopic(this.channel).publish(msg);
                return CacheResult.SUCCESS_WITHOUT_MSG;
            }
            return CacheResult.FAIL_WITHOUT_MSG;
        } catch (Throwable e) {
            SquashedLogger.getLogger(logger).error("jetcache publish error", e);
            return new CacheResult(e);
        }
    }
}
