package io.micronaut.inject.field.mapinjection;

import io.micronaut.context.annotation.Factory;
import jakarta.inject.Singleton;

import java.util.Collections;
import java.util.Map;

@Factory
public class MapFactory {

    @Singleton
    Map<String, Foo> foo() {
      return Collections.singletonMap("one", new Foo());
    }

    public static class Foo {
    }
}
