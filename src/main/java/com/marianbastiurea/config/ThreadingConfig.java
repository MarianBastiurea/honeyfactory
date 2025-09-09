package com.marianbastiurea.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ThreadFactory;

@Configuration
public class ThreadingConfig {

    private static final Logger log = LoggerFactory.getLogger(ThreadingConfig.class);

    @Bean("vtThreadFactory")
    public ThreadFactory vtThreadFactory(
            @Value("${threads.vt.name-prefix:vt-}") String namePrefix
    ) {
        log.info("Creating Virtual Thread factory (prefix='{}', JDK={}).",
                namePrefix, Runtime.version());

        ThreadFactory factory = Thread.ofVirtual().name(namePrefix, 0).factory();

        // Probe: construim un thread (nu-l pornim) ca să logăm dacă e virtual + numele
        try {
            Thread probe = factory.newThread(() -> {});
            log.debug("Virtual Thread probe -> isVirtual={}, name='{}'",
                    probe.isVirtual(), probe.getName());
        } catch (Throwable t) {
            log.warn("Could not create probe thread for virtual factory: {}", t.toString());
        }

        log.info("Virtual Thread factory created successfully with prefix='{}'.", namePrefix);
        return factory;
    }
}
