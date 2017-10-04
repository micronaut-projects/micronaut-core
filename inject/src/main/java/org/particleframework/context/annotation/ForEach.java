/*
 * Copyright 2017 original authors
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
package org.particleframework.context.annotation;

import javax.inject.Singleton;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * <p>This annotation allows driving the production of {@link Bean} definitions from either configuration or the presence of another bean definition</p>
 *
 * <p>For example:</p>
 *
 * <pre><code>
 *  {@literal @}ForEach("foo.bar")
 *   public class ExampleConfiguration {
 *   }
 * </code></pre>
 *
 * <p>In the above example a new {@code Example} bean will be created for each item under the {@code foo.bar} key in application configuration</p>
 *
 * <p>One can then drive the configuration of other beans with the same annotation:</p>
 *
 * <pre><code>
 *  {@literal @}ForEach(ExampleConfiguration)
 *  {@literal @}Singleton
 *   public class ExampleBean {
 *      ExampleBean(ExampleConfiguration config) {
 *          ...
 *      }
 *   }
 * </code></pre>
 *
 *
 * @see ConfigurationProperties
 * @author Graeme Rocher
 * @since 1.0
 */
@Documented
@Retention(RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
@Singleton
public @interface ForEach {
    /**
     * @return The bean type that this bean is driven by
     */
    Class[] value() default {};

    /**
     * @return The property that this bean is driven by
     */
    String property() default "";

    /**
     * @return The name of the key returned by {@link #property()} that should be regarded as the {@link Primary} bean
     */
    String primary() default "";
}
