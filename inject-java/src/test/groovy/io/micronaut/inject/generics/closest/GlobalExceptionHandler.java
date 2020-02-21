package io.micronaut.inject.generics.closest;

import javax.inject.Singleton;

@Singleton
public class GlobalExceptionHandler implements ExceptionHandler<Throwable, String> {

    @Override
    public String handle(Object request, Throwable exception) {
        return null;
    }
}
