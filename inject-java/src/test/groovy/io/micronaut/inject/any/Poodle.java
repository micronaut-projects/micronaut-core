package io.micronaut.inject.any;

import jakarta.inject.Named;
import jakarta.inject.Singleton;

@Singleton
@Named("poodle")
public class Poodle implements Dog<Poodle> {
    @Override
    public String getRace() {
        return "poodle";
    }

    @Override
    public Class<Poodle> getType() {
        return Poodle.class;
    }
}
