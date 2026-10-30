package io.micronaut.inject.provider.lookuppoint;

import io.micronaut.context.BeanProvider;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class Holder {

    @Inject
    @Red
    BeanProvider<RedService> services;

    @Inject
    @Red
    Lookup<RedService> lookup;
}
