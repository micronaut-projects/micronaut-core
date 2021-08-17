/*
 * Copyright 2017-2021 original authors
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
package io.micronaut.inject.beans.visitor;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.inject.ast.FieldElement;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;

import java.util.Objects;


/**
 * Allows reading and writing fields.
 *
 * @author graemerocher
 * @since 3.0.0
 */
@Internal
final class BeanFieldWriter extends BeanPropertyWriter {
    private final FieldElement fieldElement;

    /**
     * Default constructor.
     *
     * @param introspectionWriter The introspection writer
     * @param fieldType           The field type
     * @param fieldGenericType    The field generic type
     * @param fieldElement        The field element
     * @param index               The index
     */
    BeanFieldWriter(
            @NonNull BeanIntrospectionWriter introspectionWriter,
            @NonNull Type fieldType,
            @NonNull Type fieldGenericType,
            FieldElement fieldElement,
            int index) {
        super(
                introspectionWriter,
                fieldElement.getType(),
                fieldType,
                fieldGenericType,
                fieldElement.getName(),
                null,
                null,
                null,
                fieldElement.isFinal(),
                index,
                fieldElement.getAnnotationMetadata(),
                fieldElement.getGenericField().getTypeArguments()
        );
        this.fieldElement = Objects.requireNonNull(fieldElement, "Field element cannot be null");
    }

    @Override
    protected void writeWriteMethod(GeneratorAdapter writeMethod) {
        writeMethod.putField(beanType, fieldElement.getName(), propertyType);
    }

    @Override
    protected void writeReadMethod(GeneratorAdapter readMethod) {
        readMethod.getField(beanType, fieldElement.getName(), propertyType);
    }
}
