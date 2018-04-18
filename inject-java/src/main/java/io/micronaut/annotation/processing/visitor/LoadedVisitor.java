package io.micronaut.annotation.processing.visitor;

import io.micronaut.annotation.processing.AnnotationUtils;
import io.micronaut.annotation.processing.GenericUtils;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.reflect.GenericTypeUtils;
import io.micronaut.inject.visitor.TypeElementVisitor;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import java.util.List;

public class LoadedVisitor {

    private final TypeElementVisitor visitor;
    private final AnnotationUtils annotationUtils;
    private final String classAnnotation;
    private final String elementAnnotation;
    private final JavaVisitorContext visitorContext;

    public LoadedVisitor(TypeElementVisitor visitor,
                         JavaVisitorContext visitorContext,
                         GenericUtils genericUtils,
                         ProcessingEnvironment processingEnvironment,
                         AnnotationUtils annotationUtils) {
        this.visitorContext = visitorContext;
        this.visitor = visitor;
        this.annotationUtils = annotationUtils;
        TypeElement typeElement = processingEnvironment.getElementUtils().getTypeElement(visitor.getClass().getName());
        List<? extends  TypeMirror> generics = genericUtils.interfaceGenericTypesFor(typeElement, TypeElementVisitor.class.getName());
        classAnnotation = generics.get(0).toString();
        elementAnnotation = generics.get(1).toString();
    }

    public boolean matches(TypeElement typeElement) {
        if (classAnnotation.equals("java.lang.Object")) {
            return true;
        }
        AnnotationMetadata annotationMetadata = annotationUtils.getAnnotationMetadata(typeElement);
        return annotationMetadata.hasAnnotation(classAnnotation);
    }

    public boolean matches(AnnotationMetadata annotationMetadata) {
        if (elementAnnotation.equals("java.lang.Object")) {
            return true;
        }
        return annotationMetadata.hasDeclaredAnnotation(elementAnnotation);
    }

    public void visit(Element element, AnnotationMetadata annotationMetadata) {
        if (element instanceof VariableElement) {
            visitor.visitField(new JavaFieldElement((VariableElement) element), annotationMetadata, visitorContext);
        } else if (element instanceof ExecutableElement) {
            visitor.visitMethod(new JavaMethodElement((ExecutableElement) element), annotationMetadata, visitorContext);
        } else if (element instanceof TypeElement) {
            visitor.visitClass(new JavaClassElement((TypeElement) element), annotationMetadata, visitorContext);
        }
    }
}
