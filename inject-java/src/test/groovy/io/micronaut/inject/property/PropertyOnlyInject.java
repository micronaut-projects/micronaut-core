package io.micronaut.inject.property;

import io.micronaut.context.annotation.Property;

class PropertyOnlyInject {

    @Property(name = "my.int") int integer;
}
