package com.netease.nim.camellia.redis.proxy.upstream.kv.command.hash;

import com.netease.nim.camellia.redis.proxy.command.Command;
import com.netease.nim.camellia.redis.proxy.enums.RedisCommand;
import com.netease.nim.camellia.redis.proxy.monitor.KVCacheMonitor;
import com.netease.nim.camellia.redis.proxy.reply.ErrorReply;
import com.netease.nim.camellia.redis.proxy.reply.IntegerReply;
import com.netease.nim.camellia.redis.proxy.reply.Reply;
import com.netease.nim.camellia.redis.proxy.upstream.kv.command.Commander;
import com.netease.nim.camellia.redis.proxy.upstream.kv.command.CommanderConfig;
import com.netease.nim.camellia.redis.proxy.upstream.kv.meta.EncodeVersion;
import com.netease.nim.camellia.redis.proxy.upstream.kv.meta.KeyMeta;
import com.netease.nim.camellia.redis.proxy.upstream.kv.meta.KeyType;
import com.netease.nim.camellia.redis.proxy.upstream.kv.utils.BytesUtils;

/**
 * HLEN key
 * <p>
 * Created by caojiajun on 2024/4/11
 */
public class HLenCommander extends Commander {

    public HLenCommander(CommanderConfig commanderConfig) {
        super(commanderConfig);
    }

    @Override
    public RedisCommand redisCommand() {
        return RedisCommand.HLEN;
    }

    @Override
    protected boolean parse(Command command) {
        byte[][] objects = command.getObjects();
        return objects.length == 2;
    }

    @Override
    protected Reply execute(Command command) {
        byte[][] objects = command.getObjects();
        byte[] key = objects[1];
        KeyMeta keyMeta = keyMetaServer.getKeyMeta(key);
        if (keyMeta == null) {
            return IntegerReply.REPLY_0;
        }
        if (keyMeta.getKeyType() != KeyType.hash) {
            return ErrorReply.WRONG_TYPE;
        }
        EncodeVersion encodeVersion = keyMeta.getEncodeVersion();
        if (encodeVersion == EncodeVersion.version_0 || encodeVersion == EncodeVersion.version_2) {
            int size = BytesUtils.toInt(keyMeta.getExtra());
            return IntegerReply.parse(size);
        } else if (encodeVersion == EncodeVersion.version_1) {
            byte[] cacheKey = keyDesign.cacheKey(keyMeta, key);
            if (cacheConfig.isHashLocalCacheEnable()) {
                long hlen = cacheConfig.getHashLRUCache().hlen(cacheKey);
                if (hlen >= 0) {
                    KVCacheMonitor.localCache(cacheConfig.getNamespace(), redisCommand().strRaw());
                    return IntegerReply.parse(hlen);
                }
            }
            KVCacheMonitor.kvStore(cacheConfig.getNamespace(), redisCommand().strRaw());
            long size = getSizeFromKv(keyMeta, key);
            return IntegerReply.parse(size);
        } else if (encodeVersion == EncodeVersion.version_3) {
            byte[] cacheKey = keyDesign.cacheKey(keyMeta, key);
            if (cacheConfig.isHashLocalCacheEnable()) {
                long hlen = cacheConfig.getHashLRUCache().hlen(cacheKey);
                if (hlen >= 0) {
                    KVCacheMonitor.localCache(cacheConfig.getNamespace(), redisCommand().strRaw());
                    return IntegerReply.parse(hlen);
                }
            }
            Reply reply = sync(cacheRedisTemplate.sendCommand(new Command(new byte[][]{RedisCommand.HLEN.raw(), cacheKey})));
            if (reply instanceof IntegerReply) {
                Long size = ((IntegerReply) reply).getInteger();
                if (size != null && size > 0) {
                    KVCacheMonitor.redisCache(cacheConfig.getNamespace(), redisCommand().strRaw());
                    return reply;
                }
            }
            KVCacheMonitor.kvStore(cacheConfig.getNamespace(), redisCommand().strRaw());
            long size = getSizeFromKv(keyMeta, key);
            return IntegerReply.parse(size);
        } else {
            return ErrorReply.INTERNAL_ERROR;
        }
    }

    private long getSizeFromKv(KeyMeta keyMeta, byte[] key) {
        byte[] startKey = keyDesign.hashFieldSubKey(keyMeta, key, new byte[0]);
        return kvClient.countByPrefix(startKey, startKey, false);
    }
}
