package io.micronaut.inject.foreach.qualifier;

import io.micronaut.context.annotation.Requires;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

@Requires(property = "spec", value = "EachBeanQualifierSpec")
@Named("Foo")
@Singleton
public class Foo implements MyService {
    @Override
    public String getName() {
        return "foo";
    }
}
