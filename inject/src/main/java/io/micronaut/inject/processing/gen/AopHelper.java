package io.micronaut.inject.processing.gen;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.TypedElement;
import io.micronaut.inject.configuration.ConfigurationMetadataBuilder;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.inject.writer.BeanDefinitionVisitor;

import java.util.concurrent.atomic.AtomicInteger;

public interface AopHelper {

    @Nullable
    BeanDefinitionVisitor visitAdaptedMethod(ClassElement classElement,
                                                                MethodElement sourceMethod,
                                                                ConfigurationMetadataBuilder metadataBuilder,
                                                                AtomicInteger adaptedMethodIndex,
                                                                VisitorContext visitorContext);

    BeanDefinitionVisitor createIntroductionAdviceWriter(ClassElement typeElement,
                                                         ConfigurationMetadataBuilder metadataBuilder,
                                                         VisitorContext visitorContext);


    boolean isAopProxyWriter(BeanDefinitionVisitor existingWriter);

    BeanDefinitionVisitor createAopProxyWriter(BeanDefinitionVisitor existingWriter,
                                               AnnotationMetadata producedAnnotationMetadata,
                                               ConfigurationMetadataBuilder metadataBuilder,
                                               VisitorContext visitorContext, boolean isMethod);
    void visitAroundMethod(BeanDefinitionVisitor existingWriter, TypedElement beanType, MethodElement methodElement);
}
