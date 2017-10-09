package org.particleframework.context.condition;

import groovy.lang.GroovySystem;
import org.particleframework.config.PropertyResolver;
import org.particleframework.context.ApplicationContext;
import org.particleframework.context.BeanContext;
import org.particleframework.context.annotation.Requires;
import org.particleframework.context.env.Environment;
import org.particleframework.core.reflect.InstantiationUtils;
import org.particleframework.core.reflect.ReflectionUtils;
import org.particleframework.core.version.SemanticVersion;
import org.particleframework.inject.BeanConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Optional;

/**
 * An abstract {@link Condition} implementation that is based on the presence
 * of {@link Requires} annotation
 */
public class RequiresCondition implements Condition<ConditionContext> {

    private static final Logger LOG = LoggerFactory.getLogger(RequiresCondition.class);
    private final Requires[] requiresAnnotations;

    public RequiresCondition(Requires[] annotations) {
        this.requiresAnnotations = annotations;
    }

    @Override
    public boolean matches(ConditionContext context) {
        if(requiresAnnotations.length == 0 ) {
            return true;
        }

        for (Requires annotation : requiresAnnotations) {
            if(!matchesPresenceOfClasses(annotation)) {
                return false;
            }
            if(!matchesEnvironment(context, annotation)) {
                return false;
            }
            if(!matchesProperty(context, annotation)) {
                return false;
            }
            if(!matchesMissingProperty(context, annotation)) {
                return false;
            }
            if(!matchesPresenceOfBean(context, annotation)) {
                return false;
            }
            if(!matchesConfiguration(context, annotation)) {
                return false;
            }
            if(!matchesSdk(annotation)) {
                return false;
            }
            if(!matchesConditions(context, annotation)) {
                return false;
            }
        }
        return true;
    }

    private boolean matchesProperty(ConditionContext context, Requires annotation) {
        String property = annotation.property();
        if(property.length() > 0) {
            String value = annotation.value();
            BeanContext beanContext = context.getBeanContext();
            if(beanContext instanceof PropertyResolver) {
                PropertyResolver propertyResolver = (PropertyResolver) beanContext;
                Optional<String> resolved = propertyResolver.getProperty(property, String.class);
                if(!resolved.isPresent()) {
                    return false;
                }
                else if(value.length()>0) {
                    String resolvedValue = resolved.get();
                    if(!resolvedValue.equals(value)) {
                        return false;
                    }
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
                if(propertyResolver.containsProperty(property)) {
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
                return Arrays.stream(env).anyMatch(name -> name.equalsIgnoreCase(environment.getName()));
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

    protected boolean matchesPresenceOfClasses(Requires requires) {
        try {
            requires.classes();
            return true;
        } catch (TypeNotPresentException e) {
            // type not present exception
            return false;
        }
    }
    protected boolean matchesPresenceOfBean(ConditionContext context, Requires requires) {
        try {
            Class[] beans = requires.beans();
            if(beans.length == 0)
                return true;

            BeanContext beanContext = context.getBeanContext();
            long allThere = Arrays.stream(beans)
                    .filter(type -> beanContext.containsBean(type))
                    .count();

            return beans.length == allThere;
        } catch (TypeNotPresentException e) {
            // type not present exception
            return false;
        }
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
