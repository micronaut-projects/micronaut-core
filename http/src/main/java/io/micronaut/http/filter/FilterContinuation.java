package io.micronaut.http.filter;

import io.micronaut.http.HttpRequest;

public interface FilterContinuation<R> {
    R proceed(HttpRequest<?> request);
}
