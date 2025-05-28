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
package io.micronaut.inject.ast;

import io.micronaut.core.annotation.Experimental;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The marker annotation to indicate that this {@link ClassElement} is imported using {@link io.micronaut.context.annotation.ClassImport}.
 *
 * @author Denis Stepanov
 * @since 4.9
 */
@Experimental
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.SOURCE)
public @interface ImportedClass {

    /**
     * @return The package to write any kind of generated metadata to. By default, uses the class package.
     */
    String targetPackage() default "";

    /**
     * @return The class of the originating class.
     */
    String originatingClass();
}
