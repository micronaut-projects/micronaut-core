/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.annotation.processing;

import io.micronaut.core.annotation.AnnotationClassValue;
import io.micronaut.core.annotation.AnnotationUtil;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.core.value.OptionalValues;
import io.micronaut.inject.annotation.AbstractAnnotationMetadataBuilder;
import io.micronaut.inject.annotation.AnnotatedElementValidator;
import io.micronaut.inject.processing.JavaModelUtils;
import io.micronaut.inject.visitor.VisitorContext;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import javax.annotation.processing.Messager;
import javax.lang.model.element.*;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.AbstractAnnotationValueVisitor8;
import javax.lang.model.util.Elements;
import javax.tools.Diagnostic;
import java.lang.annotation.Annotation;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.Array;
import java.util.*;
import java.util.stream.Stream;

/**
 * A {@link io.micronaut.core.annotation.AnnotationMetadata} for builder for Java to be used at compile time.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class JavaAnnotationMetadataBuilder extends AbstractAnnotationMetadataBuilder<Element, AnnotationMirror> {

    private static final Map<String, Map<Element, javax.lang.model.element.AnnotationValue>> ANNOTATION_DEFAULTS = new HashMap<>();

    private final Elements elementUtils;
    private final Messager messager;
    private final AnnotationUtils annotationUtils;
    private final ModelUtils modelUtils;

    /**
     * Default constructor.
     *
     * @param elements The elementUtils
     * @param messager The messager
     * @param annotationUtils The annotation utils
     * @param modelUtils The model utils
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
                for (ExecutableElement executableElement : elementValues.keySet()) {
                    if (executableElement.getSimpleName().toString().equals("value")) {
                        javax.lang.model.element.AnnotationValue av = elementValues.get(executableElement);
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
        return Optional.ofNullable(elementUtils.getTypeElement(annotationName));
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
    protected boolean isMethodOrClassElement(Element element) {
        return element instanceof TypeElement || element instanceof ExecutableElement;
    }

    @NonNull
    @Override
    protected String getDeclaringType(@NonNull Element element) {
        TypeElement typeElement = modelUtils.classElementFor(element);
        if (typeElement != null) {
            return typeElement.getQualifiedName().toString();
        }
        return element.getSimpleName().toString();
    }

    @Override
    protected Element getTypeForAnnotation(AnnotationMirror annotationMirror) {
        return annotationMirror.getAnnotationType().asElement();
    }

    @Override
    protected List<? extends AnnotationMirror> getAnnotationsForType(Element element) {
        List<? extends AnnotationMirror> annotationMirrors = new ArrayList<>(element.getAnnotationMirrors());
        annotationMirrors.removeIf(mirror -> getAnnotationTypeName(mirror).equals(AnnotationUtil.KOTLIN_METADATA));
        return annotationMirrors;
    }

    @Override
    protected List<Element> buildHierarchy(Element element, boolean inheritTypeAnnotations, boolean declaredOnly) {
        if (declaredOnly) {
            return Collections.singletonList(element);
        }

        if (element instanceof TypeElement) {
            List<Element> hierarchy = new ArrayList<>();
            hierarchy.add(element);
            populateTypeHierarchy(element, hierarchy);
            Collections.reverse(hierarchy);
            return hierarchy;
        } else if (element instanceof ExecutableElement) {
            // we have a method
            // for methods we merge the data from any overridden interface or abstract methods
            // with type level data
            ExecutableElement executableElement = (ExecutableElement) element;
            // the starting hierarchy is the type and super types of this method
            List<Element> hierarchy;
            if (inheritTypeAnnotations) {
                hierarchy = buildHierarchy(executableElement.getEnclosingElement(), false, declaredOnly);
            } else {
                hierarchy = new ArrayList<>();
            }
            if (hasAnnotation(executableElement, Override.class)) {
                hierarchy.addAll(findOverriddenMethods(executableElement));
            }
            hierarchy.add(element);
            return hierarchy;
        } else if (element instanceof VariableElement) {
            List<Element> hierarchy = new ArrayList<>();
            VariableElement variable = (VariableElement) element;
            Element enclosingElement = variable.getEnclosingElement();
            if (enclosingElement instanceof ExecutableElement) {
                ExecutableElement executableElement = (ExecutableElement) enclosingElement;
                if (hasAnnotation(executableElement, Override.class)) {
                    int variableIdx = executableElement.getParameters().indexOf(variable);
                    for (ExecutableElement overridden: findOverriddenMethods(executableElement)) {
                        hierarchy.add(overridden.getParameters().get(variableIdx));
                    }
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

    @Override
    protected OptionalValues<?> getAnnotationValues(Element originatingElement, Element member, Class<?> annotationType) {
        List<? extends AnnotationMirror> annotationMirrors = member.getAnnotationMirrors();
        String annotationName = annotationType.getName();
        for (AnnotationMirror annotationMirror : annotationMirrors) {
            if (annotationMirror.getAnnotationType().toString().endsWith(annotationName)) {
                Map<? extends Element, ?> values = readAnnotationRawValues(annotationMirror);
                Map<CharSequence, Object> converted = new LinkedHashMap<>();
                for (Map.Entry<? extends Element, ?> entry : values.entrySet()) {
                    Element key = entry.getKey();
                    Object value = entry.getValue();
                    readAnnotationRawValues(originatingElement, annotationName, member, key.getSimpleName().toString(), value, converted);
                }
                return OptionalValues.of(Object.class, converted);
            }
        }
        return OptionalValues.empty();
    }

    @Override
    protected void readAnnotationRawValues(
            Element originatingElement,
            String annotationName, Element member,
            String memberName,
            Object annotationValue,
            Map<CharSequence, Object> annotationValues) {
        if (memberName != null && annotationValue instanceof javax.lang.model.element.AnnotationValue) {
            if (!annotationValues.containsKey(memberName)) {
                final MetadataAnnotationValueVisitor resolver = new MetadataAnnotationValueVisitor(originatingElement);
                ((javax.lang.model.element.AnnotationValue) annotationValue).accept(resolver, this);
                final Object resolvedValue = resolver.resolvedValue;
                if (resolvedValue != null) {
                    validateAnnotationValue(originatingElement, annotationName, member, memberName, resolvedValue);
                    annotationValues.put(memberName, resolvedValue);
                }
            }
        }
    }

    @Override
    protected Object readAnnotationValue(Element originatingElement, Element member, String memberName, Object annotationValue) {
        if (memberName != null && annotationValue instanceof javax.lang.model.element.AnnotationValue) {
            final MetadataAnnotationValueVisitor visitor = new MetadataAnnotationValueVisitor(originatingElement);
            ((javax.lang.model.element.AnnotationValue) annotationValue).accept(visitor, this);
            return visitor.resolvedValue;
        }
        return null;
    }

    @Override
    protected Map<? extends Element, ?> readAnnotationDefaultValues(AnnotationMirror annotationMirror) {

        final String annotationTypeName = getAnnotationTypeName(annotationMirror);
        Element element = annotationMirror.getAnnotationType().asElement();
        return readAnnotationDefaultValues(annotationTypeName, element);
    }

    @Override
    protected Map<? extends Element, ?> readAnnotationDefaultValues(String annotationTypeName, Element element) {
        Map<String, Map<Element, AnnotationValue>> defaults = JavaAnnotationMetadataBuilder.ANNOTATION_DEFAULTS;
        if (element instanceof TypeElement) {
            TypeElement annotationElement = (TypeElement) element;
            String annotationName = annotationElement.getQualifiedName().toString();
            if (!defaults.containsKey(annotationName)) {

                Map<Element, AnnotationValue> defaultValues = new HashMap<>();
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

                defaults.put(annotationName, defaultValues);
            }
        }
        return ANNOTATION_DEFAULTS.get(annotationTypeName);
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

    private void populateTypeHierarchy(Element element, List<Element> hierarchy) {
        final boolean isInterface = JavaModelUtils.resolveKind(element, ElementKind.INTERFACE).isPresent();
        if (isInterface) {
            TypeElement typeElement = (TypeElement) element;
            List<? extends TypeMirror> interfaces = typeElement.getInterfaces();
            for (TypeMirror anInterface : interfaces) {
                if (anInterface instanceof DeclaredType) {
                    Element interfaceElement = ((DeclaredType) anInterface).asElement();
                    hierarchy.add(interfaceElement);
                    populateTypeHierarchy(interfaceElement, hierarchy);
                }
            }
        } else  {
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

    private List<ExecutableElement> findOverriddenMethods(ExecutableElement executableElement) {
        List<ExecutableElement> overridden = new ArrayList<>();
        Element enclosingElement = executableElement.getEnclosingElement();
        if (enclosingElement instanceof TypeElement) {
            TypeElement supertype = (TypeElement) enclosingElement;
            while (supertype != null && !supertype.toString().equals(Object.class.getName())) {
                Optional<ExecutableElement> result = findOverridden(executableElement, supertype);
                if (result.isPresent()) {
                    ExecutableElement overriddenMethod = result.get();
                    overridden.add(overriddenMethod);
                }
                findOverriddenInterfaceMethod(executableElement, overridden, supertype);
                TypeMirror superclass = supertype.getSuperclass();
                if (superclass instanceof DeclaredType) {
                    supertype = (TypeElement) ((DeclaredType) superclass).asElement();
                } else {
                    break;
                }
            }
        }
        return overridden;
    }

    private void findOverriddenInterfaceMethod(ExecutableElement executableElement, List<ExecutableElement> overridden, TypeElement supertype) {
        Optional<ExecutableElement> result;
        List<? extends TypeMirror> interfaces = supertype.getInterfaces();

        for (TypeMirror anInterface : interfaces) {
            if (anInterface instanceof DeclaredType) {
                DeclaredType iElement = (DeclaredType) anInterface;
                TypeElement interfaceElement = (TypeElement) iElement.asElement();
                result = findOverridden(executableElement, interfaceElement);
                if (result.isPresent()) {
                    overridden.add(result.get());
                } else {
                    findOverriddenInterfaceMethod(executableElement, overridden, interfaceElement);
                }
            }
        }
    }

    private Optional<ExecutableElement> findOverridden(ExecutableElement executableElement, TypeElement supertype) {
        Stream<? extends Element> elements = supertype.getEnclosedElements().stream();
        return elements.filter(el -> el.getKind() == ElementKind.METHOD && el.getEnclosingElement().equals(supertype))
                .map(el -> (ExecutableElement) el)
                .filter(method -> elementUtils.overrides(executableElement, method, (TypeElement) method.getEnclosingElement()))
                .findFirst();
    }

    /**
     * Checks if a method has an annotation.
     *
     * @param element The method
     * @param ann    The annotation to look for
     * @return Whether if the method has the annotation
     */
    @Override
    public boolean hasAnnotation(Element element, Class<? extends Annotation> ann) {
        List<? extends AnnotationMirror> annotationMirrors = element.getAnnotationMirrors();
        for (AnnotationMirror annotationMirror : annotationMirrors) {
            if (annotationMirror.getAnnotationType().toString().equals(ann.getName())) {
                return true;
            }
        }
        return false;
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
        private Object resolvedValue;

        /**
         * @param originatingElement
         */
        MetadataAnnotationValueVisitor(Element originatingElement) {
            this.originatingElement = originatingElement;
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
            ArrayValueVisitor arrayValueVisitor = new ArrayValueVisitor();
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
        private class ArrayValueVisitor extends AbstractAnnotationValueVisitor8<Object, Object> {

            private List values = new ArrayList();
            private Class arrayType;

            Object[] getValues() {
                if (arrayType != null) {
                    for (Object value : values) {
                        if (value != null) {
                            if (!arrayType.isInstance(value)) {
                                return ArrayUtils.EMPTY_OBJECT_ARRAY;
                            }
                        }
                    }
                    return values.toArray((Object[]) Array.newInstance(arrayType, values.size()));
                } else {
                    return values.toArray(new Object[0]);
                }
            }

            @Override
            public Object visitBoolean(boolean b, Object o) {
                arrayType = Boolean.class;
                values.add(b);
                return null;
            }

            @Override
            public Object visitByte(byte b, Object o) {
                arrayType = Byte.class;
                values.add(b);
                return null;
            }

            @Override
            public Object visitChar(char c, Object o) {
                arrayType = Character.class;
                values.add(c);
                return null;
            }

            @Override
            public Object visitDouble(double d, Object o) {
                arrayType = Double.class;
                values.add(d);
                return null;
            }

            @Override
            public Object visitFloat(float f, Object o) {
                arrayType = Float.class;
                values.add(f);
                return null;
            }

            @Override
            public Object visitInt(int i, Object o) {
                arrayType = Integer.class;
                values.add(i);
                return null;
            }

            @Override
            public Object visitLong(long i, Object o) {
                arrayType = Long.class;
                values.add(i);
                return null;
            }

            @Override
            public Object visitShort(short s, Object o) {
                arrayType = Short.class;
                values.add(s);
                return null;
            }

            @Override
            public Object visitString(String s, Object o) {
                arrayType = String.class;
                values.add(s);
                return null;
            }

            @Override
            public Object visitType(TypeMirror t, Object o) {
                arrayType = AnnotationClassValue.class;
                if (t instanceof DeclaredType) {
                    Element typeElement = ((DeclaredType) t).asElement();
                    if (typeElement instanceof TypeElement) {
                        final String className = JavaModelUtils.getClassName((TypeElement) typeElement);
                        values.add(new AnnotationClassValue<>(className));
                    }
                }
                return null;
            }

            @Override
            public Object visitEnumConstant(VariableElement c, Object o) {
                arrayType = String.class;
                values.add(c.getSimpleName().toString());
                return null;
            }

            @Override
            public Object visitAnnotation(AnnotationMirror a, Object o) {
                arrayType = io.micronaut.core.annotation.AnnotationValue.class;
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
