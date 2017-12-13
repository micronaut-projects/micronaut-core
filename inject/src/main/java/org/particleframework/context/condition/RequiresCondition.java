package org.particleframework.context.condition;

import groovy.lang.GroovySystem;
import org.particleframework.context.ApplicationContext;
import org.particleframework.context.BeanContext;
import org.particleframework.context.annotation.Requirements;
import org.particleframework.context.annotation.Requires;
import org.particleframework.context.env.Environment;
import org.particleframework.core.annotation.AnnotationMetadata;
import org.particleframework.core.convert.value.ConvertibleValues;
import org.particleframework.core.reflect.ClassUtils;
import org.particleframework.core.reflect.InstantiationUtils;
import org.particleframework.core.reflect.ReflectionUtils;
import org.particleframework.core.util.StringUtils;
import org.particleframework.core.value.PropertyResolver;
import org.particleframework.core.version.SemanticVersion;
import org.particleframework.inject.BeanConfiguration;
import org.particleframework.inject.BeanDefinition;
import org.particleframework.inject.annotation.AnnotationValue;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

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
        if(annotationMetadata.hasStereotype(Requirements.class)) {

            Optional<AnnotationValue[]> requirements = annotationMetadata.getValue(Requirements.class, AnnotationValue[].class);

            if(requirements.isPresent()) {
                AnnotationValue[] annotationValues = requirements.get();

                // here we use AnnotationMetadata to avoid loading the classes referenced in the annotations directly
                for (AnnotationValue av : annotationValues) {
                    if(!matchesPresenceOfClasses(context, av)) {
                        return false;
                    }
                    // need this check because when this method is called with a BeanDefinitionReference the context
                    // is not yet initialized so we cannot know if beans are present or not
                    if(context.getComponent() instanceof BeanDefinition) {
                        if(!matchesPresenceOfBean(context, av)) {
                            return false;
                        }
                    }
                    else {
                        // check only that the classes are present for the beans requirement
                        if(!matchesPresenceOfClasses(context, av.getConvertibleValues(), "beans")) {
                            return false;
                        }
                    }
                }

                // Now it is safe to initialize the annotation requirements
                Requirements ann = annotationMetadata.getAnnotation(Requirements.class);

                if(ann != null) {
                    for (Requires requires : ann.value()) {
                        if (processRequires(context, requires)) return false;
                    }
                }
            }
        }
        else if(annotationMetadata.hasStereotype(Requires.class)) {
            ConvertibleValues<Object> values = annotationMetadata.getValues(Requires.class);
            if(!matchesPresenceOfClasses(context, values)) {
                return false;
            }
            if(!matchPresentOfBeans(context, values)) {
                return false;
            }

            Requires ann = annotationMetadata.getAnnotation(Requires.class);
            if(ann != null) {
                if (processRequires(context, ann)) return false;
            }
        }
        return true;
    }

    protected boolean processRequires(ConditionContext context, Requires annotation) {
        if(!matchesProperty(context, annotation)) {
            return true;
        }
        if(!matchesMissingProperty(context, annotation)) {
            return true;
        }
        if(!matchesEnvironment(context, annotation)) {
            return true;
        }
        if(!matchesConfiguration(context, annotation)) {
            return true;
        }
        if(!matchesSdk(annotation)) {
            return true;
        }
        if(!matchesConditions(context, annotation)) {
            return true;
        }
        if(!matchesEndpoint(context, annotation)) {
            return true;
        }
        return false;
    }

    private boolean matchesProperty(ConditionContext context, Requires annotation) {
        String property = annotation.property();
        if(property.length() > 0) {
            String value = annotation.value();
            BeanContext beanContext = context.getBeanContext();
            if(beanContext instanceof PropertyResolver) {
                PropertyResolver propertyResolver = (PropertyResolver) beanContext;
                String notEquals = annotation.notEquals();
                boolean hasNotEquals = StringUtils.isNotEmpty(notEquals);
                if(!propertyResolver.containsProperties(property)) {
                    return hasNotEquals ? true : false;
                }
                else if(StringUtils.isNotEmpty(value)) {
                    Optional<String> resolved = propertyResolver.getProperty(property, String.class);
                    return resolved.map(val -> val.equals(value)).orElse(false);
                }
                else if(hasNotEquals) {
                        String resolvedValue = propertyResolver.getProperty(property, String.class).orElse(null);
                        return resolvedValue == null || resolvedValue.equals(value);
                    }
            }
        }
        return true;
    }

    private boolean matchesMissingProperty(ConditionContext context, Requires annotation) {
        String property = annotation.missingProperty();
        if(property.length() > 0) {
            BeanContext beanContext = context.getBeanContext();
            if(beanContext instanceof PropertyResolver) {
                PropertyResolver propertyResolver = (PropertyResolver) beanContext;
                if(propertyResolver.containsProperties(property)) {
                    return false;
                }
            }
        }
        return true;
    }

    private boolean matchesEnvironment(ConditionContext context, Requires annotation) {
        String[] env = annotation.env();
        if(env.length == 0) {
            return true;
        }
        else {
            BeanContext beanContext = context.getBeanContext();
            if(beanContext instanceof ApplicationContext) {
                ApplicationContext applicationContext = (ApplicationContext) beanContext;
                Environment environment = applicationContext.getEnvironment();
                return Arrays.stream(env).anyMatch(name -> environment.getActiveNames().contains(name));
            }
        }
        return true;
    }

    private boolean matchesConditions(ConditionContext context, Requires annotation) {
        Class<? extends Condition> conditionClass = annotation.condition();
        if(conditionClass == TrueCondition.class) {
            return true;
        }
        else {
            try {
                return conditionClass.newInstance().matches(context);
            } catch (Throwable e) {
                // maybe a Groovy closure
                Optional<Constructor<?>> constructor = ReflectionUtils.findConstructor((Class)conditionClass, Object.class, Object.class);
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

    private boolean matchesEndpoint(ConditionContext context, Requires annotation) {
        String endpoint = annotation.endpoint();
        if(endpoint.length() > 0) {
            BeanContext beanContext = context.getBeanContext();

            if(beanContext instanceof PropertyResolver) {
                PropertyResolver propertyResolver = (PropertyResolver) beanContext;
                Optional<Boolean> enabled = propertyResolver.getProperty(String.format("%s.enabled", endpoint), Boolean.class);
                if (enabled.isPresent()) {
                    return enabled.get();
                } else {
                    String[] prefix = endpoint.split("\\.");
                    prefix[prefix.length - 1] = "all";
                    String allProperty = String.format("%s.enabled", String.join(".", prefix));
                    enabled = propertyResolver.getProperty(allProperty, Boolean.class);
                    return enabled.orElse(true);
                }
            }
        }
        return true;
    }

    private boolean matchesSdk(Requires annotation) {
        Requires.Sdk sdk = annotation.sdk();
        String version = annotation.version();
        if(version.length() > 0) {

            switch (sdk) {
                case GROOVY:
                    String groovyVersion = GroovySystem.getVersion();
                    return SemanticVersion.isAtLeast(groovyVersion, version);
                case JAVA:
                    String javaVersion = System.getProperty("java.version");
                    return SemanticVersion.isAtLeast(javaVersion, version);
                default:
                    return SemanticVersion.isAtLeast(getClass().getPackage().getImplementationVersion(), version);
            }
        }
        return true;
    }

    protected boolean matchesPresenceOfClasses(ConditionContext context, AnnotationValue requires) {
        ConvertibleValues<Object> convertibleValues = requires.getConvertibleValues();
        return matchesPresenceOfClasses(context, convertibleValues);
    }

    protected boolean matchesPresenceOfBean(ConditionContext context, AnnotationValue requires) {
        ConvertibleValues<Object> convertibleValues = requires.getConvertibleValues();
        return matchPresentOfBeans(context, convertibleValues);
    }

    private boolean matchesPresenceOfClasses(ConditionContext context, ConvertibleValues<Object> convertibleValues) {
        return matchesPresenceOfClasses(context, convertibleValues, "classes");
    }

    private boolean matchesPresenceOfClasses(ConditionContext context, ConvertibleValues<Object> convertibleValues, String attr) {
        if(convertibleValues.contains(attr)) {
            Optional<String[]> classNames = convertibleValues.get(attr, String[].class);
            if(classNames.isPresent()) {
                String[] names = classNames.get();
                ClassLoader classLoader = context.getBeanContext().getClassLoader();
                for (String name : names) {
                    if(!ClassUtils.forName(name, classLoader).isPresent()) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private boolean matchPresentOfBeans(ConditionContext context, ConvertibleValues<Object> convertibleValues) {
        if(convertibleValues.contains("beans")) {
            BeanContext beanContext = context.getBeanContext();
            Optional<String[]> classNames = convertibleValues.get("beans", String[].class);
            if(classNames.isPresent()) {
                String[] names = classNames.get();
                for (String name : names) {
                    Optional<Class> type = ClassUtils.forName(name, beanContext.getClassLoader());
                    if(!type.isPresent()) {
                        return false;
                    }
                    else {
                        if(!beanContext.containsBean(type.get())) {
                            return false;
                        }
                    }
                }
            }
        }
        return true;
    }

    protected boolean matchesConfiguration(ConditionContext context, Requires requires) {

        String configurationName = requires.configuration();
        if(configurationName.length() == 0) {
            return true;
        }

        BeanContext beanContext = context.getBeanContext();
        String minimumVersion = requires.version();
        Optional<BeanConfiguration> beanConfiguration = beanContext.findBeanConfiguration(configurationName);
        if(!beanConfiguration.isPresent()) {
            return false;
        }
        else {
            String version = beanConfiguration.get().getVersion();
            if(version != null && minimumVersion.length() > 0) {
                return SemanticVersion.isAtLeast(version, minimumVersion);
            }
            else {
                return true;
            }
        }
    }


}
