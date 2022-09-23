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
import org.apache.groovy.util.concurrent.LazyInitializable;
import org.codehaus.groovy.ast.FieldNode;
import org.codehaus.groovy.ast.Variable;

import java.lang.reflect.Field;

/**
 * A field element returning data from a {@link Variable}. The
 * variable could be a field or property node.
 *
 * @author James Kleeh
 * @since 1.0
 */
public class GroovyFieldElement extends AbstractGroovyVariableElement {

    private final GroovyClassElement owningType;
    private final FieldNode fieldNode;

    /**
     * @param visitorContext            The visitor context
     * @param fieldNode                 The {@link FieldNode}
     * @param annotationMetadataFactory The annotation metadata
     */
    GroovyFieldElement(GroovyVisitorContext visitorContext,
                       GroovyClassElement owningType,
                       FieldNode fieldNode,
                       ElementAnnotationMetadataFactory annotationMetadataFactory) {
        super(visitorContext, fieldNode, fieldNode, annotationMetadataFactory);
        this.owningType = owningType;
        this.fieldNode = unwrapLazyField(fieldNode);
    }

    @Deprecated
    private static FieldNode unwrapLazyField(FieldNode fieldNode) {
        if (fieldNode instanceof LazyInitializable) {
            //this nonsense is to work around https://issues.apache.org/jira/browse/GROOVY-10398
            ((LazyInitializable) fieldNode).lazyInit();
            try {
                Field delegate = fieldNode.getClass().getDeclaredField("delegate");
                delegate.setAccessible(true);
                fieldNode = (FieldNode) delegate.get(fieldNode);
            } catch (NoSuchFieldException | IllegalAccessException e) {
                // no op
            }
        }
        return fieldNode;
    }

    @Override
    public FieldNode getNativeType() {
        return fieldNode;
    }

    @Override
    public GroovyClassElement getOwningType() {
        return owningType;
    }
}
