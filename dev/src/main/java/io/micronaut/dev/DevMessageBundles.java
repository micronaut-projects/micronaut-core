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

import io.micronaut.context.MessageSource;
import io.micronaut.context.WatchableBeanContext;
import io.micronaut.context.annotation.Context;
import io.micronaut.context.env.DevelopmentActive;
import io.micronaut.context.event.BeanCreatedEvent;
import io.micronaut.context.event.BeanCreatedEventListener;
import io.micronaut.context.i18n.ResourceBundleMessageSource;
import io.micronaut.context.watch.ResourceChange;
import io.micronaut.context.reload.ResourceKind;
import io.micronaut.core.annotation.Internal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Has the message sources read their bundles again when a {@code .properties} file under a resource root changes, so
 * that an edited message bundle is served by the running generation, as a template is, without a restart. A bundle is
 * cached three times: by the message source, as resolved messages and bundles, and by the JDK, per class loader; each
 * {@link ResourceBundleMessageSource} the context created, whatever its scope, forgets all three. Recreating the
 * source instead would restart the application: the server's error responses read the message sources.
 *
 * <p>The JDK's cache for the generation's loader is cleared on every change, whether or not the context has such a
 * source: code that calls {@link ResourceBundle#getBundle(String)} itself, with the generation's loader as its own or
 * as the context loader, reads the edit too. A source created with {@code new} rather than as a bean keeps its own
 * caches.</p>
 *
 * <p>The {@code .properties} files of every root are watched. The build tools type the whole of
 * {@code src/main/resources} as the configuration root, where the bundles usually are, and a views or static root
 * nested in it takes the files under it, which are on the classpath all the same. Clearing the caches costs nothing
 * worth matching each file to a bundle's base name first.</p>
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Internal
@Context
@DevelopmentActive
final class DevMessageBundles implements BeanCreatedEventListener<MessageSource> {

    // the runtime's logger: these are the runtime's reloads
    private static final Logger LOG = LoggerFactory.getLogger(DevRuntime.class);
    private static final Duration SETTLE = Duration.ofMillis(250);

    private final WatchableBeanContext context;
    // the sources the context created, of any scope: weakly, so that a prototype nothing uses any more is not kept
    private final Set<ResourceBundleMessageSource> sources = Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));

    DevMessageBundles(WatchableBeanContext context) {
        this.context = context;
        for (ResourceKind kind : ResourceKind.values()) {
            context.resources(kind).include("**/*.properties").watch(this::changed);
        }
    }

    @Override
    public MessageSource onCreated(BeanCreatedEvent<MessageSource> event) {
        // a bean's definition may name only MessageSource, as a factory method returning one does
        if (event.getBean() instanceof ResourceBundleMessageSource source) {
            sources.add(source);
        }
        return event.getBean();
    }

    private void changed(ResourceChange change) {
        if (change.initial() || change.isEmpty()) {
            return;
        }
        int reloaded = reload();
        int files = change.changed().size() + change.removed().size();
        if (reloaded > 0) {
            LOG.info("Message bundles read again: {} message source(s) for {} changed file(s)", reloaded, files);
        } else {
            LOG.debug("Resource bundles cached for the generation's loader cleared for {} changed file(s)", files);
        }
        // a lookup in flight across the reload may have put back what it read before: forgotten again a moment later,
        // rather than synchronizing every lookup of the running application with the reload
        CompletableFuture.delayedExecutor(SETTLE.toMillis(), TimeUnit.MILLISECONDS).execute(this::reload);
    }

    private int reload() {
        if (!context.isRunning()) {
            // the generation stopped meanwhile
            return 0;
        }
        List<ResourceBundleMessageSource> created;
        synchronized (sources) {
            created = List.copyOf(sources);
        }
        Thread thread = Thread.currentThread();
        ClassLoader previous = thread.getContextClassLoader();
        try {
            ClassLoader generation = context.getClassLoader();
            // the bundles read by code of the generation, or through it as the context loader, with or without a source
            ResourceBundle.clearCache(generation);
            // the sources read their bundles through the generation's loader too
            thread.setContextClassLoader(generation);
            for (ResourceBundleMessageSource source : created) {
                source.reload();
            }
        } finally {
            thread.setContextClassLoader(previous);
        }
        return created.size();
    }
}
