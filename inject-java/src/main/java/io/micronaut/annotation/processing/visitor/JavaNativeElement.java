/*
 * Copyright 2017-2023 original authors
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
package io.micronaut.annotation.processing.visitor;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;

import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;

/**
 * The Java native element.
 *
 * @author Denis Stepanov
 * @since 4.0.0
 */
@Internal
public sealed interface JavaNativeElement {

    /**
     * @return The native element.
     */
    @Nullable
    Element element();

    /**
     * The class native element.
     * @param element The element
     * @param typeMirror The type mirror
     * @param owner The owner
     */
    record Class(TypeElement element, @Nullable TypeMirror typeMirror, @Nullable JavaNativeElement owner) implements JavaNativeElement {

        Class(TypeElement element) {
            this(element, null, null);
        }

        Class(TypeElement element, @Nullable TypeMirror typeMirror) {
            this(element, typeMirror, null);
        }

    }

    /**
     * The class native element.
     * @param element The element
     * @param typeVariable The type variable
     * @param owner The owner
     */
    record Placeholder(Element element,
                       TypeVariable typeVariable,
                       JavaNativeElement owner) implements JavaNativeElement {
    }

    /**
     * The method native element.
     * @param element The element
     */
    record Method(ExecutableElement element) implements JavaNativeElement {
    }

    /**
     * The variable native element.
     * @param element The element
     */
    record Variable(VariableElement element) implements JavaNativeElement {
    }

    /**
     * The package native element.
     * @param element The element
     */
    record Package(PackageElement element) implements JavaNativeElement {
    }

}
