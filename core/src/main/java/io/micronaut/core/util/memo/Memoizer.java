/*
 * Copyright 2017-2025 original authors
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
package io.micronaut.core.util.memo;

import io.micronaut.core.annotation.Experimental;

/**
 * An object that can optionally store additional caller-customizable memoized data. Essentially,
 * a memoizer contains a {@code Map<MemoizedReference, T>}, and {@link MemoizedReference#get(Memoizer)} does a
 * {@code computeIfAbsent} on that map.
 *
 * <p>Each {@link Memoizer} <i>type</i> is associated with a {@link MemoizerNamespace}. The
 * namespace keeps track of the fields ({@link MemoizedReference}s and {@link MemoizedFlag}s) that
 * are permitted for that namespace.
 *
 * <p>The namespace allows for the {@link Memoizer} implementation to be more efficient than a
 * simple {@link java.util.Map}, e.g. by storing booleans as a bit field.
 *
 * <p>To avoid interface calls, the actual accessor methods are on {@link MemoizedReference} and {@link MemoizedFlag},
 * and not part of this interface.
 *
 * @param <SELF> The type of the memoization namespace
 * @since 4.10.0
 * @author Jonas Konrad
 */
@SuppressWarnings("unused")
@Experimental
public interface Memoizer<SELF extends Memoizer<SELF>> {
}
