/*
 * Copyright 2017-2020 original authors
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

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.inject.ast.EnumElement;

import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Implements the {@link EnumElement} interface for Java.
 *
 * @author graemerocher
 * @since 1.0
 */
@Internal
class JavaEnumElement extends JavaClassElement implements EnumElement {
    /**
     * @param classElement       The {@link TypeElement}
     * @param annotationMetadata The annotation metadata
     * @param visitorContext The visitor context
     */
    JavaEnumElement(TypeElement classElement, AnnotationMetadata annotationMetadata, JavaVisitorContext visitorContext) {
        super(classElement, annotationMetadata, visitorContext, Collections.emptyMap());
    }

    @Override
    public List<String> values() {
        TypeElement te = (TypeElement) getNativeType();
        List<String> results = te.getEnclosedElements()
                                    .stream()
                                    .filter(e -> e.getKind() == ElementKind.ENUM_CONSTANT)
                                    .map(e -> e.getSimpleName().toString())
                                    .collect(Collectors.toList());
        return Collections.unmodifiableList(results);
    }
}
