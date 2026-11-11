package io.micronaut.web.router;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationMetadataDelegate;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class DefaultFilterRouteMatchingAnnotationTest {

    @Test
    void theAbsenceOfAMatchingAnnotationIsLookedUpOnce() {
        AtomicInteger lookups = new AtomicInteger();
        AnnotationMetadataDelegate metadata = new AnnotationMetadataDelegate() {
            @Override
            public AnnotationMetadata getAnnotationMetadata() {
                return AnnotationMetadata.EMPTY_METADATA;
            }

            @Override
            public Optional<String> getAnnotationNameByStereotype(String stereotype) {
                lookups.incrementAndGet();
                return Optional.empty();
            }
        };
        DefaultFilterRoute route = new DefaultFilterRoute(() -> {
            throw new IllegalStateException("The filter is not needed to find the matching annotation");
        }, metadata, false);

        // a filter without a matching annotation is the common one, and the router asks for each request
        for (int i = 0; i < 5; i++) {
            assertNull(route.findMatchingAnnotation());
        }

        assertEquals(1, lookups.get());
    }
}
