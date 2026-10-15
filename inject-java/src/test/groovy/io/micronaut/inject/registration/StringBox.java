package io.micronaut.inject.registration;

import io.micronaut.context.annotation.Prototype;

@Prototype
public class StringBox implements Box<String> {

    @Override
    public String value() {
        return "string";
    }
}
