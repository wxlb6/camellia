package com.netease.nim.camellia.mq.isolation.config;

import com.netease.nim.camellia.core.client.env.ThreadContextSwitchStrategy;
import com.netease.nim.camellia.mq.isolation.MqIsolationController;
import com.netease.nim.camellia.mq.isolation.executor.MsgHandler;
import com.netease.nim.camellia.mq.isolation.mq.MqSender;

/**
 * Created by caojiajun on 2024/2/6
 */
public class ConsumerConfig {

    private int threads = 200;
    private String namespace;
    private MqIsolationController controller;
    private MqSender mqSender;
    private MsgHandler msgHandler;
    private ThreadContextSwitchStrategy strategy = new ThreadContextSwitchStrategy.Default();
    private int reportIntervalSeconds = 10;
    private int reloadConfigIntervalSeconds = 30;
    private double maxPermitPercent = 0.5;

    public int getThreads() {
        return threads;
    }

    public void setThreads(int threads) {
        this.threads = threads;
    }

    public String getNamespace() {
        return namespace;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    public MqIsolationController getController() {
        return controller;
    }

    public void setController(MqIsolationController controller) {
        this.controller = controller;
    }

    public MqSender getMqSender() {
        return mqSender;
    }

    public void setMqSender(MqSender mqSender) {
        this.mqSender = mqSender;
    }

    public MsgHandler getMsgHandler() {
        return msgHandler;
    }

    public void setMsgHandler(MsgHandler msgHandler) {
        this.msgHandler = msgHandler;
    }

    public ThreadContextSwitchStrategy getStrategy() {
        return strategy;
    }

    public void setStrategy(ThreadContextSwitchStrategy strategy) {
        this.strategy = strategy;
    }

    public int getReportIntervalSeconds() {
        return reportIntervalSeconds;
    }

    public void setReportIntervalSeconds(int reportIntervalSeconds) {
        this.reportIntervalSeconds = reportIntervalSeconds;
    }

    public int getReloadConfigIntervalSeconds() {
        return reloadConfigIntervalSeconds;
    }

    public void setReloadConfigIntervalSeconds(int reloadConfigIntervalSeconds) {
        this.reloadConfigIntervalSeconds = reloadConfigIntervalSeconds;
    }

    public double getMaxPermitPercent() {
        return maxPermitPercent;
    }

    public void setMaxPermitPercent(double maxPermitPercent) {
        this.maxPermitPercent = maxPermitPercent;
    }
}
