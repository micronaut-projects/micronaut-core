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
package io.micronaut.context;

import io.micronaut.context.env.Environment;
import io.micronaut.context.env.PropertySourcePropertyResolver;
import io.micronaut.core.annotation.Internal;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;

/**
 * The values an executable method or a bean definition keeps for the next calls, laid out in one array that its owner
 * holds in a volatile field: the revision and the token they were computed for, then key, kind and value triples.
 * The array is never modified once published: a new key gives a longer copy, a new token or revision a new array.
 *
 * <p>Reads need no lock. The owner adds a value under its own lock, the one its configuration takes, after checking
 * that the token and the revision it computed the value for are still current: see {@link #with}.</p>
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
final class KeptValues {

    /**
     * A revision for which values are not kept.
     */
    static final long NOT_CACHEABLE = -1;
    /**
     * Values are not kept.
     */
    static final byte CACHE_NEVER = 0;
    /**
     * Values are kept for good: they depend on nothing that changes.
     */
    static final byte CACHE_STATIC = 1;
    /**
     * Values are kept until the properties change.
     */
    static final byte CACHE_PER_REVISION = 2;

    /**
     * A value kept by {@code getCachedValue}.
     */
    static final int VALUE = 0;
    /**
     * An annotation value kept by {@code getCachedAnnotationValue}.
     */
    static final int ANNOTATION_VALUE = 1;
    /**
     * An annotation kept by {@code getCachedAnnotation}.
     */
    static final int ANNOTATION = 2;
    /**
     * A value kept by {@code getCachedUnevaluatedValue}.
     */
    static final int UNEVALUATED_VALUE = 3;

    /**
     * Stands for a kept {@code null}, so that {@code null} means "not kept" in {@link #find}.
     */
    private static final Object NULL_VALUE = new Object();
    private static final int HEADER = 2;
    private static final int ENTRY = 3;

    private KeptValues() {
    }

    /**
     * The properties of an environment that keep a revision, which the values depending on property placeholders are
     * kept for.
     *
     * @param environment The environment
     * @return The properties, or {@code null} when the environment keeps no revision
     */
    static @Nullable PropertySourcePropertyResolver propertiesOf(@Nullable Environment environment) {
        return environment instanceof PropertyResolverDelegate delegate
            && delegate.delegate() instanceof PropertySourcePropertyResolver resolver ? resolver : null;
    }

    /**
     * The revision values are kept for in the given mode.
     *
     * @param mode       The cache mode
     * @param properties The properties, required by {@link #CACHE_PER_REVISION}
     * @return The revision, or {@link #NOT_CACHEABLE}
     */
    static long revision(byte mode, @Nullable PropertySourcePropertyResolver properties) {
        return switch (mode) {
            case CACHE_STATIC -> 0;
            case CACHE_PER_REVISION -> properties != null ? properties.getRevision() : NOT_CACHEABLE;
            default -> NOT_CACHEABLE;
        };
    }

    /**
     * Finds a kept value.
     *
     * @param values   The kept values, or {@code null}
     * @param token    The token the value was computed for, compared by identity
     * @param revision The revision the value was computed for
     * @param key      The key
     * @param kind     The kind of value
     * @return The kept value, masked, or {@code null} when none is kept
     */
    static @Nullable Object find(Object @Nullable [] values, @Nullable Object token, long revision, Object key, int kind) {
        if (values != null && values[1] == token && (long) values[0] == revision) {
            for (int i = HEADER; i < values.length; i += ENTRY) {
                if (values[i] == key && (int) values[i + 1] == kind) {
                    return values[i + 2];
                }
            }
        }
        return null;
    }

    /**
     * The values with one more, dropping the values of another token or revision. Called under the lock of the
     * owner, once {@link #find} has not found the key.
     *
     * @param values   The kept values, or {@code null}
     * @param token    The token the value was computed for
     * @param revision The revision the value was computed for
     * @param key      The key
     * @param kind     The kind of value
     * @param value    The value, masked
     * @return The new values, to publish
     */
    static Object[] with(Object @Nullable [] values, @Nullable Object token, long revision, Object key, int kind, Object value) {
        if (values == null || values[1] != token || (long) values[0] != revision) {
            return new Object[] {revision, token, key, kind, value};
        }
        Object[] newValues = Arrays.copyOf(values, values.length + ENTRY);
        newValues[values.length] = key;
        newValues[values.length + 1] = kind;
        newValues[values.length + 2] = value;
        return newValues;
    }

    /**
     * @param value A value
     * @return The value to keep, never {@code null}
     */
    static Object mask(@Nullable Object value) {
        return value != null ? value : NULL_VALUE;
    }

    /**
     * @param value A kept value
     * @return The value
     */
    static @Nullable Object unmask(Object value) {
        return value == NULL_VALUE ? null : value;
    }
}
