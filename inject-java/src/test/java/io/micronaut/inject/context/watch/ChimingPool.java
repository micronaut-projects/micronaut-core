package io.micronaut.inject.context.watch;

import io.micronaut.context.annotation.EachProperty;
import io.micronaut.context.annotation.Requires;

@EachProperty("chiming.pools")
@Requires(property = "spec.name", value = "BeanWatchTest")
@Requires(property = "chiming.pools")
public class ChimingPool {
    private int size;

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }

    @Chime
    void chime() {
        // only the @Chime annotation matters to the spec
    }
}
