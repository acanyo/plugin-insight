package com.xhhao.insight.config;

public class CollectionSettings {

    private int samplingSeconds = 15;
    private int systemRetentionDays = 7;
    private int aggregateRetentionDays = 30;
    private int eventRetentionDays = 30;
    private long slowRequestMillis = 1_000;
    private int maxDatabaseMegabytes = 512;
    private boolean pluginResourceSamplingEnabled = true;
    private int pluginResourceMaxThreads = 512;

    public int getSamplingSeconds() {
        return samplingSeconds;
    }

    public void setSamplingSeconds(int samplingSeconds) {
        this.samplingSeconds = samplingSeconds;
    }

    public int getSystemRetentionDays() {
        return systemRetentionDays;
    }

    public void setSystemRetentionDays(int systemRetentionDays) {
        this.systemRetentionDays = systemRetentionDays;
    }

    public int getAggregateRetentionDays() {
        return aggregateRetentionDays;
    }

    public void setAggregateRetentionDays(int aggregateRetentionDays) {
        this.aggregateRetentionDays = aggregateRetentionDays;
    }

    public int getEventRetentionDays() {
        return eventRetentionDays;
    }

    public void setEventRetentionDays(int eventRetentionDays) {
        this.eventRetentionDays = eventRetentionDays;
    }

    public long getSlowRequestMillis() {
        return slowRequestMillis;
    }

    public void setSlowRequestMillis(long slowRequestMillis) {
        this.slowRequestMillis = slowRequestMillis;
    }

    public int getMaxDatabaseMegabytes() {
        return maxDatabaseMegabytes;
    }

    public void setMaxDatabaseMegabytes(int maxDatabaseMegabytes) {
        this.maxDatabaseMegabytes = maxDatabaseMegabytes;
    }

    public boolean isPluginResourceSamplingEnabled() {
        return pluginResourceSamplingEnabled;
    }

    public void setPluginResourceSamplingEnabled(boolean pluginResourceSamplingEnabled) {
        this.pluginResourceSamplingEnabled = pluginResourceSamplingEnabled;
    }

    public int getPluginResourceMaxThreads() {
        return pluginResourceMaxThreads;
    }

    public void setPluginResourceMaxThreads(int pluginResourceMaxThreads) {
        this.pluginResourceMaxThreads = pluginResourceMaxThreads;
    }
}
