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
package io.micronaut.dev;

import io.micronaut.dev.compile.SourceKind;
import org.jspecify.annotations.Nullable;

import java.util.Set;

/**
 * What a compile round did.
 *
 * @param failure The compilation that failed, if one did: nothing after it was compiled
 * @param compiled Whether anything was compiled
 * @param compiledKinds The languages compiled
 * @param affectedClasses The classes the round changed, for the dependents in other languages and the tests
 * @since 5.3.0
 */
record CompileRound(@Nullable CompileFailure failure, boolean compiled, Set<SourceKind> compiledKinds, Set<String> affectedClasses) {
    static CompileRound failed(CompileFailure failure) {
        return new CompileRound(failure, false, Set.of(), Set.of());
    }
}
