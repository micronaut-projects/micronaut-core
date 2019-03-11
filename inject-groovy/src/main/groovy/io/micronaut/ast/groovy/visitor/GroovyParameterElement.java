/*
 * Copyright 2017-2019 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.ast.groovy.visitor;

import io.micronaut.ast.groovy.utils.AstAnnotationUtils;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.ParameterElement;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.Parameter;
import org.codehaus.groovy.control.SourceUnit;

import javax.annotation.Nullable;

/**
 * Implementation of {@link ParameterElement} for Groovy.
 *
 * @author graemerocher
 * @since 1.0
 */
@Internal
public class GroovyParameterElement extends AbstractGroovyElement implements ParameterElement {

    private final SourceUnit sourceUnit;
    private final Parameter parameter;

    /**
     * Default constructor.
     *
     * @param sourceUnit The source unit
     * @param parameter The parameter
     * @param annotationMetadata The annotation metadata
     */
    GroovyParameterElement(SourceUnit sourceUnit, Parameter parameter, AnnotationMetadata annotationMetadata) {
        super(parameter, annotationMetadata);
        this.parameter = parameter;
        this.sourceUnit = sourceUnit;
    }

    @Override
    public String getName() {
        return parameter.getName();
    }

    @Override
    public boolean isProtected() {
        return false;
    }

    @Override
    public boolean isPublic() {
        return true;
    }

    @Override
    public Object getNativeType() {
        return parameter;
    }

    @Nullable
    @Override
    public ClassElement getType() {
        ClassNode t = parameter.getType();
        if (t.isEnum()) {
            return new GroovyEnumElement(sourceUnit, t, AstAnnotationUtils.getAnnotationMetadata(sourceUnit, t));
        } else {
            return new GroovyClassElement(sourceUnit, t, AstAnnotationUtils.getAnnotationMetadata(sourceUnit, t));
        }
    }
}
