package io.micronaut.aop;

import java.lang.annotation.*;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * An {@code InterceptorBinding} is used as a meta-annotation on {@link Around} and {@link Introduction} advice to
 * indicate that AOP advice should be applied to the method and that any annotations that feature this stereotype annotation
 * should be used to resolve associated interceptors at runtime.
 *
 * @author graemerocher
 * @since 2.4.0
 */
@Documented
@Retention(RUNTIME)
@Target({ElementType.ANNOTATION_TYPE, ElementType.TYPE})
@Repeatable(InterceptorBindingDefinitions.class)
public @interface InterceptorBinding {
    /**
     * When declared on an interceptor, the value of this annotation can be used to indicate the annotation the
     * {@link MethodInterceptor} binds to at runtime.
     *
     * @return The annotation types the interceptor binds to.
     */
    Class<? extends Annotation> value() default Annotation.class;

    /**
     * Also allow the given interceptor type for this binding definition.
     *
     * @return The interceptor type.
     */
    Class<? extends Interceptor> interceptorType() default Interceptor.class;

    /**
     * @return The kind of interceptor.
     */
    InterceptorKind kind() default InterceptorKind.AROUND;
}