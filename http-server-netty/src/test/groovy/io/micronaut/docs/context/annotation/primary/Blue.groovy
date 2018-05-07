package io.micronaut.docs.context.annotation.primary

//tag::imports[]
import io.micronaut.context.annotation.Requires;
import javax.inject.Singleton;
//end::imports[]

@Requires(property = 'spec.name', value = 'primaryspec')
//tag::clazz[]
@Singleton
public class Blue implements ColorPicker {

    @Override
    public String color() {
        return "blue";
    }
}
//end::clazz[]

