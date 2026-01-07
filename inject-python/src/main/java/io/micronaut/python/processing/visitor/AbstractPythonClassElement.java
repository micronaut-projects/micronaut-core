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

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import io.micronaut.inject.annotation.AnnotationMetadataHierarchy;
import io.micronaut.inject.ast.annotation.ElementAnnotationMetadata;
import io.micronaut.inject.ast.annotation.MutableAnnotationMetadataDelegate;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import io.micronaut.annotation.processing.visitor.ElementProvider;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.inject.ast.ArrayableClassElement;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.ConstructorElement;
import io.micronaut.inject.ast.Element;
import io.micronaut.inject.ast.ElementQuery;
import io.micronaut.inject.ast.FieldElement;
import io.micronaut.inject.ast.MemberElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.PropertyElement;
import io.micronaut.inject.ast.PropertyElementQuery;
import io.micronaut.inject.ast.utils.EnclosedElementsQuery;
import io.micronaut.python.processing.PythonProcessingEnvironment;
import io.micronaut.python.processing.util.GraalPyUtil;
import org.jspecify.annotations.NonNull;

public abstract sealed class AbstractPythonClassElement extends AbstractPythonElement
    implements ArrayableClassElement, ElementProvider permits PythonClassElement, PythonEnumElement, PythonGenericPlaceholderElement {
    public static final String PYTHON_DEFAULT_PACKAGE = "python";

    protected final int arrayDimensions;
    protected final PythonProcessingEnvironment environment;
    /** Query implementation for enclosed elements. */
    private final PythonEnclosedElementsQuery enclosedElementsQuery = new PythonEnclosedElementsQuery();

    protected ElementDef typeAnnotationsKey;
    private ElementAnnotationMetadata typeAnnotationMetadata;

    protected AbstractPythonClassElement(ClassDef classDef, PythonProcessingEnvironment environment) {
        this(classDef, environment, 0);
    }

    protected AbstractPythonClassElement(ClassDef classDef, PythonProcessingEnvironment environment, int arrayDimensions) {
        super(
            qualifiedClassName(classDef),
            classDef,
            environment.metadataFactory()
        );
        this.environment = environment;
        this.arrayDimensions = arrayDimensions;
    }

    @Override
    protected MutableAnnotationMetadataDelegate<?> getAnnotationMetadataToWrite() {
        if (typeAnnotationsKey == null) {
            return super.getAnnotationMetadataToWrite();
        }
        return getTypeAnnotationMetadata();
    }

    @NonNull
    @Override
    public AnnotationMetadata getAnnotationMetadata() {
        if (presetAnnotationMetadata != null) {
            return presetAnnotationMetadata;
        }
        if (typeAnnotationsKey == null) {
            return super.getAnnotationMetadata();
        } else {
            return new AnnotationMetadataHierarchy(true, super.getAnnotationMetadata(), getTypeAnnotationMetadata());
        }
    }

    @Override
    public @NonNull MutableAnnotationMetadataDelegate<AnnotationMetadata> getTypeAnnotationMetadata() {
        if (typeAnnotationMetadata == null) {
            typeAnnotationMetadata = elementAnnotationMetadataFactory.buildTypeAnnotations(this);
        }
        return typeAnnotationMetadata;
    }

    public final ElementDef getTypeAnnotationsKey() {
        return typeAnnotationsKey;
    }

    @Override
    public boolean isAbstract() {
        return getNativeType()
            .bases().stream().anyMatch(b -> b.name().equals("abc.ABC"));
    }

    @Override
    public Collection<ClassElement> getInterfaces() {
        ClassDef nativeType = getNativeType();
        List<TypeRef> bases = nativeType.bases();
        if (environment.javaVisitorContext() != null) {
            return bases.stream()
                .flatMap(base -> environment.javaVisitorContext().getClassElement(base.name()).stream())
                .filter(ClassElement::isInterface)
                .toList();
        } else {
            return List.of();
        }
    }

    private static @NotNull String qualifiedClassName(ClassDef classDef) {
        return classDef.packageName().isEmpty() ? PYTHON_DEFAULT_PACKAGE + "." + classDef.name() : classDef.packageName() + "." + classDef.name();
    }

    @Override
    public @Nullable javax.lang.model.element.Element element() {
        return environment.originatingElement();
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
    public List<FieldElement> getFields() {
        return getNativeType().attributes()
            .stream().map(a -> (FieldElement) new PythonFieldElement(a, environment, this, this, environment.metadataFactory()))
            .toList();
    }

    @Override
    public ClassDef getNativeType() {
        return (ClassDef) super.getNativeType();
    }

    @Override
    public String getPackageName() {
        String packageName = getNativeType().packageName();
        return packageName.isEmpty() ? PYTHON_DEFAULT_PACKAGE : packageName;
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

    @Override
    public List<PropertyElement> getSyntheticBeanProperties() {
        return getBeanProperties();
    }

    @Override
    public List<PropertyElement> getBeanProperties() {
        PropertyElementQuery defaultPropertyElementQuery = PropertyElementQuery.of(this);
        return getBeanProperties(defaultPropertyElementQuery);
    }

    @Override
    public List<PropertyElement> getBeanProperties(PropertyElementQuery propertyElementQuery) {
        // For Python, we create properties from both @property decorators and regular attributes

        // First, add properties from @property decorators (these are already PropertyDef instances)
        List<PropertyElement> decoratorProperties = getEnclosedElements(ElementQuery.of(PropertyElement.class));
        List<PropertyElement> allProperties = new java.util.ArrayList<>(decoratorProperties);

        // Then, create properties from regular attributes that aren't already represented as properties
        List<AttributeDef> fields = getNativeType().attributes();
        for (AttributeDef field : fields) {
            // Check if this field is already represented as a property
            boolean alreadyExists = allProperties.stream()
                .anyMatch(prop -> prop.getName().equals(field.name()));

            if (!alreadyExists) {
                // Create a field-based property from the attribute
                PropertyDef propertyDef = new PropertyDef(field.name());
                propertyDef = propertyDef.withField(field);

                PythonPropertyElement propertyElement = new PythonPropertyElement(
                    propertyDef,
                    environment,
                    this,
                    this,
                    environment.metadataFactory()
                );
                allProperties.add(propertyElement);
            }
        }

        // Apply propertyElementQuery filtering
        return filterProperties(allProperties, propertyElementQuery);
    }

    static List<PropertyElement> filterProperties(List<PropertyElement> properties, PropertyElementQuery query) {
        if (properties.isEmpty()) {
            return properties;
        }

        Set<String> includes = query.getIncludes();
        Set<String> excludes = query.getExcludes();
        Set<String> excludedAnnotations = query.getExcludedAnnotations();
        Set<io.micronaut.context.annotation.BeanProperties.AccessKind> accessKinds = query.getAccessKinds();
        io.micronaut.context.annotation.BeanProperties.Visibility visibility = query.getVisibility();
        boolean allowStaticProperties = query.isAllowStaticProperties();

        List<PropertyElement> filteredProperties = new java.util.ArrayList<>();

        for (PropertyElement property : properties) {
            String propertyName = property.getName();

            // Apply includes/excludes filtering
            if (!includes.isEmpty() && !includes.contains(propertyName)) {
                continue;
            }
            if (!excludes.isEmpty() && excludes.contains(propertyName)) {
                continue;
            }

            // Apply annotation-based exclusion
            if (isExcludedByAnnotations(property, excludedAnnotations)) {
                continue;
            }

            // Apply access kind filtering
            if (!isAccessibleViaAccessKinds(property, accessKinds)) {
                continue;
            }

            // Apply visibility filtering
            if (!isAccessibleViaVisibility(property, visibility)) {
                continue;
            }

            // Apply static properties filtering
            if (!allowStaticProperties && isStaticProperty(property)) {
                continue;
            }

            filteredProperties.add(property);
        }

        return filteredProperties;
    }

    private static boolean isExcludedByAnnotations(PropertyElement property, Set<String> excludedAnnotations) {
        if (excludedAnnotations.isEmpty()) {
            return false;
        }

        // Check if any of the property's elements (field, getter, setter) have excluded annotations
        if (property.getField().isPresent() && hasExcludedAnnotation(property.getField().get(), excludedAnnotations)) {
            return true;
        }
        if (property.getReadMethod().isPresent() && hasExcludedAnnotation(property.getReadMethod().get(), excludedAnnotations)) {
            return true;
        }
        if (property.getWriteMethod().isPresent() && hasExcludedAnnotation(property.getWriteMethod().get(), excludedAnnotations)) {
            return true;
        }

        return false;
    }

    private static boolean hasExcludedAnnotation(io.micronaut.inject.ast.Element element, Set<String> excludedAnnotations) {
        return excludedAnnotations.stream().anyMatch(element::hasAnnotation);
    }

    private static boolean isAccessibleViaAccessKinds(PropertyElement property, Set<io.micronaut.context.annotation.BeanProperties.AccessKind> accessKinds) {
        // Check if any of the requested access kinds match the property's actual access kinds
        if (accessKinds.contains(io.micronaut.context.annotation.BeanProperties.AccessKind.METHOD)) {
            // Property has METHOD access if it has a getter or setter
            if (property.getReadMethod().isPresent() || property.getWriteMethod().isPresent()) {
                return true;
            }
        }
        if (accessKinds.contains(io.micronaut.context.annotation.BeanProperties.AccessKind.FIELD)) {
            // Property has FIELD access if it has a field (field-based property)
            if (property.getField().isPresent()) {
                return true;
            }
        }
        return false;
    }

    private static boolean isAccessibleViaVisibility(PropertyElement property, io.micronaut.context.annotation.BeanProperties.Visibility visibility) {
        // For Python, we consider all properties accessible for now
        // In the future, we could check for private/protected modifiers if Python supports them
        return visibility == io.micronaut.context.annotation.BeanProperties.Visibility.ANY ||
               visibility == io.micronaut.context.annotation.BeanProperties.Visibility.DEFAULT ||
               visibility == io.micronaut.context.annotation.BeanProperties.Visibility.PUBLIC;
    }

    private static boolean isStaticProperty(PropertyElement property) {
        // Check if the property is backed by a static field
        return property.getField()
            .map(field -> {
                if (field instanceof PythonFieldElement pythonField) {
                    AttributeDef attrDef = pythonField.getNativeType();
                    return attrDef.isStatic();
                }
                return false;
            })
            .orElse(false);
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
        protected ElementDef getNativeType(Element element) {
            return (ElementDef) element.getNativeType();
        }

        @Override
        protected String getElementName(ElementDef element) {
            return element.name();
        }

        @Override
        protected ClassDef getSuperClass(ClassDef classNode) {
            List<TypeRef> bases = classNode.bases();
            if (!bases.isEmpty()) {
                // Find the first base class that exists in our environment
                for (TypeRef base : bases) {
                    ClassElement baseElement = environment.classes().get(base.name());
                    if (baseElement != null) {
                        return ((PythonClassElement) baseElement).getNativeType();
                    }
                }
            }
            return null;
        }

        @Override
        protected List<ClassDef> getInterfaces(ClassDef classNode) {
            List<TypeRef> bases = classNode.bases();
            if (bases.size() <= 1) {
                return List.of();
            }
            // Return remaining base classes as "interfaces"
            return bases.subList(1, bases.size()).stream()
                .map(base -> {
                    ClassElement baseElement = environment.classes().get(base.name());
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
            if (elementType == ConstructorElement.class) {
                FunctionDef constructor = classNode.constructor();
                if (constructor != null) {
                    elements.add(constructor);
                }
            }

            // Add functions (methods) if the query is for methods/constructors or members
            if (elementType == MethodElement.class ||
                elementType == MemberElement.class) {
                for (FunctionDef function : classNode.functions()) {
                    if (includeAbstract || !function.isAbstract()) {
                        elements.add(function);
                    }
                }
            }

            // For Python, attributes are not real fields since Python uses dynamic attributes
            // So we don't return them as FieldElement instances to avoid injection issues
            // Properties are handled separately via PropertyElement
            // if (elementType == FieldElement.class ||
            //     elementType == MemberElement.class) {
            //     elements.addAll(classNode.attributes());
            // }

            // Add properties if the query is for properties or members
            if (elementType == PropertyElement.class ||
                elementType == MemberElement.class) {
                elements.addAll(classNode.properties());
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
            if (nativeType instanceof MemberDef memberDef && memberDef.declaringClass() != null) {
                ClassDef classDef = memberDef.declaringClass();
                String qualifiedName = classDef.qualifiedName();
                if (!qualifiedName.equals(declaringClassElement.getName())) {
                    ClassElement ce = environment.classes().get(qualifiedName);
                    if (ce instanceof AbstractPythonClassElement ape) {
                        declaringClassElement = ape;
                    }
                }
            } else {
                if (currentDeclaringClass != null && currentDeclaringClass != getNativeClassType(AbstractPythonClassElement.this)) {
                    // This is an inherited element - find the declaring class
                    String declaringClassName = currentDeclaringClass.name();
                    ClassElement declaringElement = environment.classes().get(declaringClassName);
                    if (declaringElement instanceof PythonClassElement pythonDeclaringClass) {
                        declaringClassElement = pythonDeclaringClass;
                    }
                }
            }

            if (nativeType instanceof FunctionDef functionDef) {
                if (functionDef.name().equals(FunctionDef.CONSTRUCTOR_NAME)) {
                    return new PythonConstructorElement(functionDef, environment, declaringClassElement, AbstractPythonClassElement.this, environment.metadataFactory());
                } else {
                    return new PythonMethodElement(functionDef, environment, declaringClassElement, AbstractPythonClassElement.this, environment.metadataFactory());
                }
            } else if (nativeType instanceof AttributeDef attributeDef) {
                return new PythonFieldElement(attributeDef, environment, declaringClassElement, AbstractPythonClassElement.this, environment.metadataFactory());
            } else if (nativeType instanceof PropertyDef propertyDef) {
                return new PythonPropertyElement(propertyDef, environment, declaringClassElement, AbstractPythonClassElement.this, environment.metadataFactory());
            }
            throw new IllegalStateException("Unknown native type: " + nativeType.getClass());
        }
    }

    @Override
    protected abstract AbstractPythonElement copyThis();

    @Override
    public ClassElement withAnnotationMetadata(AnnotationMetadata annotationMetadata) {
        return (ClassElement) super.withAnnotationMetadata(annotationMetadata);
    }
}
