package com.ygq.feedly.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String FANOUT_EXCHANGE = "feed.fanout.exchange";
    public static final String FANOUT_QUEUE = "feed.fanout.queue";
    public static final String DLX_EXCHANGE = "feed.dlx.exchange";
    public static final String DLX_QUEUE = "feed.dlx.queue";

    @Bean
    public FanoutExchange feedFanoutExchange() {
        return new FanoutExchange(FANOUT_EXCHANGE, true, false);
    }

    @Bean
    public Queue feedFanoutQueue() {
        return QueueBuilder.durable(FANOUT_QUEUE)
                .withArgument("x-dead-letter-exchange", DLX_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", "")
                .build();
    }

    @Bean
    public Binding feedFanoutBinding() {
        return BindingBuilder.bind(feedFanoutQueue()).to(feedFanoutExchange());
    }

    @Bean
    public FanoutExchange feedDlxExchange() {
        return new FanoutExchange(DLX_EXCHANGE, true, false);
    }

    @Bean
    public Queue feedDlxQueue() {
        return QueueBuilder.durable(DLX_QUEUE).build();
    }

    @Bean
    public Binding feedDlxBinding() {
        return BindingBuilder.bind(feedDlxQueue()).to(feedDlxExchange());
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}