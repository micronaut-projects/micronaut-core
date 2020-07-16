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
package io.micronaut.management.endpoint.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Annotation that can be applied to endpoint methods to control sensitivity at the method level.
 *
 * For example:
 * <code>
 *     @Endpoint(id = "loggers", prefix = "myapp")
 *     public class LoggersEndpoint {
 *
 *         @Write
 *         @Sensitive(property = "write-sensitive", defaultValue = true)
 *         public void setLogLevel@Selector String name) {
 *
 *         }
 *     }
 * </code>
 *
 * The configuration key <code>myapp.loggers.write-sensitive</code> will determine the sensitivity
 * of the method, defaulting to true if not present.
 *
 * @author James Kleeh
 * @since 2.0.0
 */
@Documented
@Retention(RUNTIME)
@Target({ElementType.METHOD})
public @interface Sensitive {

    /**
     * @return True if the method is sensitive
     */
    boolean value() default true;

    /**
     * @return The configuration key to determine sensitivity. Automatically
     * determines the configuration prefix from the endpoint annotation.
     */
    String property() default "";

    /**
     * Only to be used in conjunction with {@link #property()}.
     * @return The default value if the configuration key is not present
     */
    boolean defaultValue() default true;
}
