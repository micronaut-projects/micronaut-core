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
        List<? extends AnnotationMirror> annotationMirrors = elementUtils.getAllAnnotationMirrors(classElement);
        for (AnnotationMirror ann: annotationMirrors) {
            DeclaredType annotationType = ann.getAnnotationType();
            if (stereotypes.contains(annotationType.toString())) {
                return true;
            } else if (!Arrays.asList("Retention", "Documented", "Target").contains(
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
    Optional<String> getAnnotationElementValue(String elementValue, AnnotationMirror annMirror) {
        Map<? extends ExecutableElement, ? extends AnnotationValue> annValues = annMirror.getElementValues();
        if (annValues.isEmpty()) {
            return Optional.empty();
        }
        Optional<? extends ExecutableElement> executableElement = annMirror.getElementValues().keySet().stream()
            .filter(execElem -> execElem.toString().equals(elementValue))
            .findFirst();

        return Optional.ofNullable(
            executableElement.isPresent()
                ? annMirror.getElementValues().get(executableElement.get()).getValue().toString()
                : null
        );
    }
}
