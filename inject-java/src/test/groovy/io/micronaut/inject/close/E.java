package io.micronaut.inject.close;

import io.micronaut.context.BeanProvider;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;

import javax.annotation.PreDestroy;
import java.io.IOException;

@Requires(property = "spec.name", value = "BeanCloseOrderSpec")
@Singleton
public class E implements AutoCloseable {

    public E(BeanProvider<F> fProvider) {}

    @PreDestroy
    @Override
    public void close() throws IOException {
        BeanCloseOrderSpec.getClosed().add(E.class);
    }
}
