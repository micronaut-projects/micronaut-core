package io.micronaut.reflection;

import io.micronaut.context.annotation.Bean;
import jakarta.inject.Singleton;

/**
 * A bean the class annotation exposes only as a {@link DefTask}.
 */
@Singleton
@Bean(typed = DefTask.class)
public class DefJob implements DefTask {

    private boolean ran;

    @Override
    public void run() {
        ran = true;
    }

    public boolean hasRun() {
        return ran;
    }
}
