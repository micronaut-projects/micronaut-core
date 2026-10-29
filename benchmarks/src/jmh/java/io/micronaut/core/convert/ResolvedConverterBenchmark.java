/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.core.convert;

import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.bind.binders.HeaderAnnotationBinder;
import io.micronaut.http.bind.binders.RequestArgumentBinder;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Converting a string to an argument with {@link ConversionService#convert(Object, ArgumentConversionContext)}
 * against the converter of {@link ConversionService#resolveConverter}, alone and in a header binder. The binders of the {@code service}
 * variants run with a conversion service that keeps the default {@link ConversionService#resolveConverter},
 * which converts with the service for every value.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class ResolvedConverterBenchmark {

    private static final String HEADER = "value";

    @Param({"int", "optionalLong", "listOfInteger", "string"})
    public String type;

    private Argument<Object> argument;
    private String value;
    private ConversionService conversionService;
    private TypeConverter<String, Object> resolvedConverter;
    private HttpRequest<?> request;
    private RequestArgumentBinder<Object> serviceBinder;
    private RequestArgumentBinder<Object> resolvedBinder;

    @SuppressWarnings("unchecked")
    @Setup
    public void setup() {
        Argument<?> target = switch (type) {
            case "int" -> Argument.of(int.class, "value");
            case "optionalLong" -> Argument.of(Optional.class, "value", Argument.of(Long.class));
            case "listOfInteger" -> Argument.of(List.class, "value", Argument.of(Integer.class));
            case "string" -> Argument.of(String.class, "value");
            default -> throw new IllegalArgumentException(type);
        };
        argument = (Argument<Object>) target;
        value = type.equals("listOfInteger") ? "1,2,3" : "12345";
        conversionService = new DefaultMutableConversionService();
        resolvedConverter = conversionService.resolveConverter(String.class, argument);
        request = HttpRequest.GET("/").header(HEADER, value);
        serviceBinder = new HeaderAnnotationBinder<>(new ServiceOnly(conversionService), argument);
        resolvedBinder = new HeaderAnnotationBinder<>(conversionService, argument);
        Object bound = bindHeaderResolved();
        if (!(bound instanceof Optional<?> result) || result.isEmpty() || !result.equals(bindHeaderService())) {
            throw new IllegalStateException("The binders do not bind the header: " + bound);
        }
    }

    @Benchmark
    public Object convertService() {
        return conversionService.convert(value, ConversionContext.of(argument));
    }

    @Benchmark
    public Object convertResolved() {
        return resolvedConverter.convert(value, argument.getType(), ConversionContext.of(argument));
    }

    @Benchmark
    public Object bindHeaderService() {
        return serviceBinder.bind(ConversionContext.of(argument), request).getValue();
    }

    @Benchmark
    public Object bindHeaderResolved() {
        return resolvedBinder.bind(ConversionContext.of(argument), request).getValue();
    }

    /**
     * A conversion service that converts with the given one, and resolves converters as any service does by default.
     */
    private record ServiceOnly(ConversionService delegate) implements ConversionService {

        @Override
        public <S, T> Optional<T> convert(S object, Class<? super S> sourceType, Class<T> targetType, ConversionContext context) {
            return delegate.convert(object, sourceType, targetType, context);
        }

        @Override
        public <S, T> boolean canConvert(Class<S> sourceType, Class<T> targetType) {
            return delegate.canConvert(sourceType, targetType);
        }
    }
}
