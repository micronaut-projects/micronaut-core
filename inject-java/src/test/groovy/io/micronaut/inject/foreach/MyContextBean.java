package io.micronaut.inject.foreach;

import io.micronaut.context.annotation.Context;
import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.core.naming.Named;
import org.jetbrains.annotations.NotNull;

@EachBean(MyConfiguration.class)
@Context
public class MyContextBean implements Named {
    final MyConfiguration configuration;
    private final String named;

    MyContextBean(@Parameter String named, MyConfiguration configuration) {
        this.configuration = configuration;
        this.named = named;
    }

    public MyConfiguration getConfiguration() {
        return configuration;
    }

    @NotNull
    @Override
    public String getName() {
        return named;
    }
}
