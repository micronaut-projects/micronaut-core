/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.inject.writer;

import io.micronaut.context.AbstractInitializableBeanDefinition;
import io.micronaut.context.AbstractConstructorInjectionPoint;
import io.micronaut.context.AbstractExecutableMethod;
import io.micronaut.context.BeanContext;
import io.micronaut.context.BeanRegistration;
import io.micronaut.context.BeanResolutionContext;
import io.micronaut.context.DefaultBeanContext;
import io.micronaut.context.Qualifier;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.ConfigurationBuilder;
import io.micronaut.context.annotation.ConfigurationInject;
import io.micronaut.context.annotation.ConfigurationReader;
import io.micronaut.context.annotation.DefaultScope;
import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.EachProperty;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.context.annotation.Primary;
import io.micronaut.context.annotation.Property;
import io.micronaut.context.annotation.Provided;
import io.micronaut.context.annotation.Value;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationMetadataProvider;
import io.micronaut.core.annotation.AnnotationUtil;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.AnnotationValueBuilder;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.beans.BeanConstructor;
import io.micronaut.core.bind.annotation.Bindable;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.core.reflect.InstantiationUtils;
import io.micronaut.core.reflect.ReflectionUtils;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.DefaultArgument;
import io.micronaut.core.type.TypeVariableResolver;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.core.util.Toggleable;
import io.micronaut.inject.AdvisedBeanType;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.BeanFactory;
import io.micronaut.inject.ConstructorInjectionPoint;
import io.micronaut.inject.DisposableBeanDefinition;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.inject.ExecutableMethodsDefinition;
import io.micronaut.inject.InitializingBeanDefinition;
import io.micronaut.inject.ParametrizedBeanFactory;
import io.micronaut.inject.ProxyBeanDefinition;
import io.micronaut.inject.ValidatedBeanDefinition;
import io.micronaut.inject.annotation.AnnotationMetadataHierarchy;
import io.micronaut.inject.annotation.AnnotationMetadataWriter;
import io.micronaut.inject.annotation.DefaultAnnotationMetadata;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.ConstructorElement;
import io.micronaut.inject.ast.Element;
import io.micronaut.inject.ast.ElementQuery;
import io.micronaut.inject.ast.FieldElement;
import io.micronaut.inject.ast.MemberElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.ParameterElement;
import io.micronaut.inject.ast.PrimitiveElement;
import io.micronaut.inject.ast.PropertyElement;
import io.micronaut.inject.ast.TypedElement;
import io.micronaut.inject.ast.beans.BeanElement;
import io.micronaut.inject.ast.beans.BeanElementBuilder;
import io.micronaut.inject.configuration.ConfigurationMetadataBuilder;
import io.micronaut.inject.configuration.PropertyMetadata;
import io.micronaut.inject.processing.JavaModelUtils;
import io.micronaut.inject.qualifiers.Qualifiers;
import io.micronaut.inject.visitor.BeanElementVisitor;
import io.micronaut.inject.visitor.BeanElementVisitorContext;
import io.micronaut.inject.visitor.VisitorContext;
import jakarta.inject.Singleton;
import org.jetbrains.annotations.NotNull;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.signature.SignatureVisitor;
import org.objectweb.asm.signature.SignatureWriter;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static io.micronaut.inject.visitor.BeanElementVisitor.VISITORS;

/**
 * <p>Responsible for building {@link BeanDefinition} instances at compile time. Uses ASM build the class definition.</p>
 * <p>
 * <p>Should be used from AST frameworks to build bean definitions from source code data.</p>
 * <p>
 * <p>For example:</p>
 *
 * <pre>
 *     {@code
 *
 *          BeanDefinitionWriter writer = new BeanDefinitionWriter("my.package", "MyClass", "javax.inject.Singleton", true)
 *          writer.visitBeanDefinitionConstructor()
 *          writer.visitFieldInjectionPoint("my.Qualifier", false, "my.package.MyDependency", "myfield" )
 *          writer.visitBeanDefinitionEnd()
 *          writer.writeTo(new File(..))
 *     }
 * </pre>
 *
 * @author Graeme Rocher
 * @author Denis Stepanov
 * @see BeanDefinition
 * @since 1.0
 */
@Internal
public class BeanDefinitionWriter extends AbstractClassFileWriter implements BeanDefinitionVisitor, BeanElement, Toggleable {
    public static final String CLASS_SUFFIX = "$Definition";
    private static final String ANN_CONSTRAINT = "javax.validation.Constraint";

    private static final Constructor<AbstractConstructorInjectionPoint> CONSTRUCTOR_ABSTRACT_CONSTRUCTOR_IP = ReflectionUtils.findConstructor(
            AbstractConstructorInjectionPoint.class,
            BeanDefinition.class)
            .orElseThrow(() -> new ClassGenerationException("Invalid version of Micronaut present on the class path"));

    private static final Method POST_CONSTRUCT_METHOD = ReflectionUtils.getRequiredInternalMethod(AbstractInitializableBeanDefinition.class, "postConstruct", BeanResolutionContext.class, BeanContext.class, Object.class);

    private static final Method INJECT_BEAN_METHOD = ReflectionUtils.getRequiredInternalMethod(AbstractInitializableBeanDefinition.class, "injectBean", BeanResolutionContext.class, BeanContext.class, Object.class);

    private static final Method PRE_DESTROY_METHOD = ReflectionUtils.getRequiredInternalMethod(AbstractInitializableBeanDefinition.class, "preDestroy", BeanResolutionContext.class, BeanContext.class, Object.class);

    private static final Method GET_BEAN_FOR_CONSTRUCTOR_ARGUMENT = getBeanLookupMethod("getBeanForConstructorArgument", false);

    private static final Method GET_BEAN_REGISTRATIONS_FOR_CONSTRUCTOR_ARGUMENT = getBeanLookupMethod("getBeanRegistrationsForConstructorArgument", true);

    private static final Method GET_BEAN_REGISTRATION_FOR_CONSTRUCTOR_ARGUMENT = getBeanLookupMethod("getBeanRegistrationForConstructorArgument", true);

    private static final Method GET_BEANS_OF_TYPE_FOR_CONSTRUCTOR_ARGUMENT = getBeanLookupMethod("getBeansOfTypeForConstructorArgument", true);

    private static final Method GET_VALUE_FOR_CONSTRUCTOR_ARGUMENT = getBeanLookupMethod("getValueForConstructorArgument", false);

    private static final Method GET_STREAM_OF_TYPE_FOR_CONSTRUCTOR_ARGUMENT = getBeanLookupMethod("getStreamOfTypeForConstructorArgument", true);

    private static final Method FIND_BEAN_FOR_CONSTRUCTOR_ARGUMENT = getBeanLookupMethod("findBeanForConstructorArgument", true);

    private static final Method GET_BEAN_FOR_FIELD = getBeanLookupMethod("getBeanForField", false);

    private static final Method GET_BEAN_REGISTRATIONS_FOR_FIELD = getBeanLookupMethod("getBeanRegistrationsForField", true);

    private static final Method GET_BEAN_REGISTRATION_FOR_FIELD = getBeanLookupMethod("getBeanRegistrationForField", true);

    private static final Method GET_BEANS_OF_TYPE_FOR_FIELD = getBeanLookupMethod("getBeansOfTypeForField", true);

    private static final Method GET_VALUE_FOR_FIELD = getBeanLookupMethod("getValueForField", false);

    private static final Method GET_STREAM_OF_TYPE_FOR_FIELD = getBeanLookupMethod("getStreamOfTypeForField", true);

    private static final Method FIND_BEAN_FOR_FIELD = getBeanLookupMethod("findBeanForField", true);

    private static final Method GET_VALUE_FOR_PATH = ReflectionUtils.getRequiredInternalMethod(AbstractInitializableBeanDefinition.class, "getValueForPath", BeanResolutionContext.class, BeanContext.class, Argument.class, String.class);

    private static final Method CONTAINS_VALUE_FOR_FIELD = ReflectionUtils.getRequiredInternalMethod(
            AbstractInitializableBeanDefinition.class,
            "containsValueForField",
            BeanResolutionContext.class,
            BeanContext.class,
            int.class,
            boolean.class);

    private static final Method CONTAINS_PROPERTIES_METHOD = ReflectionUtils.getRequiredInternalMethod(AbstractInitializableBeanDefinition.class, "containsProperties", BeanResolutionContext.class, BeanContext.class);

    private static final Method GET_BEAN_FOR_METHOD_ARGUMENT = getBeanLookupMethodForArgument("getBeanForMethodArgument", false);

    private static final Method GET_BEAN_REGISTRATIONS_FOR_METHOD_ARGUMENT = getBeanLookupMethodForArgument("getBeanRegistrationsForMethodArgument", true);

    private static final Method GET_BEAN_REGISTRATION_FOR_METHOD_ARGUMENT = getBeanLookupMethodForArgument("getBeanRegistrationForMethodArgument", true);

    private static final Method GET_BEANS_OF_TYPE_FOR_METHOD_ARGUMENT = getBeanLookupMethodForArgument("getBeansOfTypeForMethodArgument", true);

    private static final Method GET_STREAM_OF_TYPE_FOR_METHOD_ARGUMENT = getBeanLookupMethodForArgument("getStreamOfTypeForMethodArgument", true);

    private static final Method FIND_BEAN_FOR_METHOD_ARGUMENT = getBeanLookupMethodForArgument("findBeanForMethodArgument", true);

    private static final Method GET_VALUE_FOR_METHOD_ARGUMENT = getBeanLookupMethodForArgument("getValueForMethodArgument", false);

    private static final Method CONTAINS_VALUE_FOR_METHOD_ARGUMENT = ReflectionUtils.getRequiredInternalMethod(
            AbstractInitializableBeanDefinition.class,
            "containsValueForMethodArgument",
            BeanResolutionContext.class,
            BeanContext.class,
            int.class,
            int.class,
            boolean.class);

    private static final Type TYPE_ABSTRACT_BEAN_DEFINITION = Type.getType(AbstractInitializableBeanDefinition.class);

    private static final org.objectweb.asm.commons.Method METHOD_OPTIONAL_EMPTY = org.objectweb.asm.commons.Method.getMethod(
            ReflectionUtils.getRequiredMethod(Optional.class, "empty")
    );
    private static final Type TYPE_OPTIONAL = Type.getType(Optional.class);
    private static final org.objectweb.asm.commons.Method METHOD_OPTIONAL_OF = org.objectweb.asm.commons.Method.getMethod(
            ReflectionUtils.getRequiredMethod(Optional.class, "of", Object.class)
    );
    private static final org.objectweb.asm.commons.Method METHOD_INVOKE_CONSTRUCTOR = org.objectweb.asm.commons.Method.getMethod(ReflectionUtils.getRequiredMethod(
            ConstructorInjectionPoint.class,
            "invoke",
            Object[].class
    ));
    private static final String METHOD_DESCRIPTOR_CONSTRUCTOR_INSTANTIATE = getMethodDescriptor(Object.class, Arrays.asList(
            BeanResolutionContext.class,
            BeanContext.class,
            List.class,
            BeanDefinition.class,
            BeanConstructor.class,
            Object[].class
    ));
    private static final String METHOD_DESCRIPTOR_INTERCEPTED_LIFECYCLE = getMethodDescriptor(Object.class, Arrays.asList(
            BeanResolutionContext.class,
            BeanContext.class,
            BeanDefinition.class,
            ExecutableMethod.class,
            Object.class
    ));
    private static final Method METHOD_GET_BEAN = ReflectionUtils.getRequiredInternalMethod(DefaultBeanContext.class, "getBean", BeanResolutionContext.class, Class.class);
    private static final org.objectweb.asm.commons.Method COLLECTION_TO_ARRAY = org.objectweb.asm.commons.Method.getMethod(
            ReflectionUtils.getRequiredInternalMethod(Collection.class, "toArray", Object[].class)
    );
    private static final Type TYPE_RESOLUTION_CONTEXT = Type.getType(BeanResolutionContext.class);
    private static final Type TYPE_BEAN_CONTEXT = Type.getType(BeanContext.class);
    private static final Type TYPE_BEAN_DEFINITION = Type.getType(BeanDefinition.class);
    private static final String METHOD_DESCRIPTOR_INITIALIZE = Type.getMethodDescriptor(Type.getType(Object.class), Type.getType(BeanResolutionContext.class), Type.getType(BeanContext.class), Type.getType(Object.class));

    private static final org.objectweb.asm.commons.Method PROTECTED_ABSTRACT_BEAN_DEFINITION_CONSTRUCTOR = new org.objectweb.asm.commons.Method(CONSTRUCTOR_NAME, getConstructorDescriptor(
            Class.class, // beanType
            AbstractInitializableBeanDefinition.MethodOrFieldReference.class // constructor
    ));

    private static final org.objectweb.asm.commons.Method SET_FIELD_WITH_REFLECTION_METHOD = org.objectweb.asm.commons.Method.getMethod(
            ReflectionUtils.getRequiredMethod(AbstractInitializableBeanDefinition.class, "setFieldWithReflection", BeanResolutionContext.class, BeanContext.class, int.class, Object.class, Object.class)
    );

    private static final org.objectweb.asm.commons.Method INVOKE_WITH_REFLECTION_METHOD = org.objectweb.asm.commons.Method.getMethod(
            ReflectionUtils.getRequiredMethod(AbstractInitializableBeanDefinition.class, "invokeMethodWithReflection", BeanResolutionContext.class, BeanContext.class, int.class, Object.class, Object[].class)
    );

    private static final org.objectweb.asm.commons.Method BEAN_DEFINITION_CLASS_CONSTRUCTOR = new org.objectweb.asm.commons.Method(CONSTRUCTOR_NAME, getConstructorDescriptor(
            Class.class, // beanType
            AbstractInitializableBeanDefinition.MethodOrFieldReference.class, // constructor
            AnnotationMetadata.class, // annotationMetadata
            AbstractInitializableBeanDefinition.MethodReference[].class, // methodInjection
            AbstractInitializableBeanDefinition.FieldReference[].class, // fieldInjection
            ExecutableMethodsDefinition.class, // executableMethodsDefinition
            Map.class, // typeArgumentsMap
            Optional.class, // scope
            boolean.class, // isAbstract
            boolean.class, // isProvided
            boolean.class, // isIterable
            boolean.class, // isSingleton
            boolean.class, // isPrimary
            boolean.class, // isConfigurationProperties
            boolean.class, // isContainerType
            boolean.class  // requiresMethodProcessing
    ));

    private static final String FIELD_CONSTRUCTOR = "$CONSTRUCTOR";
    private static final String FIELD_INJECTION_METHODS = "$INJECTION_METHODS";
    private static final String FIELD_INJECTION_FIELDS = "$INJECTION_FIELDS";
    private static final String FIELD_TYPE_ARGUMENTS = "$TYPE_ARGUMENTS";
    private static final String FIELD_INNER_CLASSES = "$INNER_CONFIGURATION_CLASSES";
    private static final String FIELD_EXPOSED_TYPES = "$EXPOSED_TYPES";

    private static final org.objectweb.asm.commons.Method METHOD_REFERENCE_CONSTRUCTOR = new org.objectweb.asm.commons.Method(CONSTRUCTOR_NAME, getConstructorDescriptor(
            Class.class, // declaringType,
            String.class, // methodName
            Argument[].class, // arguments
            AnnotationMetadata.class, // annotationMetadata
            boolean.class // boolean requiresReflection
    ));

    private static final org.objectweb.asm.commons.Method METHOD_REFERENCE_CONSTRUCTOR_POST_PRE = new org.objectweb.asm.commons.Method(CONSTRUCTOR_NAME, getConstructorDescriptor(
            Class.class, // declaringType,
            String.class, // methodName
            Argument[].class, // arguments
            AnnotationMetadata.class, // annotationMetadata
            boolean.class, // boolean requiresReflection
            boolean.class, // isPostConstructMethod
            boolean.class // isPreDestroyMethod,
    ));

    private static final org.objectweb.asm.commons.Method FIELD_REFERENCE_CONSTRUCTOR = new org.objectweb.asm.commons.Method(CONSTRUCTOR_NAME, getConstructorDescriptor(
            Class.class, // declaringType;
            Argument.class, // argument;
            boolean.class // requiresReflection;
    ));

    private final ClassWriter classWriter;
    private final String beanFullClassName;
    private final String beanDefinitionName;
    private final String beanDefinitionInternalName;
    private final Type beanType;
    private final Type providedType;
    private final Set<Class> interfaceTypes;
    private final Map<String, Integer> defaultsStorage = new HashMap<>();
    private final Map<String, GeneratorAdapter> loadTypeMethods = new LinkedHashMap<>();
    private final Map<String, ClassWriter> innerClasses = new LinkedHashMap<>(2);
    private final String providedBeanClassName;
    private final String packageName;
    private final String beanSimpleClassName;
    private final Type beanDefinitionType;
    private final boolean isInterface;
    private final boolean isAbstract;
    private final boolean isConfigurationProperties;
    private final ConfigurationMetadataBuilder<?> metadataBuilder;
    private final Element beanProducingElement;
    private final ClassElement beanTypeElement;
    private final VisitorContext visitorContext;
    private GeneratorAdapter buildMethodVisitor;
    private GeneratorAdapter injectMethodVisitor;
    private Label injectEnd = null;
    private GeneratorAdapter preDestroyMethodVisitor;
    private GeneratorAdapter postConstructMethodVisitor;
    private boolean postConstructAdded;
    private GeneratorAdapter interceptedDisposeMethod;
    private int currentFieldIndex = 0;
    private int currentMethodIndex = 0;

    private int buildInstanceLocalVarIndex = -1;
    private int injectInstanceLocalVarIndex = -1;
    private int postConstructInstanceLocalVarIndex = -1;
    private int preDestroyInstanceLocalVarIndex = -1;
    private boolean beanFinalized = false;
    private Type superType = TYPE_ABSTRACT_BEAN_DEFINITION;
    private boolean isParametrized = false;
    private boolean superBeanDefinition = false;
    private boolean isSuperFactory = false;
    private final AnnotationMetadata annotationMetadata;
    private ConfigBuilderState currentConfigBuilderState;
    private boolean preprocessMethods = false;
    private Map<String, Map<String, ClassElement>> typeArguments;
    private String interceptedType;

    private int innerClassIndex;

    private final List<FieldVisitData> fieldInjectionPoints = new ArrayList<>(2);
    private final List<MethodVisitData> methodInjectionPoints = new ArrayList<>(2);
    private final List<MethodVisitData> postConstructMethodVisits = new ArrayList<>(2);
    private final List<MethodVisitData> preDestroyMethodVisits = new ArrayList<>(2);
    private final Map<String, Boolean> isLifeCycleCache = new HashMap<>(2);
    private ExecutableMethodsDefinitionWriter executableMethodsDefinitionWriter;

    private Object constructor; // MethodElement or FieldElement
    private boolean constructorRequiresReflection;
    private boolean disabled = false;

    /**
     * Creates a bean definition writer.
     *
     * @param classElement    The class element
     * @param metadataBuilder The configuration metadata builder
     * @param visitorContext  The visitor context
     */
    public BeanDefinitionWriter(ClassElement classElement,
                                ConfigurationMetadataBuilder<?> metadataBuilder,
                                VisitorContext visitorContext) {
        this(classElement, OriginatingElements.of(classElement), metadataBuilder, visitorContext, null);
    }

    /**
     * Creates a bean definition writer.
     *
     * @param classElement        The class element
     * @param originatingElements The originating elements
     * @param metadataBuilder     The configuration metadata builder
     * @param visitorContext      The visitor context
     */
    public BeanDefinitionWriter(ClassElement classElement,
                                OriginatingElements originatingElements,
                                ConfigurationMetadataBuilder<?> metadataBuilder,
                                VisitorContext visitorContext) {
        this(classElement, originatingElements, metadataBuilder, visitorContext, null);
    }

    /**
     * Creates a bean definition writer.
     *
     * @param beanProducingElement The bean producing element
     * @param originatingElements  The originating elements
     * @param metadataBuilder      The configuration metadata builder
     * @param visitorContext       The visitor context
     * @param uniqueIdentifier     An optional unique identifier to include in the bean name
     */
    public BeanDefinitionWriter(Element beanProducingElement,
                                OriginatingElements originatingElements,
                                ConfigurationMetadataBuilder<?> metadataBuilder,
                                VisitorContext visitorContext,
                                @Nullable Integer uniqueIdentifier) {
        super(originatingElements);
        this.metadataBuilder = metadataBuilder;
        this.classWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        this.beanProducingElement = beanProducingElement;
        if (beanProducingElement instanceof ClassElement) {
            ClassElement classElement = (ClassElement) beanProducingElement;
            autoApplyNamedToBeanProducingElement(classElement);
            this.beanTypeElement = classElement;
            this.packageName = classElement.getPackageName();
            this.isInterface = classElement.isInterface();
            this.isAbstract = classElement.isAbstract();
            this.beanFullClassName = classElement.getName();
            this.beanSimpleClassName = classElement.getSimpleName();
            this.providedBeanClassName = beanFullClassName;
            this.beanDefinitionName = getBeanDefinitionName(packageName, beanSimpleClassName);
        } else if (beanProducingElement instanceof MethodElement) {
            autoApplyNamedToBeanProducingElement(beanProducingElement);
            MethodElement factoryMethodElement = (MethodElement) beanProducingElement;
            final ClassElement producedElement = factoryMethodElement.getGenericReturnType();
            this.beanTypeElement = producedElement;
            this.packageName = producedElement.getPackageName();
            this.isInterface = producedElement.isInterface();
            this.isAbstract = false;
            this.beanFullClassName = producedElement.getName();
            this.beanSimpleClassName = producedElement.getSimpleName();
            this.providedBeanClassName = producedElement.getName();
            String upperCaseMethodName = NameUtils.capitalize(factoryMethodElement.getName());
            if (uniqueIdentifier == null) {
                throw new IllegalArgumentException("Factory methods require passing a unique identifier");
            }
            final ClassElement declaringType = factoryMethodElement.getOwningType();
            this.beanDefinitionName = declaringType.getPackageName() + "." + prefixClassName(declaringType.getSimpleName()) + "$" + upperCaseMethodName + uniqueIdentifier + CLASS_SUFFIX;
        } else if (beanProducingElement instanceof FieldElement) {
            autoApplyNamedToBeanProducingElement(beanProducingElement);
            FieldElement factoryMethodElement = (FieldElement) beanProducingElement;
            final ClassElement producedElement = factoryMethodElement.getGenericField();
            this.beanTypeElement = producedElement;
            this.packageName = producedElement.getPackageName();
            this.isInterface = producedElement.isInterface();
            this.isAbstract = false;
            this.beanFullClassName = producedElement.getName();
            this.beanSimpleClassName = producedElement.getSimpleName();
            this.providedBeanClassName = producedElement.getName();
            String fieldName = NameUtils.capitalize(factoryMethodElement.getName());
            if (uniqueIdentifier == null) {
                throw new IllegalArgumentException("Factory fields require passing a unique identifier");
            }
            final ClassElement declaringType = factoryMethodElement.getOwningType();
            this.beanDefinitionName = declaringType.getPackageName() + "." + prefixClassName(declaringType.getSimpleName()) + "$" + fieldName + uniqueIdentifier + CLASS_SUFFIX;
        } else if (beanProducingElement instanceof BeanElementBuilder) {
            BeanElementBuilder beanElementBuilder = (BeanElementBuilder) beanProducingElement;
            this.beanTypeElement = beanElementBuilder.getBeanType();
            this.packageName = this.beanTypeElement.getPackageName();
            this.isInterface = this.beanTypeElement.isInterface();
            this.isAbstract = this.beanTypeElement.isAbstract();
            this.beanFullClassName = this.beanTypeElement.getName();
            this.beanSimpleClassName = this.beanTypeElement.getSimpleName();
            this.providedBeanClassName = this.beanFullClassName;
            if (uniqueIdentifier == null) {
                throw new IllegalArgumentException("Beans produced by addAssociatedBean(..) require passing a unique identifier");
            }
            final Element originatingElement = beanElementBuilder.getOriginatingElement();
            if (originatingElement instanceof ClassElement) {
                ClassElement originatingClass = (ClassElement) originatingElement;
                this.beanDefinitionName = getAssociatedBeanName(uniqueIdentifier, originatingClass);
            } else if (originatingElement instanceof MethodElement) {
                ClassElement originatingClass = ((MethodElement) originatingElement).getDeclaringType();
                this.beanDefinitionName = getAssociatedBeanName(uniqueIdentifier, originatingClass);
            } else {
                throw new IllegalArgumentException("Unsupported originating element");
            }
        } else {
            throw new IllegalArgumentException("Unsupported element type: " + beanProducingElement.getClass().getName());
        }
        this.annotationMetadata = beanProducingElement.getAnnotationMetadata();
        this.beanDefinitionType = getTypeReferenceForName(this.beanDefinitionName);
        this.beanType = getTypeReferenceForName(beanFullClassName);
        this.providedType = getTypeReferenceForName(providedBeanClassName);
        this.beanDefinitionInternalName = getInternalName(this.beanDefinitionName);
        this.interfaceTypes = new TreeSet<>(Comparator.comparing(Class::getName));
        this.interfaceTypes.add(BeanFactory.class);
        this.isConfigurationProperties = isConfigurationProperties(annotationMetadata);
        validateExposedTypes(annotationMetadata, visitorContext);
        this.visitorContext = visitorContext;
    }

    @Override
    public boolean isEnabled() {
        return !disabled;
    }

    /**
     * Returns {@link ExecutableMethodsDefinitionWriter} of one exists.
     *
     * @return An instance of {@link ExecutableMethodsDefinitionWriter}
     */
    @Nullable
    public ExecutableMethodsDefinitionWriter getExecutableMethodsWriter() {
        return executableMethodsDefinitionWriter;
    }

    @NonNull
    private String getAssociatedBeanName(@NonNull Integer uniqueIdentifier, ClassElement originatingClass) {
        return originatingClass.getPackageName() + "." + prefixClassName(originatingClass.getSimpleName()) + prefixClassName(beanSimpleClassName) + uniqueIdentifier + CLASS_SUFFIX;
    }

    private void autoApplyNamedToBeanProducingElement(Element beanProducingElement) {
        final AnnotationMetadata annotationMetadata = beanProducingElement.getAnnotationMetadata();
        if (!annotationMetadata.hasAnnotation(EachProperty.class) && !annotationMetadata.hasAnnotation(EachBean.class)) {
            autoApplyNamedIfPresent(beanProducingElement, annotationMetadata);
        }
    }

    private void validateExposedTypes(AnnotationMetadata annotationMetadata, VisitorContext visitorContext) {
        final String[] types = annotationMetadata.stringValues(Bean.class, "typed");
        if (ArrayUtils.isNotEmpty(types)) {
            for (String name : types) {
                final ClassElement exposedType = visitorContext.getClassElement(name).orElse(null);
                if (exposedType == null) {
                    visitorContext.fail("Bean defines an exposed type [" + name + "] that is not on the classpath", beanProducingElement);
                } else if (!beanTypeElement.isAssignable(exposedType)) {
                    visitorContext.fail("Bean defines an exposed type [" + name + "] that is not implemented by the bean type", beanProducingElement);
                }
            }
        }
    }

    @NonNull
    private static String getBeanDefinitionName(String packageName, String className) {
        return packageName + "." + prefixClassName(className) + CLASS_SUFFIX;
    }

    private static String prefixClassName(String className) {
        if (className.startsWith("$")) {
            return className;
        }
        return "$" + className;
    }

    @NonNull
    @Override
    public ClassElement[] getTypeArguments() {
        final Map<String, ClassElement> args = this.typeArguments.get(this.getBeanTypeName());
        if (CollectionUtils.isNotEmpty(args)) {
            return args.values().toArray(new ClassElement[0]);
        }
        return BeanDefinitionVisitor.super.getTypeArguments();
    }

    /**
     * @return The name of the bean definition reference class.
     */
    @Override
    @NonNull
    public String getBeanDefinitionReferenceClassName() {
        return beanDefinitionName + BeanDefinitionReferenceWriter.REF_SUFFIX;
    }

    /**
     * @return The data for any post construct methods that were visited
     */
    public final List<MethodVisitData> getPostConstructMethodVisits() {
        return Collections.unmodifiableList(postConstructMethodVisits);
    }

    /**
     * @return The underlying class writer
     */
    public ClassVisitor getClassWriter() {
        return classWriter;
    }

    @Override
    public boolean isInterface() {
        return isInterface;
    }

    @Override
    public boolean isSingleton() {
        return annotationMetadata.hasDeclaredStereotype(AnnotationUtil.SINGLETON);
    }

    @Override
    public void visitBeanDefinitionInterface(Class<? extends BeanDefinition> interfaceType) {
        this.interfaceTypes.add(interfaceType);
    }

    @Override
    public void visitSuperBeanDefinition(String name) {
        this.superBeanDefinition = true;
        this.superType = getTypeReferenceForName(name);
    }

    @Override
    public void visitSuperBeanDefinitionFactory(String beanName) {
        visitSuperBeanDefinition(beanName);
        this.superBeanDefinition = false;
        this.isSuperFactory = true;
    }

    @Override
    public String getBeanTypeName() {
        return beanFullClassName;
    }

    @Override
    public Type getProvidedType() {
        return providedType;
    }

    @Override
    public void setValidated(boolean validated) {
        if (validated) {
            this.interfaceTypes.add(ValidatedBeanDefinition.class);
        } else {
            this.interfaceTypes.remove(ValidatedBeanDefinition.class);
        }
    }

    @Override
    public void setInterceptedType(String typeName) {
        if (typeName != null) {
            this.interfaceTypes.add(AdvisedBeanType.class);
        }
        this.interceptedType = typeName;
    }

    @Override
    public Optional<Type> getInterceptedType() {
        return Optional.ofNullable(interceptedType)
                .map(BeanDefinitionWriter::getTypeReferenceForName);
    }

    @Override
    public boolean isValidated() {
        return this.interfaceTypes.contains(ValidatedBeanDefinition.class);
    }

    @Override
    public String getBeanDefinitionName() {
        return beanDefinitionName;
    }

    /**
     * <p>In the case where the produced class is produced by a factory method annotated with
     * {@link Bean} this method should be called.</p>
     *
     * @param factoryClass  The factory class
     * @param factoryMethod The factory method
     */
    public void visitBeanFactoryMethod(ClassElement factoryClass,
                                       MethodElement factoryMethod) {
        if (constructor != null) {
            throw new IllegalStateException("Only a single call to visitBeanFactoryMethod(..) is permitted");
        } else {
            constructor = factoryMethod;

            // now prepare the implementation of the build method. See BeanFactory interface
            visitBuildFactoryMethodDefinition(factoryClass, factoryMethod);
            // now override the injectBean method
            visitInjectMethodDefinition();
        }
    }

    /**
     * <p>In the case where the produced class is produced by a factory field annotated with
     * {@link Bean} this method should be called.</p>
     *
     * @param factoryClass The factory class
     * @param factoryField The factory field
     */
    public void visitBeanFactoryField(ClassElement factoryClass, FieldElement factoryField) {
        if (constructor != null) {
            throw new IllegalStateException("Only a single call to visitBeanFactoryMethod(..) is permitted");
        } else {
            constructor = factoryField;

            autoApplyNamed(factoryField);
            // now prepare the implementation of the build method. See BeanFactory interface
            visitBuildFactoryMethodDefinition(factoryClass, factoryField);
            // now override the injectBean method
            visitInjectMethodDefinition();
        }
    }

    /**
     * Visits the constructor used to create the bean definition.
     *
     * @param constructor        The constructor
     * @param requiresReflection Whether invoking the constructor requires reflection
     * @param visitorContext     The visitor context
     */
    @Override
    public void visitBeanDefinitionConstructor(MethodElement constructor,
                                               boolean requiresReflection,
                                               VisitorContext visitorContext) {
        if (this.constructor == null) {
            applyConfigurationInjectionIfNecessary(constructor);

            this.constructor = constructor;
            this.constructorRequiresReflection = requiresReflection;

            // now prepare the implementation of the build method. See BeanFactory interface
            visitBuildMethodDefinition(constructor, requiresReflection);

            // now override the injectBean method
            visitInjectMethodDefinition();
        }
    }

    private void applyConfigurationInjectionIfNecessary(MethodElement constructor) {
        final boolean isRecordConfig = isRecordConfig(constructor);
        if (isRecordConfig || constructor.hasAnnotation(ConfigurationInject.class)) {
            final List<String> injectionTypes =
                    Arrays.asList(Property.class.getName(), Value.class.getName(), Parameter.class.getName(), AnnotationUtil.QUALIFIER, AnnotationUtil.INJECT);

            if (isRecordConfig) {
                final List<PropertyElement> beanProperties = constructor
                        .getDeclaringType()
                        .getBeanProperties();
                final ParameterElement[] parameters = constructor.getParameters();
                if (beanProperties.size() == parameters.length) {
                    for (int i = 0; i < parameters.length; i++) {
                        ParameterElement parameter = parameters[i];
                        final PropertyElement bp = beanProperties.get(i);
                        final AnnotationMetadata beanPropertyMetadata = bp.getAnnotationMetadata();
                        AnnotationMetadata annotationMetadata = parameter.getAnnotationMetadata();
                        if (injectionTypes.stream().noneMatch(beanPropertyMetadata::hasStereotype)) {
                            processConfigurationConstructorParameter(parameter, annotationMetadata);
                        }
                        if (annotationMetadata.hasStereotype(ANN_CONSTRAINT)) {
                            setValidated(true);
                        }
                    }
                } else {
                    processConfigurationInjectionConstructor(constructor, injectionTypes);
                }
            } else {
                processConfigurationInjectionConstructor(constructor, injectionTypes);
            }
        }

    }

    private void processConfigurationInjectionConstructor(MethodElement constructor, List<String> injectionTypes) {
        ParameterElement[] parameters = constructor.getParameters();
        for (ParameterElement parameter : parameters) {
            AnnotationMetadata annotationMetadata = parameter.getAnnotationMetadata();
            if (injectionTypes.stream().noneMatch(annotationMetadata::hasStereotype)) {
                processConfigurationConstructorParameter(parameter, annotationMetadata);
            }
            if (annotationMetadata.hasStereotype(ANN_CONSTRAINT)) {
                setValidated(true);
            }
        }
    }

    private void processConfigurationConstructorParameter(ParameterElement parameter, AnnotationMetadata annotationMetadata) {
        ClassElement parameterType = parameter.getGenericType();
        if (!parameterType.hasStereotype(AnnotationUtil.SCOPE)) {
            final PropertyMetadata pm = metadataBuilder.visitProperty(
                    parameterType.getName(),
                    parameter.getName(), parameter.getDocumentation().orElse(null),
                    annotationMetadata.stringValue(Bindable.class, "defaultValue").orElse(null)
            );
            parameter.annotate(Property.class, (builder) -> builder.member("name", pm.getPath()));
        }
    }

    private boolean isRecordConfig(MethodElement constructor) {
        ClassElement declaringType = constructor.getDeclaringType();
        return declaringType.isRecord() && declaringType.hasStereotype(ConfigurationReader.class);
    }

    @Override
    public void visitDefaultConstructor(AnnotationMetadata annotationMetadata, VisitorContext visitorContext) {
        if (this.constructor == null) {
            ClassElement bean = ClassElement.of(beanType.getClassName());
            MethodElement defaultConstructor = MethodElement.of(
                    bean,
                    annotationMetadata,
                    bean,
                    bean,
                    "<init>"
            );
            constructor = defaultConstructor;

            // now prepare the implementation of the build method. See BeanFactory interface
            visitBuildMethodDefinition(defaultConstructor, false);
            // now override the injectBean method
            visitInjectMethodDefinition();
        }
    }

    /**
     * Finalize the bean definition to the given output stream.
     */
    @SuppressWarnings("Duplicates")
    @Override
    public void visitBeanDefinitionEnd() {
        if (classWriter == null) {
            throw new IllegalStateException("At least one called to visitBeanDefinitionConstructor(..) is required");
        }

        processAllBeanElementVisitors();

        if (constructor instanceof MethodElement) {
            MethodElement methodElement = (MethodElement) constructor;
            boolean isParametrized = Arrays.stream(methodElement.getParameters())
                    .map(AnnotationMetadataProvider::getAnnotationMetadata)
                    .anyMatch(this::isAnnotatedWithParameter);
            if (isParametrized) {
                interfaceTypes.add(ParametrizedBeanFactory.class);
            }
        }

        String[] interfaceInternalNames = new String[interfaceTypes.size()];
        Iterator<Class> j = interfaceTypes.iterator();
        for (int i = 0; i < interfaceInternalNames.length; i++) {
            interfaceInternalNames[i] = Type.getInternalName(j.next());
        }
        classWriter.visit(V1_8, ACC_SYNTHETIC,
                beanDefinitionInternalName,
                generateBeanDefSig(providedType.getInternalName()),
                isSuperFactory ? TYPE_ABSTRACT_BEAN_DEFINITION.getInternalName() : superType.getInternalName(),
                interfaceInternalNames);

        classWriter.visitAnnotation(TYPE_GENERATED.getDescriptor(), false);

        if (buildMethodVisitor == null) {
            throw new IllegalStateException("At least one call to visitBeanDefinitionConstructor() is required");
        }

        GeneratorAdapter staticInit = visitStaticInitializer(classWriter);

        classWriter.visitField(ACC_PRIVATE | ACC_FINAL | ACC_STATIC, FIELD_CONSTRUCTOR,
                Type.getType(AbstractInitializableBeanDefinition.MethodOrFieldReference.class).getDescriptor(), null, null);

        int methodsLength = methodInjectionPoints.size() + postConstructMethodVisits.size() + preDestroyMethodVisits.size();
        if (!superBeanDefinition && methodsLength > 0) {
            Type methodsFieldType = Type.getType(AbstractInitializableBeanDefinition.MethodReference[].class);
            classWriter.visitField(ACC_PRIVATE | ACC_FINAL | ACC_STATIC, FIELD_INJECTION_METHODS, methodsFieldType.getDescriptor(), null, null);
            pushNewArray(staticInit, AbstractInitializableBeanDefinition.MethodReference.class, methodsLength);
            int i = 0;
            for (MethodVisitData methodVisitData : methodInjectionPoints) {
                pushStoreInArray(staticInit, i++, methodsLength, () ->
                        pushNewMethodReference(
                                staticInit,
                                JavaModelUtils.getTypeReference(methodVisitData.beanType),
                                methodVisitData.methodElement,
                                methodVisitData.requiresReflection,
                                false, false
                        )
                );
            }
            for (MethodVisitData methodVisitData : postConstructMethodVisits) {
                pushStoreInArray(staticInit, i++, methodsLength, () ->
                        pushNewMethodReference(
                                staticInit,
                                JavaModelUtils.getTypeReference(methodVisitData.beanType),
                                methodVisitData.methodElement,
                                methodVisitData.requiresReflection,
                                true, false
                        )
                );
            }
            for (MethodVisitData methodVisitData : preDestroyMethodVisits) {
                pushStoreInArray(staticInit, i++, methodsLength, () ->
                        pushNewMethodReference(
                                staticInit,
                                JavaModelUtils.getTypeReference(methodVisitData.beanType),
                                methodVisitData.methodElement,
                                methodVisitData.requiresReflection,
                                false, true
                        )
                );
            }
            staticInit.putStatic(beanDefinitionType, FIELD_INJECTION_METHODS, methodsFieldType);
        }

        if (!fieldInjectionPoints.isEmpty()) {
            Type fieldsFieldType = Type.getType(AbstractInitializableBeanDefinition.FieldReference[].class);
            classWriter.visitField(ACC_PRIVATE | ACC_FINAL | ACC_STATIC, FIELD_INJECTION_FIELDS, fieldsFieldType.getDescriptor(), null, null);
            int length = fieldInjectionPoints.size();
            pushNewArray(staticInit, AbstractInitializableBeanDefinition.FieldReference.class, length);
            for (int i = 0; i < length; i++) {
                FieldVisitData fieldVisitData = fieldInjectionPoints.get(i);
                pushStoreInArray(staticInit, i, length, () ->
                        pushNewFieldReference(
                                staticInit,
                                JavaModelUtils.getTypeReference(fieldVisitData.beanType),
                                fieldVisitData.fieldElement,
                                fieldVisitData.requiresReflection
                        )
                );
            }
            staticInit.putStatic(beanDefinitionType, FIELD_INJECTION_FIELDS, fieldsFieldType);
        }

        if (!superBeanDefinition && hasTypeArguments()) {
            Type typeArgumentsFieldType = Type.getType(Map.class);
            classWriter.visitField(ACC_PRIVATE | ACC_FINAL | ACC_STATIC, FIELD_TYPE_ARGUMENTS, typeArgumentsFieldType.getDescriptor(), null, null);
            pushStringMapOf(staticInit, typeArguments, true, null, new Consumer<Map<String, ClassElement>>() {
                @Override
                public void accept(Map<String, ClassElement> stringClassElementMap) {
                    pushTypeArgumentElements(
                            beanDefinitionType,
                            classWriter,
                            staticInit,
                            beanDefinitionName,
                            stringClassElementMap,
                            defaultsStorage,
                            loadTypeMethods
                    );
                }
            });
            staticInit.putStatic(beanDefinitionType, FIELD_TYPE_ARGUMENTS, typeArgumentsFieldType);
        }

        // first build the constructor
        visitBeanDefinitionConstructorInternal(
                staticInit,
                constructor,
                constructorRequiresReflection
        );

        addInnerConfigurationMethod(staticInit);
        addGetExposedTypes(staticInit);

        staticInit.returnValue();
        staticInit.visitMaxs(DEFAULT_MAX_STACK, defaultsStorage.size() + 3);
        staticInit.visitEnd();

        finalizeBuildMethod();

        if (buildMethodVisitor != null) {
            buildMethodVisitor.returnValue();
            buildMethodVisitor.visitMaxs(DEFAULT_MAX_STACK, 10);
        }
        if (injectMethodVisitor != null) {
            if (injectEnd != null) {
                injectMethodVisitor.visitLabel(injectEnd);
            }
            invokeSuperInjectMethod(injectMethodVisitor, INJECT_BEAN_METHOD);
            injectMethodVisitor.returnValue();
            injectMethodVisitor.visitMaxs(DEFAULT_MAX_STACK, 10);
        }
        if (postConstructMethodVisitor != null) {
            postConstructMethodVisitor.loadLocal(postConstructInstanceLocalVarIndex);
            postConstructMethodVisitor.returnValue();
            postConstructMethodVisitor.visitMaxs(DEFAULT_MAX_STACK, 10);
        }
        if (preDestroyMethodVisitor != null) {
            preDestroyMethodVisitor.loadLocal(preDestroyInstanceLocalVarIndex);
            preDestroyMethodVisitor.returnValue();
            preDestroyMethodVisitor.visitMaxs(DEFAULT_MAX_STACK, 10);
        }
        if (interceptedDisposeMethod != null) {
            interceptedDisposeMethod.visitMaxs(1, 1);
            interceptedDisposeMethod.visitEnd();
        }

        getInterceptedType().ifPresent(t -> implementInterceptedTypeMethod(t, this.classWriter));

        for (GeneratorAdapter method : loadTypeMethods.values()) {
            method.visitMaxs(3, 1);
            method.visitEnd();
        }
        classWriter.visitEnd();
        this.beanFinalized = true;
    }

    private void processAllBeanElementVisitors() {
        for (BeanElementVisitor<?> visitor : VISITORS) {
            if (visitor.isEnabled() && visitor.supports(this)) {
                try {
                    this.disabled = visitor.visitBeanElement(this, visitorContext) == null;
                    if (disabled) {
                        break;
                    }
                } catch (Exception e) {
                    visitorContext.fail(
                            "Error occurred visiting BeanElementVisitor of type [" + visitor.getClass().getName() + "]: " + e.getMessage(),
                            this
                    );
                    break;
                }
            }
        }
    }

    private void addInnerConfigurationMethod(GeneratorAdapter staticInit) {
        if (isConfigurationProperties) {
            String[] innerClasses = beanTypeElement.getEnclosedElements(ElementQuery.of(ClassElement.class))
                    .stream()
                    .filter(this::isConfigurationProperties)
                    .map(Element::getName)
                    .toArray(String[]::new);

            if (innerClasses.length > 0) {
                classWriter.visitField(ACC_PRIVATE | ACC_FINAL | ACC_STATIC, FIELD_INNER_CLASSES, Type.getType(Set.class).getDescriptor(), null, null);
                pushStoreClassesAsSet(staticInit, innerClasses);
                staticInit.putStatic(beanDefinitionType, FIELD_INNER_CLASSES, Type.getType(Set.class));

                GeneratorAdapter isInnerConfigurationMethod = startProtectedMethod(classWriter, "isInnerConfiguration", boolean.class.getName(), Class.class.getName());
                isInnerConfigurationMethod.getStatic(beanDefinitionType, FIELD_INNER_CLASSES, Type.getType(Set.class));
                isInnerConfigurationMethod.loadArg(0);
                isInnerConfigurationMethod.invokeInterface(Type.getType(Collection.class), org.objectweb.asm.commons.Method.getMethod(
                        ReflectionUtils.getRequiredMethod(Collection.class, "contains", Object.class)
                ));
                isInnerConfigurationMethod.returnValue();
                isInnerConfigurationMethod.visitMaxs(1, 1);
                isInnerConfigurationMethod.visitEnd();
            }
        }
    }

    private void addGetExposedTypes(GeneratorAdapter staticInit) {
        if (annotationMetadata.hasDeclaredAnnotation(Bean.class.getName())) {
            final String[] exposedTypes = annotationMetadata.stringValues(Bean.class.getName(), "typed");
            if (exposedTypes.length > 0) {
                classWriter.visitField(ACC_PRIVATE | ACC_FINAL | ACC_STATIC, FIELD_EXPOSED_TYPES, Type.getType(Set.class).getDescriptor(), null, null);
                pushStoreClassesAsSet(staticInit, exposedTypes);
                staticInit.putStatic(beanDefinitionType, FIELD_EXPOSED_TYPES, Type.getType(Set.class));

                GeneratorAdapter getExposedTypesMethod = startPublicMethod(classWriter, "getExposedTypes", Set.class.getName());
                getExposedTypesMethod.getStatic(beanDefinitionType, FIELD_EXPOSED_TYPES, Type.getType(Set.class));
                getExposedTypesMethod.returnValue();
                getExposedTypesMethod.visitMaxs(1, 1);
                getExposedTypesMethod.visitEnd();
            }
        }
    }

    private void pushStoreClassesAsSet(GeneratorAdapter writer, String[] classes) {
        if (classes.length > 3) {
            writer.newInstance(Type.getType(HashSet.class));
            writer.dup();
            pushArrayOfClasses(writer, classes);
            writer.invokeStatic(Type.getType(Arrays.class), org.objectweb.asm.commons.Method.getMethod(
                    ReflectionUtils.getRequiredMethod(Arrays.class, "asList", Object[].class)
            ));
            writer.invokeConstructor(Type.getType(HashSet.class), org.objectweb.asm.commons.Method.getMethod(
                    ReflectionUtils.findConstructor(HashSet.class, Collection.class).get()
            ));
        } else if (classes.length == 1) {
            pushClass(writer, classes[0]);
            writer.invokeStatic(Type.getType(Collections.class), org.objectweb.asm.commons.Method.getMethod(
                    ReflectionUtils.getRequiredMethod(Collections.class, "singleton", Object.class)
            ));
        } else {
            pushArrayOfClasses(writer, classes);
            writer.invokeStatic(Type.getType(Arrays.class), org.objectweb.asm.commons.Method.getMethod(
                    ReflectionUtils.getRequiredMethod(Arrays.class, "asList", Object[].class)
            ));
        }
    }

    private boolean hasTypeArguments() {
        return typeArguments != null && !typeArguments.isEmpty() && typeArguments.entrySet().stream().anyMatch(e -> !e.getValue().isEmpty());
    }

    private boolean isSingleton(String scope) {
        if (beanProducingElement instanceof FieldElement && beanProducingElement.isFinal()) {
            // final fields can't change so effectively singleton
            return true;
        }

        if (scope != null) {
            return scope.equals(Singleton.class.getName()) || scope.equals(AnnotationUtil.SINGLETON);
        } else {
            final AnnotationMetadata annotationMetadata;
            if (beanProducingElement instanceof ClassElement) {
                annotationMetadata = getAnnotationMetadata();
            } else {
                annotationMetadata = beanProducingElement.getDeclaredMetadata();
            }

            return annotationMetadata.stringValue(DefaultScope.class)
                    .map(t -> t.equals(Singleton.class.getName()) || t.equals(AnnotationUtil.SINGLETON))
                    .orElse(false);
        }
    }

    private void lookupReferenceAnnotationMetadata(GeneratorAdapter annotationMetadataMethod) {
        annotationMetadataMethod.loadThis();
        annotationMetadataMethod.getStatic(getTypeReferenceForName(getBeanDefinitionReferenceClassName()), AbstractAnnotationMetadataWriter.FIELD_ANNOTATION_METADATA, Type.getType(AnnotationMetadata.class));
        annotationMetadataMethod.returnValue();
        annotationMetadataMethod.visitMaxs(1, 1);
        annotationMetadataMethod.visitEnd();
    }

    /**
     * @return The bytes of the class
     */
    public byte[] toByteArray() {
        if (!beanFinalized) {
            throw new IllegalStateException("Bean definition not finalized. Call visitBeanDefinitionEnd() first.");
        }
        return classWriter.toByteArray();
    }

    @Override
    public void accept(ClassWriterOutputVisitor visitor) throws IOException {
        if (disabled) {
            return;
        }
        try (OutputStream out = visitor.visitClass(getBeanDefinitionName(), getOriginatingElements())) {
            if (!innerClasses.isEmpty()) {
                for (Map.Entry<String, ClassWriter> entry : innerClasses.entrySet()) {
                    try (OutputStream constructorOut = visitor.visitClass(entry.getKey(), getOriginatingElements())) {
                        constructorOut.write(entry.getValue().toByteArray());
                    }
                }
            }
            try {
                if (executableMethodsDefinitionWriter != null) {
                    executableMethodsDefinitionWriter.accept(visitor);
                }
            } catch (RuntimeException e) {
                Throwable cause = e.getCause();
                if (cause instanceof IOException) {
                    throw (IOException) cause;
                } else {
                    throw e;
                }
            }
            out.write(toByteArray());
        }
    }

    @Override
    public void visitSetterValue(
            TypedElement declaringType,
            MethodElement methodElement,
            boolean requiresReflection,
            boolean isOptional) {

        final MethodVisitData methodVisitData = new MethodVisitData(
                declaringType,
                methodElement,
                requiresReflection
        );

        methodInjectionPoints.add(methodVisitData);

        if (!requiresReflection) {
            resolveBeanOrValueForSetter(declaringType, methodElement, isOptional);
        }
        currentMethodIndex++;

    }

    @Override
    public void visitPostConstructMethod(TypedElement declaringType,
                                         MethodElement methodElement,
                                         boolean requiresReflection, VisitorContext visitorContext) {

        visitPostConstructMethodDefinition(false);
        // for "super bean definitions" we just delegate to super
        if (!superBeanDefinition || isInterceptedLifeCycleByType(this.annotationMetadata, "POST_CONSTRUCT")) {
            MethodVisitData methodVisitData = new MethodVisitData(declaringType, methodElement, requiresReflection);
            postConstructMethodVisits.add(methodVisitData);
            visitMethodInjectionPointInternal(methodVisitData, postConstructMethodVisitor, postConstructInstanceLocalVarIndex);
        }
    }

    @Override
    public void visitPreDestroyMethod(TypedElement declaringType,
                                      MethodElement methodElement,
                                      boolean requiresReflection,
                                      VisitorContext visitorContext) {
        // for "super bean definitions" we just delegate to super
        if (!superBeanDefinition || isInterceptedLifeCycleByType(this.annotationMetadata, "PRE_DESTROY")) {
            visitPreDestroyMethodDefinition(false);

            MethodVisitData methodVisitData = new MethodVisitData(declaringType, methodElement, requiresReflection);
            preDestroyMethodVisits.add(methodVisitData);
            visitMethodInjectionPointInternal(methodVisitData, preDestroyMethodVisitor, preDestroyInstanceLocalVarIndex);
        }
    }

    @Override
    public void visitMethodInjectionPoint(TypedElement declaringType,
                                          MethodElement methodElement,
                                          boolean requiresReflection, VisitorContext visitorContext) {
        applyConfigurationInjectionIfNecessary(methodElement);

        MethodVisitData methodVisitData = new MethodVisitData(declaringType, methodElement, requiresReflection);
        methodInjectionPoints.add(methodVisitData);
        visitMethodInjectionPointInternal(methodVisitData, injectMethodVisitor, injectInstanceLocalVarIndex);
    }

    @Override
    public int visitExecutableMethod(TypedElement declaringBean,
                                     MethodElement methodElement, VisitorContext visitorContext) {
        return visitExecutableMethod(
                declaringBean,
                methodElement,
                null,
                null
        );
    }

    /**
     * Visit a method that is to be made executable allow invocation of said method without reflection.
     *
     * @param declaringType                    The declaring type of the method. Either a Class or a string representing the
     *                                         name of the type
     * @param methodElement                    The method element
     * @param interceptedProxyClassName        The intercepted proxy class name
     * @param interceptedProxyBridgeMethodName The intercepted proxy bridge method name
     * @return The index of a new method.
     */
    public int visitExecutableMethod(TypedElement declaringType,
                                     MethodElement methodElement,
                                     String interceptedProxyClassName,
                                     String interceptedProxyBridgeMethodName) {

        AnnotationMetadata annotationMetadata = methodElement.getAnnotationMetadata();

        DefaultAnnotationMetadata.contributeDefaults(
                this.annotationMetadata,
                annotationMetadata
        );
        for (ParameterElement parameterElement : methodElement.getSuspendParameters()) {
            DefaultAnnotationMetadata.contributeDefaults(
                    this.annotationMetadata,
                    parameterElement.getAnnotationMetadata()
            );
        }

        if (executableMethodsDefinitionWriter == null) {
            executableMethodsDefinitionWriter = new ExecutableMethodsDefinitionWriter(beanDefinitionName, getBeanDefinitionReferenceClassName(), originatingElements);
        }
        return executableMethodsDefinitionWriter.visitExecutableMethod(declaringType, methodElement, interceptedProxyClassName, interceptedProxyBridgeMethodName);
    }

    @Override
    public String toString() {
        return "BeanDefinitionWriter{" +
                "beanFullClassName='" + beanFullClassName + '\'' +
                '}';
    }

    @Override
    public String getPackageName() {
        return packageName;
    }

    @Override
    public String getBeanSimpleName() {
        return beanSimpleClassName;
    }

    @Override
    public AnnotationMetadata getAnnotationMetadata() {
        return this.annotationMetadata;
    }

    @Override
    public void visitConfigBuilderField(
            ClassElement type,
            String field,
            AnnotationMetadata annotationMetadata,
            ConfigurationMetadataBuilder metadataBuilder,
            boolean isInterface) {
        String factoryMethod = annotationMetadata
                .getValue(
                        ConfigurationBuilder.class,
                        "factoryMethod",
                        String.class)
                .orElse(null);

        if (StringUtils.isNotEmpty(factoryMethod)) {
            Type builderType = JavaModelUtils.getTypeReference(type);

            injectMethodVisitor.loadLocal(injectInstanceLocalVarIndex, beanType);
            injectMethodVisitor.invokeStatic(
                    builderType,
                    org.objectweb.asm.commons.Method.getMethod(
                            builderType.getClassName() + " " + factoryMethod + "()"
                    )
            );

            injectMethodVisitor.putField(beanType, field, builderType);
        }

        this.currentConfigBuilderState = new ConfigBuilderState(
                type,
                field,
                false,
                annotationMetadata,
                metadataBuilder,
                isInterface);
    }

    @Override
    public void visitConfigBuilderMethod(
            ClassElement type,
            String methodName,
            AnnotationMetadata annotationMetadata,
            ConfigurationMetadataBuilder metadataBuilder,
            boolean isInterface) {

        String factoryMethod = annotationMetadata
                .getValue(
                        ConfigurationBuilder.class,
                        "factoryMethod",
                        String.class)
                .orElse(null);

        if (StringUtils.isNotEmpty(factoryMethod)) {
            Type builderType = JavaModelUtils.getTypeReference(type);

            injectMethodVisitor.loadLocal(injectInstanceLocalVarIndex, beanType);
            injectMethodVisitor.invokeStatic(
                    builderType,
                    org.objectweb.asm.commons.Method.getMethod(
                            builderType.getClassName() + " " + factoryMethod + "()"
                    )
            );

            String propertyName = NameUtils.getPropertyNameForGetter(methodName);
            String setterName = NameUtils.setterNameFor(propertyName);

            injectMethodVisitor.invokeVirtual(beanType, org.objectweb.asm.commons.Method.getMethod(
                    "void " + setterName + "(" + builderType.getClassName() + ")"
            ));
        }

        this.currentConfigBuilderState = new ConfigBuilderState(type, methodName, true, annotationMetadata, metadataBuilder, isInterface);
    }

    @Override
    public void visitConfigBuilderDurationMethod(
            String prefix,
            ClassElement returnType,
            String methodName,
            String path) {
        visitConfigBuilderMethodInternal(
                prefix,
                returnType,
                methodName,
                ClassElement.of(Duration.class),
                Collections.emptyMap(),
                true,
                path
        );
    }

    @Override
    public void visitConfigBuilderMethod(
            String prefix,
            ClassElement returnType,
            String methodName,
            ClassElement paramType,
            Map<String, ClassElement> generics,
            String path) {

        visitConfigBuilderMethodInternal(
                prefix,
                returnType,
                methodName,
                paramType,
                generics,
                false,
                path
        );
    }

    @Override
    public void visitConfigBuilderEnd() {
        currentConfigBuilderState = null;
    }

    @Override
    public void setRequiresMethodProcessing(boolean shouldPreProcess) {
        this.preprocessMethods = shouldPreProcess;
    }

    @Override
    public void visitTypeArguments(Map<String, Map<String, ClassElement>> typeArguments) {
        this.typeArguments = typeArguments;
    }

    @Override
    public boolean requiresMethodProcessing() {
        return this.preprocessMethods;
    }

    @Override
    public void visitFieldInjectionPoint(
            TypedElement declaringType,
            FieldElement fieldElement,
            boolean requiresReflection) {

        boolean requiresGenericType = false;
        Method methodToInvoke;
        final ClassElement genericType = fieldElement.getGenericType();
        boolean isArray = genericType.isArray();
        boolean isCollection = genericType.isAssignable(Collection.class);
        if (isCollection || isArray) {
            requiresGenericType = true;
            ClassElement typeArgument = genericType.isArray() ? genericType.fromArray() : genericType.getFirstTypeArgument().orElse(null);
            if (typeArgument != null) {
                if (typeArgument.isAssignable(BeanRegistration.class)) {
                    methodToInvoke = GET_BEAN_REGISTRATIONS_FOR_FIELD;
                } else {
                    methodToInvoke = GET_BEANS_OF_TYPE_FOR_FIELD;
                }
            } else {
                requiresGenericType = false;
                methodToInvoke = GET_BEAN_FOR_FIELD;
            }
        } else if (genericType.isAssignable(Stream.class)) {
            requiresGenericType = true;
            methodToInvoke = GET_STREAM_OF_TYPE_FOR_FIELD;
        } else if (genericType.isAssignable(Optional.class)) {
            requiresGenericType = true;
            methodToInvoke = FIND_BEAN_FOR_FIELD;
        } else if (genericType.isAssignable(BeanRegistration.class)) {
            requiresGenericType = true;
            methodToInvoke = GET_BEAN_REGISTRATION_FOR_FIELD;
        } else {
            methodToInvoke = GET_BEAN_FOR_FIELD;
        }
        visitFieldInjectionPointInternal(
                declaringType,
                fieldElement,
                requiresReflection,
                methodToInvoke,
                false,
                isArray, requiresGenericType
        );

        fieldInjectionPoints.add(new FieldVisitData(declaringType, fieldElement, requiresReflection));
    }

    @Override
    public void visitFieldValue(
            TypedElement declaringType,
            FieldElement fieldElement,
            boolean requiresReflection,
            boolean isOptional) {

        visitFieldInjectionPointInternal(
                declaringType,
                fieldElement,
                requiresReflection,
                GET_VALUE_FOR_FIELD,
                isOptional,
                false, false);

        fieldInjectionPoints.add(new FieldVisitData(declaringType, fieldElement, requiresReflection));
    }

    private void visitConfigBuilderMethodInternal(
            String prefix,
            ClassElement returnType,
            String methodName,
            ClassElement paramType,
            Map<String, ClassElement> generics,
            boolean isDurationWithTimeUnit,
            String propertyPath) {

        if (currentConfigBuilderState != null) {
            Type builderType = currentConfigBuilderState.getType();
            String builderName = currentConfigBuilderState.getName();
            boolean isResolveBuilderViaMethodCall = currentConfigBuilderState.isMethod();
            GeneratorAdapter injectMethodVisitor = this.injectMethodVisitor;

            String propertyName = NameUtils.hyphenate(NameUtils.decapitalize(methodName.substring(prefix.length())), true);

            boolean zeroArgs = paramType == null;

            // Optional optional = AbstractBeanDefinition.getValueForPath(...)
            int optionalLocalIndex = pushGetValueForPathCall(injectMethodVisitor, paramType, propertyName, propertyPath, zeroArgs, generics);

            Label ifEnd = new Label();
            // if(optional.isPresent())
            injectMethodVisitor.invokeVirtual(Type.getType(Optional.class), org.objectweb.asm.commons.Method.getMethod(
                    ReflectionUtils.getRequiredMethod(Optional.class, "isPresent")
            ));
            injectMethodVisitor.push(false);
            injectMethodVisitor.ifCmp(Type.BOOLEAN_TYPE, GeneratorAdapter.EQ, ifEnd);
            if (zeroArgs) {
                pushOptionalGet(injectMethodVisitor, optionalLocalIndex);
                pushCastToType(injectMethodVisitor, boolean.class);
                injectMethodVisitor.push(false);
                injectMethodVisitor.ifCmp(Type.BOOLEAN_TYPE, GeneratorAdapter.EQ, ifEnd);
            }

            injectMethodVisitor.visitLabel(new Label());

            String methodDescriptor;
            if (zeroArgs) {
                methodDescriptor = getMethodDescriptor(returnType, Collections.emptyList());
            } else if (isDurationWithTimeUnit) {
                methodDescriptor = getMethodDescriptor(returnType, Arrays.asList(ClassElement.of(long.class), ClassElement.of(TimeUnit.class)));
            } else {
                methodDescriptor = getMethodDescriptor(returnType, Collections.singleton(paramType));
            }

            Label tryStart = new Label();
            Label tryEnd = new Label();
            Label exceptionHandler = new Label();
            injectMethodVisitor.visitTryCatchBlock(tryStart, tryEnd, exceptionHandler, Type.getInternalName(NoSuchMethodError.class));

            injectMethodVisitor.visitLabel(tryStart);

            injectMethodVisitor.loadLocal(injectInstanceLocalVarIndex);
            if (isResolveBuilderViaMethodCall) {
                String desc = builderType.getClassName() + " " + builderName + "()";
                injectMethodVisitor.invokeVirtual(beanType, org.objectweb.asm.commons.Method.getMethod(desc));
            } else {
                injectMethodVisitor.getField(beanType, builderName, builderType);
            }

            if (!zeroArgs) {
                pushOptionalGet(injectMethodVisitor, optionalLocalIndex);
                pushCastToType(injectMethodVisitor, paramType);
            }

            boolean isInterface = currentConfigBuilderState.isInterface();

            if (isDurationWithTimeUnit) {
                injectMethodVisitor.invokeVirtual(Type.getType(Duration.class), org.objectweb.asm.commons.Method.getMethod(
                        ReflectionUtils.getRequiredMethod(Duration.class, "toMillis")
                ));
                Type tu = Type.getType(TimeUnit.class);
                injectMethodVisitor.getStatic(tu, "MILLISECONDS", tu);
            }

            if (isInterface) {
                injectMethodVisitor.invokeInterface(builderType,
                        new org.objectweb.asm.commons.Method(methodName, methodDescriptor));
            } else {
                injectMethodVisitor.invokeVirtual(builderType,
                        new org.objectweb.asm.commons.Method(methodName, methodDescriptor));
            }

            if (returnType != PrimitiveElement.VOID) {
                injectMethodVisitor.pop();
            }
            injectMethodVisitor.visitJumpInsn(GOTO, tryEnd);
            injectMethodVisitor.visitLabel(exceptionHandler);
            injectMethodVisitor.pop();
            injectMethodVisitor.visitLabel(tryEnd);
            injectMethodVisitor.visitLabel(ifEnd);
        }
    }

    private void pushOptionalGet(GeneratorAdapter injectMethodVisitor, int optionalLocalIndex) {
        injectMethodVisitor.loadLocal(optionalLocalIndex);
        // get the value: optional.get()
        injectMethodVisitor.invokeVirtual(Type.getType(Optional.class), org.objectweb.asm.commons.Method.getMethod(
                ReflectionUtils.getRequiredMethod(Optional.class, "get")
        ));
    }

    private int pushGetValueForPathCall(GeneratorAdapter injectMethodVisitor, ClassElement propertyType, String propertyName, String propertyPath, boolean zeroArgs, Map<String, ClassElement> generics) {
        injectMethodVisitor.loadThis();
        injectMethodVisitor.loadArg(0); // the resolution context
        injectMethodVisitor.loadArg(1); // the bean context
        if (zeroArgs) {
            // if the parameter type is null this is a zero args method that expects a boolean flag
            buildArgument(
                    injectMethodVisitor,
                    propertyName,
                    Type.getType(Boolean.class)
            );
        } else {
            buildArgumentWithGenerics(
                    beanDefinitionType,
                    classWriter,
                    injectMethodVisitor,
                    propertyName,
                    JavaModelUtils.getTypeReference(propertyType),
                    propertyType,
                    generics,
                    new HashSet<>(),
                    new HashMap<>(),
                    loadTypeMethods
            );
        }

        injectMethodVisitor.push(propertyPath);
        // Optional optional = AbstractBeanDefinition.getValueForPath(...)
        injectMethodVisitor.invokeVirtual(beanDefinitionType, org.objectweb.asm.commons.Method.getMethod(GET_VALUE_FOR_PATH));
        int optionalInstanceIndex = injectMethodVisitor.newLocal(Type.getType(Optional.class));
        injectMethodVisitor.storeLocal(optionalInstanceIndex);
        injectMethodVisitor.loadLocal(optionalInstanceIndex);
        return optionalInstanceIndex;
    }

    private void visitFieldInjectionPointInternal(
            TypedElement declaringType,
            FieldElement fieldElement,
            boolean requiresReflection,
            Method methodToInvoke,
            boolean isValueOptional,
            boolean isArray,
            boolean requiresGenericType) {

        AnnotationMetadata annotationMetadata = fieldElement.getAnnotationMetadata();
        autoApplyNamedIfPresent(fieldElement, annotationMetadata);
        DefaultAnnotationMetadata.contributeDefaults(this.annotationMetadata, annotationMetadata);
        Type declaringTypeRef = JavaModelUtils.getTypeReference(declaringType);
        GeneratorAdapter injectMethodVisitor = this.injectMethodVisitor;

        Label falseCondition = null;
        if (isValueOptional) {
            Label trueCondition = new Label();
            falseCondition = new Label();
            injectMethodVisitor.loadThis();
            // 1st argument load BeanResolutionContext
            injectMethodVisitor.loadArg(0);
            // 2nd argument load BeanContext
            injectMethodVisitor.loadArg(1);
            // 3rd argument the field index
            injectMethodVisitor.push(currentFieldIndex);
            // 4th argument is multi value property
            injectMethodVisitor.push(isMultiValueProperty(fieldElement.getType()));
            injectMethodVisitor.invokeVirtual(beanDefinitionType, org.objectweb.asm.commons.Method.getMethod(CONTAINS_VALUE_FOR_FIELD));
            injectMethodVisitor.push(false);

            injectMethodVisitor.ifCmp(Type.BOOLEAN_TYPE, GeneratorAdapter.EQ, falseCondition);
            injectMethodVisitor.visitLabel(trueCondition);
        }

        injectMethodVisitor.loadLocal(injectInstanceLocalVarIndex, beanType);

        if (fieldElement.getGenericField().isAssignable(BeanContext.class)) {
            injectMethodVisitor.loadArg(1);
        } else {
            // first get the value of the field by calling AbstractBeanDefinition.getBeanForField(..)
            // load 'this'
            injectMethodVisitor.loadThis();
            // 1st argument load BeanResolutionContext
            injectMethodVisitor.loadArg(0);
            // 2nd argument load BeanContext
            injectMethodVisitor.loadArg(1);
            // 3rd argument the field index
            injectMethodVisitor.push(currentFieldIndex);
            if (requiresGenericType) {
                resolveFieldArgumentGenericType(injectMethodVisitor, fieldElement.getGenericType(), currentFieldIndex);
            }
            // push qualifier
            pushQualifier(injectMethodVisitor, fieldElement,
                    () -> resolveFieldArgument(injectMethodVisitor, currentFieldIndex));
            // invoke getBeanForField
            pushInvokeMethodOnSuperClass(injectMethodVisitor, methodToInvoke);
            if (isArray) {
                convertToArray(fieldElement.getType().fromArray(), injectMethodVisitor);
            }
            // cast the return value to the correct type
            pushCastToType(injectMethodVisitor, fieldElement.getType());
        }
        Type fieldType = JavaModelUtils.getTypeReference(fieldElement.getType());
        if (!requiresReflection) {
            injectMethodVisitor.putField(declaringTypeRef, fieldElement.getName(), fieldType);
        } else {
            pushBoxPrimitiveIfNecessary(fieldType, injectMethodVisitor);
            int storedIndex = injectMethodVisitor.newLocal(Type.getType(Object.class));
            injectMethodVisitor.storeLocal(storedIndex);
            injectMethodVisitor.loadThis();
            injectMethodVisitor.loadArg(0);
            injectMethodVisitor.loadArg(1);
            injectMethodVisitor.push(currentFieldIndex);
            injectMethodVisitor.loadLocal(injectInstanceLocalVarIndex);
            injectMethodVisitor.loadLocal(storedIndex);
            injectMethodVisitor.invokeVirtual(superType, SET_FIELD_WITH_REFLECTION_METHOD);
            injectMethodVisitor.pop();
        }
        if (falseCondition != null) {
            injectMethodVisitor.visitLabel(falseCondition);
        }
        currentFieldIndex++;
    }

    private boolean isMultiValueProperty(ClassElement type) {
        return type.isAssignable(Map.class) || type.isAssignable(Collection.class) || isConfigurationProperties(type);
    }

    private void pushQualifier(GeneratorAdapter generatorAdapter, AnnotationMetadata element, Runnable resolveArgument) {
        if (!element.getAnnotationNamesByStereotype(AnnotationUtil.QUALIFIER).isEmpty()) {
            resolveArgument.run();
            generatorAdapter.invokeStatic(Type.getType(Qualifiers.class), org.objectweb.asm.commons.Method.getMethod(
                    ReflectionUtils.getRequiredMethod(Qualifiers.class, "forArgument", Argument.class)
            ));
        } else if (element.hasAnnotation(AnnotationUtil.ANN_INTERCEPTOR_BINDING_QUALIFIER)) {
            resolveArgument.run();
            generatorAdapter.invokeInterface(Type.getType(AnnotationMetadataProvider.class), org.objectweb.asm.commons.Method.getMethod(
                    ReflectionUtils.getRequiredMethod(AnnotationMetadataProvider.class, "getAnnotationMetadata")
            ));
            generatorAdapter.invokeStatic(Type.getType(Qualifiers.class), org.objectweb.asm.commons.Method.getMethod(
                    ReflectionUtils.getRequiredMethod(Qualifiers.class, "byInterceptorBinding", AnnotationMetadata.class)
            ));
        } else {
            String[] byType = element.hasDeclaredAnnotation(io.micronaut.context.annotation.Type.NAME) ? element.stringValues(io.micronaut.context.annotation.Type.NAME) : null;
            if (byType != null && byType.length > 0) {
                pushArrayOfClasses(generatorAdapter, byType);
                generatorAdapter.invokeStatic(Type.getType(Qualifiers.class), org.objectweb.asm.commons.Method.getMethod(
                        ReflectionUtils.getRequiredMethod(Qualifiers.class, "byType", Class[].class)
                ));
            } else {
                generatorAdapter.push((String) null);
            }
        }
    }

    private void pushArrayOfClasses(GeneratorAdapter writer, String[] byType) {
        int len = byType.length;
        pushNewArray(writer, Class.class, len);
        for (int i = 0; i < len; i++) {
            final String type = byType[i];
            pushStoreInArray(writer, i, len, () -> pushClass(writer, type));
        }
    }

    private void pushClass(GeneratorAdapter writer, String className) {
        writer.push(Type.getObjectType(className.replace('.', '/')));
    }

    private void convertToArray(ClassElement arrayType, GeneratorAdapter injectMethodVisitor) {
        injectMethodVisitor.push(0);
        injectMethodVisitor.newArray(JavaModelUtils.getTypeReference(arrayType));
        injectMethodVisitor.invokeInterface(Type.getType(Collection.class), COLLECTION_TO_ARRAY);
    }

    private void autoApplyNamedIfPresent(Element element, AnnotationMetadata annotationMetadata) {
        if (annotationMetadata.hasAnnotation(AnnotationUtil.NAMED) || annotationMetadata.hasStereotype(AnnotationUtil.NAMED)) {
            autoApplyNamed(element);
        }
    }

    private void autoApplyNamed(Element element) {
        if (!element.stringValue(AnnotationUtil.NAMED).isPresent()) {
            element.annotate(AnnotationUtil.NAMED, (builder) -> {
                final String name;

                if (element instanceof ClassElement) {
                    name = NameUtils.decapitalize(element.getSimpleName());
                } else {
                    if (element instanceof MethodElement) {
                        final String n = element.getName();
                        if (NameUtils.isGetterName(n)) {
                            name = NameUtils.getPropertyNameForGetter(n);
                        } else {
                            name = n;
                        }
                    } else {
                        name = element.getName();
                    }
                }
                builder.value(name);
            });
        }
    }

    /**
     * @param methodVisitData     The data for the method
     * @param injectMethodVisitor The inject method visitor
     * @param injectInstanceIndex The inject instance index
     */
    private void visitMethodInjectionPointInternal(MethodVisitData methodVisitData,
                                                   GeneratorAdapter injectMethodVisitor,
                                                   int injectInstanceIndex) {


        MethodElement methodElement = methodVisitData.getMethodElement();
        final AnnotationMetadata annotationMetadata = methodElement.getAnnotationMetadata();
        final List<ParameterElement> argumentTypes = Arrays.asList(methodElement.getParameters());
        applyDefaultNamedToParameters(argumentTypes);
        final TypedElement declaringType = methodVisitData.beanType;
        final String methodName = methodElement.getName();
        final boolean requiresReflection = methodVisitData.requiresReflection;
        final ClassElement returnType = methodElement.getReturnType();
        DefaultAnnotationMetadata.contributeDefaults(this.annotationMetadata, annotationMetadata);
        boolean hasArguments = methodElement.hasParameters();
        int argCount = hasArguments ? argumentTypes.size() : 0;
        Type declaringTypeRef = JavaModelUtils.getTypeReference(declaringType);

        for (ParameterElement value : argumentTypes) {
            DefaultAnnotationMetadata.contributeDefaults(this.annotationMetadata, value.getAnnotationMetadata());
        }

        if (!requiresReflection) {
            // if the method doesn't require reflection then invoke it directly

            // invoke the method on this injected instance
            injectMethodVisitor.loadLocal(injectInstanceIndex, beanType);

            String methodDescriptor;
            if (hasArguments) {
                methodDescriptor = getMethodDescriptor(returnType, argumentTypes);
                Iterator<ParameterElement> argIterator = argumentTypes.iterator();
                for (int i = 0; i < argCount; i++) {
                    ParameterElement entry = argIterator.next();
                    pushMethodParameterValue(injectMethodVisitor, i, entry);
                }
            } else {
                methodDescriptor = getMethodDescriptor(returnType, Collections.emptyList());
            }
            injectMethodVisitor.visitMethodInsn(isInterface ? INVOKEINTERFACE : INVOKEVIRTUAL,
                    declaringTypeRef.getInternalName(), methodName,
                    methodDescriptor, isInterface);
            if (isConfigurationProperties && returnType != PrimitiveElement.VOID) {
                injectMethodVisitor.pop();
            }
        } else {
            injectMethodVisitor.loadThis();
            injectMethodVisitor.loadArg(0);
            injectMethodVisitor.loadArg(1);
            injectMethodVisitor.push(currentMethodIndex);
            injectMethodVisitor.loadLocal(injectInstanceLocalVarIndex, beanType);
            if (hasArguments) {
                pushNewArray(injectMethodVisitor, Object.class, argumentTypes.size());
                Iterator<ParameterElement> argIterator = argumentTypes.iterator();
                for (int i = 0; i < argCount; i++) {
                    int finalI = i;
                    pushStoreInArray(injectMethodVisitor, i, argumentTypes.size(), () -> {
                        ParameterElement entry = argIterator.next();
                        pushMethodParameterValue(injectMethodVisitor, finalI, entry);
                        pushBoxPrimitiveIfNecessary(entry.getType(), injectMethodVisitor);
                    });
                }
            } else {
                pushNewArray(injectMethodVisitor, Object.class, 0);
            }
            injectMethodVisitor.invokeVirtual(superType, INVOKE_WITH_REFLECTION_METHOD);
        }

        // increment the method index
        currentMethodIndex++;
    }

    private void pushMethodParameterValue(GeneratorAdapter injectMethodVisitor, int i, ParameterElement entry) {
        AnnotationMetadata argMetadata = entry.getAnnotationMetadata();
        if (entry.getGenericType().isAssignable(BeanResolutionContext.class)) {
            injectMethodVisitor.loadArg(0);
        } else if (entry.getGenericType().isAssignable(BeanContext.class)) {
            injectMethodVisitor.loadArg(1);
        } else {
            // first get the value of the field by calling AbstractBeanDefinition.getBeanForMethod(..)
            // load 'this'
            injectMethodVisitor.loadThis();
            // 1st argument load BeanResolutionContext
            injectMethodVisitor.loadArg(0);
            // 2nd argument load BeanContext
            injectMethodVisitor.loadArg(1);
            // 3rd argument the method index
            injectMethodVisitor.push(currentMethodIndex);
            // 4th argument the argument index
            injectMethodVisitor.push(i);
            // invoke getBeanForField
            boolean requiresGenericType = false;
            final ClassElement genericType = entry.getGenericType();
            Method methodToInvoke;
            boolean isCollection = genericType.isAssignable(Collection.class);
            boolean isArray = genericType.isArray();
            if (argMetadata.hasDeclaredStereotype(Value.class) || argMetadata.hasDeclaredStereotype(Property.class)) {
                methodToInvoke = GET_VALUE_FOR_METHOD_ARGUMENT;
            } else if (isCollection || isArray) {
                requiresGenericType = true;
                ClassElement typeArgument = genericType.isArray() ? genericType.fromArray() : genericType.getFirstTypeArgument().orElse(null);
                if (typeArgument != null) {
                    if (typeArgument.isAssignable(BeanRegistration.class)) {
                        methodToInvoke = GET_BEAN_REGISTRATIONS_FOR_METHOD_ARGUMENT;
                    } else {
                        methodToInvoke = GET_BEANS_OF_TYPE_FOR_METHOD_ARGUMENT;
                    }
                } else {
                    methodToInvoke = GET_BEAN_FOR_METHOD_ARGUMENT;
                    requiresGenericType = false;
                }
            } else if (genericType.isAssignable(Stream.class)) {
                requiresGenericType = true;
                methodToInvoke = GET_STREAM_OF_TYPE_FOR_METHOD_ARGUMENT;
            } else if (genericType.isAssignable(Optional.class)) {
                requiresGenericType = true;
                methodToInvoke = FIND_BEAN_FOR_METHOD_ARGUMENT;
            } else if (genericType.isAssignable(BeanRegistration.class)) {
                requiresGenericType = true;
                methodToInvoke = GET_BEAN_REGISTRATION_FOR_METHOD_ARGUMENT;
            } else {
                methodToInvoke = GET_BEAN_FOR_METHOD_ARGUMENT;
            }

            if (requiresGenericType) {
                resolveMethodArgumentGenericType(injectMethodVisitor, genericType, currentMethodIndex, i);
            }
            pushQualifier(injectMethodVisitor, entry, () -> resolveMethodArgument(injectMethodVisitor, currentMethodIndex, i));

            pushInvokeMethodOnSuperClass(injectMethodVisitor, methodToInvoke);
            if (isArray) {
                convertToArray(genericType.fromArray(), injectMethodVisitor);
            }
            // cast the return value to the correct type
            pushCastToType(injectMethodVisitor, entry);
        }
    }

    private void applyDefaultNamedToParameters(List<ParameterElement> argumentTypes) {
        for (ParameterElement parameterElement : argumentTypes) {
            final AnnotationMetadata annotationMetadata = parameterElement.getAnnotationMetadata();
            DefaultAnnotationMetadata.contributeDefaults(this.annotationMetadata, annotationMetadata);
            autoApplyNamedIfPresent(parameterElement, annotationMetadata);
        }
    }

    private void pushInvokeMethodOnSuperClass(MethodVisitor constructorVisitor, Method methodToInvoke) {
        constructorVisitor.visitMethodInsn(INVOKESPECIAL,
                isSuperFactory ? TYPE_ABSTRACT_BEAN_DEFINITION.getInternalName() : superType.getInternalName(),
                methodToInvoke.getName(),
                Type.getMethodDescriptor(methodToInvoke),
                false);
    }

    private void resolveBeanOrValueForSetter(TypedElement declaringType, MethodElement methodElement, boolean isOptional) {

        ParameterElement[] parameters = methodElement.getParameters();
        if (parameters.length != 1) {
            throw new IllegalArgumentException("Method must have exactly 1 argument");
        }
        Type declaringTypeRef = JavaModelUtils.getTypeReference(declaringType);

        ClassElement returnType = methodElement.getReturnType();
        ClassElement fieldType = parameters[0].getType();
        Method resolveMethod = GET_VALUE_FOR_METHOD_ARGUMENT;

        Label falseCondition = null;
        if (isOptional) {
            Label trueCondition = new Label();
            falseCondition = new Label();
            injectMethodVisitor.loadThis();
            // 1st argument load BeanResolutionContext
            injectMethodVisitor.loadArg(0);
            // 2nd argument load BeanContext
            injectMethodVisitor.loadArg(1);
            // 3rd argument the field index
            injectMethodVisitor.push(currentMethodIndex);
            // 4th argument the argument index
            injectMethodVisitor.push(0);
            // 5th argument is multi value property
            injectMethodVisitor.push(isMultiValueProperty(fieldType));
            // invoke method containsValueForMethodArgument
            injectMethodVisitor.invokeVirtual(beanDefinitionType, org.objectweb.asm.commons.Method.getMethod(CONTAINS_VALUE_FOR_METHOD_ARGUMENT));
            injectMethodVisitor.push(false);

            injectMethodVisitor.ifCmp(Type.BOOLEAN_TYPE, GeneratorAdapter.EQ, falseCondition);
            injectMethodVisitor.visitLabel(trueCondition);
        }
        // invoke the method on this injected instance
        injectMethodVisitor.loadLocal(injectInstanceLocalVarIndex, beanType);
        String methodDescriptor = getMethodDescriptor(returnType, Collections.singletonList(fieldType));
        // first get the value of the field by calling AbstractBeanDefinition.getBeanForField(..)
        // load 'this'
        injectMethodVisitor.loadThis();
        // 1st argument load BeanResolutionContext
        injectMethodVisitor.loadArg(0);
        // 2nd argument load BeanContext
        injectMethodVisitor.loadArg(1);
        // 3rd argument the field index
        injectMethodVisitor.push(currentMethodIndex);
        // 4th argument the argument index
        // 5th argument is the default value
        injectMethodVisitor.push(0);
        // push qualifier
        pushQualifier(injectMethodVisitor, parameters[0],
                () -> resolveMethodArgument(injectMethodVisitor, currentMethodIndex, 0));
        // invoke getBeanForField
        pushInvokeMethodOnSuperClass(injectMethodVisitor, resolveMethod);
        // cast the return value to the correct type
        pushCastToType(injectMethodVisitor, fieldType);
        injectMethodVisitor.visitMethodInsn(INVOKEVIRTUAL,
                declaringTypeRef.getInternalName(), methodElement.getName(),
                methodDescriptor, false);
        if (returnType != PrimitiveElement.VOID) {
            injectMethodVisitor.pop();
        }
        if (falseCondition != null) {
            injectMethodVisitor.visitLabel(falseCondition);
        }
    }

    @SuppressWarnings("MagicNumber")
    private void visitInjectMethodDefinition() {
        if (!superBeanDefinition && injectMethodVisitor == null) {
            String desc = getMethodDescriptor(Object.class.getName(), BeanResolutionContext.class.getName(), BeanContext.class.getName(), Object.class.getName());
            injectMethodVisitor = new GeneratorAdapter(classWriter.visitMethod(
                    ACC_PROTECTED,
                    "injectBean",
                    desc,
                    null,
                    null), ACC_PROTECTED, "injectBean", desc);

            GeneratorAdapter injectMethodVisitor = this.injectMethodVisitor;
            if (isConfigurationProperties) {
                injectMethodVisitor.loadThis();
                injectMethodVisitor.loadArg(0); // the resolution context
                injectMethodVisitor.loadArg(1); // the bean context
                // invoke AbstractBeanDefinition.containsProperties(..)
                injectMethodVisitor.invokeVirtual(beanDefinitionType, org.objectweb.asm.commons.Method.getMethod(CONTAINS_PROPERTIES_METHOD));
                injectMethodVisitor.push(false);
                injectEnd = new Label();
                injectMethodVisitor.ifCmp(Type.BOOLEAN_TYPE, GeneratorAdapter.EQ, injectEnd);
                // add the true condition
                injectMethodVisitor.visitLabel(new Label());
            }
            // The object being injected is argument 3 of the inject method
            injectMethodVisitor.loadArg(2);
            // store it in a local variable
            injectMethodVisitor.visitTypeInsn(CHECKCAST, beanType.getInternalName());
            injectInstanceLocalVarIndex = injectMethodVisitor.newLocal(beanType);
            injectMethodVisitor.storeLocal(injectInstanceLocalVarIndex);
        }
    }

    @SuppressWarnings("MagicNumber")
    private void visitPostConstructMethodDefinition(boolean intercepted) {
        if (!postConstructAdded) {
            // override the post construct method
            final String lifeCycleMethodName = "initialize";

            //  for "super bean definition" we only add code to trigger "initialize"
            if (!superBeanDefinition || intercepted) {
                interfaceTypes.add(InitializingBeanDefinition.class);

                GeneratorAdapter postConstructMethodVisitor = newLifeCycleMethod(lifeCycleMethodName);
                this.postConstructMethodVisitor = postConstructMethodVisitor;
                // The object being injected is argument 3 of the inject method
                postConstructMethodVisitor.loadArg(2);
                // store it in a local variable
                postConstructMethodVisitor.visitTypeInsn(CHECKCAST, beanType.getInternalName());
                postConstructInstanceLocalVarIndex = postConstructMethodVisitor.newLocal(beanType);
                postConstructMethodVisitor.storeLocal(postConstructInstanceLocalVarIndex);
                invokeSuperInjectMethod(postConstructMethodVisitor, POST_CONSTRUCT_METHOD);
            }

            if (intercepted) {
                // store executable method in local variable
                writeInterceptedLifecycleMethod(
                        lifeCycleMethodName,
                        lifeCycleMethodName,
                        buildMethodVisitor,
                        buildInstanceLocalVarIndex
                );
            } else {
                pushBeanDefinitionMethodInvocation(buildMethodVisitor, lifeCycleMethodName);
            }
            pushCastToType(buildMethodVisitor, beanType);
            buildMethodVisitor.loadLocal(buildInstanceLocalVarIndex);
            postConstructAdded = true;
        }
    }

    private void writeInterceptedLifecycleMethod(
            String lifeCycleMethodName,
            String dispatchMethodName,
            GeneratorAdapter targetMethodVisitor,
            int instanceLocalIndex) {
        // if there is method interception in place we need to construct an inner executable method class that invokes the "initialize"
        // method and apply interception
        final InnerClassDef postConstructInnerMethod = newInnerClass(AbstractExecutableMethod.class);
        // needs fields to propagate the correct arguments to the initialize method
        final ClassWriter postConstructInnerWriter = postConstructInnerMethod.innerClassWriter;
        final Type postConstructInnerClassType = postConstructInnerMethod.innerClassType;
        final String fieldBeanDef = "$beanDef";
        final String fieldResContext = "$resolutionContext";
        final String fieldBeanContext = "$beanContext";
        final String fieldBean = "$bean";
        newFinalField(postConstructInnerWriter, beanDefinitionType, fieldBeanDef);
        newFinalField(postConstructInnerWriter, TYPE_RESOLUTION_CONTEXT, fieldResContext);
        newFinalField(postConstructInnerWriter, TYPE_BEAN_CONTEXT, fieldBeanContext);
        newFinalField(postConstructInnerWriter, beanType, fieldBean);
        // constructor will be AbstractExecutableMethod(BeanDefinition, BeanResolutionContext, BeanContext, T beanType)
        final String constructorDescriptor = getConstructorDescriptor(new Type[]{
                beanDefinitionType,
                TYPE_RESOLUTION_CONTEXT,
                TYPE_BEAN_CONTEXT,
                beanType
        });
        GeneratorAdapter protectedConstructor = new GeneratorAdapter(
                postConstructInnerWriter.visitMethod(
                        ACC_PROTECTED, CONSTRUCTOR_NAME,
                        constructorDescriptor,
                        null,
                        null
                ),
                ACC_PROTECTED,
                CONSTRUCTOR_NAME,
                constructorDescriptor
        );
        // set field $beanDef
        protectedConstructor.loadThis();
        protectedConstructor.loadArg(0);
        protectedConstructor.putField(postConstructInnerClassType, fieldBeanDef, beanDefinitionType);
        // set field $resolutionContext
        protectedConstructor.loadThis();
        protectedConstructor.loadArg(1);
        protectedConstructor.putField(postConstructInnerClassType, fieldResContext, TYPE_RESOLUTION_CONTEXT);
        // set field $beanContext
        protectedConstructor.loadThis();
        protectedConstructor.loadArg(2);
        protectedConstructor.putField(postConstructInnerClassType, fieldBeanContext, TYPE_BEAN_CONTEXT);
        // set field $bean
        protectedConstructor.loadThis();
        protectedConstructor.loadArg(3);
        protectedConstructor.putField(postConstructInnerClassType, fieldBean, beanType);

        protectedConstructor.loadThis();
        protectedConstructor.push(beanType);
        protectedConstructor.push(lifeCycleMethodName);
        invokeConstructor(
                protectedConstructor,
                AbstractExecutableMethod.class,
                Class.class,
                String.class
        );
        protectedConstructor.returnValue();
        protectedConstructor.visitMaxs(1, 1);
        protectedConstructor.visitEnd();

        // annotation metadata should reference to the metadata for bean definition
        final GeneratorAdapter getAnnotationMetadata = startPublicFinalMethodZeroArgs(postConstructInnerWriter, AnnotationMetadata.class, "getAnnotationMetadata");
        lookupReferenceAnnotationMetadata(getAnnotationMetadata);

        // now define the invokerInternal method
        final GeneratorAdapter invokeMethod = startPublicMethod(postConstructInnerWriter, ExecutableMethodWriter.METHOD_INVOKE_INTERNAL);
        invokeMethod.loadThis();
        // load the bean definition field
        invokeMethod.getField(postConstructInnerClassType, fieldBeanDef, beanDefinitionType);
        // load the arguments to the initialize method
        // 1st argument the resolution context
        invokeMethod.loadThis();
        invokeMethod.getField(postConstructInnerClassType, fieldResContext, TYPE_RESOLUTION_CONTEXT);
        // 2nd argument the bean context
        invokeMethod.loadThis();
        invokeMethod.getField(postConstructInnerClassType, fieldBeanContext, TYPE_BEAN_CONTEXT);
        // 3rd argument the bean
        invokeMethod.loadThis();
        invokeMethod.getField(postConstructInnerClassType, fieldBean, beanType);
        // now invoke initialize
        invokeMethod.visitMethodInsn(INVOKEVIRTUAL,
                beanDefinitionInternalName,
                lifeCycleMethodName,
                METHOD_DESCRIPTOR_INITIALIZE,
                false);
        invokeMethod.returnValue();
        invokeMethod.visitMaxs(1, 1);
        invokeMethod.visitEnd();

        // now instantiate the inner class
        targetMethodVisitor.visitTypeInsn(NEW, postConstructInnerMethod.constructorInternalName);
        targetMethodVisitor.visitInsn(DUP);
        // constructor signature is AbstractExecutableMethod(BeanDefinition, BeanResolutionContext, BeanContext, T beanType)
        // 1st argument: pass outer class instance to constructor
        targetMethodVisitor.loadThis();

        // 2nd argument: resolution context
        targetMethodVisitor.loadArg(0);

        // 3rd argument: bean context
        targetMethodVisitor.loadArg(1);

        // 4th argument: bean instance
        targetMethodVisitor.loadLocal(instanceLocalIndex);
        pushCastToType(targetMethodVisitor, beanType);
        targetMethodVisitor.visitMethodInsn(
                INVOKESPECIAL,
                postConstructInnerMethod.constructorInternalName,
                "<init>",
                constructorDescriptor,
                false
        );
        final int executableInstanceIndex = targetMethodVisitor.newLocal(Type.getType(ExecutableMethod.class));
        targetMethodVisitor.storeLocal(executableInstanceIndex);
        // now invoke MethodInterceptorChain.initialize or dispose
        // 1st argument: resolution context
        targetMethodVisitor.loadArg(0);
        // 2nd argument: bean context
        targetMethodVisitor.loadArg(1);
        // 3rd argument: this definition
        targetMethodVisitor.loadThis();
        // 4th argument: executable method instance
        targetMethodVisitor.loadLocal(executableInstanceIndex);
        // 5th argument: the bean instance
        targetMethodVisitor.loadLocal(instanceLocalIndex);
        pushCastToType(targetMethodVisitor, beanType);
        targetMethodVisitor.visitMethodInsn(
                INVOKESTATIC,
                "io/micronaut/aop/chain/MethodInterceptorChain",
                dispatchMethodName,
                METHOD_DESCRIPTOR_INTERCEPTED_LIFECYCLE,
                false
        );
        targetMethodVisitor.loadLocal(instanceLocalIndex);
    }

    @SuppressWarnings("MagicNumber")
    private void visitPreDestroyMethodDefinition(boolean intercepted) {
        if (preDestroyMethodVisitor == null) {
            interfaceTypes.add(DisposableBeanDefinition.class);

            // override the dispose method
            GeneratorAdapter preDestroyMethodVisitor;
            if (intercepted) {
                preDestroyMethodVisitor = newLifeCycleMethod("doDispose");
                final GeneratorAdapter disposeMethod = newLifeCycleMethod("dispose");
                disposeMethod.loadArg(2);
                int instanceLocalIndex = disposeMethod.newLocal(beanType);
                disposeMethod.storeLocal(instanceLocalIndex);

                writeInterceptedLifecycleMethod(
                        "doDispose",
                        "dispose",
                        disposeMethod,
                        instanceLocalIndex
                );
                disposeMethod.returnValue();


                this.interceptedDisposeMethod = disposeMethod;
            } else {
                preDestroyMethodVisitor = newLifeCycleMethod("dispose");
            }

            this.preDestroyMethodVisitor = preDestroyMethodVisitor;
            // The object being injected is argument 3 of the inject method
            preDestroyMethodVisitor.loadArg(2);
            // store it in a local variable
            preDestroyMethodVisitor.visitTypeInsn(CHECKCAST, beanType.getInternalName());
            preDestroyInstanceLocalVarIndex = preDestroyMethodVisitor.newLocal(beanType);
            preDestroyMethodVisitor.storeLocal(preDestroyInstanceLocalVarIndex);

            invokeSuperInjectMethod(preDestroyMethodVisitor, PRE_DESTROY_METHOD);
        }
    }

    private GeneratorAdapter newLifeCycleMethod(String methodName) {
        String desc = getMethodDescriptor(Object.class.getName(), BeanResolutionContext.class.getName(), BeanContext.class.getName(), Object.class.getName());
        return new GeneratorAdapter(classWriter.visitMethod(
                ACC_PUBLIC,
                methodName,
                desc,
                getMethodSignature(getTypeDescriptor(providedBeanClassName), getTypeDescriptor(BeanResolutionContext.class.getName()), getTypeDescriptor(BeanContext.class.getName()), getTypeDescriptor(providedBeanClassName)),
                null),
                ACC_PUBLIC,
                methodName,
                desc
        );
    }

    private void finalizeBuildMethod() {
        // if this is a provided bean then execute "get"
        if (!providedBeanClassName.equals(beanFullClassName)) {

            buildMethodVisitor.storeLocal(buildInstanceLocalVarIndex);
            buildMethodVisitor.loadLocal(buildInstanceLocalVarIndex);
            buildMethodVisitor.visitMethodInsn(INVOKEVIRTUAL,
                    beanType.getInternalName(),
                    "get",
                    Type.getMethodDescriptor(Type.getType(Object.class)),
                    false);
            pushCastToType(buildMethodVisitor, providedType);
            buildMethodVisitor.loadLocal(buildInstanceLocalVarIndex);
            pushBeanDefinitionMethodInvocation(buildMethodVisitor, "injectAnother");
            pushCastToType(buildMethodVisitor, providedType);
        }
    }

    @SuppressWarnings("MagicNumber")
    private void invokeSuperInjectMethod(GeneratorAdapter methodVisitor, Method methodToInvoke) {
        // load this
        methodVisitor.loadThis();
        // load BeanResolutionContext arg 1
        methodVisitor.loadArg(0);
        // load BeanContext arg 2
        methodVisitor.loadArg(1);
        // load object being inject arg 3
        methodVisitor.loadArg(2);
        pushInvokeMethodOnSuperClass(methodVisitor, methodToInvoke);
    }

    private void visitBuildFactoryMethodDefinition(
            ClassElement factoryClass,
            Element factoryMethod) {
        if (buildMethodVisitor == null) {
            ParameterElement[] parameters;

            if (factoryMethod instanceof MethodElement) {
                parameters = ((MethodElement) factoryMethod).getParameters();
            } else {
                parameters = new ParameterElement[0];
            }

            List<ParameterElement> parameterList = Arrays.asList(parameters);
            boolean isParametrized = isParametrized(parameters);
            boolean isIntercepted = isConstructorIntercepted(factoryMethod);
            Type factoryType = JavaModelUtils.getTypeReference(factoryClass);

            defineBuilderMethod(isParametrized);
            // load this

            GeneratorAdapter buildMethodVisitor = this.buildMethodVisitor;
            // for Factory beans first we need to lookup the the factory bean
            // before invoking the method to instantiate
            // the below code looks up the factory bean.

            // Load the BeanContext for the method call
            buildMethodVisitor.loadArg(1);
            pushCastToType(buildMethodVisitor, DefaultBeanContext.class);
            // load the first argument of the method (the BeanResolutionContext) to be passed to the method
            buildMethodVisitor.loadArg(0);
            // second argument is the bean type
            buildMethodVisitor.push(factoryType);
            buildMethodVisitor.invokeVirtual(
                    Type.getType(DefaultBeanContext.class),
                    org.objectweb.asm.commons.Method.getMethod(METHOD_GET_BEAN)
            );

            // store a reference to the bean being built at index 3
            int factoryVar = buildMethodVisitor.newLocal(JavaModelUtils.getTypeReference(factoryClass));
            buildMethodVisitor.storeLocal(factoryVar);
            buildMethodVisitor.loadLocal(factoryVar);
            pushCastToType(buildMethodVisitor, factoryClass);
            String methodDescriptor = getMethodDescriptorForReturnType(beanType, parameterList);
            if (isIntercepted) {
                int constructorIndex = initInterceptedConstructorWriter(
                        buildMethodVisitor,
                        parameterList,
                        new FactoryMethodDef(factoryType, factoryMethod, methodDescriptor, factoryVar)
                );
                // populate an Object[] of all constructor arguments
                final int parametersIndex = createParameterArray(parameterList, buildMethodVisitor);
                invokeConstructorChain(buildMethodVisitor, constructorIndex, parametersIndex, parameterList);
            } else {

                if (!parameterList.isEmpty()) {
                    pushConstructorArguments(buildMethodVisitor, parameters);
                }
                if (factoryMethod instanceof MethodElement) {
                    buildMethodVisitor.visitMethodInsn(INVOKEVIRTUAL,
                            factoryType.getInternalName(),
                            factoryMethod.getName(),
                            methodDescriptor, false);
                } else {
                    buildMethodVisitor.getField(
                            factoryType,
                            factoryMethod.getName(),
                            beanType
                    );
                }
            }


            this.buildInstanceLocalVarIndex = buildMethodVisitor.newLocal(beanType);
            buildMethodVisitor.storeLocal(buildInstanceLocalVarIndex);
            pushBeanDefinitionMethodInvocation(buildMethodVisitor, "injectBean");
            pushCastToType(buildMethodVisitor, beanType);
            buildMethodVisitor.storeLocal(buildInstanceLocalVarIndex);
            buildMethodVisitor.loadLocal(buildInstanceLocalVarIndex);
            initLifeCycleMethodsIfNecessary();
        }
    }

    private void visitBuildMethodDefinition(MethodElement constructor, boolean requiresReflection) {
        if (buildMethodVisitor == null) {
            boolean isIntercepted = isConstructorIntercepted(constructor);
            final ParameterElement[] parameterArray = constructor.getParameters();
            List<ParameterElement> parameters = Arrays.asList(parameterArray);
            boolean isParametrized = isParametrized(parameterArray);
            defineBuilderMethod(isParametrized);
            // load this

            GeneratorAdapter buildMethodVisitor = this.buildMethodVisitor;

            // if there is constructor interception present then we have to
            // build the parameters into an Object[] and build a constructor invocation
            if (isIntercepted) {
                final int constructorIndex = initInterceptedConstructorWriter(buildMethodVisitor, parameters, null);
                // populate an Object[] of all constructor arguments
                final int parametersIndex = createParameterArray(parameters, buildMethodVisitor);
                invokeConstructorChain(buildMethodVisitor, constructorIndex, parametersIndex, parameters);
            } else {
                if (constructor.isStatic()) {
                    pushConstructorArguments(buildMethodVisitor, parameterArray);
                    final String methodDescriptor = getMethodDescriptor(constructor.getReturnType(), parameters);
                    buildMethodVisitor.invokeStatic(
                            getTypeReference(constructor.getDeclaringType()),
                            new org.objectweb.asm.commons.Method(constructor.getName(), methodDescriptor)
                    );
                } else {
                    if (requiresReflection) {
                        final int parameterArrayLocalVarIndex = createParameterArray(parameters, buildMethodVisitor);
                        final int parameterTypeArrayLocalVarIndex = createParameterTypeArray(parameters, buildMethodVisitor);
                        buildMethodVisitor.push(beanType);
                        buildMethodVisitor.loadLocal(parameterTypeArrayLocalVarIndex);
                        buildMethodVisitor.loadLocal(parameterArrayLocalVarIndex);
                        buildMethodVisitor.invokeStatic(
                                Type.getType(InstantiationUtils.class),
                                org.objectweb.asm.commons.Method.getMethod(
                                        ReflectionUtils.getRequiredInternalMethod(
                                                InstantiationUtils.class,
                                                "instantiate",
                                                Class.class,
                                                Class[].class,
                                                Object[].class
                                        )
                                )
                        );
                        pushCastToType(buildMethodVisitor, beanType);
                    } else {
                        buildMethodVisitor.newInstance(beanType);
                        buildMethodVisitor.dup();
                        pushConstructorArguments(buildMethodVisitor, parameterArray);
                        String constructorDescriptor = getConstructorDescriptor(parameters);
                        buildMethodVisitor.invokeConstructor(beanType, new org.objectweb.asm.commons.Method("<init>", constructorDescriptor));
                    }
                }
            }

            this.buildInstanceLocalVarIndex = buildMethodVisitor.newLocal(beanType);
            buildMethodVisitor.storeLocal(buildInstanceLocalVarIndex);
            pushBeanDefinitionMethodInvocation(buildMethodVisitor, "injectBean");
            pushCastToType(buildMethodVisitor, beanType);
            buildMethodVisitor.storeLocal(buildInstanceLocalVarIndex);
            buildMethodVisitor.loadLocal(buildInstanceLocalVarIndex);
            initLifeCycleMethodsIfNecessary();
        }
    }

    private void initLifeCycleMethodsIfNecessary() {
        if (isInterceptedLifeCycleByType(this.annotationMetadata, "POST_CONSTRUCT")) {
            visitPostConstructMethodDefinition(true);
        }
        if (!superBeanDefinition && isInterceptedLifeCycleByType(this.annotationMetadata, "PRE_DESTROY")) {
            visitPreDestroyMethodDefinition(true);
        }
    }

    private void invokeConstructorChain(GeneratorAdapter generatorAdapter, int constructorLocalIndex, int parametersLocalIndex, List<ParameterElement> parameters) {
        // 1st argument: The resolution context
        generatorAdapter.loadArg(0);
        // 2nd argument: The bean context
        generatorAdapter.loadArg(1);
        // 3rd argument: The interceptors if present
        if (StringUtils.isNotEmpty(interceptedType)) {
            // interceptors will be last entry in parameter list for interceptors types
            generatorAdapter.loadLocal(parametersLocalIndex);
            // array index for last parameter
            generatorAdapter.push(parameters.size() - 1);
            generatorAdapter.arrayLoad(TYPE_OBJECT);
            pushCastToType(generatorAdapter, List.class);
        } else {
            // for non interceptor types we have to perform a lookup based on the binding
            generatorAdapter.visitInsn(ACONST_NULL);
        }
        // 4th argument: the bean definition
        generatorAdapter.loadThis();
        // 5th argument: The constructor
        generatorAdapter.loadLocal(constructorLocalIndex);
        // 6th argument:  load the Object[] for the parameters
        generatorAdapter.loadLocal(parametersLocalIndex);

        generatorAdapter.visitMethodInsn(
                INVOKESTATIC,
                "io/micronaut/aop/chain/ConstructorInterceptorChain",
                "instantiate",
                METHOD_DESCRIPTOR_CONSTRUCTOR_INSTANTIATE,
                false
        );
    }

    private int initInterceptedConstructorWriter(
            GeneratorAdapter buildMethodVisitor,
            List<ParameterElement> parameters,
            @Nullable FactoryMethodDef factoryMethodDef) {
        // write the constructor that is a subclass of AbstractConstructorInjectionPoint
        InnerClassDef constructorInjectionPointInnerClass = newInnerClass(AbstractConstructorInjectionPoint.class);
        final ClassWriter interceptedConstructorWriter = constructorInjectionPointInnerClass.innerClassWriter;
        org.objectweb.asm.commons.Method constructorMethod = org.objectweb.asm.commons.Method.getMethod(CONSTRUCTOR_ABSTRACT_CONSTRUCTOR_IP);
        GeneratorAdapter protectedConstructor;

        final boolean hasFactoryMethod = factoryMethodDef != null;
        final String interceptedConstructorDescriptor;
        final Type factoryType = hasFactoryMethod ? factoryMethodDef.factoryType : null;
        final String factoryFieldName = "$factory";
        if (hasFactoryMethod) {
            // for factory methods we have to store the factory instance in a field and modify the constructor pass the factory instance
            newFinalField(interceptedConstructorWriter, factoryType, factoryFieldName);

            interceptedConstructorDescriptor = getConstructorDescriptor(new Type[]{
                    TYPE_BEAN_DEFINITION,
                    factoryType
            });
            protectedConstructor = new GeneratorAdapter(
                    interceptedConstructorWriter.visitMethod(
                            ACC_PROTECTED, CONSTRUCTOR_NAME,
                            interceptedConstructorDescriptor,
                            null,
                            null
                    ),
                    ACC_PROTECTED,
                    CONSTRUCTOR_NAME,
                    interceptedConstructorDescriptor
            );
        } else {
            interceptedConstructorDescriptor = constructorMethod.getDescriptor();
            protectedConstructor = new GeneratorAdapter(
                    interceptedConstructorWriter.visitMethod(
                            ACC_PROTECTED, CONSTRUCTOR_NAME,
                            interceptedConstructorDescriptor,
                            null,
                            null
                    ),
                    ACC_PROTECTED,
                    CONSTRUCTOR_NAME,
                    interceptedConstructorDescriptor
            );

        }
        if (hasFactoryMethod) {
            protectedConstructor.loadThis();
            protectedConstructor.loadArg(1);
            protectedConstructor.putField(constructorInjectionPointInnerClass.innerClassType, factoryFieldName, factoryType);
        }
        protectedConstructor.loadThis();
        protectedConstructor.loadArg(0);
        protectedConstructor.invokeConstructor(Type.getType(AbstractConstructorInjectionPoint.class), constructorMethod);
        protectedConstructor.returnValue();
        protectedConstructor.visitMaxs(1, 1);
        protectedConstructor.visitEnd();

        // now we need to implement the invoke method to execute the actual instantiation
        final GeneratorAdapter invokeMethod = startPublicMethod(interceptedConstructorWriter, METHOD_INVOKE_CONSTRUCTOR);
        if (hasFactoryMethod) {
            invokeMethod.loadThis();
            invokeMethod.getField(
                    constructorInjectionPointInnerClass.innerClassType,
                    factoryFieldName,
                    factoryType
            );
            pushCastToType(invokeMethod, factoryType);
        } else {
            invokeMethod.visitTypeInsn(NEW, beanType.getInternalName());
            invokeMethod.visitInsn(DUP);
        }
        for (int i = 0; i < parameters.size(); i++) {
            invokeMethod.loadArg(0);
            invokeMethod.push(i);
            invokeMethod.arrayLoad(TYPE_OBJECT);
            pushCastToType(invokeMethod, parameters.get(i));
        }

        if (hasFactoryMethod) {
            if (factoryMethodDef.factoryMethod instanceof MethodElement) {

                invokeMethod.visitMethodInsn(
                        INVOKEVIRTUAL,
                        factoryType.getInternalName(),
                        factoryMethodDef.factoryMethod.getName(),
                        factoryMethodDef.methodDescriptor,
                        false
                );
            } else {
                invokeMethod.getField(factoryType, factoryMethodDef.factoryMethod.getName(), beanType);
            }
        } else {
            String constructorDescriptor = getConstructorDescriptor(parameters);
            invokeMethod.visitMethodInsn(INVOKESPECIAL, beanType.getInternalName(), "<init>", constructorDescriptor, false);
        }
        invokeMethod.returnValue();
        invokeMethod.visitMaxs(1, 1);
        invokeMethod.visitEnd();

        // instantiate a new instance and return
        buildMethodVisitor.visitTypeInsn(NEW, constructorInjectionPointInnerClass.constructorInternalName);
        buildMethodVisitor.visitInsn(DUP);
        // pass outer class instance to constructor
        buildMethodVisitor.loadThis();

        if (hasFactoryMethod) {
            buildMethodVisitor.loadLocal(factoryMethodDef.factoryVar);
            pushCastToType(buildMethodVisitor, factoryType);
        }

        buildMethodVisitor.visitMethodInsn(
                INVOKESPECIAL,
                constructorInjectionPointInnerClass.constructorInternalName,
                "<init>",
                interceptedConstructorDescriptor,
                false
        );

        final int constructorIndex = buildMethodVisitor.newLocal(Type.getType(AbstractConstructorInjectionPoint.class));
        buildMethodVisitor.storeLocal(constructorIndex);
        return constructorIndex;
    }

    private void newFinalField(ClassWriter classWriter, Type fieldType, String fieldName) {
        classWriter
                .visitField(ACC_PRIVATE | ACC_FINAL,
                        fieldName,
                        fieldType.getDescriptor(),
                        null,
                        null
                );
    }

    private InnerClassDef newInnerClass(Class<?> superType) {
        ClassWriter interceptedConstructorWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        String interceptedConstructorWriterName = newInnerClassName();
        this.innerClasses.put(interceptedConstructorWriterName, interceptedConstructorWriter);
        final String constructorInternalName = getInternalName(interceptedConstructorWriterName);
        final Type interceptedConstructorType = getTypeReferenceForName(interceptedConstructorWriterName);
        interceptedConstructorWriter.visit(V1_8, ACC_SYNTHETIC | ACC_FINAL | ACC_PRIVATE,
                constructorInternalName,
                null,
                Type.getInternalName(superType),
                null
        );

        interceptedConstructorWriter.visitAnnotation(TYPE_GENERATED.getDescriptor(), false);
        interceptedConstructorWriter.visitOuterClass(
                beanDefinitionInternalName,
                null,
                null
        );
        classWriter.visitInnerClass(constructorInternalName, beanDefinitionInternalName, null, ACC_PRIVATE);
        return new InnerClassDef(
                interceptedConstructorWriterName,
                interceptedConstructorWriter,
                constructorInternalName,
                interceptedConstructorType
        );
    }

    @NotNull
    private String newInnerClassName() {
        return this.beanDefinitionName + "$" + ++innerClassIndex;
    }

    private int createParameterTypeArray(List<ParameterElement> parameters, GeneratorAdapter buildMethodVisitor) {
        final int pLen = parameters.size();
        pushNewArray(buildMethodVisitor, Class.class, pLen);
        for (int i = 0; i < pLen; i++) {
            final ParameterElement parameter = parameters.get(i);
            pushStoreInArray(buildMethodVisitor, i, pLen, () ->
                    buildMethodVisitor.push(getTypeReference(parameter))
            );
        }
        int local = buildMethodVisitor.newLocal(Type.getType(Object[].class));
        buildMethodVisitor.storeLocal(local);
        return local;
    }

    private int createParameterArray(List<ParameterElement> parameters, GeneratorAdapter buildMethodVisitor) {
        final int pLen = parameters.size();
        pushNewArray(buildMethodVisitor, Object.class, pLen);
        for (int i = 0; i < pLen; i++) {
            final ParameterElement parameter = parameters.get(i);
            int parameterIndex = i;
            pushStoreInArray(buildMethodVisitor, i, pLen, () ->
                    pushConstructorArgument(
                            buildMethodVisitor,
                            parameter.getName(),
                            parameter,
                            parameter.getAnnotationMetadata(),
                            parameterIndex
                    )
            );
        }
        int local = buildMethodVisitor.newLocal(Type.getType(Object[].class));
        buildMethodVisitor.storeLocal(local);
        return local;
    }

    private boolean isConstructorIntercepted(Element constructor) {
        // a constructor is intercepted when this bean is an advised type but not proxied
        // and any AROUND_CONSTRUCT annotations are present
        AnnotationMetadataHierarchy annotationMetadata = new AnnotationMetadataHierarchy(this.annotationMetadata, constructor.getAnnotationMetadata());
        final String interceptType = "AROUND_CONSTRUCT";
        // for beans that are @Around(proxyTarget=true) only the constructor of the proxy target should be intercepted. Beans returned from factories are always proxyTarget=true

        return isInterceptedLifeCycleByType(annotationMetadata, interceptType);
    }

    private boolean isInterceptedLifeCycleByType(AnnotationMetadata annotationMetadata, String interceptType) {
        return isLifeCycleCache.computeIfAbsent(interceptType, s -> {
            if (this.beanTypeElement.isAssignable("io.micronaut.aop.Interceptor")) {
                // interceptor beans cannot have lifecycle methods intercepted
                return false;
            }
            final Element originatingElement = getOriginatingElements()[0];
            final boolean isFactoryMethod = (originatingElement instanceof MethodElement && !(originatingElement instanceof ConstructorElement));
            final boolean isProxyTarget = annotationMetadata.booleanValue(AnnotationUtil.ANN_AROUND, "proxyTarget").orElse(false) || isFactoryMethod;
            // for beans that are @Around(proxyTarget=false) only the generated AOP impl should be intercepted
            final boolean isAopType = StringUtils.isNotEmpty(interceptedType);
            final boolean isConstructorInterceptionCandidate = (isProxyTarget && !isAopType) || (isAopType && !isProxyTarget);
            final boolean hasAroundConstruct;
            final AnnotationValue<Annotation> interceptorBindings
                    = annotationMetadata.getAnnotation(AnnotationUtil.ANN_INTERCEPTOR_BINDINGS);
            final List<AnnotationValue<Annotation>> interceptorBindingAnnotations;
            if (interceptorBindings != null) {
                interceptorBindingAnnotations = interceptorBindings.getAnnotations(AnnotationMetadata.VALUE_MEMBER);
                hasAroundConstruct = interceptorBindingAnnotations
                        .stream()
                        .anyMatch(av -> av.stringValue("kind").map(k -> k.equals(interceptType)).orElse(false));
            } else {
                interceptorBindingAnnotations = Collections.emptyList();
                hasAroundConstruct = false;
            }

            if (isConstructorInterceptionCandidate) {
                return hasAroundConstruct;
            } else if (hasAroundConstruct) {
                // if no other AOP advice is applied
                return interceptorBindingAnnotations
                        .stream()
                        .noneMatch(av -> av.stringValue("kind").map(k -> k.equals("AROUND")).orElse(false));
            } else {
                return false;
            }
        });
    }

    private void pushConstructorArguments(GeneratorAdapter buildMethodVisitor,
                                          ParameterElement[] parameters) {
        int size = parameters.length;
        if (size > 0) {
            for (int i = 0; i < parameters.length; i++) {
                ParameterElement parameter = parameters[i];
                pushConstructorArgument(buildMethodVisitor, parameter.getName(), parameter, parameter.getAnnotationMetadata(), i);
            }
        }
    }

    private void pushConstructorArgument(GeneratorAdapter buildMethodVisitor,
                                         String argumentName,
                                         ParameterElement argumentType,
                                         AnnotationMetadata annotationMetadata,
                                         int index) {
        if (isAnnotatedWithParameter(annotationMetadata) && isParametrized) {
            // load the args
            buildMethodVisitor.loadArg(3);
            // the argument name
            buildMethodVisitor.push(argumentName);
            buildMethodVisitor.invokeInterface(Type.getType(Map.class), org.objectweb.asm.commons.Method.getMethod(ReflectionUtils.getRequiredMethod(Map.class, "get", Object.class)));
            pushCastToType(buildMethodVisitor, argumentType);
        } else if (argumentType.getGenericType().isAssignable(BeanContext.class)) {
            buildMethodVisitor.loadArg(1);
        } else if (argumentType.getGenericType().isAssignable(BeanResolutionContext.class)) {
            buildMethodVisitor.loadArg(0);
        } else {
            boolean isArray = false;
            boolean hasGenericType = false;
            Method methodToInvoke;
            if (isValueType(annotationMetadata)) {
                methodToInvoke = GET_VALUE_FOR_CONSTRUCTOR_ARGUMENT;
            } else {
                final ClassElement genericType = argumentType.getGenericType();
                isArray = genericType.isArray();
                if (genericType.isAssignable(Collection.class) || isArray) {
                    hasGenericType = true;
                    ClassElement typeArgument = genericType.isArray() ? genericType.fromArray() : genericType.getFirstTypeArgument().orElse(null);
                    if (typeArgument != null) {
                        if (typeArgument.isAssignable(BeanRegistration.class)) {
                            methodToInvoke = GET_BEAN_REGISTRATIONS_FOR_CONSTRUCTOR_ARGUMENT;
                        } else {
                            methodToInvoke = GET_BEANS_OF_TYPE_FOR_CONSTRUCTOR_ARGUMENT;
                        }
                    } else {
                        methodToInvoke = GET_BEAN_FOR_CONSTRUCTOR_ARGUMENT;
                        hasGenericType = false;
                    }
                } else if (genericType.isAssignable(Stream.class)) {
                    hasGenericType = true;
                    methodToInvoke = GET_STREAM_OF_TYPE_FOR_CONSTRUCTOR_ARGUMENT;
                } else if (genericType.isAssignable(Optional.class)) {
                    hasGenericType = true;
                    methodToInvoke = FIND_BEAN_FOR_CONSTRUCTOR_ARGUMENT;
                } else if (genericType.isAssignable(BeanRegistration.class)) {
                    hasGenericType = true;
                    methodToInvoke = GET_BEAN_REGISTRATION_FOR_CONSTRUCTOR_ARGUMENT;
                } else {
                    methodToInvoke = GET_BEAN_FOR_CONSTRUCTOR_ARGUMENT;
                }
            }
            // Load this for method call
            buildMethodVisitor.loadThis();
            // load the first two arguments of the method (the BeanResolutionContext and the BeanContext) to be passed to the method
            buildMethodVisitor.loadArg(0);
            buildMethodVisitor.loadArg(1);
            // pass the index of the method as the third argument
            buildMethodVisitor.push(index);
            if (hasGenericType) {
                resolveConstructorArgumentGenericType(buildMethodVisitor, argumentType.getGenericType(), index);
            }
            // push qualifier
            pushQualifier(buildMethodVisitor, argumentType, () -> {
                resolveConstructorArgument(buildMethodVisitor, index);
            });
            // invoke method
            pushInvokeMethodOnSuperClass(buildMethodVisitor, methodToInvoke);
            if (isArray) {
                convertToArray(argumentType.getGenericType().fromArray(), buildMethodVisitor);
            }
            pushCastToType(buildMethodVisitor, argumentType);
        }
    }

    private void resolveConstructorArgumentGenericType(GeneratorAdapter visitor, ClassElement type, int argumentIndex) {
        if (!resolveArgumentGenericType(visitor, type)) {
            resolveConstructorArgument(visitor, argumentIndex);
            resolveFirstTypeArgument(visitor);
            resolveInnerTypeArgumentIfNeeded(visitor, type);
        }
    }

    private void resolveConstructorArgument(GeneratorAdapter visitor, int argumentIndex) {
        Type constructorField = Type.getType(AbstractInitializableBeanDefinition.MethodOrFieldReference.class);
        Type methodRefType = Type.getType(AbstractInitializableBeanDefinition.MethodReference.class);
        visitor.getStatic(beanDefinitionType, FIELD_CONSTRUCTOR, constructorField);
        pushCastToType(visitor, methodRefType);
        visitor.getField(methodRefType, "arguments", Type.getType(Argument[].class));
        visitor.push(argumentIndex);
        visitor.arrayLoad(Type.getType(Argument.class));
    }

    private void resolveMethodArgumentGenericType(GeneratorAdapter visitor, ClassElement type, int methodIndex, int argumentIndex) {
        if (!resolveArgumentGenericType(visitor, type)) {
            resolveMethodArgument(visitor, methodIndex, argumentIndex);
            resolveFirstTypeArgument(visitor);
            resolveInnerTypeArgumentIfNeeded(visitor, type);
        }
    }

    private void resolveMethodArgument(GeneratorAdapter visitor, int methodIndex, int argumentIndex) {
        Type methodsRef = Type.getType(AbstractInitializableBeanDefinition.MethodReference[].class);
        Type methodRefType = Type.getType(AbstractInitializableBeanDefinition.MethodReference.class);
        visitor.getStatic(beanDefinitionType, FIELD_INJECTION_METHODS, methodsRef);
        visitor.push(methodIndex);
        visitor.arrayLoad(methodsRef);
        visitor.getField(methodRefType, "arguments", Type.getType(Argument[].class));
        visitor.push(argumentIndex);
        visitor.arrayLoad(Type.getType(Argument.class));
    }

    private void resolveFieldArgumentGenericType(GeneratorAdapter visitor, ClassElement type, int fieldIndex) {
        if (!resolveArgumentGenericType(visitor, type)) {
            resolveFieldArgument(visitor, fieldIndex);
            resolveFirstTypeArgument(visitor);
            resolveInnerTypeArgumentIfNeeded(visitor, type);
        }
    }

    private void resolveFieldArgument(GeneratorAdapter visitor, int fieldIndex) {
        visitor.getStatic(beanDefinitionType, FIELD_INJECTION_FIELDS, Type.getType(AbstractInitializableBeanDefinition.FieldReference[].class));
        visitor.push(fieldIndex);
        visitor.arrayLoad(Type.getType(AbstractInitializableBeanDefinition.FieldReference.class));
        visitor.getField(Type.getType(AbstractInitializableBeanDefinition.FieldReference.class), "argument", Type.getType(Argument.class));
    }

    private boolean resolveArgumentGenericType(GeneratorAdapter visitor, ClassElement type) {
        if (type.isArray()) {
            if (!type.getTypeArguments().isEmpty() && isInternalGenericTypeContainer(type.fromArray())) {
                // skip for arrays of BeanRegistration
                return false;
            }
            visitor.push(JavaModelUtils.getTypeReference(type.fromArray()));
            visitor.push((String) null);
            invokeInterfaceStaticMethod(
                    visitor,
                    Argument.class,
                    METHOD_CREATE_ARGUMENT_SIMPLE
            );
            return true;
        } else if (type.getTypeArguments().isEmpty()) {
            visitor.visitInsn(ACONST_NULL);
            return true;
        }
        return false;
    }

    private void resolveInnerTypeArgumentIfNeeded(GeneratorAdapter visitor, ClassElement type) {
        if (isInternalGenericTypeContainer(type.getFirstTypeArgument().get())) {
            resolveFirstTypeArgument(visitor);
        }
    }

    private boolean isInternalGenericTypeContainer(ClassElement type) {
        return type.isAssignable(BeanRegistration.class);
    }

    private void resolveFirstTypeArgument(GeneratorAdapter visitor) {
        visitor.invokeInterface(Type.getType(TypeVariableResolver.class),
                org.objectweb.asm.commons.Method.getMethod(ReflectionUtils.findMethod(TypeVariableResolver.class, "getTypeParameters").get()));
        visitor.push(0);
        visitor.arrayLoad(Type.getType(Argument.class));
    }

    private boolean isValueType(AnnotationMetadata annotationMetadata) {
        if (annotationMetadata != null) {
            return annotationMetadata.hasDeclaredStereotype(Value.class) || annotationMetadata.hasDeclaredStereotype(Property.class);
        }
        return false;
    }

    private boolean isAnnotatedWithParameter(AnnotationMetadata annotationMetadata) {
        if (annotationMetadata != null) {
            return annotationMetadata.hasDeclaredAnnotation(Parameter.class);
        }
        return false;
    }

    private boolean isParametrized(ParameterElement... parameters) {
        return Arrays.stream(parameters).anyMatch(p -> isAnnotatedWithParameter(p.getAnnotationMetadata()));
    }

    private void defineBuilderMethod(boolean isParametrized) {
        if (isParametrized) {
            this.isParametrized = true;
        }

        String methodDescriptor;
        String methodSignature;
        if (isParametrized) {
            methodDescriptor = getMethodDescriptor(
                    Object.class.getName(),
                    BeanResolutionContext.class.getName(),
                    BeanContext.class.getName(),
                    BeanDefinition.class.getName(),
                    Map.class.getName()
            );
            methodSignature = getMethodSignature(
                    getTypeDescriptor(providedBeanClassName),
                    getTypeDescriptor(BeanResolutionContext.class.getName()),
                    getTypeDescriptor(BeanContext.class.getName()),
                    getTypeDescriptor(BeanDefinition.class.getName(),
                            providedBeanClassName),
                    getTypeDescriptor(Map.class.getName())
            );
        } else {
            methodDescriptor = getMethodDescriptor(
                    Object.class.getName(),
                    BeanResolutionContext.class.getName(),
                    BeanContext.class.getName(),
                    BeanDefinition.class.getName()
            );
            methodSignature = getMethodSignature(
                    getTypeDescriptor(providedBeanClassName),
                    getTypeDescriptor(BeanResolutionContext.class.getName()),
                    getTypeDescriptor(BeanContext.class.getName()),
                    getTypeDescriptor(BeanDefinition.class.getName(),
                            providedBeanClassName)
            );
        }

        String methodName = isParametrized ? "doBuild" : "build";
        this.buildMethodVisitor = new GeneratorAdapter(classWriter.visitMethod(
                ACC_PUBLIC,
                methodName,
                methodDescriptor,
                methodSignature,
                null), ACC_PUBLIC, methodName, methodDescriptor);
    }

    private void pushBeanDefinitionMethodInvocation(GeneratorAdapter buildMethodVisitor, String methodName) {
        buildMethodVisitor.loadThis();
        buildMethodVisitor.loadArg(0);
        buildMethodVisitor.loadArg(1);
        buildMethodVisitor.loadLocal(buildInstanceLocalVarIndex);

        buildMethodVisitor.visitMethodInsn(INVOKEVIRTUAL,
                superBeanDefinition ? superType.getInternalName() : beanDefinitionInternalName,
                methodName,
                METHOD_DESCRIPTOR_INITIALIZE,
                false);
    }

    private void visitBeanDefinitionConstructorInternal(
            GeneratorAdapter staticInit, Object constructor,
            boolean requiresReflection) {

        if (constructor instanceof MethodElement) {
            MethodElement methodElement = (MethodElement) constructor;
            AnnotationMetadata constructorMetadata = methodElement.getAnnotationMetadata();
            DefaultAnnotationMetadata.contributeDefaults(this.annotationMetadata, constructorMetadata);
            ParameterElement[] parameters = methodElement.getParameters();
            List<ParameterElement> parameterList = Arrays.asList(parameters);
            applyDefaultNamedToParameters(parameterList);

            pushNewMethodReference(staticInit, JavaModelUtils.getTypeReference(methodElement.getDeclaringType()), methodElement, requiresReflection, false, false);
        } else if (constructor instanceof FieldElement) {
            FieldElement fieldConstructor = (FieldElement) constructor;
            pushNewFieldReference(staticInit, JavaModelUtils.getTypeReference(fieldConstructor.getDeclaringType()), fieldConstructor, constructorRequiresReflection);
        } else {
            throw new IllegalArgumentException("Unexpected constructor: " + constructor);
        }

        staticInit.putStatic(beanDefinitionType, FIELD_CONSTRUCTOR, Type.getType(AbstractInitializableBeanDefinition.MethodOrFieldReference.class));

        GeneratorAdapter publicConstructor = new GeneratorAdapter(
                classWriter.visitMethod(ACC_PUBLIC, CONSTRUCTOR_NAME, DESCRIPTOR_DEFAULT_CONSTRUCTOR, null, null),
                ACC_PUBLIC,
                CONSTRUCTOR_NAME,
                DESCRIPTOR_DEFAULT_CONSTRUCTOR
        );
        publicConstructor.loadThis();
        publicConstructor.push(beanType);
        publicConstructor.getStatic(beanDefinitionType, FIELD_CONSTRUCTOR, Type.getType(AbstractInitializableBeanDefinition.MethodOrFieldReference.class));
        publicConstructor.invokeConstructor(superBeanDefinition ? superType : beanDefinitionType, PROTECTED_ABSTRACT_BEAN_DEFINITION_CONSTRUCTOR);
        publicConstructor.visitInsn(RETURN);
        publicConstructor.visitMaxs(5, 1);
        publicConstructor.visitEnd();

        // Call protected super constructor if definition is extending another one

        if (!superBeanDefinition) {
            // create protected constructor for subclasses of AbstractBeanDefinition
            GeneratorAdapter protectedConstructor = new GeneratorAdapter(
                    classWriter.visitMethod(ACC_PROTECTED,
                            PROTECTED_ABSTRACT_BEAN_DEFINITION_CONSTRUCTOR.getName(),
                            PROTECTED_ABSTRACT_BEAN_DEFINITION_CONSTRUCTOR.getDescriptor(), null, null),
                    ACC_PROTECTED,
                    PROTECTED_ABSTRACT_BEAN_DEFINITION_CONSTRUCTOR.getName(),
                    PROTECTED_ABSTRACT_BEAN_DEFINITION_CONSTRUCTOR.getDescriptor()
            );

            AnnotationMetadata annotationMetadata = this.annotationMetadata != null ? this.annotationMetadata : AnnotationMetadata.EMPTY_METADATA;

            protectedConstructor.loadThis();
            // 1: beanType
            protectedConstructor.loadArg(0);
            // 2: `AbstractBeanDefinition2.MethodOrFieldReference.class` constructor
            protectedConstructor.loadArg(1);
            // 3: annotationMetadata
            if (this.annotationMetadata == null) {
                protectedConstructor.push((String) null);
            } else {
                protectedConstructor.getStatic(getTypeReferenceForName(getBeanDefinitionReferenceClassName()), AbstractAnnotationMetadataWriter.FIELD_ANNOTATION_METADATA, Type.getType(AnnotationMetadata.class));
            }
            // 4: `AbstractBeanDefinition2.MethodReference[].class` methodInjection
            if (methodInjectionPoints.isEmpty() && preDestroyMethodVisits.isEmpty() && postConstructMethodVisits.isEmpty()) {
                protectedConstructor.push((String) null);
            } else {
                protectedConstructor.getStatic(beanDefinitionType, FIELD_INJECTION_METHODS, Type.getType(AbstractInitializableBeanDefinition.MethodReference[].class));
            }
            // 5: `AbstractBeanDefinition2.FieldReference[].class` fieldInjection
            if (fieldInjectionPoints.isEmpty()) {
                protectedConstructor.push((String) null);
            } else {
                protectedConstructor.getStatic(beanDefinitionType, FIELD_INJECTION_FIELDS, Type.getType(AbstractInitializableBeanDefinition.FieldReference[].class));
            }
            // 6: `ExecutableMethod[]` executableMethods
            if (executableMethodsDefinitionWriter == null) {
                protectedConstructor.push((String) null);
            } else {
                protectedConstructor.newInstance(executableMethodsDefinitionWriter.getClassType());
                protectedConstructor.dup();
                protectedConstructor.invokeConstructor(executableMethodsDefinitionWriter.getClassType(), METHOD_DEFAULT_CONSTRUCTOR);
            }
            // 7: `Map<String, Argument<?>[]>` typeArgumentsMap
            if (!hasTypeArguments()) {
                protectedConstructor.push((String) null);
            } else {
                protectedConstructor.getStatic(beanDefinitionType, FIELD_TYPE_ARGUMENTS, Type.getType(Map.class));
            }
            // 8: `Optional` scope
            String scope = annotationMetadata.getAnnotationNameByStereotype(AnnotationUtil.SCOPE).orElse(null);
            if (scope != null) {
                protectedConstructor.push(scope);
                protectedConstructor.invokeStatic(
                        TYPE_OPTIONAL,
                        METHOD_OPTIONAL_OF
                );
            } else {
                protectedConstructor.invokeStatic(TYPE_OPTIONAL, METHOD_OPTIONAL_EMPTY);
            }

            // 9: `boolean` isAbstract
            protectedConstructor.push(isAbstract);
            // 10: `boolean` isProvided
            protectedConstructor.push(
                    annotationMetadata.hasDeclaredStereotype(Provided.class)
            );
            // 11: `boolean` isIterable
            protectedConstructor.push(isIterable(annotationMetadata));
            // 12: `boolean` isSingleton
            protectedConstructor.push(
                    isSingleton(scope)
            );
            // 13: `boolean` isPrimary
            protectedConstructor.push(
                    annotationMetadata.hasDeclaredStereotype(Primary.class)
            );
            // 14: `boolean` isConfigurationProperties
            protectedConstructor.push(isConfigurationProperties);
            // 15: isContainerType
            protectedConstructor.push(isContainerType());
            // 16: requiresMethodProcessing
            protectedConstructor.push(preprocessMethods);

            protectedConstructor.invokeConstructor(
                    isSuperFactory ? TYPE_ABSTRACT_BEAN_DEFINITION : superType,
                    BEAN_DEFINITION_CLASS_CONSTRUCTOR
            );

            protectedConstructor.visitInsn(RETURN);
            protectedConstructor.visitMaxs(20, 1);
            protectedConstructor.visitEnd();
        }
    }

    private boolean isContainerType() {
        return DefaultArgument.CONTAINER_TYPES.stream().map(Class::getName).anyMatch(c -> c.equals(beanFullClassName));
    }

    private boolean isConfigurationProperties(AnnotationMetadata annotationMetadata) {
        return isIterable(annotationMetadata) || annotationMetadata.hasStereotype(ConfigurationReader.class);
    }

    private boolean isIterable(AnnotationMetadata annotationMetadata) {
        return annotationMetadata.hasDeclaredStereotype(EachProperty.class) || annotationMetadata.hasDeclaredStereotype(EachBean.class);
    }

    private void pushNewMethodReference(GeneratorAdapter staticInit, Type beanType, MethodElement methodElement,
                                        boolean requiresReflection,
                                        boolean isPostConstructMethod,
                                        boolean isPreDestroyMethod) {
        for (ParameterElement value : methodElement.getParameters()) {
            DefaultAnnotationMetadata.contributeDefaults(this.annotationMetadata, value.getAnnotationMetadata());
        }
        staticInit.newInstance(Type.getType(AbstractInitializableBeanDefinition.MethodReference.class));
        staticInit.dup();
        // 1: declaringType
        staticInit.push(beanType);
        // 2: methodName
        staticInit.push(methodElement.getName());
        // 3: arguments
        if (!methodElement.hasParameters()) {
            staticInit.visitInsn(ACONST_NULL);
        } else {
            pushBuildArgumentsForMethod(
                    beanFullClassName,
                    beanDefinitionType,
                    classWriter,
                    staticInit,
                    Arrays.asList(methodElement.getParameters()),
                    defaultsStorage,
                    loadTypeMethods
            );
        }
        // 4: annotationMetadata
        pushAnnotationMetadata(staticInit, methodElement.getAnnotationMetadata());
        // 5: requiresReflection
        staticInit.push(requiresReflection);
        if (isPreDestroyMethod || isPostConstructMethod) {
            // 6: isPostConstructMethod
            staticInit.push(isPostConstructMethod);
            // 7: isPreDestroyMethod
            staticInit.push(isPreDestroyMethod);
            staticInit.invokeConstructor(Type.getType(AbstractInitializableBeanDefinition.MethodReference.class), METHOD_REFERENCE_CONSTRUCTOR_POST_PRE);
        } else {
            staticInit.invokeConstructor(Type.getType(AbstractInitializableBeanDefinition.MethodReference.class), METHOD_REFERENCE_CONSTRUCTOR);
        }
    }

    private void pushNewFieldReference(GeneratorAdapter staticInit, Type declaringType, FieldElement fieldElement, boolean requiresReflection) {
        staticInit.newInstance(Type.getType(AbstractInitializableBeanDefinition.FieldReference.class));
        staticInit.dup();
        // 1: declaringType
        staticInit.push(declaringType);
        // 2: argument
        pushCreateArgument(
                beanFullClassName,
                beanDefinitionType,
                classWriter,
                staticInit,
                fieldElement.getName(),
                fieldElement.getGenericType(),
                fieldElement.getAnnotationMetadata(),
                fieldElement.getGenericType().getTypeArguments(),
                defaultsStorage,
                loadTypeMethods
        );
        // 3: requiresReflection
        staticInit.push(requiresReflection);
        staticInit.invokeConstructor(Type.getType(AbstractInitializableBeanDefinition.FieldReference.class), FIELD_REFERENCE_CONSTRUCTOR);
    }

    private void pushAnnotationMetadata(GeneratorAdapter staticInit, AnnotationMetadata annotationMetadata) {
        if (annotationMetadata == AnnotationMetadata.EMPTY_METADATA || annotationMetadata.isEmpty()) {
            staticInit.push((String) null);
        } else if (annotationMetadata instanceof AnnotationMetadataHierarchy) {
            AnnotationMetadataWriter.instantiateNewMetadataHierarchy(
                    beanDefinitionType,
                    classWriter,
                    staticInit,
                    (AnnotationMetadataHierarchy) annotationMetadata,
                    defaultsStorage,
                    loadTypeMethods);
        } else if (annotationMetadata instanceof DefaultAnnotationMetadata) {
            AnnotationMetadataWriter.instantiateNewMetadata(
                    beanDefinitionType,
                    classWriter,
                    staticInit,
                    (DefaultAnnotationMetadata) annotationMetadata,
                    defaultsStorage,
                    loadTypeMethods);
        } else {
            staticInit.push((String) null);
        }
    }

    private String generateBeanDefSig(String typeParameter) {
        SignatureVisitor sv = new SignatureWriter();
        visitSuperTypeParameters(sv, typeParameter);

        final String beanTypeInternalName = getInternalName(typeParameter);
        // visit BeanFactory interface
        for (Class<?> interfaceType : interfaceTypes) {
            String param;
            if (ProxyBeanDefinition.class == interfaceType || AdvisedBeanType.class == interfaceType) {
                param = getInterceptedType().map(Type::getInternalName).orElse(beanTypeInternalName);
            } else {
                param = beanTypeInternalName;
            }

            SignatureVisitor bfi = sv.visitInterface();
            bfi.visitClassType(Type.getInternalName(interfaceType));
            SignatureVisitor iisv = bfi.visitTypeArgument('=');
            iisv.visitClassType(param);
            iisv.visitEnd();
            bfi.visitEnd();
        }
        return sv.toString();
    }

    private void visitSuperTypeParameters(SignatureVisitor sv, String... typeParameters) {
        // visit super class
        SignatureVisitor psv = sv.visitSuperclass();
        psv.visitClassType(isSuperFactory ? TYPE_ABSTRACT_BEAN_DEFINITION.getInternalName() : superType.getInternalName());
        if (superType == TYPE_ABSTRACT_BEAN_DEFINITION || isSuperFactory) {
            for (String typeParameter : typeParameters) {

                SignatureVisitor ppsv = psv.visitTypeArgument('=');
                String beanTypeInternalName = getInternalName(typeParameter);
                ppsv.visitClassType(beanTypeInternalName);
                ppsv.visitEnd();
            }
        }

        psv.visitEnd();
    }

    private static Method getBeanLookupMethod(String methodName, boolean requiresGenericType) {
        if (requiresGenericType) {
            return ReflectionUtils.getRequiredInternalMethod(
                    AbstractInitializableBeanDefinition.class,
                    methodName,
                    BeanResolutionContext.class,
                    BeanContext.class,
                    int.class,
                    Argument.class,
                    Qualifier.class);
        } else {
            return ReflectionUtils.getRequiredInternalMethod(
                    AbstractInitializableBeanDefinition.class,
                    methodName,
                    BeanResolutionContext.class,
                    BeanContext.class,
                    int.class,
                    Qualifier.class
            );
        }
    }

    private static Method getBeanLookupMethodForArgument(String methodName, boolean requiresGenericType) {
        if (requiresGenericType) {
            return ReflectionUtils.getRequiredInternalMethod(
                    AbstractInitializableBeanDefinition.class,
                    methodName,
                    BeanResolutionContext.class,
                    BeanContext.class,
                    int.class,
                    int.class,
                    Argument.class,
                    Qualifier.class);
        }
        return ReflectionUtils.getRequiredInternalMethod(
                AbstractInitializableBeanDefinition.class,
                methodName,
                BeanResolutionContext.class,
                BeanContext.class,
                int.class,
                int.class,
                Qualifier.class);
    }

    @Override
    public String getName() {
        return beanDefinitionName;
    }

    @Override
    public boolean isProtected() {
        return false;
    }

    @Override
    public boolean isPublic() {
        return true;
    }

    @Override
    public Object getNativeType() {
        return this;
    }

    @Override
    public Collection<Element> getInjectionPoints() {
        if (fieldInjectionPoints.isEmpty() && methodInjectionPoints.isEmpty()) {
            return Collections.emptyList();
        } else {
            Collection<Element> injectionPoints = new ArrayList<>();
            for (FieldVisitData fieldInjectionPoint : fieldInjectionPoints) {
                injectionPoints.add(fieldInjectionPoint.fieldElement);
            }
            for (MethodVisitData methodInjectionPoint : methodInjectionPoints) {
                injectionPoints.add(methodInjectionPoint.methodElement);
            }
            return Collections.unmodifiableCollection(injectionPoints);
        }
    }

    @Override
    public boolean isAbstract() {
        return this.isAbstract;
    }

    @Override
    public <T extends Annotation> Element annotate(String annotationType, Consumer<AnnotationValueBuilder<T>> consumer) {
        this.beanProducingElement.annotate(annotationType, consumer);
        return this;
    }

    @Override
    public Element removeAnnotation(String annotationType) {
        this.beanProducingElement.removeAnnotation(annotationType);
        return this;
    }

    @Override
    public <T extends Annotation> Element removeAnnotationIf(Predicate<AnnotationValue<T>> predicate) {
        this.beanProducingElement.removeAnnotationIf(predicate);
        return this;
    }

    @Override
    public Element removeStereotype(String annotationType) {
        this.beanProducingElement.removeStereotype(annotationType);
        return this;
    }

    @Override
    public ClassElement getDeclaringClass() {
        final Element beanProducingElement = this.beanProducingElement;
        return getDeclaringType(beanProducingElement);
    }

    private ClassElement getDeclaringType(Element beanProducingElement) {
        if (beanProducingElement instanceof ClassElement) {
            return (ClassElement) beanProducingElement;
        } else if (beanProducingElement instanceof MemberElement) {
            return ((MemberElement) beanProducingElement).getDeclaringType();
        } else if (beanProducingElement instanceof BeanElementBuilder) {
            return ((BeanElementBuilder) beanProducingElement).getDeclaringElement();
        } else {
            return this.beanTypeElement;
        }
    }

    @Override
    public Element getProducingElement() {
        return beanProducingElement;
    }

    @Override
    public Set<ClassElement> getBeanTypes() {
        final String[] types = this.annotationMetadata.stringValues(Bean.class, "typed");
        if (ArrayUtils.isNotEmpty(types)) {
            HashSet<ClassElement> classElements = new HashSet<>();
            for (String type : types) {
                visitorContext.getClassElement(type).ifPresent(classElements::add);
            }
            return Collections.unmodifiableSet(classElements);
        } else {
            final Optional<ClassElement> superType = beanTypeElement.getSuperType();
            final Collection<ClassElement> interfaces = beanTypeElement.getInterfaces();
            if (superType.isPresent() || !interfaces.isEmpty()) {
                Set<ClassElement> beanTypes = new HashSet<>();
                beanTypes.add(beanTypeElement);
                populateBeanTypes(new HashSet<>(), beanTypes, superType.orElse(null), interfaces);
                return Collections.unmodifiableSet(beanTypes);
            } else {
                return Collections.singleton(beanTypeElement);
            }
        }
    }

    private void populateBeanTypes(Set<String> processedTypes, Set<ClassElement> beanTypes, ClassElement superType, Collection<ClassElement> interfaces) {
        for (ClassElement anInterface : interfaces) {
            final String n = anInterface.getName();
            if (!processedTypes.contains(n)) {
                processedTypes.add(n);
                beanTypes.add(anInterface);
                populateBeanTypes(processedTypes, beanTypes, null, anInterface.getInterfaces());
            }
        }
        if (superType != null) {
            final String n = superType.getName();
            if (!processedTypes.contains(n)) {
                processedTypes.add(n);
                beanTypes.add(superType);
                final ClassElement next = superType.getSuperType().orElse(null);
                populateBeanTypes(processedTypes, beanTypes, next, superType.getInterfaces());
            }
        }
    }

    @Override
    public Optional<String> getScope() {
        return annotationMetadata.getAnnotationNameByStereotype(AnnotationUtil.SCOPE);
    }

    @Override
    public Collection<String> getQualifiers() {
        return Collections.unmodifiableList(annotationMetadata.getAnnotationNamesByStereotype(AnnotationUtil.QUALIFIER));
    }

    @Override
    public BeanElementBuilder addAssociatedBean(ClassElement type) {
        if (visitorContext instanceof BeanElementVisitorContext) {
            final Element[] originatingElements = getOriginatingElements();
            return ((BeanElementVisitorContext) this.visitorContext)
                        .addAssociatedBean(originatingElements[0], type);
        }
        return BeanElement.super.addAssociatedBean(type);
    }

    @Override
    public Element[] getOriginatingElements() {
        return this.originatingElements.getOriginatingElements();
    }

    @Internal
    private static final class FieldVisitData {
        final TypedElement beanType;
        final FieldElement fieldElement;
        final boolean requiresReflection;

        FieldVisitData(
                TypedElement beanType,
                FieldElement fieldElement,
                boolean requiresReflection) {
            this.beanType = beanType;
            this.fieldElement = fieldElement;
            this.requiresReflection = requiresReflection;
        }

    }

    /**
     * Data used when visiting method.
     */
    @Internal
    public static final class MethodVisitData {
        private final TypedElement beanType;
        private final boolean requiresReflection;
        private final MethodElement methodElement;

        /**
         * Default constructor.
         *
         * @param beanType           The declaring type
         * @param methodElement      The method element
         * @param requiresReflection Whether reflection is required
         */
        MethodVisitData(
                TypedElement beanType,
                MethodElement methodElement,
                boolean requiresReflection) {
            this.beanType = beanType;
            this.requiresReflection = requiresReflection;
            this.methodElement = methodElement;
        }

        /**
         * @return The method element
         */
        public MethodElement getMethodElement() {
            return methodElement;
        }

        /**
         * @return The declaring type object.
         */
        public TypedElement getBeanType() {
            return beanType;
        }

        /**
         * @return is reflection required
         */
        public boolean isRequiresReflection() {
            return requiresReflection;
        }
    }

    private class FactoryMethodDef {
        private final Type factoryType;
        private final Element factoryMethod;
        private final String methodDescriptor;
        private final int factoryVar;

        public FactoryMethodDef(Type factoryType, Element factoryMethod, String methodDescriptor, int factoryVar) {
            this.factoryType = factoryType;
            this.factoryMethod = factoryMethod;
            this.methodDescriptor = methodDescriptor;
            this.factoryVar = factoryVar;
        }
    }

    private class InnerClassDef {
        private final ClassWriter innerClassWriter;
        private final String constructorInternalName;
        private final Type innerClassType;
        private final String innerClassName;

        public InnerClassDef(String interceptedConstructorWriterName, ClassWriter innerClassWriter, String constructorInternalName, Type innerClassType) {
            this.innerClassName = interceptedConstructorWriterName;
            this.innerClassWriter = innerClassWriter;
            this.constructorInternalName = constructorInternalName;
            this.innerClassType = innerClassType;
        }
    }
}
