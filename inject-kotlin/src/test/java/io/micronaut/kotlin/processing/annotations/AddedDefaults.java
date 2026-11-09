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
package io.micronaut.kotlin.processing.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * An annotation with defaults of every kind but a nested annotation, compiled outside the sources under test, that a
 * visitor adds by its type. See {@code AnnotationMemberDefaultsSpec}.
 */
@Retention(RetentionPolicy.RUNTIME)
public @interface AddedDefaults {

    String stringValue() default "foo";

    String emptyStringValue() default "";

    int intValue() default 42;

    boolean booleanValue() default true;

    ElementType enumValue() default ElementType.METHOD;

    Class<?> classValue() default String.class;

    String[] stringArray() default {"a", "b"};

    int[] intArray() default {1, 2};

    Class<?>[] classArray() default {String.class, Integer.class};

    ElementType[] enumArray() default {ElementType.FIELD, ElementType.TYPE};

    String[] emptyArray() default {};
}
