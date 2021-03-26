package io.micronaut.inject.any;

import javax.inject.Named;
import javax.inject.Singleton;

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
