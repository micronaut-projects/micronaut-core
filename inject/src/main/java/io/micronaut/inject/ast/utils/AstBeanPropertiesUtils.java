/*
 * Copyright 2017-2022 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.inject.ast.utils;

import io.micronaut.context.annotation.BeanProperties;
import io.micronaut.context.annotation.Property;
import io.micronaut.context.annotation.Value;
import io.micronaut.core.annotation.AnnotationUtil;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.inject.ast.BeanPropertiesConfiguration;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.ElementQuery;
import io.micronaut.inject.ast.FieldElement;
import io.micronaut.inject.ast.MemberElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.PropertyElement;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * The AST bean properties utils.
 *
 * @author Denis Stepanov
 * @since 4.0.0
 */
public final class AstBeanPropertiesUtils {

    private AstBeanPropertiesUtils() {
    }

    /**
     * Resolve the bean properties based on the configuration.
     *
     * @param configuration                    The configuration
     * @param classElement                     The class element
     * @param methodsSupplier                  The methods supplier
     * @param fieldSupplier                    The fields supplier
     * @param excludeElementsInRole            Should exclude elements in role?
     * @param propertyFields                   The fields that are properties
     * @param customReaderPropertyNameResolver Custom resolver of the property name from the reader
     * @param customWriterPropertyNameResolver Custom resolver of the property name from the writer
     * @param propertyCreator                  The property creator
     * @return the list of properties
     */
    public static List<PropertyElement> resolveBeanProperties(BeanPropertiesConfiguration configuration,
                                                              ClassElement classElement,
                                                              Supplier<List<MethodElement>> methodsSupplier,
                                                              Supplier<List<FieldElement>> fieldSupplier,
                                                              boolean excludeElementsInRole,
                                                              Set<String> propertyFields,
                                                              Function<MethodElement, Optional<String>> customReaderPropertyNameResolver,
                                                              Function<MethodElement, Optional<String>> customWriterPropertyNameResolver,
                                                              Function<BeanPropertyData, PropertyElement> propertyCreator) {
        BeanProperties.Visibility visibility = configuration.getVisibility();
        Set<BeanProperties.AccessKind> accessKinds = configuration.getAccessKinds();

        Set<String> includes = configuration.getIncludes();
        Set<String> excludes = configuration.getExcludes();
        String[] readPrefixes = configuration.getReadPrefixes();
        String[] writePrefixes = configuration.getWritePrefixes();

        Map<String, BeanPropertyData> props = new LinkedHashMap<>();
        for (MethodElement methodElement : methodsSupplier.get()) {
            // Records include everything
            if (methodElement.isStatic() && !configuration.isAllowStaticProperties()
                || !excludeElementsInRole && (methodElement.hasDeclaredAnnotation(AnnotationUtil.INJECT)
                || methodElement.hasDeclaredAnnotation(AnnotationUtil.PRE_DESTROY)
                || methodElement.hasDeclaredAnnotation(AnnotationUtil.POST_CONSTRUCT))
            ) {
                continue;
            }
            String methodName = methodElement.getName();
            if (methodName.contains("$") || methodName.equals("getMetaClass")) {
                continue;
            }
            boolean isAccessor = canMethodBeUsedForAccess(methodElement, accessKinds, visibility);
            if (classElement.isRecord()) {
                if (!isAccessor) {
                    continue;
                }
                String propertyName = methodElement.getSimpleName();
                processRecord(props, methodElement, propertyName);
            } else if (NameUtils.isReaderName(methodName, readPrefixes) && methodElement.getParameters().length == 0) {
                String propertyName = customReaderPropertyNameResolver.apply(methodElement)
                    .orElseGet(() -> NameUtils.getPropertyNameForGetter(methodName, readPrefixes));
                processGetter(props, methodElement, propertyName, isAccessor);
            } else if (NameUtils.isWriterName(methodName, writePrefixes)
                && (methodElement.getParameters().length == 1
                || configuration.isAllowSetterWithZeroArgs() && methodElement.getParameters().length == 0
                || configuration.isAllowSetterWithMultipleArgs() && methodElement.getParameters().length > 1)) {
                String propertyName = customWriterPropertyNameResolver.apply(methodElement)
                    .orElseGet(() -> NameUtils.getPropertyNameForSetter(methodName, writePrefixes));
                processSetter(props, methodElement, propertyName, isAccessor);
            }
        }
        for (FieldElement fieldElement : fieldSupplier.get()) {
            if (fieldElement.isStatic() && !configuration.isAllowStaticProperties()
                || !excludeElementsInRole && (fieldElement.hasDeclaredAnnotation(AnnotationUtil.INJECT)
                || fieldElement.hasStereotype(Value.class)
                || fieldElement.hasStereotype(Property.class))
            ) {
                continue;
            }
            String propertyName = fieldElement.getSimpleName();
            boolean isAccessor = propertyFields.contains(propertyName) || canFieldBeUsedForAccess(fieldElement, accessKinds, visibility);
            if (!isAccessor && !props.containsKey(propertyName)) {
                continue;
            }
            BeanPropertyData beanPropertyData = props.computeIfAbsent(propertyName, BeanPropertyData::new);
            resolveReadAccessForField(fieldElement, isAccessor, beanPropertyData);
            resolveWriteAccessForField(fieldElement, isAccessor, beanPropertyData);
        }
        if (!props.isEmpty()) {
            List<PropertyElement> beanProperties = new ArrayList<>(props.size());
            for (Map.Entry<String, BeanPropertyData> entry : props.entrySet()) {
                String propertyName = entry.getKey();
                BeanPropertyData value = entry.getValue();
                if (value.readAccessKind != null || value.writeAccessKind != null) {
                    value.isExcluded = shouldExclude(includes, excludes, propertyName)
                        || isExcludedByAnnotations(configuration, value)
                        || isExcludedBecauseOfMissingAccess(value);
                    PropertyElement propertyElement = propertyCreator.apply(value);
                    if (propertyElement != null) {
                        beanProperties.add(propertyElement);
                    }
                }
            }
            return beanProperties;
        }
        return Collections.emptyList();
    }

    private static boolean isExcludedBecauseOfMissingAccess(BeanPropertyData value) {
        if (value.readAccessKind == BeanProperties.AccessKind.METHOD
            && value.getter == null
            && value.writeAccessKind == BeanProperties.AccessKind.METHOD
            && value.setter == null) {
            return true;
        }
        if (value.readAccessKind == BeanProperties.AccessKind.FIELD
            && value.writeAccessKind == BeanProperties.AccessKind.FIELD
            && value.field == null) {
            return true;
        }
        return value.readAccessKind == null && value.writeAccessKind == null;
    }

    private static boolean isExcludedByAnnotations(BeanPropertiesConfiguration conf, BeanPropertyData value) {
        if (conf.getExcludedAnnotations().isEmpty()) {
            return false;
        }
        if (value.field != null && conf.getExcludedAnnotations().stream().anyMatch(value.field::hasAnnotation)) {
            return true;
        }
        if (value.getter != null && conf.getExcludedAnnotations().stream().anyMatch(value.getter::hasAnnotation)) {
            return true;
        }
        return (value.setter != null && conf.getExcludedAnnotations().stream().anyMatch(value.setter::hasAnnotation));
    }

    private static void processRecord(Map<String, BeanPropertyData> props, MethodElement methodElement, String propertyName) {
        BeanPropertyData beanPropertyData = props.computeIfAbsent(propertyName, BeanPropertyData::new);
        beanPropertyData.getter = methodElement;
        beanPropertyData.readAccessKind = BeanProperties.AccessKind.METHOD;
        beanPropertyData.type = beanPropertyData.getter.getGenericReturnType();
    }

    private static void processGetter(Map<String, BeanPropertyData> props, MethodElement methodElement, String propertyName, boolean isAccessor) {
        BeanPropertyData beanPropertyData = props.computeIfAbsent(propertyName, BeanPropertyData::new);
        beanPropertyData.getter = methodElement;
        if (isAccessor) {
            beanPropertyData.readAccessKind = BeanProperties.AccessKind.METHOD;
        }
        ClassElement genericReturnType = beanPropertyData.getter.getGenericReturnType();
        ClassElement getterType = unwrapType(genericReturnType);
        if (beanPropertyData.type != null) {
            if (!getterType.isAssignable(unwrapType(beanPropertyData.type))) {
                beanPropertyData.getter = null; // not a compatible getter
                beanPropertyData.readAccessKind = null;
            }
        } else {
            beanPropertyData.type = genericReturnType;
        }
    }

    private static void processSetter(Map<String, BeanPropertyData> props, MethodElement methodElement, String propertyName, boolean isAccessor) {
        BeanPropertyData beanPropertyData = props.computeIfAbsent(propertyName, BeanPropertyData::new);
        ClassElement paramType = methodElement.getParameters().length == 0 ? null : methodElement.getParameters()[0].getGenericType();
        ClassElement setterType = paramType == null ? null : unwrapType(paramType);
        if (setterType != null && beanPropertyData.setter != null) {
            if (setterType.isAssignable(unwrapType(beanPropertyData.type))) {
                // Override the setter because the type is higher
                beanPropertyData.setter = methodElement;
            }
            return;
        }
        beanPropertyData.setter = methodElement;
        if (isAccessor) {
            beanPropertyData.writeAccessKind = BeanProperties.AccessKind.METHOD;
        }
        if (beanPropertyData.type != null) {
            if (setterType != null && !setterType.isAssignable(unwrapType(beanPropertyData.type))) {
                beanPropertyData.setter = null; // not a compatible setter
                beanPropertyData.writeAccessKind = null;
            }
        } else {
            beanPropertyData.type = paramType;
        }
    }

    private static ClassElement unwrapType(ClassElement type) {
        if (type.isOptional()) {
            return type.getFirstTypeArgument().orElse(type);
        }
        return type;
    }

    private static void resolveWriteAccessForField(FieldElement fieldElement, boolean isAccessor, BeanPropertyData beanPropertyData) {
        if (fieldElement.isFinal()) {
            return;
        }
        ClassElement fieldType = unwrapType(fieldElement.getGenericType());
        if (beanPropertyData.setter == null) {
            if (beanPropertyData.type != null) {
                if (fieldType.isAssignable(unwrapType(beanPropertyData.type))) {
                    beanPropertyData.field = fieldElement;
                    if (isAccessor) {
                        beanPropertyData.writeAccessKind = BeanProperties.AccessKind.FIELD;
                    }
                }
                // Else: not compatible field
            } else {
                beanPropertyData.field = fieldElement;
                beanPropertyData.type = fieldElement.getGenericType();
                if (isAccessor) {
                    beanPropertyData.writeAccessKind = BeanProperties.AccessKind.FIELD;
                }
            }
        } else {
            beanPropertyData.field = fieldElement;
        }
    }

    private static void resolveReadAccessForField(FieldElement fieldElement, boolean isAccessor, BeanPropertyData beanPropertyData) {
        ClassElement unwrappedFieldType = unwrapType(fieldElement.getGenericType());
        if (beanPropertyData.getter == null) {
            if (beanPropertyData.type != null) {
                if (unwrappedFieldType.isAssignable(unwrapType(beanPropertyData.type))) {
                    // Override the existing type to include generic annotations
                    if (beanPropertyData.type.isAssignable(fieldElement.getGenericType())) {
                        beanPropertyData.type = fieldElement.getGenericType();
                    }
                    beanPropertyData.field = fieldElement;
                    if (isAccessor) {
                        beanPropertyData.readAccessKind = BeanProperties.AccessKind.FIELD;
                    }
                }
                // Else: not compatible field
            } else {
                beanPropertyData.field = fieldElement;
                beanPropertyData.type = fieldElement.getGenericType();
                if (isAccessor) {
                    beanPropertyData.readAccessKind = BeanProperties.AccessKind.FIELD;
                }
            }
        } else {
            beanPropertyData.field = fieldElement;
            if (beanPropertyData.type.isAssignable(fieldElement.getGenericType())) {
                // Override the existing type to include generic annotations
                beanPropertyData.type = fieldElement.getGenericType();
            }
        }
    }

    private static boolean canFieldBeUsedForAccess(FieldElement fieldElement,
                                                   Set<BeanProperties.AccessKind> accessKinds,
                                                   BeanProperties.Visibility visibility) {
        if (fieldElement.getOwningType().isRecord()) {
            return false;
        }
        if (accessKinds.contains(BeanProperties.AccessKind.FIELD)) {
            return isAccessible(fieldElement, visibility);
        }
        return false;
    }

    private static boolean canMethodBeUsedForAccess(MethodElement methodElement,
                                                    Set<BeanProperties.AccessKind> accessKinds,
                                                    BeanProperties.Visibility visibility) {
        return accessKinds.contains(BeanProperties.AccessKind.METHOD) && isAccessible(methodElement, visibility);
    }

    private static boolean isAccessible(MemberElement memberElement, BeanProperties.Visibility visibility) {
        switch (visibility) {
            case DEFAULT:
                return !memberElement.isPrivate() && (memberElement.isAccessible() || memberElement.getDeclaringType().hasDeclaredStereotype(BeanProperties.class));
            case PUBLIC:
                return memberElement.isPublic();
            default:
                return false;
        }
    }

    public static List<MethodElement> getSubtypeFirstMethods(ClassElement classElement) {
        List<MethodElement> methods = classElement.getEnclosedElements(ElementQuery.ALL_METHODS.onlyInstance());
        List<MethodElement> result = new ArrayList<>(methods.size());
        List<MethodElement> other = new ArrayList<>(methods.size());
        // Process subtype methods first
        for (MethodElement methodElement : methods) {
            if (methodElement.getDeclaringType().equals(classElement)) {
                other.add(methodElement);
            } else {
                result.add(methodElement);
            }
        }
        result.addAll(other);
        return result;

    }

    public static List<FieldElement> getSubtypeFirstFields(ClassElement classElement) {
        List<FieldElement> fields = classElement.getEnclosedElements(ElementQuery.ALL_FIELDS);
        List<FieldElement> result = new ArrayList<>(fields.size());
        List<FieldElement> other = new ArrayList<>(fields.size());
        // Process subtype fields first
        for (FieldElement fieldElement : fields) {
            if (fieldElement.getDeclaringType().equals(classElement)) {
                other.add(fieldElement);
            } else {
                result.add(fieldElement);
            }
        }
        result.addAll(other);
        return result;
    }

    private static boolean shouldExclude(Set<String> includes, Set<String> excludes, String propertyName) {
        if (!includes.isEmpty() && !includes.contains(propertyName)) {
            return true;
        }
        return !excludes.isEmpty() && excludes.contains(propertyName);
    }

    /**
     * Internal holder class for getters and setters.
     */
    @SuppressWarnings("VisibilityModifier")
    public static class BeanPropertyData {
        public ClassElement type;
        public MethodElement getter;
        public MethodElement setter;
        public FieldElement field;
        public BeanProperties.AccessKind readAccessKind;
        public BeanProperties.AccessKind writeAccessKind;
        public final String propertyName;
        public boolean isExcluded;

        public BeanPropertyData(String propertyName) {
            this.propertyName = propertyName;
        }
    }

}
