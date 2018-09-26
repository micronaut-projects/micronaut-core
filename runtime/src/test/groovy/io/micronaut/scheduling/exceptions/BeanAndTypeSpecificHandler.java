package io.micronaut.scheduling.exceptions;

import io.micronaut.core.reflect.exception.InstantiationException;
import io.micronaut.scheduling.TaskExceptionHandler;

import javax.inject.Singleton;

@Singleton
public class BeanAndTypeSpecificHandler implements TaskExceptionHandler<ThrowsExceptionJob1, InstantiationException> {
    private ThrowsExceptionJob1 bean;
    private InstantiationException throwable;

    @Override
    public void handle(ThrowsExceptionJob1 bean, InstantiationException throwable) {
        this.bean = bean;
        this.throwable = throwable;
    }

    public ThrowsExceptionJob1 getBean() {
        return bean;
    }

    public InstantiationException getThrowable() {
        return throwable;
    }
}
