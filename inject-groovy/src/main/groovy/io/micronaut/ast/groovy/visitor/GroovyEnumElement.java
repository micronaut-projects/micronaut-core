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

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.inject.ast.EnumElement;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.FieldNode;
import org.codehaus.groovy.control.CompilationUnit;
import org.codehaus.groovy.control.SourceUnit;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Implementation of {@link EnumElement} for Groovy.
 *
 * @author graemerocher
 * @since 1.0
 */
class GroovyEnumElement extends GroovyClassElement implements EnumElement {
    /**
     * @param sourceUnit The source unit
     * @param compilationUnit    The compilation unit
     * @param classNode          The {@link ClassNode}
     * @param annotationMetadata The annotation metadata
     */
    GroovyEnumElement(SourceUnit sourceUnit, CompilationUnit compilationUnit, ClassNode classNode, AnnotationMetadata annotationMetadata) {
        super(sourceUnit, compilationUnit, classNode, annotationMetadata);
    }

    @Override
    public List<String> values() {
        ClassNode cn = (ClassNode) getNativeType();
        List<String> values = cn.getFields().stream().filter(fn -> fn.getType().equals(cn)).map(FieldNode::getName).collect(Collectors.toList());
        return Collections.unmodifiableList(values);
    }
}
