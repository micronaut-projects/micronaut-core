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
package io.micronaut.inject.qualifiers;

import io.micronaut.context.Qualifier;
import io.micronaut.core.annotation.Internal;

/**
 * Internal interface used by generated code to propagate qualifiers.
 *
 * @author graemerocher
 * @since 1.0
 * @param <T> The qualifier type
 */
@Internal
public interface Qualified<T> {
    /**
     * Override the bean qualifier.
     *
     * @param qualifier The bean qualifier to use
     */
    @Internal
    @SuppressWarnings("MethodName")
    void $withBeanQualifier(Qualifier<T> qualifier);
}
