package io.micronaut.docs.factories.nullable;

import io.micronaut.context.ApplicationContext;
import org.junit.Test;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class EngineSpec {

    @Test
    public void testEngineNull() {
        Map<String, Object> configuration = new HashMap<>();
        configuration.put("engines.subaru.cylinders", 4);
        configuration.put("engines.ford.cylinders", 8);
        configuration.put("engines.ford.enabled", false);
        configuration.put("engines.lamborghini.cylinders", 12);
        ApplicationContext applicationContext = ApplicationContext.run(configuration);

        Collection<Engine> engines = applicationContext.getBeansOfType(Engine.class);

        assertEquals("There are 2 engines", 2, engines.size());
        int totalCylinders = engines.stream().mapToInt(Engine::getCylinders).sum();
        assertEquals("Subaru + Lamborghini equals 16 cylinders", 16, totalCylinders);
    }
}
