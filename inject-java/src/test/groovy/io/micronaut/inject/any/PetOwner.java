package io.micronaut.inject.any;

import io.micronaut.context.BeanProvider;
import io.micronaut.context.annotation.Any;

import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

@Singleton
public class PetOwner {
    @Inject @Any Dog<?> dog;

    @Inject @Any
    BeanProvider<Dog<?>> dogBeanProvider;

    @Inject @Any
    BeanProvider<Cat> catBeanProvider;

    @Inject @Named("poodle") Dog<?> poodle;

    @Inject @Named("terrier") BeanProvider<Dog<?>> terrierProvider;
}
