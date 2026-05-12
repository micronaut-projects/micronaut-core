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

import io.micronaut.annotation.processing.visitor.ElementProvider;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Executable;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationUtil;
import io.micronaut.inject.annotation.AnnotationMetadataHierarchy;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.ConstructorElement;
import io.micronaut.inject.ast.ElementQuery;
import io.micronaut.inject.ast.MemberElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.PropertyElement;
import io.micronaut.inject.ast.PropertyElementQuery;
import io.micronaut.inject.ast.annotation.ElementAnnotationMetadata;
import io.micronaut.inject.ast.annotation.MutableAnnotationMetadataDelegate;
import io.micronaut.inject.ast.utils.EnclosedElementsQuery;
import io.micronaut.python.processing.PythonProcessingEnvironment;
import jakarta.inject.Singleton;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;

import javax.lang.model.element.Element;
import java.util.List;
import java.util.Optional;
import java.util.ServiceLoader;

/**
 * Represents a Python script as a Micronaut ClassElement.
 * Scripts are modeled as singleton classes where module-level attributes
 * become injectable fields and module-level functions become methods.
 */
public final class PythonScriptElement extends AbstractPythonElement implements ClassElement, ElementProvider {

    private final ScriptDef scriptDef;
    /**
     * Query implementation for enclosed elements.
     */
    private final ScriptEnclosedElementsQuery enclosedElementsQuery = new ScriptEnclosedElementsQuery();
    private final PythonProcessingEnvironment environment;
    protected ElementDef typeAnnotationsKey;
    private ElementAnnotationMetadata typeAnnotationMetadata;

    public PythonScriptElement(ScriptDef scriptDef, PythonProcessingEnvironment environment) {
        super(qualifiedClassName(scriptDef), scriptDef, environment.metadataFactory());
        this.scriptDef = scriptDef;
        this.environment = environment;
        List<MemberElement> enclosedElements = getEnclosedElements(ElementQuery.of(MemberElement.class));

        for (MemberElement enclosedElement : enclosedElements) {
            if (enclosedElement.hasStereotype(AnnotationUtil.INJECT) ||
                enclosedElement.hasStereotype(AnnotationUtil.QUALIFIER) ||
                enclosedElement.hasStereotype(Executable.class)) {
                // make bean.
                // Mark pooled to opt the script into pooled stub generation
                annotate("io.micronaut.context.python.scope.ContextPooled");
                annotate(Bean.class);
                applyTypeLevelDefaultAnnotations(enclosedElement);
            }
        }
    }

    private void applyTypeLevelDefaultAnnotations(MemberElement enclosedElement) {
        ServiceLoader<PythonScriptElementProcessor> serviceLoader = ServiceLoader.load(
            PythonScriptElementProcessor.class,
            PythonScriptElement.class.getClassLoader()
        );
        for (PythonScriptElementProcessor processor : serviceLoader) {
            processor.process(this, enclosedElement);
        }
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

    public ElementDef getTypeAnnotationsKey() {
        return typeAnnotationsKey;
    }

    private static String qualifiedClassName(ScriptDef scriptDef) {
        return scriptDef.qualifiedName();
    }

    @Override
    public ClassElement withAnnotationMetadata(AnnotationMetadata annotationMetadata) {
        return (ClassElement) super.withAnnotationMetadata(annotationMetadata);
    }

    @Override
    public ScriptDef getNativeType() {
        return (ScriptDef) super.getNativeType();
    }

    @Override
    protected PythonScriptElement copyThis() {
        return new PythonScriptElement(scriptDef, environment);
    }

    @Override
    public String toString() {
        return "Python Script: " + getNativeType().name();
    }

    @Override
    public boolean isAssignable(String type) {
        return Object.class.getName().equals(type) || getName().equals(type);
    }

    @Override
    public Optional<MethodElement> getPrimaryConstructor() {
        // Scripts don't have constructors - they're singletons
        return Optional.empty();
    }

    @Override
    public Optional<ClassElement> getSuperType() {
        // Scripts don't inherit from other types
        return Optional.empty();
    }

    @Override
    public List<ClassElement> getInterfaces() {
        // Scripts don't implement interfaces
        return List.of();
    }

    @Override
    public @NonNull ClassElement toArray() {
        return this;
    }

    @Override
    public @NonNull ClassElement fromArray() {
        return this;
    }

    @Override
    public boolean isAbstract() {
        return false;
    }

    @Override
    public @Nullable Element element() {
        return environment.originatingElement();
    }

    @Override
    public <T extends io.micronaut.inject.ast.Element> List<T> getEnclosedElements(ElementQuery<T> query) {
        return enclosedElementsQuery.getEnclosedElements(this, query);
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
        List<PropertyElement> allProperties = new java.util.ArrayList<>();

        // Then, create properties from regular attributes that aren't already represented as properties
        List<AttributeDef> fields = getNativeType().attributes();
        for (AttributeDef field : fields) {
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

        // Apply propertyElementQuery filtering
        return AbstractPythonClassElement.filterProperties(allProperties, propertyElementQuery);
    }

    private final class ScriptEnclosedElementsQuery extends EnclosedElementsQuery<ScriptDef, ElementDef> {
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
        protected ElementDef getNativeType(io.micronaut.inject.ast.Element element) {
            return (ElementDef) element.getNativeType();
        }

        @Override
        protected String getElementName(ElementDef element) {
            return element.name();
        }

        @Override
        protected ScriptDef getSuperClass(ScriptDef classNode) {
            // scripts have no super classes
            return null;
        }

        @Override
        protected List<ScriptDef> getInterfaces(ScriptDef classNode) {
            // scripts have no interfaces
            return List.of();
        }

        @Override
        protected List<ElementDef> getEnclosedElements(ScriptDef classNode, ElementQuery.Result<?> result, boolean includeAbstract) {
            List<ElementDef> elements = new java.util.ArrayList<>();
            Class<?> elementType = result.getElementType();

            if (elementType == ConstructorElement.class) {
                // scripts have no constructors
                return List.of();
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
            if (elementType == PropertyElement.class ||
                elementType == MemberElement.class) {
                elements.addAll(classNode.attributes());
            }

            return elements;
        }

        @Override
        protected boolean excludeClass(ScriptDef classNode) {
            return false;
        }

        @Override
        protected boolean isAbstractClass(ScriptDef classNode) {
            return false;
        }

        @Override
        protected boolean isInterface(ScriptDef classNode) {
            return false;
        }

        @Override
        protected io.micronaut.inject.ast.Element toAstElement(ElementDef nativeType, Class<?> elementType) {
            // Determine the declaring class element
            PythonScriptElement declaringClassElement = PythonScriptElement.this; // Default to the queried class
            if (nativeType instanceof FunctionDef functionDef) {
                return new PythonMethodElement(functionDef, environment, declaringClassElement, PythonScriptElement.this, environment.metadataFactory());
            } else if (nativeType instanceof AttributeDef attributeDef) {
                return new PythonPropertyElement(new PropertyDef(
                    attributeDef.name()
                ).withField(attributeDef), environment, declaringClassElement, PythonScriptElement.this, environment.metadataFactory());
            }
            throw new IllegalStateException("Unknown native type: " + nativeType.getClass());
        }
    }
}
