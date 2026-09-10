package io.micronaut.inject.beans.injectionpoints;

import jakarta.inject.Named;
import jakarta.inject.Singleton;

@Singleton
@Named("red")
public class Red implements Colour {

    @Override
    public String name() {
        return "red";
    }
}
