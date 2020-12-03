package io.micronaut.inject.provider;

import edu.umd.cs.findbugs.annotations.Nullable;
import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Parameter;

import javax.inject.Provider;


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
