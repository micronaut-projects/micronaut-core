package org.particleframework.context.condition;

import groovy.lang.GroovySystem;
import org.particleframework.context.BeanContext;
import org.particleframework.context.annotation.Requires;
import org.particleframework.core.version.SemanticVersion;
import org.particleframework.inject.BeanConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Optional;

/**
 * An abstract {@link Condition} implementation that is based on the presence
 * of {@link Requires} annotation
 */
public class RequiresCondition implements Condition {

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

    private boolean matchesConditions(ConditionContext context, Requires annotation) {
        Class<? extends Condition> condition = annotation.condition();
        if(condition == TrueCondition.class) {
            return true;
        }
        else {
            try {
                return !condition.newInstance().matches(context);
            } catch (Throwable e) {
                if(LOG.isErrorEnabled()) {
                    LOG.error("Error instantiating condition ["+condition.getName()+"]: " + e.getMessage(), e);
                }
                return false;
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
            Class[] classes = requires.classes();
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
        Optional<BeanConfiguration> beanConfiguration = beanContext.getBeanConfiguration(configurationName);
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
