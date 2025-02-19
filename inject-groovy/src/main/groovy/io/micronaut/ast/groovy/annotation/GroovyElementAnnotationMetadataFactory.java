/*
 * Copyright 2017-2022 original authors
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
package io.micronaut.ast.groovy.annotation;

import io.micronaut.ast.groovy.visitor.AbstractGroovyElement;
import io.micronaut.ast.groovy.visitor.GroovyNativeElement;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.inject.annotation.AbstractAnnotationMetadataBuilder;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.Element;
import io.micronaut.inject.ast.GenericPlaceholderElement;
import io.micronaut.inject.ast.WildcardElement;
import io.micronaut.inject.ast.annotation.AbstractElementAnnotationMetadataFactory;
import io.micronaut.inject.ast.annotation.ElementAnnotationMetadataFactory;
import org.codehaus.groovy.ast.AnnotatedNode;
import org.codehaus.groovy.ast.AnnotationNode;
import org.codehaus.groovy.ast.ClassNode;

import java.util.List;

/**
 * Groovy element annotation metadata factory.
 *
 * @author Denis Stepanov
 * @since 4.0.0
 */
public final class GroovyElementAnnotationMetadataFactory extends AbstractElementAnnotationMetadataFactory<AnnotatedNode, AnnotationNode> {

    public GroovyElementAnnotationMetadataFactory(boolean isReadOnly, GroovyAnnotationMetadataBuilder metadataBuilder) {
        super(isReadOnly, metadataBuilder);
    }

    @Override
    public @NonNull ElementAnnotationMetadataFactory readOnly() {
        return new GroovyElementAnnotationMetadataFactory(true, (GroovyAnnotationMetadataBuilder) metadataBuilder);
    }

    @Override
    protected AnnotatedNode getNativeElement(Element element) {
        return ((AbstractGroovyElement) element).getNativeType().annotatedNode();
    }

    @Override
    protected AbstractAnnotationMetadataBuilder.CachedAnnotationMetadata lookupTypeAnnotationsForClass(ClassElement classElement) {
        var clazz = (GroovyNativeElement) classElement.getNativeType();
        return metadataBuilder.lookupOrBuild(clazz, getTypeAnnotationsOnly((ClassNode) clazz.annotatedNode()));
    }

    @Override
    protected AbstractAnnotationMetadataBuilder.CachedAnnotationMetadata lookupTypeAnnotationsForGenericPlaceholder(GenericPlaceholderElement placeholderElement) {
        var placeholder = (GroovyNativeElement.Placeholder) placeholderElement.getGenericNativeType();
        return metadataBuilder.lookupOrBuild(placeholder, getTypeAnnotationsOnly(placeholder.annotatedNode()));
    }

    @Override
    protected AbstractAnnotationMetadataBuilder.CachedAnnotationMetadata lookupTypeAnnotationsForWildcard(WildcardElement wildcardElement) {
        var wildcard = (GroovyNativeElement) wildcardElement.getGenericNativeType();
        return metadataBuilder.lookupOrBuild(wildcard, getTypeAnnotationsOnly((ClassNode) wildcard.annotatedNode()));
    }

    private AnnotatedNode getTypeAnnotationsOnly(ClassNode classNode) {
        var annotatedNode = new AnnotatedNode();
        List<AnnotationNode> typeAnnotations = classNode.getTypeAnnotations();
        if (CollectionUtils.isNotEmpty(typeAnnotations)) {
            annotatedNode.addAnnotations(typeAnnotations);
        }
        return annotatedNode;
    }
}
