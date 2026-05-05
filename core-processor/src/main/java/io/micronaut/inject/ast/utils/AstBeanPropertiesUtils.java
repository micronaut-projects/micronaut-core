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
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.FieldElement;
import io.micronaut.inject.ast.MemberElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.PrimitiveElement;
import io.micronaut.inject.ast.PropertyElement;
import io.micronaut.inject.ast.PropertyElementQuery;
import io.micronaut.inject.processing.ProcessingException;
import org.jspecify.annotations.NullUnmarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumSet;
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
@NullUnmarked
@Internal
public final class AstBeanPropertiesUtils {

    private static final String ANN_INTROSPECTED_PROPERTY = Introspected.Property.class.getName();
    private static final String MEMBER_IGNORE_OTHER_ACCESSORS = "ignoreOtherAccessors";

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
    public static List<PropertyElement> resolveBeanProperties(PropertyElementQuery configuration,
                                                              ClassElement classElement,
                                                              Supplier<List<MethodElement>> methodsSupplier,
                                                              Supplier<List<FieldElement>> fieldSupplier,
                                                              boolean excludeElementsInRole,
                                                              Set<String> propertyFields,
                                                              Function<MethodElement, Optional<String>> customReaderPropertyNameResolver,
                                                              Function<MethodElement, Optional<String>> customWriterPropertyNameResolver,
                                                              Function<BeanPropertyData, @Nullable PropertyElement> propertyCreator) {
        BeanProperties.Visibility visibility = configuration.getVisibility();
        Set<BeanProperties.AccessKind> accessKinds = configuration.getAccessKinds();

        Set<String> includes = configuration.getIncludes();
        Set<String> excludes = configuration.getExcludes();
        String[] readPrefixes = configuration.getReadPrefixes();
        String[] writePrefixes = configuration.getWritePrefixes();
        var isRecord = classElement.isRecord();

        var props = new LinkedHashMap<String, BeanPropertyData>();
        for (MethodElement methodElement : methodsSupplier.get()) {
            // Records include everything
            if ((methodElement.isStatic() && !configuration.isAllowStaticProperties()) || (!excludeElementsInRole && isMethodInRole(methodElement))) {
                continue;
            }
            String methodName = methodElement.getName();
            if (methodName.equals("getMetaClass")) {
                continue;
            }
            boolean isIntrospectedPropertyMethod = isIntrospectedPropertyMethod(methodElement, visibility);
            if (isRecord) {
                boolean isAccessor = canMethodBeUsedForAccess(methodElement, accessKinds, visibility) ||
                    isIntrospectedPropertyMethod;
                if (!isAccessor) {
                    continue;
                }
                String propertyName = methodElement.getSimpleName();
                processRecord(props, methodElement, propertyName);
            } else if (isReaderName(configuration, methodElement, methodName, readPrefixes)
                && methodElement.getParameters().length == 0) {
                String propertyName = customReaderPropertyNameResolver.apply(methodElement)
                    .orElseGet(() -> getPropertyNameForGetter(methodName, readPrefixes));
                boolean isAccessor = canMethodBeUsedForRead(methodElement, methodName, accessKinds, visibility, configuration) ||
                    isIntrospectedPropertyMethod;
                processGetter(props, methodElement, propertyName, isAccessor, configuration);
            } else if (isWriterName(configuration, methodName, writePrefixes)
                && canMethodBeUsedForWrite(methodElement, configuration)) {
                String propertyName = customWriterPropertyNameResolver.apply(methodElement)
                    .orElseGet(() -> getPropertyNameForSetter(methodName, writePrefixes));
                boolean isAccessor = canMethodBeUsedForWriteAccess(methodElement, accessKinds, visibility, configuration) ||
                    isIntrospectedPropertyMethod;
                processSetter(classElement, props, methodElement, propertyName, isAccessor, configuration);
            } else if (isIntrospectedPropertyReader(methodElement)) {
                processGetter(props, methodElement, methodName, isIntrospectedPropertyMethod, configuration);
            } else if (isIntrospectedPropertyWriter(methodElement)) {
                processSetter(classElement, props, methodElement, methodName, isIntrospectedPropertyMethod, configuration);
            }
        }
        for (FieldElement fieldElement : fieldSupplier.get()) {
            if ((fieldElement.isStatic() && !configuration.isAllowStaticProperties()) || (!excludeElementsInRole && isFieldInRole(fieldElement))) {
                continue;
            }
            String propertyName = fieldElement.getSimpleName();
            boolean isAccessor = propertyFields.contains(propertyName) ||
                isIntrospectedPropertyField(fieldElement, visibility) ||
                canFieldBeUsedForAccess(fieldElement, accessKinds, visibility, configuration);
            if (!isAccessor && !props.containsKey(propertyName)) {
                continue;
            }
            BeanPropertyData beanPropertyData = props.computeIfAbsent(propertyName, BeanPropertyData::new);
            boolean ignoreOtherAccessors = ignoresOtherAccessors(fieldElement);
            resolveReadAccessForField(fieldElement, isAccessor, beanPropertyData, ignoreOtherAccessors);
            resolveWriteAccessForField(fieldElement, isAccessor, beanPropertyData, ignoreOtherAccessors);
            registerIntrospectedPropertyAccess(beanPropertyData, fieldElement);
        }

        if (props.isEmpty()) {
            return List.of();
        }

        var beanProperties = new ArrayList<PropertyElement>(props.size());
        for (Map.Entry<String, BeanPropertyData> entry : props.entrySet()) {
            String propertyName = entry.getKey();
            BeanPropertyData value = entry.getValue();
            applyIntrospectedPropertyAccess(value);
            if (shouldCheckSetterTypeCompatibility(configuration, value) && value.setter != null && value.getter != null) {
                // ensure types match
                ClassElement getterType = value.getter.getGenericReturnType();
                ClassElement setterType = value.setter.getParameters()[0].getGenericType();
                if (isIncompatibleSetterType(setterType, getterType)) {
                    // getter and setter don't match, remove setter
                    value.setter = null;
                    value.type = getterType;
                }
            }
            // Define the property type based on its writer element
            if (value.writeAccessKind == BeanProperties.AccessKind.FIELD && !value.field.getType().equals(value.type)) {
                value.type = value.field.getGenericType();
            } else if (value.writeAccessKind == BeanProperties.AccessKind.METHOD
                && value.setter != null
                && value.setter.getParameters().length > 0) {
                value.type = value.setter.getParameters()[0].getGenericType();
            } else if (value.readAccessKind == BeanProperties.AccessKind.FIELD && !value.field.getType().equals(value.type)) {
                value.type = value.field.getGenericType();
            }
            if (value.readAccessKind == BeanProperties.AccessKind.METHOD
                && value.getter != null
                && !value.getter.getGenericReturnType().equals(value.type)
                && value.writeAccessKind == null) {
                value.type = value.getter.getGenericReturnType();
            }
            // In a case when the field's type is the same as the selected property type,
            // and it has more type arguments annotations - use it as the property type
            if (value.field != null
                && value.field.getType().equals(value.type)
                && hasMoreAnnotations(value.field.getType(), value.type)) {
                value.type = value.field.getGenericType();
            }
            // In a case when the getter's type is the same as the selected property type,
            // and it has more type arguments annotations - use it as the property type
            if (value.getter != null
                && value.getter.getGenericReturnType().equals(value.type)
                && hasMoreAnnotations(value.getter.getGenericReturnType(), value.type)) {
                value.type = value.getter.getGenericReturnType();
            }
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

    private static boolean isIntrospectedPropertyReader(MethodElement methodElement) {
        return methodElement.hasAnnotation(ANN_INTROSPECTED_PROPERTY) &&
            methodElement.getParameters().length == 0 &&
            !methodElement.getReturnType().isVoid();
    }

    private static boolean isIntrospectedPropertyWriter(MethodElement methodElement) {
        return methodElement.hasAnnotation(ANN_INTROSPECTED_PROPERTY) &&
            (methodElement.getParameters().length == 1 ||
                (methodElement.getParameters().length == 0 && methodElement.getReturnType().isVoid()));
    }

    private static boolean isReaderName(PropertyElementQuery configuration,
                                        MethodElement methodElement,
                                        String methodName,
                                        String[] readPrefixes) {
        return NameUtils.isReaderName(methodName, readPrefixes) ||
            (configuration.isJsonAutoDetectConfigured() && configuration.isJsonAutoDetectReaderName(methodElement, methodName));
    }

    private static String getPropertyNameForGetter(String methodName, String[] readPrefixes) {
        if (NameUtils.isReaderName(methodName, readPrefixes)) {
            return NameUtils.getPropertyNameForGetter(methodName, readPrefixes);
        }
        return NameUtils.getPropertyNameForGetter(methodName);
    }

    private static boolean isWriterName(PropertyElementQuery configuration, String methodName, String[] writePrefixes) {
        return NameUtils.isWriterName(methodName, writePrefixes) ||
            (configuration.isJsonAutoDetectConfigured() && configuration.isJsonAutoDetectWriterName(methodName));
    }

    private static String getPropertyNameForSetter(String methodName, String[] writePrefixes) {
        if (NameUtils.isWriterName(methodName, writePrefixes)) {
            return NameUtils.getPropertyNameForSetter(methodName, writePrefixes);
        }
        return NameUtils.getPropertyNameForSetter(methodName);
    }

    private static boolean canMethodBeUsedForRead(MethodElement methodElement,
                                                  String methodName,
                                                  Set<BeanProperties.AccessKind> accessKinds,
                                                  BeanProperties.Visibility visibility,
                                                  PropertyElementQuery configuration) {
        if (configuration.isJsonAutoDetectConfigured()) {
            return configuration.isJsonAutoDetectGetterVisible(methodElement, methodName);
        }
        return canMethodBeUsedForAccess(methodElement, accessKinds, visibility);
    }

    private static boolean canMethodBeUsedForWriteAccess(MethodElement methodElement,
                                                         Set<BeanProperties.AccessKind> accessKinds,
                                                         BeanProperties.Visibility visibility,
                                                         PropertyElementQuery configuration) {
        if (configuration.isJsonAutoDetectConfigured()) {
            return configuration.isJsonAutoDetectSetterVisible(methodElement);
        }
        return canMethodBeUsedForAccess(methodElement, accessKinds, visibility);
    }

    private static boolean canMethodBeUsedForWrite(MethodElement methodElement, PropertyElementQuery configuration) {
        int parameterCount = methodElement.getParameters().length;
        return parameterCount == 1 ||
            (configuration.isAllowSetterWithZeroArgs() && parameterCount == 0) ||
            (configuration.isAllowSetterWithMultipleArgs() && parameterCount > 1) ||
            isIntrospectedPropertyWriter(methodElement);
    }

    private static boolean isIntrospectedPropertyField(FieldElement fieldElement, BeanProperties.Visibility visibility) {
        return fieldElement.hasAnnotation(ANN_INTROSPECTED_PROPERTY) && isAccessible(fieldElement, visibility);
    }

    private static boolean isIntrospectedPropertyMethod(MethodElement methodElement, BeanProperties.Visibility visibility) {
        return methodElement.hasAnnotation(ANN_INTROSPECTED_PROPERTY) && isAccessible(methodElement, visibility);
    }

    private static boolean ignoresOtherAccessors(MemberElement memberElement) {
        return memberElement.hasAnnotation(ANN_INTROSPECTED_PROPERTY) &&
            memberElement.booleanValue(ANN_INTROSPECTED_PROPERTY, MEMBER_IGNORE_OTHER_ACCESSORS).orElse(false);
    }

    private static void registerIntrospectedPropertyAccess(BeanPropertyData beanPropertyData, MemberElement memberElement) {
        if (memberElement.hasAnnotation(ANN_INTROSPECTED_PROPERTY)) {
            EnumSet<Introspected.Property.Access> accessKinds = resolveIntrospectedPropertyAccess(memberElement);
            if (beanPropertyData.propertyAccessKinds == null) {
                beanPropertyData.propertyAccessKinds = accessKinds;
                beanPropertyData.propertyAccessMember = memberElement;
            } else if (!beanPropertyData.propertyAccessKinds.equals(accessKinds)) {
                throw new ProcessingException(
                    memberElement,
                    "Conflicting @Introspected.Property accessKind declarations for property ["
                        + beanPropertyData.propertyName + "]: "
                        + beanPropertyData.propertyAccessKinds + " declared by ["
                        + beanPropertyData.propertyAccessMember.getDescription()
                        + "] and " + accessKinds + " declared by ["
                        + memberElement.getDescription() + "]"
                );
            }
        }
    }

    private static EnumSet<Introspected.Property.Access> resolveIntrospectedPropertyAccess(MemberElement memberElement) {
        Introspected.Property.Access[] accessKinds = memberElement.enumValues(
            ANN_INTROSPECTED_PROPERTY,
            "accessKind",
            Introspected.Property.Access.class
        );
        if (accessKinds.length == 0) {
            return EnumSet.of(Introspected.Property.Access.READ, Introspected.Property.Access.WRITE);
        }
        EnumSet<Introspected.Property.Access> access = EnumSet.noneOf(Introspected.Property.Access.class);
        for (Introspected.Property.Access accessKind : accessKinds) {
            access.add(accessKind);
        }
        return access;
    }

    private static void applyIntrospectedPropertyAccess(BeanPropertyData beanPropertyData) {
        if (beanPropertyData.propertyAccessKinds == null) {
            return;
        }
        if (!beanPropertyData.propertyAccessKinds.contains(Introspected.Property.Access.READ)) {
            beanPropertyData.getter = null;
            beanPropertyData.readAccessKind = null;
        }
        if (!beanPropertyData.propertyAccessKinds.contains(Introspected.Property.Access.WRITE)) {
            beanPropertyData.setter = null;
            beanPropertyData.writeAccessKind = null;
        }
    }

    private static boolean hasMoreAnnotations(ClassElement c1, ClassElement c2) {
        return countGenericTypeAnnotations(c1) > countGenericTypeAnnotations(c2.getType())
            || c1.getTypeAnnotationMetadata().getAnnotationNames().size() > c2.getTypeAnnotationMetadata().getAnnotationNames().size();
    }

    private static boolean isFieldInRole(FieldElement fieldElement) {
        return fieldElement.hasDeclaredAnnotation(AnnotationUtil.INJECT)
            || fieldElement.hasStereotype(Value.class)
            || fieldElement.hasStereotype(Property.class);
    }

    private static boolean isMethodInRole(MethodElement methodElement) {
        return methodElement.hasDeclaredAnnotation(AnnotationUtil.INJECT)
            || methodElement.hasDeclaredAnnotation(AnnotationUtil.PRE_DESTROY)
            || methodElement.hasDeclaredAnnotation(AnnotationUtil.POST_CONSTRUCT);
    }

    private static int countGenericTypeAnnotations(ClassElement cl) {
        return cl.getTypeArguments().values().stream().mapToInt(t -> t.getAnnotationMetadata().getAnnotationNames().size()).sum();
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

    private static boolean isExcludedByAnnotations(PropertyElementQuery conf, BeanPropertyData value) {
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

    private static void processGetter(Map<String, BeanPropertyData> props, MethodElement methodElement, String propertyName, boolean isAccessor, PropertyElementQuery configuration) {
        BeanPropertyData beanPropertyData = props.computeIfAbsent(propertyName, BeanPropertyData::new);
        beanPropertyData.getter = methodElement;
        if (isAccessor) {
            beanPropertyData.readAccessKind = BeanProperties.AccessKind.METHOD;
        }
        ClassElement genericReturnType = beanPropertyData.getter.getGenericReturnType();
        ClassElement getterType = unwrapType(genericReturnType);
        if (shouldCheckSetterTypeCompatibility(configuration, beanPropertyData) && beanPropertyData.type != null) {
            if (!getterType.isAssignable(unwrapType(beanPropertyData.type))) {
                beanPropertyData.getter = null; // not a compatible getter
                beanPropertyData.readAccessKind = null;
            }
        } else {
            beanPropertyData.type = genericReturnType;
        }
        registerIntrospectedPropertyAccess(beanPropertyData, methodElement);
    }

    private static void processSetter(ClassElement classElement, Map<String, BeanPropertyData> props, MethodElement methodElement, String propertyName, boolean isAccessor, PropertyElementQuery configuration) {
        BeanPropertyData beanPropertyData = props.computeIfAbsent(propertyName, BeanPropertyData::new);
        registerIntrospectedPropertyAccess(beanPropertyData, methodElement);
        ClassElement paramType = methodElement.getParameters().length == 0 ? PrimitiveElement.BOOLEAN : methodElement.getParameters()[0].getGenericType();
        ClassElement setterType = unwrapType(paramType);
        ClassElement existingType = beanPropertyData.type != null ? unwrapType(beanPropertyData.type) : null;
        if (setterType != null && beanPropertyData.setter != null) {
            if (existingType != null && setterType.isAssignable(existingType)) {
                // Override the setter because the type is higher
                beanPropertyData.setter = methodElement;
            } else if (beanPropertyData.setter.getDeclaringType().equals(methodElement.getDeclaringType())) {
                // the same declared type; skip - take the first setter
                return;
            } else if (classElement.isAssignable(beanPropertyData.setter.getDeclaringType())) {
                // override must be a subclass
                beanPropertyData.setter = methodElement;
            } else {
                return;
            }
        } else {
            beanPropertyData.setter = methodElement;
        }
        if (isAccessor) {
            beanPropertyData.writeAccessKind = BeanProperties.AccessKind.METHOD;
        }
        if (shouldCheckSetterTypeCompatibility(configuration, beanPropertyData) && beanPropertyData.type != null) {
            if (existingType != null && isIncompatibleSetterType(setterType, existingType)) {
                beanPropertyData.setter = null; // not a compatible setter
                beanPropertyData.writeAccessKind = null;
            }
        } else {
            beanPropertyData.type = paramType;
        }
    }

    private static boolean isIncompatibleSetterType(ClassElement setterType, ClassElement existingType) {
        return setterType != null && !existingType.isAssignable(setterType) && !setterType.getName().equals(existingType.getName());
    }

    private static boolean shouldCheckSetterTypeCompatibility(PropertyElementQuery configuration, BeanPropertyData beanPropertyData) {
        return configuration.isIgnoreSettersWithDifferingType() && !hasIntrospectedPropertyAccess(beanPropertyData);
    }

    private static boolean hasIntrospectedPropertyAccess(BeanPropertyData beanPropertyData) {
        return hasIntrospectedPropertyAccess(beanPropertyData.getter) ||
            hasIntrospectedPropertyAccess(beanPropertyData.setter) ||
            hasIntrospectedPropertyAccess(beanPropertyData.field);
    }

    private static boolean hasIntrospectedPropertyAccess(@Nullable MemberElement memberElement) {
        return memberElement != null && memberElement.hasAnnotation(ANN_INTROSPECTED_PROPERTY);
    }

    private static ClassElement unwrapType(ClassElement type) {
        if (type.isOptional()) {
            return type.getOptionalValueType().orElse(type);
        }
        return type;
    }

    private static void resolveWriteAccessForField(FieldElement fieldElement,
                                                   boolean isAccessor,
                                                   BeanPropertyData beanPropertyData,
                                                   boolean ignoreOtherAccessors) {
        if (fieldElement.isFinal()) {
            return;
        }
        if (ignoreOtherAccessors) {
            beanPropertyData.field = fieldElement;
            beanPropertyData.writeAccessKind = BeanProperties.AccessKind.FIELD;
            beanPropertyData.type = fieldElement.getGenericType();
            return;
        }
        ClassElement fieldType = unwrapType(fieldElement.getGenericType());
        if (beanPropertyData.type == null || fieldType.isAssignable(unwrapType(beanPropertyData.type))) {
            beanPropertyData.field = fieldElement;
        } else {
            isAccessor = false; // not compatible field or setter is present
        }
        if (beanPropertyData.writeAccessKind == null && isAccessor) {
            // Use the field for write
            beanPropertyData.writeAccessKind = BeanProperties.AccessKind.FIELD;
        }
        if (beanPropertyData.type == null) {
            beanPropertyData.type = fieldElement.getGenericType();
        }
    }

    private static void resolveReadAccessForField(FieldElement fieldElement,
                                                  boolean isAccessor,
                                                  BeanPropertyData beanPropertyData,
                                                  boolean ignoreOtherAccessors) {
        if (ignoreOtherAccessors) {
            beanPropertyData.field = fieldElement;
            beanPropertyData.readAccessKind = BeanProperties.AccessKind.FIELD;
            beanPropertyData.type = fieldElement.getGenericType();
            return;
        }
        ClassElement fieldType = unwrapType(fieldElement.getGenericType());
        if (beanPropertyData.type == null || fieldType.isAssignable(unwrapType(beanPropertyData.type))) {
            beanPropertyData.field = fieldElement;
        }  else {
            isAccessor = false; // not compatible field or getter is present
        }
        if (beanPropertyData.readAccessKind == null && isAccessor) {
            // Use the field for read
            beanPropertyData.readAccessKind = BeanProperties.AccessKind.FIELD;
        }
        if (beanPropertyData.type == null) {
            beanPropertyData.type = fieldElement.getGenericType();
        }
    }

    private static boolean canFieldBeUsedForAccess(FieldElement fieldElement,
                                                   Set<BeanProperties.AccessKind> accessKinds,
                                                   BeanProperties.Visibility visibility,
                                                   PropertyElementQuery configuration) {
        if (fieldElement.getOwningType().isRecord()) {
            return false;
        }
        if (configuration.isJsonAutoDetectConfigured()) {
            return configuration.isJsonAutoDetectFieldVisible(fieldElement);
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
        return switch (visibility) {
            case DEFAULT ->
                !memberElement.isPrivate() && (memberElement.isAccessible() || memberElement.getDeclaringType().hasDeclaredStereotype(BeanProperties.class));
            case PUBLIC -> memberElement.isPublic();
            case ANY -> true;
        };
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
    public static final class BeanPropertyData {
        public ClassElement type;
        public MethodElement getter;
        public MethodElement setter;
        public FieldElement field;
        public BeanProperties.AccessKind readAccessKind;
        public BeanProperties.AccessKind writeAccessKind;
        public final String propertyName;
        public boolean isExcluded;
        public EnumSet<Introspected.Property.Access> propertyAccessKinds;
        public MemberElement propertyAccessMember;

        public BeanPropertyData(String propertyName) {
            this.propertyName = propertyName;
        }
    }

}
