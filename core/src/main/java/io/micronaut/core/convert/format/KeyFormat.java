/*
 * Copyright 2018 original authors
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
package io.micronaut.core.convert.format;
import io.micronaut.core.naming.conventions.StringConvention;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;

/**
 * Allows configuring the format of Map keys when injecting map configuration
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Documented
@Retention(RUNTIME)
public @interface KeyFormat {
    /**
     * @return The string conversion to use
     */
    StringConvention value();
}
