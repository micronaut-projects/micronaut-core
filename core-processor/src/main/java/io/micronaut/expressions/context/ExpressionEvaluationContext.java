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
package io.micronaut.expressions.context;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.ParameterElement;
import io.micronaut.inject.ast.PropertyElement;

import java.util.List;

/**
 * Compilation context is a set of entries which can be referenced in evaluated expression
 * using the '#' sign followed by entry name.
 *
 * @since 4.0.0
 * @author Sergey Gavrilov
 */
@Internal
public interface ExpressionEvaluationContext {

    /**
     * @return Find the type that represents this.
     */
    @Nullable
    ClassElement findThis();

    /**
     * Search methods in compilation context by name.
     *
     * @param name searched method name
     * @return list of methods with provided name
     */
    @NonNull
    List<MethodElement> findMethods(@NonNull String name);

    /**
     * Search bean properties in compilation context by name.
     *
     * @param name searched property name
     * @return list of properties with provided name
     */
    @NonNull
    List<PropertyElement> findProperties(@NonNull String name);

    /**
     * Search method parameters in compilation context by name.
     *
     * @param name searched parameter name
     * @return list of parameters with provided name
     */
    @NonNull
    List<ParameterElement> findParameters(@NonNull String name);
}
