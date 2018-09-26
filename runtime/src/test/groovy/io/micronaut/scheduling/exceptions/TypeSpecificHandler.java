package io.micronaut.scheduling.exceptions;

import io.micronaut.core.reflect.exception.InstantiationException;
import io.micronaut.scheduling.TaskExceptionHandler;

import javax.inject.Singleton;

@Singleton
public class TypeSpecificHandler implements TaskExceptionHandler<Object, InstantiationException> {
    private Object bean;
    private InstantiationException throwable;

    @Override
    public void handle(Object bean, InstantiationException throwable) {
        this.bean = bean;
        this.throwable = throwable;
    }

    public Object getBean() {
        return bean;
    }

    public InstantiationException getThrowable() {
        return throwable;
    }
}
