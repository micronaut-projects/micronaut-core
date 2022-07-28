package io.micronaut.inject.provider.bug;

import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Prototype;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.util.concurrent.atomic.AtomicInteger;

@Factory
public class Config {

  @Singleton
  @First
  AtomicInteger createCounterFirst() {
    return new AtomicInteger();
  }

  @Singleton
  @Second
  AtomicInteger createCounterSecond() {
    return new AtomicInteger();
  }

  @Singleton
  @Third
  AtomicInteger createCounterThird() {
    return new AtomicInteger();
  }

  @Prototype
  @First
  Injectable createFirst(@First Provider<AtomicInteger> counter) {
    return new Injectable(counter.get().addAndGet(1));
  }

  @Prototype
  @Second
  Injectable createSecond(@Second Provider<AtomicInteger> counter) {
    return new Injectable(counter.get().addAndGet(10));
  }

  @Prototype
  @Third
  @Requires(property = "third.enabled", value = "true")
  Injectable createThird(@Third Provider<AtomicInteger> counter) {
    return new Injectable(counter.get().addAndGet(100));
  }
}
