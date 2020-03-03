package io.micronaut.docs.config.builder;

import io.micronaut.context.ApplicationContext;
import org.junit.Test;

import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;


public class VehicleSpec {
    @Test
    public void testStartVehicle() {
        // tag::start[]
        HashMap<String, Object> properties = new HashMap<>();
        properties.put("my.engine.cylinders"             ,"4");
        properties.put("my.engine.manufacturer"          , "Subaru");
        properties.put("my.engine.crank-shaft.rod-length", 4);
        properties.put("my.engine.spark-plug.name"       , "6619 LFR6AIX");
        properties.put("my.engine.spark-plug.type"       , "Iridium");
        properties.put("my.engine.spark-plug.companyName", "NGK");
        ApplicationContext applicationContext = ApplicationContext.run(properties, "test");

        Vehicle vehicle = applicationContext.getBean(Vehicle.class);
        System.out.println(vehicle.start());
        // end::start[]
        
        assertEquals("Subaru Engine Starting V4 [rodLength=4.0, sparkPlug=Iridium(NGK 6619 LFR6AIX)]", vehicle.start());
                
        applicationContext.close();
    }

}