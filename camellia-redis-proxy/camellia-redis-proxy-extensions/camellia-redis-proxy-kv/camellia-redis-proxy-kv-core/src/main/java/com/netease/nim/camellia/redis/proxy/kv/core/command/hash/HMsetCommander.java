package com.netease.nim.camellia.redis.proxy.kv.core.command.hash;

import com.netease.nim.camellia.redis.proxy.command.Command;
import com.netease.nim.camellia.redis.proxy.enums.RedisCommand;
import com.netease.nim.camellia.redis.proxy.kv.core.command.CommanderConfig;
import com.netease.nim.camellia.redis.proxy.reply.Reply;
import com.netease.nim.camellia.redis.proxy.reply.StatusReply;

/**
 * HMSET key field value [field value ...]
 * <p>
 * Created by caojiajun on 2024/4/11
 */
public class HMsetCommander extends HSetCommander {

    public HMsetCommander(CommanderConfig commanderConfig) {
        super(commanderConfig);
    }

    @Override
    public RedisCommand redisCommand() {
        return RedisCommand.HMSET;
    }

    @Override
    protected Reply execute(Command command) {
        super.execute(command);
        return StatusReply.OK;
    }
}