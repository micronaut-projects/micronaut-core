package io.micronaut.test.lombok;

import io.micronaut.core.beans.BeanIntrospection;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class LombokIntrospectedBuilderTest {

    @Test
    void testLombokBuilder() {
        BeanIntrospection.Builder<RobotEntity> builder = BeanIntrospection.getIntrospection(RobotEntity.class)
            .builder();

        RobotEntity robotEntity = builder.with("name", "foo")
            .build();

        Assertions.assertEquals("foo", robotEntity.getName());
    }
}
