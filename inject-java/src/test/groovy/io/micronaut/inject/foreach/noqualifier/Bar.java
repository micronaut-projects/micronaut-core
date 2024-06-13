package io.micronaut.inject.foreach.noqualifier;

import jakarta.inject.Singleton;

@Singleton
public class Bar implements MyService {
    @Override
    public String getName() {
        return "bar";
    }
}
