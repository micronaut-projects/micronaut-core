/*
 * Copyright 2017 original authors
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
package org.particleframework.inject.annotation;


import org.particleframework.core.annotation.AnnotationMetadata;
import org.particleframework.core.value.OptionalValues;

import javax.lang.model.element.*;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.AbstractAnnotationValueVisitor8;
import javax.lang.model.util.Elements;
import java.lang.annotation.Annotation;
import java.lang.reflect.Array;
import java.util.*;
import java.util.stream.Stream;

/**
 * A {@link AnnotationMetadata} for builder for Java to be used at compile time
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class JavaAnnotationMetadataBuilder extends AbstractAnnotationMetadataBuilder<Element, AnnotationMirror> {


    private final Elements elementUtils;

    public JavaAnnotationMetadataBuilder(Elements elementUtils) {
        this.elementUtils = elementUtils;
    }

    @Override
    protected String getAnnotationMemberName(Element member) {
        return member.getSimpleName().toString();
    }

    @Override
    protected Element getTypeForAnnotation(AnnotationMirror annotationMirror) {
        return annotationMirror.getAnnotationType().asElement();
    }

    @Override
    protected List<? extends AnnotationMirror> getAnnotationsForType(Element element) {
        return element.getAnnotationMirrors();
    }

    @Override
    protected List<Element> buildHierarchy(Element element, boolean inheritTypeAnnotations) {
        if(element instanceof TypeElement) {
            List<Element> hierarchy = new ArrayList<>();
            hierarchy.add(element);
            populateTypeHierarchy(element, hierarchy);
            Collections.reverse(hierarchy);
            return hierarchy;
        }
        else if(element instanceof ExecutableElement) {
            // we have a method
            // for methods we merge the data from any overridden interface or abstract methods
            // with type level data
            ExecutableElement executableElement = (ExecutableElement) element;
            // the starting hierarchy is the type and super types of this method
            List<Element> hierarchy;
            if(inheritTypeAnnotations) {
                hierarchy = buildHierarchy(executableElement.getEnclosingElement(), false);
            }
            else {
                hierarchy = new ArrayList<>();
            }
            if( hasAnnotation(executableElement, Override.class)) {
                hierarchy.addAll( findOverriddenMethods(executableElement) );
            }
            hierarchy.add(element);
            return hierarchy;
        }
        else {
            return Collections.singletonList(element);
        }
    }



    @Override
    protected Map<? extends Element, ?> readAnnotationValues(AnnotationMirror annotationMirror) {
        return annotationMirror.getElementValues();
    }

    @Override
    protected OptionalValues<?> getAnnotationValues(Element member, Class<?> annotationType) {
        List<? extends AnnotationMirror> annotationMirrors = member.getAnnotationMirrors();
        String annotationName = annotationType.getName();
        for (AnnotationMirror annotationMirror : annotationMirrors) {
            if ( annotationMirror.getAnnotationType().toString().endsWith(annotationName) ) {
                Map<? extends Element, ?> values = readAnnotationValues(annotationMirror);
                Map<CharSequence, Object> converted = new LinkedHashMap<>();
                for (Map.Entry<? extends Element, ?> entry : values.entrySet()) {
                    Element key = entry.getKey();
                    Object value = entry.getValue();
                    readAnnotationValues(key.getSimpleName().toString(), value, converted);
                }
                return OptionalValues.of(Object.class, converted);
            }
        }
        return OptionalValues.empty();
    }

    @Override
    protected void readAnnotationValues(String memberName, Object annotationValue, Map<CharSequence, Object> annotationValues) {
        if(memberName != null && annotationValue instanceof javax.lang.model.element.AnnotationValue) {
            ((javax.lang.model.element.AnnotationValue)annotationValue).accept(new MetadataAnnotationValueVisitor(annotationValues, memberName), this);
        }
    }

    @Override
    protected String getAnnotationTypeName(AnnotationMirror annotationMirror) {
        return annotationMirror.getAnnotationType().toString();
    }

    private void populateTypeHierarchy(Element element, List<Element> hierarchy) {
        while(element != null && element.getKind() == ElementKind.CLASS) {

            TypeElement typeElement = (TypeElement) element;
            List<? extends TypeMirror> interfaces = typeElement.getInterfaces();
            for (TypeMirror anInterface : interfaces) {
                if(anInterface instanceof DeclaredType) {
                    Element interfaceElement = ((DeclaredType) anInterface).asElement();
                    hierarchy.add(interfaceElement);
                    populateTypeHierarchy(interfaceElement, hierarchy);
                }
            }
            TypeMirror superMirror = typeElement.getSuperclass();
            if(superMirror instanceof DeclaredType) {
                DeclaredType type = (DeclaredType) superMirror;
                if(type.toString().equals(Object.class.getName())) {
                    break;
                }
                else {
                    element = type.asElement();
                    hierarchy.add(element);
                }
            }
            else {
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
                    if(!hasAnnotation(overriddenMethod, Override.class)) {
                        findOverriddenInterfaceMethod(executableElement, overridden, supertype);
                        break;
                    }
                }
                else {
                    findOverriddenInterfaceMethod(executableElement, overridden, supertype);

                }
                TypeMirror superclass = supertype.getSuperclass();
                if(superclass instanceof DeclaredType) {
                    supertype = (TypeElement) ((DeclaredType) superclass).asElement();
                }
                else {
                    break;
                }
            }
        }
        return overridden;
    }

    public static boolean hasAnnotation(ExecutableElement method, Class<? extends Annotation> ann) {
        List<? extends AnnotationMirror> annotationMirrors = method.getAnnotationMirrors();
        for (AnnotationMirror annotationMirror : annotationMirrors) {
            if(annotationMirror.getAnnotationType().toString().equals(ann.getName())) {
                return true;
            }
        }
        return false;
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
                    overridden.add( result.get() );
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


    private class MetadataAnnotationValueVisitor extends AbstractAnnotationValueVisitor8<Object, Object> {
        private final Map<CharSequence, Object> annotationValues;
        private final String memberName;

        public MetadataAnnotationValueVisitor(Map<CharSequence, Object> annotationValues, String member) {
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
            String className = t.toString();
            annotationValues.put(memberName, className);
            return null;
        }

        @Override
        public Object visitEnumConstant(VariableElement c, Object o) {
            annotationValues.put(memberName, c.toString());
            return null;
        }

        @Override
        public Object visitAnnotation(AnnotationMirror a, Object o) {
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

        @SuppressWarnings("unchecked")
        private class ArrayValueVisitor extends AbstractAnnotationValueVisitor8<Object, Object> {

            private List values = new ArrayList();
            private Class arrayType;

            Object[] getValues() {
                if(arrayType != null) {
                    return values.toArray((Object[]) Array.newInstance(arrayType, values.size()));
                }
                else {
                    return values.toArray(new Object[values.size()]);
                }
            }

            @Override
            public Object visitBoolean(boolean b, Object o) {
                arrayType = boolean.class;
                values.add(b);
                return null;
            }

            @Override
            public Object visitByte(byte b, Object o) {
                arrayType = byte.class;
                values.add(b);
                return null;
            }

            @Override
            public Object visitChar(char c, Object o) {
                arrayType = char.class;
                values.add(c);
                return null;
            }

            @Override
            public Object visitDouble(double d, Object o) {
                arrayType = double.class;
                values.add(d);
                return null;
            }

            @Override
            public Object visitFloat(float f, Object o) {
                arrayType = float.class;
                values.add(f);
                return null;
            }

            @Override
            public Object visitInt(int i, Object o) {
                arrayType = int.class;
                values.add(i);
                return null;
            }

            @Override
            public Object visitLong(long i, Object o) {
                arrayType = long.class;
                values.add(i);
                return null;
            }

            @Override
            public Object visitShort(short s, Object o) {
                arrayType = short.class;
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
                values.add(t.toString());
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
                arrayType = AnnotationValue.class;
                AnnotationValue annotationValue = readNestedAnnotationValue(a);
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
