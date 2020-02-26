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
package io.micronaut.aop;

import io.micronaut.context.annotation.DefaultScope;
import io.micronaut.context.annotation.Executable;
import io.micronaut.core.annotation.Internal;

import javax.inject.Singleton;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * <p>An {@link Adapter} is advice applicable to a method that will create an entirely new bean definition that delegates to the
 * annotated method.</p>
 *
 * <p>Typically used in conjunction with an interface or class that provides a "Single Abstract Method" (or SAM) type.</p>
 *
 * <p>An example of usage could be to introduce a {@link io.micronaut.context.event.ApplicationEventListener}:</p>
 *
 * <p>For example:</p>
 *
 * <pre><code>
 *  {@literal @}Adapter(ApplicationEventListener)
 *   public void onStartup(StartupEvent startupEvent) {
 *   }
 * </code></pre>
 *
 * <p>The above example will create a new bean that delegates to the {@code onStartup} method that is an instance of {@link io.micronaut.context.event.ApplicationEventListener}.
 * The generic types are aligned and populated from the types defined in the method signature</p>
 *
 * <p>This annotation can be used as a stereotype annotation. If for some reason the generated class cannot delegate to the method then a compilation error should occur.</p>
 *
 * @author graemerocher
 * @since 1.0
 */
@Documented
@Retention(RUNTIME)
@Target({ElementType.ANNOTATION_TYPE, ElementType.METHOD})
@DefaultScope(Singleton.class)
@Executable
public @interface Adapter {

    /**
     * The target interface to adapt.
     *
     * @return An interface to adapt
     */
    Class<?> value();

    /**
     * Internal attributes for the adapter annotation.
     */
    @Internal
    class InternalAttributes {
        public static final String ADAPTED_BEAN = "adaptedBean";
        public static final String ADAPTED_METHOD = "adaptedMethod";
        public static final String ADAPTED_ARGUMENT_TYPES = "adaptedArgumentTypes";
        public static final String ADAPTED_QUALIFIER = "adaptedQualifier";
    }
}
