/*
 * Copyright 2017-2018 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.context;

import groovy.lang.GroovySystem;
import io.micronaut.context.annotation.Requirements;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.condition.Condition;
import io.micronaut.context.condition.ConditionContext;
import io.micronaut.context.condition.TrueCondition;
import io.micronaut.context.env.Environment;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationMetadataProvider;
import io.micronaut.core.convert.value.ConvertibleValues;
import io.micronaut.core.reflect.ClassUtils;
import io.micronaut.core.reflect.InstantiationUtils;
import io.micronaut.core.reflect.ReflectionUtils;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.core.value.PropertyResolver;
import io.micronaut.core.version.SemanticVersion;
import io.micronaut.inject.BeanConfiguration;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.BeanDefinitionReference;
import io.micronaut.inject.annotation.AnnotationValue;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * An abstract {@link Condition} implementation that is based on the presence
 * of {@link Requires} annotation
 */
public class RequiresCondition implements Condition {

    private final AnnotationMetadata annotationMetadata;

    public RequiresCondition(AnnotationMetadata annotationMetadata) {
        this.annotationMetadata = annotationMetadata;
    }

    @Override
    public boolean matches(ConditionContext context) {
        AnnotationMetadataProvider component = context.getComponent();
        boolean isBeanReference = component instanceof BeanDefinitionReference;
        boolean isBeanConfiguration = component instanceof BeanConfiguration;
        if (annotationMetadata.hasStereotype(Requirements.class)) {

            Optional<AnnotationValue[]> requirements = annotationMetadata.getValue(Requirements.class, AnnotationValue[].class);

            if (requirements.isPresent()) {
                AnnotationValue[] annotationValues = requirements.get();

                // here we use AnnotationMetadata to avoid loading the classes referenced in the annotations directly
                if (isBeanReference || isBeanConfiguration) {

                    for (AnnotationValue av : annotationValues) {
                        ConvertibleValues<Object> convertibleValues = av.getConvertibleValues();
                        if (processClassRequirements(context, convertibleValues)) {
                            return false;
                        }
                    }

                    return !isBeanConfiguration || !processRequirements(context);
                } else {
                    return !processRequirements(context);
                }
            }
        } else if (annotationMetadata.hasStereotype(Requires.class)) {
            ConvertibleValues<Object> values = annotationMetadata.getValues(Requires.class);
            if (isBeanReference || isBeanConfiguration) {

                if (processClassRequirements(context, values)) {
                    return false;
                }

                if (isBeanConfiguration) {
                    Requires ann = annotationMetadata.getAnnotation(Requires.class);
                    if (ann != null) {
                        return !processRequires(context, ann);
                    }
                }
            } else {
                Requires ann = annotationMetadata.getAnnotation(Requires.class);
                if (ann != null) {
                    return !processRequires(context, ann);
                }
            }

        }
        return true;
    }

    protected boolean matchesConfiguration(ConditionContext context, Requires requires) {

        String configurationName = requires.configuration();
        if (configurationName.length() == 0) {
            return true;
        }

        BeanContext beanContext = context.getBeanContext();
        String minimumVersion = requires.version();
        Optional<BeanConfiguration> beanConfiguration = beanContext.findBeanConfiguration(configurationName);
        if (!beanConfiguration.isPresent()) {
            return false;
        } else {
            String version = beanConfiguration.get().getVersion();
            if (version != null && minimumVersion.length() > 0) {
                return SemanticVersion.isAtLeast(version, minimumVersion);
            } else {
                return true;
            }
        }
    }

    private boolean processRequirements(ConditionContext context) {
        // Now it is safe to initialize the annotation requirements
        Requirements ann = annotationMetadata.getAnnotation(Requirements.class);

        if (ann != null) {
            for (Requires requires : ann.value()) {
                if (processRequires(context, requires)) {
                    return true;
                }
            }
        }
        return false;
    }

    /*
     * This method will process requirements for a {@link BeanDefinitionReference} that has not yet been loaded
     */
    private boolean processClassRequirements(ConditionContext context, ConvertibleValues<Object> convertibleValues) {
        if (!matchesPresenceOfClasses(context, convertibleValues)) {
            return true;
        }
        if (!matchesPresenceOfEntities(context, convertibleValues)) {
            return true;
        }
        // need this check because when this method is called with a BeanDefinitionReference the context
        // is not yet initialized so we cannot know if beans are present or not
        // check only that the classes are present for the beans requirement
        return !matchesPresenceOfClasses(context, convertibleValues, "beans");
    }

    private boolean processRequires(ConditionContext context, Requires annotation) {
        return
            !matchesProperty(context, annotation) ||
                !matchesMissingProperty(context, annotation) ||
                !matchesEnvironment(context, annotation) ||
                !matchesPresenceOfBeans(context, annotation) ||
                !matchesAbsenceOfBeans(context, annotation) ||
                !matchesConfiguration(context, annotation) ||
                !matchesSdk(annotation) ||
                !matchesConditions(context, annotation);
    }

    private boolean matchesProperty(ConditionContext context, Requires annotation) {
        String property = annotation.property();
        if (property.length() > 0) {
            String value = annotation.value();
            BeanContext beanContext = context.getBeanContext();
            if (beanContext instanceof PropertyResolver) {
                PropertyResolver propertyResolver = (PropertyResolver) beanContext;
                String notEquals = annotation.notEquals();
                boolean hasNotEquals = StringUtils.isNotEmpty(notEquals);
                if (!propertyResolver.containsProperties(property) && StringUtils.isEmpty(annotation.defaultValue())) {
                    return hasNotEquals ? true : false;
                } else if (StringUtils.isNotEmpty(value)) {
                    Optional<String> resolved = resolvePropertyValue(annotation, property, propertyResolver);
                    return resolved.map(val -> val.equals(value)).orElse(false);
                } else if (hasNotEquals) {
                    Optional<String> resolved = resolvePropertyValue(annotation, property, propertyResolver);
                    String resolvedValue = resolved.orElse(null);
                    return resolvedValue == null || resolvedValue.equals(value);
                }
            } else {
                return false;
            }
        }
        return true;
    }

    private Optional<String> resolvePropertyValue(Requires annotation, String property, PropertyResolver propertyResolver) {
        Optional<String> resolved = propertyResolver.getProperty(property, String.class);
        String defaultValue = annotation.defaultValue();
        if (!resolved.isPresent() && StringUtils.isNotEmpty(defaultValue)) {
            resolved = Optional.of(defaultValue);
        }
        return resolved;
    }

    private boolean matchesMissingProperty(ConditionContext context, Requires annotation) {
        String property = annotation.missingProperty();
        if (property.length() > 0) {
            BeanContext beanContext = context.getBeanContext();
            if (beanContext instanceof PropertyResolver) {
                PropertyResolver propertyResolver = (PropertyResolver) beanContext;
                if (propertyResolver.containsProperties(property)) {
                    return false;
                }
            }
        }
        return true;
    }

    private boolean matchesEnvironment(ConditionContext context, Requires annotation) {
        String[] env = annotation.env();
        if (ArrayUtils.isEmpty(env)) {
            env = annotation.notEnv();
            if (ArrayUtils.isNotEmpty(env)) {
                BeanContext beanContext = context.getBeanContext();
                if (beanContext instanceof ApplicationContext) {
                    ApplicationContext applicationContext = (ApplicationContext) beanContext;
                    Environment environment = applicationContext.getEnvironment();
                    Set<String> activeNames = environment.getActiveNames();
                    return !activeNames.contains(env[0]);
                }

            }
            return true;
        } else {
            BeanContext beanContext = context.getBeanContext();
            if (beanContext instanceof ApplicationContext) {
                ApplicationContext applicationContext = (ApplicationContext) beanContext;
                Environment environment = applicationContext.getEnvironment();
                Set<String> activeNames = environment.getActiveNames();
                return Arrays.stream(env).anyMatch(activeNames::contains);
            }
        }
        return true;
    }

    private boolean matchesConditions(ConditionContext context, Requires annotation) {
        Class<? extends Condition> conditionClass = annotation.condition();
        if (conditionClass == TrueCondition.class) {
            return true;
        } else {
            try {
                return conditionClass.newInstance().matches(context);
            } catch (Throwable e) {
                // maybe a Groovy closure
                Optional<Constructor<?>> constructor = ReflectionUtils.findConstructor((Class) conditionClass, Object.class, Object.class);
                return constructor.flatMap(ctor ->
                    InstantiationUtils.tryInstantiate(ctor, null, null)
                ).flatMap(obj -> {
                    Optional<Method> method = ReflectionUtils.findMethod(obj.getClass(), "call", ConditionContext.class);
                    if (method.isPresent()) {
                        Object result = ReflectionUtils.invokeMethod(obj, method.get(), context);
                        if (result instanceof Boolean) {
                            return Optional.of((Boolean) result);
                        }
                    }
                    return Optional.empty();
                }).orElse(false);

            }
        }
    }

    private boolean matchesSdk(Requires annotation) {
        Requires.Sdk sdk = annotation.sdk();
        String version = annotation.version();
        if (version.length() > 0) {

            switch (sdk) {
                case GROOVY:
                    String groovyVersion = GroovySystem.getVersion();
                    return SemanticVersion.isAtLeast(groovyVersion, version);
                case JAVA:
                    String javaVersion = System.getProperty("java.version");
                    try {
                        return SemanticVersion.isAtLeast(javaVersion, version);
                    } catch (Exception e) {
                        // non-semantic versioning in play
                        char majorVersion = resolveJavaMajorVersion(javaVersion);
                        char requiredVersion = resolveJavaMajorVersion(version);

                        if (majorVersion >= requiredVersion) {
                            return true;
                        }
                    }
                    return false;
                default:
                    return SemanticVersion.isAtLeast(getClass().getPackage().getImplementationVersion(), version);
            }
        }
        return true;
    }

    private char resolveJavaMajorVersion(String javaVersion) {
        char majorVersion = 0;
        if (javaVersion.indexOf('.') > -1) {
            String[] tokens = javaVersion.split("\\.");
            majorVersion = tokens[0].charAt(0);
            if (Character.isDigit(majorVersion)) {
                if (majorVersion == '1' && tokens.length > 1) {
                    majorVersion = tokens[1].charAt(0);
                }
            }
        } else {
            char ch = javaVersion.charAt(0);
            if (Character.isDigit(ch)) {
                majorVersion = ch;
            }
        }
        return majorVersion;
    }

    private boolean matchesPresenceOfClasses(ConditionContext context, ConvertibleValues<Object> convertibleValues) {
        return matchesPresenceOfClasses(context, convertibleValues, "classes");
    }

    private boolean matchesPresenceOfClasses(ConditionContext context, ConvertibleValues<Object> convertibleValues, String attr) {
        if (convertibleValues.contains(attr)) {
            Optional<String[]> classNames = convertibleValues.get(attr, String[].class);
            if (classNames.isPresent()) {
                String[] names = classNames.get();
                ClassLoader classLoader = context.getBeanContext().getClassLoader();
                for (String name : names) {
                    if (!ClassUtils.forName(name, classLoader).isPresent()) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private boolean matchesPresenceOfEntities(ConditionContext context, ConvertibleValues<Object> convertibleValues) {
        if (convertibleValues.contains("entities")) {
            BeanContext beanContext = context.getBeanContext();
            if (beanContext instanceof ApplicationContext) {
                ApplicationContext applicationContext = (ApplicationContext) beanContext;
                Optional<String[]> classNames = convertibleValues.get("entities", String[].class);
                if (classNames.isPresent()) {
                    String[] names = classNames.get();
                    if (ArrayUtils.isNotEmpty(names)) {
                        Optional<Class> type = ClassUtils.forName(names[0], beanContext.getClassLoader());
                        if (!type.isPresent()) {
                            return false;
                        } else {
                            Environment environment = applicationContext.getEnvironment();
                            Class annotationType = type.get();
                            if (!environment.scan(annotationType).findFirst().isPresent()) {
                                return false;
                            }
                        }
                    }
                }
            }

        }
        return true;
    }

    private boolean matchesPresenceOfBeans(ConditionContext context, Requires annotation) {
        Class[] beans = annotation.beans();
        BeanContext beanContext = context.getBeanContext();
        if (ArrayUtils.isNotEmpty(beans)) {
            for (Class type : beans) {
                if (!beanContext.containsBean(type)) {
                    return false;
                }
            }
        }
        return true;
    }

    private boolean matchesAbsenceOfBeans(ConditionContext context, Requires annotation) {
        Class[] missingBeans = annotation.missingBeans();
        AnnotationMetadataProvider component = context.getComponent();
        if (ArrayUtils.isNotEmpty(missingBeans) && component instanceof BeanDefinition) {
            BeanDefinition bd = (BeanDefinition) component;

            DefaultBeanContext beanContext = (DefaultBeanContext) context.getBeanContext();

            for (Class<?> type : missingBeans) {
                Collection<? extends BeanDefinition<?>> beanDefinitions = new ArrayList<>(beanContext.findBeanCandidates(type, bd));
                // remove self
                if (!beanDefinitions.isEmpty()) {
                    List<? extends BeanDefinition<?>> definitions = beanDefinitions.stream().filter(BeanDefinition::isAbstract).collect(Collectors.toList());
                    if (definitions.isEmpty()) {
                        return false;
                    }
                }
            }
        }
        return true;
    }
}
