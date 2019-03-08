package io.micronaut.validation.properties;

import io.micronaut.context.annotation.Property;
import io.micronaut.context.annotation.Value;
import io.micronaut.context.env.DefaultPropertyPlaceholderResolver;
import io.micronaut.context.env.PropertyPlaceholderResolver;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.inject.ast.FieldElement;
import io.micronaut.inject.visitor.TypeElementVisitor;
import io.micronaut.inject.visitor.VisitorContext;

import java.util.List;
import java.util.Optional;

/**
 * Visitor to check that only kebab-case values are used as values in annotations.
 *
 * @author Iván López
 * @since 1.1.0
 */
public class MixedCasePropertyTypeElementVisitor implements TypeElementVisitor<Object, Object> {

    private final PropertyPlaceholderResolver resolver = new DefaultPropertyPlaceholderResolver(null);

    @Override
    public void visitField(FieldElement element, VisitorContext context) {
        AnnotationValue<Property> propertyAnnotation = element.getAnnotation(Property.class);
        if (propertyAnnotation != null) {
            String propertyName = propertyAnnotation.getRequiredValue("name", String.class);
            if (!NameUtils.isValidHyphenatedPropertyName(propertyName)) {
                context.fail("Value '" + propertyName + "' used in @Property is not valid. Please use kebab-case notation.", element);
            }
        }

        AnnotationValue<Value> valueAnnotation = element.getAnnotation(Value.class);
        if (valueAnnotation != null) {
            Optional<String> value = valueAnnotation.get("value", String.class);
            if (value.isPresent()) {
                String placeholder = value.get();
                List<PropertyPlaceholderResolver.Placeholder> properties = resolver.resolvePropertyNames(placeholder);

                properties.forEach(propertyPlaceholder -> {
                    if (!NameUtils.isValidHyphenatedPropertyName(propertyPlaceholder.getProperty())) {
                        context.fail("Value '" + propertyPlaceholder.getProperty() + "' used in @Value is not valid. Please use kebab-case notation.", element);
                    }
                });
            }
        }
    }
}
