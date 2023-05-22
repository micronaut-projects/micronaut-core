package io.micronaut.inject.lifecycle.beancreationeventlistener;

import io.micronaut.context.BeanProvider;
import io.micronaut.context.env.Environment;
import jakarta.inject.Singleton;

@Singleton
public class C {
    public C(BeanProvider<Environment> env) {
    }
}
