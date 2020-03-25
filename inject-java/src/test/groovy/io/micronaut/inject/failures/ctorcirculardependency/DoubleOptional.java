package io.micronaut.inject.failures.ctorcirculardependency;

import io.micronaut.context.annotation.Property;

import javax.inject.Singleton;
import java.util.Optional;

@Singleton
class DoubleOptional {

    DoubleOptional(@Property(name = "x") Optional<String> x,
                   @Property(name = "y") Optional<String> y) {

    }
}
