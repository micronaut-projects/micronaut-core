package io.micronaut.inject.any;

import javax.inject.Named;
import javax.inject.Singleton;

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
