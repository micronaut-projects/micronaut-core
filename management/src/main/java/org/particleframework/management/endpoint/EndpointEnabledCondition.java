package org.particleframework.management.endpoint;

import org.particleframework.context.BeanContext;
import org.particleframework.context.condition.Condition;
import org.particleframework.context.condition.ConditionContext;
import org.particleframework.core.annotation.AnnotationMetadata;
import org.particleframework.core.annotation.AnnotationMetadataProvider;
import org.particleframework.core.value.PropertyResolver;

import java.util.Optional;

public class EndpointEnabledCondition implements Condition {

    @Override
    public boolean matches(ConditionContext context) {
        AnnotationMetadataProvider component = context.getComponent();
        AnnotationMetadata annotationMetadata = component.getAnnotationMetadata();

        if (annotationMetadata.hasDeclaredAnnotation(Endpoint.class)) {

            Boolean defaultEnabled = annotationMetadata.getValue(Endpoint.class, "defaultEnabled", Boolean.class).orElse(true);
            String prefix = annotationMetadata.getValue(Endpoint.class,"prefix", String.class).orElse(null);
            String id = annotationMetadata.getValue(Endpoint.class,"value", String.class).orElse(null);
            String defaultId = annotationMetadata.getValue(Endpoint.class,"defaultConfigurationId", String.class).orElse(null);

            BeanContext beanContext = context.getBeanContext();
            if(beanContext instanceof PropertyResolver) {
                PropertyResolver propertyResolver = (PropertyResolver) beanContext;
                Optional<Boolean> enabled = propertyResolver.getProperty(String.format("%s.%s.enabled", prefix, id), Boolean.class);
                if (enabled.isPresent()) {
                    return enabled.get();
                } else {
                    enabled = propertyResolver.getProperty(String.format("%s.%s.enabled", prefix, defaultId), Boolean.class);
                    return enabled.orElse(defaultEnabled);
                }
            }
        }

        return true;
    }
}
