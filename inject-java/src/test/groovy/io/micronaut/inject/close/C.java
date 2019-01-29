package io.micronaut.inject.close;

import io.micronaut.context.annotation.Requires;

import javax.annotation.PreDestroy;
import javax.inject.Singleton;
import java.io.IOException;

@Requires(property = "spec.name", value = "BeanCloseOrderSpec")
@Singleton
public class C implements AutoCloseable {

    public C(D d) {}

    @PreDestroy
    @Override
    public void close() throws IOException {
        BeanCloseOrderSpec.getClosed().add(C.class);
    }
}