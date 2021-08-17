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
package io.micronaut.aop.internal;

import io.micronaut.aop.InterceptorBean;
import io.micronaut.core.annotation.AnnotationClassValue;
import io.micronaut.core.annotation.AnnotationUtil;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.AnnotationValueBuilder;
import io.micronaut.core.annotation.Internal;
import io.micronaut.inject.annotation.TypedAnnotationMapper;
import io.micronaut.inject.visitor.VisitorContext;

import java.lang.annotation.Annotation;
import java.util.Collections;
import java.util.List;

@Internal
public class InterceptorBeanMapper implements TypedAnnotationMapper<InterceptorBean> {

    @Override
    public Class<InterceptorBean> annotationType() {
        return InterceptorBean.class;
    }

    @Override
    public List<AnnotationValue<?>> map(AnnotationValue<InterceptorBean> annotation, VisitorContext visitorContext) {
        final AnnotationValueBuilder<Annotation> builder = AnnotationValue.builder(AnnotationUtil.ANN_INTERCEPTOR_BINDINGS);
        final AnnotationClassValue<?>[] values = annotation.annotationClassValues("value");
        AnnotationValue[] bindings = new AnnotationValue[values.length];
        for (int i = 0; i < values.length; i++) {
            bindings[i] = AnnotationValue.builder(AnnotationUtil.ANN_INTERCEPTOR_BINDING).value(values[i].getName()).build();
        }
        return Collections.singletonList(builder.values(bindings).build());
    }
}
