package org.particleframework.context.condition;

import org.particleframework.context.BeanContext;
import org.particleframework.context.annotation.Requires;
import org.particleframework.core.version.SemanticVersion;
import org.particleframework.inject.BeanConfiguration;

import java.util.Arrays;
import java.util.Optional;

/**
 * An abstract {@link Condition} implementation that is based on the presence
 * of {@link Requires} annotation
 */
public class RequiresCondition implements Condition {

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
