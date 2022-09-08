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

import io.micronaut.ast.groovy.utils.AstGenericUtils;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.ElementAnnotationMetadataFactory;
import io.micronaut.inject.ast.ElementModifier;
import io.micronaut.inject.ast.GenericPlaceholderElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.ParameterElement;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.GenericsType;
import org.codehaus.groovy.ast.MethodNode;
import org.codehaus.groovy.ast.Parameter;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A method element returning data from a {@link MethodNode}.
 *
 * @author James Kleeh
 * @since 1.0
 */
public class GroovyMethodElement extends AbstractGroovyElement implements MethodElement {

    protected ParameterElement[] parameters;
    private final MethodNode methodNode;
    private final GroovyClassElement owningType;
    private Map<String, ClassNode> genericsSpec;
    private ClassElement declaringElement;

    /**
     * @param owningType         The owning type
     * @param visitorContext     The visitor context
     * @param methodNode         The {@link MethodNode}
     * @param annotationMetadata The annotation metadata
     */
    GroovyMethodElement(GroovyClassElement owningType,
                        GroovyVisitorContext visitorContext,
                        MethodNode methodNode,
                        ElementAnnotationMetadataFactory annotationMetadata) {
        super(visitorContext, methodNode, annotationMetadata);
        this.methodNode = methodNode;
        this.owningType = owningType;
    }

    @Override
    protected AbstractGroovyElement copyThis() {
        return new GroovyMethodElement(owningType, visitorContext, methodNode, elementAnnotationMetadataFactory);
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
        GroovyMethodElement methodElement = (GroovyMethodElement) makeCopy();
        methodElement.parameters = newParameters;
        return methodElement;
    }

    @Override
    public MethodElement withNewOwningType(ClassElement owningType) {
        GroovyMethodElement groovyMethodElement = new GroovyMethodElement((GroovyClassElement) owningType, visitorContext, methodNode, elementAnnotationMetadataFactory);
        copyValues(groovyMethodElement);
        return groovyMethodElement;
    }

    @Override
    public ClassElement[] getThrownTypes() {
        final ClassNode[] exceptions = methodNode.getExceptions();
        if (ArrayUtils.isNotEmpty(exceptions)) {
            return Arrays.stream(exceptions)
                .map(cn -> getGenericElement(cn, visitorContext.getElementFactory().newClassElement(
                    cn,
                    elementAnnotationMetadataFactory,
                    Collections.emptyMap()
                ))).toArray(ClassElement[]::new);
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
            declaringClass = owningType.getNativeType();
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
        return !isAbstract() && getDeclaringType().isInterface();
    }

    @Override
    public MethodNode getNativeType() {
        return methodNode;
    }

    @NonNull
    @Override
    public ClassElement getGenericReturnType() {
        ClassNode returnType = methodNode.getReturnType();
        ClassElement rawElement = getReturnType();
        return getGenericElement(returnType, rawElement);
    }

    /**
     * Obtains the generic element if present otherwise returns the raw element.
     *
     * @param type       The type
     * @param rawElement The raw element
     * @return The class element
     */
    @NonNull
    ClassElement getGenericElement(@NonNull ClassNode type, @NonNull ClassElement rawElement) {
        Map<String, ClassNode> genericsSpec = getGenericsSpec();

        return getGenericElement(sourceUnit, type, rawElement, genericsSpec);
    }

    /**
     * Resolves the generics spec for this method.
     *
     * @return The generic spec
     */
    @NonNull
    Map<String, ClassNode> getGenericsSpec() {
        if (genericsSpec == null) {
            Map<String, Map<String, ClassNode>> info = owningType.getGenericTypeInfo();
            if (CollectionUtils.isNotEmpty(info)) {
                ClassNode declaringClazz = methodNode.getDeclaringClass();
                if (declaringClazz == null) {
                    declaringClazz = owningType.getNativeType();
                }
                Map<String, ClassNode> typeGenericInfo = info.get(declaringClazz.getName());
                if (CollectionUtils.isNotEmpty(typeGenericInfo)) {
                    genericsSpec = AstGenericUtils.createGenericsSpec(methodNode, new HashMap<>(typeGenericInfo));
                }
            }
            if (genericsSpec == null) {
                genericsSpec = Collections.emptyMap();
            }
        }
        return genericsSpec;
    }

    @Override
    @NonNull
    public ClassElement getReturnType() {
        return visitorContext.getElementFactory().newClassElement(methodNode.getReturnType(), elementAnnotationMetadataFactory);
    }

    @Override
    public ParameterElement[] getParameters() {
        Parameter[] parameters = methodNode.getParameters();
        if (this.parameters == null) {
            this.parameters = Arrays.stream(parameters).map(parameter ->
                new GroovyParameterElement(
                    this,
                    visitorContext,
                    parameter,
                    elementAnnotationMetadataFactory
                )
            ).toArray(ParameterElement[]::new);
        }

        return this.parameters;
    }

    @Override
    public ClassElement getDeclaringType() {
        if (this.declaringElement == null) {
            ClassNode methodDeclaringClass = methodNode.getDeclaringClass();
            if (methodDeclaringClass == null) {
                return owningType;
            }
            this.declaringElement = visitorContext.getElementFactory().newClassElement(methodDeclaringClass, elementAnnotationMetadataFactory);
        }
        return this.declaringElement;
    }

    @Override
    public GroovyClassElement getOwningType() {
        return owningType;
    }

    @Override
    public List<? extends GenericPlaceholderElement> getDeclaredTypeVariables() {
        GenericsType[] genericsTypes = methodNode.getGenericsTypes();
        return genericsTypes == null ?
            Collections.emptyList() :
            Arrays.stream(genericsTypes)
                .map(gt -> (GenericPlaceholderElement) visitorContext.getElementFactory().newClassElement(gt.getType(), elementAnnotationMetadataFactory))
                .collect(Collectors.toList());
    }

}
