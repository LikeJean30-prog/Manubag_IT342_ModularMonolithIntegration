package edu.cit.manubag.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
public class SchedulingConfig {

    @Bean
    public ThreadPoolTaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(6);
        scheduler.setThreadNamePrefix("scheduling-");
        scheduler.setWaitForTasksToCompleteOnShutdown(false);
        return scheduler;
    }
}