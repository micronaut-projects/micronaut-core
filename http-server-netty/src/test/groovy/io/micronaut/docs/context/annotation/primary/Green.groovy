package io.micronaut.docs.context.annotation.primary

//tag::imports[]
import io.micronaut.context.annotation.Primary;
import io.micronaut.context.annotation.Requires;
import javax.inject.Singleton;
//end::imports[]

@Requires(property = 'spec.name', value = 'primaryspec')
//tag::clazz[]
@Primary
@Singleton
public class Green implements ColorPicker {

    @Override
    public String color() {
        return "green";
    }
}
//end::clazz[]

