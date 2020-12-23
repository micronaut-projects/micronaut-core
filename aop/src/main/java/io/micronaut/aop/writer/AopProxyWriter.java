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

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import io.micronaut.aop.HotSwappableInterceptedProxy;
import io.micronaut.aop.Intercepted;
import io.micronaut.aop.InterceptedProxy;
import io.micronaut.aop.Interceptor;
import io.micronaut.aop.Introduced;
import io.micronaut.aop.chain.InterceptorChain;
import io.micronaut.aop.chain.MethodInterceptorChain;
import io.micronaut.context.BeanContext;
import io.micronaut.context.BeanLocator;
import io.micronaut.context.ExecutionHandleLocator;
import io.micronaut.context.Qualifier;
import io.micronaut.core.annotation.AnnotationClassValue;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationValueBuilder;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.core.reflect.ReflectionUtils;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.core.value.OptionalValues;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.inject.ProxyBeanDefinition;
import io.micronaut.inject.annotation.DefaultAnnotationMetadata;
import io.micronaut.inject.ast.*;
import io.micronaut.inject.configuration.ConfigurationMetadataBuilder;
import io.micronaut.inject.writer.*;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Constructor;
import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * A class that generates AOP proxy classes at compile time.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class AopProxyWriter extends AbstractClassFileWriter implements ProxyingBeanDefinitionVisitor {
    public static final int HASHCODE = 31;
    public static final int MAX_LOCALS = 3;

    public static final Method METHOD_GET_PROXY_TARGET = Method.getMethod(
            ReflectionUtils.getRequiredInternalMethod(
                    ExecutionHandleLocator.class,
                    "getProxyTargetMethod",
                    Class.class,
                    Qualifier.class,
                    String.class,
                    Class[].class
            )
    );
    public static final Method METHOD_GET_PROXY_TARGET_BEAN = Method.getMethod(ReflectionUtils.getRequiredInternalMethod(
            BeanLocator.class,
            "getProxyTargetBean",
            Class.class,
            Qualifier.class
    ));

    public static final Type FIELD_TYPE_INTERCEPTORS = Type.getType(Interceptor[][].class);
    public static final Type TYPE_INTERCEPTOR_CHAIN = Type.getType(InterceptorChain.class);
    public static final Type TYPE_METHOD_INTERCEPTOR_CHAIN = Type.getType(MethodInterceptorChain.class);
    public static final String FIELD_TARGET = "$target";
    public static final String FIELD_READ_WRITE_LOCK = "$target_rwl";
    public static final Type TYPE_READ_WRITE_LOCK = Type.getType(ReentrantReadWriteLock.class);
    public static final String FIELD_READ_LOCK = "$target_rl";
    public static final String FIELD_WRITE_LOCK = "$target_wl";
    public static final Type TYPE_LOCK = Type.getType(Lock.class);
    public static final Type TYPE_BEAN_LOCATOR = Type.getType(BeanLocator.class);
    public static final String METHOD_RESOLVE_TARGET = "$resolveTarget";

    private static final Method METHOD_PROXY_TARGET_TYPE = Method.getMethod(ReflectionUtils.getRequiredInternalMethod(ProxyBeanDefinition.class, "getTargetDefinitionType"));

    private static final Method METHOD_PROXY_TARGET_CLASS = Method.getMethod(ReflectionUtils.getRequiredInternalMethod(ProxyBeanDefinition.class, "getTargetType"));

    private static final java.lang.reflect.Method RESOLVE_INTRODUCTION_INTERCEPTORS_METHOD = ReflectionUtils.getRequiredInternalMethod(InterceptorChain.class, "resolveIntroductionInterceptors", BeanContext.class, ExecutableMethod.class, Interceptor[].class);

    private static final java.lang.reflect.Method RESOLVE_AROUND_INTERCEPTORS_METHOD = ReflectionUtils.getRequiredInternalMethod(InterceptorChain.class, "resolveAroundInterceptors", BeanContext.class, ExecutableMethod.class, Interceptor[].class);

    private static final Constructor CONSTRUCTOR_METHOD_INTERCEPTOR_CHAIN = ReflectionUtils.findConstructor(MethodInterceptorChain.class, Interceptor[].class, Object.class, ExecutableMethod.class, Object[].class).orElseThrow(() ->
            new IllegalStateException("new MethodInterceptorChain(..) constructor not found. Incompatible version of Micronaut?")
    );

    private static final Constructor CONSTRUCTOR_METHOD_INTERCEPTOR_CHAIN_NO_PARAMS = ReflectionUtils.findConstructor(MethodInterceptorChain.class, Interceptor[].class, Object.class, ExecutableMethod.class).orElseThrow(() ->
            new IllegalStateException("new MethodInterceptorChain(..) constructor not found. Incompatible version of Micronaut?")
    );

    private static final String FIELD_INTERCEPTORS = "$interceptors";
    private static final String FIELD_BEAN_LOCATOR = "$beanLocator";
    private static final String FIELD_BEAN_QUALIFIER = "$beanQualifier";
    private static final String FIELD_PROXY_METHODS = "$proxyMethods";
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
    private final Set<Object> interceptorTypes;
    private final Set<Object> interfaceTypes;
    private final Type proxyType;
    private final boolean hotswap;
    private final boolean lazy;
    private final boolean isInterface;
    private final BeanDefinitionWriter parentWriter;
    private final boolean isIntroduction;
    private final boolean implementInterface;
    private boolean isProxyTarget;

    private MethodVisitor constructorWriter;
    private final List<ExecutableMethodWriter> proxiedMethods = new ArrayList<>();
    private final Set<MethodRef> proxiedMethodsRefSet = new HashSet<>();
    private final List<MethodRef> proxyTargetMethods = new ArrayList<>();
    private int proxyMethodCount = 0;
    private GeneratorAdapter constructorGenerator;
    private int interceptorArgumentIndex;
    private int beanContextArgumentIndex = -1;
    private int qualifierIndex;
    private Map<String, ParameterElement> constructorArgumentTypes;
    private Map<String, ClassElement> constructorGenericTypes;
    private Map<String, ParameterElement> constructorNewArgumentTypes;
    private final List<Runnable> deferredInjectionPoints = new ArrayList<>();
    private AnnotationMetadata constructorAnnotationMedata;
    private boolean constructorRequriesReflection;

    /**
     * <p>Constructs a new {@link AopProxyWriter} for the given parent {@link BeanDefinitionWriter} and starting interceptors types.</p>
     * <p>
     * <p>Additional {@link Interceptor} types can be added downstream with {@link #visitInterceptorTypes(Object...)}.</p>
     *
     * @param parent           The parent {@link BeanDefinitionWriter}
     * @param interceptorTypes The annotation types of the {@link Interceptor} instances to be injected
     */
    public AopProxyWriter(BeanDefinitionWriter parent,
                          Object... interceptorTypes) {
        this(parent, OptionalValues.empty(), interceptorTypes);
    }

    /**
     * <p>Constructs a new {@link AopProxyWriter} for the given parent {@link BeanDefinitionWriter} and starting interceptors types.</p>
     * <p>
     * <p>Additional {@link Interceptor} types can be added downstream with {@link #visitInterceptorTypes(Object...)}.</p>
     *
     * @param parent           The parent {@link BeanDefinitionWriter}
     * @param settings         optional setting
     * @param interceptorTypes The annotation types of the {@link Interceptor} instances to be injected
     */
    public AopProxyWriter(BeanDefinitionWriter parent,
                          OptionalValues<Boolean> settings,
                          Object... interceptorTypes) {
        super(parent.getOriginatingElements());
        this.isIntroduction = false;
        this.implementInterface = true;
        this.parentWriter = parent;
        this.isProxyTarget = settings.get(Interceptor.PROXY_TARGET).orElse(false) || parent.isInterface();
        this.hotswap = isProxyTarget && settings.get(Interceptor.HOTSWAP).orElse(false);
        this.lazy = isProxyTarget && settings.get(Interceptor.LAZY).orElse(false);
        this.isInterface = parent.isInterface();
        this.packageName = parent.getPackageName();
        this.targetClassShortName = parent.getBeanSimpleName();
        this.targetClassFullName = packageName + '.' + targetClassShortName;
        this.classWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);

        this.proxyFullName = parent.getBeanDefinitionName() + PROXY_SUFFIX;
        String proxyShortName = NameUtils.getSimpleName(proxyFullName);
        this.proxyInternalName = getInternalName(this.proxyFullName);
        this.proxyType = getTypeReference(proxyFullName);
        this.interceptorTypes = new HashSet<>(Arrays.asList(interceptorTypes));
        this.interfaceTypes = Collections.emptySet();
        this.proxyBeanDefinitionWriter = new BeanDefinitionWriter(
                NameUtils.getPackageName(proxyFullName),
                proxyShortName,
                isInterface,
                parent,
                parent.getAnnotationMetadata()
        );
        startClass(classWriter, getInternalName(proxyFullName), getTypeReference(targetClassFullName));
        processAlreadyVisitedMethods(parent);
        proxyBeanDefinitionWriter.setInterceptedType(targetClassFullName);
    }

    /**
     * Constructs a new {@link AopProxyWriter} for the purposes of writing {@link io.micronaut.aop.Introduction} advise.
     *
     * @param packageName        The package name
     * @param className          The class name
     * @param isInterface        Is the target of the advise an interface
     * @param originatingElement The originating element
     * @param annotationMetadata The annotation metadata
     * @param interfaceTypes     The additional interfaces to implement
     * @param interceptorTypes   The interceptor types
     */
    public AopProxyWriter(String packageName,
                          String className,
                          boolean isInterface,
                          Element originatingElement,
                          AnnotationMetadata annotationMetadata,
                          Object[] interfaceTypes,
                          Object... interceptorTypes) {
        this(packageName, className, isInterface, true, originatingElement, annotationMetadata, interfaceTypes, interceptorTypes);
    }

    /**
     * Constructs a new {@link AopProxyWriter} for the purposes of writing {@link io.micronaut.aop.Introduction} advise.
     *
     * @param packageName        The package name
     * @param className          The class name
     * @param isInterface        Is the target of the advise an interface
     * @param implementInterface Whether the interface should be implemented. If false the {@code interfaceTypes} argument should contain at least one entry
     * @param originatingElement The originating elements
     * @param annotationMetadata The annotation metadata
     * @param interfaceTypes     The additional interfaces to implement
     * @param interceptorTypes   The interceptor types
     */
    public AopProxyWriter(String packageName,
                          String className,
                          boolean isInterface,
                          boolean implementInterface,
                          Element originatingElement,
                          AnnotationMetadata annotationMetadata,
                          Object[] interfaceTypes,
                          Object... interceptorTypes) {
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
        this.targetClassShortName = className;
        this.targetClassFullName = packageName + '.' + targetClassShortName;
        this.parentWriter = null;
        this.proxyFullName = targetClassFullName + BeanDefinitionVisitor.PROXY_SUFFIX;
        this.proxyInternalName = getInternalName(this.proxyFullName);
        this.proxyType = getTypeReference(proxyFullName);
        this.interceptorTypes = new HashSet<>(Arrays.asList(interceptorTypes));
        this.interfaceTypes = interfaceTypes != null ? new HashSet<>(Arrays.asList(interfaceTypes)) : Collections.emptySet();
        this.classWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        String proxyShortName = NameUtils.getSimpleName(proxyFullName);
        this.proxyBeanDefinitionWriter = new BeanDefinitionWriter(
                NameUtils.getPackageName(proxyFullName),
                proxyShortName,
                isInterface,
                this,
                annotationMetadata
        );
        if (isInterface) {
            if (implementInterface) {
                proxyBeanDefinitionWriter.setInterceptedType(targetClassFullName);
            }
        } else {
            proxyBeanDefinitionWriter.setInterceptedType(targetClassFullName);
        }
        startClass(classWriter, proxyInternalName, getTypeReference(targetClassFullName));
    }

    /**
     * Is the target bean being proxied.
     *
     * @return True if the target bean is being proxied
     */
    public boolean isProxyTarget() {
        return isProxyTarget;
    }

    @Override
    protected void startClass(ClassVisitor classWriter, String className, Type superType) {
        String[] interfaces = getImplementedInterfaceInternalNames();
        classWriter.visit(V1_8, ACC_SYNTHETIC, className, null, !isInterface ? superType.getInternalName() : null, interfaces);

        classWriter.visitAnnotation(TYPE_GENERATED.getDescriptor(), false);

        classWriter.visitField(ACC_FINAL | ACC_PRIVATE, FIELD_INTERCEPTORS, FIELD_TYPE_INTERCEPTORS.getDescriptor(), null, null);
        classWriter.visitField(ACC_FINAL | ACC_PRIVATE, FIELD_PROXY_METHODS, FIELD_TYPE_PROXY_METHODS.getDescriptor(), null, null);
    }

    private String[] getImplementedInterfaceInternalNames() {
        return interfaceTypes.stream().map(o -> getTypeReference(o).getInternalName()).toArray(String[]::new);
    }

    @Override
    @Deprecated
    public Element getOriginatingElement() {
        return proxyBeanDefinitionWriter.getOriginatingElement();
    }

    @Override
    public void visitBeanDefinitionConstructor(AnnotationMetadata annotationMetadata,
                                               boolean requiresReflection) {
        visitBeanDefinitionConstructor(
                annotationMetadata,
                requiresReflection,
                Collections.emptyMap(),
                Collections.emptyMap(),
                Collections.emptyMap()
        );
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
     * Visits a constructor with arguments. Either this method or {@link #visitBeanDefinitionConstructor(AnnotationMetadata, boolean)}  should be called at least once
     *
     * @param argumentTypes              The argument names and types. Should be an ordered map should as {@link LinkedHashMap}
     * @param argumentAnnotationMetadata The argument names and metadata
     * @param genericTypes               The argument names and generic types. Should be an ordered map should as {@link LinkedHashMap}
     */
    @Override
    public void visitBeanDefinitionConstructor(
            AnnotationMetadata annotationMetadata,
            boolean requiresReflection,
            Map<String, ParameterElement> argumentTypes,
            Map<String, AnnotationMetadata> argumentAnnotationMetadata,
            Map<String, ClassElement> genericTypes) {
        this.constructorAnnotationMedata = annotationMetadata;
        this.constructorRequriesReflection = requiresReflection;
        this.constructorArgumentTypes = argumentTypes;
        this.constructorGenericTypes = genericTypes;
        this.constructorNewArgumentTypes = new LinkedHashMap<>(argumentTypes);
        this.beanContextArgumentIndex = argumentTypes.size();
        constructorNewArgumentTypes.put("$beanContext", ParameterElement.of(BeanContext.class, "$beanContext"));
        this.qualifierIndex = constructorNewArgumentTypes.size();
        constructorNewArgumentTypes.put("$qualifier", ParameterElement.of(Qualifier.class, "$qualifier"));
        this.interceptorArgumentIndex = constructorNewArgumentTypes.size();
        constructorNewArgumentTypes.put("$interceptors", ParameterElement.of(Interceptor[].class, "$interceptors"));

    }

    @NonNull
    @Override
    public String getBeanDefinitionReferenceClassName() {
        return proxyBeanDefinitionWriter.getBeanDefinitionReferenceClassName();
    }

    /**
     * Visit a abstract method that is to be implemented.
     *
     * @param declaringBean              The declaring bean of the method.
     * @param methodElement              The method element
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
     * @param beanType      The bean type.
     * @param methodElement The method element
     **/
    public void visitAroundMethod(TypedElement beanType,
                                  MethodElement methodElement) {

        String methodName = methodElement.getName();
        ClassElement returnType = methodElement.isSuspend() ? ClassElement.of(Object.class) : methodElement.getReturnType();
        List<ParameterElement> argumentTypeList = Arrays.asList(methodElement.getSuspendParameters());
        int argumentCount = argumentTypeList.size();
        Type returnTypeObject = getTypeReference(returnType);
        boolean isPrimitive = returnType.isPrimitive();
        boolean isVoidReturn = isPrimitive && returnTypeObject.equals(Type.VOID_TYPE);
        final Type declaringTypeReference = getTypeReference(beanType);
        MethodRef methodKey = new MethodRef(methodName, argumentTypeList, returnTypeObject);

        if (!proxiedMethodsRefSet.contains(methodKey)) {
            int index = proxyMethodCount++;

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
                    GeneratorAdapter bridgeGenerator = new GeneratorAdapter(bridgeWriter, ACC_SYNTHETIC, interceptedProxyBridgeMethodName, bridgeDesc);
                    bridgeGenerator.loadThis();
                    for (int i = 0; i < argumentTypeList.size(); i++) {
                        bridgeGenerator.loadArg(i);
                    }
                    String desc = getMethodDescriptor(returnType, argumentTypeList);
                    bridgeWriter.visitMethodInsn(INVOKESPECIAL, declaringTypeReference.getInternalName(), methodName, desc, this.isInterface && methodElement.isDefault());
                    pushReturnValue(bridgeWriter, returnType);
                    bridgeWriter.visitMaxs(DEFAULT_MAX_STACK, 1);
                    bridgeWriter.visitEnd();
                }
            }

            BeanDefinitionWriter beanDefinitionWriter = parentWriter == null ? proxyBeanDefinitionWriter : parentWriter;
            ExecutableMethodWriter executableMethodWriter = beanDefinitionWriter.visitExecutableMethod(
                    beanType,
                    methodElement,
                    interceptedProxyClassName,
                    interceptedProxyBridgeMethodName
            );

            proxiedMethods.add(executableMethodWriter);
            proxiedMethodsRefSet.add(methodKey);
            proxyTargetMethods.add(methodKey);

            buildMethodOverride(returnType, methodName, index, argumentTypeList, argumentCount, isVoidReturn);
        }
    }

    private String buildMethodOverride(TypedElement returnType, String methodName, int index, List<ParameterElement> argumentTypeList, int argumentCount, boolean isVoidReturn) {
        // override the original method
        String desc = getMethodDescriptor(returnType, argumentTypeList);
        MethodVisitor overridden = classWriter.visitMethod(ACC_PUBLIC, methodName, desc, null, null);
        GeneratorAdapter overriddenMethodGenerator = new GeneratorAdapter(overridden, ACC_PUBLIC, methodName, desc);

        // store the proxy method instance in a local variable
        // ie ExecutableMethod executableMethod = this.proxyMethods[0];
        overriddenMethodGenerator.loadThis();
        overriddenMethodGenerator.getField(proxyType, FIELD_PROXY_METHODS, FIELD_TYPE_PROXY_METHODS);
        overriddenMethodGenerator.push(index);
        overriddenMethodGenerator.visitInsn(AALOAD);
        int methodProxyVar = overriddenMethodGenerator.newLocal(EXECUTABLE_METHOD_TYPE);
        overriddenMethodGenerator.storeLocal(methodProxyVar);

        // store the interceptors in a local variable
        // ie Interceptor[] interceptors = this.interceptors[0];
        overriddenMethodGenerator.loadThis();
        overriddenMethodGenerator.getField(proxyType, FIELD_INTERCEPTORS, FIELD_TYPE_INTERCEPTORS);
        overriddenMethodGenerator.push(index);
        overriddenMethodGenerator.visitInsn(AALOAD);
        int interceptorsLocalVar = overriddenMethodGenerator.newLocal(INTERCEPTOR_ARRAY_TYPE);
        overriddenMethodGenerator.storeLocal(interceptorsLocalVar);

        // instantiate the MethodInterceptorChain
        // ie InterceptorChain chain = new MethodInterceptorChain(interceptors, this, executableMethod, name);
        overriddenMethodGenerator.newInstance(TYPE_METHOD_INTERCEPTOR_CHAIN);
        overriddenMethodGenerator.dup();

        // first argument: interceptors
        overriddenMethodGenerator.loadLocal(interceptorsLocalVar);

        // second argument: this or target
        overriddenMethodGenerator.loadThis();
        if (isProxyTarget) {
            if (hotswap || lazy) {
                overriddenMethodGenerator.invokeInterface(Type.getType(InterceptedProxy.class), Method.getMethod("java.lang.Object interceptedTarget()"));
            } else {
                overriddenMethodGenerator.getField(proxyType, FIELD_TARGET, getTypeReference(targetClassFullName));
            }
        }

        // third argument: the executable method
        overriddenMethodGenerator.loadLocal(methodProxyVar);

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

        int chainVar = overriddenMethodGenerator.newLocal(TYPE_METHOD_INTERCEPTOR_CHAIN);
        overriddenMethodGenerator.storeLocal(chainVar);
        overriddenMethodGenerator.loadLocal(chainVar);

        overriddenMethodGenerator.visitMethodInsn(INVOKEVIRTUAL, TYPE_INTERCEPTOR_CHAIN.getInternalName(), "proceed", getMethodDescriptor(Object.class.getName()), false);
        if (isVoidReturn) {
            returnVoid(overriddenMethodGenerator);
        } else {
            pushCastToType(overriddenMethodGenerator, returnType);
            pushReturnValue(overriddenMethodGenerator, returnType);
        }
        overriddenMethodGenerator.visitMaxs(DEFAULT_MAX_STACK, chainVar);
        overriddenMethodGenerator.visitEnd();
        return desc;
    }

    /**
     * Finalizes the proxy. This method should be called before writing the proxy to disk with {@link #writeTo(File)}
     */
    @Override
    public void visitBeanDefinitionEnd() {
        if (constructorArgumentTypes == null) {
            throw new IllegalStateException("The method visitBeanDefinitionConstructor(..) should be called at least once");
        }
        Type[] interceptorTypes = getObjectTypes(this.interceptorTypes);
        this.constructorNewArgumentTypes.get("$interceptors").annotate(io.micronaut.context.annotation.Type.class, builder -> {
            AnnotationClassValue<?>[] types = Arrays.stream(interceptorTypes).map(t -> new AnnotationClassValue<>(t.getClassName())).toArray(AnnotationClassValue[]::new);
            builder.values(types);
        });
        this.constructorNewArgumentTypes.get("$qualifier").annotate("javax.annotation.Nullable");

        Map<String, AnnotationMetadata> constructArgumentMetadata = new LinkedHashMap<>(constructorArgumentTypes.size());
        constructorArgumentTypes.forEach((name, p) -> constructArgumentMetadata.put(name, p.getAnnotationMetadata()));
        String constructorDescriptor = getConstructorDescriptor(constructorNewArgumentTypes.values());
        ClassWriter proxyClassWriter = this.classWriter;
        this.constructorWriter = proxyClassWriter.visitMethod(
                ACC_PUBLIC,
                CONSTRUCTOR_NAME,
                constructorDescriptor,
                null,
                null);

        // Add the interceptor @Type(..) annotation
        AnnotationVisitor interceptorTypeAnn = constructorWriter.visitParameterAnnotation(
                interceptorArgumentIndex, Type.getDescriptor(io.micronaut.context.annotation.Type.class), true
        ).visitArray("value");
        for (Type interceptorType : interceptorTypes) {
            interceptorTypeAnn.visit(null, interceptorType);
        }
        interceptorTypeAnn.visitEnd();

        this.constructorGenerator = new GeneratorAdapter(constructorWriter, Opcodes.ACC_PUBLIC, CONSTRUCTOR_NAME, constructorDescriptor);
        GeneratorAdapter proxyConstructorGenerator = this.constructorGenerator;

        proxyConstructorGenerator.loadThis();
        if (isInterface) {
            proxyConstructorGenerator.invokeConstructor(TYPE_OBJECT, METHOD_DEFAULT_CONSTRUCTOR);
        } else {
            Collection<ParameterElement> existingArguments = constructorArgumentTypes.values();
            for (int i = 0; i < existingArguments.size(); i++) {
                proxyConstructorGenerator.loadArg(i);
            }
            String superConstructorDescriptor = getConstructorDescriptor(existingArguments);
            proxyConstructorGenerator.invokeConstructor(getTypeReference(targetClassFullName), new Method(CONSTRUCTOR_NAME, superConstructorDescriptor));
        }

        proxyBeanDefinitionWriter.visitBeanDefinitionConstructor(
                constructorAnnotationMedata,
                constructorRequriesReflection,
                constructorNewArgumentTypes,
                constructArgumentMetadata,
                constructorGenericTypes
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
            targetDefinitionGenerator.push(getTypeReference(parentWriter.getBeanDefinitionName()));
            targetDefinitionGenerator.returnValue();


            targetTypeGenerator = new GeneratorAdapter(pcw.visitMethod(ACC_PUBLIC,
                    METHOD_PROXY_TARGET_CLASS.getName(),
                    METHOD_PROXY_TARGET_CLASS.getDescriptor(),
                    null, null

            ), ACC_PUBLIC, METHOD_PROXY_TARGET_CLASS.getName(), METHOD_PROXY_TARGET_CLASS.getDescriptor());
            targetTypeGenerator.loadThis();
            targetTypeGenerator.push(getTypeReference(parentWriter.getBeanTypeName()));
            targetTypeGenerator.returnValue();
        }

        Class<?> interceptedInterface = isIntroduction ? Introduced.class : Intercepted.class;
        Type targetType = getTypeReference(targetClassFullName);

        // add the $beanLocator field
        if (isProxyTarget) {
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

            if (lazy) {
                interceptedInterface = InterceptedProxy.class;
            } else {
                interceptedInterface = hotswap ? HotSwappableInterceptedProxy.class : InterceptedProxy.class;

                // add the $target field for the target bean
                int modifiers = hotswap ? ACC_PRIVATE : ACC_PRIVATE | ACC_FINAL;
                proxyClassWriter.visitField(
                        modifiers,
                        FIELD_TARGET,
                        targetType.getDescriptor(),
                        null,
                        null
                );
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

            Method resolveTargetMethodDesc = writeResolveTargetMethod(proxyClassWriter, targetType);

            if (!lazy) {
                proxyConstructorGenerator.loadThis();
                proxyConstructorGenerator.loadThis();
                proxyConstructorGenerator.invokeVirtual(proxyType, resolveTargetMethodDesc);
                proxyConstructorGenerator.putField(proxyType, FIELD_TARGET, targetType);
            }

            // Write the Object interceptedTarget() method
            writeInterceptedTargetMethod(proxyClassWriter, targetType, resolveTargetMethodDesc);

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
        proxyClassWriter.visit(V1_8, ACC_SYNTHETIC,
                proxyInternalName,
                null,
                isInterface ? TYPE_OBJECT.getInternalName() : getTypeReference(targetClassFullName).getInternalName(),
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
                    // Eg. this.$proxyMethods[0] = $PARENT_BEAN.getRequiredMethod("test", new Class[]{String.class});
                    proxyConstructorGenerator.loadThis();

                    // Step 1: dereference the array - this.$proxyMethods[0]
                    proxyConstructorGenerator.getField(proxyType, FIELD_PROXY_METHODS, FIELD_TYPE_PROXY_METHODS);
                    proxyConstructorGenerator.push(i);

                    // Step 2: lookup the Method instance from the declaring type
                    // context.getProxyTargetMethod("test", new Class[]{String.class});
                    proxyConstructorGenerator.loadArg(beanContextArgumentIndex);

                    proxyConstructorGenerator.push(targetType);
                    proxyConstructorGenerator.loadArg(qualifierIndex);

                    pushMethodNameAndTypesArguments(proxyConstructorGenerator, methodRef.name, methodRef.argumentTypes);
                    proxyConstructorGenerator.invokeInterface(
                            Type.getType(ExecutionHandleLocator.class),
                            METHOD_GET_PROXY_TARGET
                    );
                    // Step 3: store the result in the array
                    proxyConstructorGenerator.visitInsn(AASTORE);

                    // Step 4: Resolve the interceptors
                    // this.$interceptors[0] = InterceptorChain.resolveAroundInterceptors(this.$proxyMethods[0], var2);
                    pushResolveInterceptorsCall(proxyConstructorGenerator, i, isIntroduction);
                }
            }
        } else {

            for (int i = 0; i < proxyMethodCount; i++) {
                ExecutableMethodWriter executableMethodWriter = proxiedMethods.get(i);
                boolean introduction = isIntroduction && (
                        executableMethodWriter.isAbstract() || (
                                executableMethodWriter.isInterface() && !executableMethodWriter.isDefault()));

                // The following will initialize the array of $proxyMethod instances
                // Eg. this.proxyMethods[0] = new $blah0();
                proxyConstructorGenerator.loadThis();
                proxyConstructorGenerator.getField(proxyType, FIELD_PROXY_METHODS, FIELD_TYPE_PROXY_METHODS);
                proxyConstructorGenerator.push(i);
                Type methodType = Type.getObjectType(executableMethodWriter.getInternalName());
                proxyConstructorGenerator.newInstance(methodType);
                proxyConstructorGenerator.dup();
                if (executableMethodWriter.isSupportsInterceptedProxy()) {
                    proxyConstructorGenerator.push(true);
                    proxyConstructorGenerator.invokeConstructor(methodType, new Method(CONSTRUCTOR_NAME, getConstructorDescriptor(boolean.class)));
                } else {
                    proxyConstructorGenerator.invokeConstructor(methodType, new Method(CONSTRUCTOR_NAME, DESCRIPTOR_DEFAULT_CONSTRUCTOR));
                }
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

    /**
     * Write the proxy to the given compilation directory.
     *
     * @param compilationDir The target compilation directory
     * @throws IOException
     */
    @Override
    public void writeTo(File compilationDir) throws IOException {
        accept(newClassWriterOutputVisitor(compilationDir));
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
            boolean requiresReflection,
            boolean isOptional) {
        deferredInjectionPoints.add(() ->
                proxyBeanDefinitionWriter.visitSetterValue(
                        declaringType,
                        methodElement,
                        requiresReflection,
                        isOptional
                )
        );
    }

    @Override
    public void visitPostConstructMethod(
            TypedElement declaringType,
            MethodElement methodElement,
            boolean requiresReflection) {
        deferredInjectionPoints.add(() -> proxyBeanDefinitionWriter.visitPostConstructMethod(
                declaringType,
                methodElement,
                requiresReflection
        ));
    }

    @Override
    public void visitPreDestroyMethod(
            TypedElement declaringType,
            MethodElement methodElement,
            boolean requiresReflection) {
        deferredInjectionPoints.add(() ->
                proxyBeanDefinitionWriter.visitPreDestroyMethod(
                        declaringType,
                        methodElement,
                        requiresReflection)
        );
    }

    @Override
    public void visitMethodInjectionPoint(TypedElement beanType,
                                          MethodElement methodElement,
                                          boolean requiresReflection) {
        deferredInjectionPoints.add(() ->
                proxyBeanDefinitionWriter.visitMethodInjectionPoint(
                        beanType,
                        methodElement,
                        requiresReflection)
        );
    }

    @Override
    public ExecutableMethodWriter visitExecutableMethod(
            TypedElement declaringBean,
            MethodElement methodElement) {
        deferredInjectionPoints.add(() ->
                proxyBeanDefinitionWriter.visitExecutableMethod(
                        declaringBean,
                        methodElement
                )
        );
        return null;
    }

    @Override
    public void visitFieldInjectionPoint(
            TypedElement declaringType,
            ClassElement fieldType,
            String fieldName,
            boolean requiresReflection,
            AnnotationMetadata annotationMetadata,
            @Nullable Map<String, ClassElement> typeArguments) {
        deferredInjectionPoints.add(() ->
                proxyBeanDefinitionWriter.visitFieldInjectionPoint(
                        declaringType,
                        fieldType,
                        fieldName,
                        requiresReflection,
                        annotationMetadata,
                        typeArguments
                )
        );
    }

    @Override
    public void visitFieldValue(
            TypedElement declaringType,
            ClassElement fieldType,
            String fieldName,
            boolean requiresReflection,
            AnnotationMetadata annotationMetadata,
            @Nullable Map<String, ClassElement> typeArguments,
            boolean isOptional) {
        deferredInjectionPoints.add(() ->
                proxyBeanDefinitionWriter.visitFieldValue(
                        declaringType,
                        fieldType,
                        fieldName,
                        requiresReflection,
                        annotationMetadata,
                        typeArguments,
                        isOptional
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
    public void visitConfigBuilderMethod(String prefix, ClassElement returnType, String methodName, ClassElement paramType, Map<String, ClassElement> generics, String propertyPath) {
        proxyBeanDefinitionWriter.visitConfigBuilderMethod(prefix, returnType, methodName, paramType, generics, propertyPath);
    }

    @Override
    public void visitConfigBuilderDurationMethod(String prefix, ClassElement returnType, String methodName, String propertyPath) {
        proxyBeanDefinitionWriter.visitConfigBuilderDurationMethod(prefix, returnType, methodName, propertyPath);
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
    public void visitTypeArguments(Map<String, Map<String, Object>> typeArguments) {
        proxyBeanDefinitionWriter.visitTypeArguments(typeArguments);
    }

    @Override
    public boolean requiresMethodProcessing() {
        return proxyBeanDefinitionWriter.requiresMethodProcessing();
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
     * @param interceptorTypes types
     */
    public void visitInterceptorTypes(Object... interceptorTypes) {
        if (interceptorTypes != null) {
            this.interceptorTypes.addAll(Arrays.asList(interceptorTypes));
        }
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

    private Method writeResolveTargetMethod(ClassWriter proxyClassWriter, Type targetType) {
        Method resolveTargetMethodDesc = Method.getMethod(targetClassFullName + " " + METHOD_RESOLVE_TARGET + "()");
        GeneratorAdapter resolveTargetMethod = new GeneratorAdapter(proxyClassWriter.visitMethod(
                ACC_PRIVATE,
                resolveTargetMethodDesc.getName(),
                resolveTargetMethodDesc.getDescriptor(),
                null, null

        ), ACC_PRIVATE, METHOD_RESOLVE_TARGET, resolveTargetMethodDesc.getDescriptor());


        // add the logic to create to the bean instance
        resolveTargetMethod.loadThis();
        // load the bean context
        resolveTargetMethod.getField(proxyType, FIELD_BEAN_LOCATOR, TYPE_BEAN_LOCATOR);

        // 1st argument: the type
        resolveTargetMethod.push(targetType);
        // 2nd argument: null qualifier
        resolveTargetMethod.loadThis();
        // the bean qualifier
        resolveTargetMethod.getField(proxyType, FIELD_BEAN_QUALIFIER, Type.getType(Qualifier.class));

        resolveTargetMethod.invokeInterface(
                TYPE_BEAN_LOCATOR,
                METHOD_GET_PROXY_TARGET_BEAN

        );
        pushCastToType(resolveTargetMethod, getTypeReferenceForName(targetClassFullName));
        resolveTargetMethod.returnValue();
        resolveTargetMethod.visitMaxs(1, 1);
        return resolveTargetMethodDesc;
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
        Label l0 = new Label();
        Label l1 = new Label();
        Label l2 = new Label();
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

    private void writeInterceptedTargetMethod(ClassWriter proxyClassWriter, Type targetType, Method resolveTargetMethodDesc) {
        // add interceptedTarget() method
        GeneratorAdapter interceptedTargetVisitor = startPublicMethod(
                proxyClassWriter,
                "interceptedTarget",
                Object.class.getName());

        if (lazy) {
            interceptedTargetVisitor.loadThis();
            interceptedTargetVisitor.invokeVirtual(proxyType, resolveTargetMethodDesc);
            interceptedTargetVisitor.returnValue();
        } else {
            int localRef = -1;
            Label l1 = null;
            Label l2 = null;
            if (hotswap) {
                Label l0 = new Label();
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

    private void pushResolveInterceptorsCall(GeneratorAdapter proxyConstructorGenerator, int i, boolean isIntroduction) {
        // The following will initialize the array of interceptor instances
        // eg. this.interceptors[0] = InterceptorChain.resolveAroundInterceptors(beanContext, proxyMethods[0], interceptors);
        proxyConstructorGenerator.loadThis();
        proxyConstructorGenerator.getField(proxyType, FIELD_INTERCEPTORS, FIELD_TYPE_INTERCEPTORS);
        proxyConstructorGenerator.push(i);

        // First argument. The bean context
        proxyConstructorGenerator.loadArg(beanContextArgumentIndex);

        // Second argument ie. proxyMethods[0]
        proxyConstructorGenerator.loadThis();
        proxyConstructorGenerator.getField(proxyType, FIELD_PROXY_METHODS, FIELD_TYPE_PROXY_METHODS);
        proxyConstructorGenerator.push(i);
        proxyConstructorGenerator.visitInsn(AALOAD);

        // Third argument ie. interceptors
        proxyConstructorGenerator.loadArg(interceptorArgumentIndex);
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
                    methodVisit.isRequiresReflection());
        }
        final List<BeanDefinitionWriter.MethodVisitData> preDestroyMethodVisits = parent.getPreDestroyMethodVisits();
        for (BeanDefinitionWriter.MethodVisitData methodVisit : preDestroyMethodVisits) {
            visitPreDestroyMethod(
                    methodVisit.getBeanType(),
                    methodVisit.getMethodElement(),
                    methodVisit.isRequiresReflection()
            );
        }
    }

    /**
     * Method Reference class with names and a list of argument types. Used as the targets.
     */
    private static final class MethodRef {
        protected final String name;
        protected final List<ClassElement> argumentTypes;
        protected final Type returnType;
        private final List<String> rawTypes;

        public MethodRef(String name, List<ParameterElement> argumentTypes, Type returnType) {
            this.name = name;
            this.argumentTypes = argumentTypes.stream().map(ParameterElement::getType).collect(Collectors.toList());
            this.rawTypes = this.argumentTypes.stream().map(ClassElement::getName).collect(Collectors.toList());
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
