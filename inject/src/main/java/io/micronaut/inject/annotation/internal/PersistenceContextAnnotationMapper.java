/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.inject.annotation.internal;

import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.util.StringUtils;
import io.micronaut.inject.annotation.NamedAnnotationMapper;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.visitor.VisitorContext;

import javax.inject.Inject;
import javax.inject.Named;
import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Allows using the {@link javax.persistence.PersistenceContext} annotation in Micronaut.
 *
 * @author graemerocher
 * @since 1.0
 */
@Internal
public class PersistenceContextAnnotationMapper implements NamedAnnotationMapper {

    private static final String TARGET_ANNOTATION = "io.micronaut.configuration.hibernate.jpa.scope.CurrentSession";
    private static final String SOURCE_ANNOTATION = "javax.persistence.PersistenceContext";

    @Override
    public String getName() {
        return SOURCE_ANNOTATION;
    }

    @Override
    public List<AnnotationValue<?>> map(AnnotationValue<Annotation> annotation, VisitorContext visitorContext) {
        Optional<ClassElement> hibernateCurrentSession = visitorContext.getClassElement(TARGET_ANNOTATION);
        if (hibernateCurrentSession.isPresent()) {
            String name = annotation.stringValue("name").orElse(null);
            List<AnnotationValue<?>> annotationValues = new ArrayList<>(3);
            annotationValues.add(AnnotationValue.builder(Inject.class).build());
            annotationValues.add(
                    AnnotationValue.builder(TARGET_ANNOTATION)
                            .value(name)
                            .build()
            );


            if (StringUtils.isNotEmpty(name)) {
                annotationValues.add(AnnotationValue.builder(Named.class).value(name).build());
            }
            return annotationValues;
        }
        return Collections.emptyList();
    }
}
