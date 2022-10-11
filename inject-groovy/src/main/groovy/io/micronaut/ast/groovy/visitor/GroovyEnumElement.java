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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.micronaut.ast.groovy.utils.AstAnnotationUtils;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.EnumConstantElement;
import io.micronaut.inject.ast.EnumElement;

import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.FieldNode;

/**
 * Implementation of {@link EnumElement} for Groovy.
 *
 * @author graemerocher
 * @since 1.0
 */
class GroovyEnumElement extends GroovyClassElement implements EnumElement {

    protected List<EnumConstantElement> enumConstants;
    protected List<String> values;

    /**
     * @param visitorContext     The visitor context
     * @param classNode          The {@link ClassNode}
     * @param annotationMetadata The annotation metadata
     */
    GroovyEnumElement(GroovyVisitorContext visitorContext, ClassNode classNode, AnnotationMetadata annotationMetadata) {
        this(visitorContext, classNode, annotationMetadata, 0);
    }

    /**
     * @param visitorContext     The visitor context
     * @param classNode          The {@link ClassNode}
     * @param annotationMetadata The annotation metadata
     * @param arrayDimensions    The number of array dimensions
     */
    GroovyEnumElement(GroovyVisitorContext visitorContext, ClassNode classNode, AnnotationMetadata annotationMetadata, int arrayDimensions) {
        super(visitorContext, classNode, annotationMetadata, null, arrayDimensions);
    }

    @Override
    public List<String> values() {
        if (values != null) {
            return values;
        }
        initEnum();
        return values;
    }

    @Override
    public List<EnumConstantElement> elements() {
        if (enumConstants != null) {
            return enumConstants;
        }
        initEnum();
        return enumConstants;
    }

    private void initEnum() {
        values = new ArrayList<>();
        enumConstants = new ArrayList<>();
        ClassNode nativeType = (ClassNode) getNativeType();
        for (FieldNode field : nativeType.getFields()) {
            if (field.getName().equals("MAX_VALUE") || field.getName().equals("MIN_VALUE")) {
                continue;
            }
            if (field.isEnum()) {
                values.add(field.getName());
                enumConstants.add(new GroovyEnumConstantElement(this, visitorContext, field, field, AstAnnotationUtils.getAnnotationMetadata(
                        sourceUnit,
                        compilationUnit,
                        field
                )));
            }
        }

        values = Collections.unmodifiableList(values);
        enumConstants = Collections.unmodifiableList(enumConstants);
    }

    @Override
    public ClassElement withArrayDimensions(int arrayDimensions) {
        return new GroovyEnumElement(visitorContext, classNode, getAnnotationMetadata(), arrayDimensions);
    }

}
