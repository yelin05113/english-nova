package com.heima.englishnova.importservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * EnglishNova 导入模块配置属性，前缀为 {@code english-nova.import}。
 */
@ConfigurationProperties(prefix = "english-nova.import")
public class EnglishNovaProperties {

    private String exchange = "english-nova.import";
    private String queue = "english-nova.import.queue";
    private String routingKey = "english.nova.import.requested";
    private String indexedRoutingKey = "english.nova.wordbook.imported";

    /**
     * 获取 RabbitMQ Exchange 名称。
     *
     * @return Exchange 名称
     */
    public String getExchange() {
        return exchange;
    }

    /**
     * 设置 RabbitMQ Exchange 名称。
     *
     * @param exchange Exchange 名称
     */
    public void setExchange(String exchange) {
        this.exchange = exchange;
    }

    /**
     * 获取 RabbitMQ Queue 名称。
     *
     * @return Queue 名称
     */
    public String getQueue() {
        return queue;
    }

    /**
     * 设置 RabbitMQ Queue 名称。
     *
     * @param queue Queue 名称
     */
    public void setQueue(String queue) {
        this.queue = queue;
    }

    /**
     * 获取导入请求 routing key。
     *
     * @return routing key
     */
    public String getRoutingKey() {
        return routingKey;
    }

    /**
     * 设置导入请求 routing key。
     *
     * @param routingKey routing key
     */
    public void setRoutingKey(String routingKey) {
        this.routingKey = routingKey;
    }

    /**
     * 获取词书导入完成后的索引 routing key。
     *
     * @return indexed routing key
     */
    public String getIndexedRoutingKey() {
        return indexedRoutingKey;
    }

    /**
     * 设置词书导入完成后的索引 routing key。
     *
     * @param indexedRoutingKey indexed routing key
     */
    public void setIndexedRoutingKey(String indexedRoutingKey) {
        this.indexedRoutingKey = indexedRoutingKey;
    }
}
