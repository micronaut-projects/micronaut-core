/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.context.i18n;

import io.micronaut.context.AbstractMessageSource;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.order.Ordered;
import io.micronaut.core.util.ArgumentUtils;
import io.micronaut.core.util.clhm.ConcurrentLinkedHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.jspecify.annotations.Nullable;
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
    private static final int DEFAULT_ORDER = 0;
    private static final int MAX_CACHE_SIZE = 100;
    private final String baseName;
    private final Map<MessageKey, Optional<String>> messageCache =
                buildMessageCache();
    private final Map<MessageKey, Optional<ResourceBundle>> bundleCache =
            buildBundleCache();
    private final @Nullable Locale defaultLocale;
    /**
     * Replaced only by {@link #reload()}, in development. A plain field, each use of which reads it once: nothing
     * orders those reads with the replacement, since a lookup reads it before the message cache, and a miss in the cache
     * is no happens-before edge. A lookup on another thread may use the previous bundle, and cache what it read, until it
     * sees the write. Not being final, it also loses the final-field guarantee for a source shared without safe
     * publication; the bean context publishes its beans safely.
     */
    private @Nullable ResourceBundle defaultBundle;
    private final int order;

    /**
     * Default constructor.
     * @param baseName The base name of the message bundle
     */
    public ResourceBundleMessageSource(String baseName) {
        this(baseName, null);
    }

    /**
     * Default constructor.
     * @param baseName The base name of the message bundle
     * @param order used for the implementation of the {@link Ordered#getOrder()} method
     */
    public ResourceBundleMessageSource(String baseName, int order) {
        this(baseName, null, order);
    }

    /**
     * Default constructor.
     * @param baseName The base name of the message bundle
     * @param defaultLocale The default locale to use if no message is found for the given locale
     */
    public ResourceBundleMessageSource(String baseName, @Nullable Locale defaultLocale) {
        this(baseName, defaultLocale, DEFAULT_ORDER);
    }

    /**
     * Default constructor.
     * @param baseName The base name of the message bundle
     * @param defaultLocale The default locale to use if no message is found for the given locale
     * @param order used for the implementation of the {@link Ordered#getOrder()} method
     */
    public ResourceBundleMessageSource(String baseName, @Nullable Locale defaultLocale, int order) {
        this.order = order;
        ArgumentUtils.requireNonNull("baseName", baseName);
        this.baseName = baseName;
        this.defaultLocale = defaultLocale;
        this.defaultBundle = loadDefaultBundle();
    }

    private @Nullable ResourceBundle loadDefaultBundle() {
        ResourceBundle defaultBundle;
        try {
            if (defaultLocale != null) {
                defaultBundle = ResourceBundle.getBundle(baseName, defaultLocale, getClassLoader());
            } else {
                defaultBundle = ResourceBundle.getBundle(baseName);
            }
        } catch (MissingResourceException e) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("No default bundle (locale: {}) found for base name {}", defaultLocale, baseName);
            }
            defaultBundle = null;
        }
        return defaultBundle;
    }

    /**
     * Reads the bundle again on the next lookup: the messages and bundles resolved so far are forgotten, and so is the
     * JDK's cache of the bundle for the class loaders this source reads it with. A development runtime calls this when
     * a file of the bundle changed, so that the running application serves the edit without a restart; nothing calls
     * it otherwise. Lookups are not synchronized with it: one in flight, or on a thread that has not seen the new
     * default bundle yet, may put back what it read before. Calling this again a moment later drops that; nothing
     * guarantees it is the last such lookup.
     *
     * @since 5.3.0
     */
    @Internal
    public void reload() {
        ResourceBundle.clearCache(getClassLoader());
        ResourceBundle.clearCache(ResourceBundleMessageSource.class.getClassLoader());
        defaultBundle = loadDefaultBundle();
        bundleCache.clear();
        messageCache.clear();
    }

    @Override
    public int getOrder() {
        return order;
    }

    @Override
    @SuppressWarnings("java:S2789") // performance optimization
    public Optional<String> getRawMessage(String code, MessageContext context) {
        // read once: a reload may replace it, or empty it, between two reads
        final ResourceBundle fallback = defaultBundle;
        final Locale locale = fallback != null ? context.getLocale(fallback.getLocale()) : context.getLocale();
        MessageKey messageKey = new MessageKey(locale, code);
        Optional<String> opt = messageCache.get(messageKey);
        //noinspection OptionalAssignedToNull
        if (opt == null) {
            try {
                final Optional<ResourceBundle> bundle = resolveBundle(locale);
                if (bundle.isPresent()) {
                    opt = bundle.map(b -> b.getString(code));
                } else {
                    opt = resolveDefault(code);
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
        ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
        return contextClassLoader != null ? contextClassLoader : getClass().getClassLoader();
    }

    /**
     * Build the cache used to store resolved messages.
     * @return The cache.
     */
    protected Map<MessageKey, Optional<String>> buildMessageCache() {
        return new ConcurrentLinkedHashMap.Builder<MessageKey, Optional<String>>()
                .maximumWeightedCapacity(MAX_CACHE_SIZE)
                .build();
    }

    /**
     * Build the cache used to store resolved bundles.
     *
     * @return The cache.
     */
    protected Map<MessageKey, Optional<ResourceBundle>> buildBundleCache() {
        return new ConcurrentLinkedHashMap.Builder<MessageKey, Optional<ResourceBundle>>()
            .maximumWeightedCapacity(MAX_CACHE_SIZE)
            .build();
    }

    private Optional<String> resolveDefault(String code) {
        Optional<String> opt;
        final ResourceBundle fallback = defaultBundle;
        if (fallback != null) {
            try {
                opt = Optional.of(fallback.getString(code));
            } catch (MissingResourceException e) {
                opt = Optional.empty();
            }
        } else {
            opt = Optional.empty();
        }
        return opt;
    }

    @SuppressWarnings("java:S2789") // performance optimization
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
