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
package org.particleframework.annotation.processing;

import org.particleframework.core.value.OptionalValues;

import javax.inject.Qualifier;
import javax.lang.model.element.*;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import java.lang.annotation.Annotation;
import java.util.*;
import java.util.stream.Stream;

/**
 * Utility methods for annotations
 *
 * @author Graeme Rocher
 * @author Dean Wette
 */
class AnnotationUtils {

    private static final List<String> INTERNAL_ANNOTATION_NAMES = Arrays.asList("Retention", "Documented", "Target");

    private final Elements elementUtils;

    AnnotationUtils(Elements elementUtils) {
        this.elementUtils = elementUtils;
    }

    /**
     * Return whether the given element is annotated with the given annotation stereotype
     *
     * @param element The element
     * @param stereotype The stereotype
     * @return True if it is
     */
    boolean hasStereotype(Element element, Class<? extends Annotation> stereotype) {
        return hasStereotype(element, stereotype.getName());
    }

    /**
     * Return whether the given element is annotated with the given annotation stereotypes
     *
     * @param element The element
     * @param stereotypes The stereotypes
     * @return True if it is
     */
    boolean hasStereotype(Element element, String... stereotypes) {
        return hasStereotype(element, Arrays.asList(stereotypes));
    }

    /**
     * Return whether the given element is annotated with the given annotation stereotypes
     *
     * @param element The element
     * @param stereotypes The stereotypes
     * @return True if it is
     */
    boolean hasStereotype(Element element, List<String> stereotypes) {
        if (element == null) {
            return false;
        }
        if (stereotypes.contains(element.toString())) {
            return true;
        }
        Set<? extends AnnotationMirror> annotationMirrors = getAllAnnotationMirrors(element);
        for (AnnotationMirror ann : annotationMirrors) {
            DeclaredType annotationType = ann.getAnnotationType();
            String annotationTypeString = annotationType.toString();
            if (stereotypes.contains(annotationTypeString)) {
                return true;
            } else if (!INTERNAL_ANNOTATION_NAMES.contains(
                    annotationType.asElement().getSimpleName().toString())) {
                Element annotationTypeElement = annotationType.asElement();
                if (hasStereotype(annotationTypeElement, stereotypes)) {
                    return true;
                }
            }
        }
        if (element instanceof ExecutableElement) {
            ExecutableElement executableElement = (ExecutableElement) element;
            if (findAnnotation(element, Override.class) != null) {
                ExecutableElement overridden = findOverriddenMethod(executableElement);
                if (overridden != null) {
                    return hasStereotype(overridden, stereotypes);
                }
            }
        }
        return false;
    }

    /**
     * Return all the {@link AnnotationMirror} for the given element, searching the type hierarchy if necessary
     *
     * @param element The element
     * @return A set of {@link AnnotationMirror}
     */
    Set<? extends AnnotationMirror> getAllAnnotationMirrors(Element element) {
        Set<AnnotationMirror> mirrors = new HashSet<>();
        mirrors.addAll(elementUtils.getAllAnnotationMirrors(element));
        populateAnnotationMirrors(element, mirrors);
        return mirrors;
    }

    /**
     * Finds an annotation for the given class element and stereotypes. A stereotype is a meta annotation on another annotation.
     *
     * @param element     The element to search
     * @param stereotypes The stereotypes to look for
     * @return An array of matching {@link AnnotationMirror}
     */
    AnnotationMirror[] findAnnotationsWithStereotypeInHierarchy(Element element, String... stereotypes) {
        if (element == null) {
            return new AnnotationMirror[0];
        }
        List<String> stereoTypeList = Arrays.asList(stereotypes);
        List<AnnotationMirror> annotationMirrorList = new ArrayList<>();
        Set<? extends AnnotationMirror> annotationMirrors = getAllAnnotationMirrors(element);
        for (AnnotationMirror ann : annotationMirrors) {
            DeclaredType annotationType = ann.getAnnotationType();
            String annotationTypeString = annotationType.toString();
            if (stereoTypeList.contains(annotationTypeString)) {
                annotationMirrorList.add(ann);
            } else {
                Element annotationElement = annotationType.asElement();
                if (!INTERNAL_ANNOTATION_NAMES.contains(
                        annotationElement.getSimpleName().toString())) {
                    if (hasStereotype(annotationElement, stereotypes)) {
                        annotationMirrorList.add(ann);
                    }
                }
            }
        }
        if (element instanceof ExecutableElement && annotationMirrorList.isEmpty()) {
            ExecutableElement executableElement = (ExecutableElement) element;
            if (findAnnotation(element, Override.class) != null) {
                ExecutableElement overridden = findOverriddenMethod(executableElement);
                if (overridden != null) {
                    return findAnnotationsWithStereotypeInHierarchy(overridden, stereotypes);
                }
            }
        }
        return annotationMirrorList.toArray(new AnnotationMirror[annotationMirrorList.size()]);
    }

    /**
     * Finds an annotation for the given class element and stereotype. A stereotype is a meta annotation on another annotation.
     *
     * @param element    The element to search
     * @param stereotype The stereotype to look for
     * @return An array of matching {@link AnnotationMirror}
     */
    Optional<AnnotationMirror> findAnnotationWithStereotypeInHierarchy(Element element, Class<? extends Annotation> stereotype) {
        return findAnnotationWithStereotypeInHierarchy(element, stereotype.getName());
    }

    /**
     * Finds an annotation for the given class element and stereotype. This method will search the current class and all super classes. A stereotype is a meta annotation on another annotation.
     *
     * @param element    The element to search
     * @param stereotype The stereotype to look for
     * @return An array of matching {@link AnnotationMirror}
     */
    Optional<AnnotationMirror> findAnnotationWithStereotypeInHierarchy(Element element, String stereotype) {
        Set<? extends AnnotationMirror> annotationMirrors = getAllAnnotationMirrors(element);
        for (AnnotationMirror ann : annotationMirrors) {
            DeclaredType annotationType = ann.getAnnotationType();
            if (stereotype.equals(annotationType.toString())) {
                return Optional.of(ann);
            } else if (!INTERNAL_ANNOTATION_NAMES.contains(annotationType.asElement().getSimpleName().toString())) {
                if (findAnnotationWithStereotypeInHierarchy(annotationType.asElement(), stereotype).isPresent()) {
                    return Optional.of(ann);
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Finds an annotation for the given class element and stereotype. A stereotype is a meta annotation on another annotation.
     *
     * @param element    The element to search
     * @param stereotype The stereotype to look for
     * @return An array of matching {@link AnnotationMirror}
     */
    Optional<AnnotationMirror> findAnnotationWithStereotype(Element element, Class<? extends Annotation> stereotype) {
        return findAnnotationWithStereotype(element, stereotype.getName());
    }

    /**
     * Finds an annotation for the given class element and stereotype. This method will search only the current class and not the entire hierarchy
     *
     * @param element    The element to search
     * @param stereotype The stereotype to look for
     * @return An array of matching {@link AnnotationMirror}
     */
    Optional<AnnotationMirror> findAnnotationWithStereotype(Element element, String stereotype) {
        List<? extends AnnotationMirror> annotationMirrors = elementUtils.getAllAnnotationMirrors(element);
        for (AnnotationMirror ann : annotationMirrors) {
            DeclaredType annotationType = ann.getAnnotationType();
            if (stereotype.equals(annotationType.toString())) {
                return Optional.of(ann);
            } else if (!INTERNAL_ANNOTATION_NAMES.contains(annotationType.asElement().getSimpleName().toString())) {
                if (findAnnotationWithStereotype(annotationType.asElement(), stereotype).isPresent()) {
                    return Optional.of(ann);
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Finds an annotation for the given element by type. A stereotype is a meta annotation on another annotation.
     *
     * @param element        The element to search
     * @param annotationType The stereotype to look for
     * @return An array of matching {@link AnnotationMirror}
     */
    Optional<AnnotationMirror> findAnnotation(Element element, Class<? extends Annotation> annotationType) {
        return findAnnotationWithStereotypeInHierarchy(element, annotationType.getName());
    }

    /**
     * Finds an annotation for the given class element and stereotype. A stereotype is a meta annotation on another annotation.
     *
     * @param element    The element to search
     * @param stereotype The stereotype to look for
     * @return An array of matching {@link AnnotationMirror}
     */
    Optional<AnnotationMirror> findAnnotation(Element element, String stereotype) {
        Set<? extends AnnotationMirror> annotationMirrors = getAllAnnotationMirrors(element);
        for (AnnotationMirror ann : annotationMirrors) {
            DeclaredType annotationType = ann.getAnnotationType();
            if (stereotype.equals(annotationType.toString())) {
                return Optional.of(ann);
            } else if (!INTERNAL_ANNOTATION_NAMES.contains(annotationType.asElement().getSimpleName().toString())) {
                Optional<AnnotationMirror> found = findAnnotation(annotationType.asElement(), stereotype);
                if (found.isPresent()) {
                    return found;
                }
            }
        }
        return Optional.empty();
    }


    /**
     * Resolve the {@link Qualifier} to use for the given element
     * @param element The element
     * @return The Qualifier or null
     */
    String resolveQualifier(Element element) {
        Optional<AnnotationMirror> qualifier = findAnnotationWithStereotypeInHierarchy(element, Qualifier.class);
        return qualifier.map(val -> val.getAnnotationType().toString()).orElse(null);
    }

    /**
     * Resolves the attribute value as a string from the given mirror
     *
     * @param mirror The mirror
     * @param attributeName The attribute name
     * @return A string value
     */
    Optional<String> getAnnotationAttributeValueAsString(AnnotationMirror mirror, String attributeName) {
        Map<? extends ExecutableElement, ? extends AnnotationValue> annValues = mirror.getElementValues();
        if (annValues.isEmpty()) {
            return Optional.empty();
        }
        Optional<? extends ExecutableElement> executableElement = annValues.keySet().stream()
                .filter(execElem -> execElem.getSimpleName().toString().equals(attributeName))
                .findFirst();

        return Optional.ofNullable(
                executableElement.map(executableElement1 -> annValues.get(executableElement1).getValue().toString()).orElse(null)
        );
    }

    /**
     * Resolves all of the attribute values from an annotation of the given type. This method will merge values from the type hierarchy into a single view for all annotations of the given type
     *
     * @param attributeType The type The required type of the annotation values
     * @param element The element to search
     * @param stereotype The annotation stereotype
     * @param <T> The {@link OptionalValues}
     * @return An {@link OptionalValues}
     */
    <T> OptionalValues<T> resolveAttributesOfStereotype(Class<T> attributeType, Element element, String stereotype) {
        List<AnnotationMirror> mirrors = Arrays.asList(findAnnotationsWithStereotypeInHierarchy(element, stereotype));
        Collections.reverse(mirrors);
        if(!mirrors.isEmpty()) {
            Map<CharSequence, T> newValues = new LinkedHashMap<>();
            for (AnnotationMirror annotationMirror : mirrors) {
                TypeElement typeElement = (TypeElement) annotationMirror.getAnnotationType().asElement();
                if(!typeElement.getQualifiedName().toString().equals(stereotype)) {
                    annotationMirror = findAnnotation( typeElement, stereotype).orElse(null);
                }
                if(annotationMirror != null) {
                    Map<? extends ExecutableElement, ? extends AnnotationValue> values = annotationMirror.getElementValues();
                    values.forEach((exec, value) -> {
                        String key = exec.getSimpleName().toString();
                        if (value != null) {
                            Object v = value.getValue();
                            if (v != null && attributeType.isInstance(v)) {
                                newValues.put(key, (T) v);
                            }
                        }
                    });
                }
            }
            return OptionalValues.of(attributeType, newValues);
        }
        return OptionalValues.empty();
    }

    /**
     * Return whether the attribute on the given annotation is present
     *
     * @param annotation The annotation
     * @param attribute The attribute
     * @return True if it is
     */
    boolean isAttributePresent(AnnotationMirror annotation, String attribute) {
        return getAnnotationAttributeValueAsString(annotation, attribute).isPresent();
    }

    private ExecutableElement findOverriddenInterfaceMethod(ExecutableElement executableElement, TypeElement thisType) {

        ExecutableElement overridden = null;
        TypeElement supertype = thisType;
        while (supertype != null && !supertype.toString().equals(Object.class.getName())) {
            List<? extends TypeMirror> interfaces = supertype.getInterfaces();

            for (TypeMirror anInterface : interfaces) {
                if (anInterface instanceof DeclaredType) {
                    DeclaredType iElement = (DeclaredType) anInterface;
                    Optional<ExecutableElement> result = findOverridden(executableElement, (TypeElement) iElement.asElement());
                    if (result.isPresent()) {
                        overridden = result.get();
                        break;
                    } else {
                        overridden = findOverriddenInterfaceMethod(executableElement, (TypeElement) iElement.asElement());
                        if (overridden != null) break;
                    }
                }
            }
            TypeMirror superMirror = supertype.getSuperclass();
            if (superMirror instanceof DeclaredType) {
                supertype = (TypeElement) ((DeclaredType) superMirror).asElement();
            } else {
                break;
            }
        }
        return overridden;
    }


    private Optional<ExecutableElement> findOverridden(ExecutableElement executableElement, TypeElement supertype) {
        Stream<? extends Element> elements = supertype.getEnclosedElements().stream();
        return elements.filter(el -> el.getKind() == ElementKind.METHOD && el.getEnclosingElement().equals(supertype))
                .map(el -> (ExecutableElement) el)
                .filter(method -> elementUtils.overrides(executableElement, method, (TypeElement) method.getEnclosingElement()))
                .findFirst();
    }

    private void populateAnnotationMirrors(Element element, Set<AnnotationMirror> mirrors) {
        while(element.getKind() == ElementKind.CLASS) {
            List<? extends AnnotationMirror> elementMirrors = element.getAnnotationMirrors();
            mirrors.addAll(elementMirrors);
            TypeElement typeElement = (TypeElement) element;
            List<? extends TypeMirror> interfaces = typeElement.getInterfaces();
            for (TypeMirror anInterface : interfaces) {
                if(anInterface instanceof DeclaredType) {

                    Element interfaceElement = ((DeclaredType) anInterface).asElement();

                    List<? extends AnnotationMirror> interfaceAnnotationMirrors = interfaceElement.getAnnotationMirrors();
                    mirrors.addAll(interfaceAnnotationMirrors);
                    populateAnnotationMirrors(interfaceElement, mirrors);
                }
            }
            TypeMirror superMirror = typeElement.getSuperclass();
            if(superMirror instanceof DeclaredType) {
                DeclaredType type = (DeclaredType) superMirror;
                element = type.asElement();
            }
            else {
                break;
            }
        }
    }

    private ExecutableElement findOverriddenMethod(ExecutableElement executableElement) {
        ExecutableElement overridden = null;
        Element enclosingElement = executableElement.getEnclosingElement();
        if (enclosingElement instanceof TypeElement) {
            TypeElement thisType = (TypeElement) enclosingElement;
            TypeMirror superMirror = thisType.getSuperclass();
            TypeElement supertype = superMirror instanceof TypeElement ? (TypeElement) superMirror : null;
            while (supertype != null && !supertype.toString().equals(Object.class.getName())) {
                Optional<ExecutableElement> result = findOverridden(executableElement, supertype);
                if (result.isPresent()) {
                    overridden = result.get();
                    break;
                }
                else {
                    overridden = findOverriddenInterfaceMethod(executableElement, supertype);

                }
                supertype = (TypeElement) supertype.getSuperclass();
            }
            if (overridden == null) {
                overridden = findOverriddenInterfaceMethod(executableElement, thisType);
            }
        }
        return overridden;
    }


}
