/*
 * Copyright 2017-2023 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.web.router;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.ObjectUtils;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.body.MessageBodyHandlerRegistry;
import io.micronaut.inject.MethodExecutionHandle;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * The default {@link StatusRouteInfo} implementation.
 *
 * @param <T> The target
 * @param <R> The result
 * @author Denis Stepanov
 * @since 4.0.0
 */
@Internal
public final class DefaultStatusRouteInfo<T, R> extends DefaultRequestMatcher<T, R> implements StatusRouteInfo<T, R> {

    private final Class<?> originatingType;
    private final int statusCode;
    private final ConversionService conversionService;

    public DefaultStatusRouteInfo(Class<?> originatingType,
                                  int statusCode,
                                  MethodExecutionHandle<T, R> targetMethod,
                                  @Nullable
                                  String bodyArgumentName,
                                  @Nullable
                                  Argument<?> bodyArgument,
                                  List<MediaType> consumesMediaTypes,
                                  List<MediaType> producesMediaTypes,
                                  List<Predicate<HttpRequest<?>>> predicates,
                                  ConversionService conversionService,
                                  MessageBodyHandlerRegistry messageBodyHandlerRegistry) {
        super(targetMethod, bodyArgument, bodyArgumentName, consumesMediaTypes, producesMediaTypes, true, true, predicates, messageBodyHandlerRegistry);
        this.originatingType = originatingType;
        this.statusCode = statusCode;
        this.conversionService = conversionService;
    }

    @Override
    public Class<?> originatingType() {
        return originatingType;
    }

    @Override
    public HttpStatus status() {
        return HttpStatus.valueOf(statusCode);
    }

    @Override
    public int statusCode() {
        return statusCode;
    }

    @Override
    public HttpStatus findStatus(HttpStatus defaultStatus) {
        return super.findStatus(status());
    }

    @Override
    public Optional<RouteMatch<R>> match(Class<?> originatingClass, HttpStatus status) {
        if (originatingClass == this.originatingType && this.statusCode == status.getCode()) {
            return Optional.of(new StatusRouteMatch<>(this, conversionService));
        }
        return Optional.empty();
    }

    @Override
    public Optional<RouteMatch<R>> match(HttpStatus status) {
        if (this.originatingType == null && this.statusCode == status.getCode()) {
            return Optional.of(new StatusRouteMatch<>(this, conversionService));
        }
        return Optional.empty();
    }

    @Override
    public Optional<RouteMatch<R>> match(int statusCode) {
        if (this.originatingType == null && this.statusCode == statusCode) {
            return Optional.of(new StatusRouteMatch<>(this, conversionService));
        }
        return Optional.empty();
    }

    @Override
    public Optional<RouteMatch<R>> match(Class<?> originatingClass, int statusCode) {
        if (originatingClass == this.originatingType && this.statusCode == statusCode) {
            return Optional.of(new StatusRouteMatch<>(this, conversionService));
        }
        return Optional.empty();
    }

    @Override
    public int hashCode() {
        return ObjectUtils.hash(super.hashCode(), statusCode, originatingType);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DefaultStatusRouteInfo<?, ?> that)) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        return statusCode == that.statusCode &&
                Objects.equals(originatingType, that.originatingType);
    }

    @Override
    public String toString() {
        return " " +
            statusCode +
            " -> " +
            getTargetMethod().getDeclaringType().getSimpleName() +
            '#' +
            getTargetMethod();
    }
}
