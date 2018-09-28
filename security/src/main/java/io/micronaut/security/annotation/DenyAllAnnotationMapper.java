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

package io.micronaut.security.annotation;

import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.inject.annotation.TypedAnnotationMapper;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.security.rules.SecurityRule;

import javax.annotation.security.DenyAll;
import java.util.ArrayList;
import java.util.List;

/**
 * Allows using the {@link javax.annotation.security.DenyAll} annotation in Micronaut.
 *
 * @author Sergio del Amo
 * @since 1.0
 */
public class DenyAllAnnotationMapper implements TypedAnnotationMapper<DenyAll> {
    @Override
    public Class<DenyAll> annotationType() {
        return DenyAll.class;
    }

    @Override
    public List<AnnotationValue<?>> map(AnnotationValue<DenyAll> annotation, VisitorContext visitorContext) {
        List<AnnotationValue<?>> annotationValues = new ArrayList<>(1);
        annotationValues.add(
                AnnotationValue.builder(Secured.class)
                                .value(SecurityRule.DENY_ALL)
                                .build()
        );
        return annotationValues;
    }
}
