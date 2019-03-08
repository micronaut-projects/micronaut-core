package io.micronaut.validation.properties;

import io.micronaut.context.annotation.Property;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.inject.ast.FieldElement;
import io.micronaut.inject.visitor.TypeElementVisitor;
import io.micronaut.inject.visitor.VisitorContext;

/**
 * Visitor to check that only kebab-case values are used as values in annotations.
 *
 * @author Iván López
 * @since 1.1.0
 */
public class MixedCasePropertyTypeElementVisitor implements TypeElementVisitor<Object, Object> {

    @Override
    public void visitField(FieldElement element, VisitorContext context) {
        AnnotationValue<Property> propertyAnnotation = element.getAnnotation(Property.class);
        if (propertyAnnotation != null) {
            String propertyName = propertyAnnotation.getRequiredValue("name", String.class);
            if (!NameUtils.isValidHyphenatedPropertyName(propertyName)) {
                context.fail("Value '" + propertyName + "' used in @Property is not valid. Please use kebab-case notation.", element);
            }
        }
    }
}
