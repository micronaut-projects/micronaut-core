package io.micronaut.inject.beanbuilder;

import io.micronaut.aop.InterceptorKind;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.inject.annotation.TypedAnnotationTransformer;
import io.micronaut.inject.visitor.VisitorContext;

import java.util.Collections;
import java.util.List;

public class TestInterceptorBindingTransformer implements TypedAnnotationTransformer<SomeInterceptorBinding> {
    @Override
    public Class<SomeInterceptorBinding> annotationType() {
        return SomeInterceptorBinding.class;
    }

    @Override
    public List<AnnotationValue<?>> transform(AnnotationValue<SomeInterceptorBinding> annotation, VisitorContext visitorContext) {
        return Collections.singletonList(
                AnnotationValue.builder(io.micronaut.aop.InterceptorBinding.class)
                    .member("kind", InterceptorKind.AROUND, InterceptorKind.AROUND_CONSTRUCT, InterceptorKind.POST_CONSTRUCT, InterceptorKind.PRE_DESTROY)
                    .build()
        );
    }
}
