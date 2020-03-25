/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
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
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * <p>An annotation to apply to an interface to indicate which implementation
 * is the default implementation. The initial use case is to redirect {@link Replaces}
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
public @interface DefaultImplementation {

    /**
     * @return The bean type that is the default implementation
     */
    Class value() default void.class;

    /**
     * @return The fully qualified bean type name that is the default implementation
     */
    String name() default "";
}
