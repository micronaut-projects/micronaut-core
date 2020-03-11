package io.micronaut.cache;

import io.micronaut.cache.annotation.Cacheable;
import io.reactivex.Single;

import javax.inject.Singleton;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

@Singleton
@Cacheable(cacheNames = {"slow-cache"})
public class CacheableService {
    public AtomicInteger counter = new AtomicInteger();

    public CompletableFuture<String> slowCompletableFutureCall() {
        counter.incrementAndGet();
        CompletableFuture<String> completableFuture
                = new CompletableFuture<>();

        Executors.newCachedThreadPool().submit(() -> {
            Thread.sleep(500);
            completableFuture.complete("Hello");
            return null;
        });

        return completableFuture;
    }

    public Single<String> slowPublisherCall() {
        counter.incrementAndGet();

        return Single.fromFuture(slowCompletableFutureCall());
    }
}
