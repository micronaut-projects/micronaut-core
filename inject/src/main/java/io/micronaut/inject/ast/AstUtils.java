package io.micronaut.inject.ast;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.Property;
import io.micronaut.context.annotation.Value;
import io.micronaut.core.annotation.AccessorsStyle;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationUtil;
import io.micronaut.core.annotation.BeanProperties;
import io.micronaut.core.naming.NameUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class AstUtils {

    public static List<PropertyElement> resolveBeanProperties(ClassElement classElement,
                                                              Supplier<List<MethodElement>> methodsSupplier,
                                                              Supplier<List<FieldElement>> fieldSupplier,
                                                              boolean excludeMethodsInRole,
                                                              Function<MethodElement, Optional<String>> customMethodNameResolver,
                                                              Function<BeanPropertyData, PropertyElement> propertyCreator) {
        AnnotationMetadata annotationMetadata = classElement.getAnnotationMetadata();
        BeanProperties.Visibility visibility = annotationMetadata.enumValue(BeanProperties.class, BeanProperties.VISIBILITY, BeanProperties.Visibility.class)
            .orElse(BeanProperties.Visibility.DEFAULT);
        EnumSet<BeanProperties.AccessKind> accessKinds = annotationMetadata.isPresent(BeanProperties.class, BeanProperties.ACCESS_KIND) ?
            annotationMetadata.enumValuesSet(BeanProperties.class, BeanProperties.ACCESS_KIND, BeanProperties.AccessKind.class) : EnumSet.of(BeanProperties.AccessKind.METHOD);

        Set<String> includes = new HashSet<>(Arrays.asList(annotationMetadata.stringValues(BeanProperties.class, BeanProperties.INCLUDES)));
        Set<String> excludes = new HashSet<>(Arrays.asList(annotationMetadata.stringValues(BeanProperties.class, BeanProperties.EXCLUDES)));
        // TODO: investigate why aliases aren't propagated
        includes.addAll(Arrays.asList(annotationMetadata.stringValues(ConfigurationProperties.class, BeanProperties.INCLUDES)));
        excludes.addAll(Arrays.asList(annotationMetadata.stringValues(ConfigurationProperties.class, BeanProperties.EXCLUDES)));
        String[] readPrefixes = classElement.getValue(AccessorsStyle.class, "readPrefixes", String[].class)
            .orElse(new String[]{AccessorsStyle.DEFAULT_READ_PREFIX});
        String[] writePrefixes = classElement.getValue(AccessorsStyle.class, "writePrefixes", String[].class)
            .orElse(new String[]{AccessorsStyle.DEFAULT_WRITE_PREFIX});

        Map<String, BeanPropertyData> props = new LinkedHashMap<>();
        if (accessKinds.contains(BeanProperties.AccessKind.METHOD)) {
            for (MethodElement methodElement : methodsSupplier.get()) {
                // Records include everything
                if (methodElement.isStatic()
                    || !excludeMethodsInRole && (methodElement.hasDeclaredAnnotation(AnnotationUtil.INJECT)
                                        || methodElement.hasDeclaredAnnotation(AnnotationUtil.PRE_DESTROY)
                                        || methodElement.hasDeclaredAnnotation(AnnotationUtil.POST_CONSTRUCT))
                ) {
                    continue;
                }
                String methodName = methodElement.getName();
                if (methodName.contains("$") || methodName.equals("getMetaClass")) {
                    continue;
                }
                if (visibility == BeanProperties.Visibility.DEFAULT) {
                    if (methodElement.isPrivate() || !methodElement.isAccessible() && !methodElement.getDeclaringType().hasDeclaredStereotype(BeanProperties.class)) {
                        continue;
                    }
                } else if (visibility == BeanProperties.Visibility.PUBLIC && !methodElement.isPublic()) {
                    continue;
                }
                if (classElement.isRecord()) {
                    String propertyName = methodElement.getSimpleName();
                    BeanPropertyData beanPropertyData = props.computeIfAbsent(propertyName, BeanPropertyData::new);
                    beanPropertyData.getter = methodElement;
                    beanPropertyData.readAccessKind = BeanProperties.AccessKind.METHOD;
                    ClassElement getterType = beanPropertyData.getter.getGenericReturnType();
                    if (getterType.isOptional()) {
                        getterType = getterType.getFirstTypeArgument().orElse(getterType);
                    }
                    beanPropertyData.type = getterType;
                } else if (NameUtils.isReaderName(methodName, readPrefixes) && methodElement.getParameters().length == 0) {
                    String propertyName = customMethodNameResolver.apply(methodElement).orElseGet(() -> NameUtils.getPropertyNameForGetter(methodName, readPrefixes));
                    BeanPropertyData beanPropertyData = props.computeIfAbsent(propertyName, BeanPropertyData::new);
                    beanPropertyData.getter = methodElement;
                    beanPropertyData.readAccessKind = BeanProperties.AccessKind.METHOD;
                    ClassElement getterType = beanPropertyData.getter.getGenericReturnType();
                    if (getterType.isOptional()) {
                        getterType = getterType.getFirstTypeArgument().orElse(getterType);
                    }
                    if (beanPropertyData.type != null) {
                        if (!getterType.isAssignable(beanPropertyData.type)) {
                            beanPropertyData.getter = null; // not a compatible getter
                            beanPropertyData.readAccessKind = null;
                        }
                    } else {
                        beanPropertyData.type = getterType;
                    }
                } else if (NameUtils.isWriterName(methodName, writePrefixes) && methodElement.getParameters().length == 1) {
                    String propertyName = NameUtils.getPropertyNameForSetter(methodName, writePrefixes);
                    ClassElement setterType = methodElement.getParameters()[0].getType();
                    BeanPropertyData beanPropertyData = props.computeIfAbsent(propertyName, BeanPropertyData::new);
                    if (beanPropertyData.setter != null) {
                        if (setterType.isAssignable(beanPropertyData.type)) {
                            // Override the setter because the type is higher
                            beanPropertyData.setter = methodElement;
                        }
                        continue;
                    }
                    beanPropertyData.setter = methodElement;
                    beanPropertyData.writeAccessKind = BeanProperties.AccessKind.METHOD;
                    if (beanPropertyData.type != null) {
                        if (!setterType.isAssignable(beanPropertyData.type)) {
                            beanPropertyData.setter = null; // not a compatible setter
                            beanPropertyData.writeAccessKind = null;
                        }
                    } else {
                        beanPropertyData.type = setterType;
                    }
                }
            }
        }
        if (!classElement.isRecord() && accessKinds.contains(BeanProperties.AccessKind.FIELD)) {
            for (FieldElement fieldElement : getSubtypeFirstFields(classElement)) {
                if (fieldElement.isStatic()
                    || fieldElement.hasDeclaredAnnotation(AnnotationUtil.INJECT)
                    || fieldElement.hasStereotype(Value.class)
                    || fieldElement.hasStereotype(Property.class)
                ) {
                    continue;
                }
                if (visibility == BeanProperties.Visibility.DEFAULT) {
                    if (fieldElement.isPrivate() || !fieldElement.isAccessible() && !fieldElement.getDeclaringType().hasDeclaredStereotype(BeanProperties.class)) {
                        continue;
                    }
                } else if (visibility == BeanProperties.Visibility.PUBLIC && !fieldElement.isPublic()) {
                    continue;
                }
                String propertyName = fieldElement.getSimpleName();
                BeanPropertyData beanPropertyData = props.computeIfAbsent(propertyName, BeanPropertyData::new);
                ClassElement fieldType = fieldElement.getGenericType();
                if (fieldType.isOptional()) {
                    fieldType = fieldType.getFirstTypeArgument().orElse(fieldType);
                }
                if (beanPropertyData.getter == null) {
                    if (beanPropertyData.type != null) {
                        if (fieldType.isAssignable(beanPropertyData.type)) {
                            beanPropertyData.field = fieldElement;
                            beanPropertyData.readAccessKind = BeanProperties.AccessKind.FIELD;
                        }
                    } else {
                        beanPropertyData.type = fieldType;
                        beanPropertyData.field = fieldElement;
                        beanPropertyData.readAccessKind = BeanProperties.AccessKind.FIELD;
                    }
                }
                if (!fieldElement.isFinal() && beanPropertyData.setter == null) {
                    if (beanPropertyData.type != null) {
                        if (fieldType.isAssignable(beanPropertyData.type)) {
                            beanPropertyData.field = fieldElement;
                            beanPropertyData.writeAccessKind = BeanProperties.AccessKind.FIELD;
                        }
                    } else {
                        beanPropertyData.type = fieldType;
                        beanPropertyData.field = fieldElement;
                        beanPropertyData.writeAccessKind = BeanProperties.AccessKind.FIELD;
                    }
                }
            }
        }
        if (!props.isEmpty()) {
            List<PropertyElement> beanProperties = new ArrayList<>(props.size());
            Map<String, FieldElement> fields = fieldSupplier.get()
                .stream()
                .filter(f -> !f.isStatic() && !f.hasDeclaredAnnotation(AnnotationUtil.INJECT))
                .collect(Collectors.toMap(io.micronaut.inject.ast.Element::getName, f -> f));
            for (Map.Entry<String, BeanPropertyData> entry : props.entrySet()) {
                String propertyName = entry.getKey();
                BeanPropertyData value = entry.getValue();
                if (value.field == null && (value.getter != null || value.setter != null)) {
                    // Find private setter for getter or setter to include the metadata
                    value.field = fields.get(propertyName);
                }
                if (value.getter != null || value.field != null || value.setter != null) {
                    value.isExcluded = shouldExclude(includes, excludes, propertyName);
                    beanProperties.add(propertyCreator.apply(value));
                }
            }
            return Collections.unmodifiableList(beanProperties);
        }
        return Collections.emptyList();
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
