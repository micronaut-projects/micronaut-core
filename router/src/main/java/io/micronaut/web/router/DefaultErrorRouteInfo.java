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
 * The default error route info implementation.
 *
 * @param <T> The target
 * @param <R> The result
 * @author Denis Stepanov
 * @since 4.0.0
 */
@Internal
public final class DefaultErrorRouteInfo<T, R> extends DefaultRequestMatcher<T, R> implements ErrorRouteInfo<T, R> {

    @Nullable
    private final Class<?> originatingType;
    private final Class<? extends Throwable> exceptionType;
    private final ConversionService conversionService;

    public DefaultErrorRouteInfo(@Nullable Class<?> originatingType,
                                 Class<? extends Throwable> exceptionType,
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
        this.exceptionType = exceptionType;
        this.conversionService = conversionService;
    }

    @Override
    public Class<?> originatingType() {
        return originatingType;
    }

    @Override
    public Class<? extends Throwable> exceptionType() {
        return exceptionType;
    }

    @Override
    public Optional<RouteMatch<R>> match(Class<?> originatingClass, Throwable exception) {
        if (originatingClass == originatingType && exceptionType.isInstance(exception)) {
            return Optional.of(new ErrorRouteMatch<>(exception, this, conversionService));
        }
        return Optional.empty();
    }

    @Override
    public Optional<RouteMatch<R>> match(Throwable exception) {
        if (originatingType == null && exceptionType.isInstance(exception)) {
            return Optional.of(new ErrorRouteMatch<>(exception, this, conversionService));
        }
        return Optional.empty();
    }

    @Override
    public HttpStatus findStatus(HttpStatus defaultStatus) {
        return super.findStatus(defaultStatus == null ? HttpStatus.INTERNAL_SERVER_ERROR : defaultStatus);
    }

    @Override
    public int hashCode() {
        return ObjectUtils.hash(super.hashCode(), exceptionType, originatingType);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        DefaultErrorRouteInfo that = (DefaultErrorRouteInfo) o;
        return exceptionType.equals(that.exceptionType) &&
                Objects.equals(originatingType, that.originatingType);
    }

    @Override
    public String toString() {
        return new StringBuilder().append(' ')
                .append(exceptionType.getSimpleName())
                .append(" -> ")
                .append(getTargetMethod().getDeclaringType().getSimpleName())
                .append('#')
                .append(getTargetMethod())
                .toString();
    }
}
