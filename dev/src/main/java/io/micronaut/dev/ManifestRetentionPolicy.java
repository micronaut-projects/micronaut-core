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
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.env.DevelopmentMode;
import io.micronaut.context.reload.BeanRetentionPolicy;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.reflect.ClassUtils;
import io.micronaut.dev.manifest.DevManifest;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Retains across a restart the beans whose type the manifest names under
 * {@code micronaut.dev.retain}: a connection pool, a client, an engine the application's classes do
 * not define. A module declares its own with {@link io.micronaut.context.annotation.Retain} instead.
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Internal
@Singleton
@Requires(condition = DevelopmentMode.Active.class)
@Requires(beans = DevRuntime.class)
final class ManifestRetentionPolicy implements BeanRetentionPolicy {

    private static final Logger LOG = LoggerFactory.getLogger(ManifestRetentionPolicy.class);
    /**
     * The retained types.
     */
    private final List<Class<?>> retainedTypes = new ArrayList<>();

    @Inject
    ManifestRetentionPolicy(DevRuntime runtime) {
        this(runtime.manifest(), runtime.classLoader().getParent());
    }

    ManifestRetentionPolicy(DevManifest manifest, ClassLoader parent) {
        for (String name : manifest.retain()) {
            ClassUtils.forName(name, parent).ifPresentOrElse(retainedTypes::add,
                () -> LOG.warn("The type [{}] named by micronaut.dev.retain is not on the runtime classpath; nothing of that type is retained", name));
        }
    }

    @Override
    public boolean retain(BeanRegistration<?> registration) {
        Object bean = registration.getBean();
        for (Class<?> type : retainedTypes) {
            if (type.isAssignableFrom(registration.getBeanType()) || type.isInstance(bean)) {
                return true;
            }
        }
        return false;
    }
}
