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
package io.micronaut.python.processing.visitor;

import io.micronaut.inject.ast.ArrayableClassElement;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.ConstructorElement;
import io.micronaut.inject.ast.Element;
import io.micronaut.inject.ast.ElementQuery;
import io.micronaut.inject.ast.FieldElement;
import io.micronaut.inject.ast.MemberElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.utils.EnclosedElementsQuery;
import io.micronaut.python.processing.PythonProcessingEnvironment;
import io.micronaut.python.processing.util.GraalPyUtil;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public abstract sealed class AbstractPythonClassElement extends AbstractPythonElement implements ArrayableClassElement permits PythonClassElement, PythonEnumElement {
    protected final int arrayDimensions;
    protected final PythonProcessingEnvironment environment;
    /** Query implementation for enclosed elements. */
    private final PythonEnclosedElementsQuery enclosedElementsQuery = new PythonEnclosedElementsQuery();

    protected AbstractPythonClassElement(ClassDef classDef, PythonProcessingEnvironment environment) {
        this(classDef, environment, 0);
    }

    protected AbstractPythonClassElement(ClassDef classDef, PythonProcessingEnvironment environment, int arrayDimensions) {
        super(
            classDef.packageName().isEmpty() ? classDef.name() : classDef.packageName() + "." + classDef.name(),
            classDef,
            environment.metadataFactory()
        );
        this.environment = environment;
        this.arrayDimensions = arrayDimensions;
    }

    @Override
    public ClassElement withArrayDimensions(int arrayDimensions) {
        return createWithArrayDimensions(arrayDimensions);
    }

    protected abstract ClassElement createWithArrayDimensions(int arrayDimensions);

    @Override
    public boolean isArray() {
        return arrayDimensions > 0;
    }

    @Override
    public int getArrayDimensions() {
        return arrayDimensions;
    }

    @Override
    public <T extends Element> List<T> getEnclosedElements(ElementQuery<T> query) {
        return enclosedElementsQuery.getEnclosedElements(this, query);
    }

    @Override
    public ClassDef getNativeType() {
        return (ClassDef) super.getNativeType();
    }

    @Override
    public String getPackageName() {
        return getNativeType().packageName();
    }

    @Override
    public Optional<String> getDocumentation(boolean parseContent) {
        String doc = getNativeType().documentation();
        if (doc == null) {
            return Optional.empty();
        }
        if (parseContent) {
            // Parse Python docstring to extract main description
            return Optional.of(GraalPyUtil.parsePythonDocstring(doc));
        }
        return Optional.of(doc);
    }

    private final class PythonEnclosedElementsQuery extends EnclosedElementsQuery<ClassDef, ElementDef> {
        private ClassDef currentDeclaringClass;

        @Override
        protected boolean hasAnnotation(ElementDef element, Class<? extends java.lang.annotation.Annotation> annotation) {
            for (DecoratorDef decorator : element.decorators()) {
                if (decorator.annotationName().equals(annotation.getName())) {
                    return true;
                }
            }
            return false;
        }

        @Override
        protected ClassDef getNativeClassType(ClassElement classElement) {
            return ((PythonClassElement) classElement).getNativeType();
        }

        @Override
        protected ElementDef getNativeType(Element element) {
            return (ElementDef) element.getNativeType();
        }

        @Override
        protected String getElementName(ElementDef element) {
            return element.name();
        }

        @Override
        protected ClassDef getSuperClass(ClassDef classNode) {
            List<String> bases = classNode.bases();
            if (!bases.isEmpty()) {
                // Find the first base class that exists in our environment
                for (String base : bases) {
                    ClassElement baseElement = environment.classes().get(base);
                    if (baseElement != null) {
                        return ((PythonClassElement) baseElement).getNativeType();
                    }
                }
            }
            return null;
        }

        @Override
        protected List<ClassDef> getInterfaces(ClassDef classNode) {
            List<String> bases = classNode.bases();
            if (bases.size() <= 1) {
                return List.of();
            }
            // Return remaining base classes as "interfaces"
            return bases.subList(1, bases.size()).stream()
                .map(base -> {
                    ClassElement baseElement = environment.classes().get(base);
                    return baseElement != null ? ((PythonClassElement) baseElement).getNativeType() : null;
                })
                .filter(Objects::nonNull)
                .toList();
        }

        @Override
        protected List<ElementDef> getEnclosedElements(ClassDef classNode, ElementQuery.Result<?> result, boolean includeAbstract) {
            this.currentDeclaringClass = classNode;
            List<ElementDef> elements = new java.util.ArrayList<>();
            Class<?> elementType = result.getElementType();

            // Add functions (methods) if the query is for methods/constructors or members
            if (elementType == MethodElement.class ||
                elementType == ConstructorElement.class ||
                elementType == MemberElement.class) {
                for (FunctionDef function : classNode.functions()) {
                    if (includeAbstract || !function.isAbstract()) {
                        elements.add(function);
                    }
                }
            }

            // Add attributes (fields) if the query is for fields or members
            if (elementType == FieldElement.class ||
                elementType == MemberElement.class) {
                elements.addAll(classNode.attributes());
            }

            return elements;
        }

        @Override
        protected boolean excludeClass(ClassDef classNode) {
            String name = classNode.name();
            // Exclude built-in Python classes
            return "object".equals(name) || "type".equals(name);
        }

        @Override
        protected boolean isAbstractClass(ClassDef classNode) {
            return classNode.functions().stream().anyMatch(FunctionDef::isAbstract);
        }

        @Override
        protected boolean isInterface(ClassDef classNode) {
            // Python doesn't have interfaces, so always return false
            return false;
        }

        @Override
        protected Element toAstElement(ElementDef nativeType, Class<?> elementType) {
            // Determine the declaring class element
            AbstractPythonClassElement declaringClassElement = AbstractPythonClassElement.this; // Default to the queried class
            if (currentDeclaringClass != null && currentDeclaringClass != getNativeClassType(AbstractPythonClassElement.this)) {
                // This is an inherited element - find the declaring class
                String declaringClassName = currentDeclaringClass.name();
                ClassElement declaringElement = environment.classes().get(declaringClassName);
                if (declaringElement instanceof PythonClassElement pythonDeclaringClass) {
                    declaringClassElement = pythonDeclaringClass;
                }
            }

            if (nativeType instanceof FunctionDef functionDef) {
                return new PythonMethodElement(functionDef, environment, declaringClassElement, AbstractPythonClassElement.this, environment.metadataFactory());
            } else if (nativeType instanceof AttributeDef attributeDef) {
                return new PythonFieldElement(attributeDef, environment, declaringClassElement, AbstractPythonClassElement.this, environment.metadataFactory());
            }
            throw new IllegalStateException("Unknown native type: " + nativeType.getClass());
        }
    }
}
