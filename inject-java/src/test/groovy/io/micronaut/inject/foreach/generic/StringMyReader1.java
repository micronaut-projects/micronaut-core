package io.micronaut.inject.foreach.generic;

import jakarta.inject.Singleton;

@Singleton
public class StringMyReader1 implements MyReader1<String> {
    @Override
    public String read() {
        return "";
    }
}
