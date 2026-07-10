package io.micronaut.http.bind.binders;

import io.micronaut.core.annotation.*;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.annotation.QueryValue;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Annotation;
import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class QueryValueArgumentBinderTest {
    @Introspected
    static class NullableTestType {
        @Creator
        public static NullableTestType getInstance() {
            return null;
        }
    }

    @Introspected
    static class NonNullTestType {
        private final String value;

        public String getValue() {
            return value;
        }

        private NonNullTestType(String value) {
            this.value = value;
        }

        @Creator
        public static NonNullTestType getInstance(@NonNull String value) {
            return new NonNullTestType(value);
        }
    }

    QueryValueArgumentBinder<NullableTestType> nullableTestTypeQueryValueArgumentBinder =
        new QueryValueArgumentBinder<>(ConversionService.SHARED);
    QueryValueArgumentBinder<NonNullTestType> nonNullableTestTypeQueryValueArgumentBinder =
        new QueryValueArgumentBinder<>(ConversionService.SHARED);

    private static final AnnotationMetadata NULLABLE_ANNOTATION_METADATA = new AnnotationMetadata() {
        @Override
        public boolean hasStereotype(@Nullable String annotation) {
            assert annotation != null;
            return annotation.equals(AnnotationUtil.NULLABLE);
        }

        @Override
        public boolean hasAnnotation(@Nullable Class<? extends Annotation> annotation) {
            assert annotation != null;
            return annotation.equals(QueryValue.class);
        }
    };
    private static final AnnotationMetadata NON_NULLABLE_ANNOTATION_METADATA = new AnnotationMetadata() {
        @Override
        public boolean hasAnnotation(@Nullable Class<? extends Annotation> annotation) {
            assert annotation != null;
            return annotation.equals(QueryValue.class);
        }
    };

    @Test
    void shouldBindEmptyWhenInstanceCreatedByIntrospectionIsNullAndArgumentIsNullable() {
        var context = new ArgumentConversionContext<NullableTestType>() {
            @Override
            @NonNull
            public Argument<NullableTestType> getArgument() {
                return Argument.of(NullableTestType.class, NULLABLE_ANNOTATION_METADATA);
            }
        };
        var source = get("/");


        var bound = nullableTestTypeQueryValueArgumentBinder.bind(context, source);

        assertTrue(bound.isSatisfied());
        assertFalse(bound.getValue().isPresent());
    }

    @Test
    void shouldBindUnsatisfiedWhenInstanceCreatedByIntrospectionIsNullAndArgumentIsNotNullable() {
        var context = new ArgumentConversionContext<NullableTestType>() {
            @Override
            @NonNull
            public Argument<NullableTestType> getArgument() {
                return Argument.of(NullableTestType.class, NON_NULLABLE_ANNOTATION_METADATA);
            }
        };
        var source = get("/");


        var bound = nullableTestTypeQueryValueArgumentBinder.bind(context, source);


        assertFalse(bound.isSatisfied());
        assertFalse(bound.getValue().isPresent());
    }


    @Test
    void shouldBindWithValueWhenInstanceCreatedByIntrospectionIsNotNullAndArgumentIsNullableAndCreatorSucceeds() {
        var context = new ArgumentConversionContext<NonNullTestType>() {
            @Override
            @NonNull
            public Argument<NonNullTestType> getArgument() {
                return Argument.of(NonNullTestType.class, NULLABLE_ANNOTATION_METADATA);
            }
        };
        var expected = "test-value";
        var source = get("/", Map.of("value", expected));

        var bound = nonNullableTestTypeQueryValueArgumentBinder.bind(context, source);


        assertTrue(bound.isSatisfied());
        assertTrue(bound.isPresentAndSatisfied());
        assertEquals(expected, bound.get().getValue());
    }

    @Test
    void shouldBindWithValueWhenInstanceCreatedByIntrospectionIsNotNullAndArgumentIsNotNullableAndCreatorSucceeds() {
        var context = new ArgumentConversionContext<NonNullTestType>() {
            @Override
            @NonNull
            public Argument<NonNullTestType> getArgument() {
                return Argument.of(NonNullTestType.class, NON_NULLABLE_ANNOTATION_METADATA);
            }
        };
        var expected = "test-value";
        var source = get("/", Map.of("value", expected));


        var bound = nonNullableTestTypeQueryValueArgumentBinder.bind(context, source);


        assertTrue(bound.isSatisfied());
        assertTrue(bound.isPresentAndSatisfied());
        assertEquals(expected, bound.get().getValue());
    }

    @Test
    void shouldBindUnsatisfiedWhenInstanceCreatedByIntrospectionIsNotNullAndArgumentIsNullableAndCreatorFails() {
        var context = new ArgumentConversionContext<NonNullTestType>() {
            @Override
            @NonNull
            public Argument<NonNullTestType> getArgument() {
                return Argument.of(NonNullTestType.class, NULLABLE_ANNOTATION_METADATA);
            }
        };
        var source = get("/");


        var bound = nonNullableTestTypeQueryValueArgumentBinder.bind(context, source);


        assertFalse(bound.isSatisfied());
        assertFalse(bound.getValue().isPresent());
    }

    @Test
    void shouldBindUnsatisfiedWhenInstanceCreatedByIntrospectionIsNotNullAndArgumentIsNotNullableAndCreatorFails() {
        var context = new ArgumentConversionContext<NonNullTestType>() {
            @Override
            @NonNull
            public Argument<NonNullTestType> getArgument() {
                return Argument.of(NonNullTestType.class, NON_NULLABLE_ANNOTATION_METADATA);
            }
        };
        var source = get("/");


        var bound = nonNullableTestTypeQueryValueArgumentBinder.bind(context, source);


        assertFalse(bound.isSatisfied());
        assertFalse(bound.getValue().isPresent());
    }

    HttpRequest<?> get(String uri) {
        return get(uri, Collections.emptyMap());
    }

    HttpRequest<?> get(String uri, Map<String, String> queryParams) {
        var request = HttpRequest.GET(uri);
        var params = request.getParameters();
        queryParams.forEach(params::add);

        return request;
    }
}
