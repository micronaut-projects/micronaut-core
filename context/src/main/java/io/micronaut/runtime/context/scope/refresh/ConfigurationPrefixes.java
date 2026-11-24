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
package io.micronaut.runtime.context.scope.refresh;

import io.micronaut.context.annotation.ConfigurationReader;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.naming.Named;
import io.micronaut.inject.BeanDefinition;
import org.jspecify.annotations.Nullable;

/**
 * The prefix a configuration bean's keys are under, as the refresher decides which beans a change touches. The
 * development runtime decides with it which configurations a refresh left stale, so the two cannot disagree.
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Internal
public final class ConfigurationPrefixes {

    private ConfigurationPrefixes() {
    }

    /**
     * The prefix a configuration bean is bound under: the declared one, with the name of an
     * {@code @EachProperty} entry in place of its wildcard. The wildcard is the last segment of the entry itself
     * and an inner one of a configuration nested in the entry, such as {@code micronaut.http.services.*.ssl}. A
     * wildcard no name replaces, as of a bean with no entry name or nested in several entries, ends the prefix
     * there, which covers a change of any entry rather than none.
     *
     * @param definition The definition of the bean
     * @param bean       The bean, whose name names its entry when the definition declares none, if known
     * @return The prefix, or null for a bean that is not bound to the configuration
     */
    public static @Nullable String of(BeanDefinition<?> definition, @Nullable Object bean) {
        String declared = definition.stringValue(ConfigurationReader.class, ConfigurationReader.PREFIX).orElse(null);
        if (declared == null) {
            return null;
        }
        // the entry of an @EachProperty bean is its declared qualifier, whatever the bean implements
        String name = definition.getDeclaredQualifier() instanceof Named named ? named.getName() : null;
        if (name == null && bean instanceof Named named) {
            name = named.getName();
        }
        return resolve(declared, name);
    }

    /**
     * The declared prefix of a configuration bean with its first wildcard replaced by the entry's name, and cut
     * before any wildcard left.
     *
     * @param prefix The declared prefix
     * @param name   The name of the entry, if any
     * @return The prefix the bean's keys are under
     */
    static String resolve(String prefix, @Nullable String name) {
        String resolved = prefix;
        if (name != null) {
            int dot = wildcard(resolved);
            int bracket = resolved.indexOf("[*]");
            if (dot >= 0 && (bracket < 0 || dot < bracket)) {
                resolved = resolved.substring(0, dot) + "." + name + resolved.substring(dot + 2);
            } else if (bracket >= 0) {
                resolved = resolved.substring(0, bracket) + "[" + name + "]" + resolved.substring(bracket + 3);
            }
        }
        int dot = wildcard(resolved);
        int bracket = resolved.indexOf("[*]");
        int cut = dot < 0 ? bracket : bracket < 0 ? dot : Math.min(dot, bracket);
        return cut >= 0 ? resolved.substring(0, cut) : resolved;
    }

    /**
     * @return Where a {@code .*} segment starts in the prefix: one followed by its end, a dot or a bracket, and not
     *         a key that merely starts with an asterisk
     */
    private static int wildcard(String prefix) {
        int from = 0;
        while (true) {
            int index = prefix.indexOf(".*", from);
            if (index < 0) {
                return -1;
            }
            int end = index + 2;
            if (end == prefix.length() || prefix.charAt(end) == '.' || prefix.charAt(end) == '[') {
                return index;
            }
            from = index + 1;
        }
    }
}
