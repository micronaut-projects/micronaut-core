package org.particleframework.inject.writer;

import org.objectweb.asm.*;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.signature.SignatureVisitor;
import org.objectweb.asm.signature.SignatureWriter;
import org.particleframework.context.*;
import org.particleframework.context.annotation.ConfigurationProperties;
import org.particleframework.context.annotation.Value;
import org.particleframework.core.annotation.AnnotationMetadata;
import org.particleframework.core.naming.NameUtils;
import org.particleframework.core.reflect.ReflectionUtils;
import org.particleframework.core.type.Argument;
import org.particleframework.core.util.StringUtils;
import org.particleframework.inject.*;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.function.Consumer;

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
public class BeanDefinitionWriter extends AbstractClassFileWriter implements BeanDefinitionVisitor {
    private static final org.objectweb.asm.commons.Method METHOD_GET_REQUIRED_METHOD = org.objectweb.asm.commons.Method.getMethod(ReflectionUtils.getRequiredInternalMethod(
            ReflectionUtils.class,
            "getRequiredMethod",
            Class.class,
            String.class,
            Class[].class
    ));
    private static final Constructor<AbstractBeanDefinition> CONSTRUCTOR_ABSTRACT_BEAN_DEFINITION = ReflectionUtils.findConstructor(AbstractBeanDefinition.class, boolean.class, Class.class, Constructor.class, Argument[].class)
            .orElseThrow(() -> new ClassGenerationException("Invalid version of Particle found on the class path"));
    private static final org.objectweb.asm.commons.Method METHOD_CREATE_ARGUMENT_METHOD = org.objectweb.asm.commons.Method.getMethod(
            ReflectionUtils.getRequiredInternalMethod(
                    Argument.class,
                    "of",
                    Method.class,
                    String.class,
                    int.class,
                    Class.class,
                    Argument[].class
            )
    );

    private static final org.objectweb.asm.commons.Method METHOD_CREATE_ARGUMENT_FIELD = org.objectweb.asm.commons.Method.getMethod(
            ReflectionUtils.getRequiredInternalMethod(
                    Argument.class,
                    "of",
                    Field.class,
                    String.class,
                    Class.class,
                    Argument[].class
            )
    );

    private static final org.objectweb.asm.commons.Method METHOD_CREATE_ARGUMENT_CONSTRUCTOR = org.objectweb.asm.commons.Method.getMethod(
            ReflectionUtils.getRequiredInternalMethod(
                    Argument.class,
                    "of",
                    Constructor.class,
                    String.class,
                    int.class,
                    Class.class,
                    Argument[].class
            )
    );
    private static final org.objectweb.asm.commons.Method METHOD_CREATE_ARGUMENT_SIMPLE = org.objectweb.asm.commons.Method.getMethod(
            ReflectionUtils.getRequiredInternalMethod(
                    Argument.class,
                    "of",
                    Class.class,
                    String.class
            )
    );

    private static final org.objectweb.asm.commons.Method METHOD_CREATE_ARGUMENT_WITH_GENERICS = org.objectweb.asm.commons.Method.getMethod(
            ReflectionUtils.getRequiredInternalMethod(
                    Argument.class,
                    "of",
                    Class.class,
                    String.class,
                    Argument[].class
            )
    );

    private static final Method POST_CONSTRUCT_METHOD = ReflectionUtils.getRequiredInternalMethod(AbstractBeanDefinition.class, "postConstruct", BeanResolutionContext.class, BeanContext.class, Object.class);

    private static final Method INJECT_BEAN_METHOD = ReflectionUtils.getRequiredInternalMethod(AbstractBeanDefinition.class, "injectBean", BeanResolutionContext.class, BeanContext.class, Object.class);

    private static final Method PRE_DESTROY_METHOD = ReflectionUtils.getRequiredInternalMethod(AbstractBeanDefinition.class, "preDestroy", BeanResolutionContext.class, BeanContext.class, Object.class);

    private static final Method ADD_FIELD_INJECTION_POINT_METHOD = ReflectionUtils.getRequiredInternalMethod(AbstractBeanDefinition.class, "addInjectionPoint", Field.class, Annotation.class, boolean.class);

    private static final Method ADD_METHOD_INJECTION_POINT_METHOD = ReflectionUtils.getRequiredInternalMethod(AbstractBeanDefinition.class, "addInjectionPoint", Method.class, Argument[].class, boolean.class);

    private static final Method ADD_SETTER_INJECTION_POINT_METHOD = ReflectionUtils.getRequiredInternalMethod(AbstractBeanDefinition.class, "addInjectionPoint", Field.class, Method.class, Argument.class, boolean.class);

    private static final Method ADD_EXECUTABLE_METHOD = ReflectionUtils.getRequiredInternalMethod(AbstractBeanDefinition.class, "addExecutableMethod", ExecutableMethod.class);

    private static final Method GET_BEAN_FOR_CONSTRUCTOR_ARGUMENT = ReflectionUtils.getRequiredInternalMethod(AbstractBeanDefinition.class, "getBeanForConstructorArgument", BeanResolutionContext.class, BeanContext.class, int.class);

    private static final Method GET_VALUE_FOR_CONSTRUCTOR_ARGUMENT = ReflectionUtils.getRequiredInternalMethod(AbstractBeanDefinition.class, "getValueForConstructorArgument", BeanResolutionContext.class, BeanContext.class, int.class);

    private static final Method GET_BEAN_FOR_FIELD = ReflectionUtils.getRequiredInternalMethod(AbstractBeanDefinition.class, "getBeanForField", BeanResolutionContext.class, BeanContext.class, int.class);

    private static final Method GET_VALUE_FOR_FIELD = ReflectionUtils.getRequiredInternalMethod(AbstractBeanDefinition.class, "getValueForField", BeanResolutionContext.class, BeanContext.class, int.class);

    private static final Method GET_VALUE_FOR_PATH = ReflectionUtils.getRequiredInternalMethod(AbstractBeanDefinition.class, "getValueForPath", BeanResolutionContext.class, BeanContext.class, Argument.class, String[].class);

    private static final Method CONTAINS_VALUE_FOR_FIELD = ReflectionUtils.getRequiredInternalMethod(AbstractBeanDefinition.class, "containsValueForField", BeanResolutionContext.class, BeanContext.class, int.class);

    private static final Method CONTAINS_PROPERTIES_METHOD = ReflectionUtils.getRequiredInternalMethod(AbstractBeanDefinition.class, "containsProperties", BeanResolutionContext.class, BeanContext.class);

    private static final Method GET_BEAN_FOR_METHOD_ARGUMENT = ReflectionUtils.getRequiredInternalMethod(AbstractBeanDefinition.class, "getBeanForMethodArgument", BeanResolutionContext.class, BeanContext.class, int.class, int.class);

    private static final Method GET_VALUE_FOR_METHOD_ARGUMENT = ReflectionUtils.getRequiredInternalMethod(AbstractBeanDefinition.class, "getValueForMethodArgument", BeanResolutionContext.class, BeanContext.class, int.class, int.class);

    private static final Method CONTAINS_VALUE_FOR_METHOD_ARGUMENT = ReflectionUtils.getRequiredInternalMethod(AbstractBeanDefinition.class, "containsValueForMethodArgument", BeanResolutionContext.class, BeanContext.class, int.class, int.class);


    private static final org.objectweb.asm.commons.Method BEAN_DEFINITION_CLASS_CONSTRUCTOR = new org.objectweb.asm.commons.Method(CONSTRUCTOR_NAME, getConstructorDescriptor(
            boolean.class, Class.class, Constructor.class, Argument[].class
    ));

    private static final org.objectweb.asm.commons.Method BEAN_DEFINITION_METHOD_CONSTRUCTOR = new org.objectweb.asm.commons.Method(CONSTRUCTOR_NAME, getConstructorDescriptor(
            Method.class, Argument[].class
    ));

    private static final Type TYPE_ABSTRACT_BEAN_DEFINITION = Type.getType(AbstractBeanDefinition.class);
    private static final Type TYPE_ABSTRACT_PARAMETRIZED_BEAN_DEFINITION = Type.getType(AbstractParametrizedBeanDefinition.class);
    private static final String FIELD_CONSTRUCTOR = "$CONSTRUCTOR";
    private final ClassWriter classWriter;
    private final String beanFullClassName;
    private final String beanDefinitionName;
    private final String beanDefinitionInternalName;
    private final Type beanType;
    private final Type providedType;
    private final boolean isSingleton;
    private final Set<Class> interfaceTypes;
    private final Map<String, ExecutableMethodWriter> methodExecutors = new LinkedHashMap<>();
    private final String providedBeanClassName;
    private final String packageName;
    private final String beanSimpleClassName;
    private final Type beanDefinitionType;
    private final boolean isInterface;
    private final boolean isConfigurationProperties;
    private GeneratorAdapter constructorVisitor;
    private GeneratorAdapter buildMethodVisitor;
    private GeneratorAdapter injectMethodVisitor;
    private Label injectEnd = null;
    private GeneratorAdapter preDestroyMethodVisitor;
    private GeneratorAdapter postConstructMethodVisitor;
    private int methodExecutorIndex = 0;
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
    private int argsIndex = -1;
    private int injectInstanceIndex;
    private int postConstructInstanceIndex;
    private int preDestroyInstanceIndex;
    private boolean beanFinalized = false;
    private Type superType = TYPE_ABSTRACT_BEAN_DEFINITION;
    private boolean isSuperFactory = false;
    private final AnnotationMetadata annotationMetadata;
    private ConfigBuilder currentConfigBuilder;
    private int optionalInstanceIndex;
    private boolean preprocessMethods = false;


    /**
     * Creates a bean definition writer
     *
     * @param packageName The package name of the bean
     * @param className   The class name, without the package, of the bean
     * @param isSingleton Is the scope a singleton
     */
    public BeanDefinitionWriter(String packageName,
                                String className,
                                boolean isSingleton,
                                AnnotationMetadata annotationMetadata) {
        this(packageName, className, packageName + '.' + className, false, isSingleton, annotationMetadata);
    }


    /**
     * Creates a bean definition writer
     *
     * @param packageName       The package name of the bean
     * @param className         The class name, without the package, of the bean
     * @param providedClassName The type this bean definition provides, in this case where the bean implements {@link javax.inject.Provider}
     * @param isSingleton       Is the scope a singleton
     */
    public BeanDefinitionWriter(String packageName,
                                String className,
                                String providedClassName,
                                boolean isInterface,
                                boolean isSingleton,
                                AnnotationMetadata annotationMetadata) {
        this(packageName, className, packageName + ".$" + className + "Definition", providedClassName, isInterface, isSingleton, annotationMetadata);
    }


    /**
     * Creates a bean definition writer
     *
     * @param packageName       The package name of the bean
     * @param className         The class name, without the package, of the bean
     * @param providedClassName The type this bean definition provides, which differs from the class name in the case of factory beans
     * @param isInterface       Whether the provided type is an interface
     * @param isSingleton       Is the scope a singleton
     */
    public BeanDefinitionWriter(String packageName,
                                String className,
                                String beanDefinitionName,
                                String providedClassName,
                                boolean isInterface,
                                boolean isSingleton,
                                AnnotationMetadata annotationMetadata) {
        this.classWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        this.packageName = packageName;
        this.isInterface = isInterface;
        this.beanFullClassName = packageName + '.' + className;
        this.annotationMetadata = annotationMetadata;
        this.beanSimpleClassName = className;
        this.providedBeanClassName = providedClassName;
        this.beanDefinitionName = beanDefinitionName;
        this.beanDefinitionType = getTypeReference(this.beanDefinitionName);
        this.beanType = getTypeReference(beanFullClassName);
        this.providedType = getTypeReference(providedBeanClassName);
        this.beanDefinitionInternalName = getInternalName(this.beanDefinitionName);
        this.isSingleton = isSingleton;
        this.interfaceTypes = new HashSet<>();
        this.interfaceTypes.add(BeanFactory.class);
        this.isConfigurationProperties = annotationMetadata.hasDeclaredStereotype(ConfigurationProperties.class);
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
        return isSingleton;
    }

    @Override
    public void visitBeanDefinitionInterface(Class<? extends BeanDefinition> interfaceType) {
        this.interfaceTypes.add(interfaceType);
    }
    @Override
    public void visitSuperType(String name) {
        this.superType = getTypeReference(name);
    }

    @Override
    public void visitSuperFactoryType(String beanName) {
        visitSuperType(beanName);
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
    public boolean isValidated() {
        return this.interfaceTypes.contains(ValidatedBeanDefinition.class);
    }

    @Override
    public String getBeanDefinitionName() {
        return beanDefinitionName;
    }

    /**
     * @return The name of the bean definition class
     */
    public String getBeanDefinitionClassFile() {
        String className = getBeanDefinitionName();
        return getClassFileName(className);
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
                                       Map<String, Map<String, Object>> genericTypes) {
        if (constructorVisitor != null) {
            throw new IllegalStateException("Only a single call to visitBeanFactoryMethod(..) is permitted");
        } else {
            // now prepare the implementation of the build method. See BeanFactory interface
            visitBuildFactoryMethodDefinition(factoryClass, methodName, argumentTypes, qualifierTypes);

            // now implement the constructor
            buildFactoryMethodClassConstructor(factoryClass, methodName, argumentTypes, qualifierTypes, genericTypes);



            // now override the injectBean method
            visitInjectMethodDefinition();

        }
    }



    /**
     * Visits the constructor used to create the bean definition.
     *
     * @param argumentTypes  The argument type names for each parameter
     * @param qualifierTypes The qualifier type names for each parameter
     * @param genericTypes   The generic types for each parameter
     */
    @Override
    public void visitBeanDefinitionConstructor(Map<String, Object> argumentTypes,
                                               Map<String, Object> qualifierTypes,
                                               Map<String, Map<String, Object>> genericTypes) {
        if (constructorVisitor == null) {
            // first build the constructor
            visitBeanDefinitionConstructorInternal(argumentTypes, qualifierTypes, genericTypes);

            // now prepare the implementation of the build method. See BeanFactory interface
            visitBuildMethodDefinition(argumentTypes, qualifierTypes);

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
            visitBuildMethodDefinition(Collections.emptyMap(), Collections.emptyMap());

            // now override the injectBean method
            visitInjectMethodDefinition();
        }
    }

    /**
     * Finalize the bean definition to the given output stream
     */
    @Override
    public void visitBeanDefinitionEnd() {
        if (classWriter instanceof ClassWriter) {
            String[] interfaceInternalNames = new String[interfaceTypes.size()];
            Iterator<Class> j = interfaceTypes.iterator();
            for (int i = 0; i < interfaceInternalNames.length; i++) {
                interfaceInternalNames[i] = Type.getInternalName(j.next());
            }
            classWriter.visit(V1_8, ACC_PUBLIC,
                    beanDefinitionInternalName,
                    generateBeanDefSig(providedType.getInternalName()),
                    isSuperFactory ? TYPE_ABSTRACT_BEAN_DEFINITION.getInternalName() : superType.getInternalName(),
                    interfaceInternalNames);

            if (buildMethodVisitor == null) {
                throw new IllegalStateException("At least one call to visitBeanDefinitionConstructor() is required");
            }

            finalizeInjectMethod();
            finalizeBuildMethod();
            finalizeAnnotationMetadata();
            if(preprocessMethods) {
                GeneratorAdapter requiresMethodProcessing = startPublicMethod(classWriter, "requiresMethodProcessing", boolean.class.getName());
                requiresMethodProcessing.push(true);
                requiresMethodProcessing.visitInsn(IRETURN);
                requiresMethodProcessing.visitMaxs(1,1);
                requiresMethodProcessing.visitEnd();
            }
            constructorVisitor.visitInsn(RETURN);
            constructorVisitor.visitMaxs(DEFAULT_MAX_STACK, 1);
            if (buildMethodVisitor != null) {
                buildMethodVisitor.visitInsn(ARETURN);
                buildMethodVisitor.visitMaxs(DEFAULT_MAX_STACK, buildMethodLocalCount);
            }
            if (injectMethodVisitor != null) {
                injectMethodVisitor.visitMaxs(DEFAULT_MAX_STACK, injectMethodLocalCount);
            }
            if (postConstructMethodVisitor != null) {
                postConstructMethodVisitor.visitVarInsn(ALOAD, postConstructInstanceIndex);
                postConstructMethodVisitor.visitInsn(ARETURN);
                postConstructMethodVisitor.visitMaxs(DEFAULT_MAX_STACK, postConstructMethodLocalCount);
            }
            if (preDestroyMethodVisitor != null) {
                preDestroyMethodVisitor.visitVarInsn(ALOAD, preDestroyInstanceIndex);
                preDestroyMethodVisitor.visitInsn(ARETURN);
                preDestroyMethodVisitor.visitMaxs(DEFAULT_MAX_STACK, preDestroyMethodLocalCount);
            }

            classWriter.visitEnd();
        }
        this.beanFinalized = true;
    }

    protected void finalizeAnnotationMetadata() {
        if(annotationMetadata != null) {

            GeneratorAdapter annotationMetadataMethod = startPublicMethod(classWriter, "getAnnotationMetadata", AnnotationMetadata.class.getName());
            annotationMetadataMethod.loadThis();
            annotationMetadataMethod.getStatic(getTypeReference(beanDefinitionName + BeanDefinitionReferenceWriter.REF_SUFFIX), AbstractAnnotationMetadataWriter.FIELD_ANNOTATION_METADATA, Type.getType(AnnotationMetadata.class));
            annotationMetadataMethod.returnValue();
            annotationMetadataMethod.visitMaxs(1,1);
            annotationMetadataMethod.visitEnd();
        }
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
        try (OutputStream out = visitor.visitClass(getBeanDefinitionName())) {
            try {
                for (ExecutableMethodWriter methodWriter : methodExecutors.values()) {
                    methodWriter.accept(visitor);
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
    public void visitSetterInjectionPoint(Object declaringType,
                                          Object qualifierType,
                                          boolean requiresReflection,
                                          Object fieldType,
                                          String fieldName,
                                          String setterName,
                                          Map<String, Object> genericTypes) {
        Type declaringTypeRef = getTypeReference(declaringType);

        addInjectionPointForSetterInternal(qualifierType, requiresReflection, fieldType, fieldName, setterName, genericTypes, declaringTypeRef);

        if (!requiresReflection) {
            resolveBeanOrValueForSetter(declaringTypeRef, setterName, fieldType, GET_BEAN_FOR_METHOD_ARGUMENT, false);

        }
        currentMethodIndex++;
    }

    @Override
    public void visitSetterValue(Object declaringType,
                                 Object qualifierType,
                                 boolean requiresReflection,
                                 Object fieldType,
                                 String fieldName,
                                 String setterName,
                                 Map<String, Object> genericTypes,
                                 boolean isOptional) {
        Type declaringTypeRef = getTypeReference(declaringType);

        addInjectionPointForSetterInternal(qualifierType, requiresReflection, fieldType, fieldName, setterName, genericTypes, declaringTypeRef);

        if (!requiresReflection) {
            resolveBeanOrValueForSetter(declaringTypeRef, setterName, fieldType, GET_VALUE_FOR_METHOD_ARGUMENT, isOptional);
        }
        currentMethodIndex++;
    }

    @Override
    public void visitSetterValue(Object declaringType, Object qualifierType, boolean requiresReflection, Object valueType, String setterName, Map<String, Object> genericTypes, boolean isOptional) {
        Type declaringTypeRef = getTypeReference(declaringType);


        // load 'this'
        constructorVisitor.visitVarInsn(ALOAD, 0);

        // 1st argument: The method
        int currentMethodVar = pushGetMethodFromTypeCallLocalVariable(constructorVisitor, declaringTypeRef, setterName,Collections.singletonList(valueType));
        String propertyName = NameUtils.getPropertyNameForSetter(setterName);
        constructorVisitor.visitVarInsn(ALOAD, currentMethodVar);
            pushBuildArgumentsForMethod(
                    constructorVisitor,
                    generatorAdapter -> generatorAdapter.visitVarInsn(ALOAD, currentMethodVar),
                    Collections.singletonMap(propertyName, valueType),
                    qualifierType != null ? Collections.singletonMap(propertyName, qualifierType) : Collections.emptyMap(),
                    Collections.singletonMap(propertyName, genericTypes));

        // 5th argument to addInjectionPoint: do we need reflection?
        constructorVisitor.visitInsn(requiresReflection ? ICONST_1 : ICONST_0);


        // invoke add injection point method
        pushInvokeMethodOnSuperClass(constructorVisitor, ADD_METHOD_INJECTION_POINT_METHOD);

        if (!requiresReflection) {
            resolveBeanOrValueForSetter(declaringTypeRef, setterName, valueType, GET_VALUE_FOR_METHOD_ARGUMENT, isOptional);
        }
        currentMethodIndex++;

    }

    @Override
    public void visitPostConstructMethod(Object declaringType,
                                         boolean requiresReflection,
                                         Object returnType,
                                         String methodName,
                                         Map<String, Object> argumentTypes,
                                         Map<String, Object> qualifierTypes,
                                         Map<String, Map<String, Object>> genericTypes) {
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
     * @param declaringType The declaring type of the method. Either a Class or a string representing the name of the type
     * @param returnType    The return type of the method. Either a Class or a string representing the name of the type
     * @param methodName    The method name
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
     * @param declaringType The declaring type of the method. Either a Class or a string representing the name of the type
     * @param methodName    The method name
     */
    public void visitPreDestroyMethod(Object declaringType,
                                      String methodName) {
        visitPreDestroyMethodDefinition();
        visitMethodInjectionPointInternal(declaringType, false, Void.TYPE, methodName, Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap(), constructorVisitor, preDestroyMethodVisitor, preDestroyInstanceIndex);
    }


    @Override
    public void visitPreDestroyMethod(Object declaringType,
                                      boolean requiresReflection,
                                      Object returnType,
                                      String methodName,
                                      Map<String, Object> argumentTypes,
                                      Map<String, Object> qualifierTypes,
                                      Map<String, Map<String, Object>> genericTypes) {
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
                                          Map<String, Map<String, Object>> genericTypes) {
        visitMethodInjectionPoint(beanFullClassName, requiresReflection, returnType, methodName, argumentTypes, qualifierTypes, genericTypes);
    }

    @Override
    public void visitMethodInjectionPoint(Object declaringType,
                                          boolean requiresReflection,
                                          Object returnType,
                                          String methodName,
                                          Map<String, Object> argumentTypes,
                                          Map<String, Object> qualifierTypes,
                                          Map<String, Map<String, Object>> genericTypes) {
        GeneratorAdapter constructorVisitor = this.constructorVisitor;
        GeneratorAdapter injectMethodVisitor = this.injectMethodVisitor;
        int injectInstanceIndex = this.injectInstanceIndex;

        visitMethodInjectionPointInternal(declaringType, requiresReflection, returnType, methodName, argumentTypes, qualifierTypes, genericTypes, constructorVisitor, injectMethodVisitor, injectInstanceIndex);
    }

    @Override
    public ExecutableMethodWriter visitExecutableMethod(Object declaringType,
                                      Object returnType,
                                      Map<String, Object> returnTypeGenericTypes,
                                      String methodName,
                                      Map<String, Object> argumentTypes,
                                      Map<String, Object> qualifierTypes,
                                      Map<String, Map<String, Object>> genericTypes,
                                      AnnotationMetadata annotationMetadata) {

        String methodProxyShortName = "$exec" + ++methodExecutorIndex;
        String methodExecutorClassName = beanDefinitionName + "$" + methodProxyShortName;
        ExecutableMethodWriter executableMethodWriter = new ExecutableMethodWriter(
                beanFullClassName,
                methodExecutorClassName,
                methodProxyShortName,
                isInterface,
                annotationMetadata);
        // TODO: fix so that exec classes are static inner
//        executableMethodWriter.makeStaticInner(beanDefinitionInternalName, (ClassWriter) classWriter);
        executableMethodWriter.visitMethod(
                declaringType,
                returnType,
                returnTypeGenericTypes,
                methodName,
                argumentTypes,
                qualifierTypes,
                genericTypes
        );

        methodExecutors.put(methodExecutorClassName, executableMethodWriter);

        if(constructorVisitor == null) {
            throw new IllegalStateException("Method visitBeanDefinitionConstructor(..) should be called first!");
        }

        constructorVisitor.visitVarInsn(ALOAD, 0);
        String methodExecutorInternalName = executableMethodWriter.getInternalName();
        constructorVisitor.visitTypeInsn(NEW, methodExecutorInternalName);
        constructorVisitor.visitInsn(DUP);
        constructorVisitor.visitMethodInsn(INVOKESPECIAL,
                methodExecutorInternalName,
                CONSTRUCTOR_NAME,
                DESCRIPTOR_DEFAULT_CONSTRUCTOR,
                false);

        pushInvokeMethodOnSuperClass(constructorVisitor, ADD_EXECUTABLE_METHOD);
        return executableMethodWriter;
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
    public void visitConfigBuilderStart(ConfigBuilder configBuilder) {
        this.currentConfigBuilder = configBuilder;
    }

    @Override
    public void visitConfigBuilderMethod(String prefix, String configurationPrefix, Object returnType, String methodName, Object paramType, Map<String, Object> generics) {
        if(currentConfigBuilder != null) {
            Type builderType = currentConfigBuilder.getType();
            String builderName = currentConfigBuilder.getName();
            boolean invokeMethod = currentConfigBuilder.isInvokeMethod();

            GeneratorAdapter injectMethodVisitor = this.injectMethodVisitor;

            String propertyName = NameUtils.decapitalize( methodName.substring(prefix.length()) );

            injectMethodVisitor.loadThis();
            injectMethodVisitor.loadArg(0); // the resolution context
            injectMethodVisitor.loadArg(1); // the bean context
            boolean zeroArgs = paramType == null;
            if(zeroArgs) {
                // if the parameter type is null this is a zero args method that expects a boolean flag
                buildArgument(
                        injectMethodVisitor,
                        propertyName,
                        Boolean.class
                );
            }
            else {
                buildArgumentWithGenerics(
                        injectMethodVisitor,
                        propertyName,
                        Collections.singletonMap(paramType, generics)
                );
            }
            // at some point we may want to support nested builders, hence the arrays and property path resolution
            String[] propertyPath;
            if (StringUtils.isNotEmpty(configurationPrefix)) {
                propertyPath = new String[]{configurationPrefix, propertyName};
            } else {
                propertyPath = new String[]{propertyName};
            }
            int propertyPathLength = propertyPath.length;
            pushNewArray(injectMethodVisitor, String.class, propertyPathLength);

            for (int i = 0; i < propertyPathLength; i++) {
                pushStoreStringInArray(injectMethodVisitor, i, propertyPathLength, propertyPath[i]);
            }
            // Optional optional = AbstractBeanDefinition.getValueForPath(...)
            injectMethodVisitor.invokeVirtual(beanDefinitionType, org.objectweb.asm.commons.Method.getMethod(GET_VALUE_FOR_PATH));
            injectMethodVisitor.visitVarInsn(ASTORE, optionalInstanceIndex);
            injectMethodVisitor.visitVarInsn(ALOAD, optionalInstanceIndex);
            Label ifEnd = new Label();
            // if(optional.isPresent())
            injectMethodVisitor.invokeVirtual(Type.getType(Optional.class), org.objectweb.asm.commons.Method.getMethod(
                    ReflectionUtils.getRequiredMethod(Optional.class, "isPresent" )
            ));
            injectMethodVisitor.push(false);
            injectMethodVisitor.ifCmp(Type.BOOLEAN_TYPE, GeneratorAdapter.EQ, ifEnd);
            injectMethodVisitor.visitLabel(new Label());

            injectMethodVisitor.visitVarInsn(ALOAD, injectInstanceIndex);

            if (invokeMethod) {
                String desc = builderType.getClassName() + " " + builderName + "()";
                injectMethodVisitor.invokeVirtual(beanType, org.objectweb.asm.commons.Method.getMethod(desc));
            } else {
                injectMethodVisitor.getField(beanType, builderName, builderType);
            }

            Type returnTypeRef = getTypeReference(returnType);
            Type paramTypeRef = !zeroArgs ? getTypeReference(paramType) : null;
            String desc = returnTypeRef.getClassName() + " " + methodName + "(" + (!zeroArgs ? paramTypeRef.getClassName() : "") + ")";
            injectMethodVisitor.visitVarInsn(ALOAD, optionalInstanceIndex);
            // get the value: optional.get()
            injectMethodVisitor.invokeVirtual(Type.getType(Optional.class), org.objectweb.asm.commons.Method.getMethod(
                    ReflectionUtils.getRequiredMethod(Optional.class, "get" )
            ));
            pushCastToType(injectMethodVisitor, !zeroArgs ? paramType : boolean.class);
            if(zeroArgs) {
                Label zeroArgsEnd = new Label();
                injectMethodVisitor.push(false);
                injectMethodVisitor.ifCmp(Type.BOOLEAN_TYPE, GeneratorAdapter.EQ, zeroArgsEnd);
                injectMethodVisitor.visitLabel(new Label());
                injectMethodVisitor.invokeVirtual(
                        builderType,
                        org.objectweb.asm.commons.Method.getMethod(desc)
                );

                injectMethodVisitor.visitLabel(zeroArgsEnd);
            }
            else {
                injectMethodVisitor.invokeVirtual(
                        builderType,
                        org.objectweb.asm.commons.Method.getMethod(desc)
                );
            }
            if(returnType != void.class) {
                injectMethodVisitor.pop();
            }

            injectMethodVisitor.visitLabel(ifEnd);
        }
    }

    @Override
    public void visitConfigBuilderEnd() {
        currentConfigBuilder = null;
    }

    @Override
    public void setRequiresMethodProcessing(boolean shouldPreProcess) {
        this.preprocessMethods = shouldPreProcess;
    }


    @Override
    public boolean requiresMethodProcessing() {
        return this.preprocessMethods;
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

    @Override
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

    @Override
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
        visitFieldInjectionPointInternal(declaringType, qualifierType, requiresReflection, fieldType, fieldName, GET_VALUE_FOR_FIELD, isOptional);
    }

    private void buildFactoryMethodClassConstructor(
            Object factoryClass,
            String methodName,
            Map<String, Object> argumentTypes,
            Map<String, Object> qualifierTypes,
            Map<String, Map<String, Object>> genericTypes) {
        Type factoryTypeRef = getTypeReference(factoryClass);
        this.constructorVisitor = buildProtectedConstructor(BEAN_DEFINITION_METHOD_CONSTRUCTOR);

        GeneratorAdapter defaultConstructor = new GeneratorAdapter(
                startConstructor(classWriter),
                ACC_PUBLIC,
                CONSTRUCTOR_NAME,
                DESCRIPTOR_DEFAULT_CONSTRUCTOR
        );

        // ALOAD 0
        defaultConstructor.loadThis();

        // First constructor argument: The factory method
        boolean hasArgs = !argumentTypes.isEmpty();
        Collection<Object> argumentTypeClasses = hasArgs ? argumentTypes.values() : Collections.emptyList();
        // load 'this'
        defaultConstructor.loadThis();

        pushGetMethodFromTypeCall(defaultConstructor, factoryTypeRef, methodName, argumentTypeClasses);

        if (hasArgs) {
            pushBuildArgumentsForMethod(
                    defaultConstructor,
                    generatorAdapter -> pushGetMethodFromTypeCall(generatorAdapter, factoryTypeRef, methodName, argumentTypeClasses),
                    argumentTypes,
                    qualifierTypes,
                    genericTypes
            );

            // now invoke super(..) if no arg constructor
        } else {
            defaultConstructor.visitInsn(ACONST_NULL);
        }
        defaultConstructor.invokeConstructor(
                beanDefinitionType,
                BEAN_DEFINITION_METHOD_CONSTRUCTOR
        );

        defaultConstructor.visitInsn(RETURN);
        defaultConstructor.visitMaxs(DEFAULT_MAX_STACK, 1);
        defaultConstructor.visitEnd();
    }


    private void visitFieldInjectionPointInternal(Object declaringType, Object qualifierType, boolean requiresReflection, Object fieldType, String fieldName, Method methodToInvoke, boolean isValueOptional) {
        // ready this
        GeneratorAdapter constructorVisitor = this.constructorVisitor;


        constructorVisitor.loadThis();

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
        constructorVisitor.push(requiresReflection);

        // invoke addInjectionPoint method
        pushInvokeMethodOnSuperClass(constructorVisitor, ADD_FIELD_INJECTION_POINT_METHOD);

        GeneratorAdapter injectMethodVisitor = this.injectMethodVisitor;

        Label falseCondition = null;
        if(isValueOptional) {
            Label trueCondition = new Label();
            falseCondition = new Label();
            injectMethodVisitor.loadThis();
            // 1st argument load BeanResolutionContext
            injectMethodVisitor.loadArg(0);
            // 2nd argument load BeanContext
            injectMethodVisitor.loadArg(1);
            // 3rd argument the field index
            injectMethodVisitor.push(currentFieldIndex);

            // invoke method containsValueForMethodArgument
            injectMethodVisitor.invokeVirtual(beanDefinitionType, org.objectweb.asm.commons.Method.getMethod(CONTAINS_VALUE_FOR_FIELD));
            injectMethodVisitor.push(false);

            injectMethodVisitor.ifCmp(Type.BOOLEAN_TYPE, GeneratorAdapter.EQ,falseCondition );
            injectMethodVisitor.visitLabel(trueCondition);
        }

        if (!requiresReflection) {
            // if reflection is not required then set the field automatically within the body of the "injectBean" method

            injectMethodVisitor.visitVarInsn(ALOAD, injectInstanceIndex);
            // first get the value of the field by calling AbstractBeanDefinition.getBeanForField(..)
            // load 'this'
            injectMethodVisitor.loadThis();
            // 1st argument load BeanResolutionContext
            injectMethodVisitor.visitVarInsn(ALOAD, 1);
            // 2nd argument load BeanContext
            injectMethodVisitor.visitVarInsn(ALOAD, 2);
            // 3rd argument the field index
            injectMethodVisitor.push(currentFieldIndex);
            // invoke getBeanForField
            pushInvokeMethodOnSuperClass(injectMethodVisitor, methodToInvoke);
            // cast the return value to the correct type
            pushCastToType(injectMethodVisitor, fieldType);

            injectMethodVisitor.visitFieldInsn(PUTFIELD, declaringTypeRef.getInternalName(), fieldName, getTypeDescriptor(fieldType));
        }
        else {
            // if reflection is required at reflective call
            pushInjectMethodForIndex(injectMethodVisitor, injectInstanceIndex, currentFieldIndex, "injectBeanField");
        }
        if(falseCondition != null) {
            injectMethodVisitor.visitLabel(falseCondition);
        }
        currentFieldIndex++;

    }

    private int pushGetFieldFromTypeLocalVariable(MethodVisitor methodVisitor, Type declaringType, String fieldName) {
        methodVisitor.visitLdcInsn(declaringType);
        // and the field name
        methodVisitor.visitLdcInsn(fieldName);
        pushInvokeMethodOnClass(methodVisitor, "getDeclaredField", String.class);

        // store the field within using the field index. A pre-increment is used because 0 contains "this"
        return pushNewConstructorLocalVariable();
    }

    private int pushGetMethodFromTypeCallLocalVariable(GeneratorAdapter methodVisitor, Type declaringType, String methodName, Collection<Object> argumentTypes) {
        pushGetMethodFromTypeCall(methodVisitor, declaringType, methodName, argumentTypes);
        return pushNewConstructorLocalVariable();
    }

    private void addInjectionPointForSetterInternal(
            Object qualifierType,
            boolean requiresReflection,
            Object fieldType,
            String fieldName,
            String setterName,
            Map<String, Object> genericTypes,
            Type declaringTypeRef) {
        GeneratorAdapter generatorAdapter = this.constructorVisitor;
        int fieldVarIndex = pushGetFieldFromTypeLocalVariable(generatorAdapter, declaringTypeRef, fieldName);
        List<Object> argumentTypes = Collections.singletonList(fieldType);
        int currentMethodVar = pushGetMethodFromTypeCallLocalVariable(generatorAdapter, declaringTypeRef, setterName, argumentTypes);
        generatorAdapter.visitVarInsn(ALOAD, currentMethodVar);

        // load this
        generatorAdapter.visitVarInsn(ALOAD, 0);
        // 1st argument: the field
        generatorAdapter.visitVarInsn(ALOAD, fieldVarIndex);

        // 2nd argument: the method
        generatorAdapter.visitVarInsn(ALOAD,  currentMethodVar);



        // 1st argument: the constructor
        generatorAdapter.visitVarInsn(ALOAD, fieldVarIndex);
        // 2nd argument: The argument name
        generatorAdapter.push(fieldName);
        // 3rd argument:  The qualifier type
        if(qualifierType != null) {
            generatorAdapter.push(getTypeReference(qualifierType));
        }
        else {
            generatorAdapter.visitInsn(ACONST_NULL);
        }

        // 5h argument: The generic types
        if(genericTypes != null) {
            buildTypeArguments(generatorAdapter,genericTypes);
        }
        else {
            generatorAdapter.visitInsn(ACONST_NULL);
        }

        // Argument.create( .. )
        generatorAdapter.invokeStatic(
                Type.getType(Argument.class),
                METHOD_CREATE_ARGUMENT_FIELD
        );

        // 4th argument: requires reflection
        generatorAdapter.visitInsn(requiresReflection ? ICONST_1 : ICONST_0);

        // now invoke the addInjectionPoint method
        pushInvokeMethodOnSuperClass(generatorAdapter, ADD_SETTER_INJECTION_POINT_METHOD);
    }

    private void visitMethodInjectionPointInternal(Object declaringType,
                                                   boolean requiresReflection,
                                                   Object returnType,
                                                   String methodName,
                                                   Map<String, Object> argumentTypes,
                                                   Map<String, Object> qualifierTypes,
                                                   Map<String, Map<String, Object>> genericTypes,
                                                   GeneratorAdapter constructorVisitor,
                                                   GeneratorAdapter injectMethodVisitor,
                                                   int injectInstanceIndex) {
        boolean hasArguments = argumentTypes != null && !argumentTypes.isEmpty();
        int argCount = hasArguments ? argumentTypes.size() : 0;
        Type declaringTypeRef = getTypeReference(declaringType);


        // load 'this'
        constructorVisitor.visitVarInsn(ALOAD, 0);

        Collection<Object> argumentTypeClasses = hasArguments ? argumentTypes.values() : Collections.emptyList();
        // 1st argument: The method
        int currentMethodVar = pushGetMethodFromTypeCallLocalVariable(constructorVisitor, declaringTypeRef, methodName, argumentTypeClasses);
        constructorVisitor.visitVarInsn(ALOAD, currentMethodVar);
        if (hasArguments) {
            pushBuildArgumentsForMethod(
                    constructorVisitor,
                    generatorAdapter -> generatorAdapter.visitVarInsn(ALOAD, currentMethodVar),
                    argumentTypes,
                    qualifierTypes,
                    genericTypes);
        } else {
            // 2nd argument to addInjectPoint: The argument types
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
                Iterator<Map.Entry<String,Object>> argIterator = argumentTypes.entrySet().iterator();
                for (int i = 0; i < argCount; i++) {
                    Map.Entry<String, Object> entry = argIterator.next();
                    Object qualifier = qualifierTypes.get(entry.getKey());

                    // first get the value of the field by calling AbstractBeanDefinition.getBeanForMethod(..)
                    // load 'this'
                    injectMethodVisitor.visitVarInsn(ALOAD, 0);
                    // 1st argument load BeanResolutionContext
                    injectMethodVisitor.visitVarInsn(ALOAD, 1);
                    // 2nd argument load BeanContext
                    injectMethodVisitor.visitVarInsn(ALOAD, 2);
                    // 3rd argument the method index
                    injectMethodVisitor.push(currentMethodIndex);
                    // 4th argument the argument index
                    injectMethodVisitor.push(i);
                    // invoke getBeanForField

                    Method methodToInvoke = isValueType(qualifier) ? GET_VALUE_FOR_METHOD_ARGUMENT : GET_BEAN_FOR_METHOD_ARGUMENT;
                    pushInvokeMethodOnSuperClass(injectMethodVisitor, methodToInvoke);
                    // cast the return value to the correct type
                    pushCastToType(injectMethodVisitor, entry.getValue());
                }
            } else {
                methodDescriptor = getMethodDescriptor(returnType, Collections.emptyList());
            }
            injectMethodVisitor.visitMethodInsn(isInterface ? INVOKEINTERFACE : INVOKEVIRTUAL,
                    declaringTypeRef.getInternalName(), methodName,
                    methodDescriptor, isInterface);
        }
        else {
            // otherwise use injectBeanMethod instead which triggers reflective injection
            pushInjectMethodForIndex(injectMethodVisitor, injectInstanceIndex, currentMethodIndex, "injectBeanMethod");
        }

        // increment the method index
        currentMethodIndex++;
    }

    static void pushBuildArgumentsForMethod(
            GeneratorAdapter generatorAdapter,
            Consumer<GeneratorAdapter> methodSupplier,
            Map<String, Object> argumentTypes,
            Map<String, Object> qualifierTypes,
            Map<String, Map<String, Object>> genericTypes) {
        int len = argumentTypes.size();
        pushNewArray(generatorAdapter, Argument.class, len);
        int i = 0;
        for (Map.Entry<String, Object> entry : argumentTypes.entrySet()) {
            // the array index position
            generatorAdapter.push(i);

            String argumentName = entry.getKey();

            // 1st argument: the constructor
            methodSupplier.accept(generatorAdapter);
            // 2nd argument: The argument name
            generatorAdapter.push(argumentName);
            // 3rd argument: The index
            generatorAdapter.push(i);

            // 4th argument: The qualifier type
            if(qualifierTypes != null && qualifierTypes.containsKey(argumentName)) {
                generatorAdapter.push(getTypeReference(qualifierTypes.get(argumentName)));
            }
            else {
                generatorAdapter.visitInsn(ACONST_NULL);
            }

            // 5h argument: The generic types
            if(genericTypes != null && genericTypes.containsKey(argumentName)) {
                Map<String, Object> types = genericTypes.get(argumentName);
                buildTypeArguments(generatorAdapter, types);
            }
            else {
                generatorAdapter.visitInsn(ACONST_NULL);
            }

            // Argument.create( .. )
            generatorAdapter.invokeStatic(
                    Type.getType(Argument.class),
                    METHOD_CREATE_ARGUMENT_METHOD
            );
            // store the type reference
            generatorAdapter.visitInsn(AASTORE);
            // if we are not at the end of the array duplicate array onto the stack
            if (i != (len - 1)) {
                generatorAdapter.visitInsn(DUP);
            }
            i++;
        }
    }

    private void pushInvokeMethodOnSuperClass(MethodVisitor constructorVisitor, Method methodToInvoke) {
        constructorVisitor.visitMethodInsn(INVOKESPECIAL,
                isSuperFactory ? TYPE_ABSTRACT_BEAN_DEFINITION.getInternalName() : superType.getInternalName(),
                methodToInvoke.getName(),
                Type.getMethodDescriptor(methodToInvoke),
                false);
    }

    private void resolveBeanOrValueForSetter(Type declaringTypeRef, String setterName, Object fieldType, Method resolveMethod, boolean isValueOptional) {
        GeneratorAdapter injectVisitor = this.injectMethodVisitor;

        Label falseCondition = null;
        if(isValueOptional) {
            Label trueCondition = new Label();
            falseCondition = new Label();
            injectVisitor.loadThis();
            // 1st argument load BeanResolutionContext
            injectVisitor.loadArg(0);
            // 2nd argument load BeanContext
            injectVisitor.loadArg(1);
            // 3rd argument the field index
            injectVisitor.push(currentMethodIndex);
            // 4th argument the argument index
            injectVisitor.push(0);

            // invoke method containsValueForMethodArgument
            injectVisitor.invokeVirtual(beanDefinitionType, org.objectweb.asm.commons.Method.getMethod(CONTAINS_VALUE_FOR_METHOD_ARGUMENT));
            injectVisitor.push(false);

            injectVisitor.ifCmp(Type.BOOLEAN_TYPE, GeneratorAdapter.EQ,falseCondition );
            injectVisitor.visitLabel(trueCondition);
        }
        // invoke the method on this injected instance
        injectVisitor.visitVarInsn(ALOAD, injectInstanceIndex);
        String methodDescriptor = getMethodDescriptor("void", Collections.singletonList(fieldType));
        // first get the value of the field by calling AbstractBeanDefinition.getBeanForField(..)
        // load 'this'
        injectMethodVisitor.visitVarInsn(ALOAD, 0);
        // 1st argument load BeanResolutionContext
        injectMethodVisitor.visitVarInsn(ALOAD, 1);
        // 2nd argument load BeanContext
        injectMethodVisitor.visitVarInsn(ALOAD, 2);
        // 3rd argument the field index
        injectMethodVisitor.push(currentMethodIndex);
        // 4th argument the argument index
        // 5th argument is the default value
        injectVisitor.push(0);
        // invoke getBeanForField
        pushInvokeMethodOnSuperClass(injectVisitor, resolveMethod);
        // cast the return value to the correct type
        pushCastToType(injectVisitor, fieldType);
        injectVisitor.visitMethodInsn(INVOKEVIRTUAL,
                declaringTypeRef.getInternalName(), setterName,
                methodDescriptor, false);
        if(falseCondition != null) {
            injectVisitor.visitLabel(falseCondition);
        }
    }

    static void pushInvokeMethodOnClass(MethodVisitor methodVisitor, String classMethodName, Class... classMethodArgs) {
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
            String desc = getMethodDescriptor(Object.class.getName(), BeanResolutionContext.class.getName(), BeanContext.class.getName(), Object.class.getName());
            injectMethodVisitor = new GeneratorAdapter(classWriter.visitMethod(
                    ACC_PROTECTED,
                    "injectBean",
                    desc,
                    null,
                    null), ACC_PROTECTED, "injectBean", desc);

            GeneratorAdapter injectMethodVisitor = this.injectMethodVisitor;
            if(isConfigurationProperties) {
                injectMethodVisitor.loadThis();
                injectMethodVisitor.loadArg(0); // the resolution context
                injectMethodVisitor.loadArg(1); // the bean context
                // invoke AbstractBeanDefinition.containsProperties(..)
                injectMethodVisitor.invokeVirtual(beanDefinitionType, org.objectweb.asm.commons.Method.getMethod(CONTAINS_PROPERTIES_METHOD));
                injectMethodVisitor.push(false);
                injectEnd = new Label();
                injectMethodVisitor.ifCmp(Type.BOOLEAN_TYPE, GeneratorAdapter.EQ, injectEnd );
                // add the true condition
                injectMethodVisitor.visitLabel(new Label());
            }
            // The object being injected is argument 3 of the inject method
            injectMethodVisitor.visitVarInsn(ALOAD, 3);
            // store it in a local variable
            injectMethodVisitor.visitTypeInsn(CHECKCAST, beanType.getInternalName());
            injectInstanceIndex = pushNewInjectLocalVariable();
            injectMethodVisitor.visitInsn(ACONST_NULL);
            optionalInstanceIndex = pushNewInjectLocalVariable();
        }
    }

    private void visitPostConstructMethodDefinition() {
        if (postConstructMethodVisitor == null) {
            interfaceTypes.add(InitializingBeanDefinition.class);

            // override the post construct method
            GeneratorAdapter postConstructMethodVisitor = newLifeCycleMethod("initialize");

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

    private void pushInjectMethodForIndex(GeneratorAdapter methodVisitor, int instanceIndex, int injectIndex, String injectMethodName) {
        Method injectBeanMethod = ReflectionUtils.getRequiredMethod(AbstractBeanDefinition.class, injectMethodName, BeanResolutionContext.class, DefaultBeanContext.class, int.class, Object.class);
        // load 'this'
        methodVisitor.visitVarInsn(ALOAD, 0);
        // 1st argument load BeanResolutionContext
        methodVisitor.visitVarInsn(ALOAD, 1);
        // 2nd argument load BeanContext
        methodVisitor.visitVarInsn(ALOAD, 2);
        pushCastToType(methodVisitor, DefaultBeanContext.class);
        // 3rd argument the method index
        methodVisitor.push(injectIndex);
        // 4th argument: the instance being injected
        methodVisitor.visitVarInsn(ALOAD, instanceIndex);

        pushInvokeMethodOnSuperClass(methodVisitor, injectBeanMethod);
    }

    private void visitPreDestroyMethodDefinition() {
        if (preDestroyMethodVisitor == null) {
            interfaceTypes.add(DisposableBeanDefinition.class);

            // override the post construct method
            GeneratorAdapter preDestroyMethodVisitor = newLifeCycleMethod("dispose");

            this.preDestroyMethodVisitor = preDestroyMethodVisitor;
            // The object being injected is argument 3 of the inject method
            preDestroyMethodVisitor.visitVarInsn(ALOAD, 3);
            // store it in a local variable
            preDestroyMethodVisitor.visitTypeInsn(CHECKCAST, beanType.getInternalName());
            preDestroyInstanceIndex = pushNewPreDestroyLocalVariable();

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
        if(injectEnd != null) {
            injectMethodVisitor.visitLabel(injectEnd);
        }

        invokeSuperInjectMethod(injectMethodVisitor, INJECT_BEAN_METHOD);
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

    private void visitBuildFactoryMethodDefinition(Object factoryClass, String methodName, Map<String, Object> argumentTypes, Map<String, Object> qualifierTypes) {
        if (buildMethodVisitor == null) {
            defineBuilderMethod(qualifierTypes);
            // load this

            GeneratorAdapter buildMethodVisitor = this.buildMethodVisitor;
            // Load the BeanContext for the method call
            buildMethodVisitor.visitVarInsn(ALOAD, 2);
            pushCastToType(buildMethodVisitor, DefaultBeanContext.class);
            // load the first argument of the method (the BeanResolutionContext) to be passed to the method
            buildMethodVisitor.visitVarInsn(ALOAD, 1);
            // second argument is the bean type
            Type factoryType = getTypeReference(factoryClass);
            buildMethodVisitor.visitLdcInsn(factoryType);
            Method getBeanMethod = ReflectionUtils.getDeclaredMethod(DefaultBeanContext.class, "getBean", BeanResolutionContext.class, Class.class).orElseThrow(() ->
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


            if (argumentTypes.isEmpty()) {
                buildMethodVisitor.visitMethodInsn(INVOKEVIRTUAL,
                        factoryType.getInternalName(),
                        methodName,
                        Type.getMethodDescriptor(beanType), false);
            } else {
                pushContructorArguments(buildMethodVisitor, argumentTypes, qualifierTypes);
                String methodDescriptor = getMethodDescriptor(beanFullClassName, argumentTypes.values());
                buildMethodVisitor.visitMethodInsn(INVOKEVIRTUAL,
                        factoryType.getInternalName(),
                        methodName,
                        methodDescriptor, false);
            }
            this.buildInstanceIndex = pushNewBuildLocalVariable();
            pushBeanDefinitionMethodInvocation(buildMethodVisitor, "injectBean");
            pushCastToType(buildMethodVisitor, beanFullClassName);
            buildMethodVisitor.visitVarInsn(ASTORE, buildInstanceIndex);
            buildMethodVisitor.visitVarInsn(ALOAD, buildInstanceIndex);
        }
    }

    private void visitBuildMethodDefinition(Map<String, Object> argumentTypes, Map<String, Object> qualifierTypes) {
        if (buildMethodVisitor == null) {
            defineBuilderMethod(qualifierTypes);
            // load this

            GeneratorAdapter buildMethodVisitor = this.buildMethodVisitor;
            buildMethodVisitor.visitTypeInsn(NEW, beanType.getInternalName());
            buildMethodVisitor.visitInsn(DUP);
            pushContructorArguments(buildMethodVisitor, argumentTypes, qualifierTypes);
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

    private void pushContructorArguments(GeneratorAdapter buildMethodVisitor, Map<String, Object> argumentTypes, Map<String, Object> qualifierTypes) {
        int size = argumentTypes.size();
        if (size > 0) {
            Iterator<Map.Entry<String,Object>> iterator = argumentTypes.entrySet().iterator();
            for (int i = 0; i < size; i++) {
                Map.Entry<String,Object> entry = iterator.next();
                Object qualifierType = qualifierTypes.get(entry.getKey());
                if(isArgumentType(qualifierType) && argsIndex > -1) {
                    // load the args
                    buildMethodVisitor.visitVarInsn(ALOAD, argsIndex);
                    // the argument name
                    buildMethodVisitor.push(entry.getKey());
                    buildMethodVisitor.invokeInterface(Type.getType(Map.class), org.objectweb.asm.commons.Method.getMethod(ReflectionUtils.getRequiredMethod(Map.class, "get", Object.class)));
                    pushCastToType(buildMethodVisitor, entry.getValue());
                }
                else {

                    // Load this for method call
                    buildMethodVisitor.visitVarInsn(ALOAD, 0);

                    // load the first two arguments of the method (the BeanResolutionContext and the BeanContext) to be passed to the method
                    buildMethodVisitor.visitVarInsn(ALOAD, 1);
                    buildMethodVisitor.visitVarInsn(ALOAD, 2);
                    // pass the index of the method as the third argument
                    buildMethodVisitor.push(i);
                    // invoke the getBeanForConstructorArgument method
                    Method methodToInvoke = isValueType(qualifierType) ? GET_VALUE_FOR_CONSTRUCTOR_ARGUMENT : GET_BEAN_FOR_CONSTRUCTOR_ARGUMENT;
                    pushInvokeMethodOnSuperClass(buildMethodVisitor, methodToInvoke);
                    pushCastToType(buildMethodVisitor, entry.getValue());
                }
            }
        }
    }

    private boolean isValueType(Object qualifierType) {
        if(qualifierType != null) {
            if(qualifierType instanceof Class) {
                return Value.class.getName().equals(((Class)qualifierType).getName());
            }
            else if(qualifierType instanceof CharSequence) {
                return Value.class.getName().equals(qualifierType.toString());
            }
        }
        return false;
    }

    private boolean isArgumentType(Object qualifierType) {
        if(qualifierType != null) {
            if(qualifierType instanceof Class) {
                return org.particleframework.context.annotation.Argument.class.getName().equals(((Class)qualifierType).getName());
            }
            else if(qualifierType instanceof CharSequence) {
                return org.particleframework.context.annotation.Argument.class.getName().equals(qualifierType.toString());
            }
        }
        return false;
    }

    private void defineBuilderMethod(Map<String, Object> qualifierTypes) {
        Optional<Object> argumentQualifier = qualifierTypes != null ? qualifierTypes.values().stream().filter(this::isArgumentType).findFirst() : Optional.empty();
        boolean isParametrized = argumentQualifier.isPresent();
        if(isParametrized) {
            superType = TYPE_ABSTRACT_PARAMETRIZED_BEAN_DEFINITION;
            argsIndex = buildMethodLocalCount++;
        }

        String methodDescriptor;
        String methodSignature;
        if(isParametrized) {
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

        }
        else {

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

        String methodName = argumentQualifier.isPresent() ? "doBuild" : "build";
        this.buildMethodVisitor = new GeneratorAdapter(classWriter.visitMethod(
                ACC_PUBLIC,
                methodName,
                methodDescriptor,
                methodSignature,
                null),ACC_PUBLIC,methodName,methodDescriptor);
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

    private void visitBeanDefinitionConstructorInternal(Map<String, Object> argumentTypes, Map<String, Object> qualifierTypes, Map<String, Map<String, Object>> genericTypes) {
        if (constructorVisitor == null) {
            Optional<Object> argumentQualifier = qualifierTypes != null ? qualifierTypes.values().stream().filter(this::isArgumentType).findFirst() : Optional.empty();
            boolean isParametrized = argumentQualifier.isPresent();
            if(isParametrized) {
                superType = TYPE_ABSTRACT_PARAMETRIZED_BEAN_DEFINITION;
            }

            GeneratorAdapter staticInit = visitStaticInitializer(classWriter);
            classWriter.visitField(ACC_PRIVATE_STATIC_FINAL, FIELD_CONSTRUCTOR, TYPE_CONSTRUCTOR.getDescriptor(), null, null);


            Collection<Object> argumentClassNames = argumentTypes.values();

            pushGetConstructorForType(staticInit, this.beanType, argumentClassNames);

            staticInit.putStatic(
                    beanDefinitionType,
                    FIELD_CONSTRUCTOR,
                    TYPE_CONSTRUCTOR
            );

            staticInit.visitInsn(RETURN);
            staticInit.visitMaxs(1,1);
            staticInit.visitEnd();


            org.objectweb.asm.commons.Method constructorMethod = org.objectweb.asm.commons.Method.getMethod(CONSTRUCTOR_ABSTRACT_BEAN_DEFINITION);
            GeneratorAdapter protectedConstructor = new GeneratorAdapter(
                    classWriter.visitMethod(ACC_PROTECTED, CONSTRUCTOR_NAME, constructorMethod.getDescriptor(), null, null),
                    ACC_PROTECTED,
                    CONSTRUCTOR_NAME,
                    constructorMethod.getDescriptor()
            );
            constructorVisitor = protectedConstructor;

            Type[] beanDefinitionConstructorArgumentTypes = constructorMethod.getArgumentTypes();
            protectedConstructor.loadThis();
            for (int i = 0; i < beanDefinitionConstructorArgumentTypes.length; i++) {
                protectedConstructor.loadArg(i);
            }
            protectedConstructor.invokeConstructor(isSuperFactory ? TYPE_ABSTRACT_BEAN_DEFINITION : superType, BEAN_DEFINITION_CLASS_CONSTRUCTOR);

            MethodVisitor defaultConstructor = startConstructor(classWriter);
            GeneratorAdapter defaultConstructorVisitor = new GeneratorAdapter(
                    defaultConstructor,
                    ACC_PUBLIC,
                    CONSTRUCTOR_NAME,
                    DESCRIPTOR_DEFAULT_CONSTRUCTOR
            );
            // ALOAD 0
            defaultConstructor.visitVarInsn(ALOAD, 0);

            // 1st argument: pass either true or false to second argument of super(..) for singleton status
            if (isSingleton) {
                defaultConstructor.visitInsn(ICONST_1);
            } else {
                defaultConstructor.visitInsn(ICONST_0);
            }

            // 2nd argument: pass the bean definition type as the third argument to super(..)
            defaultConstructor.visitLdcInsn(beanType);

            // 3rd Argument: pass the constructor used to create the bean as the third argument
            defaultConstructorVisitor.getStatic(beanDefinitionType, FIELD_CONSTRUCTOR, TYPE_CONSTRUCTOR);


            // 4th argument: An array of Argument instances

            // now invoke super(..) if no arg constructor
            if (argumentTypes.isEmpty()) {
                defaultConstructor.visitInsn(ACONST_NULL);
            } else {
                // we have a constructor with arguments
                // Build calls to Argument.create(...)

                int len = argumentTypes.size();
                defaultConstructorVisitor.push(len);
                defaultConstructorVisitor.newArray(Type.getType(Argument.class));
                defaultConstructorVisitor.dup();
                int i = 0;
                for (Map.Entry<String, Object> entry : argumentTypes.entrySet()) {
                    // the array index position
                    defaultConstructorVisitor.push(i);

                    String argumentName = entry.getKey();

                    // 1st argument: the constructor
                    defaultConstructorVisitor.getStatic(beanDefinitionType, FIELD_CONSTRUCTOR, TYPE_CONSTRUCTOR);
                    // 2nd argument: The argument name
                    defaultConstructorVisitor.push(argumentName);
                    // 3rd argument: The index
                    defaultConstructorVisitor.push(i);

                    // 4th argument: The qualifier type
                    if(qualifierTypes != null && qualifierTypes.containsKey(argumentName)) {
                        defaultConstructorVisitor.push(getTypeReference(qualifierTypes.get(argumentName)));
                    }
                    else {
                        defaultConstructorVisitor.visitInsn(ACONST_NULL);
                    }

                    // 5h argument: The generic types
                    buildTypeArguments(defaultConstructorVisitor, genericTypes.get(argumentName));

                    // Argument.create( .. )
                    defaultConstructorVisitor.invokeStatic(
                            Type.getType(Argument.class),
                            METHOD_CREATE_ARGUMENT_CONSTRUCTOR
                    );
                    // store the type reference
                    defaultConstructorVisitor.visitInsn(AASTORE);
                    // if we are not at the end of the array duplicate array onto the stack
                    i++;
                    if (i < len) {
                        defaultConstructorVisitor.visitInsn(DUP);
                    }
                }
            }


            defaultConstructorVisitor.invokeConstructor(
                    beanDefinitionType,
                    BEAN_DEFINITION_CLASS_CONSTRUCTOR
            );

            defaultConstructorVisitor.visitInsn(RETURN);
            defaultConstructorVisitor.visitMaxs(DEFAULT_MAX_STACK, 1);
            defaultConstructorVisitor.visitEnd();
        }


    }

    static void buildTypeArguments(GeneratorAdapter generatorAdapter, Map<String, Object> types) {
        if(types == null || types.isEmpty()) {
            generatorAdapter.visitInsn(ACONST_NULL);
            return;
        }
        int len = types.size();
        // Build calls to Argument.create(...)
        pushNewArray(generatorAdapter, Argument.class, len);
        int i = 0;
        for (Map.Entry<String, Object> entry : types.entrySet()) {
            // the array index
            generatorAdapter.push(i);
            String typeParameterName = entry.getKey();
            Object value = entry.getValue();
            if(value instanceof Map) {
                buildArgumentWithGenerics(generatorAdapter, typeParameterName, (Map)value);
            }
            else {
                buildArgument(generatorAdapter, typeParameterName, value);
            }

            // store the type reference
            generatorAdapter.visitInsn(AASTORE);
            // if we are not at the end of the array duplicate array onto the stack
            if (i != (len - 1)) {
                generatorAdapter.visitInsn(DUP);
            }
            i++;
        }
    }

    private static void buildArgument(GeneratorAdapter generatorAdapter, String argumentName, Object objectType) {

        // 1st argument: the type
        generatorAdapter.push(getTypeReference(objectType));
        // 2nd argument: the name
        generatorAdapter.push(argumentName);


        // Argument.create( .. )
        generatorAdapter.invokeStatic(
                Type.getType(Argument.class),
                METHOD_CREATE_ARGUMENT_SIMPLE
        );
    }

    private static void buildArgumentWithGenerics(GeneratorAdapter generatorAdapter, String argumentName, Map nestedTypeObject) {
        Map nestedTypes = null;
        Optional<Map.Entry> nestedEntry = nestedTypeObject.entrySet().stream().findFirst();
        Object objectType;
        if(nestedEntry.isPresent()) {
            Map.Entry data = nestedEntry.get();
            Object key = data.getKey();
            Object map = data.getValue();
            objectType = key;
            if(map instanceof Map) {
                nestedTypes = (Map) map;
            }
        }
        else {
            throw new IllegalArgumentException("Must be a map with a single key containing the argument type and a map of generics as the value");
        }

        // 1st argument: the type
        generatorAdapter.push(getTypeReference(objectType));
        // 2nd argument: the name
        generatorAdapter.push(argumentName);

        // 3rd argument: generic types
        boolean hasGenerics = nestedTypes != null;
        if(hasGenerics) {
            buildTypeArguments(generatorAdapter, nestedTypes);
        }

        // Argument.create( .. )
        generatorAdapter.invokeStatic(
                Type.getType(Argument.class),
                hasGenerics ? METHOD_CREATE_ARGUMENT_WITH_GENERICS : METHOD_CREATE_ARGUMENT_SIMPLE
        );
    }


    private GeneratorAdapter buildProtectedConstructor(org.objectweb.asm.commons.Method constructorType) {
        GeneratorAdapter protectedConstructor = new GeneratorAdapter(
                classWriter.visitMethod(ACC_PROTECTED, CONSTRUCTOR_NAME, constructorType.getDescriptor(), null, null),
                ACC_PROTECTED,
                CONSTRUCTOR_NAME,
                constructorType.getDescriptor()
        );

        Type[] arguments = constructorType.getArgumentTypes();
        protectedConstructor.loadThis();
        for (int i = 0; i < arguments.length; i++) {
            protectedConstructor.loadArg(i);
        }
        if(isSuperFactory) {
            protectedConstructor.invokeConstructor(TYPE_ABSTRACT_BEAN_DEFINITION, constructorType);
        }
        else {
            protectedConstructor.invokeConstructor(superType, constructorType);
        }
        return protectedConstructor;
    }


    static void pushGetMethodFromTypeCall(GeneratorAdapter methodVisitor, Type declaringType, String methodName, Collection<Object> argumentTypes) {
        // lookup the Method instance from the declaring type
        methodVisitor.visitLdcInsn(declaringType);
        pushMethodNameAndTypesArguments(methodVisitor, methodName, argumentTypes);


        // 1st argument to addInjectPoint: The Method
        methodVisitor.invokeStatic(Type.getType(ReflectionUtils.class), METHOD_GET_REQUIRED_METHOD);
    }

    static void pushGetConstructorForType(GeneratorAdapter methodVisitor, Type beanType, Collection<Object> argumentClassNames) {
        methodVisitor.visitLdcInsn(beanType);

        int argCount = argumentClassNames.size();
        Object[] argumentTypeArray = argumentClassNames.toArray(new Object[argCount]);
        methodVisitor.push(argCount);
        methodVisitor.newArray(TYPE_CLASS);
        if(argCount > 0) {

            methodVisitor.dup();
            for (int i = 0; i < argCount; i++) {
                pushStoreTypeInArray(methodVisitor, i, argCount, argumentTypeArray[i]);
            }
        }

        // invoke Class.getConstructor()
        String getDeclaredConstructorMethod = "getDeclaredConstructor";
        Method getConstructorMethod = ReflectionUtils.getDeclaredMethod(Class.class, getDeclaredConstructorMethod, Class[].class)
                .orElseThrow(() ->
                        new IllegalStateException("Class.getConstructor(..) method not found")
                );
        methodVisitor.visitMethodInsn(
                INVOKEVIRTUAL,
                Type.getInternalName(Class.class),
                getDeclaredConstructorMethod,
                Type.getType(getConstructorMethod).getDescriptor(),
                false);
    }


    /**
     * Adds a method call to get the given annotation of the given type to tye stack
     *
     * @param targetClass    The target class
     * @param annotationType The annotation type
     */
    private void pushGetAnnotationForType(MethodVisitor methodVisitor, Type targetClass, Type annotationType) {
        methodVisitor.visitLdcInsn(targetClass);
        pushGetAnnotationCall(methodVisitor, annotationType);
    }

    private void pushGetAnnotationCall(MethodVisitor methodVisitor, Type annotationType) {
        methodVisitor.visitLdcInsn(annotationType);
        Method method = ReflectionUtils.getDeclaredMethod(Class.class, "getAnnotation", Class.class)
                .orElseThrow(() ->
                        new IllegalStateException("Class.getAnnotation(..) method not found")
                );
        String descriptor = Type.getType(method).getDescriptor();
        methodVisitor.visitMethodInsn(INVOKEVIRTUAL,
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

    private String generateBeanDefSig(String typeParameter) {
        SignatureVisitor sv = new SignatureWriter();
        visitSuperTypeParameters(sv, typeParameter);

        String beanTypeInternalName = getInternalName(typeParameter);
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

    private void visitSuperTypeParameters(SignatureVisitor sv, String... typeParameters) {


        // visit super class
        SignatureVisitor psv = sv.visitSuperclass();
        psv.visitClassType(isSuperFactory ? TYPE_ABSTRACT_BEAN_DEFINITION.getInternalName() : superType.getInternalName());
        if (superType == TYPE_ABSTRACT_BEAN_DEFINITION || superType == TYPE_ABSTRACT_PARAMETRIZED_BEAN_DEFINITION || isSuperFactory) {
            for (String typeParameter : typeParameters) {

                SignatureVisitor ppsv = psv.visitTypeArgument('=');
                String beanTypeInternalName = getInternalName(typeParameter);
                ppsv.visitClassType(beanTypeInternalName);
                ppsv.visitEnd();
            }
        }

        psv.visitEnd();

    }


}
