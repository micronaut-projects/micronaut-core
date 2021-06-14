package io.micronaut.inject.any;

import jakarta.inject.Named;
import jakarta.inject.Singleton;

@Singleton
@Named("terrier")
public class Terrier implements Dog<Terrier> {
    @Override
    public String getRace() {
        return "terrier";
    }

    @Override
    public Class<Terrier> getType() {
        return Terrier.class;
    }
}
