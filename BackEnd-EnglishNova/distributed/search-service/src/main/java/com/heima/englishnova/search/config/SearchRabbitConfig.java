package com.heima.englishnova.search.config;

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

@Configuration
@EnableConfigurationProperties(SearchRabbitConfig.SearchQueueProperties.class)
public class SearchRabbitConfig {

    @Bean
    public TopicExchange searchExchange(SearchQueueProperties properties) {
        return new TopicExchange(properties.exchange(), true, false);
    }

    @Bean
    public Queue searchIndexQueue(SearchQueueProperties properties) {
        return new Queue(properties.indexQueue(), true);
    }

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

    @Bean
    public MessageConverter searchMessageConverter() {
        SimpleMessageConverter converter = new SimpleMessageConverter();
        converter.setAllowedListPatterns(List.of(
                "com.heima.englishnova.shared.events.*",
                "java.lang.*",
                "java.util.*"
        ));
        return converter;
    }

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

    @ConfigurationProperties(prefix = "english-nova.search")
    public record SearchQueueProperties(
            String exchange,
            String indexQueue,
            String routingKey
    ) {
    }
}
