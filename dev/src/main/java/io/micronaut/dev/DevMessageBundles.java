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

import io.micronaut.context.BeanRegistration;
import io.micronaut.context.MessageSource;
import io.micronaut.context.WatchableBeanContext;
import io.micronaut.context.annotation.Context;
import io.micronaut.context.env.DevelopmentActive;
import io.micronaut.context.i18n.ResourceBundleMessageSource;
import io.micronaut.context.watch.ResourceChange;
import io.micronaut.context.reload.ResourceKind;
import io.micronaut.core.annotation.Internal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Has the message sources read their bundles again when a {@code .properties} file under a resource root changes, so
 * that an edited message bundle is served by the running generation, as a template is, without a restart. A bundle is
 * cached three times: by the message source, as resolved messages and bundles, and by the JDK, per class loader; each
 * {@link ResourceBundleMessageSource} the context created forgets all three. Recreating the source instead would
 * restart the application: the server's error responses read the message sources.
 *
 * <p>The build tools type the whole of {@code src/main/resources} as the configuration root, where the bundles
 * usually are, so the configuration, message bundle and other roots are watched. Clearing the caches costs nothing
 * worth matching each file to a bundle's base name first.</p>
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Internal
@Context
@DevelopmentActive
final class DevMessageBundles {

    // the runtime's logger: these are the runtime's reloads
    private static final Logger LOG = LoggerFactory.getLogger(DevRuntime.class);
    private static final Duration SETTLE = Duration.ofMillis(250);
    private static final List<ResourceKind> KINDS = List.of(ResourceKind.CONFIG, ResourceKind.I18N, ResourceKind.OTHER);

    private final WatchableBeanContext context;

    DevMessageBundles(WatchableBeanContext context) {
        this.context = context;
        for (ResourceKind kind : KINDS) {
            context.resources(kind).include("**/*.properties").watch(this::changed);
        }
    }

    private void changed(ResourceChange change) {
        if (change.initial() || change.isEmpty()) {
            return;
        }
        int reloaded = reload();
        if (reloaded > 0) {
            LOG.info("Message bundles read again: {} message source(s) for {} changed file(s)", reloaded, change.changed().size() + change.removed().size());
            // a lookup in flight across the reload may have put back what it read before: forgotten again a moment later,
            // rather than synchronizing every lookup of the running application with the reload
            CompletableFuture.delayedExecutor(SETTLE.toMillis(), TimeUnit.MILLISECONDS).execute(this::reload);
        }
    }

    private int reload() {
        int reloaded = 0;
        Thread thread = Thread.currentThread();
        ClassLoader previous = thread.getContextClassLoader();
        // the bundles are read through the generation's loader, whose JDK cache is cleared
        thread.setContextClassLoader(context.getClassLoader());
        try {
            for (BeanRegistration<MessageSource> registration : context.getActiveBeanRegistrations(MessageSource.class)) {
                if (registration.getBean() instanceof ResourceBundleMessageSource source) {
                    source.reload();
                    reloaded++;
                }
            }
        } catch (IllegalStateException e) {
            // the generation stopped meanwhile
            LOG.debug("The message sources could not be read again", e);
        } finally {
            thread.setContextClassLoader(previous);
        }
        return reloaded;
    }
}
