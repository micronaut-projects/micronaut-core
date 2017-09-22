package org.particleframework.annotation.processing;

import javax.inject.Qualifier;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.util.Elements;
import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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

    AnnotationMirror findAnnotationWithStereotype(Element classElement, Class<? extends Annotation> stereotype) {
        return findAnnotationWithStereotype(classElement, stereotype.getName());
    }

    AnnotationMirror findAnnotationWithStereotype(Element classElement, String stereotype) {
        List<? extends AnnotationMirror> annotationMirrors = elementUtils.getAllAnnotationMirrors(classElement);
        for (AnnotationMirror ann: annotationMirrors) {
            DeclaredType annotationType = ann.getAnnotationType();
            if (stereotype.equals(annotationType.toString())) {
                return ann;
            } else if (!Arrays.asList("Retention", "Documented", "Target").contains(annotationType.asElement().getSimpleName().toString())) {
                if (findAnnotationWithStereotype(annotationType.asElement(), stereotype) != null) {
                    return ann;
                }
            }
        }
        return null;
    }

    Object resolveQualifier(Element element) {
        AnnotationMirror qualifier = findAnnotationWithStereotype(element, Qualifier.class);
        return qualifier == null ? null : qualifier.getAnnotationType().toString();
    }

    // TODO this needs a test
    Optional<String> getAnnotationElementValue(String elementName, AnnotationMirror annMirror) {
        Map<? extends ExecutableElement, ? extends AnnotationValue> annValues = annMirror.getElementValues();
        if (annValues.isEmpty()) {
            return Optional.empty();
        }
        Optional<? extends ExecutableElement> executableElement = annValues.keySet().stream()
            .filter(execElem -> execElem.getSimpleName().toString().equals(elementName))
            .findFirst();

        return Optional.ofNullable(
            executableElement.isPresent()
                ? annValues.get(executableElement.get()).getValue().toString()
                : null
        );
    }
}
