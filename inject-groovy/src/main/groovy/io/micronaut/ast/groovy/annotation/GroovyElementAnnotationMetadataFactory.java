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

import io.micronaut.inject.annotation.AbstractElementAnnotationMetadataFactory;
import io.micronaut.inject.ast.ElementAnnotationMetadataFactory;
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

}
