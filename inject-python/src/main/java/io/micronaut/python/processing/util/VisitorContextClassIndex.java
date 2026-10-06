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
package io.micronaut.python.processing.util;

import io.micronaut.core.annotation.Internal;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.ElementQuery;
import io.micronaut.inject.ast.EnumElement;
import io.micronaut.inject.ast.FieldElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.python.imports.ClassIndex;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The {@link ClassIndex} of the compile class path, over the visitor context of the Python processor.
 *
 * @author Graeme Rocher
 * @since 5.3.0
 */
@Internal
public final class VisitorContextClassIndex implements ClassIndex {

    private static final String INTERNAL = "io.micronaut.core.annotation.Internal";

    private final VisitorContext visitorContext;

    /**
     * @param visitorContext The visitor context of the compilation
     */
    public VisitorContextClassIndex(VisitorContext visitorContext) {
        this.visitorContext = visitorContext;
    }

    @Override
    public List<TypeInfo> types(String javaPackage) {
        ClassElement[] elements = visitorContext.getClassElements(javaPackage, "*");
        List<TypeInfo> types = new ArrayList<>(elements.length);
        for (ClassElement element : elements) {
            if (!PythonJavaTypes.isPythonClass(element) && javaPackage.equals(element.getPackageName())) {
                types.add(typeInfo(element));
            }
        }
        return types;
    }

    @Override
    public Optional<TypeInfo> type(String binaryName) {
        return visitorContext.getClassElement(binaryName)
            .or(() -> visitorContext.getClassElement(binaryName.replace('$', '.')))
            .map(VisitorContextClassIndex::typeInfo);
    }

    @Override
    public List<String> staticMethods(String binaryName) {
        return classElement(binaryName)
            .map(element -> element.getEnclosedElements(ElementQuery.ALL_METHODS.onlyDeclared().onlyStatic())
                .stream()
                .filter(MethodElement::isPublic)
                .map(MethodElement::getName)
                .toList())
            .orElse(List.of());
    }

    @Override
    public List<String> constants(String binaryName) {
        return classElement(binaryName)
            .map(element -> {
                if (element instanceof EnumElement enumElement) {
                    return enumElement.values();
                }
                return element.getEnclosedElements(ElementQuery.ALL_FIELDS.onlyDeclared().onlyStatic())
                    .stream()
                    .filter(field -> field.isPublic() && field.isFinal())
                    .map(FieldElement::getName)
                    .toList();
            })
            .orElse(List.of());
    }

    private Optional<ClassElement> classElement(String binaryName) {
        return visitorContext.getClassElement(binaryName).or(() -> visitorContext.getClassElement(binaryName.replace('$', '.')));
    }

    private static TypeInfo typeInfo(ClassElement element) {
        TypeKind kind;
        if (PythonAnnotationTypes.isAnnotationType(element)) {
            kind = TypeKind.ANNOTATION;
        } else if (element.isEnum()) {
            kind = TypeKind.ENUM;
        } else if (element.isInterface()) {
            kind = TypeKind.INTERFACE;
        } else {
            kind = TypeKind.CLASS;
        }
        return new TypeInfo(element.getName(), kind, element.isPublic(), element.hasDeclaredAnnotation(INTERNAL));
    }
}
