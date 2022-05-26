package io.micronaut.inject.provider;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.context.annotation.Requires;

import jakarta.inject.Provider;


@Requires(property = "spec.name", value = "ProviderNamedInjectionSpec")
@EachBean(BeanNumber.class)
public class ErrorEachBeanProvider {

    private final String name;

    public ErrorEachBeanProvider(@Parameter String name,
                                 @Parameter Provider<NotABean> notABeanProvider) {

        this.name = name;
    }

    public String getName() {
        return name;
    }
}
