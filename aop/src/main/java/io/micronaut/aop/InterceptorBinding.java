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

import io.micronaut.core.annotation.Experimental;
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
     * The name of the {@link #matchAllOccurrences()} member.
     *
     * @since 5.3.0
     */
    String META_MATCH_ALL_OCCURRENCES = "matchAllOccurrences";

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
     * Whether the occurrences of a repeatable binding annotation are matched as one group, the way the single
     * occurrence of a binding annotation that does not repeat is matched. It only has an effect together with
     * {@link #bindMembers()}.
     *
     * <p>By default, an interceptor applies when any one of the occurrences declared on it matches any one of the
     * occurrences at the interception point, and the occurrences declared on a method are added to those declared
     * on its class. When this member is {@code true}:</p>
     *
     * <ul>
     *     <li>every occurrence declared on the interceptor must match an occurrence at the interception point,
     *     which may declare more occurrences than the interceptor;</li>
     *     <li>the occurrences declared on a method or a constructor replace those declared on its class, and a
     *     method or constructor declaring none uses those of its class.</li>
     * </ul>
     *
     * <p>Members annotated with {@link io.micronaut.context.annotation.NonBinding} are left out of the comparison.
     * This is the selection that Jakarta Interceptors and CDI specify for repeatable interceptor bindings.</p>
     *
     * @return Whether the occurrences of the binding annotation are matched as one group
     * @since 5.3.0
     */
    @Experimental
    boolean matchAllOccurrences() default false;
}
