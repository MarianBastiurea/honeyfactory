package com.marianbastiurea.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ThreadFactory;

@Configuration
public class ThreadingConfig {
    @Bean("taskThreadFactory")
    public ThreadFactory taskThreadFactory() {
        return Thread.ofVirtual().factory();
    }
}