package io.micronaut.inject.generics.closest;

public interface ExceptionHandler<T extends Throwable, R> {

    R handle(Object request, T exception);
}
