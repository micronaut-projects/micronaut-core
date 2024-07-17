package io.micronaut.inject.foreach.mapof;

import groovy.transform.CompileStatic;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

@Requires(property = "spec.name", value = "MapOfNameSpec")
@Named("foo")
@Singleton
@CompileStatic
public class MyFooService implements MyService {
}
