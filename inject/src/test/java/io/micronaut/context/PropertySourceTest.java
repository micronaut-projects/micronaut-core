package io.micronaut.context;

import io.micronaut.context.env.PropertySource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;

public class PropertySourceTest {

    @Test
    void testPropertySourceMapOf() {
        Map<String, Object> map = PropertySource.mapOf(
                "foo.bar", 10
        );

        Assertions.assertEquals(
                10,
                map.get("foo.bar")
        );

        PropertySource propertySource = PropertySource.of("test",
                "foo.bar", 10
        );


        Assertions.assertEquals(
                10,
                propertySource.get("foo.bar")
        );

        Assertions.assertEquals(
                "test",
                propertySource.getName()
        );
    }
}
