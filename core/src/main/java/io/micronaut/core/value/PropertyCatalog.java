/*
 * Copyright 2017-2024 original authors
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
package io.micronaut.core.value;

/**
 * The property catalog to use.
 *
 * @since 4.7.0
 */
public enum PropertyCatalog {
    /**
     * The catalog that contains the raw keys.
     */
    RAW,
    /**
     * The catalog that contains normalized keys. A key is normalized into
     * lower case hyphen separated form. For example an environment variable {@code FOO_BAR} would be
     * normalized to {@code foo.bar}.
     */
    NORMALIZED,
    /**
     * The catalog that contains normalized keys and also generated keys. A synthetic key can be generated from
     * an environment variable such as {@code FOO_BAR_BAZ} which will produce the following keys: {@code foo.bar.baz},
     * {@code foo.bar-baz}, and {@code foo-bar.baz}.
     */
    GENERATED
}
