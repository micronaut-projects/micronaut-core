/*
 * Copyright 2017-2022 original authors
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
package io.micronaut.core.io.service;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.io.IOUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.ServiceConfigurationError;
import java.util.Set;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Wrapper class for the tasks required to find services of a particular type.
 *
 * @param <S> service type
 */
@Internal
final class ServiceScanner<S> {
    private final ClassLoader classLoader;
    private final String serviceName;
    private final Predicate<String> lineCondition;
    private final Function<String, S> transformer;

    public ServiceScanner(ClassLoader classLoader, String serviceName, Predicate<String> lineCondition, Function<String, S> transformer) {
        this.classLoader = classLoader;
        this.serviceName = serviceName;
        this.lineCondition = lineCondition;
        this.transformer = transformer;
    }

    private static URI normalizeFilePath(String path, URI uri) {
        Path p = Paths.get(uri);
        if (p.endsWith(path)) {
            Path subpath = Paths.get(path);
            for (int i = 0; i < subpath.getNameCount(); i++) {
                p = p.getParent();
            }
            uri = p.toUri();
        }
        return uri;
    }

    /**
     * Note: referenced by {@code io.micronaut.core.graal.ServiceLoaderInitialization}.
     */
    @SuppressWarnings("java:S3398")
    private static Set<String> computeMicronautServiceTypeNames(URI uri, String path) {
        Set<String> typeNames = new HashSet<>();
        IOUtils.eachFile(
                uri, path, currentPath -> {
                    if (Files.isRegularFile(currentPath)) {
                        final String typeName = currentPath.getFileName().toString();
                        typeNames.add(typeName);
                    }
                }
        );
        return typeNames;
    }

    @SuppressWarnings("java:S3398")
    private Set<String> computeStandardServiceTypeNames(URL url) {
        Set<String> typeNames = new HashSet<>();
        try {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(url.openStream()))) {
                while (true) {
                    String line = reader.readLine();
                    if (line == null) {
                        break;
                    }
                    if (line.length() == 0 || line.charAt(0) == '#') {
                        continue;
                    }
                    if (!lineCondition.test(line)) {
                        continue;
                    }
                    int i = line.indexOf('#');
                    if (i > -1) {
                        line = line.substring(0, i);
                    }
                    typeNames.add(line);
                }
            }
        } catch (IOException | UncheckedIOException e) {
            // ignore, can't do anything here and can't log because class used in compiler
        }
        return typeNames;
    }

    private boolean isWebSphereClassLoader() {
        return classLoader.getClass().getName().startsWith("com.ibm.ws.classloader");
    }

    private String buildResourceSearchPath() {
        String path = "META-INF/micronaut/" + serviceName;

        if (isWebSphereClassLoader()) {
            // Special case WebSphere classloader
            // https://github.com/micronaut-projects/micronaut-core/issues/....
            return path + "/";
        }

        return path;
    }

    private Enumeration<URL> findStandardServiceConfigs() throws IOException {
        return classLoader.getResources(SoftServiceLoader.META_INF_SERVICES + '/' + serviceName);
    }

    private void findMicronautMetaServiceConfigs(BiConsumer<URI, String> consumer) throws IOException, URISyntaxException {
        String path = buildResourceSearchPath();
        final Enumeration<URL> micronautResources = classLoader.getResources(path);
        Set<URI> uniqueURIs = new LinkedHashSet<>();
        while (micronautResources.hasMoreElements()) {
            URL url = micronautResources.nextElement();
            final URI uri = url.toURI();
            uniqueURIs.add(uri);
        }

        for (URI uri : uniqueURIs) {
            String scheme = uri.getScheme();
            if ("file".equals(scheme)) {
                uri = normalizeFilePath(path, uri);
            }
            // on GraalVM there are spurious extra resources that end with # and then a number
            // we ignore this extra ones
            if (!("resource".equals(scheme) && uri.toString().contains("#"))) {
                consumer.accept(uri, path);
            }
        }
    }

    /**
     * Fork-join recursive services loader.
     */
    @SuppressWarnings("java:S1948")
    final class DefaultServiceCollector extends RecursiveActionValuesCollector<S> implements SoftServiceLoader.ServiceCollector<S> {

        private final List<RecursiveActionValuesCollector<S>> tasks = new ArrayList<>();

        @Override
        protected void compute() {
            try {
                Enumeration<URL> serviceConfigs = findStandardServiceConfigs();
                while (serviceConfigs.hasMoreElements()) {
                    URL url = serviceConfigs.nextElement();
                    UrlServicesLoader task = new UrlServicesLoader(url);
                    tasks.add(task);
                    task.fork();
                }
                findMicronautMetaServiceConfigs((uri, path) -> {
                    final MicronautMetaServicesLoader task = new MicronautMetaServicesLoader(uri, path);
                    tasks.add(task);
                    task.fork();
                });
            } catch (IOException | URISyntaxException e) {
                throw new ServiceConfigurationError("Failed to load resources for service: " + serviceName, e);
            }
        }

        @Override
        public void collect(Collection<S> values) {
            ForkJoinPool.commonPool().invoke(this);
            for (RecursiveActionValuesCollector<S> task : tasks) {
                task.join();
                task.collect(values);
            }
        }

        @Override
        public void collect(Collection<S> values, boolean allowFork) {
            if (allowFork) {
                ForkJoinPool.commonPool().invoke(this);
                for (RecursiveActionValuesCollector<S> task : tasks) {
                    task.join();
                    task.collect(values);
                }
            } else {
                try {
                    Enumeration<URL> serviceConfigs = findStandardServiceConfigs();
                    while (serviceConfigs.hasMoreElements()) {
                        URL url = serviceConfigs.nextElement();
                        for (String typeName : computeStandardServiceTypeNames(url)) {
                            values.add(transformer.apply(typeName));
                        }
                    }
                    findMicronautMetaServiceConfigs((uri, path) -> {
                        for (String typeName : computeMicronautServiceTypeNames(uri, path)) {
                            values.add(transformer.apply(typeName));
                        }
                    });
                } catch (IOException | URISyntaxException e) {
                    throw new ServiceConfigurationError("Failed to load resources for service: " + serviceName, e);
                }
            }
        }
    }

    private final class MicronautMetaServicesLoader extends RecursiveActionValuesCollector<S> {
        private final URI uri;
        private final List<ServiceInstanceLoader> tasks = new ArrayList<>();
        private final String path;

        private MicronautMetaServicesLoader(URI uri, String path) {
            this.uri = uri;
            this.path = path;
        }

        @Override
        public void collect(Collection<S> values) {
            for (ServiceInstanceLoader task : tasks) {
                task.join();
                task.collect(values);
            }
        }

        @Override
        @SuppressWarnings("java:S2095")
        protected void compute() {
            Set<String> typeNames = computeMicronautServiceTypeNames(uri, path);
            for (String typeName : typeNames) {
                ServiceInstanceLoader task = new ServiceInstanceLoader(typeName);
                tasks.add(task);
                task.fork();
            }
        }
    }

    /**
     * Reads URL, parses the file and produces sub tasks to initialize the entry.
     */
    @SuppressWarnings("java:S1948")
    private final class UrlServicesLoader extends RecursiveActionValuesCollector<S> {

        private final URL url;
        private final List<ServiceInstanceLoader> tasks = new ArrayList<>();

        public UrlServicesLoader(URL url) {
            this.url = url;
        }

        @Override
        @SuppressWarnings({"java:S3776", "java:S135"})
        protected void compute() {
            for (String typeName : computeStandardServiceTypeNames(url)) {
                ServiceInstanceLoader task = new ServiceInstanceLoader(typeName);
                tasks.add(task);
                task.fork();
            }
        }

        public void collect(Collection<S> values) {
            for (ServiceInstanceLoader task : tasks) {
                task.join();
                task.collect(values);
            }
        }

    }

    /**
     * Initializes and filters the entry.
     */
    @SuppressWarnings("java:S1948")
    private final class ServiceInstanceLoader extends RecursiveActionValuesCollector<S> {

        private final String className;
        private S result;
        private Throwable throwable;

        public ServiceInstanceLoader(String className) {
            this.className = className;
        }

        @Override
        protected void compute() {
            try {
                result = transformer.apply(className);
            } catch (Throwable e) {
                throwable = e;
            }
        }

        public void collect(Collection<S> values) {
            if (throwable != null) {
                throw new SoftServiceLoader.ServiceLoadingException("Failed to load a service: " + throwable.getMessage(), throwable);
            }
            if (result != null && !values.contains(result)) {
                values.add(result);
            }
        }
    }

    /**
     * Abstract recursive action class.
     *
     * @param <S> The type
     */
    private abstract static class RecursiveActionValuesCollector<S> extends RecursiveAction {

        /**
         * Collects loaded values.
         *
         * @param values The values
         */
        public abstract void collect(Collection<S> values);

    }
}
