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
package io.micronaut.aop.writer;

import io.micronaut.aop.HotSwappableInterceptedProxy;
import io.micronaut.aop.Intercepted;
import io.micronaut.aop.InterceptedProxy;
import io.micronaut.aop.Interceptor;
import io.micronaut.aop.InterceptorKind;
import io.micronaut.aop.InterceptorRegistry;
import io.micronaut.aop.Introduced;
import io.micronaut.aop.chain.InterceptorChain;
import io.micronaut.aop.chain.MethodInterceptorChain;
import io.micronaut.aop.internal.intercepted.InterceptedMethodUtil;
import io.micronaut.context.BeanContext;
import io.micronaut.context.BeanDefinitionRegistry;
import io.micronaut.context.BeanLocator;
import io.micronaut.context.BeanRegistration;
import io.micronaut.context.BeanResolutionContext;
import io.micronaut.context.DefaultBeanContext;
import io.micronaut.context.Qualifier;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationUtil;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.reflect.ReflectionUtils;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.core.util.Toggleable;
import io.micronaut.core.value.OptionalValues;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.inject.ProxyBeanDefinition;
import io.micronaut.inject.annotation.AnnotationMetadataReference;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.Element;
import io.micronaut.inject.ast.ElementQuery;
import io.micronaut.inject.ast.FieldElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.ParameterElement;
import io.micronaut.inject.ast.TypedElement;
import io.micronaut.inject.configuration.ConfigurationMetadataBuilder;
import io.micronaut.inject.processing.JavaModelUtils;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.inject.writer.AbstractClassFileWriter;
import io.micronaut.inject.writer.BeanDefinitionWriter;
import io.micronaut.inject.writer.ClassWriterOutputVisitor;
import io.micronaut.inject.writer.ExecutableMethodsDefinitionWriter;
import io.micronaut.inject.writer.OriginatingElements;
import io.micronaut.inject.writer.ProxyingBeanDefinitionVisitor;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

import static io.micronaut.core.annotation.AnnotationUtil.ZERO_ANNOTATION_VALUES;
import static io.micronaut.inject.ast.ParameterElement.ZERO_PARAMETER_ELEMENTS;

/**
 * A class that generates AOP proxy classes at compile time.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Internal
public class AopProxyWriter extends AbstractClassFileWriter implements ProxyingBeanDefinitionVisitor, Toggleable {

    public static final int ADDITIONAL_PARAMETERS_COUNT = 5;

    private static final int MAX_LOCALS = 3;

    private static final Method METHOD_GET_PROXY_TARGET_BEAN_WITH_BEAN_DEFINITION_AND_CONTEXT = Method.getMethod(ReflectionUtils.getRequiredInternalMethod(
        DefaultBeanContext.class,
        "getProxyTargetBean",
        BeanResolutionContext.class,
        BeanDefinition.class,
        Argument.class,
        Qualifier.class
    ));

    private static final Method METHOD_GET_PROXY_BEAN_DEFINITION = Method.getMethod(ReflectionUtils.getRequiredInternalMethod(
        BeanDefinitionRegistry.class,
        "getProxyTargetBeanDefinition",
        Argument.class,
        Qualifier.class
    ));

    private static final Method METHOD_HAS_CACHED_INTERCEPTED_METHOD = Method.getMethod(ReflectionUtils.getRequiredInternalMethod(
        InterceptedProxy.class,
        "hasCachedInterceptedTarget"
    ));

    private static final Method METHOD_BEAN_DEFINITION_GET_REQUIRED_METHOD = Method.getMethod(ReflectionUtils.getRequiredInternalMethod(
        BeanDefinition.class,
        "getRequiredMethod",
        String.class,
        Class[].class
    ));

    private static final Type FIELD_TYPE_INTERCEPTORS = Type.getType(Interceptor[][].class);
    private static final Type TYPE_INTERCEPTOR_CHAIN = Type.getType(InterceptorChain.class);
    private static final Type TYPE_METHOD_INTERCEPTOR_CHAIN = Type.getType(MethodInterceptorChain.class);
    private static final String FIELD_TARGET = "$target";
    private static final String FIELD_BEAN_RESOLUTION_CONTEXT = "$beanResolutionContext";
    private static final String FIELD_READ_WRITE_LOCK = "$target_rwl";
    private static final Type TYPE_READ_WRITE_LOCK = Type.getType(ReentrantReadWriteLock.class);
    private static final String FIELD_READ_LOCK = "$target_rl";
    private static final String FIELD_WRITE_LOCK = "$target_wl";
    private static final Type TYPE_LOCK = Type.getType(Lock.class);
    private static final Type TYPE_BEAN_DEFINITION = Type.getType(BeanDefinition.class);
    private static final Type TYPE_BEAN_LOCATOR = Type.getType(BeanLocator.class);
    private static final Type TYPE_DEFAULT_BEAN_CONTEXT = Type.getType(DefaultBeanContext.class);
    private static final Type TYPE_BEAN_DEFINITION_REGISTRY = Type.getType(BeanDefinitionRegistry.class);

    private static final Method METHOD_PROXY_TARGET_TYPE = Method.getMethod(ReflectionUtils.getRequiredInternalMethod(ProxyBeanDefinition.class, "getTargetDefinitionType"));

    private static final Method METHOD_PROXY_TARGET_CLASS = Method.getMethod(ReflectionUtils.getRequiredInternalMethod(ProxyBeanDefinition.class, "getTargetType"));

    private static final java.lang.reflect.Method RESOLVE_INTRODUCTION_INTERCEPTORS_METHOD = ReflectionUtils.getRequiredInternalMethod(InterceptorChain.class, "resolveIntroductionInterceptors", InterceptorRegistry.class, ExecutableMethod.class, List.class);

    private static final java.lang.reflect.Method RESOLVE_AROUND_INTERCEPTORS_METHOD = ReflectionUtils.getRequiredInternalMethod(InterceptorChain.class, "resolveAroundInterceptors", InterceptorRegistry.class, ExecutableMethod.class, List.class);

    private static final Constructor CONSTRUCTOR_METHOD_INTERCEPTOR_CHAIN = ReflectionUtils.findConstructor(MethodInterceptorChain.class, Interceptor[].class, Object.class, ExecutableMethod.class, Object[].class).orElseThrow(() ->
        new IllegalStateException("new MethodInterceptorChain(..) constructor not found. Incompatible version of Micronaut?")
    );

    private static final Constructor CONSTRUCTOR_METHOD_INTERCEPTOR_CHAIN_NO_PARAMS = ReflectionUtils.findConstructor(MethodInterceptorChain.class, Interceptor[].class, Object.class, ExecutableMethod.class).orElseThrow(() ->
        new IllegalStateException("new MethodInterceptorChain(..) constructor not found. Incompatible version of Micronaut?")
    );
    private static final String INTERCEPTORS_PARAMETER = "$interceptors";

    private static final java.lang.reflect.Method METHOD_PROCEED = ReflectionUtils.getRequiredInternalMethod(InterceptorChain.class, "proceed");

    private static final String FIELD_INTERCEPTORS = "$interceptors";
    private static final String FIELD_BEAN_LOCATOR = "$beanLocator";
    private static final String FIELD_BEAN_QUALIFIER = "$beanQualifier";
    private static final String FIELD_PROXY_METHODS = "$proxyMethods";
    private static final String FIELD_PROXY_BEAN_DEFINITION = "$proxyBeanDefinition";
    private static final Type FIELD_TYPE_PROXY_METHODS = Type.getType(ExecutableMethod[].class);
    private static final Type EXECUTABLE_METHOD_TYPE = Type.getType(ExecutableMethod.class);
    private static final Type INTERCEPTOR_ARRAY_TYPE = Type.getType(Interceptor[].class);

    private final String packageName;
    private final String targetClassShortName;
    private final ClassWriter classWriter;
    private final String targetClassFullName;
    private final String proxyFullName;
    private final BeanDefinitionWriter proxyBeanDefinitionWriter;
    private final String proxyInternalName;
    private final Set<AnnotationValue<?>> interceptorBinding;
    private final Set<ClassElement> interfaceTypes;
    private final Type proxyType;
    private final boolean hotswap;
    private final boolean lazy;
    private final boolean cacheLazyTarget;
    private final boolean isInterface;
    private final BeanDefinitionWriter parentWriter;
    private final boolean isIntroduction;
    private final boolean implementInterface;
    private boolean isProxyTarget;

    private MethodVisitor constructorWriter;
    private final List<MethodRef> proxiedMethods = new ArrayList<>();
    private final Set<MethodRef> proxiedMethodsRefSet = new HashSet<>();
    private final List<MethodRef> proxyTargetMethods = new ArrayList<>();
    private int proxyMethodCount = 0;
    private GeneratorAdapter constructorGenerator;
    private int interceptorsListArgumentIndex;
    private int beanResolutionContextArgumentIndex = -1;
    private int beanContextArgumentIndex = -1;
    private int interceptorRegistryArgumentIndex = -1;
    private int qualifierIndex;
    private final List<Runnable> deferredInjectionPoints = new ArrayList<>();
    private boolean constructorRequiresReflection;
    private MethodElement declaredConstructor;
    private MethodElement newConstructor;
    private ParameterElement qualifierParameter;
    private ParameterElement interceptorsListParameter;
    private VisitorContext visitorContext;

    /**
     * <p>Constructs a new {@link AopProxyWriter} for the given parent {@link BeanDefinitionWriter} and starting interceptors types.</p>
     *
     * <p>Additional {@link Interceptor} types can be added downstream with {@link #visitInterceptorBinding(AnnotationValue[])} .</p>
     *
     * @param parent The parent {@link BeanDefinitionWriter}
     * @param settings optional setting
     * @param visitorContext The visitor context
     * @param interceptorBinding The interceptor binding of the {@link Interceptor} instances to be injected
     */
    public AopProxyWriter(BeanDefinitionWriter parent,
                          OptionalValues<Boolean> settings,
                          VisitorContext visitorContext,
                          AnnotationValue<?>... interceptorBinding) {
        super(parent.getOriginatingElements());
        this.isIntroduction = false;
        this.implementInterface = true;
        this.parentWriter = parent;
        this.isProxyTarget = settings.get(Interceptor.PROXY_TARGET).orElse(false) || parent.isInterface();
        parent.setProxiedBean(true, isProxyTarget);
        this.hotswap = isProxyTarget && settings.get(Interceptor.HOTSWAP).orElse(false);
        this.lazy = isProxyTarget && settings.get(Interceptor.LAZY).orElse(false);
        this.cacheLazyTarget = lazy && settings.get(Interceptor.CACHEABLE_LAZY_TARGET).orElse(false);
        this.isInterface = parent.isInterface();
        this.packageName = parent.getPackageName();
        this.targetClassShortName = parent.getBeanSimpleName();
        this.targetClassFullName = packageName + '.' + targetClassShortName;
        this.classWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);

        this.proxyFullName = parent.getBeanDefinitionName() + PROXY_SUFFIX;
        this.proxyInternalName = getInternalName(this.proxyFullName);
        this.proxyType = getTypeReferenceForName(proxyFullName);
        this.interceptorBinding = toInterceptorBindingMap(interceptorBinding);
        this.interfaceTypes = Collections.emptySet();
        final ClassElement aopElement = ClassElement.of(proxyFullName, isInterface, parent.getAnnotationMetadata());
        this.proxyBeanDefinitionWriter = new BeanDefinitionWriter(
            aopElement,
            parent,
            visitorContext
        );
        proxyBeanDefinitionWriter.setRequiresMethodProcessing(parent.requiresMethodProcessing());
        startClass(classWriter, getInternalName(proxyFullName), getTypeReferenceForName(targetClassFullName));
        proxyBeanDefinitionWriter.setInterceptedType(targetClassFullName);
    }

    /**
     * Constructs a new {@link AopProxyWriter} for the purposes of writing {@link io.micronaut.aop.Introduction} advise.
     *
     * @param packageName The package name
     * @param className The class name
     * @param isInterface Is the target of the advice an interface
     * @param originatingElement The originating element
     * @param annotationMetadata The annotation metadata
     * @param interfaceTypes The additional interfaces to implement
     * @param visitorContext The visitor context
     * @param interceptorBinding The interceptor types
     */
    public AopProxyWriter(String packageName,
                          String className,
                          boolean isInterface,
                          Element originatingElement,
                          AnnotationMetadata annotationMetadata,
                          ClassElement[] interfaceTypes,
                          VisitorContext visitorContext,
                          AnnotationValue<?>... interceptorBinding) {
        this(packageName, className, isInterface, true, originatingElement, annotationMetadata, interfaceTypes, visitorContext, interceptorBinding);
    }

    /**
     * Constructs a new {@link AopProxyWriter} for the purposes of writing {@link io.micronaut.aop.Introduction} advise.
     *
     * @param packageName The package name
     * @param className The class name
     * @param isInterface Is the target of the advice an interface
     * @param implementInterface Whether the interface should be implemented. If false the {@code interfaceTypes} argument should contain at least one entry
     * @param originatingElement The originating elements
     * @param annotationMetadata The annotation metadata
     * @param interfaceTypes The additional interfaces to implement
     * @param visitorContext The visitor context
     * @param interceptorBinding The interceptor binding
     */
    public AopProxyWriter(String packageName,
                          String className,
                          boolean isInterface,
                          boolean implementInterface,
                          Element originatingElement,
                          AnnotationMetadata annotationMetadata,
                          ClassElement[] interfaceTypes,
                          VisitorContext visitorContext,
                          AnnotationValue<?>... interceptorBinding) {
        super(OriginatingElements.of(originatingElement));
        this.isIntroduction = true;
        this.implementInterface = implementInterface;

        if (!implementInterface && ArrayUtils.isEmpty(interfaceTypes)) {
            throw new IllegalArgumentException("if argument implementInterface is false at least one interface should be provided to the 'interfaceTypes' argument");
        }

        this.packageName = packageName;
        this.isInterface = isInterface;
        this.hotswap = false;
        this.lazy = false;
        this.cacheLazyTarget = false;
        this.targetClassShortName = className;
        this.targetClassFullName = packageName + '.' + targetClassShortName;
        this.parentWriter = null;
        this.proxyFullName = targetClassFullName + PROXY_SUFFIX;
        this.proxyInternalName = getInternalName(this.proxyFullName);
        this.proxyType = getTypeReferenceForName(proxyFullName);
        this.interceptorBinding = toInterceptorBindingMap(interceptorBinding);
        this.interfaceTypes = interfaceTypes != null ? new LinkedHashSet<>(Arrays.asList(interfaceTypes)) : Collections.emptySet();
        this.classWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        var aopElement = ClassElement.of(
            proxyFullName,
            isInterface,
            annotationMetadata
        );
        this.proxyBeanDefinitionWriter = new BeanDefinitionWriter(
            aopElement,
            this,
            visitorContext
        );
        if (isInterface) {
            if (implementInterface) {
                proxyBeanDefinitionWriter.setInterceptedType(targetClassFullName);
            }
        } else {
            proxyBeanDefinitionWriter.setInterceptedType(targetClassFullName);
        }
        startClass(classWriter, proxyInternalName, getTypeReferenceForName(targetClassFullName));
    }

    /**
     * Find the interceptors list constructor parameter index.
     *
     * @param parameters The constructor parameters
     * @return the index
     */
    public static int findInterceptorsListParameterIndex(List<ParameterElement> parameters) {
        return parameters.indexOf(parameters.stream().filter(p -> p.getName().equals(INTERCEPTORS_PARAMETER)).findFirst().orElseThrow());
    }

    @Override
    public boolean isEnabled() {
        return proxyBeanDefinitionWriter.isEnabled();
    }

    /**
     * Is the target bean being proxied.
     *
     * @return True if the target bean is being proxied
     */
    @Override
    public boolean isProxyTarget() {
        return false;
    }

    @Override
    protected void startClass(ClassVisitor classWriter, String className, Type superType) {
        String[] interfaces = getImplementedInterfaceInternalNames();
        classWriter.visit(V17, ACC_SYNTHETIC, className, null, !isInterface ? superType.getInternalName() : null, interfaces);

        classWriter.visitAnnotation(TYPE_GENERATED.getDescriptor(), false);

        classWriter.visitField(ACC_FINAL | ACC_PRIVATE, FIELD_INTERCEPTORS, FIELD_TYPE_INTERCEPTORS.getDescriptor(), null, null);
        classWriter.visitField(ACC_FINAL | ACC_PRIVATE, FIELD_PROXY_METHODS, FIELD_TYPE_PROXY_METHODS.getDescriptor(), null, null);
    }

    private String[] getImplementedInterfaceInternalNames() {
        return interfaceTypes.stream().map(o -> JavaModelUtils.getTypeReference(o).getInternalName()).toArray(String[]::new);
    }

    @Override
    public void visitBeanFactoryMethod(ClassElement factoryClass, MethodElement factoryMethod) {
        proxyBeanDefinitionWriter.visitBeanFactoryMethod(factoryClass, factoryMethod);
    }

    @Override
    public void visitBeanFactoryMethod(ClassElement factoryClass, MethodElement factoryMethod, ParameterElement[] parameters) {
        proxyBeanDefinitionWriter.visitBeanFactoryMethod(factoryClass, factoryMethod, parameters);
    }

    @Override
    public void visitBeanFactoryField(ClassElement factoryClass, FieldElement factoryField) {
        proxyBeanDefinitionWriter.visitBeanFactoryField(factoryClass, factoryField);
    }

    @Override
    public boolean isSingleton() {
        return proxyBeanDefinitionWriter.isSingleton();
    }

    @Override
    public boolean isInterface() {
        return isInterface;
    }

    @Override
    public void visitBeanDefinitionInterface(Class<? extends BeanDefinition> interfaceType) {
        proxyBeanDefinitionWriter.visitBeanDefinitionInterface(interfaceType);
    }

    @Override
    public String getBeanTypeName() {
        return proxyBeanDefinitionWriter.getBeanTypeName();
    }

    @Override
    public Type getProvidedType() {
        return proxyBeanDefinitionWriter.getProvidedType();
    }

    @Override
    public void setValidated(boolean validated) {
        proxyBeanDefinitionWriter.setValidated(validated);
    }

    @Override
    public void setInterceptedType(String typeName) {
        proxyBeanDefinitionWriter.setInterceptedType(typeName);
    }

    @Override
    public Optional<Type> getInterceptedType() {
        return proxyBeanDefinitionWriter.getInterceptedType();
    }

    @Override
    public boolean isValidated() {
        return proxyBeanDefinitionWriter.isValidated();
    }

    @Override
    public String getBeanDefinitionName() {
        return proxyBeanDefinitionWriter.getBeanDefinitionName();
    }

    /**
     * Visits a constructor.
     *
     * @param constructor The constructor
     * @param requiresReflection Whether reflection is required
     * @param visitorContext The visitor context
     */
    @Override
    public void visitBeanDefinitionConstructor(
        MethodElement constructor,
        boolean requiresReflection,
        VisitorContext visitorContext) {
        this.constructorRequiresReflection = requiresReflection;
        this.declaredConstructor = constructor;
        this.visitorContext = visitorContext;
        io.micronaut.core.annotation.AnnotationValue<?>[] interceptorTypes =
            InterceptedMethodUtil.resolveInterceptorBinding(constructor.getAnnotationMetadata(), InterceptorKind.AROUND_CONSTRUCT);
        visitInterceptorBinding(interceptorTypes);
    }

    @Override
    public void visitDefaultConstructor(AnnotationMetadata annotationMetadata, VisitorContext visitorContext) {
        this.constructorRequiresReflection = false;
        var classElement = ClassElement.of(proxyType.getClassName());
        this.declaredConstructor = MethodElement.of(
            classElement,
            annotationMetadata,
            classElement,
            classElement,
            "<init>"
        );
    }

    private void initConstructor(MethodElement constructor) {
        final ClassElement interceptorList = ClassElement.of(List.class, AnnotationMetadata.EMPTY_METADATA, Collections.singletonMap(
            "E", ClassElement.of(BeanRegistration.class, AnnotationMetadata.EMPTY_METADATA, Collections.singletonMap(
                "T", ClassElement.of(Interceptor.class)
            ))
        ));
        this.qualifierParameter = ParameterElement.of(Qualifier.class, "$qualifier");
        this.interceptorsListParameter = ParameterElement.of(interceptorList, INTERCEPTORS_PARAMETER);
        var interceptorRegistryParameter = ParameterElement.of(ClassElement.of(InterceptorRegistry.class), "$interceptorRegistry");
        var proxyClass = ClassElement.of(proxyType.getClassName());

        ParameterElement[] constructorParameters = constructor.getParameters();
        var newConstructorParameters = new ArrayList<ParameterElement>(constructorParameters.length + 5);
        newConstructorParameters.addAll(Arrays.asList(constructorParameters));
        newConstructorParameters.add(ParameterElement.of(BeanResolutionContext.class, "$beanResolutionContext"));
        newConstructorParameters.add(ParameterElement.of(BeanContext.class, "$beanContext"));
        newConstructorParameters.add(qualifierParameter);
        newConstructorParameters.add(interceptorsListParameter);
        newConstructorParameters.add(interceptorRegistryParameter);
        this.newConstructor = MethodElement.of(
            proxyClass,
            constructor.getAnnotationMetadata(),
            proxyClass,
            proxyClass,
            "<init>",
            newConstructorParameters.toArray(ZERO_PARAMETER_ELEMENTS)
        );
        this.beanResolutionContextArgumentIndex = constructorParameters.length;
        this.beanContextArgumentIndex = constructorParameters.length + 1;
        this.qualifierIndex = constructorParameters.length + 2;
        this.interceptorsListArgumentIndex = constructorParameters.length + 3;
        this.interceptorRegistryArgumentIndex = constructorParameters.length + 4;
    }

    @NonNull
    @Override
    public String getBeanDefinitionReferenceClassName() {
        return proxyBeanDefinitionWriter.getBeanDefinitionReferenceClassName();
    }

    /**
     * Visit an abstract method that is to be implemented.
     *
     * @param declaringBean The declaring bean of the method.
     * @param methodElement The method element
     */
    public void visitIntroductionMethod(TypedElement declaringBean,
                                        MethodElement methodElement) {
        visitAroundMethod(
            declaringBean,
            methodElement
        );
    }

    /**
     * Visit a method that is to be proxied.
     *
     * @param beanType The bean type.
     * @param methodElement The method element
     **/
    public void visitAroundMethod(TypedElement beanType,
                                  MethodElement methodElement) {

        ClassElement returnType = methodElement.isSuspend() ? ClassElement.of(Object.class) : methodElement.getReturnType();
        Type returnTypeObject = JavaModelUtils.getTypeReference(returnType);
        boolean isPrimitive = returnType.isPrimitive();
        boolean isVoidReturn = isPrimitive && returnTypeObject.equals(Type.VOID_TYPE);

        final Optional<MethodElement> overridden = methodElement.getOwningType()
            .getEnclosedElement(ElementQuery.ALL_METHODS
                .onlyInstance()
                .named(name -> name.equals(methodElement.getName()))
                .filter(el -> el.overrides(methodElement)));

        if (overridden.isPresent()) {
            MethodElement overriddenBy = overridden.get();

            String methodElementKey = methodElement.getName() +
                Arrays.stream(methodElement.getSuspendParameters())
                    .map(p -> toTypeString(p.getType()))
                    .collect(Collectors.joining(","));

            String overriddenByKey = overriddenBy.getName() +
                Arrays.stream(methodElement.getSuspendParameters())
                    .map(p -> toTypeString(p.getGenericType()))
                    .collect(Collectors.joining(","));

            if (!methodElementKey.equals(overriddenByKey)) {
                buildMethodDelegate(methodElement, overriddenBy, isVoidReturn);
                return;
            }
        }

        String methodName = methodElement.getName();
        List<ParameterElement> argumentTypeList = Arrays.asList(methodElement.getSuspendParameters());
        int argumentCount = argumentTypeList.size();
        final Type declaringTypeReference = JavaModelUtils.getTypeReference(beanType);
        var methodKey = new MethodRef(methodName, argumentTypeList, returnTypeObject);

        if (!proxiedMethodsRefSet.contains(methodKey)) {

            String interceptedProxyClassName = null;
            String interceptedProxyBridgeMethodName = null;

            if (!isProxyTarget) {
                // if the target is not being proxied then we need to generate a bridge method and executable method that knows about it

                if (!methodElement.isAbstract() || methodElement.isDefault()) {
                    interceptedProxyClassName = proxyFullName;
                    interceptedProxyBridgeMethodName = "$$access$$" + methodName;

                    String bridgeDesc = getMethodDescriptor(returnType, argumentTypeList);

                    // now build a bridge to invoke the original method
                    MethodVisitor bridgeWriter = classWriter.visitMethod(ACC_SYNTHETIC,
                        interceptedProxyBridgeMethodName, bridgeDesc, null, null);
                    var bridgeGenerator = new GeneratorAdapter(bridgeWriter, ACC_SYNTHETIC, interceptedProxyBridgeMethodName, bridgeDesc);
                    bridgeGenerator.loadThis();
                    for (int i = 0; i < argumentTypeList.size(); i++) {
                        bridgeGenerator.loadArg(i);
                    }
                    String desc = getMethodDescriptor(returnType, argumentTypeList);
                    bridgeWriter.visitMethodInsn(INVOKESPECIAL, declaringTypeReference.getInternalName(), methodName, desc, this.isInterface && methodElement.isDefault());
                    bridgeGenerator.returnValue();
                    bridgeGenerator.endMethod();
                }
            }

            BeanDefinitionWriter beanDefinitionWriter = parentWriter == null ? proxyBeanDefinitionWriter : parentWriter;
            int methodIndex = beanDefinitionWriter.visitExecutableMethod(
                beanType,
                methodElement,
                interceptedProxyClassName,
                interceptedProxyBridgeMethodName
            );
            int index = proxyMethodCount++;

            methodKey.methodIndex = methodIndex;
            proxiedMethods.add(methodKey);
            proxiedMethodsRefSet.add(methodKey);
            proxyTargetMethods.add(methodKey);

            buildMethodOverride(returnType, methodName, index, argumentTypeList, argumentCount, isVoidReturn);
        }
    }

    private void buildMethodOverride(
        TypedElement returnType,
        String methodName,
        int index,
        List<ParameterElement> argumentTypeList,
        int argumentCount,
        boolean isVoidReturn) {
        // override the original method
        String desc = getMethodDescriptor(returnType, argumentTypeList);
        MethodVisitor overridden = classWriter.visitMethod(ACC_PUBLIC, methodName, desc, null, null);
        GeneratorAdapter overriddenMethodGenerator = new GeneratorAdapter(overridden, ACC_PUBLIC, methodName, desc);

        // instantiate the MethodInterceptorChain

        // ie InterceptorChain chain = new MethodInterceptorChain(interceptors, this, executableMethod, name);
        overriddenMethodGenerator.newInstance(TYPE_METHOD_INTERCEPTOR_CHAIN);
        overriddenMethodGenerator.dup();

        // 1st argument: interceptors
        // this.interceptors[0];
        overriddenMethodGenerator.loadThis();
        overriddenMethodGenerator.getField(proxyType, FIELD_INTERCEPTORS, FIELD_TYPE_INTERCEPTORS);
        overriddenMethodGenerator.push(index);
        overriddenMethodGenerator.visitInsn(AALOAD);

        // 2nd argument: this or target
        overriddenMethodGenerator.loadThis();
        if (isProxyTarget) {
            if (hotswap || lazy) {
                overriddenMethodGenerator.invokeInterface(Type.getType(InterceptedProxy.class), Method.getMethod("java.lang.Object interceptedTarget()"));
            } else {
                overriddenMethodGenerator.getField(proxyType, FIELD_TARGET, getTypeReferenceForName(targetClassFullName));
            }
        }

        // 3rd argument: the executable method
        // this.proxyMethods[0];
        overriddenMethodGenerator.loadThis();
        overriddenMethodGenerator.getField(proxyType, FIELD_PROXY_METHODS, FIELD_TYPE_PROXY_METHODS);
        overriddenMethodGenerator.push(index);
        overriddenMethodGenerator.visitInsn(AALOAD);

        if (argumentCount > 0) {
            // fourth argument: array of the argument values
            overriddenMethodGenerator.push(argumentCount);
            overriddenMethodGenerator.newArray(Type.getType(Object.class));

            // now pass the remaining arguments from the original method
            for (int i = 0; i < argumentCount; i++) {
                overriddenMethodGenerator.dup();
                ParameterElement argType = argumentTypeList.get(i);
                overriddenMethodGenerator.push(i);
                overriddenMethodGenerator.loadArg(i);
                pushBoxPrimitiveIfNecessary(argType, overriddenMethodGenerator);
                overriddenMethodGenerator.visitInsn(AASTORE);
            }

            // invoke MethodInterceptorChain constructor with parameters
            overriddenMethodGenerator.invokeConstructor(TYPE_METHOD_INTERCEPTOR_CHAIN, Method.getMethod(CONSTRUCTOR_METHOD_INTERCEPTOR_CHAIN));
        } else {
            // invoke MethodInterceptorChain constructor without parameters
            overriddenMethodGenerator.invokeConstructor(TYPE_METHOD_INTERCEPTOR_CHAIN, Method.getMethod(CONSTRUCTOR_METHOD_INTERCEPTOR_CHAIN_NO_PARAMS));
        }

        overriddenMethodGenerator.invokeVirtual(TYPE_INTERCEPTOR_CHAIN, Method.getMethod(METHOD_PROCEED));
        if (isVoidReturn) {
            overriddenMethodGenerator.pop();
        } else {
            pushCastToType(overriddenMethodGenerator, returnType);
        }
        overriddenMethodGenerator.returnValue();
        overriddenMethodGenerator.endMethod();
    }

    private void buildMethodDelegate(MethodElement methodElement, MethodElement overriddenBy, boolean isVoidReturn) {
        String desc = getMethodDescriptor(methodElement.getReturnType().getType(), Arrays.asList(methodElement.getSuspendParameters()));
        MethodVisitor overridden = classWriter.visitMethod(ACC_PUBLIC, methodElement.getName(), desc, null, null);
        var overriddenMethodGenerator = new GeneratorAdapter(overridden, ACC_PUBLIC, methodElement.getName(), desc);
        overriddenMethodGenerator.loadThis();
        int i = 0;
        for (ParameterElement param : overriddenBy.getSuspendParameters()) {
            overriddenMethodGenerator.loadArg(i++);
            pushCastToType(overriddenMethodGenerator, param.getGenericType());
        }
        overriddenMethodGenerator.visitMethodInsn(INVOKESPECIAL,
            proxyType.getInternalName(),
            overriddenBy.getName(),
            getMethodDescriptor(overriddenBy.getReturnType().getType(), Arrays.asList(overriddenBy.getSuspendParameters())),
            this.isInterface && overriddenBy.isDefault());

        if (!isVoidReturn) {
            ClassElement returnType = overriddenBy.getReturnType();
            pushCastToType(overriddenMethodGenerator, returnType);
        }
        overriddenMethodGenerator.returnValue();
        overriddenMethodGenerator.endMethod();
    }

    /**
     * Finalizes the proxy. This method should be called before writing the proxy to disk with {@link #writeTo(File)}
     */
    @Override
    public void visitBeanDefinitionEnd() {
        if (declaredConstructor == null) {
            throw new IllegalStateException("The method visitBeanDefinitionConstructor(..) should be called at least once");
        } else {
            initConstructor(declaredConstructor);
        }

        if (parentWriter != null && !isProxyTarget) {
            processAlreadyVisitedMethods(parentWriter);
        }

        interceptorsListParameter.annotate(AnnotationUtil.ANN_INTERCEPTOR_BINDING_QUALIFIER, builder -> {
            final AnnotationValue<?>[] interceptorBinding = this.interceptorBinding.toArray(ZERO_ANNOTATION_VALUES);
            builder.values(interceptorBinding);
        });
        qualifierParameter.annotate(AnnotationUtil.NULLABLE);

        String constructorDescriptor = getConstructorDescriptor(Arrays.asList(newConstructor.getParameters()));
        ClassWriter proxyClassWriter = this.classWriter;
        this.constructorWriter = proxyClassWriter.visitMethod(
            ACC_PUBLIC,
            CONSTRUCTOR_NAME,
            constructorDescriptor,
            null,
            null
        );

        this.constructorGenerator = new GeneratorAdapter(constructorWriter, ACC_PUBLIC, CONSTRUCTOR_NAME, constructorDescriptor);
        GeneratorAdapter proxyConstructorGenerator = this.constructorGenerator;

        proxyConstructorGenerator.loadThis();
        if (isInterface) {
            proxyConstructorGenerator.invokeConstructor(TYPE_OBJECT, METHOD_DEFAULT_CONSTRUCTOR);
        } else {
            ParameterElement[] existingArguments = declaredConstructor.getParameters();
            for (int i = 0; i < existingArguments.length; i++) {
                proxyConstructorGenerator.loadArg(i);
            }
            String superConstructorDescriptor = getConstructorDescriptor(Arrays.asList(existingArguments));
            proxyConstructorGenerator.invokeConstructor(getTypeReferenceForName(targetClassFullName), new Method(CONSTRUCTOR_NAME, superConstructorDescriptor));
        }

        proxyBeanDefinitionWriter.visitBeanDefinitionConstructor(
            newConstructor,
            constructorRequiresReflection,
            visitorContext
        );

        GeneratorAdapter targetDefinitionGenerator = null;
        GeneratorAdapter targetTypeGenerator = null;
        if (parentWriter != null) {
            proxyBeanDefinitionWriter.visitBeanDefinitionInterface(ProxyBeanDefinition.class);
            ClassVisitor pcw = proxyBeanDefinitionWriter.getClassWriter();
            targetDefinitionGenerator = new GeneratorAdapter(pcw.visitMethod(ACC_PUBLIC,
                METHOD_PROXY_TARGET_TYPE.getName(),
                METHOD_PROXY_TARGET_TYPE.getDescriptor(),
                null, null

            ), ACC_PUBLIC, METHOD_PROXY_TARGET_TYPE.getName(), METHOD_PROXY_TARGET_TYPE.getDescriptor());
            targetDefinitionGenerator.loadThis();
            targetDefinitionGenerator.push(getTypeReferenceForName(parentWriter.getBeanDefinitionName()));
            targetDefinitionGenerator.returnValue();


            targetTypeGenerator = new GeneratorAdapter(pcw.visitMethod(ACC_PUBLIC,
                METHOD_PROXY_TARGET_CLASS.getName(),
                METHOD_PROXY_TARGET_CLASS.getDescriptor(),
                null, null

            ), ACC_PUBLIC, METHOD_PROXY_TARGET_CLASS.getName(), METHOD_PROXY_TARGET_CLASS.getDescriptor());
            targetTypeGenerator.loadThis();
            targetTypeGenerator.push(getTypeReferenceForName(parentWriter.getBeanTypeName()));
            targetTypeGenerator.returnValue();
        }

        Class<?> interceptedInterface = isIntroduction ? Introduced.class : Intercepted.class;
        Type targetType = getTypeReferenceForName(targetClassFullName);

        // add the $beanLocator field
        if (isProxyTarget) {
            // add the $proxyBeanDefinition field
            proxyClassWriter.visitField(
                ACC_PRIVATE | ACC_FINAL,
                FIELD_PROXY_BEAN_DEFINITION,
                TYPE_BEAN_DEFINITION.getDescriptor(),
                null,
                null
            );

            proxyClassWriter.visitField(
                ACC_PRIVATE | ACC_FINAL,
                FIELD_BEAN_LOCATOR,
                TYPE_BEAN_LOCATOR.getDescriptor(),
                null,
                null
            );

            // add the $beanQualifier field
            proxyClassWriter.visitField(
                ACC_PRIVATE,
                FIELD_BEAN_QUALIFIER,
                Type.getType(Qualifier.class).getDescriptor(),
                null,
                null
            );

            writeWithQualifierMethod(proxyClassWriter);
            if (!lazy || cacheLazyTarget) {
                // add the $target field for the target bean
                proxyClassWriter.visitField(
                    ACC_PRIVATE,
                    FIELD_TARGET,
                    targetType.getDescriptor(),
                    null,
                    null
                );
            }
            if (lazy) {
                interceptedInterface = InterceptedProxy.class;
                proxyClassWriter.visitField(
                    ACC_PRIVATE,
                    FIELD_BEAN_RESOLUTION_CONTEXT,
                    Type.getType(BeanResolutionContext.class).getDescriptor(),
                    null,
                    null
                );
            } else {
                interceptedInterface = hotswap ? HotSwappableInterceptedProxy.class : InterceptedProxy.class;
                if (hotswap) {
                    // Add ReadWriteLock field
                    // private final ReentrantReadWriteLock $target_rwl = new ReentrantReadWriteLock();
                    proxyClassWriter.visitField(
                        ACC_PRIVATE | ACC_FINAL,
                        FIELD_READ_WRITE_LOCK,
                        TYPE_READ_WRITE_LOCK.getDescriptor(),
                        null, null
                    );
                    proxyConstructorGenerator.loadThis();
                    pushNewInstance(proxyConstructorGenerator, TYPE_READ_WRITE_LOCK);
                    proxyConstructorGenerator.putField(proxyType, FIELD_READ_WRITE_LOCK, TYPE_READ_WRITE_LOCK);

                    // Add Read Lock field
                    // private final Lock $target_rl = $target_rwl.readLock();
                    proxyClassWriter.visitField(
                        ACC_PRIVATE | ACC_FINAL,
                        FIELD_READ_LOCK,
                        TYPE_LOCK.getDescriptor(),
                        null, null
                    );
                    proxyConstructorGenerator.loadThis();
                    proxyConstructorGenerator.loadThis();
                    proxyConstructorGenerator.getField(proxyType, FIELD_READ_WRITE_LOCK, TYPE_READ_WRITE_LOCK);
                    proxyConstructorGenerator.invokeInterface(
                        Type.getType(ReadWriteLock.class),
                        Method.getMethod(Lock.class.getName() + " readLock()")
                    );
                    proxyConstructorGenerator.putField(proxyType, FIELD_READ_LOCK, TYPE_LOCK);

                    // Add Write Lock field
                    // private final Lock $target_wl = $target_rwl.writeLock();
                    proxyClassWriter.visitField(
                        ACC_PRIVATE | ACC_FINAL,
                        FIELD_WRITE_LOCK,
                        Type.getDescriptor(Lock.class),
                        null, null
                    );

                    proxyConstructorGenerator.loadThis();
                    proxyConstructorGenerator.loadThis();
                    proxyConstructorGenerator.getField(proxyType, FIELD_READ_WRITE_LOCK, TYPE_READ_WRITE_LOCK);
                    proxyConstructorGenerator.invokeInterface(
                        Type.getType(ReadWriteLock.class),
                        Method.getMethod(Lock.class.getName() + " writeLock()")
                    );
                    proxyConstructorGenerator.putField(proxyType, FIELD_WRITE_LOCK, TYPE_LOCK);
                }
            }
            // assign the bean locator
            proxyConstructorGenerator.loadThis();
            proxyConstructorGenerator.loadArg(beanContextArgumentIndex);
            proxyConstructorGenerator.putField(proxyType, FIELD_BEAN_LOCATOR, TYPE_BEAN_LOCATOR);

            proxyConstructorGenerator.loadThis();
            proxyConstructorGenerator.loadArg(qualifierIndex);
            proxyConstructorGenerator.putField(proxyType, FIELD_BEAN_QUALIFIER, Type.getType(Qualifier.class));

            proxyConstructorGenerator.loadThis();
            pushResolveProxyBeanDefinition(proxyConstructorGenerator, targetType);
            proxyConstructorGenerator.putField(proxyType, FIELD_PROXY_BEAN_DEFINITION, TYPE_BEAN_DEFINITION);

            if (!lazy) {
                proxyConstructorGenerator.loadThis();
                pushResolveProxyTargetBean(proxyConstructorGenerator, targetType);
                proxyConstructorGenerator.putField(proxyType, FIELD_TARGET, targetType);
            } else {
                proxyConstructorGenerator.loadThis();
                proxyConstructorGenerator.loadArg(beanResolutionContextArgumentIndex);
                proxyConstructorGenerator.invokeInterface(
                    Type.getType(BeanResolutionContext.class),
                    Method.getMethod(
                        ReflectionUtils.getRequiredMethod(BeanResolutionContext.class, "copy")
                    )
                );
                proxyConstructorGenerator.putField(proxyType, FIELD_BEAN_RESOLUTION_CONTEXT, Type.getType(BeanResolutionContext.class));
            }

            // Write the Object interceptedTarget() method
            writeInterceptedTargetMethod(proxyClassWriter, targetType);

            if (!lazy || cacheLazyTarget) {
                // Write `boolean hasCachedInterceptedTarget()`
                writeHasCachedInterceptedTargetMethod(proxyClassWriter, targetType);
            }

            // Write the swap method
            // e. T swap(T newInstance);
            if (hotswap && !lazy) {
                writeSwapMethod(proxyClassWriter, targetType);
            }
        }

        String[] interfaces = getImplementedInterfaceInternalNames();
        if (isInterface && implementInterface) {
            String[] adviceInterfaces = {
                getInternalName(targetClassFullName),
                Type.getInternalName(interceptedInterface)
            };
            interfaces = ArrayUtils.concat(interfaces, adviceInterfaces);
        } else {
            String[] adviceInterfaces = {Type.getInternalName(interceptedInterface)};
            interfaces = ArrayUtils.concat(interfaces, adviceInterfaces);
        }
        proxyClassWriter.visit(V17, ACC_SYNTHETIC,
            proxyInternalName,
            null,
            isInterface ? TYPE_OBJECT.getInternalName() : getTypeReferenceForName(targetClassFullName).getInternalName(),
            interfaces);

        // set $proxyMethods field
        proxyConstructorGenerator.loadThis();
        proxyConstructorGenerator.push(proxyMethodCount);
        proxyConstructorGenerator.newArray(EXECUTABLE_METHOD_TYPE);
        proxyConstructorGenerator.putField(
            proxyType,
            FIELD_PROXY_METHODS,
            FIELD_TYPE_PROXY_METHODS
        );

        // set $interceptors field
        proxyConstructorGenerator.loadThis();
        proxyConstructorGenerator.push(proxyMethodCount);
        proxyConstructorGenerator.newArray(INTERCEPTOR_ARRAY_TYPE);
        proxyConstructorGenerator.putField(
            proxyType,
            FIELD_INTERCEPTORS,
            FIELD_TYPE_INTERCEPTORS
        );

        // now initialize the held values
        if (isProxyTarget) {
            if (proxiedMethods.size() == proxyMethodCount) {

                Iterator<MethodRef> iterator = proxyTargetMethods.iterator();
                for (int i = 0; i < proxyMethodCount; i++) {
                    MethodRef methodRef = iterator.next();

                    // The following will initialize the array of $proxyMethod instances
                    // E.g. this.$proxyMethods[0] = $PARENT_BEAN.getRequiredMethod("test", new Class[]{String.class});
                    proxyConstructorGenerator.loadThis();

                    // Step 1: dereference the array - this.$proxyMethods[0]
                    proxyConstructorGenerator.getField(proxyType, FIELD_PROXY_METHODS, FIELD_TYPE_PROXY_METHODS);
                    proxyConstructorGenerator.push(i);

                    // Step 2: lookup the Method instance from the declaring type
                    // $proxyBeanDefinition.getMethod("test", new Class[]{String.class});
                    proxyConstructorGenerator.loadThis();
                    proxyConstructorGenerator.getField(proxyType, FIELD_PROXY_BEAN_DEFINITION, TYPE_BEAN_DEFINITION);

                    // Arguments are written as generic types, so we need to look for the method using the generic arguments
                    pushMethodNameAndTypesArguments(proxyConstructorGenerator, methodRef.name, methodRef.genericArgumentTypes);

                    proxyConstructorGenerator.invokeInterface(
                        TYPE_BEAN_DEFINITION,
                        METHOD_BEAN_DEFINITION_GET_REQUIRED_METHOD
                    );

                    // Step 3: store the result in the array
                    proxyConstructorGenerator.arrayStore(FIELD_TYPE_PROXY_METHODS);

                    // Step 4: Resolve the interceptors
                    // this.$interceptors[0] = InterceptorChain.resolveAroundInterceptors(this.$proxyMethods[0], var2);
                    pushResolveInterceptorsCall(proxyConstructorGenerator, i, isIntroduction);
                }
            }
        } else if (!proxiedMethods.isEmpty()) {
            BeanDefinitionWriter beanDefinitionWriter = parentWriter == null ? proxyBeanDefinitionWriter : parentWriter;
            ExecutableMethodsDefinitionWriter executableMethodsDefinitionWriter = beanDefinitionWriter.getExecutableMethodsWriter();
            Type executableMethodsDefinitionType = executableMethodsDefinitionWriter.getClassType();
            proxyConstructorGenerator.newInstance(executableMethodsDefinitionType);
            proxyConstructorGenerator.dup();
            if (executableMethodsDefinitionWriter.isSupportsInterceptedProxy()) {
                proxyConstructorGenerator.push(true);
                proxyConstructorGenerator.invokeConstructor(executableMethodsDefinitionType, new Method(CONSTRUCTOR_NAME, getConstructorDescriptor(boolean.class)));
            } else {
                proxyConstructorGenerator.invokeConstructor(executableMethodsDefinitionType, new Method(CONSTRUCTOR_NAME, DESCRIPTOR_DEFAULT_CONSTRUCTOR));
            }
            int executableMethodsDefinitionIndex = proxyConstructorGenerator.newLocal(executableMethodsDefinitionType);
            proxyConstructorGenerator.storeLocal(executableMethodsDefinitionIndex, executableMethodsDefinitionType);

            for (int i = 0; i < proxyMethodCount; i++) {
                MethodRef methodRef = proxiedMethods.get(i);
                int methodIndex = methodRef.methodIndex;

                boolean introduction = isIntroduction && (
                    executableMethodsDefinitionWriter.isAbstract(methodIndex) || (
                        executableMethodsDefinitionWriter.isInterface(methodIndex) && !executableMethodsDefinitionWriter.isDefault(methodIndex)));

                // The following will initialize the array of $proxyMethod instances
                // E.g. this.proxyMethods[0] = new $blah0();
                proxyConstructorGenerator.loadThis();
                proxyConstructorGenerator.getField(proxyType, FIELD_PROXY_METHODS, FIELD_TYPE_PROXY_METHODS);
                proxyConstructorGenerator.push(i);
                // getExecutableMethodByIndex
                proxyConstructorGenerator.loadLocal(executableMethodsDefinitionIndex);
                proxyConstructorGenerator.push(methodIndex);
                proxyConstructorGenerator.invokeVirtual(executableMethodsDefinitionType, ExecutableMethodsDefinitionWriter.GET_EXECUTABLE_AT_INDEX_METHOD);
                proxyConstructorGenerator.visitInsn(AASTORE);

                pushResolveInterceptorsCall(proxyConstructorGenerator, i, introduction);
            }
        }

        for (Runnable fieldInjectionPoint : deferredInjectionPoints) {
            fieldInjectionPoint.run();
        }

        constructorWriter.visitInsn(RETURN);
        constructorWriter.visitMaxs(DEFAULT_MAX_STACK, 1);

        this.constructorWriter.visitEnd();
        proxyBeanDefinitionWriter.visitBeanDefinitionEnd();
        if (targetDefinitionGenerator != null) {
            targetDefinitionGenerator.visitMaxs(1, 1);
            targetDefinitionGenerator.visitEnd();
        }

        if (targetTypeGenerator != null) {
            targetTypeGenerator.visitMaxs(1, 1);
            targetTypeGenerator.visitEnd();
        }


        proxyClassWriter.visitEnd();
    }

    private void pushResolveLazyProxyTargetBean(GeneratorAdapter generatorAdapter, Type targetType) {
        // load the bean context
        generatorAdapter.loadThis();
        generatorAdapter.getField(proxyType, FIELD_BEAN_LOCATOR, TYPE_BEAN_LOCATOR);
        pushCastToType(generatorAdapter, TYPE_DEFAULT_BEAN_CONTEXT);

        // 1st argument: the bean resolution context
        generatorAdapter.loadThis();
        generatorAdapter.getField(proxyType, FIELD_BEAN_RESOLUTION_CONTEXT, Type.getType(BeanResolutionContext.class));
        // 2nd argument: this.$proxyBeanDefinition
        generatorAdapter.loadThis();
        generatorAdapter.getField(proxyType, FIELD_PROXY_BEAN_DEFINITION, TYPE_BEAN_DEFINITION);
        // 3rd argument: the type
        pushTargetArgument(generatorAdapter, targetType);
        // 4th argument: the qualifier
        pushQualifier(generatorAdapter);

        generatorAdapter.invokeVirtual(
            TYPE_DEFAULT_BEAN_CONTEXT,
            METHOD_GET_PROXY_TARGET_BEAN_WITH_BEAN_DEFINITION_AND_CONTEXT
        );
        pushCastToType(generatorAdapter, getTypeReferenceForName(targetClassFullName));
    }

    private void pushResolveProxyBeanDefinition(GeneratorAdapter generatorAdapter, Type targetType) {
        // load the bean context
        generatorAdapter.loadArg(beanContextArgumentIndex);

        // 1t argument: the type
        pushTargetArgument(generatorAdapter, targetType);
        // 2nd argument: the qualifier
        pushQualifier(generatorAdapter);

        generatorAdapter.invokeInterface(
            TYPE_BEAN_DEFINITION_REGISTRY,
            METHOD_GET_PROXY_BEAN_DEFINITION
        );
    }

    private void pushQualifier(GeneratorAdapter generatorAdapter) {
        generatorAdapter.loadThis();
        generatorAdapter.getField(proxyType, FIELD_BEAN_QUALIFIER, Type.getType(Qualifier.class));
    }

    private void pushResolveProxyTargetBean(GeneratorAdapter generatorAdapter, Type targetType) {
        // load the bean context
        generatorAdapter.loadArg(beanContextArgumentIndex);
        pushCastToType(generatorAdapter, TYPE_DEFAULT_BEAN_CONTEXT);

        // 1st argument: the bean resolution context
        generatorAdapter.loadArg(beanResolutionContextArgumentIndex);
        // 2nd argument: this.$proxyBeanDefinition
        generatorAdapter.loadThis();
        generatorAdapter.getField(proxyType, FIELD_PROXY_BEAN_DEFINITION, TYPE_BEAN_DEFINITION);
        // 3rd argument: the type
        pushTargetArgument(generatorAdapter, targetType);
        // 4th argument: the qualifier
        pushQualifier(generatorAdapter);

        generatorAdapter.invokeVirtual(
            TYPE_DEFAULT_BEAN_CONTEXT,
            METHOD_GET_PROXY_TARGET_BEAN_WITH_BEAN_DEFINITION_AND_CONTEXT
        );
        pushCastToType(generatorAdapter, getTypeReferenceForName(targetClassFullName));
    }

    private void pushTargetArgument(GeneratorAdapter proxyConstructorGenerator, Type targetType) {
        buildArgumentWithGenerics(
            proxyConstructorGenerator,
            targetType,
            new AnnotationMetadataReference(
                getBeanDefinitionName(),
                getAnnotationMetadata()
            ),
            parentWriter != null ? parentWriter.getTypeArguments() : proxyBeanDefinitionWriter.getTypeArguments()
        );
    }

    /**
     * Write the proxy to the given compilation directory.
     *
     * @param compilationDir The target compilation directory
     * @throws IOException If an error occurs writing the file
     */
    @Override
    public void writeTo(File compilationDir) throws IOException {
        accept(newClassWriterOutputVisitor(compilationDir));
    }

    @NonNull
    @Override
    public ClassElement[] getTypeArguments() {
        return proxyBeanDefinitionWriter.getTypeArguments();
    }

    @NonNull
    @Override
    public Map<String, ClassElement> getTypeArgumentMap() {
        return proxyBeanDefinitionWriter.getTypeArgumentMap();
    }

    /**
     * Write the class to output via a visitor that manages output destination.
     *
     * @param visitor the writer output visitor
     * @throws IOException If an error occurs
     */
    @Override
    public void accept(ClassWriterOutputVisitor visitor) throws IOException {
        proxyBeanDefinitionWriter.accept(visitor);
        try (OutputStream out = visitor.visitClass(proxyFullName, getOriginatingElements())) {
            out.write(classWriter.toByteArray());
        }
    }

    @Override
    public void visitSuperBeanDefinition(String name) {
        proxyBeanDefinitionWriter.visitSuperBeanDefinition(name);
    }

    @Override
    public void visitSuperBeanDefinitionFactory(String beanName) {
        proxyBeanDefinitionWriter.visitSuperBeanDefinitionFactory(beanName);
    }

    @Override
    public void visitSetterValue(
        TypedElement declaringType,
        MethodElement methodElement,
        AnnotationMetadata annotationMetadata,
        boolean requiresReflection,
        boolean isOptional) {
        deferredInjectionPoints.add(() ->
            proxyBeanDefinitionWriter.visitSetterValue(
                declaringType,
                methodElement,
                annotationMetadata,
                requiresReflection,
                isOptional
            )
        );
    }

    @Override
    public void visitPostConstructMethod(
        TypedElement declaringType,
        MethodElement methodElement,
        boolean requiresReflection,
        VisitorContext visitorContext) {
        deferredInjectionPoints.add(() -> proxyBeanDefinitionWriter.visitPostConstructMethod(
            declaringType,
            methodElement,
            requiresReflection,
            visitorContext
        ));
    }

    @Override
    public void visitPreDestroyMethod(
        TypedElement declaringType,
        MethodElement methodElement,
        boolean requiresReflection,
        VisitorContext visitorContext) {
        deferredInjectionPoints.add(() ->
            proxyBeanDefinitionWriter.visitPreDestroyMethod(
                declaringType,
                methodElement,
                requiresReflection,
                visitorContext)
        );
    }

    @Override
    public void visitMethodInjectionPoint(TypedElement beanType,
                                          MethodElement methodElement,
                                          boolean requiresReflection,
                                          VisitorContext visitorContext) {
        deferredInjectionPoints.add(() ->
            proxyBeanDefinitionWriter.visitMethodInjectionPoint(
                beanType,
                methodElement,
                requiresReflection,
                visitorContext)
        );
    }

    @Override
    public int visitExecutableMethod(
        TypedElement declaringBean,
        MethodElement methodElement,
        VisitorContext visitorContext) {
        deferredInjectionPoints.add(() ->
            proxyBeanDefinitionWriter.visitExecutableMethod(
                declaringBean,
                methodElement,
                visitorContext
            )
        );
        return -1;
    }

    @Override
    public void visitFieldInjectionPoint(
        TypedElement declaringType,
        FieldElement fieldType,
        boolean requiresReflection, VisitorContext visitorContext) {
        deferredInjectionPoints.add(() ->
            proxyBeanDefinitionWriter.visitFieldInjectionPoint(
                declaringType,
                fieldType,
                requiresReflection,
                visitorContext
            )
        );
    }

    @Override
    public void visitAnnotationMemberPropertyInjectionPoint(TypedElement annotationMemberBeanType,
                                                            String annotationMemberProperty,
                                                            String requiredValue,
                                                            String notEqualsValue) {
        deferredInjectionPoints.add(() ->
            proxyBeanDefinitionWriter.visitAnnotationMemberPropertyInjectionPoint(
                annotationMemberBeanType,
                annotationMemberProperty,
                requiredValue,
                notEqualsValue));
    }

    @Override
    public void visitFieldValue(
        TypedElement declaringType,
        FieldElement fieldType,
        boolean requiresReflection, boolean isOptional) {
        deferredInjectionPoints.add(() ->
            proxyBeanDefinitionWriter.visitFieldValue(
                declaringType,
                fieldType, requiresReflection, isOptional
            )
        );
    }

    @Override
    public String getPackageName() {
        return proxyBeanDefinitionWriter.getPackageName();
    }

    @Override
    public String getBeanSimpleName() {
        return proxyBeanDefinitionWriter.getBeanSimpleName();
    }

    @Override
    public AnnotationMetadata getAnnotationMetadata() {
        return proxyBeanDefinitionWriter.getAnnotationMetadata();
    }

    @Override
    public void visitConfigBuilderField(ClassElement type, String field, AnnotationMetadata annotationMetadata, ConfigurationMetadataBuilder metadataBuilder, boolean isInterface) {
        proxyBeanDefinitionWriter.visitConfigBuilderField(type, field, annotationMetadata, metadataBuilder, isInterface);
    }

    @Override
    public void visitConfigBuilderMethod(ClassElement type, String methodName, AnnotationMetadata annotationMetadata, ConfigurationMetadataBuilder metadataBuilder, boolean isInterface) {
        proxyBeanDefinitionWriter.visitConfigBuilderMethod(type, methodName, annotationMetadata, metadataBuilder, isInterface);
    }

    @Override
    public void visitConfigBuilderMethod(String propertyName, ClassElement returnType, String methodName, ClassElement paramType, Map<String, ClassElement> generics, String propertyPath) {
        proxyBeanDefinitionWriter.visitConfigBuilderMethod(propertyName, returnType, methodName, paramType, generics, propertyPath);
    }

    @Override
    public void visitConfigBuilderDurationMethod(String propertyName, ClassElement returnType, String methodName, String propertyPath) {
        proxyBeanDefinitionWriter.visitConfigBuilderDurationMethod(propertyName, returnType, methodName, propertyPath);
    }

    @Override
    public void visitConfigBuilderEnd() {
        proxyBeanDefinitionWriter.visitConfigBuilderEnd();
    }

    @Override
    public void setRequiresMethodProcessing(boolean shouldPreProcess) {
        proxyBeanDefinitionWriter.setRequiresMethodProcessing(shouldPreProcess);
    }

    @Override
    public void visitTypeArguments(Map<String, Map<String, ClassElement>> typeArguments) {
        proxyBeanDefinitionWriter.visitTypeArguments(typeArguments);
    }

    @Override
    public boolean requiresMethodProcessing() {
        return proxyBeanDefinitionWriter.requiresMethodProcessing() || (parentWriter != null && parentWriter.requiresMethodProcessing());
    }

    @Override
    public String getProxiedTypeName() {
        return targetClassFullName;
    }

    @Override
    public String getProxiedBeanDefinitionName() {
        return parentWriter != null ? parentWriter.getBeanDefinitionName() : null;
    }

    /**
     * visitInterceptorTypes.
     *
     * @param interceptorBinding the interceptor binding
     */
    public void visitInterceptorBinding(AnnotationValue<?>... interceptorBinding) {
        if (interceptorBinding != null) {
            for (AnnotationValue<?> annotationValue : interceptorBinding) {
                annotationValue.stringValue().ifPresent(annName ->
                    this.interceptorBinding.add(annotationValue)
                );
            }
        }
    }

    private Set<AnnotationValue<?>> toInterceptorBindingMap(AnnotationValue<?>[] interceptorBinding) {
        return new LinkedHashSet<>(Arrays.asList(interceptorBinding));
    }

    private void readUnlock(GeneratorAdapter interceptedTargetVisitor) {
        invokeMethodOnLock(interceptedTargetVisitor, FIELD_READ_LOCK, Method.getMethod("void unlock()"));
    }

    private void readLock(GeneratorAdapter interceptedTargetVisitor) {
        invokeMethodOnLock(interceptedTargetVisitor, FIELD_READ_LOCK, Method.getMethod("void lock()"));
    }

    private void writeUnlock(GeneratorAdapter interceptedTargetVisitor) {
        invokeMethodOnLock(interceptedTargetVisitor, FIELD_WRITE_LOCK, Method.getMethod("void unlock()"));
    }

    private void writeLock(GeneratorAdapter interceptedTargetVisitor) {
        invokeMethodOnLock(interceptedTargetVisitor, FIELD_WRITE_LOCK, Method.getMethod("void lock()"));
    }

    private void invokeMethodOnLock(GeneratorAdapter interceptedTargetVisitor, String field, Method method) {
        interceptedTargetVisitor.loadThis();
        interceptedTargetVisitor.getField(proxyType, field, TYPE_LOCK);
        interceptedTargetVisitor.invokeInterface(TYPE_LOCK, method);
    }

    private void writeWithQualifierMethod(ClassWriter proxyClassWriter) {
        GeneratorAdapter withQualifierMethod = startPublicMethod(proxyClassWriter, "$withBeanQualifier", void.class.getName(), Qualifier.class.getName());

        withQualifierMethod.loadThis();
        withQualifierMethod.loadArg(0);
        withQualifierMethod.putField(proxyType, FIELD_BEAN_QUALIFIER, Type.getType(Qualifier.class));
        withQualifierMethod.visitInsn(RETURN);
        withQualifierMethod.visitEnd();
        withQualifierMethod.visitMaxs(1, 1);
    }

    private void writeSwapMethod(ClassWriter proxyClassWriter, Type targetType) {
        GeneratorAdapter swapGenerator = startPublicMethod(proxyClassWriter, "swap", targetType.getClassName(), targetType.getClassName());
        var l0 = new Label();
        var l1 = new Label();
        var l2 = new Label();
        swapGenerator.visitTryCatchBlock(
            l0,
            l1,
            l2,
            null
        );
        // add write lock
        writeLock(swapGenerator);
        swapGenerator.visitLabel(l0);
        swapGenerator.loadThis();
        swapGenerator.getField(proxyType, FIELD_TARGET, targetType);
        // release write lock
        int localRef = swapGenerator.newLocal(targetType);
        swapGenerator.storeLocal(localRef);

        // assign the new value
        swapGenerator.loadThis();
        swapGenerator.visitVarInsn(ALOAD, 1);
        swapGenerator.putField(proxyType, FIELD_TARGET, targetType);

        swapGenerator.visitLabel(l1);
        writeUnlock(swapGenerator);
        swapGenerator.loadLocal(localRef);
        swapGenerator.returnValue();
        swapGenerator.visitLabel(l2);
        // release write lock in finally
        int var = swapGenerator.newLocal(targetType);
        swapGenerator.storeLocal(var);
        writeUnlock(swapGenerator);
        swapGenerator.loadLocal(var);
        swapGenerator.throwException();

        swapGenerator.visitMaxs(2, MAX_LOCALS);
        swapGenerator.visitEnd();
    }

    private void writeInterceptedTargetMethod(ClassWriter proxyClassWriter, Type targetType) {
        // add interceptedTarget() method
        GeneratorAdapter interceptedTargetVisitor = startPublicMethod(
            proxyClassWriter,
            "interceptedTarget",
            Object.class.getName()
        );

        if (lazy) {
            if (cacheLazyTarget) {
                // Object local = this.$target;
                int targetLocal = interceptedTargetVisitor.newLocal(targetType);
                interceptedTargetVisitor.loadThis();
                interceptedTargetVisitor.getField(proxyType, FIELD_TARGET, targetType);
                interceptedTargetVisitor.storeLocal(targetLocal, targetType);
                // if (local == null) {
                interceptedTargetVisitor.loadLocal(targetLocal, targetType);
                var returnLabel = new Label();
                interceptedTargetVisitor.ifNonNull(returnLabel);
                // synchronized (this) {
                var synchronizationEnd = new Label();
                interceptedTargetVisitor.loadThis();
                interceptedTargetVisitor.monitorEnter();

                var tryLabel = new Label();
                var catchLabel = new Label();

                interceptedTargetVisitor.visitTryCatchBlock(tryLabel, returnLabel, catchLabel, null);

                // Try body
                interceptedTargetVisitor.visitLabel(tryLabel);
                // local = this.$target
                interceptedTargetVisitor.loadThis();
                interceptedTargetVisitor.getField(proxyType, FIELD_TARGET, targetType);
                interceptedTargetVisitor.storeLocal(targetLocal, targetType);
                // if (local == null) {
                interceptedTargetVisitor.loadLocal(targetLocal, targetType);
                interceptedTargetVisitor.ifNonNull(synchronizationEnd);
                // this.$target =
                interceptedTargetVisitor.loadThis();
                pushResolveLazyProxyTargetBean(interceptedTargetVisitor, targetType);
                interceptedTargetVisitor.putField(proxyType, FIELD_TARGET, targetType);
                // cleanup this.$beanResolutionContext
                interceptedTargetVisitor.loadThis();
                interceptedTargetVisitor.push((String) null);
                interceptedTargetVisitor.putField(proxyType, FIELD_BEAN_RESOLUTION_CONTEXT, Type.getType(BeanResolutionContext.class));
                interceptedTargetVisitor.goTo(synchronizationEnd);

                // Catch body
                interceptedTargetVisitor.visitLabel(catchLabel);
                interceptedTargetVisitor.loadThis();
                interceptedTargetVisitor.monitorExit();
                interceptedTargetVisitor.throwException();

                // Synchronization end label
                interceptedTargetVisitor.visitLabel(synchronizationEnd);
                interceptedTargetVisitor.loadThis();
                interceptedTargetVisitor.monitorExit();
                interceptedTargetVisitor.goTo(returnLabel);

                // Return label just loads and returns value
                interceptedTargetVisitor.visitLabel(returnLabel);
                interceptedTargetVisitor.loadThis();
                interceptedTargetVisitor.getField(proxyType, FIELD_TARGET, targetType);
                interceptedTargetVisitor.returnValue();
            } else {
                pushResolveLazyProxyTargetBean(interceptedTargetVisitor, targetType);
                interceptedTargetVisitor.returnValue();
            }
        } else {
            int localRef = -1;
            Label l1 = null;
            Label l2 = null;
            if (hotswap) {
                var l0 = new Label();
                l1 = new Label();
                l2 = new Label();
                interceptedTargetVisitor.visitTryCatchBlock(
                    l0,
                    l1,
                    l2,
                    null
                );
                // add read lock
                readLock(interceptedTargetVisitor);
                interceptedTargetVisitor.visitLabel(l0);
            }
            interceptedTargetVisitor.loadThis();
            interceptedTargetVisitor.getField(proxyType, FIELD_TARGET, targetType);
            if (hotswap) {
                // release read lock
                localRef = interceptedTargetVisitor.newLocal(targetType);
                interceptedTargetVisitor.storeLocal(localRef);
                interceptedTargetVisitor.visitLabel(l1);
                readUnlock(interceptedTargetVisitor);
                interceptedTargetVisitor.loadLocal(localRef);
            }
            interceptedTargetVisitor.returnValue();
            if (localRef > -1) {
                interceptedTargetVisitor.visitLabel(l2);
                // release read lock in finally
                int var = interceptedTargetVisitor.newLocal(targetType);
                interceptedTargetVisitor.storeLocal(var);
                readUnlock(interceptedTargetVisitor);
                interceptedTargetVisitor.loadLocal(var);
                interceptedTargetVisitor.throwException();
            }
        }

        interceptedTargetVisitor.visitMaxs(1, 2);
        interceptedTargetVisitor.visitEnd();
    }

    private void writeHasCachedInterceptedTargetMethod(ClassWriter proxyClassWriter, Type targetType) {
        GeneratorAdapter methodVisitor = startPublicMethod(proxyClassWriter, METHOD_HAS_CACHED_INTERCEPTED_METHOD);
        methodVisitor.loadThis();
        methodVisitor.getField(proxyType, FIELD_TARGET, targetType);
        var notNull = new Label();
        methodVisitor.ifNonNull(notNull);
        methodVisitor.push(false);
        methodVisitor.returnValue();
        methodVisitor.visitLabel(notNull);
        methodVisitor.push(true);
        methodVisitor.returnValue();
        methodVisitor.visitMaxs(1, 2);
        methodVisitor.visitEnd();
    }

    private void pushResolveInterceptorsCall(GeneratorAdapter proxyConstructorGenerator, int i, boolean isIntroduction) {
        // The following will initialize the array of interceptor instances
        // e.g. this.interceptors[0] = InterceptorChain.resolveAroundInterceptors(beanContext, proxyMethods[0], interceptors);
        proxyConstructorGenerator.loadThis();
        proxyConstructorGenerator.getField(proxyType, FIELD_INTERCEPTORS, FIELD_TYPE_INTERCEPTORS);
        proxyConstructorGenerator.push(i);

        // First argument. The interceptor registry
        proxyConstructorGenerator.loadArg(interceptorRegistryArgumentIndex);

        // Second argument i.e. proxyMethods[0]
        proxyConstructorGenerator.loadThis();
        proxyConstructorGenerator.getField(proxyType, FIELD_PROXY_METHODS, FIELD_TYPE_PROXY_METHODS);
        proxyConstructorGenerator.push(i);
        proxyConstructorGenerator.visitInsn(AALOAD);

        // Third argument i.e. interceptors
        proxyConstructorGenerator.loadArg(interceptorsListArgumentIndex);
        if (isIntroduction) {
            proxyConstructorGenerator.invokeStatic(TYPE_INTERCEPTOR_CHAIN, Method.getMethod(RESOLVE_INTRODUCTION_INTERCEPTORS_METHOD));
        } else {
            proxyConstructorGenerator.invokeStatic(TYPE_INTERCEPTOR_CHAIN, Method.getMethod(RESOLVE_AROUND_INTERCEPTORS_METHOD));
        }
        proxyConstructorGenerator.visitInsn(AASTORE);
    }

    private void processAlreadyVisitedMethods(BeanDefinitionWriter parent) {
        final List<BeanDefinitionWriter.MethodVisitData> postConstructMethodVisits = parent.getPostConstructMethodVisits();
        for (BeanDefinitionWriter.MethodVisitData methodVisit : postConstructMethodVisits) {
            visitPostConstructMethod(
                methodVisit.getBeanType(),
                methodVisit.getMethodElement(),
                methodVisit.isRequiresReflection(),
                visitorContext
            );
        }
    }

    /**
     * Method Reference class with names and a list of argument types. Used as the targets.
     */
    private static final class MethodRef {
        int methodIndex;
        private final String name;
        private final List<ClassElement> argumentTypes;
        private final List<ClassElement> genericArgumentTypes;
        private final Type returnType;
        private final List<String> rawTypes;

        public MethodRef(String name, List<ParameterElement> parameterElements, Type returnType) {
            this.name = name;
            this.argumentTypes = parameterElements.stream().map(ParameterElement::getType).toList();
            this.genericArgumentTypes = parameterElements.stream().map(ParameterElement::getGenericType).toList();
            this.rawTypes = this.argumentTypes.stream().map(AopProxyWriter::toTypeString).toList();
            this.returnType = returnType;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            MethodRef methodRef = (MethodRef) o;
            return Objects.equals(name, methodRef.name) &&
                Objects.equals(rawTypes, methodRef.rawTypes) &&
                Objects.equals(returnType, methodRef.returnType);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, rawTypes, returnType);
        }
    }
}
