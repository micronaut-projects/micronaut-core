package io.micronaut.inject.field.samename.v1;

import io.micronaut.context.ApplicationContext;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;

public class SameFieldProtectedTest {

    @Test
    public void test() {
        try (ApplicationContext ctx = ApplicationContext.run(Map.of("spec.name", SameFieldProtectedTest.class.getSimpleName()))) {
            Abc abc = ctx.getBean(Abc.class);
            Foo foo = ctx.getBean(Foo.class);
            Assertions.assertEquals(foo.getFooAbc(), abc);
            Assertions.assertEquals(foo.getBarAbc(), abc);
        }
    }

}
