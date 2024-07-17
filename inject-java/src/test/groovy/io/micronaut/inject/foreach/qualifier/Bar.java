package io.micronaut.inject.foreach.qualifier;

import io.micronaut.context.annotation.Requires;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

@Requires(property = "spec", value = "EachBeanQualifierSpec")
@Named("Bar")
@Singleton
public class Bar implements MyService {
    @Override
    public String getName() {
        return "bar";
    }
}
