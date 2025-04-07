package org.example.connector.utils;

import java.time.Duration;

public class ZeebeSourceBackoff {

  private static final int NO_BACKOFF = -1;

  private final long minBackoff;
  private final long maxBackoff;
  private long backoff;

  private ZeebeSourceBackoff(final long minBackoff, final long maxBackoff) {
    this.minBackoff = minBackoff;
    this.maxBackoff = maxBackoff;
    reset();
  }

  ZeebeSourceBackoff(final ZeebeSourceConnectorConfig config) {
    this(100, config.getLong(ZeebeClientConfigDef.REQUEST_TIMEOUT_CONFIG));
  }

  void reset() {
    backoff = NO_BACKOFF;
  }

  Duration currentDuration() {
    return Duration.ofMillis(backoff);
  }

  private long nextDuration() {
    final long nextBackoff = Math.max(backoff, minBackoff) * 2;
    return Math.min(nextBackoff, maxBackoff);
  }

  void backoff() {
    backoff = nextDuration();
    try {
      Thread.sleep(backoff);
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

}