package com.heima.englishnova.importservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "english-nova.import")
public class EnglishNovaProperties {

    private String exchange = "english-nova.import";
    private String queue = "english-nova.import.queue";
    private String routingKey = "english.nova.import.requested";
    private String indexedRoutingKey = "english.nova.wordbook.imported";

    public String getExchange() {
        return exchange;
    }

    public void setExchange(String exchange) {
        this.exchange = exchange;
    }

    public String getQueue() {
        return queue;
    }

    public void setQueue(String queue) {
        this.queue = queue;
    }

    public String getRoutingKey() {
        return routingKey;
    }

    public void setRoutingKey(String routingKey) {
        this.routingKey = routingKey;
    }

    public String getIndexedRoutingKey() {
        return indexedRoutingKey;
    }

    public void setIndexedRoutingKey(String indexedRoutingKey) {
        this.indexedRoutingKey = indexedRoutingKey;
    }
}
