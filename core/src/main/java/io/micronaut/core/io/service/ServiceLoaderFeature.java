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

import io.micronaut.core.annotation.AnnotationClassValue;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.BuildTimeInit;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.beans.BeanInfo;
import io.micronaut.core.graal.GraalReflectionConfigurer;
import io.micronaut.core.io.service.ServiceScanner.StaticServiceDefinitions;
import io.micronaut.core.reflect.exception.InstantiationException;
import io.micronaut.core.util.ArrayUtils;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.RuntimeClassInitialization;
import org.graalvm.nativeimage.hosted.RuntimeReflection;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import static io.micronaut.core.util.StringUtils.EMPTY_STRING_ARRAY;

/**
 * Integrates {@link io.micronaut.core.io.service.SoftServiceLoader} with GraalVM Native Image.
 *
 * @author graemerocher
 * @since 3.5.0
 */
@SuppressWarnings("unused")
class ServiceLoaderFeature implements Feature {

    @Override
    @SuppressWarnings("java:S1119")
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        configureForReflection(access);

        StaticServiceDefinitions staticServiceDefinitions = buildStaticServiceDefinitions(access);
        final Collection<Set<String>> allTypeNames = staticServiceDefinitions.serviceTypeMap().values();
        for (Set<String> typeNameSet : allTypeNames) {
            Iterator<String> i = typeNameSet.iterator();
            serviceLoop: while (i.hasNext()) {
                String typeName = i.next();
                try {
                    final Class<?> c = access.findClassByName(typeName);
                    if (c != null) {
                        if (GraalReflectionConfigurer.class.isAssignableFrom(c)) {
                            continue;
                        } else if (BeanInfo.class.isAssignableFrom(c)) {
                            BuildTimeInit buildInit = c.getAnnotation(BuildTimeInit.class);
                            if (buildInit != null) {
                                String[] classNames = buildInit.value();
                                for (String className : classNames) {
                                    Class<?> buildInitClass = access.findClassByName(className);
                                    initializeAtBuildTime(buildInitClass);
                                }
                            }
                            initializeAtBuildTime(c);
                            BeanInfo<?> beanInfo;
                            try {
                                beanInfo = (BeanInfo<?>) c.getDeclaredConstructor().newInstance();
                            } catch (Exception e) {
                                // not loadable at runtime either, remove it
                                i.remove();
                                continue;
                            }
                            Class<?> beanType = beanInfo.getBeanType();
                            List<AnnotationValue<Annotation>> values = beanInfo.getAnnotationMetadata().getAnnotationValuesByName("io.micronaut.context.annotation.Requires");
                            if (values.isEmpty()) {
                                AnnotationValue<Annotation> requirements = beanInfo.getAnnotationMetadata().getAnnotation("io.micronaut.context.annotation.Requirements");
                                if (requirements != null) {
                                    values = requirements.getAnnotations("value");
                                }
                            }
                            if (!values.isEmpty()) {
                                for (AnnotationValue<Annotation> value : values) {
                                    String[] classNames = EMPTY_STRING_ARRAY;
                                    if (value.contains("classes")) {
                                        classNames = value.stringValues("classes");
                                    }
                                    if (value.contains("beans")) {
                                        ArrayUtils.concat(classNames, value.stringValues("beans"));
                                    }
                                    if (value.contains("condition")) {
                                        Object o = value.getValues().get("condition");
                                        if (o instanceof AnnotationClassValue<?> annotationClassValue) {
                                            annotationClassValue.getType().ifPresent(this::initializeAtBuildTime);
                                        }
                                    }
                                    for (String className : classNames) {
                                        if (access.findClassByName(className) == null) {
                                            i.remove();
                                            continue serviceLoop;
                                        }
                                    }
                                }
                            }
                        }

                        registerForReflectiveInstantiation(c);
                        registerRuntimeReflection(c);
                    }
                    final Class<?> exec = access.findClassByName(typeName + "$Exec");
                    if (exec != null) {
                        initializeAtBuildTime(exec);
                    }


                } catch (NoClassDefFoundError | InstantiationException e) {
                    i.remove();
                }
            }
        }
        addImageSingleton(staticServiceDefinitions);
    }

    /**
     * Register a class for reflective instantiation.
     * @param c The class
     */
    protected void registerForReflectiveInstantiation(Class<?> c) {
        RuntimeReflection.registerForReflectiveInstantiation(c);
    }

    /**
     * Add an image singleton.
     * @param staticServiceDefinitions The static definitions.
     */
    protected void addImageSingleton(StaticServiceDefinitions staticServiceDefinitions) {
        ImageSingletons.add(StaticServiceDefinitions.class, staticServiceDefinitions);
    }

    /**
     * Register a class for runtime reflection.
     * @param c The class
     */
    protected void registerRuntimeReflection(Class<?> c) {
        RuntimeReflection.register(c);
    }

    /**
     * Register a methods for runtime reflection.
     * @param methods The methods
     */
    protected void registerRuntimeReflection(Method... methods) {
        RuntimeReflection.register(methods);
    }

    /**
     * Register a field for runtime reflection.
     * @param fields The field
     */
    protected void registerRuntimeReflection(Field... fields) {
        RuntimeReflection.register(fields);
    }

    /**
     * Initialize a class at build time.
     * @param buildInitClass The class
     */
    protected void initializeAtBuildTime(@Nullable Class<?> buildInitClass) {
        if (buildInitClass != null) {
            RuntimeClassInitialization.initializeAtBuildTime(buildInitClass);
        }
    }

    /**
     * Build the static service definitions.
     * @param access The access
     * @return The definitions
     */
    @NonNull
    protected StaticServiceDefinitions buildStaticServiceDefinitions(BeforeAnalysisAccess access) {
        try {
            return new StaticServiceDefinitions(
                MicronautMetaServiceLoaderUtils.findAllMicronautMetaServices(getClass().getClassLoader())
            );
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void configureForReflection(BeforeAnalysisAccess access) {
        Collection<GraalReflectionConfigurer> configurers = loadReflectionConfigurers(access);

        final GraalReflectionConfigurer.ReflectionConfigurationContext context = new GraalReflectionConfigurer.ReflectionConfigurationContext() {
            @Override
            public Class<?> findClassByName(@NonNull String name) {
                return access.findClassByName(name);
            }

            @Override
            public void register(Class<?>... types) {
                for (Class<?> type : types) {
                    registerRuntimeReflection(type);
                }
            }

            @Override
            public void register(Method... methods) {
                registerRuntimeReflection(methods);
            }

            @Override
            public void register(Field... fields) {
                registerRuntimeReflection(fields);
            }

            @Override
            public void register(Constructor<?>... constructors) {
                RuntimeReflection.register(constructors);
            }
        };
        for (GraalReflectionConfigurer configurer : configurers) {
            initializeAtBuildTime(configurer.getClass());
            configurer.configure(context);
        }
    }

    @NonNull
    protected Collection<GraalReflectionConfigurer> loadReflectionConfigurers(BeforeAnalysisAccess access) {
        Collection<GraalReflectionConfigurer> configurers = new ArrayList<>();
        SoftServiceLoader.load(GraalReflectionConfigurer.class, access.getApplicationClassLoader())
                .collectAll(configurers);
        return configurers;
    }
}


