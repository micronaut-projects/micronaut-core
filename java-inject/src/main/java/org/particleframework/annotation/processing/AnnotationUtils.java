package org.particleframework.annotation.processing;

import org.particleframework.core.convert.OptionalValues;

import javax.inject.Qualifier;
import javax.lang.model.AnnotatedConstruct;
import javax.lang.model.element.*;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import java.lang.annotation.Annotation;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Stream;

class AnnotationUtils {

    private static final List<String> IGNORED_ANNOTATIONS = Arrays.asList("Retention", "Documented", "Target");

    private final Elements elementUtils;

    AnnotationUtils(Elements elementUtils) {
        this.elementUtils = elementUtils;
    }

    boolean hasStereotype(Element classElement, Class<? extends Annotation> stereotype) {
        return hasStereotype(classElement, stereotype.getName());
    }

    boolean hasStereotype(Element classElement, String... stereotypes) {
        return hasStereotype(classElement, Arrays.asList(stereotypes));
    }

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
            } else if (!IGNORED_ANNOTATIONS.contains(
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

    Set<? extends AnnotationMirror> getAllAnnotationMirrors(Element element) {
        Set<AnnotationMirror> mirrors = new HashSet<>();
        mirrors.addAll(elementUtils.getAllAnnotationMirrors(element));
        populateAnnotationMirrors(element, mirrors);
        return mirrors;
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

    /**
     * Finds an annotation for the given class element and stereotypes. A stereotype is a meta annotation on another annotation.
     *
     * @param element     The element to search
     * @param stereotypes The stereotypes to look for
     * @return An array of matching {@link AnnotationMirror}
     */
    AnnotationMirror[] findAnnotationsWithStereotype(Element element, String... stereotypes) {
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
                if (!IGNORED_ANNOTATIONS.contains(
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
                    return findAnnotationsWithStereotype(overridden, stereotypes);
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
    Optional<AnnotationMirror> findAnnotationWithStereotype(Element element, Class<? extends Annotation> stereotype) {
        return findAnnotationWithStereotype(element, stereotype.getName());
    }

    /**
     * Finds an annotation for the given class element and stereotype. A stereotype is a meta annotation on another annotation.
     *
     * @param element    The element to search
     * @param stereotype The stereotype to look for
     * @return An array of matching {@link AnnotationMirror}
     */
    Optional<AnnotationMirror> findAnnotationWithStereotype(Element element, String stereotype) {
        Set<? extends AnnotationMirror> annotationMirrors = getAllAnnotationMirrors(element);
        for (AnnotationMirror ann : annotationMirrors) {
            DeclaredType annotationType = ann.getAnnotationType();
            if (stereotype.equals(annotationType.toString())) {
                return Optional.of(ann);
            } else if (!Arrays.asList("Retention", "Documented", "Target").contains(annotationType.asElement().getSimpleName().toString())) {
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
        return findAnnotationWithStereotype(element, annotationType.getName());
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
            } else if (!Arrays.asList("Retention", "Documented", "Target").contains(annotationType.asElement().getSimpleName().toString())) {
                Optional<AnnotationMirror> found = findAnnotation(annotationType.asElement(), stereotype);
                if (found.isPresent()) {
                    return found;
                }
            }
        }
        return Optional.empty();
    }

    Object resolveQualifier(Element element) {
        Optional<AnnotationMirror> qualifier = findAnnotationWithStereotype(element, Qualifier.class);
        return qualifier.map(val -> val.getAnnotationType().toString()).orElse(null);
    }

    // TODO this needs a test
    Optional<String> getAnnotationAttributeValue(AnnotationMirror annMirror, String attributeName) {
        Map<? extends ExecutableElement, ? extends AnnotationValue> annValues = annMirror.getElementValues();
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
     * Resolves all of the attribute values from an annotation of the given type
     * @param type The type
     * @param element The element
     * @param annotationType The annotation type
     * @param <T> The {@link OptionalValues}
     * @return An {@link OptionalValues}
     */
    public <T> OptionalValues<T> resolveAttributesOfType(Class<T> type, Element element, String annotationType) {
        return findAnnotation(element, annotationType)
                .map(annotationMirror -> {
                    Map<? extends ExecutableElement, ? extends AnnotationValue> values = annotationMirror.getElementValues();
                    Map<CharSequence, T> newValues = new LinkedHashMap<>();
                    values.forEach((exec, value) -> {
                        String key = exec.getSimpleName().toString();
                        if (value != null) {
                            Object v = value.getValue();
                            if (v != null && type.isInstance(v)) {
                                newValues.put(key, (T) v);
                            }
                        }
                    });
                    return newValues;
                })
                .map(charSequenceTMap -> OptionalValues.of(type, charSequenceTMap))
                .orElse(OptionalValues.empty());
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

    /**
     * Return whether the attribute on the given annotation is present
     *
     * @param annotation The annotation
     * @param attribute The attribute
     * @return True if it is
     */
    public boolean isAttributePresent(AnnotationMirror annotation, String attribute) {
        return getAnnotationAttributeValue(annotation, attribute).isPresent();
    }


}
