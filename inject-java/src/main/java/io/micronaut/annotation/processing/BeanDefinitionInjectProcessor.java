/*
 * Copyright 2017-2021 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.annotation.processing;

import io.micronaut.annotation.processing.visitor.JavaClassElement;
import io.micronaut.annotation.processing.visitor.JavaNativeElement;
import io.micronaut.context.annotation.ClassImport;
import io.micronaut.context.annotation.ConfigurationReader;
import io.micronaut.context.visitor.VisitorUtils;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationUtil;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Vetoed;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.inject.annotation.AbstractAnnotationMetadataBuilder;
import io.micronaut.inject.ast.UnresolvedTypeKind;
import io.micronaut.inject.ast.annotation.ElementAnnotationMetadataFactory;
import io.micronaut.inject.processing.BeanDefinitionCreator;
import io.micronaut.inject.processing.BeanDefinitionCreatorFactory;
import io.micronaut.inject.processing.JavaModelUtils;
import io.micronaut.inject.processing.ProcessingException;
import io.micronaut.inject.visitor.BeanElementVisitor;
import io.micronaut.inject.writer.AbstractBeanDefinitionBuilder;
import io.micronaut.inject.writer.BeanDefinitionVisitor;
import io.micronaut.inject.writer.BeanDefinitionWriter;

import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedOptions;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Name;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static javax.lang.model.element.ElementKind.ENUM;

/**
 * <p>The core annotation processor used to generate bean definitions and power AOP for Micronaut.</p>
 *
 * <p>Each dependency injection candidate is visited and {@link BeanDefinitionWriter} is used to produce byte code via ASM.
 * Each bean results in a instanceof {@link io.micronaut.inject.BeanDefinition}</p>
 *
 * @author Graeme Rocher
 * @author Dean Wette
 * @since 1.0
 */
@Internal
@SupportedOptions({AbstractInjectAnnotationProcessor.MICRONAUT_PROCESSING_INCREMENTAL, AbstractInjectAnnotationProcessor.MICRONAUT_PROCESSING_ANNOTATIONS, BeanDefinitionWriter.OMIT_CONFPROP_INJECTION_POINTS})
public class BeanDefinitionInjectProcessor extends AbstractInjectAnnotationProcessor {

    private static final String[] ANNOTATION_STEREOTYPES = new String[] {
        AnnotationUtil.POST_CONSTRUCT,
        AnnotationUtil.PRE_DESTROY,
        "jakarta.annotation.PreDestroy",
        "jakarta.annotation.PostConstruct",
        "jakarta.inject.Inject",
        "jakarta.inject.Qualifier",
        "jakarta.inject.Singleton",
        "jakarta.inject.Inject",
        "jakarta.inject.Qualifier",
        "jakarta.inject.Singleton",
        "io.micronaut.context.annotation.Bean",
        "io.micronaut.context.annotation.Replaces",
        "io.micronaut.context.annotation.Value",
        "io.micronaut.context.annotation.Property",
        "io.micronaut.context.annotation.Executable",
        ClassImport.class.getName(),
        AnnotationUtil.ANN_AROUND,
        AnnotationUtil.ANN_INTERCEPTOR_BINDINGS,
        AnnotationUtil.ANN_INTERCEPTOR_BINDING,
        AnnotationUtil.ANN_INTRODUCTION
    };

    private Set<String> beanDefinitions;
    private final Set<String> processed = new HashSet<>();
    private final Map<String, PostponeToNextRoundException> postponed = new HashMap<>();

    @Override
    public final synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        this.beanDefinitions = new LinkedHashSet<>();

        for (BeanElementVisitor<?> visitor : BeanElementVisitor.VISITORS) {
            if (visitor.isEnabled()) {
                try {
                    visitor.start(javaVisitorContext);
                } catch (Exception e) {
                    javaVisitorContext.fail("Error initializing bean element visitor [" + visitor.getClass().getName() + "]: " + e.getMessage(), null);
                }
            }
        }
    }

    @Override
    public final boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        boolean processingOver = roundEnv.processingOver();
        if (!processingOver) {
            JavaAnnotationMetadataBuilder annotationMetadataBuilder = javaVisitorContext.getAnnotationMetadataBuilder();
            annotations = annotations
                .stream()
                .filter(ann -> {
                    final String name = ann.getQualifiedName().toString();
                    String packageName = NameUtils.getPackageName(name);
                    return !name.equals(AnnotationUtil.KOTLIN_METADATA) && !AnnotationUtil.STEREOTYPE_EXCLUDES.contains(packageName);
                })
                .filter(ann -> annotationMetadataBuilder.lookupOrBuildForType(ann).hasStereotype(ANNOTATION_STEREOTYPES)
                    || isProcessedAnnotation(ann.getQualifiedName().toString()))
                .collect(Collectors.toSet());

            if (!annotations.isEmpty()) {
                TypeElement groovyObjectTypeElement = elementUtils.getTypeElement("groovy.lang.GroovyObject");
                TypeMirror groovyObjectType = groovyObjectTypeElement != null ? groovyObjectTypeElement.asType() : null;
                // accumulate all the class elements for all annotated elements
                annotations.forEach(annotation -> modelUtils.resolveTypeElements(
                        roundEnv.getElementsAnnotatedWith(annotation)
                    )
                    .flatMap(typeElement -> {
                        if (annotation.getQualifiedName().toString().equals(ClassImport.class.getName())) {
                            ElementAnnotationMetadataFactory annotationMetadataFactory = javaVisitorContext.getElementAnnotationMetadataFactory().readOnly();
                            JavaClassElement classElement = javaVisitorContext.getElementFactory()
                                .newClassElement(typeElement, annotationMetadataFactory);
                            return VisitorUtils.collectImportedElements(classElement, javaVisitorContext)
                                .stream().map(e -> ((JavaClassElement) e).getNativeType().element());
                        }
                        return Stream.of(typeElement);
                    })
                    .forEach(typeElement -> {
                        if (typeElement.getKind() == ENUM) {
                            final AnnotationMetadata am = annotationMetadataBuilder.lookupOrBuildForType(typeElement);
                            if (BeanDefinitionCreatorFactory.isDeclaredBeanInMetadata(am)) {
                                error(typeElement, "Enum types cannot be defined as beans");
                            }
                            return;
                        }
                        // skip Groovy code, handled by InjectTransform. Required for GroovyEclipse compiler
                        if ((groovyObjectType != null && typeUtils.isAssignable(typeElement.asType(), groovyObjectType))) {
                            return;
                        }

                        String name = typeElement.getQualifiedName().toString();
                        if (beanDefinitions.contains(name) || processed.contains(name) || name.endsWith(BeanDefinitionVisitor.PROXY_SUFFIX)) {
                            return;
                        }
                        boolean isInterface = JavaModelUtils.resolveKind(typeElement, ElementKind.INTERFACE).isPresent();
                        if (!isInterface) {
                            beanDefinitions.add(name);
                        } else {
                            AnnotationMetadata annotationMetadata = annotationMetadataBuilder.lookupOrBuildForType(typeElement);
                            if (BeanDefinitionCreatorFactory.isIntroduction(annotationMetadata) || annotationMetadata.hasStereotype(ConfigurationReader.class)) {
                                beanDefinitions.add(name);
                            }
                        }
                    }));
            }

            // remove already processed in previous round
            for (String name : processed) {
                beanDefinitions.remove(name);
                postponed.remove(name);
            }

            // process remaining
            int count = beanDefinitions.size();
            if (count > 0) {
                ElementAnnotationMetadataFactory annotationMetadataFactory = javaVisitorContext.getElementAnnotationMetadataFactory().readOnly();
                for (String className : beanDefinitions) {
                    if (processed.add(className)) {
                        final TypeElement typeElement = elementUtils.getTypeElement(className);
                        PostponeToNextRoundException nextRoundException = postponed.get(className);
                        if (nextRoundException != null) {
                            Object errorElement = nextRoundException.getErrorElement();
                            if (errorElement instanceof Element element) {
                                AbstractAnnotationMetadataBuilder.CachedAnnotationMetadata cachedAnnotationMetadata = javaVisitorContext.getAnnotationMetadataBuilder().lookupOrBuildForType(element);
                                if (!cachedAnnotationMetadata.wasCleared()) {
                                    AbstractAnnotationMetadataBuilder.clearMutated(errorElement);
                                }
                            }
                        }
                        try {
                            Name classElementQualifiedName = typeElement.getQualifiedName();
                            if ("java.lang.Record".equals(classElementQualifiedName.toString())) {
                                continue;
                            }
                            JavaClassElement classElement = javaVisitorContext.getElementFactory()
                                .newClassElement(typeElement, annotationMetadataFactory);
                            if (classElement.hasAnnotation(Vetoed.class) || classElement.getPackage().hasAnnotation(Vetoed.class)) {
                                continue;
                            }

                            if (!postponed.containsKey(classElement.getName()) && // don't throw again if it was already postponed
                                classElement.hasUnresolvedTypes(UnresolvedTypeKind.INTERFACE, UnresolvedTypeKind.SUPERCLASS)) {
                                throw new PostponeToNextRoundException(
                                    classElement.getNativeType().element(),
                                    classElement.getName()
                                );
                            }
                            BeanDefinitionCreator beanDefinitionCreator = BeanDefinitionCreatorFactory.produce(classElement, javaVisitorContext);
                            for (BeanDefinitionVisitor writer : beanDefinitionCreator.build()) {
                                if (processed.contains(writer.getBeanDefinitionName())) {
                                    throw new IllegalStateException("Already processed: " + writer.getBeanDefinitionName());
                                } else {
                                    processBeanDefinitions(writer);
                                    processed.add(writer.getBeanDefinitionName());
                                }
                            }
                        } catch (ProcessingException ex) {
                            error(((JavaNativeElement) ex.getOriginatingElement()).element(), ex.getMessage());
                        } catch (PostponeToNextRoundException e) {
                            processed.remove(className);
                            postponed.put(className, e);
                        }
                    }
                }
            }
        }

        /*
        Since the underlying Filer expects us to write only once into a file we need to make sure it happens in the last
        processing round.
        */
        if (processingOver) {
            for (Map.Entry<String, PostponeToNextRoundException> e : postponed.entrySet()) {
                javaVisitorContext.warn("Bean definition generation [" + e.getKey() + "] skipped from processing because of prior error: [" + e.getValue().getPath() + "]." +
                    " This error is normally due to missing classes on the classpath. Verify the compilation classpath is correct to resolve the problem.", (Element) e.getValue().getErrorElement());
            }

            try {
                writeBeanDefinitionsToMetaInf();
                for (BeanElementVisitor<?> visitor : BeanElementVisitor.VISITORS) {
                    if (visitor.isEnabled()) {
                        try {
                            visitor.finish(javaVisitorContext);
                        } catch (Exception e) {
                            javaVisitorContext.fail("Error finalizing bean element visitor [" + visitor.getClass().getName() + "]: " + e.getMessage(), null);
                        }
                    }
                }
                final List<AbstractBeanDefinitionBuilder> beanElementBuilders = javaVisitorContext.getBeanElementBuilders();
                if (CollectionUtils.isNotEmpty(beanElementBuilders)) {
                    try {
                        AbstractBeanDefinitionBuilder.writeBeanDefinitionBuilders(classWriterOutputVisitor, beanElementBuilders);
                    } catch (IOException e) {
                        // raise a compile error
                        String message = e.getMessage();
                        error("Unexpected error: %s", message != null ? message : e.getClass().getSimpleName());
                    }
                }
            } finally {
                BeanDefinitionWriter.finish();
            }
        }

        return false;
    }

    /**
     * Writes {@link io.micronaut.inject.BeanDefinitionReference} into /META-INF/services/io
     * .micronaut.inject.BeanDefinitionReference.
     */
    private void writeBeanDefinitionsToMetaInf() {
        try {
            classWriterOutputVisitor.finish();
        } catch (Exception e) {
            String message = e.getMessage();
            error("Error occurred writing META-INF files: %s", message != null ? message : e);
        }
    }

    private void processBeanDefinitions(BeanDefinitionVisitor beanDefinitionWriter) {
        try {
            beanDefinitionWriter.visitBeanDefinitionEnd();
            if (beanDefinitionWriter.isEnabled()) {
                beanDefinitionWriter.accept(classWriterOutputVisitor);
            }
        } catch (IOException e) {
            // raise a compile error
            String message = e.getMessage();
            error("Unexpected error: %s", message != null ? message : e.getClass().getSimpleName());
        }
    }

}
