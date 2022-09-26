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

import io.micronaut.inject.ast.ElementAnnotationMetadataFactory;
import org.codehaus.groovy.ast.FieldNode;
import org.codehaus.groovy.ast.PropertyNode;

/**
 * A field element.
 */
public class GroovyVarPropertyElement extends AbstractGroovyVariableElement {

    private final PropertyNode propertyNode;

    /**
     * @param visitorContext            The visitor context
     * @param propertyNode                  The {@link FieldNode}
     * @param annotationMetadataFactory The annotation metadata
     */
    GroovyVarPropertyElement(GroovyVisitorContext visitorContext,
                             PropertyNode propertyNode,
                             ElementAnnotationMetadataFactory annotationMetadataFactory) {
        super(visitorContext, propertyNode.getField(), propertyNode.getField(), annotationMetadataFactory);
        this.propertyNode = propertyNode;
    }

}
