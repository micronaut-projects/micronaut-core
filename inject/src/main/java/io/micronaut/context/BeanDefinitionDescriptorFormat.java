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
package io.micronaut.context;

import io.micronaut.core.annotation.Internal;

/**
 * The codes of the descriptor of a bean definition, which the processors write into the
 * {@code META-INF/micronaut/io.micronaut.inject.BeanDefinitionReference} entry of each definition and a context with
 * lazy bean definitions reads: one definition of them for the writer and the reader.
 *
 * @author Álvaro Sánchez-Mariscal
 * @since 5.3.0
 */
@Internal
public final class BeanDefinitionDescriptorFormat {

    /**
     * The first four bytes of a descriptor: {@code MNBD}.
     */
    public static final int MAGIC = 0x4D4E4244;
    /**
     * The version of the format.
     */
    public static final int VERSION = 1;
    /**
     * The length of the header: the magic, the version and the length of the payload.
     */
    public static final int HEADER_LENGTH = 10;

    // the flags, each answering a question of a reference

    public static final int FLAG_CONTEXT_SCOPE = 1;
    public static final int FLAG_PARALLEL = 1 << 1;
    public static final int FLAG_PROXIED_BEAN = 1 << 2;
    public static final int FLAG_PROXY_TARGET = 1 << 3;
    public static final int FLAG_SINGLETON = 1 << 4;
    public static final int FLAG_PRIMARY = 1 << 5;
    public static final int FLAG_CONFIGURATION_PROPERTIES = 1 << 6;
    public static final int FLAG_CONTAINER_TYPE = 1 << 7;
    public static final int FLAG_REQUIRES_METHOD_PROCESSING = 1 << 8;
    public static final int FLAG_POST_LOAD_CONDITIONS = 1 << 9;
    public static final int FLAG_DECLARED_INDEXES = 1 << 10;
    public static final int FLAG_PROPERTY_EXPRESSIONS = 1 << 11;

    // the bits of an annotation, each answering a question of the annotation metadata

    public static final int MEMBERSHIP_DECLARED_ANNOTATION = 1;
    public static final int MEMBERSHIP_ANNOTATION = 1 << 1;
    public static final int MEMBERSHIP_DECLARED_STEREOTYPE = 1 << 2;
    public static final int MEMBERSHIP_STEREOTYPE = 1 << 3;

    // the kinds of values

    public static final int VALUE_STRING = 1;
    public static final int VALUE_BOOLEAN = 2;
    public static final int VALUE_BYTE = 3;
    public static final int VALUE_CHAR = 4;
    public static final int VALUE_SHORT = 5;
    public static final int VALUE_INT = 6;
    public static final int VALUE_LONG = 7;
    public static final int VALUE_FLOAT = 8;
    public static final int VALUE_DOUBLE = 9;
    public static final int VALUE_CLASS = 10;
    public static final int VALUE_ANNOTATION = 11;
    public static final int VALUE_ARRAY = 12;
    public static final int VALUE_WRAPPER_ARRAY = 13;
    public static final int VALUE_EMPTY_ARRAY = 14;

    // the kinds of conditions checked before a definition is loaded

    public static final int CONDITION_CLASSES = 1;
    public static final int CONDITION_MISSING_CLASSES = 2;
    public static final int CONDITION_ENVIRONMENTS = 3;
    public static final int CONDITION_NOT_ENVIRONMENTS = 4;
    public static final int CONDITION_ENTITIES = 5;
    public static final int CONDITION_PROPERTY = 6;
    public static final int CONDITION_MISSING_PROPERTY = 7;
    public static final int CONDITION_CONFIGURATION = 8;
    public static final int CONDITION_SDK = 9;
    public static final int CONDITION_RESOURCES = 10;
    public static final int CONDITION_OS = 11;
    public static final int CONDITION_NOT_OS = 12;

    private BeanDefinitionDescriptorFormat() {
    }
}
