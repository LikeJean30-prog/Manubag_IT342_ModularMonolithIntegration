package edu.cit.manubag.channel;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.util.UUID;

@Configuration
class ChannelConfig {

    private final String instanceId = UUID.randomUUID().toString();

    @Bean
    String clientInstanceId() {
        return instanceId;
    }
}