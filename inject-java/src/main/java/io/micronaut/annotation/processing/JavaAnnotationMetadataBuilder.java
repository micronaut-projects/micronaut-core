/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.annotation.processing;

import io.micronaut.core.annotation.AnnotationClassValue;
import io.micronaut.core.annotation.AnnotationUtil;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.reflect.ClassUtils;
import io.micronaut.core.reflect.ReflectionUtils;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.core.util.clhm.ConcurrentLinkedHashMap;
import io.micronaut.inject.annotation.AbstractAnnotationMetadataBuilder;
import io.micronaut.inject.annotation.AnnotatedElementValidator;
import io.micronaut.inject.processing.JavaModelUtils;
import io.micronaut.inject.visitor.VisitorContext;

import javax.annotation.processing.Messager;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.NullType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.AbstractAnnotationValueVisitor8;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import java.lang.annotation.Annotation;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A {@link io.micronaut.core.annotation.AnnotationMetadata} for builder for Java to be used at compile time.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class JavaAnnotationMetadataBuilder extends AbstractAnnotationMetadataBuilder<Element, AnnotationMirror> {

    private static final Map<ExecutableElement, List<ExecutableElement>> OVERRIDDEN_METHOD_CACHE = new ConcurrentLinkedHashMap.Builder<ExecutableElement, List<ExecutableElement>>().maximumWeightedCapacity(100).build();

    private final Elements elementUtils;
    private final Messager messager;
    private final AnnotationUtils annotationUtils;
    private final ModelUtils modelUtils;

    /**
     * Default constructor.
     *
     * @param elements        The elementUtils
     * @param messager        The messager
     * @param annotationUtils The annotation utils
     * @param modelUtils      The model utils
     */
    public JavaAnnotationMetadataBuilder(
        Elements elements,
        Messager messager,
        AnnotationUtils annotationUtils,
        ModelUtils modelUtils) {
        this.elementUtils = elements;
        this.messager = messager;
        this.annotationUtils = annotationUtils;
        this.modelUtils = modelUtils;
    }

    @Nullable
    @Override
    protected AnnotatedElementValidator getElementValidator() {
        return annotationUtils.getElementValidator();
    }

    @Override
    protected void addError(@NonNull Element originatingElement, @NonNull String error) {
        messager.printMessage(Diagnostic.Kind.ERROR, error, originatingElement);
    }

    @Override
    protected void addWarning(@NonNull Element originatingElement, @NonNull String warning) {
        messager.printMessage(Diagnostic.Kind.WARNING, warning, originatingElement);
    }

    @Override
    protected String getAnnotationMemberName(Element member) {
        return member.getSimpleName().toString();
    }

    @Nullable
    @Override
    protected String getRepeatableName(AnnotationMirror annotationMirror) {
        final Element typeElement = annotationMirror.getAnnotationType().asElement();
        return getRepeatableNameForType(typeElement);
    }

    @Nullable
    @Override
    protected String getRepeatableNameForType(Element annotationType) {
        List<? extends AnnotationMirror> mirrors = annotationType.getAnnotationMirrors();
        for (AnnotationMirror mirror : mirrors) {
            String name = mirror.getAnnotationType().toString();
            if (Repeatable.class.getName().equals(name)) {
                Map<? extends ExecutableElement, ? extends javax.lang.model.element.AnnotationValue> elementValues = mirror.getElementValues();
                for (Map.Entry<? extends ExecutableElement, ? extends javax.lang.model.element.AnnotationValue> entry : elementValues.entrySet()) {
                    if (entry.getKey().getSimpleName().toString().equals("value")) {
                        javax.lang.model.element.AnnotationValue av = entry.getValue();
                        Object value = av.getValue();
                        if (value instanceof DeclaredType) {
                            Element element = ((DeclaredType) value).asElement();
                            return JavaModelUtils.getClassName((TypeElement) element);
                        }
                    }
                }
            }
        }
        return null;
    }

    @Override
    protected Optional<Element> getAnnotationMirror(String annotationName) {
        TypeElement typeElement = elementUtils.getTypeElement(annotationName);
        if (typeElement == null) {
            // maybe inner class?
            typeElement = elementUtils.getTypeElement(annotationName.replace('$', '.'));
        }
        return Optional.ofNullable(typeElement);
    }

    @Override
    protected VisitorContext createVisitorContext() {
        return annotationUtils.newVisitorContext();
    }

    @NonNull
    @Override
    protected RetentionPolicy getRetentionPolicy(@NonNull Element annotation) {
        final List<? extends AnnotationMirror> annotationMirrors = annotation.getAnnotationMirrors();
        for (AnnotationMirror annotationMirror : annotationMirrors) {
            final String annotationTypeName = getAnnotationTypeName(annotationMirror);
            if (Retention.class.getName().equals(annotationTypeName)) {

                final Iterator<? extends AnnotationValue> i = annotationMirror
                    .getElementValues().values().iterator();
                if (i.hasNext()) {
                    final AnnotationValue av = i.next();
                    final String v = av.getValue().toString();
                    return RetentionPolicy.valueOf(v);
                }
                break;
            }

        }
        return RetentionPolicy.RUNTIME;
    }

    @Override
    protected Element getTypeForAnnotation(AnnotationMirror annotationMirror) {
        return annotationMirror.getAnnotationType().asElement();
    }

    @Override
    protected List<? extends AnnotationMirror> getAnnotationsForType(Element element) {
        List<AnnotationMirror> expanded = new ArrayList<>();
        for (AnnotationMirror annotation : element.getAnnotationMirrors()) {
            boolean repeatable = false;
            boolean hasOtherMembers = false;
            for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry : annotation.getElementValues().entrySet()) {
                if (entry.getKey().getSimpleName().toString().equals("value")) {
                    Object value = entry.getValue().getValue();
                    if (value instanceof List) {
                        String parentAnnotationName = getAnnotationTypeName(annotation);
                        for (Object val : (List<?>) value) {
                            if (val instanceof AnnotationMirror) {
                                String name = getRepeatableName((AnnotationMirror) val);
                                if (name != null && name.equals(parentAnnotationName)) {
                                    repeatable = true;
                                    expanded.add((AnnotationMirror) val);
                                }
                            }
                        }
                    }
                } else {
                    hasOtherMembers = true;
                }
            }
            if (!repeatable || hasOtherMembers) {
                expanded.add(annotation);
            }
        }
        return expanded;
    }

    @Override
    protected boolean isExcludedAnnotation(@NonNull Element element, @NonNull String annotationName) {
        if (annotationName.startsWith("java.lang.annotation") && element.getKind() == ElementKind.ANNOTATION_TYPE) {
            return false;
        } else {
            return super.isExcludedAnnotation(element, annotationName);
        }
    }

    @Override
    protected List<Element> buildHierarchy(Element element, boolean inheritTypeAnnotations, boolean declaredOnly) {
        if (declaredOnly) {
            List<Element> onlyDeclared = new ArrayList<>(1);
            onlyDeclared.add(element);
            return onlyDeclared;
        }

        if (element instanceof TypeElement) {
            List<Element> hierarchy = new ArrayList<>();
            hierarchy.add(element);
            if (element.getKind() == ElementKind.ANNOTATION_TYPE) {
                return hierarchy;
            }
            populateTypeHierarchy(element, hierarchy);
            Collections.reverse(hierarchy);
            return hierarchy;
        } else if (element instanceof ExecutableElement executableElement) {
            // we have a method
            // for methods we merge the data from any overridden interface or abstract methods
            // with type level data
            // the starting hierarchy is the type and super types of this method
            List<Element> hierarchy;
            if (inheritTypeAnnotations) {
                hierarchy = buildHierarchy(executableElement.getEnclosingElement(), false, declaredOnly);
            } else {
                hierarchy = new ArrayList<>();
            }
            hierarchy.addAll(findOverriddenMethods(executableElement));
            hierarchy.add(element);
            return hierarchy;
        } else if (element instanceof VariableElement variable) {
            List<Element> hierarchy = new ArrayList<>();
            Element enclosingElement = variable.getEnclosingElement();
            if (enclosingElement instanceof ExecutableElement executableElement) {
                int variableIdx = executableElement.getParameters().indexOf(variable);
                for (ExecutableElement overridden : findOverriddenMethods(executableElement)) {
                    hierarchy.add(overridden.getParameters().get(variableIdx));
                }
            }
            hierarchy.add(variable);
            return hierarchy;
        } else {
            ArrayList<Element> single = new ArrayList<>();
            single.add(element);
            return single;
        }
    }

    @Override
    protected Map<? extends Element, ?> readAnnotationRawValues(AnnotationMirror annotationMirror) {
        return annotationMirror.getElementValues();
    }

    @Nullable
    @Override
    protected Element getAnnotationMember(Element annotationElement, CharSequence member) {
        if (annotationElement instanceof TypeElement) {
            List<? extends Element> enclosedElements = annotationElement.getEnclosedElements();
            for (Element enclosedElement : enclosedElements) {
                if (enclosedElement instanceof ExecutableElement && enclosedElement.getSimpleName().toString().equals(member.toString())) {
                    return enclosedElement;
                }
            }
        }
        return null;
    }

    @Override
    protected String getOriginatingClassName(Element orginatingElement) {
        TypeElement typeElement = getOriginatingTypeElement(orginatingElement);
        if (typeElement != null) {
            return JavaModelUtils.getClassName(typeElement);
        }
        return null;
    }

    private TypeElement getOriginatingTypeElement(Element element) {
        if (element == null) {
            return null;
        }
        if (element instanceof TypeElement typeElement) {
            return typeElement;
        }

        return getOriginatingTypeElement(element.getEnclosingElement());
    }

    @Override
    protected <K extends Annotation> Optional<io.micronaut.core.annotation.AnnotationValue<K>> getAnnotationValues(Element originatingElement, Element member, Class<K> annotationType) {
        List<? extends AnnotationMirror> annotationMirrors = member.getAnnotationMirrors();
        String annotationName = annotationType.getName();
        for (AnnotationMirror annotationMirror : annotationMirrors) {
            if (annotationMirror.getAnnotationType().toString().endsWith(annotationName)) {
                Map<? extends Element, ?> values = readAnnotationRawValues(annotationMirror);
                Map<CharSequence, Object> converted = new LinkedHashMap<>();
                for (Map.Entry<? extends Element, ?> entry : values.entrySet()) {
                    Element key = entry.getKey();
                    Object value = entry.getValue();
                    readAnnotationRawValues(originatingElement, annotationName, key, key.getSimpleName().toString(), value, converted);
                }
                return Optional.of(io.micronaut.core.annotation.AnnotationValue.builder(annotationType).members(converted).build());
            }
        }
        return Optional.empty();
    }

    @Override
    protected void readAnnotationRawValues(
        Element originatingElement,
        String annotationName,
        Element member,
        String memberName,
        Object annotationValue,
        Map<CharSequence, Object> annotationValues) {
        if (memberName != null && annotationValue instanceof javax.lang.model.element.AnnotationValue && !annotationValues.containsKey(memberName)) {
            final MetadataAnnotationValueVisitor resolver = new MetadataAnnotationValueVisitor(originatingElement, (ExecutableElement) member);
            ((javax.lang.model.element.AnnotationValue) annotationValue).accept(resolver, this);
            Object resolvedValue = resolver.resolvedValue;
            if (resolvedValue != null) {
                if (isEvaluatedExpression(resolvedValue)) {
                    resolvedValue = buildEvaluatedExpressionReference(originatingElement, annotationName, memberName, resolvedValue);
                }
                validateAnnotationValue(originatingElement, annotationName, member, memberName, resolvedValue);
                annotationValues.put(memberName, resolvedValue);
            }
        }
    }

    @Override
    protected boolean isValidationRequired(Element member) {
        final List<? extends AnnotationMirror> annotationMirrors = member.getAnnotationMirrors();
        return isValidationRequired(annotationMirrors);
    }

    private boolean isValidationRequired(List<? extends AnnotationMirror> annotationMirrors) {
        for (AnnotationMirror annotationMirror : annotationMirrors) {
            final String annotationName = getAnnotationTypeName(annotationMirror);
            if (annotationName.startsWith("jakarta.validation")) {
                return true;
            } else if (!AnnotationUtil.INTERNAL_ANNOTATION_NAMES.contains(annotationName)) {
                final Element element = getAnnotationMirror(annotationName).orElse(null);
                if (element != null) {
                    final List<? extends AnnotationMirror> childMirrors = element.getAnnotationMirrors()
                        .stream()
                        .filter(ann -> !getAnnotationTypeName(ann).equals(annotationName))
                        .collect(Collectors.toList());
                    if (isValidationRequired(childMirrors)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @Override
    protected Object readAnnotationValue(Element originatingElement, Element member, String annotationName, String memberName, Object annotationValue) {
        if (memberName != null && annotationValue instanceof javax.lang.model.element.AnnotationValue) {
            final MetadataAnnotationValueVisitor visitor = new MetadataAnnotationValueVisitor(originatingElement, (ExecutableElement) member);
            ((javax.lang.model.element.AnnotationValue) annotationValue).accept(visitor, this);
            return visitor.resolvedValue;
        } else if (memberName != null && annotationValue != null && ClassUtils.isJavaLangType(annotationValue.getClass())) {
            // only allow basic types
            if (isEvaluatedExpression(annotationValue)) {
                annotationValue = buildEvaluatedExpressionReference(originatingElement, annotationName, memberName, annotationValue);
            }
            return annotationValue;
        }
        return null;
    }

    @Override
    protected Map<? extends Element, ?> readAnnotationDefaultValues(String annotationTypeName, Element element) {
        Map<Element, AnnotationValue> defaultValues = new LinkedHashMap<>();
        if (element instanceof TypeElement) {
            TypeElement annotationElement = (TypeElement) element;
            final List<? extends Element> allMembers = elementUtils.getAllMembers(annotationElement);
            allMembers
                .stream()
                .filter(member -> member.getEnclosingElement().equals(annotationElement))
                .filter(ExecutableElement.class::isInstance)
                .map(ExecutableElement.class::cast)
                .filter(this::isValidDefaultValue)
                .forEach(executableElement -> {
                        final AnnotationValue defaultValue = executableElement.getDefaultValue();
                        defaultValues.put(executableElement, defaultValue);
                    }
                );
        }
        return defaultValues;
    }

    private boolean isValidDefaultValue(ExecutableElement executableElement) {
        AnnotationValue defaultValue = executableElement.getDefaultValue();
        if (defaultValue != null) {
            Object v = defaultValue.getValue();
            if (v != null) {
                if (v instanceof String) {
                    return StringUtils.isNotEmpty((CharSequence) v);
                } else {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    protected String getAnnotationTypeName(AnnotationMirror annotationMirror) {
        return JavaModelUtils.getClassName((TypeElement) annotationMirror.getAnnotationType().asElement());
    }

    @Override
    protected String getElementName(Element element) {
        if (element instanceof TypeElement) {
            return elementUtils.getBinaryName(((TypeElement) element)).toString();
        }
        return element.getSimpleName().toString();
    }

    private void populateTypeHierarchy(Element element, List<Element> hierarchy) {
        final boolean isInterface = JavaModelUtils.resolveKind(element, ElementKind.INTERFACE).isPresent();
        final Types typeUtils = modelUtils.getTypeUtils();
        if (isInterface) {
            TypeElement typeElement = (TypeElement) element;
            List<? extends TypeMirror> interfaces = typeElement.getInterfaces();
            for (TypeMirror anInterface : interfaces) {
                final Element e = typeUtils.asElement(anInterface);
                if (e != null) {
                    hierarchy.add(e);
                    populateTypeHierarchy(e, hierarchy);
                }
            }
        } else {
            while (JavaModelUtils.resolveKind(element, ElementKind.CLASS).isPresent()) {

                TypeElement typeElement = (TypeElement) element;
                List<? extends TypeMirror> interfaces = typeElement.getInterfaces();
                for (TypeMirror anInterface : interfaces) {
                    if (anInterface instanceof DeclaredType) {
                        Element interfaceElement = ((DeclaredType) anInterface).asElement();
                        hierarchy.add(interfaceElement);
                        populateTypeHierarchy(interfaceElement, hierarchy);
                    }
                }
                TypeMirror superMirror = typeElement.getSuperclass();
                if (superMirror instanceof DeclaredType) {
                    DeclaredType type = (DeclaredType) superMirror;
                    if (type.toString().equals(Object.class.getName())) {
                        break;
                    } else {
                        element = type.asElement();
                        hierarchy.add(element);
                    }
                } else {
                    break;
                }
            }
        }
    }

    private List<ExecutableElement> findOverriddenMethods(ExecutableElement sourceMethod) {
        return OVERRIDDEN_METHOD_CACHE.computeIfAbsent(sourceMethod, executableElement -> {
            List<ExecutableElement> overridden = new ArrayList<>(3);
            Element enclosingElement = executableElement.getEnclosingElement();
            if (enclosingElement instanceof TypeElement) {
                TypeElement declaringElement = (TypeElement) enclosingElement;
                final Set<TypeElement> allInterfaces = modelUtils.getAllInterfaces(declaringElement);
                for (TypeElement itfe : allInterfaces) {
                    addOverriddenMethodIfNecessary(executableElement, overridden, declaringElement, itfe);
                }
                final Types typeUtils = modelUtils.getTypeUtils();
                TypeElement supertype = toTypeElement(declaringElement.getSuperclass(), typeUtils);
                while (supertype != null && !supertype.toString().equals(Object.class.getName())) {
                    addOverriddenMethodIfNecessary(executableElement, overridden, declaringElement, supertype);
                    supertype = toTypeElement(supertype.getSuperclass(), typeUtils);
                }

            }
            return overridden;
        });

    }

    private void addOverriddenMethodIfNecessary(ExecutableElement executableElement,
                                                List<ExecutableElement> overridden,
                                                TypeElement declaringElement,
                                                TypeElement supertype) {
        final List<ExecutableElement> possibleMethods =
            ElementFilter.methodsIn(supertype.getEnclosedElements());
        for (ExecutableElement possibleMethod : possibleMethods) {
            if (elementUtils.overrides(executableElement, possibleMethod, declaringElement)) {
                overridden.add(possibleMethod);
            }
        }
    }

    private TypeElement toTypeElement(TypeMirror mirror, Types typeUtils) {
        if (mirror != null) {
            final Element e = typeUtils.asElement(mirror);
            if (e instanceof TypeElement) {
                return (TypeElement) e;
            }
        }
        return null;
    }

    /**
     * Checks if a method has an annotation.
     *
     * @param element The method
     * @param ann     The annotation to look for
     * @return Whether if the method has the annotation
     */
    @Override
    public boolean hasAnnotation(Element element, Class<? extends Annotation> ann) {
        return hasAnnotation(element, ann.getName());
    }

    /**
     * Checks if a method has an annotation.
     *
     * @param element The method
     * @param ann     The annotation to look for
     * @return Whether if the method has the annotation
     */
    @Override
    public boolean hasAnnotation(Element element, String ann) {
        List<? extends AnnotationMirror> annotationMirrors = element.getAnnotationMirrors();
        if (CollectionUtils.isNotEmpty(annotationMirrors)) {
            for (AnnotationMirror annotationMirror : annotationMirrors) {
                final DeclaredType annotationType = annotationMirror.getAnnotationType();
                if (annotationType.toString().equals(ann)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    protected boolean hasAnnotations(Element element) {
        return CollectionUtils.isNotEmpty(element.getAnnotationMirrors());
    }

    /**
     * Clears any caches from the last compilation round.
     */
    public static void clearCaches() {
        OVERRIDDEN_METHOD_CACHE.clear();
        AbstractAnnotationMetadataBuilder.clearCaches();
    }

    /**
     * Checks if a method has an annotation.
     *
     * @param method The method
     * @param ann    The annotation to look for
     * @return Whether if the method has the annotation
     */
    public static boolean hasAnnotation(ExecutableElement method, Class<? extends Annotation> ann) {
        List<? extends AnnotationMirror> annotationMirrors = method.getAnnotationMirrors();
        for (AnnotationMirror annotationMirror : annotationMirrors) {
            if (annotationMirror.getAnnotationType().toString().equals(ann.getName())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Meta annotation value visitor class.
     */
    private class MetadataAnnotationValueVisitor extends AbstractAnnotationValueVisitor8<Object, Object> {
        private final Element originatingElement;
        private final ExecutableElement member;
        private Object resolvedValue;

        /**
         * @param originatingElement
         * @param member
         */
        MetadataAnnotationValueVisitor(Element originatingElement, ExecutableElement member) {
            this.originatingElement = originatingElement;
            this.member = member;
        }

        @Override
        public Object visitBoolean(boolean b, Object o) {
            resolvedValue = b;
            return null;
        }

        @Override
        public Object visitByte(byte b, Object o) {
            resolvedValue = b;
            return null;
        }

        @Override
        public Object visitChar(char c, Object o) {
            resolvedValue = c;
            return null;
        }

        @Override
        public Object visitDouble(double d, Object o) {
            resolvedValue = d;
            return null;
        }

        @Override
        public Object visitFloat(float f, Object o) {
            resolvedValue = f;
            return null;
        }

        @Override
        public Object visitInt(int i, Object o) {
            resolvedValue = i;
            return null;
        }

        @Override
        public Object visitLong(long i, Object o) {
            resolvedValue = i;
            return null;
        }

        @Override
        public Object visitShort(short s, Object o) {
            resolvedValue = s;
            return null;
        }

        @Override
        public Object visitString(String s, Object o) {
            resolvedValue = s;
            return null;
        }

        @Override
        public Object visitType(TypeMirror t, Object o) {
            if (t instanceof DeclaredType) {
                Element typeElement = ((DeclaredType) t).asElement();
                if (typeElement instanceof TypeElement) {
                    String className = JavaModelUtils.getClassName((TypeElement) typeElement);
                    resolvedValue = new AnnotationClassValue<>(className);
                }
            }
            return null;
        }

        @Override
        public Object visitEnumConstant(VariableElement c, Object o) {
            resolvedValue = c.toString();
            return null;
        }

        @Override
        public Object visitAnnotation(AnnotationMirror a, Object o) {
            if (a instanceof javax.lang.model.element.AnnotationValue) {
                resolvedValue = readNestedAnnotationValue(originatingElement, a);
            }
            return null;
        }

        @Override
        public Object visitArray(List<? extends javax.lang.model.element.AnnotationValue> vals, Object o) {
            ArrayValueVisitor arrayValueVisitor = new ArrayValueVisitor(member);
            for (javax.lang.model.element.AnnotationValue val : vals) {
                val.accept(arrayValueVisitor, o);
            }
            resolvedValue = arrayValueVisitor.getValues();
            return null;
        }

        /**
         * Array value visitor class.
         */
        @SuppressWarnings("unchecked")
        private final class ArrayValueVisitor extends AbstractAnnotationValueVisitor8<Object, Object> {

            private List values = new ArrayList();
            private final ExecutableElement member;

            private ArrayValueVisitor(ExecutableElement member) {
                this.member = member;
            }

            Object getValues() {
                final Types typeUtils = modelUtils.getTypeUtils();
                TypeMirror arrayType;
                TypeMirror methodReturnType = member.getReturnType();
                if (methodReturnType instanceof NullType) {
                    return null;
                }
                if (methodReturnType instanceof ArrayType at) {
                    arrayType = at.getComponentType();
                } else {
                    throw new IllegalStateException("Expected an array got: " + methodReturnType + " " + originatingElement + " " + member);
                }
                Class<?> type = ClassUtils.getPrimitiveType(arrayType.toString()).orElse(null);
                if (type == null) {
                    Element element = typeUtils.asElement(arrayType);
                    if (element != null) {
                        if (element.getKind() == ElementKind.ENUM) {
                            type = String.class;
                        } else if (element.getKind() == ElementKind.ANNOTATION_TYPE) {
                            type = io.micronaut.core.annotation.AnnotationValue.class;
                        } else if (Class.class.getName().equals(element.toString())) {
                            type = io.micronaut.core.annotation.AnnotationClassValue.class;
                        } else {
                            // Annotations allow only basic Java classes so this should be fine
                            type = ClassUtils.forName(element.toString(), getClass().getClassLoader()).orElse(null);
                        }
                    }
                }
                if (type == null) {
                    throw new IllegalStateException("Cannot determine the type of: " + methodReturnType);
                }
                type = ReflectionUtils.getPrimitiveType(type);
                if (type.isPrimitive()) {
                    Class<?> wrapperType = ReflectionUtils.getWrapperType(type);
                    Class<?> primitiveArrayType = Array.newInstance(type, 0).getClass();
                    Object[] emptyWrapperArray = (Object[]) Array.newInstance(wrapperType, 0);
                    Object[] wrapperArray = values.toArray(emptyWrapperArray);
                    // Convert to a proper primitive type array
                    return ConversionService.SHARED.convertRequired(wrapperArray, primitiveArrayType);
                }
                return ConversionService.SHARED.convertRequired(values, Array.newInstance(type, 0).getClass());
            }

            @Override
            public Object visitBoolean(boolean b, Object o) {
                values.add(b);
                return null;
            }

            @Override
            public Object visitByte(byte b, Object o) {
                values.add(b);
                return null;
            }

            @Override
            public Object visitChar(char c, Object o) {
                values.add(c);
                return null;
            }

            @Override
            public Object visitDouble(double d, Object o) {
                values.add(d);
                return null;
            }

            @Override
            public Object visitFloat(float f, Object o) {
                values.add(f);
                return null;
            }

            @Override
            public Object visitInt(int i, Object o) {
                values.add(i);
                return null;
            }

            @Override
            public Object visitLong(long i, Object o) {
                values.add(i);
                return null;
            }

            @Override
            public Object visitShort(short s, Object o) {
                values.add(s);
                return null;
            }

            @Override
            public Object visitString(String s, Object o) {
                values.add(s);
                return null;
            }

            @Override
            public Object visitType(TypeMirror t, Object o) {
                if (t instanceof DeclaredType) {
                    Element typeElement = ((DeclaredType) t).asElement();
                    if (typeElement instanceof TypeElement) {
                        final String className = JavaModelUtils.getClassName((TypeElement) typeElement);
                        values.add(new AnnotationClassValue<>(className));
                    }
                } else if (t instanceof ArrayType) {
                    TypeMirror componentType = ((ArrayType) t).getComponentType();
                    if (componentType instanceof DeclaredType) {
                        Element typeElement = ((DeclaredType) componentType).asElement();
                        if (typeElement instanceof TypeElement) {
                            final String className = JavaModelUtils.getClassArrayName((TypeElement) typeElement);
                            values.add(new AnnotationClassValue<>(className));
                        }
                    }
                }
                return null;
            }

            @Override
            public Object visitEnumConstant(VariableElement c, Object o) {
                values.add(c.getSimpleName().toString());
                return null;
            }

            @Override
            public Object visitAnnotation(AnnotationMirror a, Object o) {
                io.micronaut.core.annotation.AnnotationValue annotationValue = readNestedAnnotationValue(originatingElement, a);
                values.add(annotationValue);
                return null;
            }

            @Override
            public Object visitArray(List<? extends javax.lang.model.element.AnnotationValue> vals, Object o) {
                return null;
            }
        }
    }
}
