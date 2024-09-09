package io.micronaut.inject.foreach.generic;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class CoreReaders2 {

    @Inject
    public CoreReader2<String> stringReader;
    @Inject
    public CoreReader2<Integer> integerReader;

}
