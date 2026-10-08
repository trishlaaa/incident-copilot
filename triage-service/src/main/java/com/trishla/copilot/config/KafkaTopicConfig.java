package com.trishla.copilot.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    @Bean
    public NewTopic appLogsTopic(@Value("${app.topic}") String topic) {
        // 3 partitions, keyed by service name, so events from one service stay ordered.
        return TopicBuilder.name(topic).partitions(3).replicas(1).build();
    }
}
