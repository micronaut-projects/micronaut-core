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
package io.micronaut.inject.ast.beans;

import java.util.Objects;
import java.util.function.Consumer;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.inject.ast.ConstructorElement;

/**
 * Represents the current bean constructor when used through the {@link io.micronaut.inject.ast.beans.BeanElementBuilder} API.
 *
 * @author graemerocher
 * @since 3.0.0
 */
public interface BeanConstructorElement extends ConstructorElement {

    /**
     * Process the bean parameters.
     *
     * @param parameterConsumer The parameter consumer
     * @return This bean method
     */
    default @NonNull BeanConstructorElement withParameters(@NonNull Consumer<BeanParameterElement[]> parameterConsumer) {
        Objects.requireNonNull(parameterConsumer, "The parameter consumer cannot be null");
        parameterConsumer.accept(getParameters());
        return this;
    }

    @NonNull
    @Override
    BeanParameterElement[] getParameters();
}
