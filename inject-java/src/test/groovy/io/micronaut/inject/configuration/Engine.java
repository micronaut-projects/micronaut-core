package io.micronaut.inject.configuration;

public class Engine {
    private final String manufacturer;

    public Engine(String manufacturer) {
        this.manufacturer = manufacturer;
    }

    public String getManufacturer() {
        return manufacturer;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String manufacturer = "Ford";

        public Builder withManufacturer(String manufacturer) {
            this.manufacturer = manufacturer;
            return this;
        }

        public Engine build() {
            return new Engine(manufacturer);
        }
    }
}
