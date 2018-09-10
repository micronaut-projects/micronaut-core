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

package io.micronaut.security;

import io.micronaut.core.annotation.AnnotationMapper;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.security.rules.SecurityRule;

import javax.annotation.security.PermitAll;
import java.util.ArrayList;
import java.util.List;

/**
 * Allows using the {@link javax.annotation.security.PermitAll} annotation in Micronaut.
 *
 * @author Sergio del Amo
 * @since 1.0
 */
public class PermitAllAnnotationMapper implements AnnotationMapper<PermitAll> {
    @Override
    public Class<PermitAll> annotationType() {
        return PermitAll.class;
    }

    @Override
    public List<AnnotationValue<?>> map(AnnotationValue<PermitAll> annotation) {
        List<AnnotationValue<?>> annotationValues = new ArrayList<>(1);
        annotationValues.add(
                AnnotationValue.builder(Secured.class)
                                .value(SecurityRule.IS_ANONYMOUS)
                                .build()
        );
        return annotationValues;
    }
}
