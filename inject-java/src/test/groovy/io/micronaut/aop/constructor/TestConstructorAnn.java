package io.micronaut.aop.constructor;

import io.micronaut.aop.AroundConstruct;
import io.micronaut.aop.InterceptorBinding;
import io.micronaut.aop.InterceptorKind;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
@AroundConstruct
@InterceptorBinding(kind = InterceptorKind.POST_CONSTRUCT)
@InterceptorBinding(kind = InterceptorKind.PRE_DESTROY)
public @interface TestConstructorAnn {
}
