package io.micronaut.inject.env;

import io.micronaut.context.annotation.Property;
import io.micronaut.context.annotation.PropertySource;

import javax.inject.Singleton;

@PropertySource({
        @Property(name="x", value="${from.config}")
})
@Singleton
public class B {
}
