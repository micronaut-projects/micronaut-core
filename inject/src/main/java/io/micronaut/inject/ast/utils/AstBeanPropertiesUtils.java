package io.micronaut.inject.ast.utils;

import io.micronaut.context.annotation.Property;
import io.micronaut.context.annotation.Value;
import io.micronaut.core.annotation.AnnotationUtil;
import io.micronaut.core.annotation.BeanProperties;
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
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

public class AstBeanPropertiesUtils {

    public static List<PropertyElement> resolveBeanProperties(BeanPropertiesConfiguration configuration,
                                                              ClassElement classElement,
                                                              Supplier<List<MethodElement>> methodsSupplier,
                                                              Supplier<List<FieldElement>> fieldSupplier,
                                                              boolean excludeElementsInRole,
                                                              Set<String> propertyFields,
                                                              Function<MethodElement, Optional<String>> customMethodNameResolver,
                                                              Function<BeanPropertyData, PropertyElement> propertyCreator) {
        BeanProperties.Visibility visibility = configuration.getVisibility();
        EnumSet<BeanProperties.AccessKind> accessKinds = configuration.getAccessKinds();

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
                String propertyName = customMethodNameResolver.apply(methodElement).orElseGet(() -> NameUtils.getPropertyNameForGetter(methodName, readPrefixes));
                processGetter(props, methodElement, propertyName, isAccessor);
            } else if (NameUtils.isWriterName(methodName, writePrefixes)
                && (methodElement.getParameters().length == 1
                || configuration.isAllowSetterWithZeroArgs() && methodElement.getParameters().length == 0
                || configuration.isAllowSetterWithMultipleArgs() && methodElement.getParameters().length > 1)) {
                String propertyName = NameUtils.getPropertyNameForSetter(methodName, writePrefixes);
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
            ClassElement fieldType = processType(fieldElement.getGenericType());
            resolveReadAccessForField(fieldElement, isAccessor, beanPropertyData, fieldType);
            resolveWriteAccessForField(fieldElement, isAccessor, beanPropertyData, fieldType);
        }
        if (!props.isEmpty()) {
            List<PropertyElement> beanProperties = new ArrayList<>(props.size());
            for (Map.Entry<String, BeanPropertyData> entry : props.entrySet()) {
                String propertyName = entry.getKey();
                BeanPropertyData value = entry.getValue();
                if (value.readAccessKind != null || value.writeAccessKind != null) {
                    value.isExcluded = shouldExclude(includes, excludes, propertyName);
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

    private static void processRecord(Map<String, BeanPropertyData> props, MethodElement methodElement, String propertyName) {
        BeanPropertyData beanPropertyData = props.computeIfAbsent(propertyName, BeanPropertyData::new);
        beanPropertyData.getter = methodElement;
        beanPropertyData.readAccessKind = BeanProperties.AccessKind.METHOD;
        beanPropertyData.type = processType(beanPropertyData.getter.getGenericReturnType());
    }

    private static void processGetter(Map<String, BeanPropertyData> props, MethodElement methodElement, String propertyName, boolean isAccessor) {
        BeanPropertyData beanPropertyData = props.computeIfAbsent(propertyName, BeanPropertyData::new);
        beanPropertyData.getter = methodElement;
        if (isAccessor) {
            beanPropertyData.readAccessKind = BeanProperties.AccessKind.METHOD;
        }
        ClassElement genericReturnType = beanPropertyData.getter.getGenericReturnType();
        ClassElement getterType = processType(genericReturnType);
        if (beanPropertyData.type != null) {
            if (!getterType.isAssignable(beanPropertyData.type)) {
                beanPropertyData.getter = null; // not a compatible getter
                beanPropertyData.readAccessKind = null;
            }
        } else {
            beanPropertyData.type = getterType;
        }
    }

    private static void processSetter(Map<String, BeanPropertyData> props, MethodElement methodElement, String propertyName, boolean isAccessor) {
        BeanPropertyData beanPropertyData = props.computeIfAbsent(propertyName, BeanPropertyData::new);
        ClassElement setterType = methodElement.getParameters().length == 0 ? null : processType(methodElement.getParameters()[0].getGenericType());
        if (setterType != null && beanPropertyData.setter != null) {
            if (setterType.isAssignable(beanPropertyData.type)) {
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
            if (setterType != null && !setterType.isAssignable(beanPropertyData.type)) {
                beanPropertyData.setter = null; // not a compatible setter
                beanPropertyData.writeAccessKind = null;
            }
        } else {
            beanPropertyData.type = setterType;
        }
    }

    private static ClassElement processType(ClassElement type) {
        if (type.isOptional()) {
            return type.getFirstTypeArgument().orElse(type);
        }
        return type;
    }

    private static void resolveWriteAccessForField(FieldElement fieldElement, boolean isAccessor, BeanPropertyData beanPropertyData, ClassElement fieldType) {
        if (fieldElement.isFinal()) {
            return;
        }
        if (beanPropertyData.setter == null) {
            if (beanPropertyData.type != null) {
                if (fieldType.isAssignable(beanPropertyData.type)) {
                    beanPropertyData.field = fieldElement;
                    if (isAccessor) {
                        beanPropertyData.writeAccessKind = BeanProperties.AccessKind.FIELD;
                    }
                }
                // Else: not compatible field
            } else {
                beanPropertyData.field = fieldElement;
                beanPropertyData.type = fieldType;
                if (isAccessor) {
                    beanPropertyData.writeAccessKind = BeanProperties.AccessKind.FIELD;
                }
            }
        } else {
            beanPropertyData.field = fieldElement;
        }
    }

    private static void resolveReadAccessForField(FieldElement fieldElement, boolean isAccessor, BeanPropertyData beanPropertyData, ClassElement fieldType) {
        if (beanPropertyData.getter == null) {
            if (beanPropertyData.type != null) {
                if (fieldType.isAssignable(beanPropertyData.type)) {
                    beanPropertyData.field = fieldElement;
                    if (isAccessor) {
                        beanPropertyData.readAccessKind = BeanProperties.AccessKind.FIELD;
                    }
                }
                // Else: not compatible field
            } else {
                beanPropertyData.field = fieldElement;
                beanPropertyData.type = fieldType;
                if (isAccessor) {
                    beanPropertyData.readAccessKind = BeanProperties.AccessKind.FIELD;
                }
            }
        } else {
            beanPropertyData.field = fieldElement;
        }
    }

    private static boolean canFieldBeUsedForAccess(FieldElement fieldElement,
                                                   EnumSet<BeanProperties.AccessKind> accessKinds,
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
                                                    EnumSet<BeanProperties.AccessKind> accessKinds,
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
