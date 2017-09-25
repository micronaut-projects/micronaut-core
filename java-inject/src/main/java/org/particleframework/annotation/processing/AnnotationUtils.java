package org.particleframework.annotation.processing;

import javax.inject.Qualifier;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.util.Elements;
import java.lang.annotation.Annotation;
import java.util.*;

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

    boolean hasStereotype(Element classElement, List<String> stereotypes) {
        if(classElement == null) {
            return false;
        }
        if(stereotypes.contains(classElement.toString())) {
            return true;
        }
        List<? extends AnnotationMirror> annotationMirrors = elementUtils.getAllAnnotationMirrors(classElement);
        for (AnnotationMirror ann: annotationMirrors) {
            DeclaredType annotationType = ann.getAnnotationType();
            String annotationTypeString = annotationType.toString();
            if (stereotypes.contains(annotationTypeString)) {
                return true;
            } else if (!IGNORED_ANNOTATIONS.contains(
                annotationType.asElement().getSimpleName().toString())) {
                if (hasStereotype(annotationType.asElement(), stereotypes)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Finds an annotation for the given class element and stereotypes. A stereotype is a meta annotation on another annotation.
     * @param element The element to search
     * @param stereotypes The stereotypes to look for
     * @return An array of matching {@link AnnotationMirror}
     */
    AnnotationMirror[] findAnnotationsWithStereotype(Element element, String...stereotypes) {
        if(element == null) {
            return new AnnotationMirror[0];
        }
        List<String> stereoTypeList = Arrays.asList(stereotypes);
        List<AnnotationMirror> annotationMirrorList = new ArrayList<>();
        List<? extends AnnotationMirror> annotationMirrors = elementUtils.getAllAnnotationMirrors(element);
        for (AnnotationMirror ann: annotationMirrors) {
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
        return annotationMirrorList.toArray(new AnnotationMirror[annotationMirrorList.size()]);
    }

    /**
     * Finds an annotation for the given class element and stereotype. A stereotype is a meta annotation on another annotation.
     * @param element The element to search
     * @param stereotype The stereotype to look for
     * @return An array of matching {@link AnnotationMirror}
     */
    Optional<AnnotationMirror> findAnnotationWithStereotype(Element element, Class<? extends Annotation> stereotype) {
        return findAnnotationWithStereotype(element, stereotype.getName());
    }

    /**
     * Finds an annotation for the given class element and stereotype. A stereotype is a meta annotation on another annotation.
     * @param element The element to search
     * @param stereotype The stereotype to look for
     * @return An array of matching {@link AnnotationMirror}
     */
    Optional<AnnotationMirror> findAnnotationWithStereotype(Element element, String stereotype) {
        List<? extends AnnotationMirror> annotationMirrors = elementUtils.getAllAnnotationMirrors(element);
        for (AnnotationMirror ann: annotationMirrors) {
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
     * @param element The element to search
     * @param annotationType The stereotype to look for
     * @return An array of matching {@link AnnotationMirror}
     */
    Optional<AnnotationMirror> findAnnotation(Element element, Class<? extends Annotation> annotationType) {
        return findAnnotationWithStereotype(element, annotationType.getName());
    }

    /**
     * Finds an annotation for the given class element and stereotype. A stereotype is a meta annotation on another annotation.
     * @param element The element to search
     * @param stereotype The stereotype to look for
     * @return An array of matching {@link AnnotationMirror}
     */
    Optional<AnnotationMirror> findAnnotation(Element element, String stereotype) {
        List<? extends AnnotationMirror> annotationMirrors = elementUtils.getAllAnnotationMirrors(element);
        for (AnnotationMirror ann: annotationMirrors) {
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
        return qualifier.map( val -> val.getAnnotationType().toString()).orElse(null);
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

    public boolean isAttributeTrue(Element element, String annotationType, String attributeName) {
        return findAnnotation(element, annotationType)
                .map(annotationMirror -> {
                    Map<? extends ExecutableElement, ? extends AnnotationValue> values = annotationMirror.getElementValues();
                    Optional<? extends ExecutableElement> foundElement = values.keySet().stream()
                            .filter(execElem -> execElem.getSimpleName().toString().equals(attributeName))
                            .findFirst();
                    return foundElement.map(exec ->
                            {
                                AnnotationValue annotationValue = values.get(exec);
                                if(annotationValue != null) {
                                    Object value = annotationValue.getValue();
                                    if(value instanceof Boolean) {
                                        return (Boolean) value;
                                    }
                                }
                                return false;
                            }
                    ).orElse(false);

                }
        ).orElse(false);
    }
}
