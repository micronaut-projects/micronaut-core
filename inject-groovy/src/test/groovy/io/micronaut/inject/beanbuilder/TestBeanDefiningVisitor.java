package io.micronaut.inject.beanbuilder;

import io.micronaut.aop.InterceptorBean;
import io.micronaut.aop.InterceptorBinding;
import io.micronaut.aop.InterceptorBindingDefinitions;
import io.micronaut.aop.InterceptorKind;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Executable;
import io.micronaut.context.env.Environment;
import io.micronaut.core.annotation.AnnotationUtil;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.ElementQuery;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.beans.BeanFieldElement;
import io.micronaut.inject.visitor.TypeElementVisitor;
import io.micronaut.inject.visitor.VisitorContext;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class TestBeanDefiningVisitor implements TypeElementVisitor<SomeInterceptor, AroundInvoke> {

    private ClassElement classElement;
    private Set<String> interceptorBindings = Collections.emptySet();

    @Override
    public void visitClass(ClassElement element, VisitorContext context) {
        this.interceptorBindings = new HashSet<>(element.getAnnotationNamesByStereotype(AnnotationUtil.ANN_INTERCEPTOR_BINDINGS));
        element.removeStereotype(AnnotationUtil.ANN_INTERCEPTOR_BINDINGS);
        element.removeAnnotation(AnnotationUtil.ANN_INTERCEPTOR_BINDINGS);
        element.annotate(Bean.class);

        this.classElement = element;
    }

    @NonNull
    @Override
    public VisitorKind getVisitorKind() {
        return VisitorKind.ISOLATING;
    }

    @Override
    public void visitMethod(MethodElement element, VisitorContext context) {
        element.annotate(Executable.class);
        context.getClassElement(TestInterceptorAdapter.class)
                .ifPresent(type ->
                        element.addAssociatedBean(type)
                                .annotate(InterceptorBean.class)
                                .annotate(InterceptorBindingDefinitions.class, (builder) -> {
                                    final AnnotationValue[] annotationValues = interceptorBindings.stream()
                                            .map(name -> AnnotationValue.builder(InterceptorBinding.class)
                                                    .value(name)
                                                    .member("kind", InterceptorKind.AROUND).build()
                                            ).toArray(AnnotationValue[]::new);
                                    builder.values(annotationValues);
                                })
                                .typeArguments(classElement)
                                .qualifier("test")
                                .withParameters((parameters) -> {
                                    parameters[0].typeArguments(classElement);
                                    parameters[1].injectValue(element.getName());
                                })
                                .withMethods(
                                    ElementQuery.ALL_METHODS.named(name -> name.equals("testMethod")),
                                    (method) -> method.inject()
                                          .withParameters((parameters) ->
                                                parameters[1].injectValue("test")
                                          )
                                )
                                .withFields(
                                    ElementQuery.ALL_FIELDS.typed((ce) -> ce.isAssignable(Environment.class)),
                                    BeanFieldElement::inject
                                )
        );
    }
}
