package io.micronaut.inject.foreach.generic;

import jakarta.inject.Singleton;

@Singleton
public class StringMyReader2 implements MyReader2<String> {
    @Override
    public String read() {
        return "";
    }
}
