package io.micronaut.inject.context.retain;

import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Requires;
import jakarta.annotation.PreDestroy;

import java.util.concurrent.atomic.AtomicInteger;

@EachBean(Feed.class)
@Requires(property = "spec.name", value = "RetainedRegistrationsSpec")
public class FeedReader {
    public static final AtomicInteger DESTROYED = new AtomicInteger();
    public final Feed feed;

    public FeedReader(Feed feed) {
        this.feed = feed;
    }

    @PreDestroy
    void close() {
        DESTROYED.incrementAndGet();
    }
}
