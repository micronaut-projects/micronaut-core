package io.micronaut.docs.config.mapFormat;

import io.micronaut.context.annotation.ConfigurationProperties;
import javax.validation.constraints.Min;
import java.util.Map;

// tag::imports[]
import io.micronaut.core.convert.format.MapFormat;
// end::imports[]

// tag::class[]
@ConfigurationProperties("my.engine")
public class EngineConfig {
    public int getCylinders() {
        return cylinders;
    }

    public void setCylinders(int cylinders) {
        this.cylinders = cylinders;
    }

    public Map<Integer, String> getSensors() {
        return sensors;
    }

    public void setSensors(Map<Integer, String> sensors) {
        this.sensors = sensors;
    }

    @Min(1L)
    private int cylinders;
    @MapFormat(transformation = MapFormat.MapTransformation.FLAT) //<1>
    private Map<Integer, String> sensors;
}
// end::class[]
