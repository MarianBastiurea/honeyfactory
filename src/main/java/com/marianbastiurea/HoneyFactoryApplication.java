package com.marianbastiurea;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.core.env.Environment;
import org.springframework.context.annotation.Bean;

import java.time.ZoneId;
import java.util.Arrays;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

@SpringBootApplication
public class HoneyFactoryApplication {

    private static final Logger log = LoggerFactory.getLogger(HoneyFactoryApplication.class);

    public static void main(String[] args) {
        TimeZone.setDefault(TimeZone.getTimeZone(ZoneId.of("Europe/London")));
        SpringApplication app = new SpringApplication(HoneyFactoryApplication.class);
        app.setDefaultProperties(Map.of("spring.threads.virtual.enabled", "true"));

        long t0 = System.nanoTime();
        var ctx = app.run(args);
        long tookMs = (System.nanoTime() - t0) / 1_000_000;

        String vtEnabled = ctx.getEnvironment().getProperty("spring.threads.virtual.enabled", "false");
        log.info("✅ Application started in {} ms (virtual threads enabled={})", tookMs, vtEnabled);
        log.info("JDK={}, PID={}, CPU cores={}, TZ={}",
                Runtime.version(), ProcessHandle.current().pid(),
                Runtime.getRuntime().availableProcessors(), TimeZone.getDefault().getID());
    }


    @Bean
    CommandLineRunner startupSummary(Environment env) {
        return args -> {
            log.info("Active profiles: {}", Arrays.toString(env.getActiveProfiles()));
            log.info("spring.threads.virtual.enabled={}", env.getProperty("spring.threads.virtual.enabled", "false"));
            log.info("aws.region={}", env.getProperty("aws.region", "(not set)"));
            log.info("app.process-orders-on-startup={}", env.getProperty("app.process-orders-on-startup", "false"));
            log.info("smoke.db.enabled={}", env.getProperty("smoke.db.enabled", "false"));
            log.debug("honey.repo.retries={}", env.getProperty("honey.repo.retries", "5"));
        };
    }

    @Bean(destroyMethod = "close")
    public ExecutorService virtualExecutor(@Qualifier("vtThreadFactory") ThreadFactory taskThreadFactory) {

        try {
            Thread probe = taskThreadFactory.newThread(() -> {});
            log.debug("VT executor probe -> isVirtual={}, name='{}'", probe.isVirtual(), probe.getName());
        } catch (Throwable t) {
            log.warn("Could not create VT probe thread: {}", t.toString());
        }

        log.info("Creating ThreadPerTaskExecutor backed by virtual threads.");
        return Executors.newThreadPerTaskExecutor(taskThreadFactory);
    }
}
