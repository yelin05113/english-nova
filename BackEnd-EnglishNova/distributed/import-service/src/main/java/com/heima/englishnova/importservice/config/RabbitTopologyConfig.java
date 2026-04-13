package com.heima.englishnova.importservice.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitTopologyConfig {

    @Bean
    public TopicExchange importExchange(EnglishNovaProperties properties) {
        return new TopicExchange(properties.getExchange(), true, false);
    }

    @Bean
    public Queue importQueue(EnglishNovaProperties properties) {
        return new Queue(properties.getQueue(), true);
    }

    @Bean
    public Binding importBinding(Queue importQueue, TopicExchange importExchange, EnglishNovaProperties properties) {
        return BindingBuilder.bind(importQueue)
                .to(importExchange)
                .with(properties.getRoutingKey());
    }
}
