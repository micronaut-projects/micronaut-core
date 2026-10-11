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

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.DefaultBeanContext;
import io.micronaut.context.env.Environment;
import io.micronaut.context.env.PropertySource;
import io.micronaut.context.env.PropertySourceLoader;
import io.micronaut.context.reload.ResourceKind;
import io.micronaut.context.watch.ResourceChange;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Tells the running context's resource watches of the resources a batch changed, and decides whether the batch
 * changed the configuration.
 *
 * <p>The build tools type the whole of {@code src/main/resources} as the configuration root, so it holds more than
 * configuration: a GraphQL schema, a template or a data file kept there is a resource like any other. Every change
 * under a root reaches the watches of that root's kind, the configuration root included; a watch of
 * {@link ResourceKind#CONFIG} asked for the files of that root and is told of the configuration files too, as it is
 * of every file when the context starts. Only a configuration file refreshes the configuration: an application or
 * bootstrap file at the root, or a file the environment read a property source from, such as one
 * {@code micronaut.config.files} names.</p>
 *
 * @author graemerocher
 * @since 5.3.0
 */
final class ResourceNotifier {

    private static final Logger LOG = LoggerFactory.getLogger(ResourceNotifier.class);
    /**
     * The extensions of the property source loaders core and the configuration modules provide, for a context that
     * is not running to ask.
     */
    private static final String CLASSPATH_PREFIX = "classpath:";
    private static final String FILE_PREFIX = "file:";
    private static final Set<String> DEFAULT_EXTENSIONS = Set.of("properties", "yml", "yaml", "json", "toml", "groovy");

    private ResourceNotifier() {
    }

    /**
     * Tells the watches of the running context of the resources changed.
     *
     * @param current The running context, if any
     * @param resources The resource changes by kind
     * @param rootsOf The roots of a kind
     * @param browsers What refreshes the browsers for a change of static files or templates
     * @return Whether a configuration file changed, which refreshes the configuration
     */
    static boolean notify(@Nullable ApplicationContext current, Map<ResourceKind, SourceChanges> resources,
                          Function<ResourceKind, List<Path>> rootsOf, Consumer<SourceChanges> browsers) {
        boolean configurationChanged = false;
        for (Map.Entry<ResourceKind, SourceChanges> entry : resources.entrySet()) {
            ResourceKind kind = entry.getKey();
            SourceChanges changes = entry.getValue();
            List<Path> roots = rootsOf.apply(kind);
            if (kind == ResourceKind.CONFIG && !configurationChanged) {
                configurationChanged = anyConfigurationFile(current, roots, changes);
            }
            if (current instanceof DefaultBeanContext defaultBeanContext && current.isRunning()) {
                defaultBeanContext.notifyResourceChange(new ResourceChange(kind, roots,
                    new ArrayList<>(changes.changed()), new ArrayList<>(changes.deleted()), false));
            }
            if (kind == ResourceKind.STATIC || kind == ResourceKind.VIEWS) {
                browsers.accept(changes);
            }
        }
        return configurationChanged;
    }

    private static boolean anyConfigurationFile(@Nullable ApplicationContext current, List<Path> roots, SourceChanges changes) {
        Set<String> extensions = extensions(current);
        Set<String> names = names();
        Set<String> origins = origins(current);
        for (Set<Path> files : List.of(changes.changed(), changes.deleted())) {
            for (Path file : files) {
                if (isConfigurationFile(file, roots, names, extensions) || isPropertySource(file, roots, origins)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Whether a file is one the environment reads from the class path root as a property source: the application's
     * or the bootstrap configuration, of any active environment ({@code application-dev.yml}), in a format a property
     * source loader reads.
     *
     * @param file The file
     * @param roots The configuration roots, each the root of the class path the file is read from
     * @param names The base names of the configuration files
     * @param extensions The extensions the property source loaders read
     * @return Whether it is a configuration file
     */
    static boolean isConfigurationFile(Path file, List<Path> roots, Set<String> names, Set<String> extensions) {
        Path absolute = file.toAbsolutePath().normalize();
        Path parent = absolute.getParent();
        Path fileName = absolute.getFileName();
        if (parent == null || fileName == null || roots.stream().noneMatch(root -> root.toAbsolutePath().normalize().equals(parent))) {
            return false;
        }
        String name = fileName.toString();
        int dot = name.lastIndexOf('.');
        if (dot <= 0 || !extensions.contains(name.substring(dot + 1).toLowerCase(Locale.ROOT))) {
            return false;
        }
        String base = name.substring(0, dot);
        for (String configurationName : names) {
            if (base.equals(configurationName) || base.startsWith(configurationName + "-")) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether a file is where a property source of the running environment came from, such as one
     * {@code micronaut.config.files} names: {@code classpath:custom.yml} is read from a configuration root.
     *
     * @param file The file
     * @param roots The configuration roots
     * @param origins The locations the property sources of the environment came from
     * @return Whether the environment read the file as a property source
     */
    static boolean isPropertySource(Path file, List<Path> roots, Set<String> origins) {
        if (origins.isEmpty()) {
            return false;
        }
        Path absolute = file.toAbsolutePath().normalize();
        if (origins.contains(absolute.toString()) || origins.contains(FILE_PREFIX + absolute)) {
            return true;
        }
        for (Path root : roots) {
            Path normalizedRoot = root.toAbsolutePath().normalize();
            if (!absolute.startsWith(normalizedRoot) || absolute.equals(normalizedRoot)) {
                continue;
            }
            StringBuilder relative = new StringBuilder();
            for (Path segment : normalizedRoot.relativize(absolute)) {
                if (!relative.isEmpty()) {
                    relative.append('/');
                }
                relative.append(segment);
            }
            if (origins.contains(CLASSPATH_PREFIX + relative) || origins.contains(CLASSPATH_PREFIX + "/" + relative)) {
                return true;
            }
        }
        return false;
    }

    private static Set<String> origins(@Nullable ApplicationContext current) {
        Set<String> origins = new HashSet<>();
        if (current != null && current.isRunning()) {
            try {
                for (PropertySource propertySource : current.getEnvironment().getPropertySources()) {
                    origins.add(propertySource.getOrigin().location());
                }
            } catch (RuntimeException e) {
                LOG.debug("Cannot list the property sources", e);
            }
        }
        return origins;
    }

    static Set<String> names() {
        Set<String> names = new HashSet<>();
        names.add(Environment.DEFAULT_NAME);
        names.add(Environment.BOOTSTRAP_NAME);
        String bootstrapName = System.getProperty(Environment.BOOTSTRAP_NAME_PROPERTY);
        if (bootstrapName != null && !bootstrapName.isBlank()) {
            names.add(bootstrapName);
        }
        return names;
    }

    private static Set<String> extensions(@Nullable ApplicationContext current) {
        Set<String> extensions = new HashSet<>(DEFAULT_EXTENSIONS);
        if (current != null && current.isRunning()) {
            try {
                for (PropertySourceLoader loader : current.getEnvironment().getPropertySourceLoaders()) {
                    for (String extension : loader.getExtensions()) {
                        extensions.add(extension.toLowerCase(Locale.ROOT));
                    }
                }
            } catch (RuntimeException e) {
                LOG.debug("Cannot list the property source loaders: using the defaults", e);
            }
        }
        return extensions;
    }
}
