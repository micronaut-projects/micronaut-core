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

import io.micronaut.context.annotation.Property;
import io.micronaut.context.env.DefaultPropertyPlaceholderResolver;
import io.micronaut.context.env.DefaultPropertyPlaceholderResolver.*;
import io.micronaut.context.env.PropertySourcePropertyResolver;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.convert.DefaultConversionService;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.ConstructorElement;
import io.micronaut.inject.ast.Element;
import io.micronaut.inject.ast.FieldElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.ParameterElement;
import io.micronaut.inject.visitor.TypeElementVisitor;
import io.micronaut.inject.visitor.VisitorContext;

import javax.annotation.processing.SupportedOptions;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Visitor to check that only kebab-case values are used as values in annotations.
 *
 * @author Iván López
 * @since 1.1.0
 * @deprecated No replacement because mixed case keys can now be resolved
 */
@SupportedOptions(MixedCasePropertyTypeElementVisitor.VALIDATION_OPTION)
@Deprecated
public class MixedCasePropertyTypeElementVisitor implements TypeElementVisitor<Object, Object> {

    static final String VALIDATION_OPTION = "micronaut.configuration.validation";
    private boolean skipValidation = false;
    private final DefaultPropertyPlaceholderResolver resolver = new DefaultPropertyPlaceholderResolver(new PropertySourcePropertyResolver(), new DefaultConversionService());

    @Override
    public void visitClass(ClassElement element, VisitorContext context) {
        visitElement(element, context);
    }

    @Override
    public void visitField(FieldElement element, VisitorContext context) {
        visitElement(element, context);
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

    @Override
    public void start(VisitorContext visitorContext) {
        String prop = visitorContext.getOptions().getOrDefault(VALIDATION_OPTION, "true");
        skipValidation = prop != null && prop.equals("false");
    }

    private void visitElement(Element element, VisitorContext context) {
        if (skipValidation) {
            return;
        }
        AnnotationValue<Property> propertyAnnotation = element.getAnnotation(Property.class);
        if (propertyAnnotation != null) {
            String propertyName = propertyAnnotation.getRequiredValue("name", String.class);
            if (!NameUtils.isValidHyphenatedPropertyName(propertyName)) {
                emitError(propertyName, element, context);
            }
        }

        Set<String> annotationNames = element.getAnnotationNames();

        for (String annotationName : annotationNames) {
            if (!annotationName.startsWith("io.micronaut.")) {
                // only process Micronaut annotations
                continue;
            }
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
        resolver.buildSegments(value)
                .stream()
                .filter(PlaceholderSegment.class::isInstance)
                .map(PlaceholderSegment.class::cast)
                .flatMap(placeholder -> placeholder.getExpressions().stream())
                .forEach((String propertyName) -> {
                    if (!isValidPropertyName(propertyName)) {
                        emitError(propertyName, element, context);
                    }
                });

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
