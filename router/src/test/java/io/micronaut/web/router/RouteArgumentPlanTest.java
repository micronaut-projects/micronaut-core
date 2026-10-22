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
package io.micronaut.web.router;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.bind.ArgumentBinder.BindingResult;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.convert.ConversionError;
import io.micronaut.core.convert.exceptions.ConversionErrorException;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.uri.QueryStringDecoder;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Header;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.http.annotation.RequestAttribute;
import io.micronaut.http.bind.DefaultRequestBinderRegistry;
import io.micronaut.http.bind.binders.AnnotatedRequestArgumentBinder;
import io.micronaut.http.bind.binders.PendingRequestBindingResult;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class RouteArgumentPlanTest {
    private static MutableHttpRequest<?> request(String uri) {
        var request = HttpRequest.GET(uri);
        new QueryStringDecoder(uri).parameters()
            .forEach((name, values) -> values.forEach(value -> request.getParameters().add(name, value)));
        return request;
    }

    @Test
    void requestsSharePreparationAndKeepIndependentValuesAndFilterPhases() {
        try (var context = ApplicationContext.run(Map.of("spec.name", "RouteArgumentPlanTest"))) {
            Router router = context.getBean(Router.class);
            var registry = new DefaultRequestBinderRegistry(ConversionService.SHARED);
            var firstRequest = request("/binding-plan/first?count=7");
            var secondRequest = request("/binding-plan/second?count=8");
            var first = router.findClosest(firstRequest);
            var second = router.findClosest(secondRequest);
            assertSame(((DefaultMethodBasedRouteInfo<?, ?>) first.getRouteInfo()).getArgumentPlan(),
                ((DefaultMethodBasedRouteInfo<?, ?>) second.getRouteInfo()).getArgumentPlan());
            first.fulfillBeforeFilters(registry, firstRequest);
            second.fulfillBeforeFilters(registry, secondRequest);
            assertFalse(first.isFulfilled());
            assertTrue(first.isSatisfied("id"));
            firstRequest.setAttribute("token", "one");
            secondRequest.setAttribute("token", "two");
            second.fulfillAfterFilters(registry, secondRequest);
            first.fulfillAfterFilters(registry, firstRequest);
            assertEquals("first:7:one", first.execute());
            assertEquals("second:8:two", second.execute());
        }
    }

    @Test
    void nullableUnmatchedArgumentsStayOpenUntilAfterFilters() {
        try (var context = ApplicationContext.run(Map.of("spec.name", "RouteArgumentPlanTest"))) {
            Router router = context.getBean(Router.class);
            var registry = new DefaultRequestBinderRegistry(ConversionService.SHARED);
            for (boolean suppliedByFilter : List.of(true, false)) {
                var request = request("/binding-plan/nullable");
                var match = router.findClosest(request);
                match.fulfillBeforeFilters(registry, request);
                assertFalse(match.isSatisfied("item"));
                assertFalse(match.isFulfilled());
                if (suppliedByFilter) {
                    request.setAttribute("item", new Input("from-filter"));
                }
                match.fulfillAfterFilters(registry, request);
                assertEquals(suppliedByFilter ? "from-filter" : "null", match.execute());
            }
        }
    }

    @Test
    void filtersCanReplaceUnmatchedConversionErrorsAndUnresolvedErrorsArePreserved() {
        try (var context = ApplicationContext.run(Map.of("spec.name", "RouteArgumentPlanTest"))) {
            Router router = context.getBean(Router.class);
            var registry = new DefaultRequestBinderRegistry(ConversionService.SHARED);
            ConversionError error = () -> new IllegalArgumentException("invalid count");
            // Optional conversion may treat invalid input as absent; supply an actual binder error.
            registry.addUnmatchedRequestArgumentBinder((conversionContext, request) -> {
                if (!conversionContext.getArgument().isOptional()) {
                    return BindingResult.unsatisfied();
                }
                return new BindingResult<>() {
                    @Override
                    public Optional<Object> getValue() { return Optional.empty(); }
                    @Override
                    public List<ConversionError> getConversionErrors() { return List.of(error); }
                };
            });
            for (String path : List.of("error", "nullable-error", "optional-error")) {
                for (boolean suppliedByFilter : List.of(true, false)) {
                    var request = request("/binding-plan/" + path + "?count=bad");
                    var match = router.findClosest(request);
                    match.fulfillBeforeFilters(registry, request);
                    assertFalse(match.isSatisfied("count"));
                    assertFalse(match.isFulfilled());
                    if (suppliedByFilter) {
                        request.setAttribute("count", 7);
                    }
                    // The executor checks readiness before applying the postponed binders.
                    assertFalse(match.isFulfilled());
                    match.fulfillAfterFilters(registry, request);
                    if (suppliedByFilter) {
                        assertEquals("count=7", match.execute());
                    } else {
                        var failure = assertThrows(ConversionErrorException.class, match::execute);
                        assertEquals("count", failure.getArgument().getName());
                    }
                }
            }
        }
    }

    @Test
    void anExplicitQueryConversionErrorStillFailsBeforeFilters() {
        try (var context = ApplicationContext.run(Map.of("spec.name", "RouteArgumentPlanTest"))) {
            Router router = context.getBean(Router.class);
            var registry = new DefaultRequestBinderRegistry(ConversionService.SHARED);
            var request = request("/binding-plan/item?count=bad");
            var match = router.findClosest(request);
            assertThrows(ConversionErrorException.class, () -> match.fulfillBeforeFilters(registry, request));
        }
    }

    @Test
    void aDeferredConversionErrorCanBeReplacedWhileAnotherArgumentIsPending() {
        try (var context = ApplicationContext.run(Map.of("spec.name", "RouteArgumentPlanTest"))) {
            Router router = context.getBean(Router.class);
            var pending = new PendingToken();
            var registry = new DefaultRequestBinderRegistry(ConversionService.SHARED);
            registry.addArgumentBinder(new AnnotatedRequestArgumentBinder<Header, String>() {
                @Override
                public Class<Header> getAnnotationType() { return Header.class; }
                @Override
                public BindingResult<String> bind(ArgumentConversionContext<String> argument, HttpRequest<?> request) {
                    return pending;
                }
            });
            var request = request("/binding-plan/pending-error?count=bad");
            var match = router.findClosest(request);
            match.fulfillBeforeFilters(registry, request);
            assertFalse(match.isFulfilled());
            request.setAttribute("count", 7);
            match.fulfillAfterFilters(registry, request);
            assertFalse(match.isFulfilled());
            pending.ready = true;
            assertTrue(match.isFulfilled());
            assertEquals("7:later", match.execute());
        }
    }

    @Test
    void resultsAreReadOnceAndAnImmediateMappedResultCanWait() {
        try (var context = ApplicationContext.run(Map.of("spec.name", "RouteArgumentPlanTest"))) {
            AtomicInteger reads = new AtomicInteger();
            var pending = new PendingToken();
            var registry = new DefaultRequestBinderRegistry(ConversionService.SHARED);
            registry.addArgumentBinder(new AnnotatedRequestArgumentBinder<Header, String>() {
                    @Override
        public Class<Header> getAnnotationType() { return Header.class; }
                    @Override
        public BindingResult<String> bind(ArgumentConversionContext<String> arg, HttpRequest<?> request) {
                        if (request.getParameters().contains("pending")) {
                            return BindingResult.converted("source").flatMap(v -> pending);
                        }
                        return () -> { reads.incrementAndGet(); return Optional.of("value"); };
                    }
                });
            Router router = context.getBean(Router.class);
            var request = request("/binding-plan/token");
            var match = router.findClosest(request);
            match.fulfillBeforeFilters(registry, request);
            assertTrue(match.isFulfilled());
            assertEquals("value", match.execute());
            assertEquals(1, reads.get());
            var later = request("/binding-plan/token?pending=true");
            var waiting = router.findClosest(later);
            waiting.fulfillBeforeFilters(registry, later);
            waiting.fulfillAfterFilters(registry, later);
            assertFalse(waiting.isFulfilled());
            pending.ready = true;
            assertTrue(waiting.isFulfilled());
            assertEquals("later", waiting.execute());
        }
    }

    @Introspected
    record Input(String name) {
    }

    static final class PendingToken implements PendingRequestBindingResult<String> {
        boolean ready;
        @Override
        public boolean isPending() { return !ready; }
        @Override
        public Optional<String> getValue() { return ready ? Optional.of("later") : Optional.empty(); }
    }

    @Requires(property = "spec.name", value = "RouteArgumentPlanTest")
    @Controller("/binding-plan")
    static class TestController {
        @Get("/{id}")
        String values(String id, @QueryValue int count, @RequestAttribute String token) {
            return id + ":" + count + ":" + token;
        }
        @Get("/error")
        String error(int count) { return "count=" + count; }

        @Get("/nullable-error")
        String nullableError(@Nullable Integer count) { return "count=" + count; }

        @Get("/optional-error")
        String optionalError(Optional<Integer> count) { return "count=" + count.orElse(null); }

        @Get("/pending-error")
        String pendingError(int count, @Header String token) { return count + ":" + token; }

        @Get("/nullable")
        String nullable(@Nullable Input item) {
            return item == null ? "null" : item.name();
        }

        @Get("/token")
        String token(@Header String token) { return token; }
    }
}
