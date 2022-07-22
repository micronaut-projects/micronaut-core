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
package io.micronaut.core.graal;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.io.IOUtils;
import io.micronaut.core.io.service.SoftServiceLoader;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.RuntimeClassInitialization;
import org.graalvm.nativeimage.hosted.RuntimeReflection;

/**
 * Integrates {@link io.micronaut.core.io.service.SoftServiceLoader} with GraalVM Native Image.
 *
 * @author graemerocher
 * @since 3.5.0
 */
@SuppressWarnings("unused")
@AutomaticFeature
final class ServiceLoaderFeature implements Feature {

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        configureForReflection(access);

        StaticServiceDefinitions staticServiceDefinitions = buildStaticServiceDefinitions(access);
        final Collection<Set<String>> allTypeNames = staticServiceDefinitions.serviceTypeMap.values();
        for (Set<String> typeNameSet : allTypeNames) {
            for (String typeName : typeNameSet) {
                try {
                    final Class<?> c = access.findClassByName(typeName);
                    if (c != null) {
                        RuntimeReflection.registerForReflectiveInstantiation(c);
                        RuntimeReflection.register(c);
                    }
                } catch (NoClassDefFoundError e) {
                    // missing dependencies ignore and let it fail at runtime
                }
            }
        }
        ImageSingletons.add(StaticServiceDefinitions.class, staticServiceDefinitions);
    }

    @NonNull
    private StaticServiceDefinitions buildStaticServiceDefinitions(BeforeAnalysisAccess access) {
        StaticServiceDefinitions staticServiceDefinitions = new StaticServiceDefinitions();
        final String path = "META-INF/micronaut/";
        try {
            final Enumeration<URL> micronautResources = access.getApplicationClassLoader().getResources(path);
            while (micronautResources.hasMoreElements()) {
                Set<String> servicePaths = new HashSet<>();
                URL url = micronautResources.nextElement();
                URI uri = url.toURI();
                boolean isFileScheme = "file".equals(uri.getScheme());
                if (isFileScheme) {
                    Path p = Paths.get(uri);
                    // strip the META-INF/micronaut part
                    uri = p.getParent().getParent().toUri();
                }
                IOUtils.eachFile(
                        uri,
                        path,
                        servicePath -> {
                            if (Files.isDirectory(servicePath)) {
                                String serviceName = servicePath.toString();
                                if (isFileScheme) {
                                    int i = serviceName.indexOf(path);
                                    if (i > -1) {
                                        serviceName = serviceName.substring(i);
                                    }
                                } else if (serviceName.startsWith("/")) {
                                    serviceName = serviceName.substring(1);
                                }
                                if (serviceName.startsWith(path)) {
                                    servicePaths.add(serviceName);
                                }
                            }
                        }
                );

                for (String servicePath : servicePaths) {
                    IOUtils.eachFile(
                            uri,
                            servicePath,
                            serviceTypePath -> {
                                if (Files.isRegularFile(serviceTypePath)) {
                                    final Set<String> serviceTypeNames = staticServiceDefinitions.serviceTypeMap
                                            .computeIfAbsent(servicePath,
                                                             key -> new HashSet<>());
                                    final String serviceTypeName = serviceTypePath.getFileName().toString();
                                    serviceTypeNames.add(serviceTypeName);
                                }
                            }
                    );
                }
            }

        } catch (IOException | URISyntaxException e) {
            // ignore
        }
        return staticServiceDefinitions;
    }

    private void configureForReflection(BeforeAnalysisAccess access) {
        Collection<GraalReflectionConfigurer> configurers = new ArrayList<>();
        SoftServiceLoader.load(GraalReflectionConfigurer.class, access.getApplicationClassLoader())
                .collectAll(configurers);

        final GraalReflectionConfigurer.ReflectionConfigurationContext context = new GraalReflectionConfigurer.ReflectionConfigurationContext() {
            @Override
            public Class<?> findClassByName(@NonNull String name) {
                return access.findClassByName(name);
            }

            @Override
            public void register(Class<?>... types) {
                RuntimeReflection.register(types);
            }

            @Override
            public void register(Method... methods) {
                RuntimeReflection.register(methods);
            }

            @Override
            public void register(Field... fields) {
                RuntimeReflection.register(fields);
            }

            @Override
            public void register(Constructor<?>... constructors) {
                RuntimeReflection.register(constructors);
            }
        };
        for (GraalReflectionConfigurer configurer : configurers) {
            RuntimeClassInitialization.initializeAtBuildTime(configurer.getClass());
            configurer.configure(context);
        }
    }
}

@Internal
final class StaticServiceDefinitions {
    final Map<String, Set<String>> serviceTypeMap = new HashMap<>();
}

@SuppressWarnings("unused")
@TargetClass(className = "io.micronaut.core.io.service.ServiceScanner")
@Internal
final class ServiceLoaderInitialization {
    private ServiceLoaderInitialization() {
    }

    @Substitute
    private static Set<String> computeMicronautServiceTypeNames(URI uri, String path) {
        final StaticServiceDefinitions ssd = ImageSingletons.lookup(StaticServiceDefinitions.class);
        return ssd.serviceTypeMap.getOrDefault(
                path,
                Collections.emptySet()
        );
    }
}
