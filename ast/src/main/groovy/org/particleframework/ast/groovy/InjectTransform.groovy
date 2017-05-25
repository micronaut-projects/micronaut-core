package org.particleframework.ast.groovy

import groovy.transform.CompilationUnitAware
import groovy.transform.CompileStatic
import groovy.transform.PackageScope
import org.codehaus.groovy.ast.*
import org.codehaus.groovy.control.CompilationUnit
import org.codehaus.groovy.control.CompilePhase
import org.codehaus.groovy.control.SourceUnit
import org.codehaus.groovy.transform.ASTTransformation
import org.codehaus.groovy.transform.GroovyASTTransformation
import org.particleframework.ast.groovy.annotation.AnnotationStereoTypeFinder
import org.particleframework.ast.groovy.descriptor.ServiceDescriptorGenerator
import org.particleframework.ast.groovy.utils.AstAnnotationUtils
import org.particleframework.ast.groovy.utils.AstGenericUtils
import org.particleframework.ast.groovy.utils.AstMessageUtils
import org.particleframework.context.annotation.Configuration
import org.particleframework.context.annotation.Context
import org.particleframework.inject.BeanConfiguration
import org.particleframework.inject.BeanDefinitionClass
import org.particleframework.inject.asm.BeanDefinitionClassWriter
import org.particleframework.inject.asm.BeanDefinitionWriter
import org.particleframework.inject.asm.BeanConfigurationWriter

import javax.annotation.PostConstruct
import javax.annotation.PreDestroy
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Qualifier
import javax.inject.Scope
import javax.inject.Singleton
import java.lang.reflect.Modifier

import static org.codehaus.groovy.ast.ClassHelper.makeCached
import static org.codehaus.groovy.ast.tools.GeneralUtils.*

/**
 * An AST transformation that produces metadata for use by the injection container
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@CompileStatic
@GroovyASTTransformation(phase = CompilePhase.SEMANTIC_ANALYSIS)
class InjectTransform implements ASTTransformation, CompilationUnitAware {

    CompilationUnit unit
    AnnotationStereoTypeFinder stereoTypeFinder = new AnnotationStereoTypeFinder()

    @Override
    void visit(ASTNode[] nodes, SourceUnit source) {
        ModuleNode moduleNode = source.getAST()
        Map<ClassNode, BeanDefinitionWriter> beanDefinitionWriters = [:]

        List<ClassNode> classes = moduleNode.getClasses()
        if (classes.size() == 1) {
            ClassNode classNode = classes[0]
            if (classNode.nameWithoutPackage == 'package-info') {
                PackageNode packageNode = classNode.getPackage()
                AnnotationNode annotationNode = AstAnnotationUtils.findAnnotation(packageNode, Configuration.name)
                if (annotationNode != null) {
                    BeanConfigurationWriter writer = new BeanConfigurationWriter()
                    String configurationName = null

                    try {
                        configurationName = writer.writeConfiguration(classNode.packageName, source.configuration.targetDirectory)
                    } catch (Throwable e) {
                        AstMessageUtils.error(source, classNode, "Error generating bean configuration for package-info class [${classNode.name}]: $e.message")
                    }

                    if(configurationName != null) {

                        try {
                            ServiceDescriptorGenerator generator = new ServiceDescriptorGenerator()
                            File targetDirectory = source.configuration.targetDirectory
                            if (targetDirectory != null) {
                                generator.generate(targetDirectory, configurationName, BeanConfiguration.class)
                            }
                        } catch (Throwable e) {
                            AstMessageUtils.error(source, classNode, "Error generating bean configuration descriptor for package-info class [${classNode.name}]: $e.message")
                        }
                    }
                }

                return
            }
        }

        for (ClassNode classNode in classes) {
            if (classNode.isAbstract() || (classNode instanceof InnerClassNode && !Modifier.isStatic(classNode.getModifiers()))) {
                continue
            }
            InjectVisitor injectVisitor = new InjectVisitor(source, classNode)
            injectVisitor.visitClass(classNode)
            beanDefinitionWriters.putAll(injectVisitor.beanDefinitionWriters)
        }

        ServiceDescriptorGenerator generator = new ServiceDescriptorGenerator()
        for (entry in beanDefinitionWriters) {
            BeanDefinitionWriter beanDefWriter = entry.value
            File classesDir = source.configuration.targetDirectory
            String beanDefinitionClassName = null
            try {
                BeanDefinitionClassWriter beanClassWriter = new BeanDefinitionClassWriter()
                beanDefinitionClassName = beanClassWriter.writeBeanDefinitionClass(classesDir, beanDefWriter.beanDefinitionName, stereoTypeFinder.hasStereoType(entry.key, Context.name))
            } catch (Throwable e) {
                AstMessageUtils.error(source, entry.key, "Error generating bean definition class for dependency injection of class [${entry.key.name}]: $e.message")
            }

            if (beanDefinitionClassName != null) {
                boolean abort = false
                try {
                    generator.generate(classesDir, beanDefinitionClassName, BeanDefinitionClass)
                }
                catch (Throwable e) {
                    abort = true
                    AstMessageUtils.error(source, entry.key, "Error generating bean definition class descriptor for dependency injection of class [${entry.key.name}]: $e.message")
                }
                if(!abort) {
                    try {
                        beanDefWriter.visitBeanDefinitionEnd()
                        beanDefWriter.writeTo(classesDir)
                    } catch (Throwable e) {
                        AstMessageUtils.error(source, entry.key, "Error generating bean definition for dependency injection of class [${entry.key.name}]: $e.message")
                    }
                }
            }

        }
    }

    @Override
    void setCompilationUnit(CompilationUnit unit) {
        this.unit = unit
    }

    private static class InjectVisitor extends ClassCodeVisitorSupport {
        final SourceUnit sourceUnit
        final ClassNode concreteClass
        final Map<ClassNode, BeanDefinitionWriter> beanDefinitionWriters = [:]
        BeanDefinitionWriter beanWriter
        AnnotationStereoTypeFinder stereoTypeFinder = new AnnotationStereoTypeFinder()

        InjectVisitor(SourceUnit sourceUnit, ClassNode targetClassNode) {
            this.sourceUnit = sourceUnit
            this.concreteClass = targetClassNode
            if (stereoTypeFinder.hasStereoType(concreteClass, Scope)) {
                defineBeanDefinition(concreteClass)
            }
        }

        @Override
        void visitClass(ClassNode node) {

            ClassNode superClass = node.getSuperClass()
            List<ClassNode> superClasses = []
            while (superClass != null) {
                superClasses.add(superClass)
                superClass = superClass.getSuperClass()
            }
            superClasses = superClasses.reverse()
            for (classNode in superClasses) {
                classNode.visitContents(this)
            }
            super.visitClass(node)

        }

        @Override
        protected void visitConstructorOrMethod(MethodNode methodNode, boolean isConstructor) {
            if (stereoTypeFinder.hasStereoType(methodNode, Inject.name, PostConstruct.name, PreDestroy.name)) {
                defineBeanDefinition(concreteClass)

                if (!isConstructor) {
                    if (!methodNode.isStatic() && !methodNode.isAbstract()) {
                        boolean isParent = methodNode.declaringClass != concreteClass
                        MethodNode overriddenMethod = isParent ? concreteClass.getMethod(methodNode.name, methodNode.parameters) : methodNode
                        boolean overridden = isParent && overriddenMethod.declaringClass != methodNode.declaringClass

                        boolean isPackagePrivate = isPackagePrivate(methodNode, methodNode.modifiers)
                        boolean isPrivate = methodNode.isPrivate()

                        if (isParent && !isPrivate && !isPackagePrivate) {

                            if (overridden) {
                                // bail out if the method has been overridden, since it will have already been handled
                                return
                            }
                        }
                        boolean isPackagePrivateAndPackagesDiffer = overridden && (overriddenMethod.declaringClass.packageName != methodNode.declaringClass.packageName) && isPackagePrivate
                        boolean requiresReflection = isPrivate || isPackagePrivateAndPackagesDiffer
                        boolean overriddenInjected = overridden && stereoTypeFinder.hasStereoType(overriddenMethod, Inject)

                        if (isParent && isPackagePrivate && !isPackagePrivateAndPackagesDiffer && !overriddenInjected) {
                            // bail out if the overridden method is package private and in the same package
                            // and is not annotated with @Inject
                            return
                        }
                        if (!requiresReflection && isInheritedAndNotPublic(methodNode, methodNode.declaringClass, methodNode.modifiers)) {
                            requiresReflection = true
                        }

                        Map<String, Object> paramsToType = [:]
                        Map<String, Object> qualifierTypes = [:]
                        Map<String, List<Object>> genericTypeMap = [:]
                        populateParameterData(methodNode.parameters, paramsToType, qualifierTypes, genericTypeMap)
                        ClassNode declaringClass = methodNode.declaringClass

                        if (stereoTypeFinder.hasStereoType(methodNode, PostConstruct.name)) {
                            beanWriter.visitPostConstructMethod(
                                    resolveTypeReference(declaringClass),
                                    requiresReflection,
                                    resolveTypeReference(methodNode.returnType),
                                    methodNode.name,
                                    paramsToType,
                                    qualifierTypes,
                                    genericTypeMap)
                        } else if (stereoTypeFinder.hasStereoType(methodNode, PreDestroy.name)) {
                            beanWriter.visitPreDestroyMethod(
                                    resolveTypeReference(declaringClass),
                                    requiresReflection,
                                    resolveTypeReference(methodNode.returnType),
                                    methodNode.name,
                                    paramsToType,
                                    qualifierTypes,
                                    genericTypeMap)
                        } else {
                            beanWriter.visitMethodInjectionPoint(
                                    resolveTypeReference(declaringClass),
                                    requiresReflection,
                                    resolveTypeReference(methodNode.returnType),
                                    methodNode.name,
                                    paramsToType,
                                    qualifierTypes,
                                    genericTypeMap)
                        }


                    }
                }
            }

        }

        protected boolean isPackagePrivate(AnnotatedNode annotatedNode, int modifiers) {
            return ((!Modifier.isProtected(modifiers) && !Modifier.isPublic(modifiers) && !Modifier.isPrivate(modifiers)) || !annotatedNode.getAnnotations(makeCached(PackageScope)).isEmpty())
        }


        @Override
        void visitField(FieldNode fieldNode) {
            ClassNode declaringClass = fieldNode.declaringClass
            if (stereoTypeFinder.hasStereoType(fieldNode, Inject) && declaringClass.getProperty(fieldNode.getName()) == null) {
                defineBeanDefinition(concreteClass)
                if (!fieldNode.isStatic()) {
                    AnnotationNode qualifierAnn = stereoTypeFinder.findAnnotationWithStereoType(fieldNode, Qualifier)
                    ClassNode qualifierClassNode = qualifierAnn?.classNode
                    Object qualifierRef = qualifierClassNode?.isResolved() ? qualifierClassNode.typeClass : qualifierClassNode?.name


                    boolean isPrivate = Modifier.isPrivate(fieldNode.getModifiers())
                    boolean requiresReflection = isPrivate || isInheritedAndNotPublic(fieldNode, fieldNode.declaringClass, fieldNode.modifiers)

                    beanWriter.visitFieldInjectionPoint(
                            declaringClass.isResolved() ? declaringClass.typeClass : declaringClass.name, qualifierRef,
                            requiresReflection,
                            fieldNode.type.isResolved() ? fieldNode.type.typeClass : fieldNode.type.name,
                            fieldNode.name
                    )
                }
            }
        }

        @Override
        void visitProperty(PropertyNode propertyNode) {
            FieldNode fieldNode = propertyNode.field
            if (fieldNode != null && !propertyNode.isStatic() && stereoTypeFinder.hasStereoType(fieldNode, Inject)) {
                defineBeanDefinition(concreteClass)
                ClassNode qualfierType = stereoTypeFinder.findAnnotationWithStereoType(fieldNode, Qualifier.class)?.classNode

                ClassNode fieldType = fieldNode.type
                GenericsType[] genericsTypes = fieldType.genericsTypes
                List<Object> genericTypeList = null
                if (genericsTypes != null && genericsTypes.length > 0) {
                    genericTypeList = []
                    for (genericType in genericsTypes) {
                        if (!genericType.isPlaceholder()) {
                            genericTypeList.add(resolveTypeReference(genericType.type))
                        }
                    }
                } else if (fieldType.isArray()) {
                    genericTypeList = []
                    genericTypeList.add(resolveTypeReference(fieldType.componentType))
                }
                ClassNode declaringClass = fieldNode.declaringClass
                beanWriter.visitSetterInjectionPoint(
                        resolveTypeReference(declaringClass),
                        resolveTypeReference(qualfierType),
                        false,
                        resolveTypeReference(fieldType),
                        fieldNode.name,
                        getSetterName(propertyNode.name),
                        genericTypeList
                )
            }
        }

        private Object resolveTypeReference(ClassNode classNode) {
            if (classNode == null) {
                return null
            } else {
                return classNode.isResolved() || ClassHelper.isPrimitiveType(classNode) ? classNode.typeClass : classNode.name
            }
        }

        protected boolean isInheritedAndNotPublic(AnnotatedNode annotatedNode, ClassNode declaringClass, int modifiers) {
            return declaringClass != concreteClass &&
                    declaringClass.packageName != concreteClass.packageName &&
                    ((Modifier.isProtected(modifiers) || !Modifier.isPublic(modifiers)) || !annotatedNode.getAnnotations(makeCached(PackageScope)).isEmpty())
        }

        @Override
        protected SourceUnit getSourceUnit() {
            return sourceUnit
        }

        private void defineBeanDefinition(ClassNode classNode) {
            if (!beanDefinitionWriters.containsKey(classNode) && !classNode.isAbstract()) {
                ClassNode providerGenericType = AstGenericUtils.resolveInterfaceGenericType(classNode, Provider)
                boolean isProvider = providerGenericType != null
                AnnotationStereoTypeFinder annotationStereoTypeFinder = new AnnotationStereoTypeFinder()
                AnnotationNode scopeAnn = annotationStereoTypeFinder.findAnnotationWithStereoType(classNode, Scope.class)
                AnnotationNode singletonAnn = annotationStereoTypeFinder.findAnnotationWithStereoType(classNode, Singleton.class)

                if (isProvider) {
                    beanWriter = new BeanDefinitionWriter(
                            classNode.packageName,
                            classNode.nameWithoutPackage,
                            providerGenericType.name,
                            scopeAnn?.classNode?.name,
                            singletonAnn != null)
                } else {

                    beanWriter = new BeanDefinitionWriter(
                            classNode.packageName,
                            classNode.nameWithoutPackage,
                            scopeAnn?.classNode?.name,
                            singletonAnn != null)
                }
                beanDefinitionWriters.put(classNode, beanWriter)



                List<ConstructorNode> constructors = classNode.getDeclaredConstructors()

                if (constructors.isEmpty()) {
                    beanWriter.visitBeanDefinitionConstructor(Collections.emptyMap(), null, null)

                } else {
                    List<ConstructorNode> publicConstructors = findPublicConstructors(constructors)

                    ConstructorNode constructorNode
                    if (publicConstructors.size() == 1) {
                        constructorNode = publicConstructors[0]
                    } else {
                        constructorNode = publicConstructors.find() { it.getAnnotations(makeCached(Inject)) }
                    }
                    if (constructorNode != null) {
                        Map<String, Object> paramsToType = [:]
                        Map<String, Object> qualifierTypes = [:]
                        Map<String, List<Object>> genericTypeMap = [:]
                        Parameter[] parameters = constructorNode.parameters
                        populateParameterData(parameters, paramsToType, qualifierTypes, genericTypeMap)
                        beanWriter.visitBeanDefinitionConstructor(paramsToType, qualifierTypes, genericTypeMap)
                    } else {
                        addError("Class must have at least one public constructor in order to be a candidate for dependency injection", classNode)
                    }
                }

            } else if (!classNode.isAbstract()) {
                beanWriter = beanDefinitionWriters.get(classNode)
            }
        }

        private void populateParameterData(Parameter[] parameters, Map<String, Object> paramsToType, Map<String, Object> qualifierTypes, Map<String, List<Object>> genericTypeMap) {
            for (param in parameters) {
                ClassNode parameterType = param.type
                String parameterName = param.name
                if (parameterType.isResolved()) {
                    paramsToType.put(parameterName, parameterType.typeClass)
                } else {
                    paramsToType.put(parameterName, parameterType.name)
                }
                AnnotationNode ann = stereoTypeFinder.findAnnotationWithStereoType(param, Qualifier)
                if (ann != null) {
                    if (ann.classNode.isResolved()) {
                        qualifierTypes.put(parameterName, ann.classNode.typeClass)
                    } else {
                        qualifierTypes.put(parameterName, ann.classNode.name)
                    }
                }

                GenericsType[] genericsTypes = parameterType.genericsTypes
                if (genericsTypes != null && genericsTypes.length > 0) {
                    List<Object> genericTypeList = []
                    genericTypeMap.put(parameterName, genericTypeList)
                    for (genericType in genericsTypes) {
                        if (!genericType.isPlaceholder()) {
                            genericTypeList.add(resolveTypeReference(genericType.type))
                        }
                    }
                } else if (parameterType.isArray()) {
                    List<Object> genericTypeList = []
                    genericTypeList.add(resolveTypeReference(parameterType.componentType))
                    genericTypeMap.put(parameterName, genericTypeList)
                }
            }
        }


        private List<ConstructorNode> findPublicConstructors(List<ConstructorNode> constructorNodes) {
            List<ConstructorNode> publicConstructors = []
            for (node in constructorNodes) {
                if (Modifier.isPublic(node.modifiers)) {
                    publicConstructors.add(node)
                }
            }
            return publicConstructors
        }
    }

}
