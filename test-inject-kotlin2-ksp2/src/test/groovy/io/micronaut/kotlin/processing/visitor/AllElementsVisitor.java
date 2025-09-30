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
package io.micronaut.kotlin.processing.visitor;

import io.micronaut.core.annotation.AnnotationMetadataProvider;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.Element;
import io.micronaut.inject.ast.FieldElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.TypedElement;
import io.micronaut.inject.visitor.TypeElementVisitor;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.inject.writer.GeneratedFile;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class AllElementsVisitor implements TypeElementVisitor<Object, Object> {
    public static boolean WRITE_FILE = false;
    public static boolean WRITE_IN_METAINF = false;
    public static List<String> VISITED_ELEMENTS = new ArrayList<>();
    public static List<ClassElement> VISITED_CLASS_ELEMENTS = new ArrayList<>();
    public static List<MethodElement> VISITED_METHOD_ELEMENTS = new ArrayList<>();

    public Set<ClassElement> visited = Collections.newSetFromMap(new IdentityHashMap<>());

    @Override
    public void start(VisitorContext visitorContext) {
        VISITED_ELEMENTS.clear();
        VISITED_CLASS_ELEMENTS.clear();
        VISITED_METHOD_ELEMENTS.clear();
    }

    @Override
    public void finish(VisitorContext visitorContext) {
        Optional<GeneratedFile> generatedFile;
        if (WRITE_IN_METAINF) {
            generatedFile = visitorContext.visitMetaInfFile("foo/bar.txt", VISITED_CLASS_ELEMENTS.toArray(ClassElement.ZERO_CLASS_ELEMENTS));
        } else {
            generatedFile = visitorContext.visitGeneratedFile("foo/bar.txt", VISITED_CLASS_ELEMENTS.toArray(ClassElement.ZERO_CLASS_ELEMENTS));
        }
        var gf = generatedFile.orElseThrow();
        try (Writer w = gf.openWriter()) {
            w.write("All good");
        } catch (IOException e) {
            visitorContext.fail(e.getMessage(), null);
        }
    }

    @Override
    public void visitClass(ClassElement element, VisitorContext context) {
        visit(element);
        // Preload annotations and elements for tests otherwise it fails because the compiler is done
        initializeClassElement(element, 0);
        VISITED_CLASS_ELEMENTS.add(element);
    }

    @Override
    public void visitMethod(MethodElement methodElement, VisitorContext context) {
        VISITED_METHOD_ELEMENTS.add(methodElement);
        // Preload
        initializeMethodElement(methodElement, 0);
        visit(methodElement);
    }

    @Override
    public void visitField(FieldElement element, VisitorContext context) {
        initializeTypedElement(element, 0);
        visit(element);
    }

    private void initializeElement(Element typedElement) {
        typedElement.getAnnotationMetadata().getAnnotationNames();
    }

    private void initializeTypedElement(TypedElement typedElement, int level) {
        initializeElement(typedElement);
        initializeClassElement(typedElement.getType(), level + 1);
        initializeClassElement(typedElement.getGenericType(), level + 1);
    }

    private void initializeClassElement(ClassElement classElement, int level) {
        String name = classElement.getName();
        if (!name.startsWith("test.") || name.equals(Object.class.getName()) || name.equals("kotlin.Any")) {
            return;
        }
        if (!visited.add(classElement)) {
            return;
        }
        if (level > 5) {
            return;
        }

        initializeTypedElement(classElement, level + 1);
        classElement.getTypeAnnotationMetadata().getAnnotationNames();
        classElement.getPrimaryConstructor().ifPresent(methodElement -> initializeMethodElement(methodElement, level + 1));
        classElement.getSuperType().ifPresent(superType -> initializeClassElement(superType, level + 1));
        classElement.getFields().forEach(field -> initializeTypedElement(field, level + 1));
        classElement.getMethods().forEach(method -> initializeMethodElement(method, level + 1));
        classElement.getDeclaredGenericPlaceholders();
        classElement.getSyntheticBeanProperties();
        classElement.getBeanProperties().forEach(AnnotationMetadataProvider::getAnnotationMetadata);
        classElement.getBeanProperties().forEach(propertyElement -> {
            initializeTypedElement(propertyElement, level + 1);
            propertyElement.getField().ifPresent(f -> initializeTypedElement(f, level + 1));
            propertyElement.getWriteMethod().ifPresent(methodElement -> initializeMethodElement(methodElement, level + 1));
            propertyElement.getReadMethod().ifPresent(methodElement -> initializeMethodElement(methodElement, level + 1));
        });
        classElement.getAllTypeArguments().values().forEach(ta -> ta.values().forEach(ce -> initializeClassElement(ce, level + 1)));
    }

    private void initializeMethodElement(MethodElement methodElement, int level) {
        initializeElement(methodElement);
        initializeClassElement(methodElement.getReturnType(), level + 1);
        initializeClassElement(methodElement.getGenericReturnType(), level + 1);
        Arrays.stream(methodElement.getParameters()).forEach(p -> initializeTypedElement(p, level + 1));
        methodElement.getDeclaredTypeArguments().values().forEach(c -> initializeClassElement(c, level + 1));
        methodElement.getTypeArguments().values().forEach(c -> initializeClassElement(c, level + 1));
    }

    private void visit(Element element) {
        VISITED_ELEMENTS.add(element.getName());
    }
}
