package io.micronaut.inject.beanbuilder;

import io.micronaut.aop.InterceptorKind;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.inject.annotation.TypedAnnotationTransformer;
import io.micronaut.inject.visitor.VisitorContext;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class TestInterceptorBindingTransformer implements TypedAnnotationTransformer<SomeInterceptorBinding> {
    static final List<AnnotationValue<?>> ANNOTATION_VALUES = Arrays.asList(
            AnnotationValue.builder(io.micronaut.aop.InterceptorBinding.class)
                    .member("kind", InterceptorKind.AROUND)
                    .build(),
            AnnotationValue.builder(io.micronaut.aop.InterceptorBinding.class)
                    .member("kind", InterceptorKind.AROUND_CONSTRUCT)
                    .build(),
            AnnotationValue.builder(io.micronaut.aop.InterceptorBinding.class)
                    .member("kind", InterceptorKind.PRE_DESTROY)
                    .build(),
            AnnotationValue.builder(io.micronaut.aop.InterceptorBinding.class)
                    .member("kind", InterceptorKind.POST_CONSTRUCT)
                    .build()

    );
    @Override
    public Class<SomeInterceptorBinding> annotationType() {
        return SomeInterceptorBinding.class;
    }

    @Override
    public List<AnnotationValue<?>> transform(AnnotationValue<SomeInterceptorBinding> annotation, VisitorContext visitorContext) {
        return ANNOTATION_VALUES;
    }
}
