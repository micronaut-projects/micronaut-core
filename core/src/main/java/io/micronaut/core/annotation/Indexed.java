/*
 * Copyright 2017-2019 original authors
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

package io.micronaut.core.annotation;

import java.lang.annotation.*;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * An annotation that can be used on types where there may be many implementations of a
 * particular interface. This triggers building of an index internal to the bean context that speeds up bean lookups by type.
 *
 * @since 1.1
 * @author graemerocher
 */
@Documented
@Retention(RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD, ElementType.ANNOTATION_TYPE})
@Repeatable(value = Indexes.class)
public @interface Indexed {
    /**
     * @return The indexed type
     */
    Class<?> value();
}
