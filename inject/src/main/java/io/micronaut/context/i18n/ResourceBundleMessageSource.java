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
package io.micronaut.context.i18n;

import io.micronaut.context.AbstractMessageSource;
import io.micronaut.core.util.ArgumentUtils;
import io.micronaut.core.util.clhm.ConcurrentLinkedHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A message source backed by a resource bundle.
 *
 * @author graemerocher
 * @since 1.2
 */
public class ResourceBundleMessageSource extends AbstractMessageSource {

    private static final Logger LOG = LoggerFactory.getLogger(ResourceBundleMessageSource.class);
    private final String baseName;
    private final Map<MessageKey, Optional<String>> messageCache =
                buildMessageCache();
    private final Map<MessageKey, Optional<ResourceBundle>> bundleCache =
            buildBundleCache();
    private final @Nullable ResourceBundle defaultBundle;

    /**
     * Default constructor.
     * @param baseName The base name of the message bundle
     */
    public ResourceBundleMessageSource(@NonNull String baseName) {
        this(baseName, null);
    }

    /**
     * Default constructor.
     * @param baseName The base name of the message bundle
     * @param defaultLocale The default locale to use if no message is found for the given locale
     */
    public ResourceBundleMessageSource(@NonNull String baseName, @Nullable Locale defaultLocale) {
        ArgumentUtils.requireNonNull("baseName", baseName);
        this.baseName = baseName;
        ResourceBundle defaultBundle;
        try {
            if (defaultLocale != null) {
                defaultBundle = ResourceBundle.getBundle(baseName, defaultLocale, getClassLoader());
            } else {
                defaultBundle = ResourceBundle.getBundle(baseName);
            }
        } catch (MissingResourceException e) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("No default bundle (locale: " + defaultLocale + ") found for base name " + baseName);
            }
            defaultBundle = null;
        }
        this.defaultBundle = defaultBundle;
    }

    @NonNull
    @Override
    public Optional<String> getRawMessage(@NonNull String code, @NonNull MessageContext context) {
        final Locale locale = defaultBundle != null ? context.getLocale(defaultBundle.getLocale()) : context.getLocale();
        MessageKey messageKey = new MessageKey(locale, code);
        Optional<String> opt = messageCache.get(messageKey);
        //noinspection OptionalAssignedToNull
        if (opt == null) {
            try {
                final Optional<ResourceBundle> bundle = resolveBundle(locale);
                if (bundle.isPresent()) {
                    return bundle.map(b -> b.getString(code));
                } else {
                    return resolveDefault(code);
                }
            } catch (MissingResourceException e) {
                opt = resolveDefault(code);
            }
            messageCache.put(messageKey, opt);
        }
        return opt;
    }

    /**
     * The class loader to use.
     *
     * @return The classloader
     */
    protected ClassLoader getClassLoader() {
        return getClass().getClassLoader();
    }

    /**
     * Build the cache used to store resolved messages.
     * @return The cache.
     */
    @NonNull
    protected Map<MessageKey, Optional<String>> buildMessageCache() {
        return new ConcurrentLinkedHashMap.Builder<MessageKey, Optional<String>>()
                .maximumWeightedCapacity(100)
                .build();
    }

    /**
     * Build the cache used to store resolved bundles.
     *
     * @return The cache.
     */
    @NonNull
    protected Map<MessageKey, Optional<ResourceBundle>> buildBundleCache() {
        return new ConcurrentHashMap<>(18);
    }

    @NonNull
    private Optional<String> resolveDefault(@NonNull String code) {
        Optional<String> opt;
        if (defaultBundle != null) {
            try {
                opt = Optional.of(defaultBundle.getString(code));
            } catch (MissingResourceException e) {
                opt = Optional.empty();
            }
        } else {
            opt = Optional.empty();
        }
        return opt;
    }

    private Optional<ResourceBundle> resolveBundle(Locale locale) {
        MessageKey key = new MessageKey(locale, baseName);
        final Optional<ResourceBundle> resourceBundle = bundleCache.get(key);
        //noinspection OptionalAssignedToNull
        if (resourceBundle != null) {
            return resourceBundle;
        } else {

            Optional<ResourceBundle> opt;
            try {
                opt = Optional.of(ResourceBundle.getBundle(baseName, locale, getClassLoader()));
            } catch (MissingResourceException e) {
                opt = Optional.empty();
            }
            bundleCache.put(key, opt);
            return opt;
        }
    }
}
