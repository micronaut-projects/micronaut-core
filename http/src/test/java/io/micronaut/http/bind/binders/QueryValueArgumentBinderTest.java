package io.micronaut.http.bind.binders;

import io.micronaut.core.annotation.*;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.annotation.QueryValue;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
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

    @Introspected
    static class FilterTestType {
        @Nullable
        private Long id;
        @Nullable
        private String type;

        @Nullable
        public Long getId() {
            return id;
        }

        public void setId(@Nullable Long id) {
            this.id = id;
        }

        @Nullable
        public String getType() {
            return type;
        }

        public void setType(@Nullable String type) {
            this.type = type;
        }
    }

    @Introspected
    static class FailingCreatorTestType {
        @Creator
        public static FailingCreatorTestType getInstance(@Nullable String value) {
            throw new IllegalStateException("Creator failed");
        }
    }

    QueryValueArgumentBinder<FilterTestType> filterTestTypeQueryValueArgumentBinder =
        new QueryValueArgumentBinder<>(ConversionService.SHARED);
    QueryValueArgumentBinder<FailingCreatorTestType> failingCreatorTestTypeQueryValueArgumentBinder =
        new QueryValueArgumentBinder<>(ConversionService.SHARED);
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
    void shouldBindEmptyWhenNoValuesArePresentAndArgumentIsNullable() {
        var context = new ArgumentConversionContext<NonNullTestType>() {
            @Override
            @NonNull
            public Argument<NonNullTestType> getArgument() {
                return Argument.of(NonNullTestType.class, NULLABLE_ANNOTATION_METADATA);
            }
        };
        var source = get("/");


        var bound = nonNullableTestTypeQueryValueArgumentBinder.bind(context, source);


        assertTrue(bound.isSatisfied());
        assertFalse(bound.getValue().isPresent());
    }

    @Test
    void shouldBindEmptyWhenNoBeanPropertyIsPresentAndArgumentIsNullable() {
        var context = new ArgumentConversionContext<FilterTestType>() {
            @Override
            @NonNull
            public Argument<FilterTestType> getArgument() {
                return Argument.of(FilterTestType.class, NULLABLE_ANNOTATION_METADATA);
            }
        };
        var source = get("/");


        var bound = filterTestTypeQueryValueArgumentBinder.bind(context, source);


        assertTrue(bound.isSatisfied());
        assertFalse(bound.getValue().isPresent());
    }

    @Test
    void shouldBindUnsatisfiedWhenNoBeanPropertyIsPresentAndArgumentIsNotNullable() {
        var context = new ArgumentConversionContext<FilterTestType>() {
            @Override
            @NonNull
            public Argument<FilterTestType> getArgument() {
                return Argument.of(FilterTestType.class, NON_NULLABLE_ANNOTATION_METADATA);
            }
        };
        var source = get("/");


        var bound = filterTestTypeQueryValueArgumentBinder.bind(context, source);


        assertFalse(bound.isSatisfied());
        assertFalse(bound.getValue().isPresent());
    }

    @Test
    void shouldBindWithValueWhenSomeBeanPropertiesArePresent() {
        var context = new ArgumentConversionContext<FilterTestType>() {
            @Override
            @NonNull
            public Argument<FilterTestType> getArgument() {
                return Argument.of(FilterTestType.class, NULLABLE_ANNOTATION_METADATA);
            }
        };
        var source = get("/", Map.of("type", "CARD"));


        var bound = filterTestTypeQueryValueArgumentBinder.bind(context, source);


        assertTrue(bound.isPresentAndSatisfied());
        assertEquals("CARD", bound.get().getType());
        assertNull(bound.get().getId());
    }

    @Test
    void shouldBindUnsatisfiedWhenValuesArePresentAndCreatorFailsAndArgumentIsNullable() {
        var context = new ArgumentConversionContext<FailingCreatorTestType>() {
            @Override
            @NonNull
            public Argument<FailingCreatorTestType> getArgument() {
                return Argument.of(FailingCreatorTestType.class, NULLABLE_ANNOTATION_METADATA);
            }
        };
        var source = get("/", Map.of("value", "test-value"));


        var bound = failingCreatorTestTypeQueryValueArgumentBinder.bind(context, source);


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
