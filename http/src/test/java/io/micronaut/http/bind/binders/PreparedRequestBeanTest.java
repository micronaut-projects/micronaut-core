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
package io.micronaut.http.bind.binders;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.bind.ArgumentBinder;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.convert.exceptions.ConversionErrorException;
import io.micronaut.core.bind.exceptions.UnsatisfiedArgumentException;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.uri.QueryStringDecoder;
import io.micronaut.http.annotation.Header;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.http.annotation.RequestBean;
import io.micronaut.http.bind.DefaultRequestBinderRegistry;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Annotation;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class PreparedRequestBeanTest {
    private static final AnnotationMetadata REQUEST_BEAN = new AnnotationMetadata() {
        @Override
        public boolean hasAnnotation(Class<? extends Annotation> type) { return type == RequestBean.class; }
    };

    private static MutableHttpRequest<?> request(String uri) {
        var request = HttpRequest.GET(uri);
        new QueryStringDecoder(uri).parameters()
            .forEach((name, values) -> values.forEach(value -> request.getParameters().add(name, value)));
        return request;
    }

    @Test
    void constructorMembersArePreparedOnceAndResultsAreMaterializedOnce() {
        AtomicInteger lookups = new AtomicInteger();
        var registry = new DefaultRequestBinderRegistry(ConversionService.SHARED) {
            @Override
            public <T> Optional<ArgumentBinder<T, HttpRequest<?>>> findArgumentBinder(Argument<T> argument) {
                lookups.incrementAndGet();
                return super.findArgumentBinder(argument);
            }
        };
        var argument = Argument.of(Inputs.class, REQUEST_BEAN);
        var binder = new RequestBeanAnnotationBinder<Inputs>(registry).createSpecific(argument);
        int preparedLookups = lookups.get();
        Inputs.constructed = 0;
        var first = binder.bind(ConversionContext.of(argument), request("/?count=7&name=first"));
        assertSame(first.get(), first.get());
        assertTrue(first.isConvertedToArgumentType());
        assertEquals(7, first.get().count());
        assertEquals(1, Inputs.constructed);
        var second = binder.bind(ConversionContext.of(argument), request("/?count=8&name=second"));
        assertEquals(8, second.get().count());
        assertEquals("first", first.get().name());
        assertNotSame(first.get(), second.get());
        assertEquals(preparedLookups, lookups.get());
        assertEquals(2, Inputs.constructed);
    }

    @Test
    void pendingMembersAreRequestLocalAndLateReadsCreateOneBean() {
        var source = new PendingHeader();
        var registry = new DefaultRequestBinderRegistry(ConversionService.SHARED);
        registry.addArgumentBinder(source);
        var argument = Argument.of(LateInputs.class, REQUEST_BEAN);
        var binder = new RequestBeanAnnotationBinder<LateInputs>(registry).createSpecific(argument);
        LateInputs.constructed = 0;
        var first = binder.bind(ConversionContext.of(argument), request("/"));
        var firstPending = source.last;
        var second = binder.bind(ConversionContext.of(argument), request("/"));
        assertTrue(first.isPending());
        assertTrue(first.getValue().isEmpty());
        assertEquals(0, LateInputs.constructed);
        firstPending.value = "first";
        assertFalse(first.isPending());
        assertEquals("first", first.get().token());
        assertSame(first.get(), first.get());
        assertTrue(second.isPending());
        source.last.value = "second";
        assertEquals("second", second.get().token());
        assertEquals(2, LateInputs.constructed);
    }

    @Test
    void missingInvalidOptionalAndSetterValuesRetainTheirBehavior() {
        var registry = new DefaultRequestBinderRegistry(ConversionService.SHARED);
        var argument = Argument.of(Inputs.class, REQUEST_BEAN);
        var binder = new RequestBeanAnnotationBinder<Inputs>(registry).createSpecific(argument);
        assertThrows(UnsatisfiedArgumentException.class,
            () -> binder.bind(ConversionContext.of(argument), request("/?name=test")));
        assertThrows(ConversionErrorException.class,
            () -> binder.bind(ConversionContext.of(argument), request("/?count=bad&name=test")));
        var setterArg = Argument.of(SetterInputs.class, REQUEST_BEAN);
        var setters = new RequestBeanAnnotationBinder<SetterInputs>(registry).createSpecific(setterArg);
        var result = setters.bind(ConversionContext.of(setterArg), request("/?count=12"));
        assertEquals(12, result.get().getCount());
        assertEquals(Optional.empty(), result.get().getName());
        assertSame(result.get(), result.get());
    }

    @Test
    void completeOptionalResultsAreNotWrappedAgain() {
        var registry = new DefaultRequestBinderRegistry(ConversionService.SHARED);
        registry.addArgumentBinder(new AnnotatedRequestArgumentBinder<QueryValue, Object>() {
            @Override
            public Class<QueryValue> getAnnotationType() { return QueryValue.class; }
            @Override
            public BindingResult<Object> bind(ArgumentConversionContext<Object> context, HttpRequest<?> request) {
                return BindingResult.converted(context.getArgument().isOptional() ? Optional.empty() : 12);
            }
        });
        var argument = Argument.of(SetterInputs.class, REQUEST_BEAN);
        var binder = new RequestBeanAnnotationBinder<SetterInputs>(registry).createSpecific(argument);
        var value = binder.bind(ConversionContext.of(argument), request("/")).get();
        assertEquals(12, value.getCount());
        assertEquals(Optional.empty(), value.getName());
    }

    @Test
    void subclassesRetainTheirBindingImplementation() {
        var binder = new RequestBeanAnnotationBinder<Inputs>(new DefaultRequestBinderRegistry(ConversionService.SHARED)) {
            @Override
            public BindingResult<Inputs> bind(ArgumentConversionContext<Inputs> context, HttpRequest<?> request) {
                return BindingResult.converted(new Inputs(1, "custom"));
            }
        };
        var argument = Argument.of(Inputs.class, REQUEST_BEAN);
        var specific = binder.createSpecific(argument);
        assertSame(binder, specific);
        assertEquals("custom", specific.bind(ConversionContext.of(argument), request("/")).get().name());
    }

    @Test
    void legacyDirectBindingStillWorks() {
        var argument = Argument.of(Inputs.class, REQUEST_BEAN);
        var binder = new RequestBeanAnnotationBinder<Inputs>(new DefaultRequestBinderRegistry(ConversionService.SHARED));
        assertEquals(9, binder.bind(ConversionContext.of(argument), request("/?count=9&name=direct")).get().count());
    }

    @Introspected
    record Inputs(@QueryValue int count, @QueryValue String name) {
        static int constructed;
        Inputs { constructed++; }
    }

    @Introspected
    record LateInputs(@Header String token) {
        static int constructed;
        LateInputs { constructed++; }
    }

    @Introspected
    public static class SetterInputs {
        @QueryValue
        private int count;
        @QueryValue
        private Optional<String> name;
        public int getCount() { return count; }
        public void setCount(int count) { this.count = count; }
        public Optional<String> getName() { return name; }
        public void setName(Optional<String> name) { this.name = name; }
    }

    static final class PendingHeader implements AnnotatedRequestArgumentBinder<Header, String> {
        PendingValue last;
        @Override
        public Class<Header> getAnnotationType() { return Header.class; }
        @Override
        public BindingResult<String> bind(ArgumentConversionContext<String> context, HttpRequest<?> source) {
            last = new PendingValue();
            // Exercise an immediate result whose mapped child waits for the request.
            return BindingResult.converted("source").flatMap(v -> last);
        }
    }

    static final class PendingValue implements PendingRequestBindingResult<String> {
        @Nullable String value;
        @Override
        public boolean isPending() { return value == null; }
        @Override
        public Optional<String> getValue() { return Optional.ofNullable(value); }
    }
}
