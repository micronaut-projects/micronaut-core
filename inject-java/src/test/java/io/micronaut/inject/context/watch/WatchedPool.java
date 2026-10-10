package io.micronaut.inject.context.watch;

import io.micronaut.context.annotation.EachProperty;
import io.micronaut.context.annotation.Requires;

@EachProperty("watched.pools")
@Requires(property = "spec.name", value = "BeanWatchTest")
public class WatchedPool {
    private int size;

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }
}
