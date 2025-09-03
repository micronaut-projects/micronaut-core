package io.micronaut.test.lombok;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Property;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

@Property(name = "spec.name", value = "lombokFieldsTest")
@MicronautTest
public class LombokFieldsTest {

    @Inject
    ApplicationContext applicationContext;

    @Test
    public void testBean() {
        Assertions.assertTrue(applicationContext.containsBean(FooBean.class));
    }

}
