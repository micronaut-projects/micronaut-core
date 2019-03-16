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
package io.micronaut.validation.properties;

import io.micronaut.context.annotation.Executable;
import io.micronaut.context.annotation.Property;
import io.micronaut.context.env.DefaultPropertyPlaceholderResolver;
import io.micronaut.context.env.PropertyPlaceholderResolver;
import io.micronaut.context.env.PropertyPlaceholderResolver.Placeholder;
import io.micronaut.context.env.PropertySourcePropertyResolver;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.core.util.StringUtils;
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

    private final PropertyPlaceholderResolver resolver = new DefaultPropertyPlaceholderResolver(new PropertySourcePropertyResolver());

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
        List<String> annotationNames = element.getAnnotationNamesByStereotype(Executable.class);

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
        if (value.contains("${")) {
            List<Placeholder> properties = resolver.resolvePropertyNames(value);

            for (Placeholder property : properties) {
                String propertyName = property.getProperty();

                if (!isValidPropertyName(propertyName)) {
                    emitError(propertyName, element, context);
                }

                Optional<Placeholder> placeholder = property.getPlaceholderValue();
                while (placeholder.isPresent()) {
                    String propValue = placeholder.get().getProperty();
                    String defaultValue = placeholder.get().getDefaultValue().orElse("");

                    if (!StringUtils.isEmpty(defaultValue) && !isValidPropertyName(propValue)) {
                        emitError(propValue, element, context);
                    }

                    placeholder = placeholder.get().getPlaceholderValue();
                }
            }
        }
    }

    private boolean isValidPropertyName(String value) {
        return NameUtils.isEnvironmentName(value) ||
                NameUtils.isValidHyphenatedPropertyName(value);
    }

    private void emitError(String value, Element element, VisitorContext context) {
        String kebabCaseValue = NameUtils.hyphenate(value);
        context.fail("Value '" + value + "' is not valid property placeholder. " +
                "Please use kebab-case notation, for example '" + kebabCaseValue + "'.", element);
    }
}
