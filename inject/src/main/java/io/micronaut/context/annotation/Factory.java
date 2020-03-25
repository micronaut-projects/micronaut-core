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

import javax.inject.Singleton;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * <p>A factory is a {@link Singleton} that produces one or many other bean implementations.</p>
 *
 * <p>Each produced bean is defined by method that is annotated with {@link Bean}</p>
 *
 * <pre class="code">
 * &#064;Factory
 * public class MyFactory {
 *
 *     &#064;Bean
 *     public MyBean myBean() {
 *         // create the bean
 *     }
 * }</pre>
 *
 * <p>Methods defined within the body of the class that are annotated with {@link Bean} will be exposed as beans.</p>
 *
 * <p>You can use a {@link javax.inject.Scope} annotation to control the scope the bean is exposed within. For example for a
 * singleton instance you can annotation the method with {@link Singleton}.</p>
 *
 * <p>Methods annotated with {@link Bean} can accept arguments and Micronaut will attempt to inject those arguments from existing beans or values. For example:</p>
 *
 * <pre class="code">
 * &#064;Factory
 * public class MyFactory {
 *
 *     &#064;Bean
 *     public MyBean myBean(&#064;Value("foo.bar") String myValue) {
 *         // create the bean
 *     }
 * }</pre>
 *
 * @see Bean
 * @see Configuration
 * @author Graeme Rocher
 * @since 1.0
 */
@Singleton
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Factory {
}
