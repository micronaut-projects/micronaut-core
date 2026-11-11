package io.micronaut.visitors.imports.first;

import jakarta.inject.Named;

@Named("first")
public class FirstPackageBean {

    @Named("excluded")
    @Deprecated
    public static class Excluded {
    }
}
