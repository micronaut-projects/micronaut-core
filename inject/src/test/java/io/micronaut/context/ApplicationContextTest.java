package io.micronaut.context;

import io.micronaut.context.env.Environment;
import io.micronaut.context.env.PropertySource;
import io.micronaut.context.env.PropertySourcesLocator;
import io.micronaut.core.io.scan.ClassClassPathResourceLoader;
import io.micronaut.core.io.scan.ClassLoaderClassPathResourceLoader;
import io.micronaut.core.io.scan.CombinedClassPathResourceLoader;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ApplicationContextTest {

    @Test
    void testResourceResolver() {
        try (ApplicationContext context = ApplicationContext.builder()
            .environments("mytest", "denis")
            .resourceResolver(CombinedClassPathResourceLoader.of(
                new ClassLoaderClassPathResourceLoader(),
                new ClassClassPathResourceLoader(ApplicationContext.class)
            ))
            .start()) {
            List<PropertySource> propertySources = new ArrayList<>(context.getEnvironment().getPropertySources());
            Assertions.assertEquals(5, propertySources.size());
            Assertions.assertEquals("application", propertySources.get(0).getName());
            Assertions.assertEquals("application-mytest", propertySources.get(1).getName());
            Assertions.assertEquals("application-denis", propertySources.get(2).getName());
            Assertions.assertEquals("system", propertySources.get(3).getName());
            Assertions.assertEquals("env", propertySources.get(4).getName());
            Assertions.assertEquals( "testworld", propertySources.get(1).get("hellotest"));
            Assertions.assertEquals( "world", propertySources.get(2).get("hello"));
        }
    }

    @Test
    void testPropertySourcesLocatorHasPreviousPropertySources() {
        try (ApplicationContext context = ApplicationContext.builder()
            .environments("denis")
            .propertySources(PropertySource.of(Map.of("myKey", "myValue")))
            .propertySourcesLocator(new PropertySourcesLocator() {

                @Override
                public Collection<PropertySource> load(Environment environment) {
                    if (!environment.getActiveNames().contains("test")) {
                        return List.of();
                    }
                    List<PropertySource> propertySources = new ArrayList<>(environment.getPropertySources());
                    Assertions.assertEquals(4, propertySources.size());
                    Assertions.assertEquals("application", propertySources.get(0).getName());
                    Assertions.assertEquals("application-denis", propertySources.get(1).getName());
                    Assertions.assertEquals("system", propertySources.get(2).getName());
                    Assertions.assertEquals("env", propertySources.get(3).getName());
                    Map<String, Object> values = new HashMap<>();
                    for (PropertySource propertySource : propertySources) {
                        for (String key : propertySource) {
                            values.put(key, propertySource.get(key));
                        }
                    }
                    values.put("myKey", values.get("myKey") + "_modified");
                    values.put("hello", values.get("hello") + "_modified");
                    return List.of(PropertySource.of(values));
                }
            })
            .build().start()) {
            Assertions.assertEquals("world_modified", context.get("hello", String.class).get());
            Assertions.assertEquals("myValue_modified", context.get("myKey", String.class).get());
        }
    }
}
