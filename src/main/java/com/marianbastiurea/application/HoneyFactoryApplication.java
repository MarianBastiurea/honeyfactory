package com.marianbastiurea.application;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.time.ZoneId;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

@SpringBootApplication
public class HoneyFactoryApplication {

    private static final Logger log = LoggerFactory.getLogger(HoneyFactoryApplication.class);

    public static void main(String[] args) {
        // (opțional) setează TZ pentru loguri/rapoarte
        TimeZone.setDefault(TimeZone.getTimeZone(ZoneId.of("Europe/London")));

        SpringApplication app = new SpringApplication(HoneyFactoryApplication.class);

        // Activează integrarea Spring cu virtual threads (Spring Boot 3.2+)
        app.setDefaultProperties(Map.of("spring.threads.virtual.enabled", "true"));

        app.run(args);
        log.info("✅ Application started (virtual threads enabled)");
    }

    /** Bean folosit în servicii pentru StructuredTaskScope etc. */
    @Bean(name = "taskThreadFactory")
    public ThreadFactory taskThreadFactory() {
        return Thread.ofVirtual().name("vt-", 0).factory();
    }

    /** Executor pe virtual threads (bun pentru lucrări fire-and-forget/@Async). */
    @Bean(destroyMethod = "close")
    public ExecutorService virtualExecutor(ThreadFactory taskThreadFactory) {
        // În JDK 21/23: newThreadPerTaskExecutor + factory de virtual threads
        return Executors.newThreadPerTaskExecutor(taskThreadFactory);
        // Alternativ: return Executors.newVirtualThreadPerTaskExecutor();
    }
}

