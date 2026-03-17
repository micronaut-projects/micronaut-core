package io.micronaut.http.server.netty.binding;

import io.micronaut.core.annotation.Introspected;

@Introspected
public record PaginationRequest(Integer page, Integer size) {}
