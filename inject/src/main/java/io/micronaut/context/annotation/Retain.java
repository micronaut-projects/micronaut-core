/*
 * Copyright 2017-2026 original authors
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
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Marks a singleton that survives a restart of the application context in development mode: the
 * instance is not destroyed with the stopped context and is not created again, the next context
 * adopts it. It suits what is expensive to create and independent of the application's classes,
 * such as a connection pool, and is declared by the module that defines the bean, on the bean's
 * class or on the factory method that produces it.
 *
 * <p>The annotation has no effect outside development mode. In development mode the bean is
 * retained only when it is safe to: its class and everything it received come from the runtime
 * classpath rather than the application's reloadable classes, and it holds nothing bound to the
 * stopped context, such as the context itself, a provider or a lazy proxy. A bean that is not safe
 * to retain is created again, and the log says why.</p>
 *
 * <p>The decision is read from the bean definition's annotation metadata, without loading the
 * bean's class, by {@link io.micronaut.context.reload.AnnotatedBeanRetentionPolicy}.</p>
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Experimental
@Documented
@Retention(RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface Retain {

    /**
     * The configuration prefixes a change under which releases the retained bean: the next context
     * creates it again from the changed configuration, so that a changed connection URL produces a new
     * pool. A data source would name {@code datasources}, for example.
     *
     * @return The configuration prefixes, empty when no configuration change releases the bean
     */
    String[] invalidatedBy() default {};
}
