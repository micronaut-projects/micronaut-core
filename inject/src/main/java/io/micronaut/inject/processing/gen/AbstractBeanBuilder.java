package io.micronaut.inject.processing.gen;

import io.micronaut.context.RequiresCondition;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.DefaultScope;
import io.micronaut.context.annotation.Executable;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Property;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Value;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationUtil;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.reflect.ClassUtils;
import io.micronaut.inject.annotation.AnnotationMetadataHierarchy;
import io.micronaut.inject.annotation.AnnotationMetadataReference;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.Element;
import io.micronaut.inject.ast.ElementFactory;
import io.micronaut.inject.ast.ElementQuery;
import io.micronaut.inject.ast.FieldElement;
import io.micronaut.inject.ast.MemberElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.configuration.ConfigurationMetadataBuilder;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.inject.writer.BeanDefinitionReferenceWriter;
import io.micronaut.inject.writer.BeanDefinitionVisitor;

import java.lang.annotation.Annotation;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

public abstract class AbstractBeanBuilder {

    public static final String ANN_VALIDATED = "io.micronaut.validation.Validated";
    protected static final String ANN_REQUIRES_VALIDATION = "io.micronaut.validation.RequiresValidation";

    protected final ClassElement classElement;
    protected final VisitorContext visitorContext;
    protected final List<BeanDefinitionVisitor> beanDefinitionWriters = new LinkedList<>();

    protected ConfigurationMetadataBuilder metadataBuilder = ConfigurationMetadataBuilder.INSTANCE;

    protected final AopHelper aopHelper;

    protected AbstractBeanBuilder(ClassElement classElement, VisitorContext visitorContext) {
        this.classElement = classElement;
        this.visitorContext = visitorContext;
        checkPackage(classElement);
//        Iterator<AopHelper> iterator = ServiceLoader.load(AopHelper.class).iterator();
//        if (ClassUtils.isPresent("io.micronaut.aop.writer.AopHelperImpl", getClass().getClassLoader())) {
//            System.out.println("YESSS!!!!");
//        }
//        if (iterator.hasNext()) {
//            System.out.println("FOUND!!!!");
//            aopHelper = iterator.next();
//        } else {
//            System.out.println("NULLL!!!!");
//            aopHelper = null;
//        }
        try {
            aopHelper = (AopHelper) ClassUtils.forName("io.micronaut.aop.writer.AopHelperImpl", getClass().getClassLoader()).get().newInstance();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void checkPackage(ClassElement classElement) {
        io.micronaut.inject.ast.PackageElement packageElement = classElement.getPackage();
        if (packageElement.isUnnamed()) {
            throw new ProcessingException(classElement, "Micronaut beans cannot be in the default package");
        }
    }

    public static AbstractBeanBuilder of(ClassElement classElement, VisitorContext visitorContext) {
        boolean isAbstract = classElement.isAbstract();
        boolean isIntroduction = classElement.hasStereotype(AnnotationUtil.ANN_INTRODUCTION);
        if (ConfigurationPropertiesBeanBuilder.isConfigurationProperties(classElement)) {
            if (classElement.isInterface()) {
                return new IntroductionInterfaceBeanBuilder(classElement, visitorContext, null);
            }
            return new ConfigurationPropertiesBeanBuilder(classElement, visitorContext);
        }
        boolean aopProxyType = isAopProxyType(classElement);
        if (!isAbstract && classElement.hasStereotype(Factory.class)) {
            return new FactoryBeanBuilder(classElement, visitorContext, aopProxyType);
        }
        if (aopProxyType) {
            if (isIntroduction) {
                return new AopIntroductionProxySupportedBeanBuilder(classElement, visitorContext, true);
            }
            return new SimpleBeanBuilder(classElement, visitorContext, true);
        }
        if (isIntroduction) {
            if (classElement.isInterface()) {
                return new IntroductionInterfaceBeanBuilder(classElement, visitorContext, null);
            }
            return new AopIntroductionProxySupportedBeanBuilder(classElement, visitorContext, false);
        }
        // NOTE: In Micronaut 3 abstract classes are allowed to be beans, but are not pickup to be beans just by having methods or fields with @Inject
        if (isDeclaredBean(classElement) || (!isAbstract && (containsInjectMethod(classElement) || containsInjectField(classElement)))) {
            if (classElement.hasStereotype("groovy.lang.Singleton")) {
                throw new ProcessingException(classElement, "Class annotated with groovy.lang.Singleton instead of jakarta.inject.Singleton. Import jakarta.inject.Singleton to use Micronaut Dependency Injection.");
            }
            return new SimpleBeanBuilder(classElement, visitorContext, false);
        }
        return null;
    }

    public abstract void build();

    private static boolean isDeclaredBean(ClassElement classElement) {
        if (isDeclaredBeanInMetadata(classElement.getAnnotationMetadata())) {
            return true;
        }
        if (classElement.isAbstract()) {
            return false;
        }
        return classElement.hasStereotype(Executable.class) ||
            classElement.hasStereotype(AnnotationUtil.QUALIFIER) ||
            classElement.getPrimaryConstructor().map(constructor -> constructor.hasStereotype(AnnotationUtil.INJECT)).orElse(false);
    }

    private static boolean containsInjectMethod(ClassElement classElement) {
        return classElement.getEnclosedElement(
            ElementQuery.ALL_METHODS.onlyConcrete()
                .onlyDeclared()
                .annotated(annotationMetadata -> annotationMetadata.hasDeclaredAnnotation(AnnotationUtil.INJECT))
        ).isPresent();
    }

    private static boolean containsInjectField(ClassElement classElement) {
        return classElement.getEnclosedElement(
            ElementQuery.ALL_FIELDS
                .onlyDeclared()
                .annotated(AbstractBeanBuilder::containsInjectPoint)
        ).isPresent();
    }

    private static boolean containsInjectPoint(AnnotationMetadata annotationMetadata) {
        return annotationMetadata.hasStereotype(AnnotationUtil.INJECT)
            || annotationMetadata.hasStereotype(Value.class)
            || annotationMetadata.hasStereotype(Property.class);
    }

    private static boolean isAopProxyType(ClassElement classElement) {
        return !classElement.isAbstract()
            && !classElement.isAssignable("io.micronaut.aop.Interceptor")
            && hasAroundStereotype(classElement.getAnnotationMetadata());
    }

    private static boolean isDeclaredBeanInMetadata(AnnotationMetadata concreteClassMetadata) {
        return concreteClassMetadata.hasDeclaredStereotype(Bean.class) ||
            concreteClassMetadata.hasStereotype(AnnotationUtil.SCOPE) ||
            concreteClassMetadata.hasStereotype(DefaultScope.class);
    }

    /**
     * Does the given metadata have AOP advice declared.
     *
     * @param annotationMetadata The annotation metadata
     * @return True if it does
     */
    protected static boolean hasAroundStereotype(@Nullable AnnotationMetadata annotationMetadata) {
        return hasAround(annotationMetadata,
            annMetadata -> annMetadata.hasStereotype(AnnotationUtil.ANN_AROUND),
            annMetdata -> annMetdata.getAnnotationValuesByName(AnnotationUtil.ANN_INTERCEPTOR_BINDING));
    }

    /**
     * Does the given metadata have declared AOP advice.
     *
     * @param annotationMetadata The annotation metadata
     * @return True if it does
     */
    protected static boolean hasDeclaredAroundAdvice(@Nullable AnnotationMetadata annotationMetadata) {
        return hasAround(annotationMetadata,
            annMetadata -> annMetadata.hasDeclaredStereotype(AnnotationUtil.ANN_AROUND),
            annMetdata -> annMetdata.getDeclaredAnnotationValuesByName(AnnotationUtil.ANN_INTERCEPTOR_BINDING));
    }

    private static boolean hasAround(@Nullable AnnotationMetadata annotationMetadata,
                                     @NonNull Predicate<AnnotationMetadata> hasFunction,
                                     @NonNull Function<AnnotationMetadata, List<AnnotationValue<Annotation>>> interceptorBindingsFunction) {
        if (annotationMetadata == null) {
            return false;
        }
        if (hasFunction.test(annotationMetadata)) {
            return true;
        } else if (annotationMetadata.hasDeclaredStereotype(AnnotationUtil.ANN_INTERCEPTOR_BINDINGS)) {
            return interceptorBindingsFunction.apply(annotationMetadata)
                .stream().anyMatch(av ->
                    av.stringValue("kind").orElse("AROUND").equals("AROUND")
                );
        }

        return false;
    }

    protected void visitAnnotationMetadata(BeanDefinitionVisitor writer, AnnotationMetadata annotationMetadata) {
        for (io.micronaut.core.annotation.AnnotationValue<Requires> annotation : annotationMetadata.getAnnotationValuesByType(Requires.class)) {
            annotation.stringValue(RequiresCondition.MEMBER_BEAN_PROPERTY)
                .ifPresent(beanProperty -> {
                    annotation.stringValue(RequiresCondition.MEMBER_BEAN)
                        .map(className -> visitorContext.getClassElement(className, visitorContext.getElementAnnotationMetadataFactory().readOnly()).get())
                        .ifPresent(classElement -> {
                            String requiredValue = annotation.stringValue().orElse(null);
                            String notEqualsValue = annotation.stringValue(RequiresCondition.MEMBER_NOT_EQUALS).orElse(null);
                            writer.visitAnnotationMemberPropertyInjectionPoint(classElement, beanProperty, requiredValue, notEqualsValue);
                        });
                });
        }
    }

    public List<BeanDefinitionVisitor> getBeanDefinitionWriters() {
        return beanDefinitionWriters;
    }

    protected AnnotationMetadata annotationMetadataCombineWithBeanMetadata(BeanDefinitionVisitor beanDefinitionVisitor, AnnotationMetadata annotationMetadata) {
        AnnotationMetadata beanClassAnnotationMetadata = new AnnotationMetadataReference(
            beanDefinitionVisitor.getBeanDefinitionName() + BeanDefinitionReferenceWriter.REF_SUFFIX,
            classElement.getAnnotationMetadata()
        );
        return new AnnotationMetadataHierarchy(beanClassAnnotationMetadata, annotationMetadata);
    }

    protected void adjustMethodToIncludeClassMetadata(BeanDefinitionVisitor beanDefinitionVisitor, MethodElement methodElement) {
        // TODO: eliminate the need to do the adjustment in the future
        AnnotationMetadata methodAnnotationMetadata = getElementAnnotationMetadata(methodElement);
        methodElement.replaceAnnotations(annotationMetadataCombineWithBeanMetadata(beanDefinitionVisitor, methodAnnotationMetadata));
    }

    protected void elementAnnotationsGuard(Element element, Consumer<MemberElement> consumer) {
        if (element instanceof MethodElement) {
            methodAnnotationsGuard((MethodElement) element, consumer::accept);
        } else if (element instanceof FieldElement) {
            fieldAnnotationsGuard((FieldElement) element, consumer::accept);
        } else {
            throw new IllegalStateException();
        }
    }

    protected void methodAnnotationsGuard(MethodElement methodElement, Consumer<MethodElement> consumer) {
        methodAnnotationsGuard(visitorContext, methodElement, consumer);
    }

    protected void fieldAnnotationsGuard(FieldElement fieldElement, Consumer<FieldElement> consumer) {
        fieldAnnotationsGuard(visitorContext, fieldElement, consumer);
    }

    protected void classAnnotationsGuard(ClassElement classElement, Consumer<ClassElement> consumer) {
        classAnnotationsGuard(visitorContext, classElement, consumer);
    }

    public static void adjustMethodToIncludeClassMetadata(MethodElement methodElement) {
        adjustMethodToIncludeClassMetadata(methodElement.getDeclaringType(), methodElement);
    }

    public static void adjustMethodToIncludeClassMetadata(ClassElement classElement, MethodElement methodElement) {
        AnnotationMetadata declaringClassAnnotationMetadata = classElement.getAnnotationMetadata();
        AnnotationMetadata methodAnnotationMetadata = getElementAnnotationMetadata(methodElement);
        methodElement.replaceAnnotations(new AnnotationMetadataHierarchy(declaringClassAnnotationMetadata, methodAnnotationMetadata));
    }

    public static AnnotationMetadata getElementAnnotationMetadata(MemberElement methodElement) {
        // NOTE: if annotation processor modified the method's annotation
        // annotationUtils.getAnnotationMetadata(method) will return AnnotationMetadataHierarchy of both method+class metadata
        AnnotationMetadata annotationMetadata = methodElement.getAnnotationMetadata();
        if (annotationMetadata instanceof AnnotationMetadataHierarchy) {
            return annotationMetadata.getDeclaredMetadata();
        }
        return annotationMetadata;
    }

    public static void methodAnnotationsGuard(VisitorContext visitorContext, MethodElement methodElement, Consumer<MethodElement> consumer) {
        if (methodElement.isSynthetic()) {
            consumer.accept(methodElement);
            return;
        }
        ElementFactory elementFactory = visitorContext.getElementFactory();
        // Because of the shared method's annotation cache we need to make a copy of the method.
        // The method is going to be stored in the visitor till the write process
        // We need to make sure we don't reuse the same instance for other adapters, and we don't override
        // added annotations.
        MethodElement targetMethod = elementFactory.newMethodElement(
            methodElement.getOwningType(),
            methodElement.getNativeType(),
            visitorContext.getElementAnnotationMetadataFactory().readOnly()
        );
        consumer.accept(targetMethod);
        // Previous modifications will modify the shared annotation cache of this method
        // We need to put original values into the cache
        methodElement.replaceAnnotations(methodElement.getAnnotationMetadata());
    }

    public static void fieldAnnotationsGuard(VisitorContext visitorContext, FieldElement fieldElement, Consumer<FieldElement> consumer) {
        ElementFactory elementFactory = visitorContext.getElementFactory();
        // Because of the shared method's annotation cache we need to make a copy of the method.
        // The method is going to be stored in the visitor till the write process
        // We need to make sure we don't reuse the same instance for other adapters, and we don't override
        // added annotations.
        FieldElement targetField = elementFactory.newFieldElement(
            fieldElement.getOwningType(),
            fieldElement.getNativeType(),
            visitorContext.getElementAnnotationMetadataFactory().readOnly()
        );
        consumer.accept(targetField);
        // Previous modifications will modify the shared annotation cache of this method
        // We need to put original values into the cache
        fieldElement.replaceAnnotations(fieldElement.getAnnotationMetadata());
    }

    public static void classAnnotationsGuard(VisitorContext visitorContext, ClassElement classElement, Consumer<ClassElement> consumer) {
        if (classElement.isPrimitive()) {
            consumer.accept(classElement);
            return;
        }
        ElementFactory elementFactory = visitorContext.getElementFactory();
        // Because of the shared method's annotation cache we need to make a copy of the class.
        // The class is going to be stored in the visitor till the write process
        // We need to make sure we don't reuse the same instance for other adapters, and we don't override
        // added annotations.
        ClassElement newClassElement = elementFactory.newClassElement(
            classElement.getNativeType(),
            visitorContext.getElementAnnotationMetadataFactory().readOnly(),
            classElement.getTypeArguments()
        );
        consumer.accept(newClassElement);
        // Previous modifications will modify the shared annotation cache of this class
        // We need to put original values into the cache
        classElement.replaceAnnotations(classElement.getAnnotationMetadata());
    }

}
