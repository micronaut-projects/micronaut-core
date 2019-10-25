package io.micronaut.http.filter;

import io.micronaut.http.HttpRequest;

import java.util.List;

public interface HttpFilterResolver {

    List<? extends HttpFilter> resolveFilters(HttpRequest<?> request);
}
