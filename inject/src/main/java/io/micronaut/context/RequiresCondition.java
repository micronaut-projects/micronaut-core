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

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.*;

/**
 * An abstract {@link Condition} implementation that is based on the presence
 * of {@link Requires} annotation.
 */
public class RequiresCondition implements Condition {

    private final AnnotationMetadata annotationMetadata;

    /**
     * @param annotationMetadata The annotation metadata
     */
    public RequiresCondition(AnnotationMetadata annotationMetadata) {
        this.annotationMetadata = annotationMetadata;
    }

    @Override
    public boolean matches(ConditionContext context) {
        AnnotationMetadataProvider component = context.getComponent();
        boolean isBeanReference = component instanceof BeanDefinitionReference;

        List<ConvertibleValues<Object>> requirements = annotationMetadata.getAnnotationValuesByType(Requires.class);

        if (!requirements.isEmpty()) {
            // here we use AnnotationMetadata to avoid loading the classes referenced in the annotations directly
            if (isBeanReference) {
                for (ConvertibleValues<Object> requirement : requirements) {
                    processPreStartRequirements(context, requirement);
                    if (context.isFailing()) {
                        return false;
                    }
                }
            } else {
                for (ConvertibleValues<Object> requires : requirements) {
                    processPostStartRequirements(context, requires);
                    if (context.isFailing()) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    /**
     * @param context  The condition context
     * @param requirements The requires
     * @return Whether matches the condition
     */
    protected boolean matchesConfiguration(ConditionContext context, ConvertibleValues<Object> requirements) {

        String configurationName = requirements.get("configuration", String.class).orElse(null);
        if (StringUtils.isEmpty(configurationName)) {
            return true;
        }

        BeanContext beanContext = context.getBeanContext();
        String minimumVersion = requirements.get("version", String.class).orElse(null);
        Optional<BeanConfiguration> beanConfiguration = beanContext.findBeanConfiguration(configurationName);
        if (!beanConfiguration.isPresent()) {
            context.fail("Required configuration [" + configurationName + "] is not active");
            return false;
        } else {
            String version = beanConfiguration.get().getVersion();
            if (version != null && StringUtils.isNotEmpty(minimumVersion)) {
                boolean result = SemanticVersion.isAtLeast(version, minimumVersion);
                context.fail("Required configuration [" + configurationName + "] version requirements not met. Required: " + minimumVersion + ", Current: " + version);
                return result;
            } else {
                return true;
            }
        }
    }

    /**
     * This method will process requirements for a {@link BeanDefinitionReference} that has not yet been loaded. Unlike {@link #processPostStartRequirements(ConditionContext, ConvertibleValues)}
     * this method is executed prior to the bean being loaded and processes requirements that do not require all the beans to be loaded.
     */
    private void processPreStartRequirements(ConditionContext context, ConvertibleValues<Object> requirements) {
        if (!matchesPresenceOfClasses(context, requirements)) {
            return;
        }

        if (!matchesPresenceOfEntities(context, requirements)) {
            return;
        }

        if (!matchesProperty(context, requirements)) {
            return;
        }

        if (!matchesMissingProperty(context, requirements)) {
            return;
        }

        if (!matchesEnvironment(context, requirements)) {
            return;
        }

        if (!matchesConfiguration(context, requirements)) {
            return;
        }

        if (!matchesSdk(context, requirements)) {
            return;
        }

        // need this check because when this method is called with a BeanDefinitionReference the context
        // is not yet initialized so we cannot know if beans are present or not
        // check only that the classes are present for the beans requirement
        matchesPresenceOfClasses(context, requirements, "beans");
    }

    /**
     * This method will run conditions that require all beans to be loaded. These conditions included "beans", "missingBeans" and custom conditions.
     */
    private void processPostStartRequirements(ConditionContext context, ConvertibleValues<Object> requirements) {
        processPreStartRequirements(context, requirements);

        if (context.isFailing()) {
            return;
        }

        if (!matchesPresenceOfBeans(context, requirements)) {
            return;
        }

        if (!matchesAbsenceOfBeans(context, requirements)) {
            return;
        }

        matchesCustomConditions(context, requirements);
    }

    private boolean matchesProperty(ConditionContext context, ConvertibleValues<Object> requirements) {
        String property = requirements.get("property", String.class).orElse(null);
        if (StringUtils.isNotEmpty(property)) {
            String value = requirements.get("value", String.class).orElse(null);
            BeanContext beanContext = context.getBeanContext();
            if (beanContext instanceof PropertyResolver) {
                PropertyResolver propertyResolver = (PropertyResolver) beanContext;
                String notEquals = requirements.get("notEquals", String.class).orElse(null);
                String defaultValue = requirements.get("defaultValue", String.class).orElse(null);

                boolean hasNotEquals = StringUtils.isNotEmpty(notEquals);
                if (!propertyResolver.containsProperties(property) && StringUtils.isEmpty(defaultValue)) {
                    if (hasNotEquals) {
                        return true;
                    } else {
                        context.fail("Required property [" + property + "] not present");
                        return false;
                    }
                } else if (StringUtils.isNotEmpty(value)) {
                    String resolved = resolvePropertyValue(property, propertyResolver, defaultValue);
                    boolean result = resolved != null && resolved.equals(value);
                    if (!result) {
                        context.fail("Property [" + property + "] with value [" + resolved + "] does not equal required value: " + value);
                    }
                    return result;
                } else if (hasNotEquals) {
                    String resolved = resolvePropertyValue(property, propertyResolver, defaultValue);
                    boolean result = resolved == null || !resolved.equals(notEquals);
                    if (!result) {
                        context.fail("Property [" + property + "] with value [" + resolved + "] should not equal: " + notEquals);
                    }
                    return result;
                }
            } else {
                context.fail("Bean requires property but BeanContext does not support property resolution");
                return false;
            }
        }
        return true;
    }

    private String resolvePropertyValue(String property, PropertyResolver propertyResolver, String defaultValue) {
        return propertyResolver.getProperty(property, String.class).orElse(defaultValue);
    }

    private boolean matchesMissingProperty(ConditionContext context, ConvertibleValues<Object> requirements) {
        String property = requirements.get("missingProperty", String.class).orElse(null);
        if (StringUtils.isNotEmpty(property)) {
            BeanContext beanContext = context.getBeanContext();
            if (beanContext instanceof PropertyResolver) {
                PropertyResolver propertyResolver = (PropertyResolver) beanContext;
                if (propertyResolver.containsProperties(property)) {
                    context.fail("Property [" + property + "] present");
                    return false;
                }
            }
        }
        return true;
    }

    private boolean matchesEnvironment(ConditionContext context, ConvertibleValues<Object> requirements) {
        String[] env = requirements.get("env", String[].class).orElse(null);
        if (ArrayUtils.isEmpty(env)) {
            env = requirements.get("notEnv", String[].class).orElse(null);
            if (ArrayUtils.isNotEmpty(env)) {
                BeanContext beanContext = context.getBeanContext();
                if (beanContext instanceof ApplicationContext) {
                    ApplicationContext applicationContext = (ApplicationContext) beanContext;
                    Environment environment = applicationContext.getEnvironment();
                    Set<String> activeNames = environment.getActiveNames();
                    boolean result = Arrays.stream(env).anyMatch(s -> !activeNames.contains(s));
                    if (!result) {
                        context.fail("Disallowed environments [" + ArrayUtils.toString(env) + "] are active: " + activeNames);
                    }
                    return result;
                }

            }
            return true;
        } else {
            BeanContext beanContext = context.getBeanContext();
            if (beanContext instanceof ApplicationContext) {
                ApplicationContext applicationContext = (ApplicationContext) beanContext;
                Environment environment = applicationContext.getEnvironment();
                Set<String> activeNames = environment.getActiveNames();
                boolean result = Arrays.stream(env).anyMatch(activeNames::contains);
                if (!result) {
                    context.fail("None of the required environments [" + ArrayUtils.toString(env) + "] are active: " + activeNames);
                }
                return result;
            }
        }
        return true;
    }

    @SuppressWarnings("unchecked")
    private boolean matchesCustomConditions(ConditionContext context, ConvertibleValues<Object> requirements) {
        Class<? extends Condition> conditionClass = requirements.get("condition", Class.class).orElse(null);
        if (conditionClass == TrueCondition.class) {
            return true;
        } else if (conditionClass != null) {
            try {
                Constructor<? extends Condition> constructor = conditionClass.getDeclaredConstructor();
                constructor.setAccessible(true);
                boolean conditionResult = constructor.newInstance().matches(context);
                if (!conditionResult) {
                    context.fail("Custom condition [" + conditionClass + "] failed evaluation");
                }
                return conditionResult;
            } catch (Throwable e) {
                // maybe a Groovy closure
                Optional<Constructor<?>> constructor = ReflectionUtils.findConstructor((Class) conditionClass, Object.class, Object.class);
                boolean conditionResult = constructor.flatMap(ctor ->
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
                if (!conditionResult) {
                    context.fail("Custom condition [" + conditionClass + "] failed evaluation");
                }
                return conditionResult;

            }
        }
        return !context.isFailing();
    }

    private boolean matchesSdk(ConditionContext context, ConvertibleValues<Object> requirements) {
        Requires.Sdk sdk = requirements.get("sdk", Requires.Sdk.class).orElse(null);
        String version = requirements.get("version", String.class).orElse(null);
        if (sdk != null && StringUtils.isNotEmpty(version)) {

            switch (sdk) {
                case GROOVY:
                    String groovyVersion = GroovySystem.getVersion();
                    boolean versionMatch = SemanticVersion.isAtLeast(groovyVersion, version);
                    if (!versionMatch) {
                        context.fail("Groovy version [" + groovyVersion + "] must be at least " + version);
                    }
                    return versionMatch;
                case JAVA:
                    String javaVersion = System.getProperty("java.version");
                    try {
                        boolean result = SemanticVersion.isAtLeast(javaVersion, version);
                        if (!result) {
                            context.fail("Java version [" + javaVersion + "] must be at least " + version);
                        }
                        return result;
                    } catch (Exception e) {
                        // non-semantic versioning in play
                        char majorVersion = resolveJavaMajorVersion(javaVersion);
                        char requiredVersion = resolveJavaMajorVersion(version);

                        if (majorVersion >= requiredVersion) {
                            return true;
                        } else {
                            context.fail("Java major version [" + majorVersion + "] must be at least " + requiredVersion);
                        }
                    }

                    return context.isFailing();
                default:
                    String micronautVersion = getClass().getPackage().getImplementationVersion();
                    boolean versionCheck = SemanticVersion.isAtLeast(micronautVersion, version);
                    if (!versionCheck) {
                        context.fail("Micronaut version [" + micronautVersion + "] must be at least " + version);
                    }
                    return versionCheck;
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
                        context.fail("Class [" + name + "] is not present");
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
                            context.fail("Annotation type [" + names[0] + "] not present on classpath");
                            return false;
                        } else {
                            Environment environment = applicationContext.getEnvironment();
                            Class annotationType = type.get();
                            if (!environment.scan(annotationType).findFirst().isPresent()) {
                                context.fail("No entities found on classpath");
                                return false;
                            }
                        }
                    }
                }
            }

        }
        return true;
    }

    private boolean matchesPresenceOfBeans(ConditionContext context, ConvertibleValues<Object> requirements) {
        Class[] beans = requirements.get("beans", Class[].class).orElse(null);
        if (ArrayUtils.isNotEmpty(beans)) {
            BeanContext beanContext = context.getBeanContext();
            for (Class type : beans) {
                if (!beanContext.containsBean(type)) {
                    context.fail("No bean of type [" + type + "] present within context");
                    return false;
                }
            }
        }
        return true;
    }

    private boolean matchesAbsenceOfBeans(ConditionContext context, ConvertibleValues<Object> requirements) {
        Class[] missingBeans = requirements.get("missingBeans", Class[].class).orElse(null);
        AnnotationMetadataProvider component = context.getComponent();
        if (ArrayUtils.isNotEmpty(missingBeans) && component instanceof BeanDefinition) {
            BeanDefinition bd = (BeanDefinition) component;

            DefaultBeanContext beanContext = (DefaultBeanContext) context.getBeanContext();

            for (Class<?> type : missingBeans) {
                // remove self by passing definition as filter
                Collection<? extends BeanDefinition<?>> beanDefinitions = new ArrayList<>(beanContext.findBeanCandidates(type, bd));

                if (!beanDefinitions.isEmpty()) {
                    // remove abstract beans
                    beanDefinitions.removeIf(BeanDefinition::isAbstract);
                    if (!beanDefinitions.isEmpty()) {
                        BeanDefinition<?> existing = beanDefinitions.iterator().next();
                        context.fail("Existing bean [" + existing.getName() + "] of type [" + type + "] registered in context");
                        return false;
                    }
                }
            }
        }
        return true;
    }
}
