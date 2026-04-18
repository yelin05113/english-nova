package com.nightfall.englishnova.search.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.amqp.support.converter.SimpleMessageConverter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * 搜索服务 RabbitMQ 拓扑配置类，声明搜索索引所需的 Exchange、Queue、Binding 及消息转换器。
 */
@Configuration
@EnableConfigurationProperties(SearchRabbitConfig.SearchQueueProperties.class)
public class SearchRabbitConfig {

    /**
     * 声明搜索服务使用的 Topic Exchange。
     *
     * @param properties 搜索队列配置属性
     * @return Topic Exchange 实例
     */
    @Bean
    public TopicExchange searchExchange(SearchQueueProperties properties) {
        return new TopicExchange(properties.exchange(), true, false);
    }

    /**
     * 声明搜索索引队列。
     *
     * @param properties 搜索队列配置属性
     * @return Queue 实例
     */
    @Bean
    public Queue searchIndexQueue(SearchQueueProperties properties) {
        return new Queue(properties.indexQueue(), true);
    }

    /**
     * 将搜索索引队列绑定到搜索 Exchange。
     *
     * @param searchIndexQueue 搜索索引队列
     * @param searchExchange   搜索 Topic Exchange
     * @param properties       搜索队列配置属性
     * @return Binding 实例
     */
    @Bean
    public Binding searchIndexBinding(
            Queue searchIndexQueue,
            TopicExchange searchExchange,
            SearchQueueProperties properties
    ) {
        return BindingBuilder.bind(searchIndexQueue)
                .to(searchExchange)
                .with(properties.routingKey());
    }

    /**
     * 提供搜索服务消息转换器，配置允许反序列化的类名模式。
     *
     * @return MessageConverter 实例
     */
    @Bean
    public MessageConverter searchMessageConverter() {
        SimpleMessageConverter converter = new SimpleMessageConverter();
        converter.setAllowedListPatterns(List.of(
                "com.nightfall.englishnova.shared.events.*",
                "java.lang.*",
                "java.util.*"
        ));
        return converter;
    }

    /**
     * 提供搜索服务 RabbitMQ 监听器容器工厂。
     *
     * @param connectionFactory   RabbitMQ 连接工厂
     * @param searchMessageConverter 搜索消息转换器
     * @return SimpleRabbitListenerContainerFactory 实例
     */
    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            MessageConverter searchMessageConverter
    ) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(searchMessageConverter);
        return factory;
    }

    /**
 * 搜索服务 RabbitMQ 队列配置属性，绑定前缀为 english-nova.search 的配置项。
 *
 * @param exchange    Exchange 名称
 * @param indexQueue  索引队列名称
 * @param routingKey  路由键
     */
    @ConfigurationProperties(prefix = "english-nova.search")
    public record SearchQueueProperties(
            String exchange,
            String indexQueue,
            String routingKey
    ) {
    }
}
