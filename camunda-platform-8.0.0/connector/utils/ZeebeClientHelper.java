package org.example.connector.utils;

import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.ZeebeClientBuilder;
import lombok.experimental.UtilityClass;
import org.apache.kafka.common.config.AbstractConfig;

import java.time.Duration;

@UtilityClass
public class ZeebeClientHelper {

    public static ZeebeClient buildClient(final AbstractConfig config) {
        final long requestTimeoutMs = config.getLong(ZeebeClientConfigDef.REQUEST_TIMEOUT_CONFIG);
        final ZeebeClientBuilder zeebeClientBuilder =
                ZeebeClient.newClientBuilder()
                        .gatewayAddress(config.getString(ZeebeClientConfigDef.GATEWAY_ADDRESS_CONFIG))
                        .numJobWorkerExecutionThreads(1)
                        .defaultRequestTimeout(Duration.ofMillis(requestTimeoutMs));

        if (config.getBoolean(ZeebeClientConfigDef.USE_PLAINTEXT_CONFIG)) {
            zeebeClientBuilder.usePlaintext();
        }

        return zeebeClientBuilder.build();

    }

}