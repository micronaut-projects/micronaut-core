package io.micronaut.inject.foreach.qualifier;

import jakarta.inject.Named;
import jakarta.inject.Singleton;

@Named("Foo")
@Singleton
public class Foo implements MyService {
    @Override
    public String getName() {
        return "foo";
    }
}
