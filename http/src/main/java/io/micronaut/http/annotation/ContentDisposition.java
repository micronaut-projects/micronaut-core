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
package io.micronaut.http.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * <p>Declares that the response returned by the annotated route should include a
 * {@code Content-Disposition} header, independent of the method's return type.</p>
 *
 * <pre class="code">
 * &#064;Get("/report")
 * &#064;ContentDisposition(filename = "report.csv")
 * Flux&lt;String&gt; report() {
 *     ...
 * }
 * </pre>
 *
 * <p>Set {@link #type()} to {@code "inline"} to ask the browser to display the response instead of
 * downloading it.</p>
 *
 * @author Shubham Jain
 * @since 4.10.0
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface ContentDisposition {

    /**
     * @return The disposition type, either {@code attachment} or {@code inline}. Defaults to {@code attachment}.
     */
    String type() default "attachment";

    /**
     * @return The filename to include in the header. If left blank, no filename parameter is added.
     */
    String filename() default "";
}
