package io.micronaut.docs.config.properties;

// tag::imports[]
import io.micronaut.context.annotation.ConfigurationProperties;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
// end::imports[]
import java.util.Optional;

// tag::class[]
@ConfigurationProperties("my.engine") // <1>
public class EngineConfig {
    public String getManufacturer() {
        return manufacturer;
    }

    public void setManufacturer(String manufacturer) {
        this.manufacturer = manufacturer;
    }

    public int getCylinders() {
        return cylinders;
    }

    public void setCylinders(int cylinders) {
        this.cylinders = cylinders;
    }

    public CrankShaft getCrankShaft() {
        return crankShaft;
    }

    public void setCrankShaft(CrankShaft crankShaft) {
        this.crankShaft = crankShaft;
    }

    @NotBlank // <2>
    private String manufacturer = "Ford"; // <3>
    @Min(1L)
    private int cylinders;
    private CrankShaft crankShaft = new CrankShaft();

    @ConfigurationProperties("crank-shaft")
    public static class CrankShaft { // <4>
        public Optional<Double> getRodLength() {
            return rodLength;
        }

        public void setRodLength(Optional<Double> rodLength) {
            this.rodLength = rodLength;
        }

        private Optional<Double> rodLength = Optional.empty(); // <5>
    }
}
// end::class[]