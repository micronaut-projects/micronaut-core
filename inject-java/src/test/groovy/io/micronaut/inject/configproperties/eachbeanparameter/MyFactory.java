package io.micronaut.inject.configproperties.eachbeanparameter;

import io.micronaut.context.annotation.Context;
import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Parameter;

@Factory
public class MyFactory {

    @EachBean(AbstractDataSource.class)
    MyHelper buildHelper() {
        return new MyHelper();
    }

    @Context // The context should load without properties and properly fill the parameter from DefaultDataSource
    @EachBean(MyHelper.class)
    MyBean buildBean(@Parameter String name) {
        // The parameter should be correctly resolved for the case where DefaultDataSource is annotated
        // with both @Named and @Primary making the qualifier composite
        return new MyBean(name);
    }

}
