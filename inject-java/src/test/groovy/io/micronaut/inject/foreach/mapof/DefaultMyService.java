package io.micronaut.inject.foreach.mapof;

import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;

@Requires(property = "spec.name", value = "MapOfNameSpec")
@Singleton
public class DefaultMyService implements MyService {

}
