package io.micronaut.inject.context.watch;

import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Requires;

@EachBean(ChimingPool.class)
@Requires(property = "spec.name", value = "BeanWatchTest")
@Requires(property = "chiming.pools")
public class ChimingBell {
    private final ChimingPool pool;

    ChimingBell(ChimingPool pool) {
        this.pool = pool;
    }

    public ChimingPool getPool() {
        return pool;
    }

    @Chime
    void ring() {
        // only the @Chime annotation matters to the spec
    }
}
