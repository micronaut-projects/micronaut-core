/*
 * Copyright 2017-2022 original authors
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
package io.micronaut.core.annotation;

import java.util.Optional;
import java.util.function.Function;

/**
 * A reference to a bean property in annotation metadata.
 * Annotation property reference consists of bean property owning type, the property name (which is the original
 * annotation member value) and a function which can be used to retrieve bean property value
 *
 * @param <T> The generic type of the bean property owning class
 * @param <R> The generic type of bean property value
 * @author Sergey Gavrilov
 * @since 3.4.0
 */
public final class AnnotationPropertyReference<T, R> {

    private final AnnotationClassValue<T> owningType;
    private final String propertyName;
    private final Function<T, R> propertyGetter;

    /**
     * Constructs annotation property property reference.
     *
     * @param owningType     the type owning referenced bean property
     * @param propertyName   the name of reference bean property
     * @param propertyGetter the function which can be used to access bean property
     */
    public AnnotationPropertyReference(AnnotationClassValue<T> owningType,
                                       String propertyName,
                                       Function<T, R> propertyGetter) {
        this.owningType = owningType;
        this.propertyName = propertyName;
        this.propertyGetter = propertyGetter;
    }

    /**
     * @return bean property name
     */
    public String getPropertyName() {
        return propertyName;
    }

    /**
     * @return bean property owning type
     */
    public AnnotationClassValue<T> getPropertyOwningType() {
        return owningType;
    }

    /**
     * Used to obtain bean property value when from the provided bean.
     *
     * @param bean object which property is obtained using respective property getter
     * @return bean property value
     */
    @SuppressWarnings("unchecked")
    public Optional<R> getPropertyValue(Object bean) {
        try {
            return Optional.ofNullable(propertyGetter.apply((T) bean));
        } catch (Throwable ex) {
            return Optional.empty();
        }
    }

}
