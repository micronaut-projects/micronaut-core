/*
 * Copyright 2017-2025 original authors
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
package io.micronaut.context.python.annotation;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.Internal;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Metadata for generated Python bridge classes.
 *
 * @author Micronaut Team
 * @since 5.2.0
 */
@Documented
@Inherited
@Internal
@Experimental
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface PythonClass {
    /**
     * @return The Python package, or {@code python} for top-level classes
     */
    String packageName();

    /**
     * @return The top-level Python import/member name
     */
    String rootName();

    /**
     * @return The nested member names below the root class
     */
    String[] nestedMemberNames() default {};

    /**
     * @return The class display name used in diagnostics
     */
    String displayName();

    /**
     * @return The stable class cache key used by pooled contexts
     */
    String cacheKey();
}
