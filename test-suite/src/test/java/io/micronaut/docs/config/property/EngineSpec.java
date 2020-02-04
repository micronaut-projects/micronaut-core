package io.micronaut.docs.config.property;

import io.micronaut.context.ApplicationContext;
import org.junit.Test;

import java.util.LinkedHashMap;

import static org.junit.Assert.assertEquals;

public class EngineSpec {

    @Test
    public void testStartVehicleWithConfiguration() {
        LinkedHashMap<String, Object> map = new LinkedHashMap<>(1);
        map.put("my.engine.cylinders", "8");
        map.put("my.engine.manufacturer", "Honda");
        ApplicationContext ctx = ApplicationContext.run(map);

        Engine engine = ctx.getBean(Engine.class);

        assertEquals("Honda", engine.getManufacturer());
        assertEquals(8, engine.getCylinders());

        ctx.close();
    }
}
