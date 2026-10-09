package io.micronaut.http;

import io.micronaut.core.convert.value.MutableConvertibleValues;
import io.micronaut.core.execution.ExecutionFlow;
import io.micronaut.http.simple.SimpleHttpRequest;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A binding that is not the route's, e.g. of an argument of a filter method, detaches the
 * conditions and the bodies of the route: without creating the attributes of a request that has
 * none, as nearly every request.
 */
class BasicHttpAttributesDetachTest {

    @Test
    void aRequestWithoutConditionsGetsNoAttributes() {
        LazyAttributesRequest request = new LazyAttributesRequest();

        BasicHttpAttributes.DetachedRouteState detached = BasicHttpAttributes.detachRouteState(request);
        BasicHttpAttributes.restoreRouteState(request, detached);
        BasicHttpAttributes.detachRouteWaitsFor(request, () -> { });

        assertNull(detached);
        assertEquals(0, request.created, "the attributes of the request were created");
    }

    @Test
    void theConditionsOfTheRouteAreRestoredAndThoseOfTheBindingDropped() {
        LazyAttributesRequest request = new LazyAttributesRequest();
        ExecutionFlow<?> route = ExecutionFlow.just("route");
        BasicHttpAttributes.addRouteWaitsFor(request, route);

        ExecutionFlow<?> binding = ExecutionFlow.just("binding");
        ExecutionFlow<?> waitsFor = BasicHttpAttributes.detachRouteWaitsFor(request, () -> {
            assertFalse(request.getAttribute("io.micronaut.http.BasicHttpAttributes.ROUTE_WAITS_FOR").isPresent(), "detached");
            BasicHttpAttributes.addRouteWaitsFor(request, binding);
        });

        assertSame(binding, waitsFor);
        assertSame(route, BasicHttpAttributes.getRouteWaitsFor(request));
    }

    @Test
    void theConditionsOfABindingOfARequestWithoutConditionsAreDropped() {
        LazyAttributesRequest request = new LazyAttributesRequest();

        BasicHttpAttributes.DetachedRouteState detached = BasicHttpAttributes.detachRouteState(request);
        BasicHttpAttributes.addRouteWaitsFor(request, ExecutionFlow.just("binding"));
        BasicHttpAttributes.restoreRouteState(request, detached);

        assertTrue(request.getAttribute("io.micronaut.http.BasicHttpAttributes.ROUTE_WAITS_FOR").isEmpty());
    }

    /**
     * A request that creates its attributes on the first call of {@link #getAttributes()}, like
     * the request of the Netty server.
     */
    private static final class LazyAttributesRequest extends SimpleHttpRequest<Object> {
        private @Nullable MutableConvertibleValues<Object> attributes;
        int created;

        LazyAttributesRequest() {
            super(HttpMethod.GET, "/", null);
        }

        @Override
        public MutableConvertibleValues<Object> getAttributes() {
            if (attributes == null) {
                created++;
                attributes = super.getAttributes();
            }
            return attributes;
        }

        @Override
        public Optional<Object> getAttribute(CharSequence name) {
            return attributes == null ? Optional.empty() : attributes.get(name.toString(), Object.class);
        }
    }
}
