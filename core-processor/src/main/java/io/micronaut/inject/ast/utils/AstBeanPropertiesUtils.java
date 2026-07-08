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
import io.micronaut.inject.ast.ParameterElement;
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

        Set<String> includes = configuration.getIncludes();
        Set<String> excludes = configuration.getExcludes();
        String[] readPrefixes = configuration.getReadPrefixes();
        String[] writePrefixes = configuration.getWritePrefixes();
        var isRecord = classElement.isRecord();
        Set<BeanProperties.AccessKind> effectiveAccessKinds = configuration.getAccessKinds();

        var props = new LinkedHashMap<String, BeanPropertyData>();
        for (MethodElement methodElement : methodsSupplier.get()) {
            // Records include everything
            boolean isIntrospectedPropertyMethod = isIntrospectedPropertyMethod(methodElement);
            boolean isExcludedMethod = (methodElement.isStatic() && !configuration.isAllowStaticProperties()) ||
                (!excludeElementsInRole && isMethodInRole(methodElement));
            if (isExcludedMethod) {
                if (isIntrospectedPropertyMethod) {
                    failInvalidIntrospectedProperty(
                        methodElement,
                        "the method is excluded from bean property resolution"
                    );
                }
                continue;
            }
            if (isIntrospectedPropertyMethod && !isAccessible(methodElement, visibility)) {
                failInvalidIntrospectedProperty(
                    methodElement,
                    "the method is not accessible for visibility [" + visibility + "]"
                );
            }
            String methodName = methodElement.getName();
            if (methodName.equals("getMetaClass")) {
                continue;
            }
            if (isRecord) {
                boolean isAccessor = canMethodBeUsedForAccess(methodElement, effectiveAccessKinds, visibility) ||
                    isIntrospectedPropertyMethod;
                if (!isAccessor) {
                    continue;
                }
                String propertyName = methodElement.getSimpleName();
                processRecord(props, methodElement, propertyName);
                if (isIntrospectedPropertyMethod) {
                    validateProcessedIntrospectedPropertyMethod(classElement, methodElement, visibility, props.get(propertyName));
                }
            } else if (isReaderName(configuration, methodElement, methodName, readPrefixes)
                && methodElement.getParameters().length == 0) {
                String propertyName = customReaderPropertyNameResolver.apply(methodElement)
                    .orElseGet(() -> getPropertyNameForGetter(methodName, readPrefixes));
                boolean isAccessor = canMethodBeUsedForRead(methodElement, methodName, effectiveAccessKinds, visibility, configuration) ||
                    isIntrospectedPropertyMethod;
                processGetter(
                    props,
                    methodElement,
                    propertyName,
                    isAccessor,
                    configuration,
                    ignoresOtherAccessors(methodElement)
                );
                if (isIntrospectedPropertyMethod) {
                    validateProcessedIntrospectedPropertyMethod(classElement, methodElement, visibility, props.get(propertyName));
                }
            } else if (isWriterName(configuration, methodName, writePrefixes)
                && canMethodBeUsedForWrite(methodElement, configuration, visibility)) {
                String propertyName = customWriterPropertyNameResolver.apply(methodElement)
                    .orElseGet(() -> getPropertyNameForSetter(methodName, writePrefixes));
                boolean isAccessor = canMethodBeUsedForWriteAccess(methodElement, effectiveAccessKinds, visibility, configuration) ||
                    isIntrospectedPropertyMethod;
                processSetter(
                    classElement,
                    props,
                    methodElement,
                    propertyName,
                    isAccessor,
                    configuration,
                    ignoresOtherAccessors(methodElement)
                );
                if (isIntrospectedPropertyMethod) {
                    validateProcessedIntrospectedPropertyMethod(classElement, methodElement, visibility, props.get(propertyName));
                }
            } else if (isIntrospectedPropertyReader(methodElement, visibility)) {
                processGetter(
                    props,
                    methodElement,
                    methodName,
                    isIntrospectedPropertyMethod,
                    configuration,
                    ignoresOtherAccessors(methodElement)
                );
                validateProcessedIntrospectedPropertyMethod(classElement, methodElement, visibility, props.get(methodName));
            } else if (isIntrospectedPropertyWriter(methodElement, visibility)) {
                processSetter(
                    classElement,
                    props,
                    methodElement,
                    methodName,
                    isIntrospectedPropertyMethod,
                    configuration,
                    ignoresOtherAccessors(methodElement)
                );
                validateProcessedIntrospectedPropertyMethod(classElement, methodElement, visibility, props.get(methodName));
            } else if (isIntrospectedPropertyMethod) {
                validateIntrospectedPropertyMethod(methodElement, visibility, false);
            }
        }
        for (FieldElement fieldElement : fieldSupplier.get()) {
            boolean isIntrospectedPropertyField = isIntrospectedPropertyField(fieldElement);
            boolean isExcludedField = (fieldElement.isStatic() && !configuration.isAllowStaticProperties()) ||
                (!excludeElementsInRole && isFieldInRole(fieldElement));
            if (isExcludedField) {
                if (isIntrospectedPropertyField) {
                    failInvalidIntrospectedProperty(
                        fieldElement,
                        "the field is excluded from bean property resolution"
                    );
                }
                continue;
            }
            String fieldPropertyName = fieldElement.getSimpleName();
            String propertyName = resolvePropertyNameForField(props, fieldPropertyName, isIntrospectedPropertyField);
            boolean isPropertyField = propertyFields.contains(fieldPropertyName);
            boolean hasConstructorWriteAccess = isIntrospectedPropertyField
                && isWriteOnlyIntrospectedProperty(fieldElement)
                && hasConstructorWriteAccess(classElement, propertyName, fieldPropertyName, fieldElement.getGenericType());
            boolean canUseFieldForAccess = canFieldBeUsedForAccess(fieldElement, effectiveAccessKinds, visibility, configuration) ||
                canNativePropertyFieldBeUsedForAccess(isPropertyField, fieldElement, effectiveAccessKinds, visibility) ||
                canIntrospectedPropertyFieldBeUsedForAccess(fieldElement, visibility);
            if (!isPropertyField && !canUseFieldForAccess && !props.containsKey(propertyName) && !hasConstructorWriteAccess) {
                if (isIntrospectedPropertyField) {
                    validateIntrospectedPropertyField(fieldElement, visibility, null, false);
                }
                continue;
            }
            BeanPropertyData beanPropertyData = props.computeIfAbsent(propertyName, BeanPropertyData::new);
            boolean ignoreOtherAccessors = ignoresOtherAccessors(fieldElement);
            boolean hasGeneratedPropertyAccessors = isPropertyField && !ignoreOtherAccessors;
            beanPropertyData.hasGeneratedPropertyAccessors |= hasGeneratedPropertyAccessors;
            resolveReadAccessForField(fieldElement, canUseFieldForAccess, beanPropertyData, ignoreOtherAccessors);
            resolveWriteAccessForField(fieldElement, canUseFieldForAccess, beanPropertyData, ignoreOtherAccessors);
            registerIntrospectedPropertyAccess(beanPropertyData, fieldElement);
            if (hasConstructorWriteAccess) {
                beanPropertyData.constructorWriteAccess = true;
            }
            if (isIntrospectedPropertyField) {
                validateIntrospectedPropertyField(fieldElement, visibility, beanPropertyData, hasGeneratedPropertyAccessors);
            }
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
            if (value.hasGeneratedPropertyAccessors || value.readAccessKind != null || value.writeAccessKind != null || value.constructorWriteAccess) {
                value.isExcluded = shouldExclude(includes, excludes, propertyName)
                    || isExcludedByAnnotations(configuration, value)
                    || (!value.hasGeneratedPropertyAccessors && isExcludedBecauseOfMissingAccess(value));

                PropertyElement propertyElement = propertyCreator.apply(value);
                if (propertyElement != null) {
                    beanProperties.add(propertyElement);
                }
            }
        }
        return beanProperties;
    }

    private static String resolvePropertyNameForField(Map<String, BeanPropertyData> props,
                                                      String fieldPropertyName,
                                                      boolean isIntrospectedPropertyField) {
        if (props.containsKey(fieldPropertyName)) {
            return fieldPropertyName;
        }
        if (fieldPropertyName.length() > 1 && fieldPropertyName.charAt(0) == '$') {
            String accessorPropertyName = "$" + Character.toUpperCase(fieldPropertyName.charAt(1)) + fieldPropertyName.substring(2);
            if (props.containsKey(accessorPropertyName)) {
                return accessorPropertyName;
            }
        }
        if (isIntrospectedPropertyField) {
            if (isBooleanAccessorPropertyFieldName(fieldPropertyName)) {
                String accessorPropertyName = NameUtils.decapitalize(fieldPropertyName.substring(2));
                if (props.containsKey(accessorPropertyName)) {
                    return accessorPropertyName;
                }
            }
            if (isAcronymPropertyFieldName(fieldPropertyName)) {
                String accessorPropertyName = Character.toUpperCase(fieldPropertyName.charAt(0)) + fieldPropertyName.substring(1);
                if (props.containsKey(accessorPropertyName)) {
                    return accessorPropertyName;
                }
            }
        }
        return fieldPropertyName;
    }

    private static boolean isBooleanAccessorPropertyFieldName(String fieldPropertyName) {
        return fieldPropertyName.length() > 2 &&
            fieldPropertyName.startsWith("is") &&
            Character.isUpperCase(fieldPropertyName.charAt(2));
    }

    private static boolean isAcronymPropertyFieldName(String fieldPropertyName) {
        return fieldPropertyName.length() > 1 &&
            Character.isLowerCase(fieldPropertyName.charAt(0)) &&
            Character.isUpperCase(fieldPropertyName.charAt(1));
    }

    private static boolean isIntrospectedPropertyReader(MethodElement methodElement, BeanProperties.Visibility visibility) {
        return methodElement.hasAnnotation(ANN_INTROSPECTED_PROPERTY) &&
            isAccessible(methodElement, visibility) &&
            canMethodProvideReadAccess(methodElement);
    }

    private static boolean isIntrospectedPropertyWriter(MethodElement methodElement, BeanProperties.Visibility visibility) {
        return methodElement.hasAnnotation(ANN_INTROSPECTED_PROPERTY) &&
            isAccessible(methodElement, visibility) &&
            canMethodProvideWriteAccess(methodElement);
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

    private static boolean canMethodBeUsedForWrite(MethodElement methodElement,
                                                   PropertyElementQuery configuration,
                                                   BeanProperties.Visibility visibility) {
        int parameterCount = methodElement.getParameters().length;
        return parameterCount == 1 ||
            (configuration.isAllowSetterWithZeroArgs() && parameterCount == 0) ||
            (configuration.isAllowSetterWithMultipleArgs() && parameterCount > 1) ||
            isIntrospectedPropertyWriter(methodElement, visibility);
    }

    private static boolean isIntrospectedPropertyField(FieldElement fieldElement) {
        return fieldElement.hasAnnotation(ANN_INTROSPECTED_PROPERTY);
    }

    private static boolean canIntrospectedPropertyFieldBeUsedForAccess(FieldElement fieldElement,
                                                                       BeanProperties.Visibility visibility) {
        return isIntrospectedPropertyField(fieldElement) &&
            isAccessible(fieldElement, visibility) &&
            !fieldElement.getOwningType().isRecord();
    }

    private static boolean canNativePropertyFieldBeUsedForAccess(boolean isPropertyField,
                                                                 FieldElement fieldElement,
                                                                 Set<BeanProperties.AccessKind> accessKinds,
                                                                 BeanProperties.Visibility visibility) {
        if (!isPropertyField || fieldElement.getOwningType().isRecord() || !accessKinds.contains(BeanProperties.AccessKind.FIELD)) {
            return false;
        }
        return switch (visibility) {
            case DEFAULT -> !fieldElement.isPrivate() || fieldElement.isPackagePrivate();
            case PUBLIC -> fieldElement.isPublic();
            case ANY -> true;
        };
    }

    private static boolean isIntrospectedPropertyMethod(MethodElement methodElement) {
        return methodElement.hasAnnotation(ANN_INTROSPECTED_PROPERTY);
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

    private static void validateProcessedIntrospectedPropertyMethod(ClassElement classElement,
                                                                    MethodElement methodElement,
                                                                    BeanProperties.Visibility visibility,
                                                                    BeanPropertyData beanPropertyData) {
        if (beanPropertyData != null && isWriteOnlyIntrospectedProperty(methodElement)) {
            beanPropertyData.constructorWriteAccess = hasConstructorWriteAccess(
                classElement,
                beanPropertyData.propertyName,
                beanPropertyData.type
            );
        }
        validateIntrospectedPropertyMethod(
            methodElement,
            visibility,
            beanPropertyData != null && beanPropertyData.constructorWriteAccess
        );
    }

    private static boolean isWriteOnlyIntrospectedProperty(MemberElement memberElement) {
        EnumSet<Introspected.Property.Access> accessKinds = resolveIntrospectedPropertyAccess(memberElement);
        return accessKinds.contains(Introspected.Property.Access.WRITE)
            && !accessKinds.contains(Introspected.Property.Access.READ);
    }

    private static boolean hasConstructorWriteAccess(ClassElement classElement,
                                                     String propertyName,
                                                     @Nullable ClassElement type) {
        if (type == null) {
            return false;
        }
        Optional<MethodElement> constructor = classElement.getPrimaryConstructor();
        if (constructor.isEmpty()) {
            return false;
        }
        for (ParameterElement parameter : constructor.get().getParameters()) {
            if (propertyName.equals(parameter.getName()) && type.getType().isAssignable(parameter.getGenericType())) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasConstructorWriteAccess(ClassElement classElement,
                                                     String propertyName,
                                                     String fieldPropertyName,
                                                     @Nullable ClassElement type) {
        return hasConstructorWriteAccess(classElement, propertyName, type) ||
            (!fieldPropertyName.equals(propertyName) && hasConstructorWriteAccess(classElement, fieldPropertyName, type));
    }

    private static void validateIntrospectedPropertyField(FieldElement fieldElement,
                                                          BeanProperties.Visibility visibility,
                                                          @Nullable BeanPropertyData beanPropertyData,
                                                          boolean hasGeneratedPropertyAccessors) {
        EnumSet<Introspected.Property.Access> accessKinds = resolveIntrospectedPropertyAccess(fieldElement);
        boolean canReadField = isAccessible(fieldElement, visibility) && !fieldElement.getOwningType().isRecord();
        boolean canWriteField = canReadField && !fieldElement.isFinal();
        boolean canRead = canReadField || hasGeneratedPropertyAccessors || hasMethodReadAccess(beanPropertyData);
        boolean canWrite = canWriteField ||
            (hasGeneratedPropertyAccessors && !fieldElement.isFinal()) ||
            hasMethodWriteAccess(beanPropertyData) ||
            hasConstructorWriteAccess(beanPropertyData);
        boolean hasReadAccess = accessKinds.contains(Introspected.Property.Access.READ);
        boolean hasWriteAccess = accessKinds.contains(Introspected.Property.Access.WRITE);
        if ((hasReadAccess && canRead) || (hasWriteAccess && canWrite)) {
            return;
        }
        if (!canReadField && !canWriteField) {
            failInvalidIntrospectedProperty(
                fieldElement,
                "the field is not accessible for visibility [" + visibility + "]"
            );
        }
        if (hasWriteAccess && fieldElement.isFinal()) {
            failInvalidIntrospectedProperty(fieldElement, "write access requires a non-final field");
        }
        failInvalidIntrospectedProperty(
            fieldElement,
            "the field does not provide any of the declared access kinds " + accessKinds
        );
    }

    private static boolean hasMethodReadAccess(@Nullable BeanPropertyData beanPropertyData) {
        return beanPropertyData != null &&
            beanPropertyData.getter != null &&
            beanPropertyData.readAccessKind == BeanProperties.AccessKind.METHOD;
    }

    private static boolean hasMethodWriteAccess(@Nullable BeanPropertyData beanPropertyData) {
        return beanPropertyData != null &&
            beanPropertyData.setter != null &&
            beanPropertyData.writeAccessKind == BeanProperties.AccessKind.METHOD;
    }

    private static boolean hasConstructorWriteAccess(@Nullable BeanPropertyData beanPropertyData) {
        return beanPropertyData != null && beanPropertyData.constructorWriteAccess;
    }

    private static void validateIntrospectedPropertyMethod(MethodElement methodElement,
                                                           BeanProperties.Visibility visibility,
                                                           boolean hasConstructorWriteAccess) {
        if (!isAccessible(methodElement, visibility)) {
            failInvalidIntrospectedProperty(
                methodElement,
                "the method is not accessible for visibility [" + visibility + "]"
            );
        }
        EnumSet<Introspected.Property.Access> accessKinds = resolveIntrospectedPropertyAccess(methodElement);
        boolean canRead = canMethodProvideReadAccess(methodElement);
        boolean canWrite = canMethodProvideWriteAccess(methodElement) || hasConstructorWriteAccess;
        boolean hasReadAccess = accessKinds.contains(Introspected.Property.Access.READ);
        boolean hasWriteAccess = accessKinds.contains(Introspected.Property.Access.WRITE);
        if ((hasReadAccess && canRead) || (hasWriteAccess && canWrite)) {
            return;
        }
        if (hasReadAccess && !hasWriteAccess) {
            failInvalidIntrospectedProperty(
                methodElement,
                "read access requires a zero-argument method with a non-void return type"
            );
        }
        if (hasWriteAccess && !hasReadAccess) {
            failInvalidIntrospectedProperty(
                methodElement,
                "write access requires a one-argument method or a zero-argument void method"
            );
        }
        failInvalidIntrospectedProperty(methodElement, "the method must be a readable or writable property accessor");
    }

    private static boolean canMethodProvideReadAccess(MethodElement methodElement) {
        return methodElement.getParameters().length == 0 && !methodElement.getReturnType().isVoid();
    }

    private static boolean canMethodProvideWriteAccess(MethodElement methodElement) {
        return methodElement.getParameters().length == 1 ||
            (methodElement.getParameters().length == 0 && methodElement.getReturnType().isVoid());
    }

    private static void failInvalidIntrospectedProperty(MemberElement memberElement, String reason) {
        throw new ProcessingException(
            memberElement,
            "Element annotated with @Introspected.Property cannot be used as an introspected property: " +
                reason
        );
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
            beanPropertyData.constructorWriteAccess = false;
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
        if (value.constructorWriteAccess) {
            return false;
        }
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
        registerIntrospectedPropertyAccess(beanPropertyData, methodElement);
    }

    private static void processGetter(Map<String, BeanPropertyData> props,
                                      MethodElement methodElement,
                                      String propertyName,
                                      boolean isAccessor,
                                      PropertyElementQuery configuration,
                                      boolean ignoreOtherAccessors) {
        BeanPropertyData beanPropertyData = props.computeIfAbsent(propertyName, BeanPropertyData::new);
        if (beanPropertyData.ignoreReadAccessors && !ignoreOtherAccessors) {
            registerIntrospectedPropertyAccess(beanPropertyData, methodElement);
            return;
        }
        beanPropertyData.getter = methodElement;
        if (isAccessor) {
            beanPropertyData.readAccessKind = BeanProperties.AccessKind.METHOD;
        }
        ClassElement genericReturnType = beanPropertyData.getter.getGenericReturnType();
        if (ignoreOtherAccessors) {
            beanPropertyData.type = genericReturnType;
            beanPropertyData.ignoreReadAccessors = true;
            registerIntrospectedPropertyAccess(beanPropertyData, methodElement);
            return;
        }
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

    private static void processSetter(ClassElement classElement,
                                      Map<String, BeanPropertyData> props,
                                      MethodElement methodElement,
                                      String propertyName,
                                      boolean isAccessor,
                                      PropertyElementQuery configuration,
                                      boolean ignoreOtherAccessors) {
        BeanPropertyData beanPropertyData = props.computeIfAbsent(propertyName, BeanPropertyData::new);
        registerIntrospectedPropertyAccess(beanPropertyData, methodElement);
        ClassElement paramType = methodElement.getParameters().length == 0
            ? PrimitiveElement.BOOLEAN
            : methodElement.getParameters()[0].getGenericType();
        if (beanPropertyData.ignoreWriteAccessors && !ignoreOtherAccessors) {
            return;
        }
        if (ignoreOtherAccessors) {
            beanPropertyData.setter = methodElement;
            if (isAccessor) {
                beanPropertyData.writeAccessKind = BeanProperties.AccessKind.METHOD;
            }
            beanPropertyData.type = paramType;
            beanPropertyData.ignoreWriteAccessors = true;
            return;
        }
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
            if (isAccessor) {
                beanPropertyData.writeAccessKind = BeanProperties.AccessKind.FIELD;
            }
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
            if (isAccessor) {
                beanPropertyData.readAccessKind = BeanProperties.AccessKind.FIELD;
            }
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
                (!memberElement.isPrivate() || memberElement.isPackagePrivate()) &&
                    (memberElement.isAccessible() || memberElement.getDeclaringType().hasDeclaredStereotype(BeanProperties.class));
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
        public boolean ignoreReadAccessors;
        public boolean ignoreWriteAccessors;
        public boolean hasGeneratedPropertyAccessors;
        public boolean constructorWriteAccess;

        public BeanPropertyData(String propertyName) {
            this.propertyName = propertyName;
        }
    }

}
