package io.micronaut.inject.reflection;

class ShadowedBase {

    @Tag("super")
    String value = "inherited";
}
