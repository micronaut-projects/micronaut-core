/*
 * Copyright 2017-2018 original authors
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

package io.micronaut.inject.processing;

import javax.lang.model.element.Element;
import javax.lang.model.element.Name;
import javax.lang.model.element.NestingKind;
import javax.lang.model.element.TypeElement;

/**
 * Utility methods for Java model handling.
 *
 * @author graemerocher
 * @since 1.0
 */
public class JavaModelUtils {

    /**
     * Get the class name for the given type element. Handles {@link NestingKind}.
     *
     * @param typeElement The type element
     * @return The class name
     */
    public static String getClassName(TypeElement typeElement) {
        Name qualifiedName = typeElement.getQualifiedName();
        NestingKind nestingKind = typeElement.getNestingKind();
        if (nestingKind == NestingKind.MEMBER) {
            TypeElement enclosingElement = typeElement;
            StringBuilder builder = new StringBuilder();
            while (nestingKind == NestingKind.MEMBER) {
                builder.insert(0, '$').insert(1, enclosingElement.getSimpleName());
                Element enclosing = enclosingElement.getEnclosingElement();

                if (enclosing instanceof TypeElement) {
                    enclosingElement = (TypeElement) enclosing;
                    nestingKind = enclosingElement.getNestingKind();
                } else {
                    break;
                }
            }
            Name enclosingName = enclosingElement.getQualifiedName();
            return enclosingName.toString() + builder;
        } else {
            return qualifiedName.toString();
        }
    }
}
