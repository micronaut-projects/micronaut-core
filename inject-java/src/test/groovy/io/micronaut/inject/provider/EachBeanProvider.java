package io.micronaut.inject.provider;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Parameter;

import jakarta.inject.Provider;

@EachBean(BeanNumber.class)
public class EachBeanProvider {

    private final String name;

    public EachBeanProvider(@Parameter String name,
                            @Parameter @Nullable Provider<NotABean> notABeanProvider) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
