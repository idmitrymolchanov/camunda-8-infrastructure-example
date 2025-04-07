package org.example.connector;

import org.apache.kafka.common.config.ConfigDef;
import org.apache.kafka.common.config.ConfigException;
import org.apache.kafka.connect.connector.Task;
import org.apache.kafka.connect.errors.ConnectException;
import org.apache.kafka.connect.source.SourceConnector;
import org.apache.kafka.connect.util.ConnectorUtils;
import org.example.connector.utils.ZeebeSourceConnectorConfig;
import org.example.connector.utils.ZeebeSourceTask;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ZeebeSourceConnector extends SourceConnector {

  private List<String> jobTypes;
  private ZeebeSourceConnectorConfig config;

  @Override
  public void start(final Map<String, String> props) {
    try {
      config = new ZeebeSourceConnectorConfig(props);
    } catch (final ConfigException e) {
      throw new ConnectException(
          "Failed to start ZeebeSourceConnector due to configuration error", e);
    }

    jobTypes = config.getList(ZeebeSourceConnectorConfig.JOB_TYPES_CONFIG);
    if (jobTypes.isEmpty()) {
      throw new ConnectException(
          "Expected at least one job type to poll for, but nothing configured");
    }
  }

  @Override
  public Class<? extends Task> taskClass() {
    return ZeebeSourceTask.class;
  }

  @Override
  public List<Map<String, String>> taskConfigs(final int maxTasks) {
    return ConnectorUtils.groupPartitions(jobTypes, maxTasks).stream()
        .map(this::transformTask)
        .collect(Collectors.toList());
  }

  @Override
  public void stop() {}

  @Override
  public ConfigDef config() {
    return ZeebeSourceConnectorConfig.DEFINITIONS;
  }

  @Override
  public String version() {
    return "0.51.1-SNAPSHOT";
  }

  private Map<String, String> transformTask(final List<String> jobTypes) {
    final Map<String, String> taskConfig = new HashMap<>(config.originalsStrings());
    taskConfig.put(ZeebeSourceConnectorConfig.JOB_TYPES_CONFIG, String.join(",", jobTypes));

    return taskConfig;
  }

}