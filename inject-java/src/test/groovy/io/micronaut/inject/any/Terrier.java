package io.micronaut.inject.any;

import javax.inject.Named;
import javax.inject.Singleton;

@Singleton
@Named("terrier")
public class Terrier implements Dog {
    @Override
    public String getRace() {
        return "terrier";
    }
}
