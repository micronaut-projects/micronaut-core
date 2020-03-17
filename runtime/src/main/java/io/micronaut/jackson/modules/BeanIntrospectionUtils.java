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

package io.micronaut.jackson.modules;

import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.beans.BeanProperty;
import io.micronaut.core.beans.BeanWrapper;
import io.micronaut.core.beans.exceptions.IntrospectionException;
import io.micronaut.core.reflect.ClassUtils;
import io.micronaut.inject.qualifiers.ClosestTypeArgumentQualifier;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.util.Optional;

/**
 * Utility class to work with {@link BeanIntrospection} for classes annotated with {@link io.micronaut.core.annotation.Introspected}.
 *
 * @author sdelamo
 * @since 1.3
 */
public class BeanIntrospectionUtils  {
    private static final Logger LOG = ClassUtils.getLogger(ClosestTypeArgumentQualifier.class);

    /**
     *
     * @param property The bean's property
     * @param bean The bean being introspected
     * @param annotationClass The annotation class
     * @param annotationValueClass The annotation class's value
     * @return returns <code>null</code> if neither the property nor the class is annotated withe supplied annotation. If the property is annotated, it takes precedence over the annotation in the class.
     */
    @Nullable
    public static Object parseAnnotationValueForProperty(String property,
                                                         Object bean,
                                                         Class annotationClass,
                                                         Class annotationValueClass) {
        try {
            BeanWrapper wrapper = BeanWrapper.getWrapper(bean);
            BeanIntrospection beanIntrospection = wrapper.getIntrospection();
            AnnotationValue beanAnnotationValue = beanIntrospection.getAnnotation(annotationClass);
            Optional<BeanProperty> beanPropertyOptional = beanIntrospection.getProperty(property);
            if (beanPropertyOptional.isPresent()) {
                BeanProperty beanProperty = beanPropertyOptional.get();
                AnnotationValue beanPropertyAnnotationValue = beanProperty.getAnnotation(annotationClass);
                if (beanPropertyAnnotationValue != null) {
                    Optional annotationValueOptional = beanPropertyAnnotationValue.getValue(annotationValueClass);
                    if (annotationValueOptional.isPresent()) {
                        return annotationValueOptional.get();
                    }
                } else {
                    if (beanAnnotationValue != null) {
                        Optional annotationValueOptional = beanAnnotationValue.getValue(annotationValueClass);
                        if (annotationValueOptional.isPresent()) {
                            return annotationValueOptional.get();
                        }
                    }
                }
            }
        } catch (IntrospectionException e) {
            if (LOG.isWarnEnabled()) {
                LOG.warn("{}", e.getMessage());
            }
        }
        return null;
    }
}
