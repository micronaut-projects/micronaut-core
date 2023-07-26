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
package io.micronaut.ast.groovy.visitor;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.ElementModifier;
import io.micronaut.inject.ast.GenericPlaceholderElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.ParameterElement;
import io.micronaut.inject.ast.annotation.ElementAnnotationMetadata;
import io.micronaut.inject.ast.annotation.ElementAnnotationMetadataFactory;
import io.micronaut.inject.ast.annotation.MethodElementAnnotationsHelper;
import io.micronaut.inject.ast.annotation.MutableAnnotationMetadataDelegate;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.GenericsType;
import org.codehaus.groovy.ast.MethodNode;
import org.codehaus.groovy.ast.Parameter;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A method element returning data from a {@link MethodNode}.
 *
 * @author James Kleeh
 * @since 1.0
 */
@Internal
public class GroovyMethodElement extends AbstractGroovyElement implements MethodElement {

    protected ParameterElement[] parameters;
    private final MethodNode methodNode;
    private final GroovyClassElement owningType;
    private ClassElement declaringType;
    private Map<String, ClassElement> declaredTypeArguments;
    private Map<String, ClassElement> typeArguments;
    @Nullable
    private ClassElement returnType;
    @Nullable
    private ClassElement genericReturnType;
    private final MethodElementAnnotationsHelper helper;

    /**
     * @param owningType         The owning type
     * @param visitorContext     The visitor context
     * @param nativeElement      The native element
     * @param methodNode         The {@link MethodNode}
     * @param annotationMetadata The annotation metadata
     */
    GroovyMethodElement(GroovyClassElement owningType,
                        GroovyVisitorContext visitorContext,
                        GroovyNativeElement nativeElement,
                        MethodNode methodNode,
                        ElementAnnotationMetadataFactory annotationMetadata) {
        super(visitorContext, nativeElement, annotationMetadata);
        this.methodNode = methodNode;
        this.owningType = owningType;
        this.helper = new MethodElementAnnotationsHelper(this, annotationMetadata);
    }

    @Override
    protected MutableAnnotationMetadataDelegate<?> getAnnotationMetadataToWrite() {
        return helper.getMethodAnnotationMetadata(presetAnnotationMetadata);
    }

    @Override
    public ElementAnnotationMetadata getMethodAnnotationMetadata() {
        return helper.getMethodAnnotationMetadata(presetAnnotationMetadata);
    }

    @Override
    public AnnotationMetadata getAnnotationMetadata() {
        return helper.getAnnotationMetadata(presetAnnotationMetadata);
    }

    @Override
    protected AbstractGroovyElement copyConstructor() {
        return new GroovyMethodElement(owningType, visitorContext, getNativeType(), methodNode, elementAnnotationMetadataFactory);
    }

    @Override
    protected void copyValues(AbstractGroovyElement element) {
        ((GroovyMethodElement) element).parameters = parameters;
    }

    @Override
    public MethodElement withAnnotationMetadata(AnnotationMetadata annotationMetadata) {
        return (MethodElement) super.withAnnotationMetadata(annotationMetadata);
    }

    @Override
    public MethodElement withParameters(ParameterElement... newParameters) {
        GroovyMethodElement methodElement = (GroovyMethodElement) copy();
        methodElement.parameters = newParameters;
        return methodElement;
    }

    @Override
    public MethodElement withNewOwningType(ClassElement owningType) {
        GroovyMethodElement groovyMethodElement = new GroovyMethodElement((GroovyClassElement) owningType, visitorContext, getNativeType(), methodNode, elementAnnotationMetadataFactory);
        copyValues(groovyMethodElement);
        return groovyMethodElement;
    }

    @Override
    public ClassElement[] getThrownTypes() {
        final ClassNode[] exceptions = methodNode.getExceptions();
        if (ArrayUtils.isNotEmpty(exceptions)) {
            return Arrays.stream(exceptions)
                .map(cn -> newClassElement(cn, getDeclaringType().getTypeArguments()))
                .toArray(ClassElement[]::new);
        }
        return ClassElement.ZERO_CLASS_ELEMENTS;
    }

    @Override
    public Set<ElementModifier> getModifiers() {
        return resolveModifiers(this.methodNode);
    }

    @Override
    public String toString() {
        ClassNode declaringClass = methodNode.getDeclaringClass();
        if (declaringClass == null) {
            declaringClass = owningType.classNode;
        }
        return declaringClass.getName() + "." + methodNode.getName() + "(..)";
    }

    @Override
    public String getName() {
        return methodNode.getName();
    }

    @Override
    public boolean isAbstract() {
        return methodNode.isAbstract();
    }

    @Override
    public boolean isStatic() {
        return methodNode.isStatic();
    }

    @Override
    public boolean isPublic() {
        return methodNode.isPublic() || (methodNode.isSyntheticPublic() && !isPackagePrivate());
    }

    @Override
    public boolean isPrivate() {
        return methodNode.isPrivate();
    }

    @Override
    public boolean isPackagePrivate() {
        return methodNode.isPackageScope();
    }

    @Override
    public boolean isFinal() {
        return methodNode.isFinal();
    }

    @Override
    public boolean isProtected() {
        return methodNode.isProtected();
    }

    @Override
    public boolean isDefault() {
        return methodNode.isDefault() || (!isAbstract() && getDeclaringType().isInterface());
    }

    @Override
    public Map<String, ClassElement> getDeclaredTypeArguments() {
        if (declaredTypeArguments == null) {
            declaredTypeArguments = resolveMethodTypeArguments(getNativeType(), methodNode, getDeclaringType().getTypeArguments());
        }
        return declaredTypeArguments;
    }

    @Override
    public Map<String, ClassElement> getTypeArguments() {
        if (typeArguments == null) {
            typeArguments = MethodElement.super.getTypeArguments();
        }
        return typeArguments;
    }

    @NonNull
    @Override
    public ClassElement getGenericReturnType() {
        if (genericReturnType == null) {
            genericReturnType = newClassElement(methodNode.getReturnType(), getTypeArguments());
        }
        return genericReturnType;
    }

    @Override
    @NonNull
    public ClassElement getReturnType() {
        if (returnType == null) {
            returnType = newClassElement(methodNode.getReturnType());
        }
        return returnType;
    }

    @Override
    public ParameterElement[] getParameters() {
        Parameter[] parameters = methodNode.getParameters();
        if (this.parameters == null) {
            this.parameters = Arrays.stream(parameters).map(this::newParameter).toArray(ParameterElement[]::new);
        }
        return this.parameters;
    }

    private GroovyParameterElement newParameter(Parameter parameter) {
        return new GroovyParameterElement(
            this,
            visitorContext,
            new GroovyNativeElement.Parameter(parameter, methodNode),
            parameter,
            elementAnnotationMetadataFactory
        );
    }

    @Override
    public ClassElement getDeclaringType() {
        if (declaringType == null) {
            ClassNode declaringClassNode = methodNode.getDeclaringClass();
            if (declaringClassNode == null) {
                return owningType;
            }
            Map<String, ClassElement> typeArguments = getOwningType().getTypeArguments(declaringClassNode.getName());
            declaringType = newClassElement(declaringClassNode, typeArguments);
        }
        return declaringType;
    }

    @Override
    public GroovyClassElement getOwningType() {
        return owningType;
    }

    @Override
    public List<? extends GenericPlaceholderElement> getDeclaredTypeVariables() {
        GenericsType[] genericsTypes = methodNode.getGenericsTypes();
        if (genericsTypes == null) {
            return Collections.emptyList();
        }
        return Arrays.stream(genericsTypes)
            .map(gt -> (GenericPlaceholderElement) newClassElement(gt))
            .toList();
    }

}
