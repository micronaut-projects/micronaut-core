package io.micronaut.validation.properties;

import io.micronaut.context.annotation.Property;
import io.micronaut.context.env.DefaultPropertyPlaceholderResolver;
import io.micronaut.context.env.PropertyPlaceholderResolver;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.ConstructorElement;
import io.micronaut.inject.ast.Element;
import io.micronaut.inject.ast.FieldElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.ParameterElement;
import io.micronaut.inject.visitor.TypeElementVisitor;
import io.micronaut.inject.visitor.VisitorContext;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Visitor to check that only kebab-case values are used as values in annotations.
 *
 * @author Iván López
 * @since 1.1.0
 */
public class MixedCasePropertyTypeElementVisitor implements TypeElementVisitor<Object, Object> {

    private final PropertyPlaceholderResolver resolver = new DefaultPropertyPlaceholderResolver(null);

    @Override
    public void visitClass(ClassElement element, VisitorContext context) {
        visitElement(element, context);
    }

    @Override
    public void visitField(FieldElement element, VisitorContext context) {
        AnnotationValue<Property> propertyAnnotation = element.getAnnotation(Property.class);
        if (propertyAnnotation != null) {
            String propertyName = propertyAnnotation.getRequiredValue("name", String.class);
            if (!NameUtils.isValidHyphenatedPropertyName(propertyName)) {
                emitError(propertyName, element, context);
            }
        } else {
            visitElement(element, context);
        }
    }

    @Override
    public void visitConstructor(ConstructorElement element, VisitorContext context) {
        for (ParameterElement parameter : element.getParameters()) {
            visitElement(parameter, context);
        }
    }

    @Override
    public void visitMethod(MethodElement element, VisitorContext context) {
        visitElement(element, context);
    }

    private void visitElement(Element element, VisitorContext context) {
        Set<String> annotationNames = element.getAnnotationNames();

        for (String annotationName : annotationNames) {
            AnnotationValue annotationValue = element.getAnnotation(annotationName);

            Map<String, Object> values = annotationValue.getValues();
            for (Map.Entry<String, Object> entry : values.entrySet()) {
                Object value = entry.getValue();
                if (value instanceof String) {
                    String key = entry.getKey();
                    Optional<String> optAnnValue = annotationValue.get(key, String.class);

                    if (optAnnValue.isPresent()) {
                        String annValue = optAnnValue.get();
                        checkValidPropertyName(annValue, element, context);
                    }
                } else if (value instanceof String[]) {
                    String key = entry.getKey();
                    Optional<String[]> optAnnValue = annotationValue.get(key, String[].class);

                    if (optAnnValue.isPresent()) {
                        String[] annValues = optAnnValue.get();
                        for (String annValue : annValues) {
                            checkValidPropertyName(annValue, element, context);
                        }
                    }
                }
            }
        }
    }

    private void checkValidPropertyName(String value, Element element, VisitorContext context) {
        if (value.startsWith("${")) {
            List<PropertyPlaceholderResolver.Placeholder> properties = resolver.resolvePropertyNames(value);

            for (PropertyPlaceholderResolver.Placeholder property : properties) {
                if (!NameUtils.isValidHyphenatedPropertyName(property.getProperty())) {
                    emitError(property.getProperty(), element, context);
                }
            }
        }
    }

    private void emitError(String value, Element element, VisitorContext context) {
        context.fail("Value '" + value + "' is not valid. Please use kebab-case notation.", element);
    }
}
