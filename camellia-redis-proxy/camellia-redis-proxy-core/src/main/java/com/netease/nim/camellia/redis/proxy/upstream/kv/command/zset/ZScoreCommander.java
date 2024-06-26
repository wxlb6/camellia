package com.netease.nim.camellia.redis.proxy.upstream.kv.command.zset;

import com.netease.nim.camellia.redis.proxy.command.Command;
import com.netease.nim.camellia.redis.proxy.enums.RedisCommand;
import com.netease.nim.camellia.redis.proxy.reply.BulkReply;
import com.netease.nim.camellia.redis.proxy.reply.ErrorReply;
import com.netease.nim.camellia.redis.proxy.reply.Reply;
import com.netease.nim.camellia.redis.proxy.upstream.kv.command.Commander;
import com.netease.nim.camellia.redis.proxy.upstream.kv.command.CommanderConfig;
import com.netease.nim.camellia.redis.proxy.upstream.kv.domain.Index;
import com.netease.nim.camellia.redis.proxy.upstream.kv.kv.KeyValue;
import com.netease.nim.camellia.redis.proxy.upstream.kv.meta.EncodeVersion;
import com.netease.nim.camellia.redis.proxy.upstream.kv.meta.KeyMeta;
import com.netease.nim.camellia.redis.proxy.upstream.kv.meta.KeyType;
import com.netease.nim.camellia.redis.proxy.upstream.kv.utils.BytesUtils;
import com.netease.nim.camellia.redis.proxy.util.Utils;

import java.nio.charset.StandardCharsets;

/**
 * ZSCORE key member
 * <p>
 * Created by caojiajun on 2024/5/15
 */
public class ZScoreCommander extends Commander {

    private static final byte[] script = ("local arg = redis.call('exists', KEYS[1]);\n" +
            "if tonumber(arg) == 1 then\n" +
            "\tlocal ret = redis.call('zscore', KEYS[1], ARG[1]);\n" +
            "\tredis.call('pexpire', KEYS[1], ARGV[2]);\n" +
            "\treturn {'1', ret};\n" +
            "end\n" +
            "return {'2'};").getBytes(StandardCharsets.UTF_8);

    public ZScoreCommander(CommanderConfig commanderConfig) {
        super(commanderConfig);
    }

    @Override
    public RedisCommand redisCommand() {
        return RedisCommand.ZSCORE;
    }

    @Override
    protected boolean parse(Command command) {
        byte[][] objects = command.getObjects();
        return objects.length == 3;
    }

    @Override
    protected Reply execute(Command command) {
        byte[][] objects = command.getObjects();
        byte[] key = objects[1];
        KeyMeta keyMeta = keyMetaServer.getKeyMeta(key);
        if (keyMeta == null) {
            return BulkReply.NIL_REPLY;
        }
        if (keyMeta.getKeyType() != KeyType.zset) {
            return ErrorReply.WRONG_TYPE;
        }

        byte[] member = objects[2];

        EncodeVersion encodeVersion = keyMeta.getEncodeVersion();
        if (encodeVersion == EncodeVersion.version_0) {
            return zscoreFromKv(keyMeta, key, member);
        }

        byte[] cacheKey = keyDesign.cacheKey(keyMeta, key);

        if (encodeVersion == EncodeVersion.version_1) {
            Reply reply = checkCache(script, cacheKey, new byte[][]{member, zsetRangeCacheMillis()});
            if (reply != null) {
                return reply;
            }
            return zscoreFromKv(keyMeta, key, member);
        }

        if (encodeVersion == EncodeVersion.version_2) {
            Index index = Index.fromRaw(member);
            Reply reply = checkCache(script, cacheKey, new byte[][]{index.getRef(), zsetRangeCacheMillis()});
            if (reply != null) {
                return reply;
            }
            return zscoreFromKv(keyMeta, key, member);
        }

        if (encodeVersion == EncodeVersion.version_3) {
            Index index = Index.fromRaw(member);
            return sync(storeRedisTemplate.sendCommand(new Command(new byte[][]{RedisCommand.ZSCORE.raw(), cacheKey, index.getRef()})));
        }

        return ErrorReply.INTERNAL_ERROR;
    }

    private Reply zscoreFromKv(KeyMeta keyMeta, byte[] key, byte[] member) {
        byte[] subKey1 = keyDesign.zsetMemberSubKey1(keyMeta, key, member);
        KeyValue keyValue = kvClient.get(subKey1);
        if (keyValue == null || keyValue.getValue() == null) {
            return BulkReply.NIL_REPLY;
        }
        double score = BytesUtils.toDouble(keyValue.getValue());
        return new BulkReply(Utils.doubleToBytes(score));
    }

    protected final byte[] zsetRangeCacheMillis() {
        return Utils.stringToBytes(String.valueOf(cacheConfig.zsetRangeCacheMillis()));
    }
}
