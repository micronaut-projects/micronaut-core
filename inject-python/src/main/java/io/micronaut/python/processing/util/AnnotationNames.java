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
package io.micronaut.python.processing.util;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.inject.ast.ClassElement;

/**
 * Name and type helpers shared by the annotation metadata builder and the stub generators.
 *
 * @since 5.2.0
 */
@Internal
public final class AnnotationNames {

    private AnnotationNames() {
    }

    /**
     * @param typeName A type name that may carry type arguments
     * @return The name without type arguments
     */
    public static String rawTypeName(String typeName) {
        int genericStart = typeName.indexOf('<');
        return genericStart > -1 ? typeName.substring(0, genericStart) : typeName;
    }

    /**
     * The annotation member a positional decorator argument maps to: the first is {@code value}, the
     * others {@code argN}.
     *
     * @param memberName A member name or a positional index
     * @return The member name
     */
    public static String memberName(Object memberName) {
        if (memberName instanceof Number number) {
            int index = number.intValue();
            return index == 0 ? AnnotationMetadata.VALUE_MEMBER : "arg" + index;
        }
        return memberName.toString();
    }

    /**
     * @param memberType An annotation member type
     * @return Whether the member holds an enum constant
     */
    public static boolean isEnumMember(@Nullable ClassElement memberType) {
        return memberType != null && (memberType.isEnum() || memberType.isAssignable(Enum.class));
    }
}
