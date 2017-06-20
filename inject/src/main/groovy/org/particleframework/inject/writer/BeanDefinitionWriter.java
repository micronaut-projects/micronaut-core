package org.particleframework.inject.writer;

import groovyjarjarasm.asm.*;
import groovyjarjarasm.asm.signature.SignatureVisitor;
import groovyjarjarasm.asm.signature.SignatureWriter;
import org.codehaus.groovy.ast.tools.GeneralUtils;
import org.codehaus.groovy.runtime.MetaClassHelper;
import org.particleframework.context.AbstractBeanDefinition;
import org.particleframework.context.BeanContext;
import org.particleframework.context.BeanResolutionContext;
import org.particleframework.context.DefaultBeanContext;
import org.particleframework.core.reflect.ReflectionUtils;
import org.particleframework.inject.*;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;

/**
 * <p>Responsible for building bean definitions at compile time. Uses ASM build the class definition.</p>
 * <p>
 * <p>Should be used from AST frameworks to build bean definitions from source code data.</p>
 * <p>
 * <p>For example:</p>
 * <p>
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
 * @since 1.0
 */
public class BeanDefinitionWriter extends AbstractClassFileWriter {
    private static final Method CREATE_MAP_METHOD = ReflectionUtils.getDeclaredMethod(AbstractBeanDefinition.class, "createMap", Object[].class).orElseThrow(() ->
            new IllegalStateException("AbstractBeanDefinition.createMap(..) method not found. Incompatible version of Particle on the classpath?")
    );
    private static final Method INJECT_BEAN_FIELDS_METHOD = ReflectionUtils.getDeclaredMethod(AbstractBeanDefinition.class, "injectBeanFields", BeanResolutionContext.class, DefaultBeanContext.class, Object.class).orElseThrow(() ->
            new IllegalStateException("AbstractBeanDefinition.injectBeanFields(..) method not found. Incompatible version of Particle on the classpath?")
    );
    private static final Method POST_CONSTRUCT_METHOD = ReflectionUtils.getDeclaredMethod(AbstractBeanDefinition.class, "postConstruct", BeanResolutionContext.class, BeanContext.class, Object.class).orElseThrow(() ->
            new IllegalStateException("AbstractBeanDefinition.postConstruct(..) method not found. Incompatible version of Particle on the classpath?")
    );
    private static final Method PRE_DESTROY_METHOD = ReflectionUtils.getDeclaredMethod(AbstractBeanDefinition.class, "preDestroy", BeanResolutionContext.class, BeanContext.class, Object.class).orElseThrow(() ->
            new IllegalStateException("AbstractBeanDefinition.preDestroy(..) method not found. Incompatible version of Particle on the classpath?")
    );
    private static final Method INJECT_BEAN_METHODS_METHOD = ReflectionUtils.getDeclaredMethod(AbstractBeanDefinition.class, "injectBeanMethods", BeanResolutionContext.class, DefaultBeanContext.class, Object.class).orElseThrow(() ->
            new IllegalStateException("AbstractBeanDefinition.injectBeanMethods(..) method not found. Incompatible version of Particle on the classpath?")
    );

    private static final Method ADD_FIELD_INJECTION_POINT_METHOD = ReflectionUtils.getDeclaredMethod(AbstractBeanDefinition.class, "addInjectionPoint", Field.class, Annotation.class, boolean.class).orElseThrow(() ->
            new IllegalStateException("AbstractBeanDefinition.addInjectionPoint(..) method not found. Incompatible version of Particle on the classpath?")
    );

    private static final Method ADD_METHOD_INJECTION_POINT_METHOD = ReflectionUtils.getDeclaredMethod(AbstractBeanDefinition.class, "addInjectionPoint", Method.class, Map.class, Map.class, Map.class, boolean.class).orElseThrow(() ->
            new IllegalStateException("AbstractBeanDefinition.addInjectionPoint(..) method not found. Incompatible version of Particle on the classpath?")
    );

    private static final Method ADD_SETTER_INJECTION_POINT_METHOD = ReflectionUtils.getDeclaredMethod(AbstractBeanDefinition.class, "addInjectionPoint", Field.class, Method.class, Annotation.class, List.class, boolean.class).orElseThrow(() ->
            new IllegalStateException("AbstractBeanDefinition.addInjectionPoint(..) method not found. Incompatible version of Particle on the classpath?")
    );


    private static final Method CREATE_LIST_METHOD = ReflectionUtils.getDeclaredMethod(Arrays.class, "asList", Object[].class).orElseThrow(() ->
            new IllegalStateException("Arrays.asList(..) method not found. Incompatible JVM?")
    );

    private static final Method GET_BEAN_FOR_CONSTRUCTOR_ARGUMENT = ReflectionUtils.getDeclaredMethod(AbstractBeanDefinition.class, "getBeanForConstructorArgument", BeanResolutionContext.class, BeanContext.class, int.class).orElseThrow(() ->
            new IllegalStateException("AbstractBeanDefinition.getBeanForConstructorArgument(..) method not found. Incompatible version of Particle on the classpath?")
    );


    private static final Method GET_BEAN_FOR_FIELD = ReflectionUtils.getDeclaredMethod(AbstractBeanDefinition.class, "getBeanForField", BeanResolutionContext.class, BeanContext.class, int.class).orElseThrow(() ->
            new IllegalStateException("AbstractBeanDefinition.getBeanForField(..) method not found. Incompatible version of Particle on the classpath?")
    );

    private static final Method GET_VALUE_FOR_FIELD = ReflectionUtils.getDeclaredMethod(AbstractBeanDefinition.class, "getValueForField", BeanResolutionContext.class, BeanContext.class, int.class).orElseThrow(() ->
            new IllegalStateException("AbstractBeanDefinition.getValueForField(..) method not found. Incompatible version of Particle on the classpath?")
    );

    private static final Method GET_OPTIONAL_VALUE_FOR_FIELD = ReflectionUtils.getDeclaredMethod(AbstractBeanDefinition.class, "getValueForField", BeanResolutionContext.class, BeanContext.class, int.class, Object.class).orElseThrow(() ->
            new IllegalStateException("AbstractBeanDefinition.getValueForField(..) method not found. Incompatible version of Particle on the classpath?")
    );

    private static final Method GET_BEAN_FOR_METHOD_ARGUMENT = ReflectionUtils.getDeclaredMethod(AbstractBeanDefinition.class, "getBeanForMethodArgument", BeanResolutionContext.class, BeanContext.class, int.class, int.class).orElseThrow(() ->
            new IllegalStateException("AbstractBeanDefinition.getBeanForMethodArgument(..) method not found. Incompatible version of Particle on the classpath?")
    );

    private static final Method GET_VALUE_FOR_METHOD_ARGUMENT = ReflectionUtils.getDeclaredMethod(AbstractBeanDefinition.class, "getValueForMethodArgument", BeanResolutionContext.class, BeanContext.class, int.class, int.class).orElseThrow(() ->
            new IllegalStateException("AbstractBeanDefinition.getValueForMethodArgument(..) method not found. Incompatible version of Particle on the classpath?")
    );

    private static final Method GET_OPTIONAL_VALUE_FOR_METHOD_ARGUMENT = ReflectionUtils.getDeclaredMethod(AbstractBeanDefinition.class, "getValueForMethodArgument", BeanResolutionContext.class, BeanContext.class, int.class, int.class, Object.class).orElseThrow(() ->
            new IllegalStateException("AbstractBeanDefinition.getValueForMethodArgument(..) method not found. Incompatible version of Particle on the classpath?")
    );

    private final ClassVisitor classWriter;
    private final String beanFullClassName;
    private final String beanDefinitionName;
    private final String beanDefinitionInternalName;
    private final Type beanType;
    private final Type providedType;
    private final Type scope;
    private final boolean isSingleton;
    private final Set<Class> interfaceTypes;
    private final String providedBeanClassName;
    private MethodVisitor constructorVisitor;
    private MethodVisitor buildMethodVisitor;
    private MethodVisitor injectMethodVisitor;
    private MethodVisitor preDestroyMethodVisitor;
    private MethodVisitor postConstructMethodVisitor;
    private int defaultMaxStack = 13;
    private int constructorLocalVariableCount = 1;
    private int currentFieldIndex = 0;
    private int currentMethodIndex = 0;

    // 0 is this, while 1,2 and 3 are the first 3 parameters in the "build" method signature. See BeanFactory
    private int buildMethodLocalCount = 4;

    // 0 is this, while 1,2 and 3 are the first 3 parameters in the "injectBean" method signature. See AbstractBeanDefinition
    private int injectMethodLocalCount = 4;

    // 0 is this, while 1,2 and 3 are the first 3 parameters in the "initialize" method signature. See InitializingBeanDefinition
    private int postConstructMethodLocalCount = 4;

    // 0 is this, while 1,2 and 3 are the first 3 parameters in the "dispose" method signature. See DisposableBeanDefinition
    private int preDestroyMethodLocalCount = 4;

    // the instance being built position in the index
    private int buildInstanceIndex;
    private Integer injectValueIndex;
    private int injectInstanceIndex;
    private int postConstructInstanceIndex;
    private int preDestroyInstanceIndex;
    private boolean beanFinalized = false;

    /**
     * Creates a bean definition writer
     *
     * @param packageName The package name of the bean
     * @param className   The class name, without the package, of the bean
     * @param scope       The scope of the bean
     * @param isSingleton Is the scope a singleton
     */
    public BeanDefinitionWriter(String packageName,
                                String className,
                                String scope,
                                boolean isSingleton) {
        this(packageName, className, packageName + '.' + className, scope, isSingleton);
    }



    /**
     * Creates a bean definition writer
     *
     * @param packageName       The package name of the bean
     * @param className         The class name, without the package, of the bean
     * @param providedClassName The type this bean definition provides, in this case where the bean implements {@link javax.inject.Provider}
     * @param scope             The scope of the bean
     * @param isSingleton       Is the scope a singleton
     */
    public BeanDefinitionWriter(String packageName,
                                String className,
                                String providedClassName,
                                String scope,
                                boolean isSingleton) {
        this(packageName, className, packageName, providedClassName, scope, isSingleton);
    }

    /**
     * Creates a singleton bean definition writer
     *
     * @param packageName        The package name of the bean
     * @param className          The class name, without the package, of the bean
     * @param scope              The scope of the bean
     * @param beanDefinitionPackageName The name of the package to store the bean definition within
     */
    public BeanDefinitionWriter(String packageName,
                                String className,
                                String scope,
                                boolean isSingleton,
                                String beanDefinitionPackageName) {
        this(packageName, className, beanDefinitionPackageName, packageName + '.' + className,  scope, isSingleton);
    }

    /**
     * Creates a bean definition writer
     *
     * @param packageName       The package name of the bean
     * @param className         The class name, without the package, of the bean
     * @param providedClassName The type this bean definition provides, which differs from the class name in the case of factory beans
     * @param scope             The scope of the bean
     * @param isSingleton       Is the scope a singleton
     */
    public BeanDefinitionWriter(String packageName,
                                String className,
                                String beanDefinitionPackageName,
                                String providedClassName,
                                String scope,
                                boolean isSingleton) {
        this.classWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        this.beanFullClassName = packageName + '.' + className;
        this.providedBeanClassName = providedClassName;
        this.beanDefinitionName = beanDefinitionPackageName + ".$" + className + "Definition";
        this.beanType = getTypeReference(beanFullClassName);
        this.providedType = getTypeReference(providedBeanClassName);
        this.scope = scope != null ? getTypeReference(scope) : null;
        this.beanDefinitionInternalName = getInternalName(beanDefinitionName);
        this.isSingleton = isSingleton;
        this.interfaceTypes = new HashSet<>();
        this.interfaceTypes.add(BeanFactory.class);
    }

    /**
     * @return The full class name of the bean
     */
    public String getBeanTypeName() {
        return beanFullClassName;
    }

    /**
     * Make the bean definition as validated by javax.validation
     *
     * @param validated Whether the bean definition is validated
     */
    public void setValidated(boolean validated) {
        if (validated) {
            this.interfaceTypes.add(ValidatedBeanDefinition.class);
        } else {
            this.interfaceTypes.remove(ValidatedBeanDefinition.class);
        }
    }

    /**
     * @return Return whether the bean definition is validated
     */
    public boolean isValidated() {
        return this.interfaceTypes.contains(ValidatedBeanDefinition.class);
    }

    /**
     * @return The name of the bean definition class
     */
    public String getBeanDefinitionName() {
        return beanDefinitionName;
    }

    /**
     * @return The name of the bean definition class
     */
    public String getBeanDefinitionClassFile() {
        return getBeanDefinitionName().replace('.', File.separatorChar) + ".class";
    }

    /**
     * <p>In the case where the produced class is produced by a factory method annotated with {@link org.particleframework.context.annotation.Bean} this method should be called</p>
     *
     * @param factoryClass   The factory class
     * @param methodName     The method name
     * @param argumentTypes  The arguments to the method
     * @param qualifierTypes The qualifiers for the method parameters
     * @param genericTypes   The generic types for the method parameters
     */
    public void visitBeanFactoryMethod(Object factoryClass,
                                       String methodName,
                                       Map<String, Object> argumentTypes,
                                       Map<String, Object> qualifierTypes,
                                       Map<String, List<Object>> genericTypes) {
        if (constructorVisitor != null) {
            throw new IllegalStateException("Only a single call to visitBeanFactoryMethod(..) is permitted");
        } else {
            buildFactoryMethodClassConstructor(factoryClass, methodName, argumentTypes, qualifierTypes, genericTypes);

            // now implement the build method
            // now prepare the implementation of the build method. See BeanFactory interface
            visitBuildFactoryMethodDefinition(factoryClass, methodName, argumentTypes);

            // now override the injectBean method
            visitInjectMethodDefinition();

        }
    }



    private void buildFactoryMethodClassConstructor(Object factoryClass, String methodName, Map<String, Object> argumentTypes, Map<String, Object> qualifierTypes, Map<String, List<Object>> genericTypes) {
        Type factoryTypeRef = getTypeReference(factoryClass);
        this.constructorVisitor = startConstructor(classWriter);
        // ALOAD 0
        constructorVisitor.visitVarInsn(ALOAD, 0);

        // First constructor argument: The factory method
        boolean hasArgs = !argumentTypes.isEmpty();
        Collection<Object> argumentTypeClasses = hasArgs ? argumentTypes.values() : Collections.emptyList();
        // load 'this'
        constructorVisitor.visitVarInsn(ALOAD, 0);

        pushGetMethodFromTypeCall(constructorVisitor, factoryTypeRef, methodName, argumentTypeClasses);


        if(hasArgs) {
            // 2nd Argument: Create a call to createMap from an argument types
            pushCreateMapCall(this.constructorVisitor, argumentTypes);

            // 3rd Argument: Create a call to createMap from qualifier types
            if (qualifierTypes != null) {
                pushCreateMapCall(this.constructorVisitor, qualifierTypes);
            } else {
                constructorVisitor.visitInsn(ACONST_NULL);
            }

            // 4th Argument: Create a call to createMap from generic types
            if (genericTypes != null) {
                pushCreateGenericsMapCall(this.constructorVisitor, genericTypes);
            } else {
                constructorVisitor.visitInsn(ACONST_NULL);
            }
            // now invoke super(..) if no arg constructor
            invokeConstructor(constructorVisitor, AbstractBeanDefinition.class, Method.class, Map.class, Map.class, Map.class);
        }
        else {
            invokeConstructor(constructorVisitor, AbstractBeanDefinition.class, Method.class);
        }
    }


    /**
     * Visits the constructor used to create the bean definition.
     *
     * @param argumentTypes  The argument type names for each parameter
     * @param qualifierTypes The qualifier type names for each parameter
     * @param genericTypes   The generic types for each parameter
     */
    public void visitBeanDefinitionConstructor(Map<String, Object> argumentTypes,
                                               Map<String, Object> qualifierTypes,
                                               Map<String, List<Object>> genericTypes) {
        if (constructorVisitor == null) {
            // first build the constructor
            visitBeanDefinitionConstructorInternal(argumentTypes, qualifierTypes, genericTypes);

            // now prepare the implementation of the build method. See BeanFactory interface
            visitBuildMethodDefinition(argumentTypes);

            // now override the injectBean method
            visitInjectMethodDefinition();
        }
    }

    /**
     * Visits a no-args constructor used to create the bean definition.
     */
    public void visitBeanDefinitionConstructor() {
        if (constructorVisitor == null) {
            // first build the constructor
            visitBeanDefinitionConstructorInternal(Collections.emptyMap(), null, null);

            // now prepare the implementation of the build method. See BeanFactory interface
            visitBuildMethodDefinition(Collections.emptyMap());

            // now override the injectBean method
            visitInjectMethodDefinition();
        }
    }

    /**
     * Finalize the bean definition to the given output stream
     */
    public void visitBeanDefinitionEnd() throws IOException {
        if (classWriter instanceof ClassWriter) {
            String[] interfaceInternalNames = new String[interfaceTypes.size()];
            Iterator<Class> j = interfaceTypes.iterator();
            for (int i = 0; i < interfaceInternalNames.length; i++) {
                interfaceInternalNames[i] = Type.getInternalName(j.next());
            }
            classWriter.visit(V1_8, ACC_PUBLIC,
                    beanDefinitionInternalName,
                    generateBeanDefSig(providedType.getInternalName()),
                    Type.getInternalName(AbstractBeanDefinition.class),
                    interfaceInternalNames);

            if (buildMethodVisitor == null) {
                throw new IllegalStateException("At least one call to visitBeanDefinitionConstructor() is required");
            }

            finalizeInjectMethod();
            finalizeBuildMethod();
            constructorVisitor.visitInsn(RETURN);
            constructorVisitor.visitMaxs(defaultMaxStack, 1);
            if (buildMethodVisitor != null) {
                buildMethodVisitor.visitInsn(ARETURN);
                buildMethodVisitor.visitMaxs(defaultMaxStack, buildMethodLocalCount);
            }
            if (injectMethodVisitor != null) {
                injectMethodVisitor.visitMaxs(defaultMaxStack, injectMethodLocalCount);
            }
            if (postConstructMethodVisitor != null) {
                postConstructMethodVisitor.visitVarInsn(ALOAD, postConstructInstanceIndex);
                postConstructMethodVisitor.visitInsn(ARETURN);
                postConstructMethodVisitor.visitMaxs(defaultMaxStack, postConstructMethodLocalCount);
            }
            if (preDestroyMethodVisitor != null) {
                preDestroyMethodVisitor.visitVarInsn(ALOAD, preDestroyInstanceIndex);
                preDestroyMethodVisitor.visitInsn(ARETURN);
                preDestroyMethodVisitor.visitMaxs(defaultMaxStack, preDestroyMethodLocalCount);
            }

            classWriter.visitEnd();
        }
        this.beanFinalized = true;
    }

    /**
     * Write the class to the given output stream
     *
     * @param outputStream The output stream
     * @throws IOException If an error occurs
     */
    public void writeTo(OutputStream outputStream) throws IOException {
        outputStream.write(toByteArray());
    }

    /**
     * @return The bytes of the class
     */
    public byte[] toByteArray() {
        if (!beanFinalized) {
            throw new IllegalStateException("Bean definition not finalized. Call visitBeanDefinitionEnd() first.");
        }
        return ((ClassWriter) classWriter).toByteArray();
    }

    /**
     * Write the class to the given output stream
     *
     * @param compilationDir The compilation dir
     * @throws IOException If an error occurs
     */
    public void writeTo(File compilationDir) throws IOException {
        File targetFile = new File(compilationDir, getBeanDefinitionClassFile()).getCanonicalFile();
        File parentDir = targetFile.getParentFile();
        if (!parentDir.exists() && !parentDir.mkdirs()) {
            throw new IOException("Cannot create parent directory: " + targetFile.getParentFile());
        }
        try (FileOutputStream out = new FileOutputStream(targetFile)) {
            writeTo(out);
        }
    }

    /**
     * Visits an injection point for a field and setter pairing.
     *
     * @param declaringType      The declaring type
     * @param qualifierType      The qualifier type
     * @param requiresReflection Whether the setter requires reflection
     * @param fieldType          The field type
     * @param fieldName          The field name
     */
    public void visitSetterInjectionPoint(Object declaringType,
                                          Object qualifierType,
                                          boolean requiresReflection,
                                          Object fieldType,
                                          String fieldName,
                                          String setterName,
                                          List<Object> genericTypes) {
        Type declaringTypeRef = getTypeReference(declaringType);

        addInjectionPointForSetterInternal(qualifierType, requiresReflection, fieldType, fieldName, setterName, genericTypes, declaringTypeRef);

        if (!requiresReflection) {
            resolveBeanOrValueForSetter(declaringTypeRef, fieldName, setterName, fieldType, GET_BEAN_FOR_METHOD_ARGUMENT);

        }
        currentMethodIndex++;
    }

    /**
     * Visits an injection point for a field and setter pairing.
     *
     * @param declaringType      The declaring type
     * @param qualifierType      The qualifier type
     * @param requiresReflection Whether the setter requires reflection
     * @param fieldType          The field type
     * @param fieldName          The field name
     */
    public void visitSetterValue(Object declaringType,
                                 Object qualifierType,
                                 boolean requiresReflection,
                                 Object fieldType,
                                 String fieldName,
                                 String setterName,
                                 List<Object> genericTypes,
                                 boolean isOptional) {
        Type declaringTypeRef = getTypeReference(declaringType);

        addInjectionPointForSetterInternal(qualifierType, requiresReflection, fieldType, fieldName, setterName, genericTypes, declaringTypeRef);

        if (!requiresReflection) {
            resolveBeanOrValueForSetter(declaringTypeRef, fieldName, setterName, fieldType, isOptional ? GET_OPTIONAL_VALUE_FOR_METHOD_ARGUMENT : GET_VALUE_FOR_METHOD_ARGUMENT);
        }
        currentMethodIndex++;
    }

    private void addInjectionPointForSetterInternal(Object qualifierType, boolean requiresReflection, Object fieldType, String fieldName, String setterName, List<Object> genericTypes, Type declaringTypeRef) {
        int fieldVarIndex = pushGetFieldFromTypeLocalVariable(constructorVisitor, declaringTypeRef, fieldName);

        // load this
        constructorVisitor.visitVarInsn(ALOAD, 0);
        // 1st argument: the field
        constructorVisitor.visitVarInsn(ALOAD, fieldVarIndex);

        // 2nd argument: the method
        pushGetMethodFromTypeCall(constructorVisitor, declaringTypeRef, setterName, Collections.singletonList(fieldType));

        // 3rd argument: the qualifier
        if (qualifierType != null) {
            constructorVisitor.visitVarInsn(ALOAD, fieldVarIndex);
            pushGetAnnotationForField(constructorVisitor, getTypeReference(qualifierType));
        } else {
            constructorVisitor.visitInsn(ACONST_NULL);
        }

        // 4th argument: generic types
        if (genericTypes != null) {
            pushNewListOfTypes(constructorVisitor, genericTypes);
        } else {
            constructorVisitor.visitInsn(ACONST_NULL);
        }

        // 5th argument: requires reflection
        constructorVisitor.visitInsn(requiresReflection ? ICONST_1 : ICONST_0);

        // now invoke the addInjectionPoint method
        pushInvokeMethodOnSuperClass(constructorVisitor, ADD_SETTER_INJECTION_POINT_METHOD);
    }

    private void resolveBeanOrValueForSetter(Type declaringTypeRef, String fieldName, String setterName, Object fieldType, Method resolveMethod) {
        // invoke the method on this injected instance
        injectMethodVisitor.visitVarInsn(ALOAD, injectInstanceIndex);
        String methodDescriptor = getMethodDescriptor("void", Collections.singletonList(fieldType));
        // first get the value of the field by calling AbstractBeanDefinition.getBeanForField(..)
        // load 'this'
        injectMethodVisitor.visitVarInsn(ALOAD, 0);
        // 1st argument load BeanResolutionContext
        injectMethodVisitor.visitVarInsn(ALOAD, 1);
        // 2nd argument load BeanContext
        injectMethodVisitor.visitVarInsn(ALOAD, 2);
        // 3rd argument the field index
        pushIntegerConstant(injectMethodVisitor, currentMethodIndex);
        // 4th argument the argument index
        pushIntegerConstant(injectMethodVisitor, 0);
        if (resolveMethod == GET_OPTIONAL_VALUE_FOR_METHOD_ARGUMENT) {
            injectMethodVisitor.visitVarInsn(ALOAD, injectInstanceIndex);
            String getterName = "get" + MetaClassHelper.capitalize(fieldName);
            groovyjarjarasm.asm.commons.Method getterMethod = groovyjarjarasm.asm.commons.Method.getMethod(getTypeReference(fieldType).getClassName() + " " + getterName + "()");
            injectMethodVisitor.visitMethodInsn(INVOKEVIRTUAL, declaringTypeRef.getInternalName(), getterName, getterMethod.getDescriptor(), false);
            pushBoxPrimitiveIfNecessary(fieldType, injectMethodVisitor);
        }
        // invoke getBeanForField
        pushInvokeMethodOnSuperClass(injectMethodVisitor, resolveMethod);
        // cast the return value to the correct type
        pushCastToType(injectMethodVisitor, fieldType);
        injectMethodVisitor.visitMethodInsn(INVOKEVIRTUAL,
                declaringTypeRef.getInternalName(), setterName,
                methodDescriptor, false);
    }

    /**
     * Visits a method injection point
     *
     * @param declaringType      The declaring type of the method. Either a Class or a string representing the name of the type
     * @param requiresReflection Whether the method requires reflection
     * @param returnType         The return type of the method. Either a Class or a string representing the name of the type
     * @param methodName         The method name
     * @param argumentTypes      The argument types. Note: an ordered map should be used such as LinkedHashMap. Can be null or empty.
     * @param qualifierTypes     The qualifier types of each argument. Can be null.
     * @param genericTypes       The generic types of each argument. Can be null.
     */
    public void visitPostConstructMethod(Object declaringType,
                                         boolean requiresReflection,
                                         Object returnType,
                                         String methodName,
                                         Map<String, Object> argumentTypes,
                                         Map<String, Object> qualifierTypes,
                                         Map<String, List<Object>> genericTypes) {
        visitPostConstructMethodDefinition();

        visitMethodInjectionPointInternal(declaringType, requiresReflection, returnType, methodName, argumentTypes, qualifierTypes, genericTypes, constructorVisitor, postConstructMethodVisitor, postConstructInstanceIndex);
    }

    /**
     * Visits a method injection point
     *
     * @param declaringType      The declaring type of the method. Either a Class or a string representing the name of the type
     * @param requiresReflection Whether the method requires reflection
     * @param returnType         The return type of the method. Either a Class or a string representing the name of the type
     * @param methodName         The method name
     */
    public void visitPreDestroyMethod(Object declaringType,
                                      boolean requiresReflection,
                                      Object returnType,
                                      String methodName) {
        visitPreDestroyMethodDefinition();
        visitMethodInjectionPointInternal(declaringType, requiresReflection, returnType, methodName, Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap(), constructorVisitor, preDestroyMethodVisitor, preDestroyInstanceIndex);
    }


    /**
     * Visits a method injection point
     *
     * @param declaringType      The declaring type of the method. Either a Class or a string representing the name of the type
     * @param returnType         The return type of the method. Either a Class or a string representing the name of the type
     * @param methodName         The method name
     */
    public void visitPreDestroyMethod(Object declaringType,
                                      Object returnType,
                                      String methodName) {
        visitPreDestroyMethodDefinition();
        visitMethodInjectionPointInternal(declaringType, false, returnType, methodName, Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap(), constructorVisitor, preDestroyMethodVisitor, preDestroyInstanceIndex);
    }

    /**
     * Visits a pre-destroy method injection point
     *
     * @param declaringType      The declaring type of the method. Either a Class or a string representing the name of the type
     * @param methodName         The method name
     */
    public void visitPreDestroyMethod(Object declaringType,
                                      String methodName) {
        visitPreDestroyMethodDefinition();
        visitMethodInjectionPointInternal(declaringType, false, Void.TYPE, methodName, Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap(), constructorVisitor, preDestroyMethodVisitor, preDestroyInstanceIndex);
    }
    /**
     * Visits a method injection point
     *
     * @param declaringType      The declaring type of the method. Either a Class or a string representing the name of the type
     * @param requiresReflection Whether the method requires reflection
     * @param returnType         The return type of the method. Either a Class or a string representing the name of the type
     * @param methodName         The method name
     * @param argumentTypes      The argument types. Note: an ordered map should be used such as LinkedHashMap. Can be null or empty.
     * @param qualifierTypes     The qualifier types of each argument. Can be null.
     * @param genericTypes       The generic types of each argument. Can be null.
     */
    public void visitPreDestroyMethod(Object declaringType,
                                      boolean requiresReflection,
                                      Object returnType,
                                      String methodName,
                                      Map<String, Object> argumentTypes,
                                      Map<String, Object> qualifierTypes,
                                      Map<String, List<Object>> genericTypes) {
        visitPreDestroyMethodDefinition();
        visitMethodInjectionPointInternal(declaringType, requiresReflection, returnType, methodName, argumentTypes, qualifierTypes, genericTypes, constructorVisitor, preDestroyMethodVisitor, preDestroyInstanceIndex);
    }
    /**
     * Visits a method injection point
     *
     * @param requiresReflection Whether the method requires reflection
     * @param returnType         The return type of the method. Either a Class or a string representing the name of the type
     * @param methodName         The method name
     * @param argumentTypes      The argument types. Note: an ordered map should be used such as LinkedHashMap. Can be null or empty.
     */
    public void visitMethodInjectionPoint(boolean requiresReflection,
                                          Object returnType,
                                          String methodName,
                                          Map<String, Object> argumentTypes) {
        visitMethodInjectionPoint(beanFullClassName, requiresReflection, returnType, methodName, argumentTypes, null, null);
    }

    /**
     * Visits a method injection point
     *
     * @param requiresReflection Whether the method requires reflection
     * @param returnType         The return type of the method. Either a Class or a string representing the name of the type
     * @param methodName         The method name
     * @param argumentTypes      The argument types. Note: an ordered map should be used such as LinkedHashMap. Can be null or empty.
     * @param qualifierTypes     The qualifier types of each argument. Can be null.
     * @param genericTypes       The generic types of each argument. Can be null.
     */
    public void visitMethodInjectionPoint(boolean requiresReflection,
                                          Object returnType,
                                          String methodName,
                                          Map<String, Object> argumentTypes,
                                          Map<String, Object> qualifierTypes,
                                          Map<String, List<Object>> genericTypes) {
        visitMethodInjectionPoint(beanFullClassName, requiresReflection, returnType, methodName, argumentTypes, qualifierTypes, genericTypes);
    }


    /**
     * Visits a method injection point
     *
     * @param declaringType      The declaring type of the method. Either a Class or a string representing the name of the type
     * @param requiresReflection Whether the method requires reflection
     * @param returnType         The return type of the method. Either a Class or a string representing the name of the type
     * @param methodName         The method name
     * @param argumentTypes      The argument types. Note: an ordered map should be used such as LinkedHashMap. Can be null or empty.
     * @param qualifierTypes     The qualifier types of each argument. Can be null.
     * @param genericTypes       The generic types of each argument. Can be null.
     */
    public void visitMethodInjectionPoint(Object declaringType,
                                          boolean requiresReflection,
                                          Object returnType,
                                          String methodName,
                                          Map<String, Object> argumentTypes,
                                          Map<String, Object> qualifierTypes,
                                          Map<String, List<Object>> genericTypes) {
        MethodVisitor constructorVisitor = this.constructorVisitor;
        MethodVisitor injectMethodVisitor = this.injectMethodVisitor;
        int injectInstanceIndex = this.injectInstanceIndex;

        visitMethodInjectionPointInternal(declaringType, requiresReflection, returnType, methodName, argumentTypes, qualifierTypes, genericTypes, constructorVisitor, injectMethodVisitor, injectInstanceIndex);
    }

    private void visitMethodInjectionPointInternal(Object declaringType,
                                                   boolean requiresReflection,
                                                   Object returnType,
                                                   String methodName,
                                                   Map<String, Object> argumentTypes,
                                                   Map<String, Object> qualifierTypes,
                                                   Map<String, List<Object>> genericTypes,
                                                   MethodVisitor constructorVisitor,
                                                   MethodVisitor injectMethodVisitor,
                                                   int injectInstanceIndex) {
        boolean hasArguments = argumentTypes != null && !argumentTypes.isEmpty();
        int argCount = hasArguments ? argumentTypes.size() : 0;
        Type declaringTypeRef = getTypeReference(declaringType);


        Collection<Object> argumentTypeClasses = hasArguments ? argumentTypes.values() : Collections.emptyList();
        // load 'this'
        constructorVisitor.visitVarInsn(ALOAD, 0);

        pushGetMethodFromTypeCall(constructorVisitor, declaringTypeRef, methodName, argumentTypeClasses);

        if (hasArguments) {
            // 2nd argument to addInjectPoint: The argument types
            pushCreateMapCall(constructorVisitor, argumentTypes);
            // 3rd argument to addInjectPoint: The qualifiers
            if (qualifierTypes != null) {
                pushCreateMapCall(constructorVisitor, qualifierTypes);
            } else {
                constructorVisitor.visitInsn(ACONST_NULL);
            }
            if (genericTypes != null) {
                pushCreateGenericsMapCall(constructorVisitor, genericTypes);
            } else {
                constructorVisitor.visitInsn(ACONST_NULL);
            }
        } else {
            // 2nd argument to addInjectPoint: The argument types
            constructorVisitor.visitInsn(ACONST_NULL);
            // 3rd argument to addInjectPoint: The qualifiers
            constructorVisitor.visitInsn(ACONST_NULL);
            // 4th argument to addInjectPoint: The generic types
            constructorVisitor.visitInsn(ACONST_NULL);
        }

        // 5th argument to addInjectionPoint: do we need reflection?
        constructorVisitor.visitInsn(requiresReflection ? ICONST_1 : ICONST_0);

        // invoke add injection point method
        pushInvokeMethodOnSuperClass(constructorVisitor, ADD_METHOD_INJECTION_POINT_METHOD);

        if (!requiresReflection) {
            // if the method doesn't require reflection then invoke it directly

            // invoke the method on this injected instance
            injectMethodVisitor.visitVarInsn(ALOAD, injectInstanceIndex);


            String methodDescriptor;
            if (hasArguments) {
                methodDescriptor = getMethodDescriptor(returnType, argumentTypeClasses);
                Iterator<Object> argIterator = argumentTypeClasses.iterator();
                for (int i = 0; i < argCount; i++) {
                    // first get the value of the field by calling AbstractBeanDefinition.getBeanForField(..)
                    // load 'this'
                    injectMethodVisitor.visitVarInsn(ALOAD, 0);
                    // 1st argument load BeanResolutionContext
                    injectMethodVisitor.visitVarInsn(ALOAD, 1);
                    // 2nd argument load BeanContext
                    injectMethodVisitor.visitVarInsn(ALOAD, 2);
                    // 3rd argument the method index
                    pushIntegerConstant(injectMethodVisitor, currentMethodIndex);
                    // 4th argument the argument index
                    pushIntegerConstant(injectMethodVisitor, i);
                    // invoke getBeanForField
                    pushInvokeMethodOnSuperClass(injectMethodVisitor, GET_BEAN_FOR_METHOD_ARGUMENT);
                    // cast the return value to the correct type
                    pushCastToType(injectMethodVisitor, argIterator.next());
                }
            } else {
                methodDescriptor = getMethodDescriptor(returnType, Collections.emptyList());
            }
            injectMethodVisitor.visitMethodInsn(INVOKEVIRTUAL,
                    declaringTypeRef.getInternalName(), methodName,
                    methodDescriptor, false);
        }

        // increment the method index
        currentMethodIndex++;
    }

    private void pushGetMethodFromTypeCall(MethodVisitor methodVisitor, Type declaringType, String methodName, Collection<Object> argumentTypes) {
        int argTypeCount = argumentTypes.size();
        // lookup the Method instance from the declaring type
        methodVisitor.visitLdcInsn(declaringType);

        // and the method name
        methodVisitor.visitLdcInsn(methodName);


        if (!argumentTypes.isEmpty()) {
            pushNewArray(methodVisitor, Class.class, argTypeCount);
            Iterator<Object> argIterator = argumentTypes.iterator();
            for (int i = 0; i < argTypeCount; i++) {
                pushStoreTypeInArray(methodVisitor, i, argTypeCount, argIterator.next());
            }
        } else {
            // no arguments
            pushNewArray(methodVisitor, Class.class, 0);
        }

        // 1st argument to addInjectPoint: The Method
        pushInvokeMethodOnClass(methodVisitor, "getDeclaredMethod", String.class, Class[].class);
    }

    /**
     * Visits a field injection point
     *
     * @param qualifierType      The qualifier type. Either a Class or a string representing the name of the type
     * @param requiresReflection Whether accessing the field requires reflection
     * @param fieldType          The type of the field
     * @param fieldName          The name of the field
     */
    public void visitFieldInjectionPoint(Object qualifierType,
                                         boolean requiresReflection,
                                         Object fieldType,
                                         String fieldName) {
        visitFieldInjectionPoint(beanFullClassName, qualifierType, requiresReflection, fieldType, fieldName);
    }

    /**
     * Visits a field injection point
     *
     * @param requiresReflection Whether accessing the field requires reflection
     * @param fieldType          The type of the field
     * @param fieldName          The name of the field
     */
    public void visitFieldInjectionPoint(boolean requiresReflection,
                                         Object fieldType,
                                         String fieldName) {
        visitFieldInjectionPoint(beanFullClassName, null, requiresReflection, fieldType, fieldName);
    }

    /**
     * Visits a field injection point
     *
     * @param declaringType      The declaring type. Either a Class or a string representing the name of the type
     * @param qualifierType      The qualifier type. Either a Class or a string representing the name of the type
     * @param requiresReflection Whether accessing the field requires reflection
     * @param fieldType          The type of the field
     * @param fieldName          The name of the field
     */
    public void visitFieldInjectionPoint(Object declaringType,
                                         Object qualifierType,
                                         boolean requiresReflection,
                                         Object fieldType,
                                         String fieldName) {
        // Implementation notes.
        // This method modifies the constructor adding addInjectPoint calls for each field that is annotated with @Inject
        // The constructor is a zero args constructor therefore there are no other local variables and "this" is stored in the 0 index.
        // The "currentFieldIndex" variable is used as a reference point for both the position of the local variable and also
        // for later on within the "build" method to in order to call "getBeanForField" with the appropriate index
        visitFieldInjectionPointInternal(declaringType, qualifierType, requiresReflection, fieldType, fieldName, GET_BEAN_FOR_FIELD, false);
    }

    /**
     * Visits a field injection point
     *
     * @param declaringType      The declaring type. Either a Class or a string representing the name of the type
     * @param qualifierType      The qualifier type. Either a Class or a string representing the name of the type
     * @param requiresReflection Whether accessing the field requires reflection
     * @param fieldType          The type of the field
     * @param fieldName          The name of the field
     */
    public void visitFieldValue(Object declaringType,
                                Object qualifierType,
                                boolean requiresReflection,
                                Object fieldType,
                                String fieldName,
                                boolean isOptional) {
        // Implementation notes.
        // This method modifies the constructor adding addInjectPoint calls for each field that is annotated with @Inject
        // The constructor is a zero args constructor therefore there are no other local variables and "this" is stored in the 0 index.
        // The "currentFieldIndex" variable is used as a reference point for both the position of the local variable and also
        // for later on within the "build" method to in order to call "getBeanForField" with the appropriate index
        visitFieldInjectionPointInternal(declaringType, qualifierType, requiresReflection, fieldType, fieldName, isOptional ? GET_OPTIONAL_VALUE_FOR_FIELD : GET_VALUE_FOR_FIELD, isOptional);
    }

    private void visitFieldInjectionPointInternal(Object declaringType, Object qualifierType, boolean requiresReflection, Object fieldType, String fieldName, Method methodToInvoke, boolean isOptional) {
        // ready this
        MethodVisitor constructorVisitor = this.constructorVisitor;
        constructorVisitor.visitVarInsn(ALOAD, 0);

        // lookup the Field instance from the declaring type
        Type declaringTypeRef = getTypeReference(declaringType);
        int fieldVarIndex = pushGetFieldFromTypeLocalVariable(constructorVisitor, declaringTypeRef, fieldName);

        // first argument to the method is the Field reference
        // load the first argument. The field.
        constructorVisitor.visitVarInsn(ALOAD, fieldVarIndex);

        // second argument is the annotation or null
        // pass the qualifier type if present
        if (qualifierType != null) {

            constructorVisitor.visitVarInsn(ALOAD, fieldVarIndex);
            pushGetAnnotationForField(constructorVisitor, getTypeReference(qualifierType));
        } else {
            constructorVisitor.visitInsn(ACONST_NULL);
        }

        // third argument is whether it requires reflection
        constructorVisitor.visitInsn(requiresReflection ? ICONST_1 : ICONST_0);

        // invoke addInjectionPoint method
        pushInvokeMethodOnSuperClass(constructorVisitor, ADD_FIELD_INJECTION_POINT_METHOD);


        MethodVisitor injectMethodVisitor = this.injectMethodVisitor;
        if (!requiresReflection) {
            // if reflection is not required then set the field automatically within the body of the "injectBean" method

            boolean isOptionalValue = methodToInvoke == GET_OPTIONAL_VALUE_FOR_FIELD && isOptional;
            injectMethodVisitor.visitVarInsn(ALOAD, injectInstanceIndex);
            // first get the value of the field by calling AbstractBeanDefinition.getBeanForField(..)
            // load 'this'
            injectMethodVisitor.visitVarInsn(ALOAD, 0);
            // 1st argument load BeanResolutionContext
            injectMethodVisitor.visitVarInsn(ALOAD, 1);
            // 2nd argument load BeanContext
            injectMethodVisitor.visitVarInsn(ALOAD, 2);
            // 3rd argument the field index
            pushIntegerConstant(injectMethodVisitor, currentFieldIndex);
            // 4th argument the current value
            if (isOptionalValue) {
                injectMethodVisitor.visitVarInsn(ALOAD, injectInstanceIndex);
                injectMethodVisitor.visitFieldInsn(GETFIELD, declaringTypeRef.getInternalName(), fieldName, getTypeDescriptor(fieldType));
                pushBoxPrimitiveIfNecessary(fieldType, injectMethodVisitor);
            }
            // invoke getBeanForField
            pushInvokeMethodOnSuperClass(injectMethodVisitor, methodToInvoke);
            // cast the return value to the correct type
            pushCastToType(injectMethodVisitor, fieldType);

            injectMethodVisitor.visitFieldInsn(PUTFIELD, declaringTypeRef.getInternalName(), fieldName, getTypeDescriptor(fieldType));
        }
        currentFieldIndex++;
    }

    private void pushBoxPrimitiveIfNecessary(Object fieldType, MethodVisitor injectMethodVisitor) {
        Class wrapperType = getWrapperType(fieldType);
        if (wrapperType != null) {
            Class primitiveType = (Class) fieldType;
            Type wrapper = Type.getType(wrapperType);
            String primitiveName = primitiveType.getName();
            String sig = wrapperType.getName() + " valueOf(" + primitiveName + ")";
            groovyjarjarasm.asm.commons.Method valueOfMethod = groovyjarjarasm.asm.commons.Method.getMethod(sig);
            injectMethodVisitor.visitMethodInsn(INVOKESTATIC, wrapper.getInternalName(), "valueOf", valueOfMethod.getDescriptor(), false);
        }
    }

    private Class getWrapperType(Object type) {
        if (type instanceof Class) {
            Class typeClass = (Class) type;
            if (typeClass.isPrimitive()) {
                return ReflectionUtils.getWrapperType(typeClass);
            }
        }
        return null;
    }

    private int pushGetFieldFromTypeLocalVariable(MethodVisitor methodVisitor, Type declaringType, String fieldName) {
        methodVisitor.visitLdcInsn(declaringType);
        // and the field name
        methodVisitor.visitLdcInsn(fieldName);
        pushInvokeMethodOnClass(methodVisitor, "getDeclaredField", String.class);

        // store the field within using the field index. A pre-increment is used because 0 contains "this"
        return pushNewConstructorLocalVariable();
    }

    private void pushInvokeMethodOnSuperClass(MethodVisitor constructorVisitor, Method methodToInvoke) {
        constructorVisitor.visitMethodInsn(INVOKEVIRTUAL,
                Type.getInternalName(AbstractBeanDefinition.class),
                methodToInvoke.getName(),
                Type.getMethodDescriptor(methodToInvoke),
                false);
    }

    private void pushInvokeMethodOnClass(MethodVisitor methodVisitor, String classMethodName, Class... classMethodArgs) {
        Method method = ReflectionUtils.getDeclaredMethod(Class.class, classMethodName, classMethodArgs)
                .orElseThrow(() ->
                        new IllegalStateException("Class." + classMethodName + "(..) method not found")
                );
        methodVisitor.visitMethodInsn(INVOKEVIRTUAL,
                Type.getInternalName(Class.class),
                classMethodName,
                Type.getMethodDescriptor(method),
                false);
    }

    private void visitInjectMethodDefinition() {
        if (injectMethodVisitor == null) {
            injectMethodVisitor = classWriter.visitMethod(
                    ACC_PROTECTED,
                    "injectBean",
                    getMethodDescriptor(Object.class.getName(), BeanResolutionContext.class.getName(), BeanContext.class.getName(), Object.class.getName()),
                    null,
                    null);

            MethodVisitor injectMethodVisitor = this.injectMethodVisitor;
            // The object being injected is argument 3 of the inject method
            injectMethodVisitor.visitVarInsn(ALOAD, 3);
            // store it in a local variable
            injectMethodVisitor.visitTypeInsn(CHECKCAST, beanType.getInternalName());
            injectInstanceIndex = pushNewInjectLocalVariable();

            invokeSuperInjectMethod(injectMethodVisitor, INJECT_BEAN_FIELDS_METHOD);
        }
    }

    private void visitPostConstructMethodDefinition() {
        if (postConstructMethodVisitor == null) {
            interfaceTypes.add(InitializingBeanDefinition.class);

            // override the post construct method
            MethodVisitor postConstructMethodVisitor = newLifeCycleMethod("initialize");

            this.postConstructMethodVisitor = postConstructMethodVisitor;
            // The object being injected is argument 3 of the inject method
            postConstructMethodVisitor.visitVarInsn(ALOAD, 3);
            // store it in a local variable
            postConstructMethodVisitor.visitTypeInsn(CHECKCAST, beanType.getInternalName());
            postConstructInstanceIndex = pushNewPostConstructLocalVariable();

            invokeSuperInjectMethod(postConstructMethodVisitor, POST_CONSTRUCT_METHOD);

            pushBeanDefinitionMethodInvocation(buildMethodVisitor, "initialize");
            pushCastToType(buildMethodVisitor, beanFullClassName);
            buildMethodVisitor.visitVarInsn(ASTORE, buildInstanceIndex);
        }
    }

    private void visitPreDestroyMethodDefinition() {
        if (preDestroyMethodVisitor == null) {
            interfaceTypes.add(DisposableBeanDefinition.class);

            // override the post construct method
            MethodVisitor preDestroyMethodVisitor = newLifeCycleMethod("dispose");

            this.preDestroyMethodVisitor = preDestroyMethodVisitor;
            // The object being injected is argument 3 of the inject method
            preDestroyMethodVisitor.visitVarInsn(ALOAD, 3);
            // store it in a local variable
            preDestroyMethodVisitor.visitTypeInsn(CHECKCAST, beanType.getInternalName());
            preDestroyInstanceIndex = pushNewPreDestroyLocalVariable();

            invokeSuperInjectMethod(preDestroyMethodVisitor, PRE_DESTROY_METHOD);
        }
    }

    private MethodVisitor newLifeCycleMethod(String methodName) {
        return classWriter.visitMethod(
                ACC_PUBLIC,
                methodName,
                getMethodDescriptor(Object.class.getName(), BeanResolutionContext.class.getName(), BeanContext.class.getName(), Object.class.getName()),
                getMethodSignature(getTypeDescriptor(providedBeanClassName), getTypeDescriptor(BeanResolutionContext.class.getName()), getTypeDescriptor(BeanContext.class.getName()), getTypeDescriptor(providedBeanClassName)),
                null);
    }

    private void finalizeBuildMethod() {
        // if this is a provided bean then execute "get"
        if (!providedBeanClassName.equals(beanFullClassName)) {

            buildMethodVisitor.visitVarInsn(ASTORE, buildInstanceIndex);
            buildMethodVisitor.visitVarInsn(ALOAD, buildInstanceIndex);
            buildMethodVisitor.visitMethodInsn(INVOKEVIRTUAL,
                    beanType.getInternalName(),
                    "get",
                    Type.getMethodDescriptor(Type.getType(Object.class)),
                    false);
            pushCastToType(buildMethodVisitor, providedBeanClassName);
            buildMethodVisitor.visitVarInsn(ASTORE, buildInstanceIndex);
            pushBeanDefinitionMethodInvocation(buildMethodVisitor, "injectAnother");
            pushCastToType(buildMethodVisitor, providedBeanClassName);
        }
    }

    private void finalizeInjectMethod() {
        invokeSuperInjectMethod(injectMethodVisitor, INJECT_BEAN_METHODS_METHOD);

        injectMethodVisitor.visitVarInsn(ALOAD, 3);
        injectMethodVisitor.visitInsn(ARETURN);
    }

    private void invokeSuperInjectMethod(MethodVisitor methodVisitor, Method methodToInvoke) {
        // load this
        methodVisitor.visitVarInsn(ALOAD, 0);
        // load BeanResolutionContext arg 1
        methodVisitor.visitVarInsn(ALOAD, 1);
        // load BeanContext arg 2
        methodVisitor.visitVarInsn(ALOAD, 2);
        pushCastToType(methodVisitor, DefaultBeanContext.class);

        // load object being inject arg 3
        methodVisitor.visitVarInsn(ALOAD, 3);
        pushInvokeMethodOnSuperClass(methodVisitor, methodToInvoke);
    }

    private void visitBuildFactoryMethodDefinition(Object factoryClass, String methodName, Map<String, Object> argumentTypes) {
        if (buildMethodVisitor == null) {
            defineBuilderMethod();
            // load this

            MethodVisitor buildMethodVisitor = this.buildMethodVisitor;
            // Load the BeanContext for the method call
            buildMethodVisitor.visitVarInsn(ALOAD, 2);
            pushCastToType(buildMethodVisitor, DefaultBeanContext.class);
            // load the first argument of the method (the BeanResolutionContext) to be passed to the method
            buildMethodVisitor.visitVarInsn(ALOAD, 1);
            // second argument is the bean type
            Type factoryType = getTypeReference(factoryClass);
            buildMethodVisitor.visitLdcInsn(factoryType);
            Method getBeanMethod = ReflectionUtils.getDeclaredMethod(DefaultBeanContext.class, "getBean", BeanResolutionContext.class, Class.class).orElseThrow(()->
                new IllegalStateException("DefaultContext.getBean(..) method not found. Incompatible version of Particle?")
            );

            buildMethodVisitor.visitMethodInsn(INVOKEVIRTUAL,
                    Type.getInternalName(DefaultBeanContext.class),
                    "getBean",
                    Type.getMethodDescriptor(getBeanMethod), false);

            // store a reference to the bean being built at index 3
            int factoryVar = pushNewBuildLocalVariable();
            buildMethodVisitor.visitVarInsn(ALOAD, factoryVar);
            pushCastToType(buildMethodVisitor, factoryClass);


            if(argumentTypes.isEmpty()) {
                buildMethodVisitor.visitMethodInsn(INVOKEVIRTUAL,
                        factoryType.getInternalName(),
                        methodName,
                        Type.getMethodDescriptor(beanType), false);
            }
            else {

                pushContructorArguments(buildMethodVisitor, argumentTypes);
                String methodDescriptor = getMethodDescriptor(beanFullClassName, argumentTypes.values());
                buildMethodVisitor.visitMethodInsn(INVOKEVIRTUAL,
                        factoryType.getInternalName(),
                        methodName,
                        methodDescriptor, false);
            }
            this.buildInstanceIndex = pushNewBuildLocalVariable();


//            String constructorDescriptor = getConstructorDescriptor(argumentTypes.values());
//            buildMethodVisitor.visitMethodInsn(INVOKESPECIAL, beanType.getInternalName(), "<init>", constructorDescriptor, false);
//            // store a reference to the bean being built at index 3
//            this.buildInstanceIndex = pushNewBuildLocalVariable();
            pushBeanDefinitionMethodInvocation(buildMethodVisitor, "injectBean");
            pushCastToType(buildMethodVisitor, beanFullClassName);
            buildMethodVisitor.visitVarInsn(ASTORE, buildInstanceIndex);
            buildMethodVisitor.visitVarInsn(ALOAD, buildInstanceIndex);
        }
    }

    private void visitBuildMethodDefinition(Map<String, Object> argumentTypes) {
        if (buildMethodVisitor == null) {
            defineBuilderMethod();
            // load this

            MethodVisitor buildMethodVisitor = this.buildMethodVisitor;
            buildMethodVisitor.visitTypeInsn(NEW, beanType.getInternalName());
            buildMethodVisitor.visitInsn(DUP);
            pushContructorArguments(buildMethodVisitor, argumentTypes);
            String constructorDescriptor = getConstructorDescriptor(argumentTypes.values());
            buildMethodVisitor.visitMethodInsn(INVOKESPECIAL, beanType.getInternalName(), "<init>", constructorDescriptor, false);
            // store a reference to the bean being built at index 3
            this.buildInstanceIndex = pushNewBuildLocalVariable();
            pushBeanDefinitionMethodInvocation(buildMethodVisitor, "injectBean");
            pushCastToType(buildMethodVisitor, beanFullClassName);
            buildMethodVisitor.visitVarInsn(ASTORE, buildInstanceIndex);
            buildMethodVisitor.visitVarInsn(ALOAD, buildInstanceIndex);
        }
    }

    private void pushContructorArguments(MethodVisitor buildMethodVisitor, Map<String, Object> argumentTypes) {
        int size = argumentTypes.size();
        if (size > 0) {
            Iterator<Object> iterator = argumentTypes.values().iterator();
            for (int i = 0; i < size; i++) {
                // Load this for method call
                buildMethodVisitor.visitVarInsn(ALOAD, 0);
                Object argType = iterator.next();

                // load the first two arguments of the method (the BeanResolutionContext and the BeanContext) to be passed to the method
                buildMethodVisitor.visitVarInsn(ALOAD, 1);
                buildMethodVisitor.visitVarInsn(ALOAD, 2);
                // pass the index of the method as the third argument
                pushIntegerConstant(buildMethodVisitor, i);
                // invoke the getBeanForConstructorArgument method
                pushInvokeMethodOnSuperClass(buildMethodVisitor, GET_BEAN_FOR_CONSTRUCTOR_ARGUMENT);
                pushCastToType(buildMethodVisitor, argType);
            }
        }
    }

    private void defineBuilderMethod() {
        this.buildMethodVisitor = classWriter.visitMethod(
                ACC_PUBLIC,
                "build",
                getMethodDescriptor(Object.class.getName(), BeanResolutionContext.class.getName(), BeanContext.class.getName(), BeanDefinition.class.getName()),
                getMethodSignature(getTypeDescriptor(providedBeanClassName), getTypeDescriptor(BeanResolutionContext.class.getName()), getTypeDescriptor(BeanContext.class.getName()), getTypeDescriptor(BeanDefinition.class.getName(), providedBeanClassName)),
                null);
    }


    private void pushBeanDefinitionMethodInvocation(MethodVisitor buildMethodVisitor, String methodName) {
        buildMethodVisitor.visitVarInsn(ALOAD, 0);
        buildMethodVisitor.visitVarInsn(ALOAD, 1);
        buildMethodVisitor.visitVarInsn(ALOAD, 2);
        buildMethodVisitor.visitVarInsn(ALOAD, buildInstanceIndex);

        buildMethodVisitor.visitMethodInsn(INVOKEVIRTUAL,
                beanDefinitionInternalName,
                methodName,
                Type.getMethodDescriptor(Type.getType(Object.class), Type.getType(BeanResolutionContext.class), Type.getType(BeanContext.class), Type.getType(Object.class)),
                false);

    }

    private void pushCastToType(MethodVisitor methodVisitor, Object type) {
        String internalName = getInternalNameForCast(type);
        methodVisitor.visitTypeInsn(CHECKCAST, internalName);
        if (type instanceof Class) {
            Class typeClass = (Class) type;
            if (typeClass.isPrimitive()) {
                Type primitiveType = Type.getType(typeClass);
                groovyjarjarasm.asm.commons.Method valueMethod = null;
                switch (primitiveType.getSort()) {
                    case Type.BOOLEAN:
                        valueMethod = groovyjarjarasm.asm.commons.Method.getMethod("boolean booleanValue()");
                        break;
                    case Type.CHAR:
                        valueMethod = groovyjarjarasm.asm.commons.Method.getMethod("char charValue()");
                        break;
                    case Type.BYTE:
                        valueMethod = groovyjarjarasm.asm.commons.Method.getMethod("byte byteValue()");
                        break;
                    case Type.SHORT:
                        valueMethod = groovyjarjarasm.asm.commons.Method.getMethod("short shortValue()");
                        break;
                    case Type.INT:
                        valueMethod = groovyjarjarasm.asm.commons.Method.getMethod("int intValue()");
                        break;
                    case Type.LONG:
                        valueMethod = groovyjarjarasm.asm.commons.Method.getMethod("long longValue()");
                        break;
                    case Type.DOUBLE:
                        valueMethod = groovyjarjarasm.asm.commons.Method.getMethod("double doubleValue()");
                        break;
                    case Type.FLOAT:
                        valueMethod = groovyjarjarasm.asm.commons.Method.getMethod("float floatValue()");
                        break;
                }

                if (valueMethod != null) {
                    methodVisitor.visitMethodInsn(INVOKEVIRTUAL, internalName, valueMethod.getName(), valueMethod.getDescriptor(), false);
                }
            }
        }
    }

    private int pushNewBuildLocalVariable() {
        buildMethodVisitor.visitVarInsn(ASTORE, buildMethodLocalCount);
        return buildMethodLocalCount++;
    }

    private int pushNewConstructorLocalVariable() {
        constructorVisitor.visitVarInsn(ASTORE, constructorLocalVariableCount);
        return constructorLocalVariableCount++;
    }

    private int pushNewInjectLocalVariable() {
        injectMethodVisitor.visitVarInsn(ASTORE, injectMethodLocalCount);
        return injectMethodLocalCount++;
    }

    private int pushNewPostConstructLocalVariable() {
        postConstructMethodVisitor.visitVarInsn(ASTORE, postConstructMethodLocalCount);
        return postConstructMethodLocalCount++;
    }

    private int pushNewPreDestroyLocalVariable() {
        preDestroyMethodVisitor.visitVarInsn(ASTORE, preDestroyMethodLocalCount);
        return preDestroyMethodLocalCount++;
    }

    private void visitBeanDefinitionConstructorInternal(Map<String, Object> argumentTypes, Map<String, Object> qualifierTypes, Map<String, List<Object>> genericTypes) {
        if (constructorVisitor == null) {
            this.constructorVisitor = startConstructor(classWriter);
            // ALOAD 0
            constructorVisitor.visitVarInsn(ALOAD, 0);

            // First constructor argument: The Annotation scope
            if (scope != null) {

                // this builds SomeClass.getAnnotation(Scope.class) to be passed to first argument of super(..)
                pushGetAnnotationForType(this.beanType, this.scope);
            } else {
                // pass "null" to first argument of super(..) if no scope is specified
                constructorVisitor.visitInsn(ACONST_NULL);
            }

            // Second argument: pass either true or false to second argument of super(..) for singleton status
            if (isSingleton) {
                constructorVisitor.visitInsn(ICONST_1);
            } else {
                constructorVisitor.visitInsn(ICONST_0);
            }

            // Third argument: pass the bean definition type as the third argument to super(..)
            constructorVisitor.visitLdcInsn(beanType);

            // 4th Argument: pass the constructor used to create the bean as the third argument

            Collection<Object> argumentClassNames = argumentTypes.values();
            pushGetConstructorForType(this.constructorVisitor, this.beanType, argumentClassNames);


            // now invoke super(..) if no arg constructor
            if (argumentTypes.isEmpty()) {

                invokeConstructor(constructorVisitor, AbstractBeanDefinition.class, Annotation.class, boolean.class, Class.class, Constructor.class);
            } else {
                // we have a constructor with arguments

                // 5th Argument: Create a call to createMap from an argument types
                pushCreateMapCall(this.constructorVisitor, argumentTypes);

                // 6th Argument: Create a call to createMap from qualifier types
                if (qualifierTypes != null) {
                    pushCreateMapCall(this.constructorVisitor, qualifierTypes);
                } else {
                    constructorVisitor.visitInsn(ACONST_NULL);
                }

                // 7th Argument: Create a call to createMap from generic types
                if (genericTypes != null) {
                    pushCreateGenericsMapCall(this.constructorVisitor, genericTypes);
                } else {
                    constructorVisitor.visitInsn(ACONST_NULL);
                }


                invokeConstructor(constructorVisitor, AbstractBeanDefinition.class, Annotation.class, boolean.class, Class.class, Constructor.class, Map.class, Map.class, Map.class);

            }
        }
    }

    private void pushCreateMapCall(MethodVisitor methodVisitor, Map<String, Object> argumentTypes) {
        int totalSize = argumentTypes.size() * 2;
        if (totalSize > 0) {

            // start a new array
            pushNewArray(methodVisitor, Object.class, totalSize);
            int i = 0;
            for (Map.Entry<String, Object> entry : argumentTypes.entrySet()) {
                // use the property name as the key
                pushStoreStringInArray(methodVisitor, i++, totalSize, entry.getKey());
                // use the property type as the value
                pushStoreTypeInArray(methodVisitor, i++, totalSize, entry.getValue());
            }
            // invoke the AbstractBeanDefinition.createMap method
            methodVisitor.visitMethodInsn(INVOKESTATIC, Type.getInternalName(AbstractBeanDefinition.class), "createMap", Type.getMethodDescriptor(CREATE_MAP_METHOD), false);
        } else {
            methodVisitor.visitInsn(ACONST_NULL);
        }
    }

    private void pushCreateGenericsMapCall(MethodVisitor methodVisitor, Map<String, List<Object>> genericTypes) {
        int totalSize = genericTypes.size() * 2;
        if (totalSize > 0) {
            // start a new array
            pushNewArray(methodVisitor, Object.class, totalSize);
            int i = 0;

            for (Map.Entry<String, List<Object>> entry : genericTypes.entrySet()) {
                // use the property name as the key
                pushStoreStringInArray(methodVisitor, i++, totalSize, entry.getKey());


                // push a new array for the generic types
                pushIntegerConstant(methodVisitor, i++);

                List<Object> genericTypeRefs = entry.getValue();
                pushNewListOfTypes(methodVisitor, genericTypeRefs);
                methodVisitor.visitInsn(AASTORE);
                if (i < totalSize) {
                    // if we are not at the end of the array duplicate array onto the stack
                    methodVisitor.visitInsn(DUP);
                }
            }
            methodVisitor.visitMethodInsn(INVOKESTATIC, Type.getInternalName(AbstractBeanDefinition.class), "createMap", Type.getMethodDescriptor(CREATE_MAP_METHOD), false);
        } else {
            methodVisitor.visitInsn(ACONST_NULL);
        }
    }

    private void pushNewListOfTypes(MethodVisitor methodVisitor, List<Object> types) {
        int genericTypeCount = types.size();

        pushNewArray(methodVisitor, Object.class, genericTypeCount);
        for (int j = 0; j < genericTypeCount; j++) {
            pushStoreTypeInArray(methodVisitor, j, genericTypeCount, types.get(j));
        }


        // invoke the Arrays.asList() method
        methodVisitor.visitMethodInsn(INVOKESTATIC, Type.getInternalName(Arrays.class), "asList", Type.getMethodDescriptor(CREATE_LIST_METHOD), false);
    }

    private void pushGetConstructorForType(MethodVisitor methodVisitor, Type beanType, Collection<Object> argumentClassNames) {
        methodVisitor.visitLdcInsn(beanType);

        int argCount = argumentClassNames.size();
        Object[] argumentTypeArray = argumentClassNames.toArray(new Object[argCount]);
        pushNewArray(methodVisitor, Class.class, argCount);
        for (int i = 0; i < argCount; i++) {
            pushStoreTypeInArray(methodVisitor, i, argCount, argumentTypeArray[i]);
        }

        // invoke Class.getConstructor()
        Method getConstructorMethod = ReflectionUtils.getDeclaredMethod(Class.class, "getConstructor", Class[].class)
                .orElseThrow(() ->
                        new IllegalStateException("Class.getConstructor(..) method not found")
                );
        methodVisitor.visitMethodInsn(
                INVOKEVIRTUAL,
                Type.getInternalName(Class.class),
                "getConstructor",
                Type.getType(getConstructorMethod).getDescriptor(),
                false);
    }

    private void pushStoreTypeInArray(MethodVisitor methodVisitor, int index, int size, Object type) {
        // the array index position
        pushIntegerConstant(methodVisitor, index);
        // the type reference
        if (type instanceof Class) {
            Class typeClass = (Class) type;
            if (typeClass.isPrimitive()) {
                Type wrapperType = Type.getType(ReflectionUtils.getWrapperType(typeClass));

                methodVisitor.visitFieldInsn(GETSTATIC, wrapperType.getInternalName(), "TYPE", Type.getDescriptor(Class.class));
            } else {
                methodVisitor.visitLdcInsn(Type.getType(typeClass));
            }
        } else {
            methodVisitor.visitLdcInsn(getObjectType(type.toString()));
        }
        // store the type reference
        methodVisitor.visitInsn(AASTORE);
        // if we are not at the end of the array duplicate array onto the stack
        if (index != (size - 1)) {
            methodVisitor.visitInsn(DUP);
        }
    }

    private void pushStoreStringInArray(MethodVisitor methodVisitor, int index, int size, String string) {
        // the array index position
        pushIntegerConstant(methodVisitor, index);
        // load the constant string
        methodVisitor.visitLdcInsn(string);
        // store the string in the position
        methodVisitor.visitInsn(AASTORE);
        if (index != (size - 1)) {
            // if we are not at the end of the array duplicate array onto the stack
            methodVisitor.visitInsn(DUP);
        }
    }

    private void pushNewArray(MethodVisitor methodVisitor, Class arrayType, int size) {
        // the size of the array
        pushIntegerConstant(methodVisitor, size);
        // define the array
        methodVisitor.visitTypeInsn(ANEWARRAY, Type.getInternalName(arrayType));
        // add a reference to the array on the stack
        if (size > 0) {
            methodVisitor.visitInsn(DUP);
        }
    }

    private void pushIntegerConstant(MethodVisitor methodVisitor, int value) {
        if (value == 0) {
            // push empty class array
            methodVisitor.visitInsn(ICONST_0);
        } else {
            switch (value) {
                case 1:
                    methodVisitor.visitInsn(ICONST_1);
                    break;
                case 2:
                    methodVisitor.visitInsn(ICONST_2);
                    break;
                case 3:
                    methodVisitor.visitInsn(ICONST_3);
                    break;
                case 4:
                    methodVisitor.visitInsn(ICONST_4);
                    break;
                case 5:
                    methodVisitor.visitInsn(ICONST_5);
                    break;
                default:
                    methodVisitor.visitVarInsn(BIPUSH, value);
            }
        }
    }


    /**
     * Adds a method call to get the given annotation of the given type to tye stack
     *
     * @param targetClass    The target class
     * @param annotationType The annotation type
     */
    private void pushGetAnnotationForType(Type targetClass, Type annotationType) {
        constructorVisitor.visitLdcInsn(targetClass);
        pushGetAnnotationCall(annotationType);
    }

    private void pushGetAnnotationCall(Type annotationType) {
        constructorVisitor.visitLdcInsn(annotationType);
        Method method = ReflectionUtils.getDeclaredMethod(Class.class, "getAnnotation", Class.class)
                .orElseThrow(() ->
                        new IllegalStateException("Class.getAnnotation(..) method not found")
                );
        String descriptor = Type.getType(method).getDescriptor();
        constructorVisitor.visitMethodInsn(INVOKEVIRTUAL,
                Type.getInternalName(Class.class),
                "getAnnotation",
                descriptor,
                false);
    }

    /**
     * Adds a method call to get the given annotation of the given type to tye stack
     *
     * @param annotationType The annotation type
     */
    private void pushGetAnnotationForField(MethodVisitor methodVisitor, Type annotationType) {
        methodVisitor.visitLdcInsn(annotationType);
        Method method = ReflectionUtils.getDeclaredMethod(Field.class, "getAnnotation", Class.class)
                .orElseThrow(() ->
                        new IllegalStateException("Field.getAnnotation(..) method not found. Incompatible JVM?")
                );
        String descriptor = Type.getType(method).getDescriptor();
        methodVisitor.visitMethodInsn(INVOKEVIRTUAL,
                Type.getInternalName(Field.class),
                "getAnnotation",
                descriptor,
                false);
    }

    protected String generateBeanDefSig(String typeParameter) {
        SignatureVisitor sv = new SignatureWriter();
        String beanTypeInternalName = getInternalName(typeParameter);

        // visit super class
        SignatureVisitor psv = sv.visitSuperclass();
        psv.visitClassType(Type.getInternalName(AbstractBeanDefinition.class));
        SignatureVisitor ppsv = psv.visitTypeArgument('=');
        ppsv.visitClassType(beanTypeInternalName);
        ppsv.visitEnd();
        psv.visitEnd();

        // visit BeanFactory interface
        for (Class interfaceType : interfaceTypes) {

            SignatureVisitor bfi = sv.visitInterface();
            bfi.visitClassType(Type.getInternalName(interfaceType));
            SignatureVisitor iisv = bfi.visitTypeArgument('=');
            iisv.visitClassType(beanTypeInternalName);
            iisv.visitEnd();
            bfi.visitEnd();
        }
//        sv.visitEnd();
        return sv.toString();
    }

    @Override
    public String toString() {
        return "BeanDefinitionWriter{" +
                "beanFullClassName='" + beanFullClassName + '\'' +
                '}';
    }
}
