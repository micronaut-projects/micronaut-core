package io.micronaut.inject.generics.closest;

import javax.inject.Singleton;

@Singleton
public class CustomErrorExceptionHandler implements ExceptionHandler<CustomError, String> {

    @Override
    public String handle(Object request, CustomError exception) {
        return null;
    }
}
