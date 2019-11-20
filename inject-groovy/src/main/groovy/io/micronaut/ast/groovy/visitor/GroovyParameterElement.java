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
import org.codehaus.groovy.control.CompilationUnit;
import org.codehaus.groovy.control.SourceUnit;

import javax.annotation.Nonnull;
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
    private final GroovyMethodElement methodElement;

    /**
     * Default constructor.
     *
     * @param methodElement      The parent method element
     * @param sourceUnit         The source unit
     * @param compilationUnit    The compilation unit
     * @param parameter          The parameter
     * @param annotationMetadata The annotation metadata
     */
    GroovyParameterElement(GroovyMethodElement methodElement, SourceUnit sourceUnit, CompilationUnit compilationUnit, Parameter parameter, AnnotationMetadata annotationMetadata) {
        super(sourceUnit, compilationUnit, parameter, annotationMetadata);
        this.parameter = parameter;
        this.sourceUnit = sourceUnit;
        this.methodElement = methodElement;
    }

    @Nullable
    @Override
    public ClassElement getGenericType() {
        ClassElement type = getType();
        return methodElement.getGenericElement(parameter.getType(), type);
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

    @Nonnull
    @Override
    public ClassElement getType() {
        ClassNode t = parameter.getType();
        if (t.isEnum()) {
            return new GroovyEnumElement(sourceUnit, compilationUnit, t, AstAnnotationUtils.getAnnotationMetadata(sourceUnit, compilationUnit, t));
        } else {
            return new GroovyClassElement(sourceUnit, compilationUnit, t, AstAnnotationUtils.getAnnotationMetadata(sourceUnit, compilationUnit, t));
        }
    }
}
