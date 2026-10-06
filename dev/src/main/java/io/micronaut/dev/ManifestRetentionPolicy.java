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

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Retains across a restart the beans whose type the manifest names under
 * {@code micronaut.dev.retain}: a connection pool, a client, an engine the application's classes do
 * not define. The {@link DevManifest#DEFAULT_RETAIN connection pools} are retained without being named,
 * unless {@code micronaut.dev.retain-defaults=false}: a JDBC {@code DataSource} is dropped, and opened
 * again, when the configuration under {@code datasources} changes, an R2DBC {@code ConnectionFactory}
 * when the configuration under {@code r2dbc} does.
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
     * The configuration a default type is created from, a change under which drops the retained bean.
     */
    private static final Map<String, Set<String>> DEFAULT_PREFIXES = Map.of(
        "javax.sql.DataSource", Set.of("datasources"),
        "io.r2dbc.spi.ConnectionFactory", Set.of("r2dbc")
    );

    /**
     * The retained types, with the configuration prefixes that invalidate their beans.
     */
    private final Map<Class<?>, Set<String>> retainedTypes = new LinkedHashMap<>();

    @Inject
    ManifestRetentionPolicy(DevRuntime runtime) {
        this(runtime.manifest(), runtime.classLoader().getParent());
    }

    ManifestRetentionPolicy(DevManifest manifest, ClassLoader parent) {
        for (String name : manifest.retain()) {
            ClassUtils.forName(name, parent).ifPresentOrElse(type -> retainedTypes.put(type, DEFAULT_PREFIXES.getOrDefault(name, Set.of())),
                () -> LOG.warn("The type [{}] named by micronaut.dev.retain is not on the runtime classpath; nothing of that type is retained", name));
        }
        if (manifest.retainDefaults()) {
            for (String name : DevManifest.DEFAULT_RETAIN) {
                // a default absent from the classpath is a library the application does not use
                ClassUtils.forName(name, parent).ifPresent(type -> retainedTypes.putIfAbsent(type, DEFAULT_PREFIXES.getOrDefault(name, Set.of())));
            }
        }
    }

    @Override
    public boolean retain(BeanRegistration<?> registration) {
        return !retainingTypes(registration).isEmpty();
    }

    @Override
    public Set<String> observedConfigurationPrefixes(BeanRegistration<?> registration) {
        Set<String> prefixes = new LinkedHashSet<>();
        for (Class<?> type : retainingTypes(registration)) {
            prefixes.addAll(retainedTypes.get(type));
        }
        return prefixes;
    }

    private List<Class<?>> retainingTypes(BeanRegistration<?> registration) {
        Object bean = registration.getBean();
        return retainedTypes.keySet().stream()
            .filter(type -> type.isAssignableFrom(registration.getBeanType()) || type.isInstance(bean))
            .toList();
    }
}
