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
package io.micronaut.http.util;

import io.micronaut.core.annotation.Internal;

import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bounds the number of distinct {@link Locale}s that request values resolve to. The JDK keeps
 * per-locale caches without a bound (formatters, text providers, resource bundles), so a client
 * that sends a new language tag with every request must not get a new locale every time.
 * <p>
 * A locale the JDK provides is always kept. Other locales, e.g. with variants, extensions or
 * private-use subtags, are kept until a fixed number of them has been seen. After that, an
 * unseen one resolves to the closest available locale, or to the default.
 *
 * @author Denis Stepanov
 * @since 4.10.35
 */
@Internal
final class BoundedLocales {
    static final BoundedLocales INSTANCE = new BoundedLocales(1024);

    private static final Set<Locale> AVAILABLE = Set.of(Locale.getAvailableLocales());

    private final int maxOther;
    private final Set<Locale> other = ConcurrentHashMap.newKeySet();

    BoundedLocales(int maxOther) {
        this.maxOther = maxOther;
    }

    /**
     * @param locale The locale parsed from a request value
     * @return The locale to use
     */
    Locale resolve(Locale locale) {
        if (AVAILABLE.contains(locale) || other.contains(locale)) {
            return locale;
        }
        // the size check races, which may admit a few more than the maximum
        if (other.size() < maxOther) {
            other.add(locale);
            return locale;
        }
        return closestAvailable(locale);
    }

    @SuppressWarnings("deprecation") // Locale.of is JDK 19+
    private static Locale closestAvailable(Locale locale) {
        String language = locale.getLanguage();
        String script = locale.getScript();
        String country = locale.getCountry();
        Locale candidate = new Locale(language, country);
        if (!script.isEmpty()) {
            Locale withScript = new Locale.Builder().setLanguage(language).setScript(script).setRegion(country).build();
            if (AVAILABLE.contains(withScript)) {
                return withScript;
            }
        }
        if (AVAILABLE.contains(candidate)) {
            return candidate;
        }
        candidate = new Locale(language);
        if (AVAILABLE.contains(candidate)) {
            return candidate;
        }
        return Locale.getDefault();
    }
}
