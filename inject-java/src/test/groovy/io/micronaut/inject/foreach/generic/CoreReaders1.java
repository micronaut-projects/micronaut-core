package io.micronaut.inject.foreach.generic;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class CoreReaders1 {

    @Inject
    public CoreReader1<String> stringReader;
    @Inject
    public CoreReader1<Integer> integerReader;

}
