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

import io.micronaut.ast.groovy.visitor.GroovyNativeElement;
import io.micronaut.inject.annotation.AbstractAnnotationMetadataBuilder;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.FieldElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.PackageElement;
import io.micronaut.inject.ast.ParameterElement;
import io.micronaut.inject.ast.annotation.AbstractElementAnnotationMetadataFactory;
import io.micronaut.inject.ast.annotation.ElementAnnotationMetadataFactory;
import org.codehaus.groovy.ast.AnnotatedNode;
import org.codehaus.groovy.ast.AnnotationNode;

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
    public ElementAnnotationMetadataFactory readOnly() {
        return new GroovyElementAnnotationMetadataFactory(true, (GroovyAnnotationMetadataBuilder) metadataBuilder);
    }

    @Override
    protected AbstractAnnotationMetadataBuilder.CachedAnnotationMetadata lookupForPackage(PackageElement packageElement) {
        GroovyNativeElement groovyNativeElement = (GroovyNativeElement) packageElement.getNativeType();
        return metadataBuilder.lookupOrBuild(groovyNativeElement, groovyNativeElement.annotatedNode(), true);
    }

    @Override
    protected AbstractAnnotationMetadataBuilder.CachedAnnotationMetadata lookupForParameter(ParameterElement parameterElement) {
        GroovyNativeElement groovyNativeElement = (GroovyNativeElement) parameterElement.getNativeType();
        return metadataBuilder.lookupOrBuild(groovyNativeElement, groovyNativeElement.annotatedNode(), false);
    }

    @Override
    protected AbstractAnnotationMetadataBuilder.CachedAnnotationMetadata lookupForField(FieldElement fieldElement) {
        GroovyNativeElement groovyNativeElement = (GroovyNativeElement) fieldElement.getNativeType();
        return metadataBuilder.lookupOrBuild(groovyNativeElement, groovyNativeElement.annotatedNode(), false);
    }

    @Override
    protected AbstractAnnotationMetadataBuilder.CachedAnnotationMetadata lookupForMethod(MethodElement methodElement) {
        GroovyNativeElement groovyNativeElement = (GroovyNativeElement) methodElement.getNativeType();
        return metadataBuilder.lookupOrBuild(groovyNativeElement, groovyNativeElement.annotatedNode(), false);
    }

    @Override
    protected AbstractAnnotationMetadataBuilder.CachedAnnotationMetadata lookupForClass(ClassElement classElement) {
        GroovyNativeElement groovyNativeElement = (GroovyNativeElement) classElement.getNativeType();
        return metadataBuilder.lookupOrBuild(groovyNativeElement, groovyNativeElement.annotatedNode(), true);
    }

}
