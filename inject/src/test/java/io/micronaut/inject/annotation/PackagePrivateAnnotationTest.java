package io.micronaut.inject.annotation;

import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.AnnotationValueProvider;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Annotation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A package private annotation type has to be synthesizable, a constraint annotation declared next to its
 * validator is one, and the Jakarta Validation API does not require constraints to be public.
 */
class PackagePrivateAnnotationTest {

    @Test
    void buildsAnAnnotationOfAPackagePrivateType() {
        PackagePrivateExample annotation = AnnotationMetadataSupport.buildAnnotation(
            PackagePrivateExample.class,
            AnnotationValue.builder(PackagePrivateExample.class).member("value", "x").build()
        );

        assertEquals("x", annotation.value());
        assertEquals("default", annotation.other());
        assertSame(PackagePrivateExample.class, annotation.annotationType());
        assertTrue(annotation instanceof AnnotationValueProvider);
        assertEquals("x", ((AnnotationValueProvider<?>) annotation).annotationValue().stringValue().orElse(null));
    }

    @Test
    void aPackagePrivateAnnotationComparesWithARegularInstance() {
        PackagePrivateExample synthesized = AnnotationMetadataSupport.buildAnnotation(
            PackagePrivateExample.class,
            AnnotationValue.builder(PackagePrivateExample.class).member("value", "x").build()
        );
        PackagePrivateExample same = AnnotationMetadataSupport.buildAnnotation(
            PackagePrivateExample.class,
            AnnotationValue.builder(PackagePrivateExample.class).member("value", "x").build()
        );
        PackagePrivateExample other = AnnotationMetadataSupport.buildAnnotation(
            PackagePrivateExample.class,
            AnnotationValue.builder(PackagePrivateExample.class).member("value", "y").build()
        );
        PackagePrivateExample regular = new RegularInstance();

        assertEquals(synthesized, same);
        assertEquals(synthesized.hashCode(), same.hashCode());
        assertEquals(synthesized, regular);
        assertNotEquals(synthesized, other);
    }

    /**
     * An implementation written by hand, following the {@link Annotation} equals and hashCode contract, to compare
     * the synthesized annotation against.
     */
    private static final class RegularInstance implements PackagePrivateExample {

        @Override
        public String value() {
            return "x";
        }

        @Override
        public String other() {
            return "default";
        }

        @Override
        public Class<? extends Annotation> annotationType() {
            return PackagePrivateExample.class;
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof PackagePrivateExample other
                && value().equals(other.value())
                && this.other().equals(other.other());
        }

        @Override
        public int hashCode() {
            return (127 * "value".hashCode() ^ value().hashCode())
                + (127 * "other".hashCode() ^ other().hashCode());
        }
    }
}
