package com.netease.nim.camellia.redis.proxy.upstream.kv.command;


import com.netease.nim.camellia.redis.proxy.upstream.kv.domain.CacheConfig;
import com.netease.nim.camellia.redis.proxy.upstream.kv.domain.KeyDesign;
import com.netease.nim.camellia.redis.proxy.upstream.kv.domain.KvConfig;
import com.netease.nim.camellia.redis.proxy.upstream.kv.gc.KvGcExecutor;
import com.netease.nim.camellia.redis.proxy.upstream.kv.kv.KVClient;
import com.netease.nim.camellia.redis.proxy.upstream.kv.meta.KeyMetaServer;

/**
 * Created by caojiajun on 2024/4/8
 */
public class CommanderConfig {

    private final KVClient kvClient;
    private final KeyDesign keyDesign;
    private final CacheConfig cacheConfig;
    private final KvConfig kvConfig;
    private final KeyMetaServer keyMetaServer;
    private final RedisTemplate cacheRedisTemplate;
    private final RedisTemplate storeRedisTemplate;
    private final KvGcExecutor gcExecutor;

    public CommanderConfig(KVClient kvClient, KeyDesign keyDesign, CacheConfig cacheConfig,
                           KvConfig kvConfig, KeyMetaServer keyMetaServer,
                           RedisTemplate cacheRedisTemplate, RedisTemplate storeRedisTemplate, KvGcExecutor gcExecutor) {
        this.kvClient = kvClient;
        this.keyDesign = keyDesign;
        this.cacheConfig = cacheConfig;
        this.kvConfig = kvConfig;
        this.keyMetaServer = keyMetaServer;
        this.cacheRedisTemplate = cacheRedisTemplate;
        this.storeRedisTemplate = storeRedisTemplate;
        this.gcExecutor = gcExecutor;
    }

    public KVClient getKvClient() {
        return kvClient;
    }

    public KeyDesign getKeyDesign() {
        return keyDesign;
    }

    public CacheConfig getCacheConfig() {
        return cacheConfig;
    }

    public KvConfig getKvConfig() {
        return kvConfig;
    }

    public KeyMetaServer getKeyMetaServer() {
        return keyMetaServer;
    }

    public RedisTemplate getCacheRedisTemplate() {
        return cacheRedisTemplate;
    }

    public RedisTemplate getStoreRedisTemplate() {
        return storeRedisTemplate;
    }

    public KvGcExecutor getGcExecutor() {
        return gcExecutor;
    }
}

