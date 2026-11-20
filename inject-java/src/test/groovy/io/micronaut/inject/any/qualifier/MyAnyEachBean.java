package io.micronaut.inject.any.qualifier;

import io.micronaut.context.annotation.Any;
import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.context.annotation.Requires;

@Any
@EachBean(MyAnyEngine.class)
@Requires(property = "spec.name", value = "AnyQualifierSpec")
public class MyAnyEachBean {

    private final MyAnyEngine engine;

    public MyAnyEachBean(@Parameter MyAnyEngine engine) {
        this.engine = engine;
    }

    public MyAnyEngine getEngine() {
        return engine;
    }
}
