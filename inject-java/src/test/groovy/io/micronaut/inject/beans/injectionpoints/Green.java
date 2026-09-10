package io.micronaut.inject.beans.injectionpoints;

import jakarta.inject.Named;
import jakarta.inject.Singleton;

@Singleton
@Named("green")
public class Green implements Colour {

    @Override
    public String name() {
        return "green";
    }
}
