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
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.ElementAnnotationMetadataFactory;
import io.micronaut.inject.ast.ElementModifier;
import io.micronaut.inject.ast.EnumConstantElement;
import io.micronaut.inject.ast.MemberElement;
import org.codehaus.groovy.ast.AnnotatedNode;
import org.codehaus.groovy.ast.FieldNode;

import java.util.Set;

/**
 * A enum constant element returning data from a {@link org.codehaus.groovy.ast.Variable}.
 *
 * @since 3.6.0
 */
@Internal
public final class GroovyEnumConstantElement extends AbstractGroovyElement implements EnumConstantElement {

    private final GroovyClassElement declaringEnum;
    private final FieldNode variable;

    /**
     * @param declaringEnum             The declaring enum
     * @param visitorContext            The visitor context
     * @param variable                  The {@link org.codehaus.groovy.ast.Variable}
     * @param annotatedNode             The annotated node
     * @param annotationMetadataFactory The annotation medatada
     */
    GroovyEnumConstantElement(GroovyClassElement declaringEnum,
                              GroovyVisitorContext visitorContext,
                              FieldNode variable, AnnotatedNode annotatedNode,
                              ElementAnnotationMetadataFactory annotationMetadataFactory) {
        super(visitorContext, annotatedNode, annotationMetadataFactory);
        this.declaringEnum = declaringEnum;
        this.variable = variable;
    }

    @Override
    protected AbstractGroovyElement copyThis() {
        return new GroovyEnumConstantElement(declaringEnum, visitorContext, variable, getNativeType(), elementAnnotationMetadataFactory);
    }

    @Override
    public MemberElement withAnnotationMetadata(AnnotationMetadata annotationMetadata) {
        return (MemberElement) super.withAnnotationMetadata(annotationMetadata);
    }

    @Override
    public ClassElement getDeclaringType() {
        return declaringEnum;
    }

    @NonNull
    @Override
    public ClassElement getType() {
        return declaringEnum;
    }

    @Override
    public Set<ElementModifier> getModifiers() {
        return ENUM_CONSTANT_MODIFIERS;
    }

    @Override
    public boolean isPackagePrivate() {
        return false;
    }

    @Override
    public boolean isAbstract() {
        return false;
    }

    @Override
    public boolean isStatic() {
        return true;
    }

    @Override
    public boolean isPublic() {
        return true;
    }

    @Override
    public boolean isPrivate() {
        return false;
    }

    @Override
    public boolean isFinal() {
        return true;
    }

    @Override
    public boolean isProtected() {
        return false;
    }

    @Override
    public boolean isPrimitive() {
        return false;
    }

    @Override
    public boolean isArray() {
        return false;
    }

    @Override
    public String getName() {
        return variable.getName();
    }

    @Override
    public FieldNode getNativeType() {
        return variable;
    }

    @Override
    public String toString() {
        return variable.getName();
    }
}
