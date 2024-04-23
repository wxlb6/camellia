package com.netease.nim.camellia.redis.proxy.upstream.kv;

import com.netease.nim.camellia.core.api.CamelliaApi;
import com.netease.nim.camellia.core.api.CamelliaApiUtil;
import com.netease.nim.camellia.core.model.Resource;
import com.netease.nim.camellia.core.model.ResourceTable;
import com.netease.nim.camellia.core.util.ReadableResourceTableUtil;
import com.netease.nim.camellia.redis.base.resource.RedisKvResource;
import com.netease.nim.camellia.redis.proxy.command.Command;
import com.netease.nim.camellia.redis.proxy.conf.ProxyDynamicConf;
import com.netease.nim.camellia.redis.proxy.enums.RedisCommand;
import com.netease.nim.camellia.redis.proxy.netty.GlobalRedisProxyEnv;
import com.netease.nim.camellia.redis.proxy.reply.*;
import com.netease.nim.camellia.redis.proxy.upstream.IUpstreamClient;
import com.netease.nim.camellia.redis.proxy.upstream.RedisProxyEnv;
import com.netease.nim.camellia.redis.proxy.upstream.UpstreamRedisClientTemplate;
import com.netease.nim.camellia.redis.proxy.upstream.kv.command.CommanderConfig;
import com.netease.nim.camellia.redis.proxy.upstream.kv.command.Commanders;
import com.netease.nim.camellia.redis.proxy.upstream.kv.command.RedisKvClientExecutor;
import com.netease.nim.camellia.redis.proxy.upstream.kv.command.RedisTemplate;
import com.netease.nim.camellia.redis.proxy.upstream.kv.conf.RedisKvConf;
import com.netease.nim.camellia.redis.proxy.upstream.kv.domain.CacheConfig;
import com.netease.nim.camellia.redis.proxy.upstream.kv.domain.KeyStruct;
import com.netease.nim.camellia.redis.proxy.upstream.kv.domain.KvConfig;
import com.netease.nim.camellia.redis.proxy.upstream.kv.exception.KvException;
import com.netease.nim.camellia.redis.proxy.upstream.kv.gc.KvGcExecutor;
import com.netease.nim.camellia.redis.proxy.upstream.kv.kv.KVClient;
import com.netease.nim.camellia.redis.proxy.upstream.kv.meta.DefaultKeyMetaServer;
import com.netease.nim.camellia.redis.proxy.upstream.kv.meta.KeyMetaServer;
import com.netease.nim.camellia.redis.proxy.upstream.utils.CompletableFutureUtils;
import com.netease.nim.camellia.redis.proxy.util.BeanInitUtils;
import com.netease.nim.camellia.redis.proxy.util.Utils;
import com.netease.nim.camellia.tools.executor.CamelliaHashedExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Created by caojiajun on 2024/4/17
 */
public class RedisKvClient implements IUpstreamClient {

    private static final Logger logger = LoggerFactory.getLogger(RedisKvClient.class);

    private CamelliaHashedExecutor executor;
    private final Resource resource;
    private final String namespace;
    private Commanders commanders;

    public RedisKvClient(RedisKvResource resource) {
        this.resource = resource;
        this.namespace = resource.getNamespace();
    }

    @Override
    public void sendCommand(int db, List<Command> commands, List<CompletableFuture<Reply>> futureList) {
        if (db > 0) {
            for (CompletableFuture<Reply> future : futureList) {
                future.complete(ErrorReply.DB_INDEX_OUT_OF_RANGE);
            }
            return;
        }
        for (int i=0; i<commands.size(); i++) {
            Command command = commands.get(i);
            CompletableFuture<Reply> future = futureList.get(i);
            RedisCommand redisCommand = command.getRedisCommand();
            if (redisCommand.getCommandKeyType() == RedisCommand.CommandKeyType.None) {
                sendNoneKeyCommand(redisCommand, command, future);
            } else {
                List<byte[]> keys = command.getKeys();
                if (keys.size() == 1) {
                    byte[] key = keys.get(0);
                    sendCommand(key, command, future);
                } else {
                    sendMultiKeyCommand(redisCommand, command, future);
                }
            }
        }
    }

    @Override
    public void start() {
        this.executor = RedisKvClientExecutor.getInstance().getExecutor();
        KVClient kvClient = initKVClient();
        KeyStruct keyStruct = new KeyStruct(namespace.getBytes(StandardCharsets.UTF_8));
        KvConfig kvConfig = new KvConfig(namespace);
        KvGcExecutor gcExecutor = new KvGcExecutor(kvClient, keyStruct, kvConfig);
        gcExecutor.start();
        this.commanders = initCommanders(kvClient, keyStruct, kvConfig, gcExecutor);
        logger.info("RedisKvClient start success, resource = {}", getResource());
    }

    private void sendNoneKeyCommand(RedisCommand redisCommand, Command command, CompletableFuture<Reply> future) {
        if (redisCommand == RedisCommand.PING) {
            future.complete(StatusReply.PONG);
        } else if (redisCommand == RedisCommand.ECHO) {
            byte[][] objects = command.getObjects();
            if (objects.length == 2) {
                future.complete(new BulkReply(objects[1]));
            } else {
                future.complete(ErrorReply.argNumWrong(redisCommand));
            }
        } else {
            future.complete(ErrorReply.NOT_SUPPORT);
        }
    }

    private void sendMultiKeyCommand(RedisCommand redisCommand, Command command, CompletableFuture<Reply> future) {
        List<byte[]> keys = command.getKeys();
        if (redisCommand == RedisCommand.DEL || redisCommand == RedisCommand.UNLINK || redisCommand == RedisCommand.EXISTS) {
            List<CompletableFuture<Reply>> futures = new ArrayList<>(keys.size());
            for (byte[] key : keys) {
                CompletableFuture<Reply> f = new CompletableFuture<>();
                sendCommand(key, new Command(new byte[][]{redisCommand.raw(), key}), f);
                futures.add(f);
            }
            CompletableFutureUtils.allOf(futures).thenAccept(replies -> future.complete(Utils.mergeIntegerReply(replies)));
        } else if (redisCommand == RedisCommand.MGET) {
            List<CompletableFuture<Reply>> futures = new ArrayList<>(keys.size());
            for (byte[] key : keys) {
                CompletableFuture<Reply> f = new CompletableFuture<>();
                sendCommand(key, new Command(new byte[][]{RedisCommand.GET.raw(), key}), f);
                futures.add(f);
            }
            CompletableFutureUtils.allOf(futures).thenAccept(replies -> {
                for (Reply reply : replies) {
                    if (reply instanceof ErrorReply) {
                        future.complete(reply);
                        return;
                    }
                }
                future.complete(new MultiBulkReply(replies.toArray(new Reply[0])));
            });
        } else {
            future.complete(ErrorReply.NOT_SUPPORT);
        }
    }

    private void sendCommand(byte[] key, Command command, CompletableFuture<Reply> future) {
        try {
            executor.submit(key, () -> {
                try {
                    Reply reply = commanders.execute(command);
                    future.complete(reply);
                } catch (Exception e) {
                    future.complete(ErrorReply.NOT_AVAILABLE);
                }
            });
        } catch (Exception e) {
            future.complete(ErrorReply.TOO_BUSY);
        }
    }

    private KVClient initKVClient() {
        String className = ProxyDynamicConf.getString("kv.client.class.name", null);
        return (KVClient) GlobalRedisProxyEnv.getProxyBeanFactory().getBean(BeanInitUtils.parseClass(className));
    }

    private Commanders initCommanders(KVClient kvClient, KeyStruct keyStruct, KvConfig kvConfig, KvGcExecutor gcExecutor) {
        boolean metaCacheEnable = RedisKvConf.getBoolean(namespace, "kv.meta.cache.enable", true);
        boolean valueCacheEnable = RedisKvConf.getBoolean(namespace, "kv.value.cache.enable", true);
        CacheConfig cacheConfig = new CacheConfig(namespace, metaCacheEnable, valueCacheEnable);

        RedisTemplate metaCacheRedisTemplate = null;
        if (metaCacheEnable) {
            metaCacheRedisTemplate = initRedisTemplate("key.meta.cache");
        }
        KeyMetaServer keyMetaServer = new DefaultKeyMetaServer(kvClient, metaCacheRedisTemplate, keyStruct, cacheConfig);

        RedisTemplate valueCacheRedisTemplate = null;
        if (valueCacheEnable) {
            valueCacheRedisTemplate = initRedisTemplate("key.value.cache");
        }
        CommanderConfig commanderConfig = new CommanderConfig(kvClient, keyStruct, cacheConfig, kvConfig, keyMetaServer, valueCacheRedisTemplate, gcExecutor);

        return new Commanders(commanderConfig);
    }

    private RedisTemplate initRedisTemplate(String key) {
        String type = RedisKvConf.getString(namespace, key + ".config.type", "local");
        if (type.equalsIgnoreCase("local")) {
            String url = RedisKvConf.getString(namespace, key + ".redis.url", null);
            ResourceTable resourceTable = ReadableResourceTableUtil.parseTable(url);
            RedisProxyEnv env = GlobalRedisProxyEnv.getClientTemplateFactory().getEnv();
            UpstreamRedisClientTemplate template = new UpstreamRedisClientTemplate(env, resourceTable);
            return new RedisTemplate(template);
        } else if (type.equalsIgnoreCase("remote")) {
            String dashboardUrl = RedisKvConf.getString(namespace, key + ".camellia.dashboard.url", null);
            if (dashboardUrl == null) {
                throw new KvException("illegal dashboardUrl");
            }
            boolean monitorEnable = RedisKvConf.getBoolean(namespace, key + ".camellia.dashboard.monitor.enable", true);
            long checkIntervalMillis = RedisKvConf.getLong(namespace, key + ".camellia.dashboard.check.interval.millis", 3000L);
            long bid = RedisKvConf.getLong(namespace, key + ".bid", -1);
            String bgroup = RedisKvConf.getString(namespace, key + ".bgroup", "default");
            if (bid <= 0) {
                throw new KvException("illegal bid");
            }
            RedisProxyEnv env = GlobalRedisProxyEnv.getClientTemplateFactory().getEnv();
            CamelliaApi camelliaApi = CamelliaApiUtil.init(dashboardUrl);
            UpstreamRedisClientTemplate template = new UpstreamRedisClientTemplate(env, camelliaApi, bid, bgroup, monitorEnable, checkIntervalMillis);
            return new RedisTemplate(template);
        } else {
            throw new KvException("init redis template error");
        }
    }

    @Override
    public void preheat() {
        //do nothing
    }

    @Override
    public boolean isValid() {
        return true;
    }

    @Override
    public void shutdown() {
        //do nothing
    }

    @Override
    public Resource getResource() {
        return resource;
    }

    @Override
    public void renew() {
        //do nothing
    }
}