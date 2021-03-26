package io.micronaut.inject.any;

import io.micronaut.context.BeanProvider;
import io.micronaut.context.annotation.Any;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

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
