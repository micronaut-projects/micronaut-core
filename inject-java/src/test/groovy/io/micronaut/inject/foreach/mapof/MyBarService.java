package io.micronaut.inject.foreach.mapof;

import io.micronaut.context.annotation.Requires;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

@Requires(property = "spec.name", value = "MapOfNameSpec")
@Named("bar")
@Singleton
public class MyBarService implements MyService {
}
