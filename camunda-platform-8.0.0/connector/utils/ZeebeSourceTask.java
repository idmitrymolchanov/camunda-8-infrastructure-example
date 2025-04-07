package org.example.connector.utils;

import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.response.ActivatedJob;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.connect.data.Schema;
import org.apache.kafka.connect.source.SourceRecord;
import org.apache.kafka.connect.source.SourceTask;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
public class ZeebeSourceTask extends SourceTask {

  private ManagedClient managedClient;
  private ZeebeSourceTopicExtractor topicExtractor;
  private ZeebeSourceTaskFetcher taskFetcher;
  private ZeebeSourceInflightRegistry inflightRegistry;
  private ZeebeSourceBackoff backoff;

  public ZeebeSourceTask() {}

  @Override
  public void start(final Map<String, String> props) {
    final ZeebeSourceConnectorConfig config = new ZeebeSourceConnectorConfig(props);
    final ZeebeClient client = ZeebeClientHelper.buildClient(config);
    managedClient = new ManagedClient(client);

    topicExtractor = new ZeebeSourceTopicExtractor(config);
    taskFetcher = new ZeebeSourceTaskFetcher(config, topicExtractor);
    inflightRegistry = new ZeebeSourceInflightRegistry(config);
    backoff = new ZeebeSourceBackoff(config);
  }

  @SuppressWarnings("squid:S1168")
  @Override
  public List<SourceRecord> poll() {
    if (!inflightRegistry.hasCapacity()) {
      log.trace("No capacity left to poll new jobs, returning control to caller after backoff");
      backoff.backoff();
      return null;
    }

    final List<SourceRecord> records =
        inflightRegistry
            .jobTypesWithCapacity()
            .flatMap(this::fetchJobs)
            .map(inflightRegistry::registerJob)
            .map(this::transformJob)
            .collect(Collectors.toList());

    // poll interface specifies to return null instead of empty
    if (records.isEmpty()) {
      log.trace("Nothing to publish, returning control to caller after backoff");
      backoff.backoff();
      return null;
    }

    log.debug("Publishing {} source records", records.size());
    return records;
  }

  private Stream<ActivatedJob> fetchJobs(final String jobType) {
    final int amount = inflightRegistry.capacityForType(jobType);
    final Duration requestTimeout = backoff.currentDuration();
    try {
      return managedClient
          .withClient(c -> taskFetcher.fetchBatch(c, jobType, amount, requestTimeout))
          .stream();
    } catch (final ManagedClient.AlreadyClosedException e) {
      log.warn("Expected to activate jobs for type {}, but failed to receive response {}", jobType, e);

    } catch (final InterruptedException e) {
      log.warn("Expected to activate jobs for type {}, but was interrupted", jobType);
      Thread.currentThread().interrupt();
    }
    return Stream.empty();
  }

  @Override
  public void stop() {
    managedClient.close();
  }

  @Override
  public void commit() {
    inflightRegistry.unregisterAllJobs().forEach(this::completeJob);
  }

  @Override
  public void commitRecord(final SourceRecord record) {
    final long key = (Long) record.sourceOffset().get("key");
    completeJob(key);
    inflightRegistry.unregisterJob(key);
    backoff.reset();
  }

  private void completeJob(final long key) {
    try {
      managedClient.withClient(c -> c.newCompleteCommand(key).send());
    } catch (final CancellationException e) {
      log.debug("Complete command cancelled probably because task is stopping", e);
    } catch (final ManagedClient.AlreadyClosedException e) {
      log.debug("Expected to complete job {}, but client is already closed", key);
    } catch (final InterruptedException e) {
      log.debug("Expected to complete job {}, but was interrupted", key);
      Thread.currentThread().interrupt();
    }
  }

  @Override
  public String version() {
    return "0.51.1-SNAPSHOT";
  }

  private SourceRecord transformJob(final ActivatedJob job) {
    final String topic = topicExtractor.extract(job);
    final Map<String, Integer> sourcePartition =
        Collections.singletonMap("partitionId", decodePartitionId(job.getKey()));
    // a better sourceOffset would be the position but we don't have it here unfortunately
    // key is however a monotonically increasing value, so in a sense it can provide a good
    // approximation of an offset
    final Map<String, Long> sourceOffset = Collections.singletonMap("key", job.getKey());
    return new SourceRecord(
        sourcePartition,
        sourceOffset,
        topic,
        Schema.INT64_SCHEMA,
        job.getKey(),
        Schema.STRING_SCHEMA,
        job.toJson());
  }

  // Copied from Zeebe Protocol as it is currently fixed to Java 11, and the connector to Java 8
  // Should be fixed eventually and we can use the protocol directly again
  private int decodePartitionId(final long key) {
    return (int) (key >> 51);
  }

}