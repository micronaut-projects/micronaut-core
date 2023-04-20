/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.core.expressions;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;

/**
 * Context that can be used by evaluated expression to obtain objects required
 * for evaluation process.
 *
 * @since 4.0.0
 * @author Sergey Gavrilov
 */
@Internal
public interface ExpressionEvaluationContext extends AutoCloseable {

    /**
     * Expressions that are evaluated in non-static contexts can reference "this".
     *
     * <p>The object returned here is a reference to this.</p>
     *
     * @return The object that represents this.
     */
    @Nullable Object getThis();

    /**
     * Provides method argument by index.
     *
     * @param index argument index
     * @return argument value
     */
    Object getArgument(int index);

    /**
     * Provides bean by type.
     *
     * @param <T> type of required bean
     * @param type required bean class object
     * @return bean instance
     */
    <T> T getBean(Class<T> type);

    /**
     * Provides property by name.
     * @param name property name
     * @return property value or null
     */
    @Nullable
    String getProperty(String name);
}
