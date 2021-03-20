package io.micronaut.inject.any;

import javax.inject.Named;
import javax.inject.Singleton;

@Singleton
@Named("poodle")
public class Poodle implements Dog {
    @Override
    public String getRace() {
        return "poodle";
    }
}
