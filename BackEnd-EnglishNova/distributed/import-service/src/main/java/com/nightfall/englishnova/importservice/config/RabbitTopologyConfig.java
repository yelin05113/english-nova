package com.nightfall.englishnova.importservice.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 拓扑配置类，声明导入服务所需的 Exchange、Queue 与 Binding。
 */
@Configuration
public class RabbitTopologyConfig {

    /**
     * 声明导入 Topic Exchange。
     *
     * @param properties EnglishNova 配置属性
     * @return TopicExchange 实例
     */
    @Bean
    public TopicExchange importExchange(EnglishNovaProperties properties) {
        return new TopicExchange(properties.getExchange(), true, false);
    }

    /**
     * 声明导入队列。
     *
     * @param properties EnglishNova 配置属性
     * @return Queue 实例
     */
    @Bean
    public Queue importQueue(EnglishNovaProperties properties) {
        return new Queue(properties.getQueue(), true);
    }

    /**
     * 将导入队列绑定到导入 Exchange，使用配置中的 routingKey。
     *
     * @param importQueue    导入队列
     * @param importExchange 导入交换机
     * @param properties     EnglishNova 配置属性
     * @return Binding 实例
     */
    @Bean
    public Binding importBinding(Queue importQueue, TopicExchange importExchange, EnglishNovaProperties properties) {
        return BindingBuilder.bind(importQueue)
                .to(importExchange)
                .with(properties.getRoutingKey());
    }
}
