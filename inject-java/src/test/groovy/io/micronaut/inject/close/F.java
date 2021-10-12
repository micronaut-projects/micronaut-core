package io.micronaut.inject.close;

import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;

import javax.annotation.PreDestroy;
import java.io.IOException;

@Requires(property = "spec.name", value = "BeanCloseOrderSpec")
@Singleton
public class F implements AutoCloseable {

    public F() {}

    @PreDestroy
    @Override
    public void close() throws IOException {
        BeanCloseOrderSpec.getClosed().add(F.class);
    }
}
