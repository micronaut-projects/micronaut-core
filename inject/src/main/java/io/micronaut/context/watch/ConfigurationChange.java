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
package io.micronaut.context.watch;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.naming.NameUtils;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * One batch of configuration changes, as a refresh computed it, or the first batch of a watch that
 * asked for one, which describes the configuration as it is rather than a change.
 *
 * @param all Whether everything is to be considered changed, as a full refresh does
 * @param changed The keys whose value changed, was added or was removed
 * @param previous The previous values of the changed keys, absent for keys that were added
 * @param current The current values of the changed keys, absent for keys that were removed
 * @param initial Whether this is the first batch of a watch, delivered when it is registered: everything is
 * to be read as it is now, and {@link #all()} holds
 * @author graemerocher
 * @since 5.3.0
 */
@Experimental
@NullMarked
public record ConfigurationChange(boolean all, Set<String> changed, Map<String, @Nullable Object> previous, Map<String, @Nullable Object> current, boolean initial) {

    /**
     * Validating constructor.
     *
     * @param all Whether all
     * @param changed The changed keys
     * @param previous The previous values
     * @param current The current values
     * @param initial Whether initial
     */
    public ConfigurationChange {
        changed = Collections.unmodifiableSet(new LinkedHashSet<>(Objects.requireNonNull(changed, "changed")));
        previous = Collections.unmodifiableMap(new LinkedHashMap<>(Objects.requireNonNull(previous, "previous")));
        current = Collections.unmodifiableMap(new LinkedHashMap<>(Objects.requireNonNull(current, "current")));
    }

    /**
     * A change that is not a first batch.
     *
     * @param all Whether all
     * @param changed The changed keys
     * @param previous The previous values
     * @param current The current values
     */
    public ConfigurationChange(boolean all, Set<String> changed, Map<String, @Nullable Object> previous, Map<String, @Nullable Object> current) {
        this(all, changed, previous, current, false);
    }

    /**
     * The first batch of a watch that asked for one: every key, to be read as it is now.
     *
     * @return The change
     */
    public static ConfigurationChange ofInitial() {
        return new ConfigurationChange(true, Set.of(), Map.of(), Map.of(), true);
    }

    /**
     * A change of every key.
     *
     * @return The change
     */
    public static ConfigurationChange ofAll() {
        return new ConfigurationChange(true, Set.of(), Map.of(), Map.of());
    }

    /**
     * A change of the given keys, without their values.
     *
     * @param keys The keys
     * @return The change
     */
    public static ConfigurationChange ofKeys(Set<String> keys) {
        return new ConfigurationChange(false, keys, Map.of(), Map.of());
    }

    /**
     * Whether the change touches a prefix: the prefix itself, or a key below it at a dot boundary. Keys
     * and prefixes are compared in a form where camel case, hyphens, dots and an environment variable's
     * underscores all name the same property, since an environment variable stands for every dot and
     * hyphen spelling at once.
     *
     * @param prefix The prefix, such as {@code datasources.default}
     * @return True if a changed key is at or under the prefix, or everything changed
     */
    public boolean touches(String prefix) {
        if (all) {
            return true;
        }
        String normalizedPrefix = normalize(prefix);
        for (String key : changed) {
            String normalizedKey = normalize(key);
            if (normalizedKey.equals(normalizedPrefix) || normalizedKey.startsWith(normalizedPrefix + '.')) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether the change touches any of the prefixes.
     *
     * @param prefixes The prefixes
     * @return True if one is touched
     */
    public boolean touchesAny(String... prefixes) {
        for (String prefix : prefixes) {
            if (touches(prefix)) {
                return true;
            }
        }
        return false;
    }

    private static String normalize(String key) {
        String property = key;
        if (key.indexOf('_') >= 0 && key.equals(key.toUpperCase(Locale.ROOT))) {
            // an environment variable names the property with underscores for dots
            property = key.toLowerCase(Locale.ROOT).replace('_', '.');
        }
        StringBuilder normalized = new StringBuilder(property.length());
        for (String segment : property.split("\\.")) {
            if (!normalized.isEmpty()) {
                normalized.append('.');
            }
            // a segment given in camel case names the same property as its hyphenated form, and an environment
            // variable cannot tell a hyphen from a dot: both become a boundary
            normalized.append(NameUtils.hyphenate(segment, true).replace('-', '.'));
        }
        return normalized.toString();
    }
}
