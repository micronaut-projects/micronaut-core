package io.micronaut.inject.foreach.qualifier;

import jakarta.inject.Named;
import jakarta.inject.Singleton;

@Named("Bar")
@Singleton
public class Bar implements MyService {
    @Override
    public String getName() {
        return "bar";
    }
}
