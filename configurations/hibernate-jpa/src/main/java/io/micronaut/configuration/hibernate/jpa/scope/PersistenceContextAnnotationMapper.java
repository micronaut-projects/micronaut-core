/*
 * Copyright 2017-2018 original authors
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

package io.micronaut.configuration.hibernate.jpa.scope;

import io.micronaut.core.annotation.AnnotationMapper;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.util.StringUtils;

import javax.inject.Inject;
import javax.inject.Named;
import javax.persistence.PersistenceContext;
import java.util.ArrayList;
import java.util.List;

/**
 * Allows using the {@link PersistenceContext} annotation in Micronaut.
 *
 * @author graemerocher
 * @since 1.0
 */
public class PersistenceContextAnnotationMapper implements AnnotationMapper<PersistenceContext> {
    @Override
    public Class<PersistenceContext> annotationType() {
        return PersistenceContext.class;
    }

    @Override
    public List<AnnotationValue<?>> map(AnnotationValue<PersistenceContext> annotation) {
        String name = annotation.get("name", String.class).orElse(null);
        List<AnnotationValue<?>> annotationValues = new ArrayList<>(3);
        annotationValues.add(AnnotationValue.builder(Inject.class).build());
        annotationValues.add(
                AnnotationValue.builder(CurrentSession.class)
                                .value(name)
                                .build()
        );


        if (StringUtils.isNotEmpty(name)) {
            annotationValues.add(AnnotationValue.builder(Named.class).value(name).build());
        }
        return annotationValues;
    }
}
