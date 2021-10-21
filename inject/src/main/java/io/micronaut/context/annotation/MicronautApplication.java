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
package io.micronaut.context.annotation;
import io.micronaut.core.annotation.Experimental;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.RetentionPolicy.SOURCE;

/**
 * <p>Annotation used to mark a Micronaut application entry point.</p>
 *
 *
 * <pre class="code">
 * &#064;MicronautApplication
 * public class Application implements ApplicationContextCustomizer {
 *
 *     &#064;Override
 *     public void configure(ApplicationContextBuilder builder) {
 *         // configure the application context
 *     }
 *
 *     public static void main(String... args) {
 *         Micronaut.run(Application.class, args);
 *     }
 *
 * }</pre>
 *
 * @since 3.2
 */
@Documented
@Retention(SOURCE)
@Target(ElementType.TYPE)
@ContextConfigurer
@Experimental
public @interface MicronautApplication {
}
