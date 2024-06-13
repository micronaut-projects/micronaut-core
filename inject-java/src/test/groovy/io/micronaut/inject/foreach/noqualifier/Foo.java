package io.micronaut.inject.foreach.noqualifier;

import jakarta.inject.Singleton;

@Singleton
public class Foo implements MyService {
    @Override
    public String getName() {
        return "foo";
    }
}
