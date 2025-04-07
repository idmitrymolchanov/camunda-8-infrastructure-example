package org.example.connector.utils;

import io.camunda.zeebe.client.ZeebeClient;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;

public class ManagedClient {

  private final Lock lock;
  private final ZeebeClient client;

  private volatile boolean closed;

  public ManagedClient(final ZeebeClient client) {
    this.client = client;
    this.lock = new ReentrantLock();
  }

  public <T> T withClient(final Function<ZeebeClient, T> callback)
      throws AlreadyClosedException, InterruptedException {
    if (closed) {
      throw new AlreadyClosedException();
    }

    lock.lockInterruptibly();
    try {
      return callback.apply(client);
    } finally {
      lock.unlock();

      if (closed) {
        close();
      }
    }
  }

  public void close() {
    closed = true;

    if (lock.tryLock()) {
      try {
        client.close();
      } finally {
        lock.unlock();
      }
    }
  }

  public static class AlreadyClosedException extends Exception {}

}