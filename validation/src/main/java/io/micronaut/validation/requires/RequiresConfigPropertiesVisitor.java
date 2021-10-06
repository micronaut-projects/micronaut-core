package io.micronaut.validation.requires;

import io.micronaut.context.RequiresCondition;
import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.inject.ast.beans.BeanElement;
import io.micronaut.inject.configuration.ConfigurationMetadataBuilder;
import io.micronaut.inject.configuration.PropertyMetadata;
import io.micronaut.inject.visitor.BeanElementVisitor;
import io.micronaut.inject.visitor.VisitorContext;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.List;

import static io.micronaut.inject.writer.BeanDefinitionVisitor.PROXY_SUFFIX;

/**
 * A {@link io.micronaut.inject.visitor.BeanElementVisitor} that validates usage of {@link Requires} annotations
 * which reference configuration properties classes and fields.
 */
@Internal
public class RequiresConfigPropertiesVisitor implements BeanElementVisitor<Annotation> {

    private List<BeanElement> requiresConfigPropsBeans = new ArrayList<>();
    private List<BeanElement> configPropertiesBeans = new ArrayList<>();

    private ConfigurationMetadataBuilder<?> metadataBuilder;

    @Override
    public void start(VisitorContext visitorContext) {
        metadataBuilder = ConfigurationMetadataBuilder.getConfigurationMetadataBuilder().orElse(null);
    }

    @Override
    public BeanElement visitBeanElement(@NonNull BeanElement beanElement,
                                        @NonNull VisitorContext visitorContext) {
        final AnnotationValue<Requires> requiresAnnotation = beanElement.getAnnotation(Requires.class);
        if (requiresAnnotation != null &&
                (requiresAnnotation.contains(RequiresCondition.MEMBER_CONFIGURATION_PROPERTIES) ||
                        requiresAnnotation.contains(RequiresCondition.MEMBER_CONFIGURATION_PROPERTY))) {
            requiresConfigPropsBeans.add(beanElement);
        }

        AnnotationValue<ConfigurationProperties> configPropertiesAnnotation = beanElement.getAnnotation(ConfigurationProperties.class);
        if (configPropertiesAnnotation != null) {
            configPropertiesBeans.add(beanElement);
        }

        return beanElement;
    }

    @Override
    public void finish(VisitorContext visitorContext) {
        for (BeanElement conditionalBean : requiresConfigPropsBeans) {
            AnnotationValue<Requires> requiresAnnotation = conditionalBean.getAnnotation(Requires.class);
            if (requiresAnnotation == null || metadataBuilder == null) {
                return;
            }

            String configProperty = requiresAnnotation.stringValue(RequiresCondition.MEMBER_CONFIGURATION_PROPERTY).orElse(null);
            String configPropertiesClass = requiresAnnotation.stringValue(RequiresCondition.MEMBER_CONFIGURATION_PROPERTIES).orElse(null);

            if (configPropertiesClass == null && configProperty != null) {
                fail("configProperty member of @Requires annotation can not be used without specifying configProperties class", visitorContext, conditionalBean);
                return;
            }

            boolean isConfigPropertyBean = false;
            if (configPropertiesClass != null) {
                for (BeanElement configPropertiesBean: configPropertiesBeans) {
                    String className = getClassName(configPropertiesBean);
                    if (className.equals(configPropertiesClass)) {
                        isConfigPropertyBean = true;
                        break;
                    }
                }
            }

            if (!isConfigPropertyBean) {
                fail("Specified class [" + configPropertiesClass + "] must be configuration properties bean", visitorContext, conditionalBean);
                return;
            }

            configPropertiesClass = configPropertiesClass.replace("$", ".");
            if (configProperty != null) {
                boolean propertyPresent = false;
                for (PropertyMetadata propertyMetadata: metadataBuilder.getProperties()) {
                    if (propertyMetadata.getDeclaringType().equals(configPropertiesClass) && propertyMetadata.getName().equals(configProperty)) {
                        propertyPresent = true;
                        break;
                    }
                }

                if (!propertyPresent) {
                    fail("Configuration property [" + configProperty + "] is not present on ["
                            + configPropertiesClass + "] class", visitorContext, conditionalBean);
                }
            }
        }
        cleanUp();
    }

    private String getClassName(BeanElement beanElement) {
        String className = beanElement.getDeclaringClass().getName();
        // maybe an interface
        if (className.endsWith(PROXY_SUFFIX)) {
            className = className.replace(PROXY_SUFFIX, "");
        }
        return className;
    }

    private void fail(String message, VisitorContext visitorContext, BeanElement element) {
        cleanUp();
        visitorContext.fail(message, element);
    }

    private void cleanUp() {
        this.metadataBuilder = null;
        this.configPropertiesBeans = new ArrayList<>();
        this.requiresConfigPropsBeans = new ArrayList<>();
    }
}
