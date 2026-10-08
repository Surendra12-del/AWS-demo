package com.example.aws.demo.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient;
import software.amazon.awssdk.services.s3.S3Client;

/**
 * Creates the AWS SDK v2 clients used by the demo.
 *
 * <p>Both clients are built with {@link DefaultCredentialsProvider}, which resolves credentials
 * in this order: environment variables, Java system properties, the {@code ~/.aws/credentials}
 * profile, and finally the EC2 instance profile (IAM role) when running on an EC2 instance.
 * Building the client does NOT require credentials — they are only resolved when a request is
 * actually made. This lets the application start locally even before AWS is configured.
 */
@Configuration
public class AwsConfig {

    @Value("${aws.region:us-east-1}")
    private String region;

    @Bean
    public S3Client s3Client() {
        return S3Client.builder()
                .region(Region.of(region))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }

    @Bean
    public CloudWatchClient cloudWatchClient() {
        return CloudWatchClient.builder()
                .region(Region.of(region))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }
}
