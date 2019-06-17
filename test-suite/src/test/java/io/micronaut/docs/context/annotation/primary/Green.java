package io.micronaut.docs.context.annotation.primary;

import io.micronaut.context.annotation.Primary;

//tag::imports[]
import io.micronaut.context.annotation.Requires;
import javax.inject.Singleton;
//end::imports[]

@Requires(property = "spec.name", value = "primaryspec")
//tag::clazz[]
@Primary
@Singleton
class Green implements ColorPicker {

    @Override
    public String color() {
        return "green";
    }
}
//end::clazz[]
