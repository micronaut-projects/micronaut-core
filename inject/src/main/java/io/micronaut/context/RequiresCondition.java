/*
 * Copyright 2017-2019 original authors
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
import io.micronaut.core.annotation.AnnotationClassValue;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationMetadataProvider;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.io.ResourceLoader;
import io.micronaut.core.io.ResourceResolver;
import io.micronaut.core.io.file.FileSystemResourceLoader;
import io.micronaut.core.io.scan.ClassPathResourceLoader;
import io.micronaut.core.reflect.ClassLoadingReporter;
import io.micronaut.core.reflect.ClassUtils;
import io.micronaut.core.reflect.InstantiationUtils;
import io.micronaut.core.reflect.ReflectionUtils;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.core.value.PropertyResolver;
import io.micronaut.core.version.SemanticVersion;
import io.micronaut.core.version.VersionUtils;
import io.micronaut.inject.BeanConfiguration;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.BeanDefinitionReference;
import kotlin.KotlinVersion;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.*;

/**
 * An abstract {@link Condition} implementation that is based on the presence
 * of {@link Requires} annotation.
 */
public class RequiresCondition implements Condition {

    public static final String MEMBER_PROPERTY = "property";
    public static final String MEMBER_NOT_EQUALS = "notEquals";
    public static final String MEMBER_DEFAULT_VALUE = "defaultValue";
    public static final String MEMBER_PATTERN = "pattern";
    public static final String MEMBER_MISSING_PROPERTY = "missingProperty";
    public static final String MEMBER_ENV = "env";
    public static final String MEMBER_NOT_ENV = "notEnv";
    public static final String MEMBER_CONDITION = "condition";
    public static final String MEMBER_SDK = "sdk";
    public static final String MEMBER_VERSION = "version";
    public static final String MEMBER_MISSING_CLASSES = "missing";
    public static final String MEMBER_RESOURCES = "resources";
    public static final String MEMBER_CONFIGURATION = "configuration";
    public static final String MEMBER_CLASSES = "classes";
    public static final String MEMBER_ENTITIES = "entities";
    public static final String MEMBER_BEANS = "beans";
    public static final String MEMBER_MISSING_BEANS = "missingBeans";

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

        List<AnnotationValue<Requires>> requirements = annotationMetadata.getAnnotationValuesByType(Requires.class);

        if (!requirements.isEmpty()) {
            // here we use AnnotationMetadata to avoid loading the classes referenced in the annotations directly
            if (isBeanReference) {
                for (AnnotationValue<Requires> requirement : requirements) {
                    processPreStartRequirements(context, requirement);
                    if (context.isFailing()) {
                        return false;
                    }
                }
            } else {
                for (AnnotationValue<Requires> requires : requirements) {
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
    protected boolean matchesConfiguration(ConditionContext context, AnnotationValue<Requires> requirements) {

        String configurationName = requirements.stringValue(MEMBER_CONFIGURATION).orElse(null);
        if (StringUtils.isEmpty(configurationName)) {
            return true;
        }

        BeanContext beanContext = context.getBeanContext();
        String minimumVersion = requirements.stringValue(MEMBER_VERSION).orElse(null);
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
     * This method will process requirements for a {@link BeanDefinitionReference} that has not yet been loaded. Unlike {@link #processPostStartRequirements(ConditionContext, AnnotationValue)}
     * this method is executed prior to the bean being loaded and processes requirements that do not require all the beans to be loaded.
     */
    private void processPreStartRequirements(ConditionContext context, AnnotationValue<Requires> requirements) {
        if (!matchesPresenceOfClasses(context, requirements)) {
            return;
        }

        if (!matchesAbsenceOfClasses(context, requirements)) {
            return;
        }

        if (!matchesEnvironment(context, requirements)) {
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

        if (!matchesConfiguration(context, requirements)) {
            return;
        }

        if (!matchesSdk(context, requirements)) {
            return;
        }

        if (!matchesPresenceOfResources(context, requirements)) {
            return;
        }

        // need this check because when this method is called with a BeanDefinitionReference the context
        // is not yet initialized so we cannot know if beans are present or not
        // check only that the classes are present for the beans requirement
        matchesPresenceOfClasses(context, requirements, MEMBER_BEANS);
    }

    /**
     * This method will run conditions that require all beans to be loaded. These conditions included "beans", "missingBeans" and custom conditions.
     */
    private void processPostStartRequirements(ConditionContext context, AnnotationValue<Requires> requirements) {
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

    private boolean matchesProperty(ConditionContext context, AnnotationValue<Requires> requirements) {
        String property = requirements.stringValue(MEMBER_PROPERTY).orElse(null);
        if (StringUtils.isNotEmpty(property)) {
            String value = requirements.stringValue().orElse(null);
            BeanContext beanContext = context.getBeanContext();
            if (beanContext instanceof PropertyResolver) {
                PropertyResolver propertyResolver = (PropertyResolver) beanContext;
                String notEquals = requirements.stringValue(MEMBER_NOT_EQUALS).orElse(null);
                String defaultValue = requirements.stringValue(MEMBER_DEFAULT_VALUE).orElse(null);
                String pattern = requirements.stringValue(MEMBER_PATTERN).orElse(null);

                boolean hasNotEquals = StringUtils.isNotEmpty(notEquals);
                boolean hasPattern = StringUtils.isNotEmpty(pattern);
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
                } else if (hasPattern) {
                    String resolved = resolvePropertyValue(property, propertyResolver, defaultValue);
                    boolean result = resolved != null && resolved.matches(pattern);
                    if (!result) {
                        context.fail("Property [" + property + "] with value [" + resolved + "] does not match required pattern: " + pattern);
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

    private boolean matchesMissingProperty(ConditionContext context, AnnotationValue<Requires> requirements) {
        String property = requirements.stringValue(MEMBER_MISSING_PROPERTY).orElse(null);
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

    private boolean matchesEnvironment(ConditionContext context, AnnotationValue<Requires> requirements) {
        String[] env = requirements.stringValues(MEMBER_ENV);
        if (ArrayUtils.isEmpty(env)) {
            env = requirements.stringValues(MEMBER_NOT_ENV);
            if (ArrayUtils.isNotEmpty(env)) {
                BeanContext beanContext = context.getBeanContext();
                if (beanContext instanceof ApplicationContext) {
                    ApplicationContext applicationContext = (ApplicationContext) beanContext;
                    Environment environment = applicationContext.getEnvironment();
                    Set<String> activeNames = environment.getActiveNames();
                    boolean result = Arrays.stream(env).noneMatch(activeNames::contains);
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
    private boolean matchesCustomConditions(ConditionContext context, AnnotationValue<Requires> requirements) {
        Class<? extends Condition> conditionClass = (Class<? extends Condition>) requirements.classValue(MEMBER_CONDITION).orElse(null);
        if (conditionClass == TrueCondition.class) {
            return true;
        } else if (conditionClass != null) {
            // try first via instantiated metadata
            Optional<? extends Condition> condition = requirements.get(MEMBER_CONDITION, conditionClass);
            if (!condition.isPresent()) {
                condition = InstantiationUtils.tryInstantiate(conditionClass);
            }
            if (condition.isPresent()) {
                boolean conditionResult = condition.get().matches(context);
                if (!conditionResult) {
                    context.fail("Custom condition [" + conditionClass + "] failed evaluation");
                }
                return conditionResult;
            } else {
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

    private boolean matchesSdk(ConditionContext context, AnnotationValue<Requires> requirements) {
        Requires.Sdk sdk = requirements.enumValue(MEMBER_SDK, Requires.Sdk.class).orElse(null);
        String version = requirements.stringValue(MEMBER_VERSION).orElse(null);
        if (sdk != null && StringUtils.isNotEmpty(version)) {

            switch (sdk) {
                case GROOVY:
                    String groovyVersion = GroovySystem.getVersion();
                    boolean versionMatch = SemanticVersion.isAtLeast(groovyVersion, version);
                    if (!versionMatch) {
                        context.fail("Groovy version [" + groovyVersion + "] must be at least " + version);
                    }
                    return versionMatch;
                case KOTLIN:
                    String kotlinVersion = KotlinVersion.CURRENT.toString();
                    boolean isSupported = SemanticVersion.isAtLeast(kotlinVersion, version);
                    if (!isSupported) {
                        context.fail("Kotlin version [" + kotlinVersion + "] must be at least " + version);
                    }
                    return isSupported;
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
                    boolean versionCheck = VersionUtils.isAtLeastMicronautVersion(version);
                    if (!versionCheck) {
                        context.fail("Micronaut version [" + VersionUtils.MICRONAUT_VERSION + "] must be at least " + version);
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

    private boolean matchesPresenceOfClasses(ConditionContext context, AnnotationValue<Requires> convertibleValues) {
        return matchesPresenceOfClasses(context, convertibleValues, MEMBER_CLASSES);
    }

    private boolean matchesAbsenceOfClasses(ConditionContext context, AnnotationValue<Requires> requirements) {
        if (requirements.contains(MEMBER_MISSING_CLASSES)) {
            Optional<AnnotationClassValue[]> classNames = requirements.get(MEMBER_MISSING_CLASSES, AnnotationClassValue[].class);
            if (classNames.isPresent()) {
                AnnotationClassValue[] classValues = classNames.get();
                if (ArrayUtils.isNotEmpty(classValues)) {
                    for (AnnotationClassValue classValue : classValues) {
                        if (classValue.getType().isPresent()) {
                            context.fail("Class [" + classValue.getName() + "] is not absent");
                            return false;
                        }
                    }
                } else {
                    return matchAbsenceOfClassNames(context, requirements);
                }
            } else {
                return matchAbsenceOfClassNames(context, requirements);
            }
        }
        return true;
    }

    private boolean matchAbsenceOfClassNames(ConditionContext context, AnnotationValue<Requires> requirements) {
        final String[] classNameArray = requirements.stringValues(MEMBER_MISSING_CLASSES);
        final ClassLoader classLoader = context.getBeanContext().getClassLoader();
        for (String name : classNameArray) {
            if (ClassUtils.isPresent(name, classLoader)) {
                context.fail("Class [" + name + "] is not absent");
                return false;
            }
        }
        return true;
    }

    private boolean matchesPresenceOfClasses(ConditionContext context, AnnotationValue<Requires> requirements, String attr) {
        if (requirements.contains(attr)) {
            Optional<AnnotationClassValue[]> classNames = requirements.get(attr, AnnotationClassValue[].class);
            if (classNames.isPresent()) {
                AnnotationClassValue[] classValues = classNames.get();
                for (AnnotationClassValue classValue : classValues) {
                    if (!classValue.getType().isPresent()) {
                        context.fail("Class [" + classValue.getName() + "] is not present");
                        if (ClassLoadingReporter.isReportingEnabled()) {
                            for (AnnotationClassValue cv : classValues) {
                                ClassLoadingReporter.reportMissing(cv.getName());
                            }
                            reportMissingClass(context);
                        }
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private void reportMissingClass(ConditionContext context) {
        AnnotationMetadataProvider component = context.getComponent();
        if (component instanceof BeanDefinitionReference) {
            BeanDefinitionReference ref = (BeanDefinitionReference) component;
            ClassLoadingReporter.reportMissing(ref.getClass().getName());
            ClassLoadingReporter.reportMissing(ref.getBeanDefinitionName());
            ClassLoadingReporter.reportMissing(ref.getName());
        }
    }

    private boolean matchesPresenceOfEntities(ConditionContext context, AnnotationValue<Requires> annotationValue) {
        if (annotationValue.contains("entities")) {
            Optional<AnnotationClassValue[]> classNames = annotationValue.get(MEMBER_ENTITIES, AnnotationClassValue[].class);
            if (classNames.isPresent()) {
                BeanContext beanContext = context.getBeanContext();
                if (beanContext instanceof ApplicationContext) {
                    ApplicationContext applicationContext = (ApplicationContext) beanContext;
                    final AnnotationClassValue[] classValues = classNames.get();
                    for (AnnotationClassValue<?> classValue : classValues) {
                        final Optional<? extends Class<?>> entityType = classValue.getType();
                        if (!entityType.isPresent()) {
                            context.fail("Annotation type [" + classValue.getName() + "] not present on classpath");
                            return false;
                        } else {
                            Environment environment = applicationContext.getEnvironment();
                            Class annotationType = entityType.get();
                            if (!environment.scan(annotationType).findFirst().isPresent()) {
                                context.fail("No entities found in packages [" + String.join(", ", environment.getPackages()) + "] for annotation: " + annotationType);
                                return false;
                            }
                        }
                    }
                }
            }
        }
        return true;
    }

    private boolean matchesPresenceOfBeans(ConditionContext context, AnnotationValue<Requires> requirements) {
        Class[] beans = requirements.classValues(MEMBER_BEANS);
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

    private boolean matchesAbsenceOfBeans(ConditionContext context, AnnotationValue<Requires> requirements) {
        Class[] missingBeans = requirements.classValues(MEMBER_MISSING_BEANS);
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

    private boolean matchesPresenceOfResources(ConditionContext context, AnnotationValue<Requires> requirements) {
        final String[] resourcePaths = requirements.stringValues(MEMBER_RESOURCES);
        if (ArrayUtils.isNotEmpty(resourcePaths)) {
            final BeanContext beanContext = context.getBeanContext();
            ResourceResolver resolver;
            final List<ResourceLoader> resourceLoaders;
            if (beanContext instanceof ApplicationContext) {
                ResourceLoader resourceLoader = ((ApplicationContext) beanContext).getEnvironment();
                resourceLoaders = Arrays.asList(resourceLoader, FileSystemResourceLoader.defaultLoader());
            } else {
                resourceLoaders = Arrays.asList(
                        ClassPathResourceLoader.defaultLoader(beanContext.getClassLoader()),
                        FileSystemResourceLoader.defaultLoader()
                );
            }
            resolver = new ResourceResolver(resourceLoaders);
            for (String resourcePath : resourcePaths) {
                if (!resolver.getResource(resourcePath).isPresent()) {
                    context.fail("Resource [" + resourcePath + "] does not exist");
                    return false;
                }
            }
        }
        return true;
    }
}
