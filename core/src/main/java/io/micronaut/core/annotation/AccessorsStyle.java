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
package io.micronaut.core.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Annotate a class (typically a Java Bean) to make it explicit the style of its accessors when not using the standard
 * getter/setters. This is necessary, for example when using Lombok fluent accessors:
 *
 * <pre class="code">
 * &#064;AccessorsStyle(readPrefixes = {""}, writePrefixes = {""})
 * &#064;Accessors(fluent = true)
 * &#064;Getter
 * &#064;Setter
 * public class Person {
 *
 *     private String name;
 *     private int age;
 *
 *     public Person(String name, int age) {
 *         this.name = name;
 *         this.age = age;
 *     }
 * }</pre>
 * <p>
 * Give the previous class, Lombok will generate getters and setter: {@code name()}, {@code age()}, {@code name(String)}
 * and {@code age(int)}. Defining the {@code readPrefixes} and {@code writePrefixes} as empty strings makes Micronaut
 * aware of them.
 *
 * @author Iván López
 * @since 3.2.0
 */
@Documented
@Retention(RUNTIME)
@Target({ElementType.TYPE, ElementType.ANNOTATION_TYPE})
@Inherited
@Experimental
public @interface AccessorsStyle {

    /**
     * The default read prefix.
     */
    String DEFAULT_READ_PREFIX = "get";

    /**
     * The default write prefix.
     */
    String DEFAULT_WRITE_PREFIX = "set";

    /**
     * @return The valid read prefixes.
     */
    String[] readPrefixes() default {DEFAULT_READ_PREFIX};

    /**
     * @return The valid write prefixes.
     */
    String[] writePrefixes() default {DEFAULT_WRITE_PREFIX};
}
