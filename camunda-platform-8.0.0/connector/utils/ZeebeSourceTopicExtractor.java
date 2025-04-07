package org.example.connector.utils;

import io.camunda.zeebe.client.api.response.ActivatedJob;
import lombok.Getter;

public class ZeebeSourceTopicExtractor {

  private final String jobHeaderTopic;
  @Getter
  private final String topicHeaderNotFoundErrorMessage;

  public ZeebeSourceTopicExtractor(ZeebeSourceConnectorConfig config) {
    jobHeaderTopic = config.getString(ZeebeSourceConnectorConfig.JOB_HEADER_TOPICS_CONFIG);

    topicHeaderNotFoundErrorMessage =
        String.format(
            "Expected a kafka topic to be defined as a custom header with key '%s', but none found",
            jobHeaderTopic);
  }

  public String extract(ActivatedJob job) {
    return job.getCustomHeaders().get(jobHeaderTopic);
  }

}