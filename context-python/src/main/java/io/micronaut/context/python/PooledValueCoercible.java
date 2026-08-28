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
package io.micronaut.context.python;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;

/**
 * A generated pooled Python stub that can resolve its Python value for a specific context.
 */
@Internal
@Experimental
public interface PooledValueCoercible extends ValueCoercible {

    /**
     * Converts the type to a Truffle value in the given context.
     * @param context The target context
     * @return The value
     */
    @NonNull Value asPolyglotValue(Context context);
}
