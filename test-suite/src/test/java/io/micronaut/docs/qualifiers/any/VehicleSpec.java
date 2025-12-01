package io.micronaut.docs.qualifiers.any;

import io.micronaut.context.annotation.Property;
import io.micronaut.docs.qualifiers.annotationmember.Engine;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

// tag::imports[]
import io.micronaut.context.annotation.Any;
import jakarta.inject.Inject;
// end::imports[]

@Property(name = "spec.name", value = "VehicleAnySpec")
@MicronautTest
public class VehicleSpec {

    // tag::any[]
    @Inject @Any
    Engine engine;
    // end::any[]

    @Test
    void testEngine() {
        Assertions.assertNotNull(engine);
    }
}
