/*
 * Copyright 2017-2018 original authors
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

package io.micronaut.inject.annotation;

import io.micronaut.core.annotation.AnnotationUtil;
import io.micronaut.core.util.StringUtils;
import io.micronaut.core.value.OptionalValues;
import io.micronaut.inject.processing.JavaModelUtils;

import javax.annotation.Nullable;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.AbstractAnnotationValueVisitor8;
import javax.lang.model.util.Elements;
import java.lang.annotation.Annotation;
import java.lang.annotation.Repeatable;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * A {@link io.micronaut.core.annotation.AnnotationMetadata} for builder for Java to be used at compile time.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class JavaAnnotationMetadataBuilder extends AbstractAnnotationMetadataBuilder<Element, AnnotationMirror> {

    public static final Map<String, Map<Element, javax.lang.model.element.AnnotationValue>> ANNOTATION_DEFAULTS = new HashMap<>();

    private final Elements elementUtils;

    /**
     * @param elementUtils Element utils
     */
    public JavaAnnotationMetadataBuilder(Elements elementUtils) {
        this.elementUtils = elementUtils;
    }

    @Override
    protected String getAnnotationMemberName(Element member) {
        return member.getSimpleName().toString();
    }

    @Nullable
    @Override
    protected String getRepeatableName(AnnotationMirror annotationMirror) {
        List<? extends AnnotationMirror> mirrors = annotationMirror.getAnnotationType().asElement().getAnnotationMirrors();
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
    protected List<Element> buildHierarchy(Element element, boolean inheritTypeAnnotations) {
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
                hierarchy = buildHierarchy(executableElement.getEnclosingElement(), false);
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
    protected OptionalValues<?> getAnnotationValues(Element member, Class<?> annotationType) {
        List<? extends AnnotationMirror> annotationMirrors = member.getAnnotationMirrors();
        String annotationName = annotationType.getName();
        for (AnnotationMirror annotationMirror : annotationMirrors) {
            if (annotationMirror.getAnnotationType().toString().endsWith(annotationName)) {
                Map<? extends Element, ?> values = readAnnotationRawValues(annotationMirror);
                Map<CharSequence, Object> converted = new LinkedHashMap<>();
                for (Map.Entry<? extends Element, ?> entry : values.entrySet()) {
                    Element key = entry.getKey();
                    Object value = entry.getValue();
                    readAnnotationRawValues(key.getSimpleName().toString(), value, converted);
                }
                return OptionalValues.of(Object.class, converted);
            }
        }
        return OptionalValues.empty();
    }

    @Override
    protected void readAnnotationRawValues(String memberName, Object annotationValue, Map<CharSequence, Object> annotationValues) {
        if (memberName != null && annotationValue instanceof javax.lang.model.element.AnnotationValue) {
            if (!annotationValues.containsKey(memberName)) {
                ((javax.lang.model.element.AnnotationValue) annotationValue).accept(new MetadataAnnotationValueVisitor(annotationValues, memberName), this);

            }
        }
    }

    @Override
    protected Object readAnnotationValue(String memberName, Object annotationValue) {
        if (memberName != null && annotationValue instanceof javax.lang.model.element.AnnotationValue) {
            HashMap<CharSequence, Object> result = new HashMap<>(1);
            ((javax.lang.model.element.AnnotationValue) annotationValue).accept(new MetadataAnnotationValueVisitor(result, memberName), this);
            return result.get(memberName);
        }
        return null;
    }

    @Override
    protected Map<? extends Element, ?> readAnnotationDefaultValues(AnnotationMirror annotationMirror) {
        Map<String, Map<Element, javax.lang.model.element.AnnotationValue>> defaults = JavaAnnotationMetadataBuilder.ANNOTATION_DEFAULTS;

        Element element = annotationMirror.getAnnotationType().asElement();
        if (element instanceof  TypeElement) {
            TypeElement annotationElement = (TypeElement) element;
            String annotationName = annotationElement.getQualifiedName().toString();
            if (!defaults.containsKey(annotationName)) {

                Map<Element, AnnotationValue> defaultValues = new HashMap<>();
                elementUtils.getAllMembers(annotationElement)
                        .stream()
                        .filter(member -> member.getEnclosingElement() == annotationElement)
                        .filter(ExecutableElement.class::isInstance)
                        .map(ExecutableElement.class::cast)
                        .filter(this::isValidDefaultValue)
                        .forEach(executableElement ->
                                defaultValues.put(executableElement, executableElement.getDefaultValue())
                        );

                if (!defaultValues.isEmpty()) {
                    defaults.put(annotationName, defaultValues);
                }
            }
        }
        return ANNOTATION_DEFAULTS.get(getAnnotationTypeName(annotationMirror));
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
        while (element != null && element.getKind() == ElementKind.CLASS) {

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
                    if (!hasAnnotation(overriddenMethod, Override.class)) {
                        findOverriddenInterfaceMethod(executableElement, overridden, supertype);
                        break;
                    }
                } else {
                    findOverriddenInterfaceMethod(executableElement, overridden, supertype);

                }
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
        private final Map<CharSequence, Object> annotationValues;
        private final String memberName;

        /**
         * @param annotationValues A map of annotation values
         * @param member           A member
         */
        MetadataAnnotationValueVisitor(Map<CharSequence, Object> annotationValues, String member) {
            this.annotationValues = annotationValues;
            this.memberName = member;
        }

        @Override
        public Object visitBoolean(boolean b, Object o) {
            annotationValues.put(memberName, b);
            return null;
        }

        @Override
        public Object visitByte(byte b, Object o) {
            annotationValues.put(memberName, b);
            return null;
        }

        @Override
        public Object visitChar(char c, Object o) {
            annotationValues.put(memberName, c);
            return null;
        }

        @Override
        public Object visitDouble(double d, Object o) {
            annotationValues.put(memberName, d);
            return null;
        }

        @Override
        public Object visitFloat(float f, Object o) {
            annotationValues.put(memberName, f);
            return null;
        }

        @Override
        public Object visitInt(int i, Object o) {
            annotationValues.put(memberName, i);
            return null;
        }

        @Override
        public Object visitLong(long i, Object o) {
            annotationValues.put(memberName, i);
            return null;
        }

        @Override
        public Object visitShort(short s, Object o) {
            annotationValues.put(memberName, s);
            return null;
        }

        @Override
        public Object visitString(String s, Object o) {
            annotationValues.put(memberName, s);
            return null;
        }

        @Override
        public Object visitType(TypeMirror t, Object o) {
            if (t instanceof DeclaredType) {
                Element typeElement = ((DeclaredType) t).asElement();
                if (typeElement instanceof TypeElement) {
                    String className = JavaModelUtils.getClassName((TypeElement) typeElement);
                    annotationValues.put(memberName, className);
                }
            }
            return null;
        }

        @Override
        public Object visitEnumConstant(VariableElement c, Object o) {
            annotationValues.put(memberName, c.toString());
            return null;
        }

        @Override
        public Object visitAnnotation(AnnotationMirror a, Object o) {
            if (a instanceof javax.lang.model.element.AnnotationValue) {
                io.micronaut.core.annotation.AnnotationValue value = readNestedAnnotationValue(a);
                annotationValues.put(memberName, value);
            }
            return null;
        }

        @Override
        public Object visitArray(List<? extends javax.lang.model.element.AnnotationValue> vals, Object o) {
            ArrayValueVisitor arrayValueVisitor = new ArrayValueVisitor();
            for (javax.lang.model.element.AnnotationValue val : vals) {
                val.accept(arrayValueVisitor, o);
            }
            annotationValues.put(memberName, arrayValueVisitor.getValues());
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
                    return values.toArray((Object[]) Array.newInstance(arrayType, values.size()));
                } else {
                    return values.toArray(new Object[values.size()]);
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
                arrayType = String.class;
                if (t instanceof DeclaredType) {
                    Element typeElement = ((DeclaredType) t).asElement();
                    if (typeElement instanceof TypeElement) {
                        values.add(JavaModelUtils.getClassName((TypeElement) typeElement));
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
                io.micronaut.core.annotation.AnnotationValue annotationValue = readNestedAnnotationValue(a);
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
