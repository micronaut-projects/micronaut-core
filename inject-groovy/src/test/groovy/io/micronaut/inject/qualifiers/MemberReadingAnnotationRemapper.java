package io.micronaut.inject.qualifiers;

import java.util.List;

import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.inject.annotation.AnnotationRemapper;
import io.micronaut.inject.ast.AnnotationElement;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.Element;
import io.micronaut.inject.visitor.VisitorContext;

/**
 * A remapper that reads the metadata of the members of every annotation declared in the
 * {@code remapreentry} package, the way a remapper deriving an annotation's semantics from its
 * declaration does. Reading a member's metadata reads the annotations on it, and a member annotated
 * with an annotation whose own members are annotated with the first one reads the first one again.
 */
public class MemberReadingAnnotationRemapper implements AnnotationRemapper {

    public static final String PACKAGE = "remapreentry";

    @Override
    public String getPackageName() {
        return ALL_PACKAGES;
    }

    @Override
    public List<AnnotationValue<?>> remap(AnnotationValue<?> annotation, VisitorContext visitorContext) {
        String annotationName = annotation.getAnnotationName();
        if (annotationName.startsWith(PACKAGE + '.')) {
            visitorContext.getClassElement(annotationName).ifPresent(MemberReadingAnnotationRemapper::readMembers);
        }
        return List.of(annotation);
    }

    private static void readMembers(ClassElement annotationType) {
        List<? extends Element> members = annotationType instanceof AnnotationElement annotationElement
            ? annotationElement.getMembers()
            : annotationType.getMethods();
        for (Element member : members) {
            member.getAnnotationMetadata().getAnnotationNames();
        }
    }
}
