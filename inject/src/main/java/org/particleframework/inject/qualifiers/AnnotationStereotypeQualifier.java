/*
 * Copyright 2017 original authors
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
package org.particleframework.inject.qualifiers;

import org.particleframework.context.Qualifier;
import org.particleframework.inject.BeanDefinition;
import org.particleframework.inject.BeanType;

import java.lang.annotation.Annotation;
import java.util.stream.Stream;

/**
 * A {@link Qualifier} that qualifies based on a bean stereotype
 *
 * @author Graeme Rocher
 * @since 1.0
 */
class AnnotationStereotypeQualifier<T> implements Qualifier<T> {

    private final Class<? extends Annotation> stereotype;

    public AnnotationStereotypeQualifier(Class<? extends Annotation> stereotype) {
        this.stereotype = stereotype;
    }

    @Override
    public <BT extends BeanType<T>> Stream<BT> reduce(Class<T> beanType, Stream<BT> candidates) {
        return candidates.filter(candidate -> candidate.getAnnotationMetadata().hasStereotype(stereotype));
    }
}
