/*
 * Copyright 2017-2024 original authors
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
package io.micronaut.core.beans;

import io.micronaut.core.annotation.AnnotationMetadataDelegate;

import java.util.List;

/**
 * A variation of {@link BeanIntrospection} that is representing an enum as a bean.
 *
 * @param <E> The type of the enum
 * @author Denis Stepanov
 * @since 4.4
 */
public interface EnumBeanIntrospection<E extends Enum<E>> extends BeanIntrospection<E> {

    /**
     * @return The constants of the enum
     */
    List<EnumConstant<E>> getConstants();

    /**
     * The enum constant.
     *
     * @param <E> The enum type
     */
    interface EnumConstant<E> extends AnnotationMetadataDelegate {

        /**
         * @return The instance value.
         */
        E getValue();

    }

}
