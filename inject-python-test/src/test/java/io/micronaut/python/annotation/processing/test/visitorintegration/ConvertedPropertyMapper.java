/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.python.annotation.processing.test.visitorintegration;

import io.micronaut.core.annotation.AnnotationClassValue;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.AnnotationValueBuilder;
import io.micronaut.inject.annotation.TypedAnnotationMapper;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.visitor.VisitorContext;

import java.util.List;
import java.util.Map;

/**
 * Resolves the converter class element through the visitor context while the annotated class is being built, the
 * way Micronaut Data's {@code MappedPropertyMapper} does, and records the persisted type of the converter.
 */
public class ConvertedPropertyMapper implements TypedAnnotationMapper<ConvertedProperty> {

    static final String ATTRIBUTE_CONVERTER = "io.micronaut.data.model.runtime.convert.AttributeConverter";

    @Override
    public Class<ConvertedProperty> annotationType() {
        return ConvertedProperty.class;
    }

    @Override
    public List<AnnotationValue<?>> map(AnnotationValue<ConvertedProperty> annotation, VisitorContext visitorContext) {
        AnnotationValueBuilder<PersistedAs> builder = AnnotationValue.builder(PersistedAs.class);
        annotation.stringValue("converter").ifPresent(converter -> {
            builder.member("converter", converter);
            visitorContext.getClassElement(converter).ifPresent(converterElement -> {
                ClassElement genericType = converterElement.getGenericType();
                Map<String, ClassElement> typeArguments = genericType.getTypeArguments(ATTRIBUTE_CONVERTER);
                ClassElement persistedType = typeArguments.get("Y");
                if (persistedType != null) {
                    builder.member("value", new AnnotationClassValue<>(persistedType.getName()));
                }
            });
        });
        return List.of(builder.build());
    }
}
