/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.context.annotation;

import io.micronaut.core.annotation.Experimental;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * <p>An annotation to apply to an interface to indicate which implementation
 * is the default implementation.</p>
 *
 * <p>When a bean is looked up and if there are multiple possible candidates with
 * no concrete {@link Primary} this annotation will impact bean selection by
 * selecting the default implementation.</p>
 *
 * <p>It should be noted that {@link Primary} and {@link io.micronaut.core.order.Ordered}
 * take precedence over this an annotation and a fallback to the default implementation only
 * occurs if no primary candidate can be established.</p>
 *
 * <p>Note that this annotation also has an impact on bean replacement via {@link Replaces}
 * to another class to allow the replacement of an implementation that isn't
 * accessible due to visibility restrictions.</p>
 *
 * <p>For example:</p>
 *
 * <pre class="code">
 * &#064;DefaultImplementation(MyImpl.class)
 * public interface SomeInterface {
 *
 * }
 *
 * class MyImpl implements SomeInterface {
 *
 * }
 *
 * &#064;Replaces(SomeInterface.class)
 * class OtherImpl implements SomeInterface {
 *
 * }
 * </pre>
 *
 * <p>In the above example the {@code OtherImpl} bean will replace the
 * {@code MyImpl} bean because the class in the {@link Replaces} annotation
 * has a default implementation.</p>
 *
 * @author James Kleeh
 * @since 1.2.0
 */
@Documented
@Retention(RUNTIME)
@Target(ElementType.TYPE)
@Experimental
@Inherited
public @interface DefaultImplementation {

    /**
     * @return The bean type that is the default implementation
     */
    @AliasFor(member = "name")
    Class<?> value() default void.class;

    /**
     * @return The fully qualified bean type name that is the default implementation
     */
    String name() default "";
}
