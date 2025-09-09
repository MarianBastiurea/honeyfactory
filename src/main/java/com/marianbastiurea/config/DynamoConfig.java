package com.marianbastiurea.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClientBuilder;

import java.net.URI;

@Configuration(proxyBeanMethods = false)
public class DynamoConfig {

    private static final Logger log = LoggerFactory.getLogger(DynamoConfig.class);

    @Bean
    public AwsCredentialsProvider awsCredentialsProvider() {
        log.debug("Creating AWS DefaultCredentialsProvider.");
        return DefaultCredentialsProvider.create();
    }

    @Bean
    public Region awsRegion(@Value("${app.dynamodb.region}") String region) {
        Region resolved = Region.of(region);
        log.info("AWS region configured: {}", resolved);
        return resolved;
    }

    @Bean
    public DynamoDbClient dynamoDbClient(Region region,
                                         AwsCredentialsProvider creds,
                                         @Value("${aws.dynamodb.endpoint-override:}") String endpointOverride) {

        String endpointMsg = (endpointOverride == null || endpointOverride.isBlank()) ? "<none>" : endpointOverride;
        log.info("Building DynamoDbClient (region={}, endpointOverride={})", region, endpointMsg);
        log.debug("Using AWS credentials provider: {}", creds.getClass().getName());

        DynamoDbClientBuilder builder = DynamoDbClient.builder()
                .region(region)
                .credentialsProvider(creds);

        if (endpointOverride != null && !endpointOverride.isBlank()) {
            log.warn("DynamoDB endpoint override is set (usually for Local/Dev): {}", endpointOverride);
            builder.endpointOverride(URI.create(endpointOverride));
        }

        DynamoDbClient client = builder.build();
        log.debug("DynamoDbClient created: {}", client);
        return client;
    }

    @Bean
    public DynamoDbEnhancedClient enhancedClient(DynamoDbClient client) {
        log.info("Creating DynamoDbEnhancedClient.");
        DynamoDbEnhancedClient enhanced = DynamoDbEnhancedClient.builder()
                .dynamoDbClient(client)
                .build();
        log.debug("DynamoDbEnhancedClient created: {}", enhanced);
        return enhanced;
    }
}
