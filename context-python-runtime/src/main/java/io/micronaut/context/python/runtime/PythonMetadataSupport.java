/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.context.python.runtime;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.UsedByGeneratedCode;

/**
 * The entry points the generated classes initialize themselves through. A generated class passes its bean type; the
 * state is materialized once per bean type from the model the runtime already holds, or from the saved model
 * resource when the class was written at build time.
 *
 * @since 5.3.0
 */
@Internal
@UsedByGeneratedCode
public final class PythonMetadataSupport {

    private PythonMetadataSupport() {
    }

    /**
     * @param beanType The bean type
     * @return The definition state
     */
    public static PythonDefinitionState definitionState(Class<?> beanType) {
        return PythonRuntimeMetadata.holder(beanType).definitionState();
    }

    /**
     * @param beanType The bean type
     * @return The introspection state
     */
    public static PythonIntrospectionState introspectionState(Class<?> beanType) {
        return PythonRuntimeMetadata.holder(beanType).introspectionState();
    }
}
