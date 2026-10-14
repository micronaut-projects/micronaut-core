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
import io.micronaut.context.reload.ResourceKind;
import io.micronaut.context.watch.ResourceChange;
import io.micronaut.core.io.file.FileSystemResourceLoader;
import io.micronaut.core.io.scan.ClassPathResourceLoader;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
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
 * of every file when the context starts. Only a configuration file refreshes the configuration: one the environment
 * looked for when it read its property sources, found or not, as {@link Environment#getConfigurationLocations()}
 * records them: an application or bootstrap file of a configuration location, a file {@code micronaut.config.files}
 * names, or a file a {@code micronaut.config.import} imports, an optional import of a file created later included.</p>
 *
 * @author graemerocher
 * @since 5.3.0
 */
final class ResourceNotifier {

    private static final Logger LOG = LoggerFactory.getLogger(ResourceNotifier.class);

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
        if (changes.changed().isEmpty() && changes.deleted().isEmpty()) {
            return false;
        }
        if (current == null) {
            // no environment to ask which files it reads: any of them may be one
            return true;
        }
        Environment environment;
        try {
            environment = current.getEnvironment();
        } catch (RuntimeException e) {
            LOG.debug("Cannot ask the environment for its configuration files", e);
            return true;
        }
        for (Set<Path> files : List.of(changes.changed(), changes.deleted())) {
            for (Path file : files) {
                if (isConfigurationFile(environment, file, roots)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Whether a file is one the environment looked for when it read its configuration, as
     * {@link Environment#isConfigurationLocation(String)} tells: the file itself, or the class path resource it is
     * below one of the roots.
     *
     * @param environment The environment of the running context
     * @param file The file
     * @param roots The configuration roots, each the root of the class path the file is read from
     * @return Whether it is a configuration file
     */
    static boolean isConfigurationFile(Environment environment, Path file, List<Path> roots) {
        Path absolute = file.toAbsolutePath().normalize();
        if (environment.isConfigurationLocation(FileSystemResourceLoader.PREFIX + absolute)) {
            return true;
        }
        for (Path root : roots) {
            Path normalizedRoot = root.toAbsolutePath().normalize();
            if (absolute.startsWith(normalizedRoot) && !absolute.equals(normalizedRoot)
                && environment.isConfigurationLocation(ClassPathResourceLoader.PREFIX + normalizedRoot.relativize(absolute))) {
                return true;
            }
        }
        return false;
    }
}
