package com.example.aws.demo.metrics;

import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient;
import software.amazon.awssdk.services.cloudwatch.model.Dimension;
import software.amazon.awssdk.services.cloudwatch.model.MetricDatum;
import software.amazon.awssdk.services.cloudwatch.model.PutMetricDataRequest;
import software.amazon.awssdk.services.cloudwatch.model.StandardUnit;

/**
 * Publishes custom application metrics to Amazon CloudWatch via {@code PutMetricData}.
 *
 * <p>Publishing failures (e.g. missing credentials when running locally) are caught and logged so
 * that a metrics problem never breaks the business operation that triggered it.
 */
@Service
public class CloudWatchService {

    private static final Logger log = LoggerFactory.getLogger(CloudWatchService.class);

    private final CloudWatchClient cloudWatchClient;
    private final String namespace;

    public CloudWatchService(CloudWatchClient cloudWatchClient,
                             @Value("${aws.cloudwatch.namespace:AwsDemo/Application}") String namespace) {
        this.cloudWatchClient = cloudWatchClient;
        this.namespace = namespace;
    }

    /**
     * Publishes a single count-based metric (value 1.0) with an "Operation" dimension.
     *
     * @param metricName   the CloudWatch metric name, e.g. "ProductCreated"
     * @param operation    value for the "Operation" dimension, e.g. "create"
     */
    public void publishCount(String metricName, String operation) {
        try {
            MetricDatum datum = MetricDatum.builder()
                    .metricName(metricName)
                    .unit(StandardUnit.COUNT)
                    .value(1.0)
                    .timestamp(Instant.now())
                    .dimensions(Dimension.builder().name("Operation").value(operation).build())
                    .build();

            PutMetricDataRequest request = PutMetricDataRequest.builder()
                    .namespace(namespace)
                    .metricData(datum)
                    .build();

            cloudWatchClient.putMetricData(request);
            log.debug("Published CloudWatch metric {}={} (operation={})", metricName, 1.0, operation);
        } catch (RuntimeException ex) {
            // Do not let a metrics failure break the request.
            log.warn("Failed to publish CloudWatch metric {} (operation={}): {}",
                    metricName, operation, ex.getMessage());
        }
    }
}
