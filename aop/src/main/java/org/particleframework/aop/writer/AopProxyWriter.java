/*
 * Copyright 2017 original authors
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License. 
 */
package org.particleframework.aop.writer;

import org.objectweb.asm.*;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;
import org.particleframework.aop.*;
import org.particleframework.aop.internal.InterceptorChain;
import org.particleframework.aop.internal.MethodInterceptorChain;
import org.particleframework.context.BeanContext;
import org.particleframework.context.BeanLocator;
import org.particleframework.context.ExecutionHandleLocator;
import org.particleframework.context.Qualifier;
import org.particleframework.core.annotation.AnnotationMetadata;
import org.particleframework.core.naming.NameUtils;
import org.particleframework.core.reflect.ReflectionUtils;
import org.particleframework.core.value.OptionalValues;
import org.particleframework.inject.BeanDefinition;
import org.particleframework.inject.ExecutableMethod;
import org.particleframework.inject.ProxyBeanDefinition;
import org.particleframework.inject.writer.*;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Constructor;
import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * A class that generates AOP proxy classes at compile time
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class AopProxyWriter extends AbstractClassFileWriter implements ProxyingBeanDefinitionVisitor {

    public static final Method METHOD_GET_PROXY_TARGET = Method.getMethod(
            ReflectionUtils.getRequiredInternalMethod(
                    ExecutionHandleLocator.class,
                    "getProxyTargetMethod",
                    Class.class,
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
    private static final Method METHOD_PROXY_TARGET_TYPE = Method.getMethod(ReflectionUtils.getRequiredInternalMethod(ProxyBeanDefinition.class, "getTargetDefinitionType"));

    private static final java.lang.reflect.Method RESOLVE_INTRODUCTION_INTERCEPTORS_METHOD = ReflectionUtils.getRequiredInternalMethod(InterceptorChain.class, "resolveIntroductionInterceptors", AnnotatedElement.class, Interceptor[].class);

    private static final java.lang.reflect.Method RESOLVE_AROUND_INTERCEPTORS_METHOD = ReflectionUtils.getRequiredInternalMethod(InterceptorChain.class, "resolveAroundInterceptors", AnnotatedElement.class, Interceptor[].class);

    private static final Constructor CONSTRUCTOR_METHOD_INTERCEPTOR_CHAIN = ReflectionUtils.findConstructor(MethodInterceptorChain.class, Interceptor[].class, Object.class, ExecutableMethod.class, Object[].class).orElseThrow(() ->
            new IllegalStateException("new MethodInterceptorChain(..) constructor not found. Incompatible version of Particle?")
    );

    private static final String FIELD_INTERCEPTORS = "$interceptors";
    private static final String FIELD_BEAN_LOCATOR = "$beanLocator";
    private static final String FIELD_PROXY_METHODS = "$proxyMethods";
    private static final Type FIELD_TYPE_PROXY_METHODS = Type.getType(ExecutableMethod[].class);
    private static final Type EXECUTABLE_METHOD_TYPE = Type.getType(ExecutableMethod.class);
    private static final Type INTERCEPTOR_ARRAY_TYPE = Type.getType(Interceptor[].class);
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

    private final String packageName;
    private final String targetClassShortName;
    private final ClassWriter classWriter;
    private final String targetClassFullName;
    private final String proxyFullName;
    private final BeanDefinitionWriter proxyBeanDefinitionWriter;
    private final String proxyInternalName;
    private final Set<Object> interceptorTypes;
    private final Type proxyType;
    private final boolean hotswap;
    private final boolean lazy;
    private final boolean isInterface;
    private final BeanDefinitionWriter parentWriter;
    private final boolean isIntroduction;
    private boolean isProxyTarget;
    private final String parentBeanDefinitionName;


    private MethodVisitor constructorWriter;
    private List<ExecutableMethodWriter> proxiedMethods = new ArrayList<>();
    private Set<MethodRef> proxiedMethodsRefSet = new HashSet<>();
    private List<MethodRef> proxyTargetMethods = new ArrayList<>();
    private int proxyMethodCount = 0;
    private GeneratorAdapter constructorGenerator;
    private int interceptorArgumentIndex;
    private int beanContextArgumentIndex = -1;
    private Map<String, Object> constructorArgumentTypes;
    private Map<String, Object> constructorQualfierTypes;
    private Map<String, Map<String, Object>> constructorGenericTypes;
    private Map<String, Object> constructorNewArgumentTypes;
    /**
     * <p>Constructs a new {@link AopProxyWriter} for the given parent {@link BeanDefinitionWriter} and starting interceptors types.</p>
     *
     * <p>Additional {@link Interceptor} types can be added downstream with {@link #visitInterceptorTypes(Object...)}.</p>
     *
     * @param parent The parent {@link BeanDefinitionWriter}
     * @param interceptorTypes The annotation types of the {@link Interceptor} instances to be injected
     */
    public AopProxyWriter(BeanDefinitionWriter parent,
                          Object... interceptorTypes) {
        this(parent, OptionalValues.empty(), interceptorTypes);
    }
    /**
     * <p>Constructs a new {@link AopProxyWriter} for the given parent {@link BeanDefinitionWriter} and starting interceptors types.</p>
     *
     * <p>Additional {@link Interceptor} types can be added downstream with {@link #visitInterceptorTypes(Object...)}.</p>
     *
     * @param parent The parent {@link BeanDefinitionWriter}
     * @param interceptorTypes The annotation types of the {@link Interceptor} instances to be injected
     */
    public AopProxyWriter(BeanDefinitionWriter parent,
                          OptionalValues<Boolean> settings,
                          Object... interceptorTypes) {
        this.isIntroduction = false;
        this.parentWriter = parent;
        this.isProxyTarget = settings.get(Interceptor.PROXY_TARGET).orElse(false) || parent.isInterface();
        this.hotswap = isProxyTarget && settings.get(Interceptor.HOTSWAP).orElse(false);
        this.lazy = isProxyTarget && settings.get(Interceptor.LAZY).orElse(false);
        this.isInterface = parent.isInterface();
        this.packageName = parent.getPackageName();
        this.targetClassShortName = parent.getBeanSimpleName();
        this.parentBeanDefinitionName = parent.getBeanDefinitionName();
        this.targetClassFullName = packageName + '.' + targetClassShortName;
        this.classWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);

        this.proxyFullName = parent.getBeanDefinitionName() + "$Intercepted";
        String proxyShortName = NameUtils.getSimpleName(proxyFullName);
        this.proxyInternalName = getInternalName(this.proxyFullName);
        this.proxyType = getTypeReference(proxyFullName);
        this.interceptorTypes = new HashSet<>(Arrays.asList(interceptorTypes));
        Type scope = parent.getScope();
        String scopeClassName = scope != null ? scope.getClassName() : null;
        this.proxyBeanDefinitionWriter = new BeanDefinitionWriter(
                NameUtils.getPackageName(proxyFullName),
                proxyShortName,
                scopeClassName,
                parent.isSingleton(),
                parent.getAnnotationMetadata());
        startClass(classWriter, proxyFullName, getTypeReference(targetClassFullName));
    }

    /**
     * Constructs a new {@link AopProxyWriter} for the purposes of writing {@link org.particleframework.aop.Introduction} advise
     *
     * @param packageName The package name
     * @param className The class name
     * @param scope The scope annotation type
     * @param isInterface Is the target of the advise an interface
     * @param isSingleton Is the target of the advise singleton
     * @param interceptorTypes The interceptor types
     */
    public AopProxyWriter(String packageName,
                          String className,
                          String scope,
                          boolean isInterface,
                          boolean isSingleton,
                          AnnotationMetadata annotationMetadata,
                          Object... interceptorTypes) {
        this.isIntroduction = true;
        this.packageName = packageName;
        this.isInterface = isInterface;
        this.hotswap = false;
        this.lazy = false;
        this.targetClassShortName = className;
        this.targetClassFullName = packageName + '.' + targetClassShortName;
        this.parentWriter = null;
        this.proxyFullName = targetClassFullName + "$Intercepted";
        this.proxyInternalName = getInternalName(this.proxyFullName);
        this.proxyType = getTypeReference(proxyFullName);
        this.interceptorTypes = new HashSet<>(Arrays.asList(interceptorTypes));
        this.classWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        this.parentBeanDefinitionName = null;
        String proxyShortName = NameUtils.getSimpleName(proxyFullName);
        this.proxyBeanDefinitionWriter = new BeanDefinitionWriter(
                NameUtils.getPackageName(proxyFullName),
                proxyShortName,
                scope,
                isSingleton, annotationMetadata);
        startClass(classWriter, proxyFullName, getTypeReference(targetClassFullName));
    }

    @Override
    protected void startClass(ClassVisitor classWriter, String className, Type superType) {
        super.startClass(classWriter, className, superType);

        classWriter.visitField(ACC_FINAL | ACC_PRIVATE, FIELD_INTERCEPTORS, FIELD_TYPE_INTERCEPTORS.getDescriptor(), null, null);
        classWriter.visitField(ACC_FINAL | ACC_PRIVATE, FIELD_PROXY_METHODS, FIELD_TYPE_PROXY_METHODS.getDescriptor(), null, null);
    }

    @Override
    public void visitBeanDefinitionConstructor() {
        visitBeanDefinitionConstructor(Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap());
    }

    @Override
    public List<TypeAnnotationSource> getAnnotationSources() {
        return proxyBeanDefinitionWriter.getAnnotationSources();
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
    public Type getScope() {
        return proxyBeanDefinitionWriter.getScope();
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
    public boolean isValidated() {
        return proxyBeanDefinitionWriter.isValidated();
    }

    @Override
    public String getBeanDefinitionName() {
        return  proxyBeanDefinitionWriter.getBeanDefinitionName();
    }

    /**
     * Visits a constructor with arguments. Either this method or {@link #visitBeanDefinitionConstructor()} should be called at least once
     *
     * @param argumentTypes  The argument names and types. Should be an ordered map should as {@link LinkedHashMap}
     * @param qualifierTypes The argument names and qualifier types. Should be an ordered map should as {@link LinkedHashMap}
     * @param genericTypes   The argument names and generic types. Should be an ordered map should as {@link LinkedHashMap}
     */
    public void visitBeanDefinitionConstructor(Map<String, Object> argumentTypes, Map<String, Object> qualifierTypes, Map<String, Map<String, Object>> genericTypes) {
        this.constructorArgumentTypes = argumentTypes;
        this.constructorQualfierTypes = qualifierTypes;
        this.constructorGenericTypes = genericTypes;
        this.constructorNewArgumentTypes = new LinkedHashMap<>(argumentTypes);
        if(isProxyTarget) {
            this.beanContextArgumentIndex = argumentTypes.size();
            constructorNewArgumentTypes.put("beanContext", BeanContext.class);
        }
        this.interceptorArgumentIndex = constructorNewArgumentTypes.size();
        constructorNewArgumentTypes.put("interceptors", Interceptor[].class);
    }

    /**
     * Visit a method that is to be proxied
     *
     * @param declaringType  The declaring type of the method. Either a Class or a string representing the name of the type
     * @param returnType     The return type of the method. Either a Class or a string representing the name of the type
     * @param methodName     The method name
     * @param argumentTypes  The argument types. Note: an ordered map should be used such as LinkedHashMap. Can be null or empty.
     * @param qualifierTypes The qualifier types of each argument. Can be null.
     * @param genericTypes   The generic types of each argument. Can be null.
     */
    public void visitAroundMethod(Object declaringType,
                                  Object returnType,
                                  Map<String, Object> returnTypeGenericTypes,
                                  String methodName,
                                  Map<String, Object> argumentTypes,
                                  Map<String, Object> qualifierTypes,
                                  Map<String, Map<String, Object>> genericTypes) {

        List<Object> argumentTypeList = new ArrayList<>(argumentTypes.values());
        int argumentCount = argumentTypes.size();
        Type returnTypeObject = getTypeReference(returnType);
        boolean isPrimitive = isPrimitive(returnType);
        boolean isVoidReturn = isPrimitive && returnTypeObject.equals(Type.VOID_TYPE);
        MethodRef methodKey = new MethodRef(methodName, argumentTypeList);
        if(isProxyTarget) {
            // if the target class is being proxied then the method will be looked up from the parent bean definition.
            // Therefore no need to generate a bridge
            if(!proxyTargetMethods.contains(methodKey)) {
                int index = proxyMethodCount++;
                proxyTargetMethods.add(methodKey);
                buildMethodOverride(returnType, methodName, index, argumentTypeList, argumentCount, isVoidReturn);
            }
        }
        else if(!proxiedMethodsRefSet.contains(methodKey)){
            int index = proxyMethodCount++;
            // if the target is not being proxied then we generate a subclass where only the proxied methods are overridden
            // Each overridden method calls super.blah(..) thus maintaining class semantics better and providing smaller more efficient
            // proxies

            String methodProxyShortName = "$$proxy" + index;
            String bridgeName = "$$access" + index;
            String methodExecutorClassName = proxyFullName + methodProxyShortName;

            List<Object> bridgeArguments = new ArrayList<>();
            bridgeArguments.add(proxyFullName);
            bridgeArguments.addAll(argumentTypeList);
            String bridgeDesc = getMethodDescriptor(returnType, bridgeArguments);

            ExecutableMethodWriter executableMethodWriter = new ExecutableMethodWriter(
                    proxyFullName, methodExecutorClassName, methodProxyShortName, isInterface
            ) {
                @Override
                protected void buildInvokeMethod(Type declaringTypeObject, String methodName, Object returnType, Collection<Object> argumentTypes, GeneratorAdapter invokeMethodVisitor) {
                    // load this
                    invokeMethodVisitor.loadThis();
                    // first argument to static bridge is reference to parent
                    invokeMethodVisitor.getField(methodType, FIELD_PARENT, proxyType);
                    // now remaining arguments
                    for (int i = 0; i < argumentTypeList.size(); i++) {
                        invokeMethodVisitor.loadArg(1);
                        invokeMethodVisitor.push(i);
                        invokeMethodVisitor.visitInsn(AALOAD);
                        AopProxyWriter.pushCastToType(invokeMethodVisitor, argumentTypeList.get(i));
                    }
                    invokeMethodVisitor.visitMethodInsn(INVOKESTATIC, proxyInternalName, bridgeName, bridgeDesc, false);
                    if(isVoidReturn) {
                        invokeMethodVisitor.visitInsn(ACONST_NULL);
                    }
                    else {
                        AopProxyWriter.pushBoxPrimitiveIfNecessary(returnType, invokeMethodVisitor);
                    }
                    invokeMethodVisitor.visitInsn(ARETURN);
                    invokeMethodVisitor.visitMaxs(AbstractClassFileWriter.DEFAULT_MAX_STACK, 1);
                    invokeMethodVisitor.visitEnd();
                }
            };
            executableMethodWriter.makeInner(proxyInternalName, classWriter);
            String declaringClassName = getTypeReference(declaringType).getClassName();
            if(!declaringClassName.equals(targetClassFullName)) {
                executableMethodWriter.visitTypeAnnotationSource(targetClassFullName);
            }

            executableMethodWriter.visitMethod(declaringType, returnType, returnTypeGenericTypes, methodName, argumentTypes, qualifierTypes, genericTypes);
            executableMethodWriter.visitEnd();

            proxiedMethods.add(executableMethodWriter);
            proxiedMethodsRefSet.add(methodKey);
            String overrideDescriptor = buildMethodOverride(returnType, methodName, index, argumentTypeList, argumentCount, isVoidReturn);


            // now build a bridge to invoke the original method

            MethodVisitor bridgeWriter = classWriter.visitMethod(ACC_STATIC | ACC_SYNTHETIC,
                    bridgeName, bridgeDesc, null, null);
            GeneratorAdapter bridgeGenerator = new GeneratorAdapter(bridgeWriter, ACC_STATIC + ACC_SYNTHETIC, bridgeName, bridgeDesc);
            for (int i = 0; i < bridgeArguments.size(); i++) {
                bridgeGenerator.loadArg(i);
            }
            bridgeWriter.visitMethodInsn(INVOKESPECIAL, getInternalName(targetClassFullName), methodName, overrideDescriptor, false);
            pushReturnValue(bridgeWriter, returnType);
            bridgeWriter.visitMaxs(DEFAULT_MAX_STACK, 1);
            bridgeWriter.visitEnd();
        }


    }

    private String buildMethodOverride(Object returnType, String methodName, int index, List<Object> argumentTypeList, int argumentCount, boolean isVoidReturn) {
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
        if( isProxyTarget ) {
            if(hotswap || lazy) {
               overriddenMethodGenerator.invokeInterface(Type.getType(InterceptedProxy.class), Method.getMethod("java.lang.Object interceptedTarget()") );
            }
            else {
                overriddenMethodGenerator.getField(proxyType, FIELD_TARGET, getTypeReference(targetClassFullName));
            }
        }

        // third argument: the executable method
        overriddenMethodGenerator.loadLocal(methodProxyVar);

        // fourth argument: array of the argument values
        overriddenMethodGenerator.push(argumentCount);
        overriddenMethodGenerator.newArray(Type.getType(Object.class));

        // now pass the remaining arguments from the original method
        for (int i = 0; i < argumentCount; i++) {
            overriddenMethodGenerator.dup();
            Object argType = argumentTypeList.get(i);
            overriddenMethodGenerator.push(i);
            overriddenMethodGenerator.loadArg(i);
            pushBoxPrimitiveIfNecessary(argType, overriddenMethodGenerator);
            overriddenMethodGenerator.visitInsn(AASTORE);
        }
        // invoke MethodInterceptorChain constructor
        overriddenMethodGenerator.invokeConstructor(TYPE_METHOD_INTERCEPTOR_CHAIN, Method.getMethod(CONSTRUCTOR_METHOD_INTERCEPTOR_CHAIN));
        int chainVar = overriddenMethodGenerator.newLocal(TYPE_METHOD_INTERCEPTOR_CHAIN);
        overriddenMethodGenerator.storeLocal(chainVar);
        overriddenMethodGenerator.loadLocal(chainVar);

        overriddenMethodGenerator.visitMethodInsn(INVOKEVIRTUAL, TYPE_INTERCEPTOR_CHAIN.getInternalName(), "proceed", getMethodDescriptor(Object.class.getName()), false);
        if (isVoidReturn) {
            returnVoid(overriddenMethodGenerator);
        }
        else {

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
        String constructorDescriptor = getConstructorDescriptor(constructorNewArgumentTypes.values());
        ClassWriter proxyClassWriter = this.classWriter;
        this.constructorWriter = proxyClassWriter.visitMethod(
                ACC_PUBLIC,
                CONSTRUCTOR_NAME,
                constructorDescriptor,
                null,
                null);

        // Add the interceptor @Type(..) annotation
        Type[] interceptorTypes = getObjectTypes(this.interceptorTypes);
        AnnotationVisitor interceptorTypeAnn = constructorWriter.visitParameterAnnotation(
                interceptorArgumentIndex, Type.getDescriptor(org.particleframework.context.annotation.Type.class), true
        ).visitArray("value");
        for (Type interceptorType : interceptorTypes) {
            interceptorTypeAnn.visit(null, interceptorType);
        }
        interceptorTypeAnn.visitEnd();

        this.constructorGenerator = new GeneratorAdapter(constructorWriter, Opcodes.ACC_PUBLIC, CONSTRUCTOR_NAME, constructorDescriptor);
        GeneratorAdapter proxyConstructorGenerator = this.constructorGenerator;

        proxyConstructorGenerator.loadThis();
        if(isInterface) {
            proxyConstructorGenerator.invokeConstructor(TYPE_OBJECT, METHOD_DEFAULT_CONSTRUCTOR);
        }
        else {

            Collection<Object> existingArguments = constructorArgumentTypes.values();
            for (int i = 0; i < existingArguments.size(); i++) {
                proxyConstructorGenerator.loadArg(i);
            }
            String superConstructorDescriptor = getConstructorDescriptor(existingArguments);
            proxyConstructorGenerator.invokeConstructor(getTypeReference(targetClassFullName), new Method(CONSTRUCTOR_NAME, superConstructorDescriptor));
        }

        proxyBeanDefinitionWriter.visitBeanDefinitionConstructor(constructorNewArgumentTypes, constructorQualfierTypes, constructorGenericTypes);

        GeneratorAdapter targetDefinitionGenerator = null;
        if(parentWriter != null) {
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
        }


        Class interceptedInterface = isIntroduction ? Introduced.class : Intercepted.class;
        Type targetType = getTypeReference(targetClassFullName);

        // add the $beanLocator field

        if(isProxyTarget) {
            proxyClassWriter.visitField(
                    ACC_PRIVATE | ACC_FINAL,
                    FIELD_BEAN_LOCATOR,
                    TYPE_BEAN_LOCATOR.getDescriptor(),
                    null,
                    null
            );

            if(lazy) {
                interceptedInterface = InterceptedProxy.class;
            }
            else {
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
                if(hotswap) {
                    // Add ReadWriteLock field
                    // private final ReentrantReadWriteLock $target_rwl = new ReentrantReadWriteLock();
                    proxyClassWriter.visitField(
                            ACC_PRIVATE | ACC_FINAL,
                            FIELD_READ_WRITE_LOCK,
                            TYPE_READ_WRITE_LOCK.getDescriptor(),
                            null,null
                    );
                    proxyConstructorGenerator.loadThis();
                    proxyConstructorGenerator.newInstance(TYPE_READ_WRITE_LOCK);
                    proxyConstructorGenerator.dup();
                    proxyConstructorGenerator.invokeConstructor(TYPE_READ_WRITE_LOCK, METHOD_DEFAULT_CONSTRUCTOR );
                    proxyConstructorGenerator.putField(proxyType, FIELD_READ_WRITE_LOCK, TYPE_READ_WRITE_LOCK);

                    // Add Read Lock field
                    // private final Lock $target_rl = $target_rwl.readLock();
                    proxyClassWriter.visitField(
                            ACC_PRIVATE | ACC_FINAL,
                            FIELD_READ_LOCK,
                            TYPE_LOCK.getDescriptor(),
                            null,null
                    );
                    proxyConstructorGenerator.loadThis();
                    proxyConstructorGenerator.loadThis();
                    proxyConstructorGenerator.getField(proxyType, FIELD_READ_WRITE_LOCK, TYPE_READ_WRITE_LOCK);
                    proxyConstructorGenerator.invokeInterface(
                            Type.getType(ReadWriteLock.class),
                            Method.getMethod(Lock.class.getName() + " readLock()")
                    );
                    proxyConstructorGenerator.putField(proxyType, FIELD_READ_LOCK, TYPE_LOCK );

                    // Add Write Lock field
                    // private final Lock $target_wl = $target_rwl.writeLock();
                    proxyClassWriter.visitField(
                            ACC_PRIVATE | ACC_FINAL,
                            FIELD_WRITE_LOCK,
                            Type.getDescriptor(Lock.class),
                            null,null
                    );

                    proxyConstructorGenerator.loadThis();
                    proxyConstructorGenerator.loadThis();
                    proxyConstructorGenerator.getField(proxyType, FIELD_READ_WRITE_LOCK, TYPE_READ_WRITE_LOCK);
                    proxyConstructorGenerator.invokeInterface(
                            Type.getType(ReadWriteLock.class),
                            Method.getMethod(Lock.class.getName() + " writeLock()")
                    );
                    proxyConstructorGenerator.putField(proxyType, FIELD_WRITE_LOCK, TYPE_LOCK );
                }
            }
            // assign the bean locator
            proxyConstructorGenerator.loadThis();
            proxyConstructorGenerator.loadArg(beanContextArgumentIndex);
            proxyConstructorGenerator.putField(proxyType, FIELD_BEAN_LOCATOR, TYPE_BEAN_LOCATOR);


            Method resolveTargetMethodDesc = writeResolveTargetMethod(proxyClassWriter, targetType);

            if(!lazy) {
                proxyConstructorGenerator.loadThis();
                proxyConstructorGenerator.loadThis();
                proxyConstructorGenerator.invokeVirtual(proxyType, resolveTargetMethodDesc);
                proxyConstructorGenerator.putField(proxyType, FIELD_TARGET, targetType);
            }

            // Write the Object interceptedTarget() method
            writeInterceptedTargetMethod(proxyClassWriter, targetType, resolveTargetMethodDesc);

            // Write the swap method
            // e. T swap(T newInstance);
            if(hotswap && !lazy) {
                writeSwapMethod(proxyClassWriter, targetType);
            }
        }

        String[] interfaces;
        if(isInterface) {
            interfaces = new String[]{
                    getInternalName(targetClassFullName),
                    Type.getInternalName(interceptedInterface)
            };
        }
        else {
            interfaces = new String[]{Type.getInternalName(interceptedInterface)};
        }
        proxyClassWriter.visit(V1_8, ACC_PUBLIC,
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
        if(isProxyTarget) {
            if(proxyTargetMethods.size() == proxyMethodCount) {

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

                    pushMethodNameAndTypesArguments(proxyConstructorGenerator, methodRef.name, methodRef.argumentTypes);
                    proxyConstructorGenerator.invokeInterface(
                            Type.getType(ExecutionHandleLocator.class),
                            METHOD_GET_PROXY_TARGET
                    );
                    // Step 3: store the result in the array
                    proxyConstructorGenerator.visitInsn(AASTORE);

                    // Step 4: Resolve the interceptors
                    // this.$interceptors[0] = InterceptorChain.resolveAroundInterceptors(this.$proxyMethods[0], var2);
                    pushResolveInterceptorsCall(proxyConstructorGenerator, i);
                }
            }
        }
        else {

            for (int i = 0; i < proxyMethodCount; i++) {

                ExecutableMethodWriter executableMethodWriter = proxiedMethods.get(i);

                // The following will initialize the array of $proxyMethod instances
                // Eg. this.proxyMethods[0] = new $blah0();
                proxyConstructorGenerator.loadThis();
                proxyConstructorGenerator.getField(proxyType, FIELD_PROXY_METHODS, FIELD_TYPE_PROXY_METHODS);
                proxyConstructorGenerator.push(i);
                Type methodType = Type.getObjectType(executableMethodWriter.getInternalName());
                proxyConstructorGenerator.newInstance(methodType);
                proxyConstructorGenerator.dup();
                proxyConstructorGenerator.loadThis();
                proxyConstructorGenerator.invokeConstructor(methodType, new Method(CONSTRUCTOR_NAME, getConstructorDescriptor(proxyFullName)));
                proxyConstructorGenerator.visitInsn(AASTORE);
                pushResolveInterceptorsCall(proxyConstructorGenerator, i);

            }
        }

        constructorWriter.visitInsn(RETURN);
        constructorWriter.visitMaxs(DEFAULT_MAX_STACK, 1);

        this.constructorWriter.visitEnd();
        proxyBeanDefinitionWriter.visitBeanDefinitionEnd();
        if(targetDefinitionGenerator != null) {
            targetDefinitionGenerator.visitMaxs(1,1);
            targetDefinitionGenerator.visitEnd();
        }

        proxyClassWriter.visitEnd();
    }



    /**
     * Write the proxy to the given compilation directory
     *
     * @param compilationDir The target compilation directory
     * @throws IOException
     */
    @Override
    public void writeTo(File compilationDir) throws IOException {
        accept(newClassWriterOutputVisitor(compilationDir));
    }

    /**
     * Write the class to output via a visitor that manages output destination
     *
     * @param visitor the writer output visitor
     * @throws IOException If an error occurs
     */
    @Override
    public void accept(ClassWriterOutputVisitor visitor) throws IOException {
        proxyBeanDefinitionWriter.accept(visitor);
        try (OutputStream out = visitor.visitClass(proxyFullName)) {
            out.write(classWriter.toByteArray());
            proxiedMethods.forEach((writer) -> {
                try {
                    try (OutputStream outputStream = visitor.visitClass(writer.getClassName())) {
                        outputStream.write(writer.getClassWriter().toByteArray());
                    }
                } catch (Throwable e) {
                    throw new ClassGenerationException("Error generating class: " + writer.getClassName(), e);
                }
            });

        }
    }

    @Override
    public void visitSetterInjectionPoint(
            Object declaringType,
            Object qualifierType,
            boolean requiresReflection,
            Object fieldType,
            String fieldName,
            String setterName,
            Map<String, Object> genericTypes) {
        proxyBeanDefinitionWriter.visitSetterInjectionPoint(
                declaringType, qualifierType, requiresReflection, fieldType, fieldName, setterName, genericTypes
        );
    }

    @Override
    public void visitSuperType(String name) {
        proxyBeanDefinitionWriter.visitSuperType(name);
    }

    @Override
    public void visitSuperFactoryType(String beanName) {
        proxyBeanDefinitionWriter.visitSuperFactoryType(beanName);
    }

    @Override
    public void visitSetterValue(
            Object declaringType,
            Object qualifierType,
            boolean requiresReflection,
            Object fieldType,
            String fieldName,
            String setterName,
            Map<String, Object> genericTypes,
            boolean isOptional) {
        proxyBeanDefinitionWriter.visitSetterValue(
                declaringType, qualifierType, requiresReflection, fieldType, fieldName, setterName, genericTypes, isOptional
        );
    }

    @Override
    public void visitPostConstructMethod(
            Object declaringType,
            boolean requiresReflection,
            Object returnType,
            String methodName,
            Map<String, Object> argumentTypes,
            Map<String, Object> qualifierTypes,
            Map<String, Map<String, Object>> genericTypes) {
        proxyBeanDefinitionWriter.visitPostConstructMethod(
                declaringType,requiresReflection, returnType, methodName, argumentTypes, qualifierTypes, genericTypes
        );
    }

    @Override
    public void visitPreDestroyMethod(
            Object declaringType,
            boolean requiresReflection,
            Object returnType,
            String methodName,
            Map<String, Object> argumentTypes,
            Map<String, Object> qualifierTypes,
            Map<String, Map<String, Object>> genericTypes) {
        proxyBeanDefinitionWriter.visitPreDestroyMethod(declaringType, requiresReflection,returnType, methodName,argumentTypes, qualifierTypes, genericTypes);
    }

    @Override
    public void visitMethodInjectionPoint(
            Object declaringType,
            boolean requiresReflection,
            Object returnType,
            String methodName,
            Map<String, Object> argumentTypes,
            Map<String, Object> qualifierTypes,
            Map<String, Map<String, Object>> genericTypes) {
        proxyBeanDefinitionWriter.visitMethodInjectionPoint(declaringType, requiresReflection,returnType, methodName,argumentTypes, qualifierTypes, genericTypes);
    }

    @Override
    public ExecutableMethodWriter visitExecutableMethod(
            Object declaringType,
            Object returnType,
            Map<String, Object> returnTypeGenericTypes,
            String methodName,
            Map<String, Object> argumentTypes,
            Map<String, Object> qualifierTypes,
            Map<String, Map<String, Object>> genericTypes) {
        return proxyBeanDefinitionWriter.visitExecutableMethod(
                declaringType,
                returnType,
                returnTypeGenericTypes,
                methodName,
                argumentTypes,
                qualifierTypes,
                genericTypes
        );
    }

    @Override
    public void visitFieldInjectionPoint(Object declaringType, Object qualifierType, boolean requiresReflection, Object fieldType, String fieldName) {
        proxyBeanDefinitionWriter.visitFieldInjectionPoint(declaringType, qualifierType, requiresReflection, fieldType, fieldName);
    }

    @Override
    public void visitFieldValue(Object declaringType, Object qualifierType, boolean requiresReflection, Object fieldType, String fieldName, boolean isOptional) {
        proxyBeanDefinitionWriter.visitFieldValue(declaringType, qualifierType, requiresReflection, fieldType, fieldName, isOptional);
    }

    @Override
    public void visitMethodAnnotationSource(Object declaringType, String methodName, Map<String, Object> parameters) {
        proxyBeanDefinitionWriter.visitMethodAnnotationSource(declaringType, methodName, parameters);
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
    public String getProxiedTypeName() {
        return targetClassFullName;
    }

    @Override
    public String getProxiedBeanDefinitionName() {
        return parentWriter != null ? parentWriter.getBeanDefinitionName() : null;
    }


    public void visitInterceptorTypes(Object... interceptorTypes) {
        if(interceptorTypes != null) {
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
                null,null

        ), ACC_PRIVATE, METHOD_RESOLVE_TARGET, resolveTargetMethodDesc.getDescriptor());


        // add the logic to create to the bean instance
        resolveTargetMethod.loadThis();
        // load the bean context
        resolveTargetMethod.getField(proxyType, FIELD_BEAN_LOCATOR, TYPE_BEAN_LOCATOR);

        // 1st argument: the type
        resolveTargetMethod.push(targetType);
        // 2nd argument: null qualifier
        resolveTargetMethod.visitInsn(ACONST_NULL);

        resolveTargetMethod.invokeInterface(
                TYPE_BEAN_LOCATOR,
                METHOD_GET_PROXY_TARGET_BEAN

        );
        pushCastToType(resolveTargetMethod, targetClassFullName);
        resolveTargetMethod.returnValue();
        resolveTargetMethod.visitMaxs(1,1);
        return resolveTargetMethodDesc;
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
        swapGenerator.getField(proxyType,FIELD_TARGET, targetType);
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

        swapGenerator.visitMaxs(2,3);
        swapGenerator.visitEnd();
    }


    private void writeInterceptedTargetMethod(ClassWriter proxyClassWriter, Type targetType, Method resolveTargetMethodDesc) {
        // add interceptedTarget() method
        GeneratorAdapter interceptedTargetVisitor = startPublicMethod(
                proxyClassWriter,
                "interceptedTarget",
                Object.class.getName());

        if(lazy) {
            interceptedTargetVisitor.loadThis();
            interceptedTargetVisitor.invokeVirtual(proxyType, resolveTargetMethodDesc);
            interceptedTargetVisitor.returnValue();
        }
        else {
            int localRef = -1;
            Label l1 = null;
            Label l2 = null;
            if(hotswap) {
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
            interceptedTargetVisitor.getField(proxyType,FIELD_TARGET, targetType);
            if(hotswap) {
                // release read lock
                localRef = interceptedTargetVisitor.newLocal(targetType);
                interceptedTargetVisitor.storeLocal(localRef);
                interceptedTargetVisitor.visitLabel(l1);
                readUnlock(interceptedTargetVisitor);
                interceptedTargetVisitor.loadLocal(localRef);
            }
            interceptedTargetVisitor.returnValue();
            if(localRef > -1) {
                interceptedTargetVisitor.visitLabel(l2);
                // release read lock in finally
                int var = interceptedTargetVisitor.newLocal(targetType);
                interceptedTargetVisitor.storeLocal(var);
                readUnlock(interceptedTargetVisitor);
                interceptedTargetVisitor.loadLocal(var);
                interceptedTargetVisitor.throwException();
            }
        }

        interceptedTargetVisitor.visitMaxs(1,2);
        interceptedTargetVisitor.visitEnd();
    }

    private void pushResolveInterceptorsCall(GeneratorAdapter proxyConstructorGenerator, int i) {
        // The following will initialize the array of interceptor instances
        // eg. this.interceptors[0] = InterceptorChain.resolveAroundInterceptors(proxyMethods[0], interceptors);
        proxyConstructorGenerator.loadThis();
        proxyConstructorGenerator.getField(proxyType, FIELD_INTERCEPTORS, FIELD_TYPE_INTERCEPTORS);
        proxyConstructorGenerator.push(i);

        // First argument ie. proxyMethods[0]
        proxyConstructorGenerator.loadThis();
        proxyConstructorGenerator.getField(proxyType, FIELD_PROXY_METHODS, FIELD_TYPE_PROXY_METHODS);
        proxyConstructorGenerator.push(i);
        proxyConstructorGenerator.visitInsn(AALOAD);

        // Second argument ie. interceptors
        proxyConstructorGenerator.loadArg(interceptorArgumentIndex);
        if(isIntroduction) {
            proxyConstructorGenerator.invokeStatic(TYPE_INTERCEPTOR_CHAIN, Method.getMethod(RESOLVE_INTRODUCTION_INTERCEPTORS_METHOD));
        }
        else {
            proxyConstructorGenerator.invokeStatic(TYPE_INTERCEPTOR_CHAIN, Method.getMethod(RESOLVE_AROUND_INTERCEPTORS_METHOD));
        }
        proxyConstructorGenerator.visitInsn(AASTORE);
    }

    private class MethodRef {
        final String name;
        final List<Object> argumentTypes;

        public MethodRef(String name, List<Object> argumentTypes) {
            this.name = name;
            this.argumentTypes = argumentTypes;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            MethodRef methodRef = (MethodRef) o;

            if (!name.equals(methodRef.name)) return false;
            return argumentTypes.equals(methodRef.argumentTypes);
        }

        @Override
        public int hashCode() {
            int result = name.hashCode();
            result = 31 * result + argumentTypes.hashCode();
            return result;
        }
    }
}
