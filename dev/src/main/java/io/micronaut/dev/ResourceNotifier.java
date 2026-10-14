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
import io.micronaut.context.ApplicationContextConfiguration;
import io.micronaut.context.DefaultBeanContext;
import io.micronaut.context.env.Environment;
import io.micronaut.context.env.PropertySource;
import io.micronaut.context.env.PropertySourceLoader;
import io.micronaut.context.reload.ResourceKind;
import io.micronaut.context.watch.ResourceChange;
import io.micronaut.core.util.ConnectionString;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * Tells the running context's resource watches of the resources a batch changed, and decides whether the batch
 * changed the configuration.
 *
 * <p>The build tools type the whole of {@code src/main/resources} as the configuration root, so it holds more than
 * configuration: a GraphQL schema, a template or a data file kept there is a resource like any other. Every change
 * under a root reaches the watches of that root's kind, the configuration root included; a watch of
 * {@link ResourceKind#CONFIG} asked for the files of that root and is told of the configuration files too, as it is
 * of every file when the context starts. Only a configuration file refreshes the configuration: an application or
 * bootstrap file in one of the environment's configuration locations (the root, unless the application overrode them),
 * a file the environment read a property source from, such as one {@code micronaut.config.files} names, or a file a
 * {@code micronaut.config.import} declares, which an optional import may name before it exists.</p>
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
    private static final String CLASSPATH_ALL_PREFIX = "classpath*:";
    private static final String FILE_PREFIX = "file:";
    private static final Set<String> DEFAULT_EXTENSIONS = Set.of("properties", "yml", "yaml", "json", "toml", "groovy");
    /**
     * Where the environment looks for the application and bootstrap files unless the application overrides it.
     */
    private static final List<String> DEFAULT_LOCATIONS = List.of("classpath:/", "file:config/");
    private static final String CONFIG_IMPORT = "micronaut.config.import";
    private static final String RESOURCE_PATH = "resource-path";
    private static final String PROVIDER = "provider";
    private static final Pattern INDEX = Pattern.compile("\\[\\d+]");

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

    /**
     * The resource changes as the file system has them when the batch is handled, as {@link SourceChanges#settled()}
     * has the sources: the watcher's report of a write can arrive after the harness's or the IDE's report of the
     * deletion that followed it, and the other way round, and the later report would win the merge. A file that is
     * there is changed and one that is not is deleted, whichever event named it last. A file reported written that
     * is gone is removed as a deletion reported for it is, with the files known under it when it was a directory,
     * each recorded as belonging to its live root and reported under the kind of the deepest root it is under.
     *
     * @param resources The resource changes by kind, as the batches merged them
     * @param removed The resources a deletion of a path removes, recording each as belonging to its live root
     * @param kindOf The kind of the deepest resource root a file is under, if any
     * @return The settled changes
     */
    static Map<ResourceKind, SourceChanges> settle(Map<ResourceKind, SourceChanges> resources, Function<Path, Set<Path>> removed,
                                                   Function<Path, @Nullable ResourceKind> kindOf) {
        Map<ResourceKind, Set<Path>> changed = new LinkedHashMap<>();
        Map<ResourceKind, Set<Path>> deleted = new LinkedHashMap<>();
        for (Map.Entry<ResourceKind, SourceChanges> entry : resources.entrySet()) {
            ResourceKind kind = entry.getKey();
            Set<Path> kindChanged = changed.computeIfAbsent(kind, k -> new LinkedHashSet<>());
            Set<Path> kindDeleted = deleted.computeIfAbsent(kind, k -> new LinkedHashSet<>());
            for (Path file : entry.getValue().changed()) {
                if (Files.exists(file)) {
                    // a directory a harness reported written stays as it was reported
                    kindChanged.add(file);
                    continue;
                }
                for (Path gone : removed.apply(file)) {
                    ResourceKind goneKind = kindOf.apply(gone);
                    if (!Files.isRegularFile(gone)) {
                        deleted.computeIfAbsent(goneKind == null ? kind : goneKind, k -> new LinkedHashSet<>()).add(gone);
                    }
                }
            }
            for (Path file : entry.getValue().deleted()) {
                if (Files.isRegularFile(file)) {
                    kindChanged.add(file);
                } else if (!Files.isDirectory(file)) {
                    // a directory deleted and created again is not gone: its files are reported each as they are
                    kindDeleted.add(file);
                }
            }
        }
        Map<ResourceKind, SourceChanges> settled = new LinkedHashMap<>();
        for (Map.Entry<ResourceKind, Set<Path>> entry : changed.entrySet()) {
            ResourceKind kind = entry.getKey();
            Set<Path> kindDeleted = deleted.getOrDefault(kind, Set.of());
            entry.getValue().removeAll(kindDeleted);
            settled.put(kind, new SourceChanges(entry.getValue(), kindDeleted));
        }
        deleted.forEach((kind, files) -> settled.putIfAbsent(kind, new SourceChanges(Set.of(), files)));
        return settled.equals(resources) ? resources : settled;
    }

    private static boolean anyConfigurationFile(@Nullable ApplicationContext current, List<Path> roots, SourceChanges changes) {
        Set<String> extensions = extensions(current);
        Set<String> names = names();
        List<String> locations = locations(current);
        Set<String> origins = origins(current);
        Set<String> imports = Set.of();
        if (current != null && current.isRunning()) {
            try {
                imports = imports(current.getEnvironment().getPropertySources());
            } catch (RuntimeException e) {
                LOG.debug("Cannot list the configuration imports", e);
            }
        }
        for (Set<Path> files : List.of(changes.changed(), changes.deleted())) {
            for (Path file : files) {
                if (isConfigurationFile(file, roots, locations, names, extensions) || isPropertySource(file, roots, origins)
                    || isImport(file, roots, imports, extensions)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Whether a file is one the environment reads from the class path root as a property source, as
     * {@link #isConfigurationFile(Path, List, List, Set, Set)} with the default configuration locations.
     *
     * @param file The file
     * @param roots The configuration roots, each the root of the class path the file is read from
     * @param names The base names of the configuration files
     * @param extensions The extensions the property source loaders read
     * @return Whether it is a configuration file
     */
    static boolean isConfigurationFile(Path file, List<Path> roots, Set<String> names, Set<String> extensions) {
        return isConfigurationFile(file, roots, DEFAULT_LOCATIONS, names, extensions);
    }

    /**
     * Whether a file is one the environment reads from a configuration location as a property source: the
     * application's or the bootstrap configuration, of any active environment ({@code application-dev.yml}), in a
     * format a property source loader reads, directly in a {@code classpath:} location under a root or in a
     * {@code file:} location.
     *
     * @param file The file
     * @param roots The configuration roots, each the root of the class path the file is read from
     * @param locations The configuration locations, as {@code overrideConfigLocations} gives them
     * @param names The base names of the configuration files
     * @param extensions The extensions the property source loaders read
     * @return Whether it is a configuration file
     */
    static boolean isConfigurationFile(Path file, List<Path> roots, List<String> locations, Set<String> names, Set<String> extensions) {
        Path absolute = file.toAbsolutePath().normalize();
        Path parent = absolute.getParent();
        Path fileName = absolute.getFileName();
        if (parent == null || fileName == null || !isConfigurationLocation(parent, roots, locations)) {
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
        for (String origin : origins) {
            // micronaut.config.files may name a file relative to the working directory, as the environment reads it
            if (origin.startsWith(FILE_PREFIX) && isFile(origin.substring(FILE_PREFIX.length()), absolute)) {
                return true;
            }
        }
        for (Path root : roots) {
            String relative = relativeName(root, absolute);
            if (relative == null) {
                continue;
            }
            for (String prefix : List.of(CLASSPATH_PREFIX, CLASSPATH_ALL_PREFIX)) {
                if (origins.contains(prefix + relative) || origins.contains(prefix + "/" + relative)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Whether a file is one a {@code micronaut.config.import} of the environment declares, loaded or not: an optional
     * import of a file absent when the context started has no property source to tell it by, yet creating the file
     * changes the configuration. An import without an extension names a file of any extension a property source
     * loader reads.
     *
     * @param file The file
     * @param roots The configuration roots
     * @param imports The paths the imports declare, as {@link #imports(Iterable)} gives them
     * @param extensions The extensions the property source loaders read
     * @return Whether an import declares the file
     */
    static boolean isImport(Path file, List<Path> roots, Set<String> imports, Set<String> extensions) {
        if (imports.isEmpty()) {
            return false;
        }
        Path absolute = file.toAbsolutePath().normalize();
        List<Path> candidates = new ArrayList<>(2);
        candidates.add(absolute);
        Path fileName = absolute.getFileName();
        String name = fileName == null ? "" : fileName.toString();
        int dot = name.lastIndexOf('.');
        if (dot > 0 && extensions.contains(name.substring(dot + 1).toLowerCase(Locale.ROOT))) {
            candidates.add(absolute.resolveSibling(name.substring(0, dot)));
        }
        for (Path candidate : candidates) {
            for (String declared : imports) {
                if (isAbsolute(declared) && isFile(declared, candidate)) {
                    return true;
                }
            }
            for (Path root : roots) {
                String relative = relativeName(root, candidate);
                if (relative == null) {
                    continue;
                }
                if (imports.contains(relative)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * The files the {@code micronaut.config.import} declarations of the given property sources import from the class
     * path or the file system, whether or not they exist: the scalar forms ({@code optional:classpath://custom.yml}, a
     * list of them, or indexed) and the structured ones with a {@code resource-path}. A relative import resolves as
     * the environment resolves it, against the location of the property source that declares it.
     *
     * @param propertySources The property sources
     * @return The paths of the class path resources, relative to a class path root, and of the files, absolute
     */
    static Set<String> imports(Iterable<PropertySource> propertySources) {
        Set<String> imports = new HashSet<>();
        for (PropertySource propertySource : propertySources) {
            String origin = propertySource.getOrigin().location();
            for (String key : propertySource) {
                if (!key.startsWith(CONFIG_IMPORT)) {
                    continue;
                }
                String rest = key.substring(CONFIG_IMPORT.length());
                Object value = propertySource.get(key);
                if (rest.isEmpty() || INDEX.matcher(rest).matches()) {
                    addImports(value, origin, imports);
                } else if ((rest.startsWith(".") || rest.startsWith("[")) && rest.endsWith(RESOURCE_PATH) && value instanceof CharSequence path) {
                    Object provider = propertySource.get(key.substring(0, key.length() - RESOURCE_PATH.length()) + PROVIDER);
                    if (provider instanceof CharSequence name) {
                        addImport(name.toString(), path.toString(), null, imports);
                    }
                }
            }
        }
        return imports;
    }

    private static void addImports(@Nullable Object value, String origin, Set<String> imports) {
        if (value instanceof CharSequence declaration) {
            try {
                ConnectionString parsed = ConnectionString.parse(declaration.toString());
                if (parsed.getHosts().isEmpty()) {
                    addImport(parsed.getProtocol(), parsed.getCanonicalPath(), origin, imports);
                }
            } catch (RuntimeException e) {
                LOG.debug("Cannot parse the configuration import {}", declaration, e);
            }
        } else if (value instanceof Iterable<?> declarations) {
            for (Object declaration : declarations) {
                addImports(declaration, origin, imports);
            }
        } else if (value instanceof Map<?, ?> structured && structured.get(RESOURCE_PATH) instanceof CharSequence path
            && structured.get(PROVIDER) instanceof CharSequence provider) {
            addImport(provider.toString(), path.toString(), null, imports);
        }
    }

    /**
     * Resolves an import as the environment does: a class path import below the directory of a {@code classpath:}
     * property source that declares it, or of the class path root otherwise, and a relative file import beside the
     * file that declares it. A structured import has no declaring origin to resolve against: its importers read a
     * class path resource from the root and a relative file from the working directory.
     */
    private static void addImport(String provider, String path, @Nullable String origin, Set<String> imports) {
        String normalized = path.replace('\\', '/');
        if (normalized.isBlank() || normalized.endsWith("/")) {
            return;
        }
        try {
            switch (provider.toLowerCase(Locale.ROOT)) {
                case "classpath", "classpath*" -> {
                    if (normalized.startsWith("/")) {
                        return;
                    }
                    String base = "";
                    if (origin != null && origin.startsWith(CLASSPATH_PREFIX)) {
                        String declaring = origin.substring(CLASSPATH_PREFIX.length());
                        int slash = declaring.lastIndexOf('/');
                        base = slash >= 0 ? declaring.substring(0, slash + 1) : "";
                    }
                    String resolved = Path.of(base + normalized).normalize().toString().replace('\\', '/');
                    while (resolved.startsWith("/")) {
                        resolved = resolved.substring(1);
                    }
                    imports.add(resolved);
                }
                case "file" -> {
                    Path file = Path.of(normalized);
                    if (!file.isAbsolute()) {
                        if (origin == null) {
                            file = file.toAbsolutePath();
                        } else {
                            String declaring = origin.startsWith(FILE_PREFIX) ? origin.substring(FILE_PREFIX.length()) : origin;
                            Path directory = Path.of(declaring).isAbsolute() ? Path.of(declaring).getParent() : null;
                            if (directory == null) {
                                return;
                            }
                            file = directory.resolve(normalized);
                        }
                    }
                    imports.add(file.normalize().toString());
                }
                default -> {
                    // not a file: nothing under a root to watch
                }
            }
        } catch (InvalidPathException e) {
            LOG.debug("Cannot resolve the configuration import {}", path, e);
        }
    }

    /**
     * Whether a directory is one of the environment's configuration locations: {@code classpath:/} is a root itself,
     * {@code classpath:config/} the directory of that name under a root, and {@code file:config/} that directory of
     * the working directory.
     */
    private static boolean isConfigurationLocation(Path directory, List<Path> roots, List<String> locations) {
        for (String location : locations) {
            if (location.startsWith(CLASSPATH_PREFIX)) {
                String base = location.substring(CLASSPATH_PREFIX.length());
                while (base.startsWith("/")) {
                    base = base.substring(1);
                }
                for (Path root : roots) {
                    Path normalizedRoot = root.toAbsolutePath().normalize();
                    if ((base.isEmpty() ? normalizedRoot : normalizedRoot.resolve(base).normalize()).equals(directory)) {
                        return true;
                    }
                }
            } else if (location.startsWith(FILE_PREFIX) && isFile(location.substring(FILE_PREFIX.length()), directory)) {
                return true;
            }
        }
        return false;
    }

    /**
     * @return The path of a file below a root, with slashes, or null if the file is not below it
     */
    private static @Nullable String relativeName(Path root, Path absolute) {
        Path normalizedRoot = root.toAbsolutePath().normalize();
        if (!absolute.startsWith(normalizedRoot) || absolute.equals(normalizedRoot)) {
            return null;
        }
        StringBuilder relative = new StringBuilder();
        for (Path segment : normalizedRoot.relativize(absolute)) {
            if (!relative.isEmpty()) {
                relative.append('/');
            }
            relative.append(segment);
        }
        return relative.toString();
    }

    private static List<String> locations(@Nullable ApplicationContext current) {
        if (current != null && current.getContextConfiguration() instanceof ApplicationContextConfiguration configuration) {
            List<String> overridden = configuration.getOverrideConfigLocations();
            if (overridden != null) {
                return overridden;
            }
        }
        return DEFAULT_LOCATIONS;
    }

    private static boolean isAbsolute(String path) {
        try {
            return Path.of(path).isAbsolute();
        } catch (InvalidPathException e) {
            return false;
        }
    }

    private static boolean isFile(String location, Path absolute) {
        try {
            return Path.of(location).toAbsolutePath().normalize().equals(absolute);
        } catch (InvalidPathException e) {
            return false;
        }
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
