/*
 * Copyright 2017-2021 original authors
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
package io.micronaut.aop;

import io.micronaut.core.annotation.Retainable;
import java.lang.annotation.Annotation;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

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
@Retainable
public @interface InterceptorBinding {

    /**
     * The bind members name.
     */
    String META_BIND_MEMBERS = "bindMembers";

    /**
     * When declared on an interceptor, the value of this annotation can be used to indicate the annotation the
     * {@link MethodInterceptor} binds to at runtime.
     *
     * @return The annotation type the interceptor binds to.
     */
    Class<? extends Annotation> value() default Annotation.class;

    /**
     * @return The kind of interceptor.
     */
    InterceptorKind kind() default InterceptorKind.AROUND;

    /**
     * By default, annotation members are not used when resolving interceptors. The value of
     * {@code bindMembers()} can be set to {@code true} to indicate that annotation members
     * should be used when binding interceptors to an annotation declaration.
     *
     * <p>The {@link io.micronaut.context.annotation.NonBinding} annotation should
     * then be used on any annotation members that should not be included in this binding computation.</p>
     *
     * @return Whether members should be included in interceptor binding
     * @see io.micronaut.context.annotation.NonBinding
     * @since 3.3.0
     */
    boolean bindMembers() default false;

    /**
     * Declared on a binding annotation, the value of {@code lazyInterceptorsPerTarget()} can be set to {@code true} to
     * have every proxy that fronts a separate target, and that is bound by this binding, resolve its interceptors for
     * each target, as {@link Around#lazyInterceptorsPerTarget()} does.
     *
     * <p>A proxy is bound by the {@link InterceptorKind#AROUND} bindings of the bean and of its intercepted methods,
     * and by the {@link InterceptorKind#AROUND_CONSTRUCT} bindings of its constructor. Such a binding opts the proxy in
     * wherever its annotation is declared: on the bean, on one of those members, or on the factory member producing
     * the bean. It does so whatever {@link Around} the bean declares or its scope contributes, so a bean of a scope such
     * as {@code RequestScope} needs no {@code Around} of its own. Either this or
     * {@link Around#lazyInterceptorsPerTarget()} is enough. The value is read at compile time, and it changes nothing
     * for a proxy without a separate target, or when declared on an interceptor.</p>
     *
     * @return Whether the proxies with a separate target bound by this binding resolve their interceptors for each target
     * @see Around#lazyInterceptorsPerTarget()
     * @since 5.3.0
     */
    boolean lazyInterceptorsPerTarget() default false;
}
