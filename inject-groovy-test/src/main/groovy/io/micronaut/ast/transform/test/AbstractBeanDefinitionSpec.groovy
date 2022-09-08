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
package io.micronaut.ast.transform.test

import groovy.transform.CompileStatic
import io.micronaut.aop.internal.InterceptorRegistryBean
import io.micronaut.ast.groovy.annotation.GroovyAnnotationMetadataBuilder
import io.micronaut.ast.groovy.utils.InMemoryByteCodeGroovyClassLoader
import io.micronaut.ast.groovy.visitor.GroovyElementFactory
import io.micronaut.ast.groovy.visitor.GroovyVisitorContext
import io.micronaut.context.ApplicationContext
import io.micronaut.context.DefaultApplicationContext
import io.micronaut.context.Qualifier
import io.micronaut.context.event.ApplicationEventPublisherFactory
import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.core.beans.BeanIntrospection
import io.micronaut.core.io.scan.ClassPathResourceLoader
import io.micronaut.core.naming.NameUtils
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.BeanDefinitionReference
import io.micronaut.inject.annotation.AbstractAnnotationMetadataBuilder
import io.micronaut.inject.annotation.AnnotationMetadataWriter
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.provider.BeanProviderDefinition
import io.micronaut.inject.writer.BeanDefinitionReferenceWriter
import io.micronaut.inject.writer.BeanDefinitionVisitor
import io.micronaut.inject.writer.BeanDefinitionWriter
import org.codehaus.groovy.ast.ASTNode
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.MethodNode
import org.codehaus.groovy.ast.Parameter
import org.codehaus.groovy.control.CompilationUnit
import org.codehaus.groovy.control.CompilerConfiguration
import org.codehaus.groovy.control.ErrorCollector
import org.codehaus.groovy.control.SourceUnit
import org.intellij.lang.annotations.Language
import spock.lang.Specification

import java.util.stream.Collectors
/**
 * @author graemerocher
 * @since 1.0
 */
abstract class AbstractBeanDefinitionSpec extends Specification {

    /**
     * Builds a class element from the given source.
     * @param source The source
     * @return The class element
     */
    ClassElement buildClassElement(@Language("groovy") String source) {
        def nodes = new MicronautAstBuilder().compile(source)
        def lastNode = nodes ? nodes[-1] : null
        ClassNode cn = lastNode instanceof ClassNode ? lastNode : null
        if (cn != null) {
            def cc = new CompilerConfiguration()
            def sourceUnit = new SourceUnit("test", source, cc, new GroovyClassLoader(), new ErrorCollector(cc))
            def compilationUnit = new CompilationUnit()
            def visitorContext = new GroovyVisitorContext(sourceUnit, compilationUnit)
            def elementFactory = new GroovyElementFactory(visitorContext)
            return elementFactory.newClassElement(cn, visitorContext.getElementAnnotationMetadataFactory())
        } else {
            throw new IllegalArgumentException("No class found in passed source code")
        }
    }

    ClassElement buildClassElement(String className, @Language("groovy") String source) {
        ASTNode[] nodes = new MicronautAstBuilder().compile(source)
        for (ASTNode node: nodes) {
            if (node instanceof ClassNode) {
                if (node.getName() == className) {
                    def cc = new CompilerConfiguration()
                    def sourceUnit = new SourceUnit("test", source, cc, new GroovyClassLoader(), new ErrorCollector(cc))
                    def compilationUnit = new CompilationUnit()
                    def visitorContext = new GroovyVisitorContext(sourceUnit, compilationUnit)
                    def elementFactory = new GroovyElementFactory(visitorContext)
                    return elementFactory.newClassElement(node, visitorContext.getElementAnnotationMetadataFactory())
                }
            }
        }
        throw new IllegalArgumentException("No class found in passed source code")
    }

    @CompileStatic
    BeanDefinition buildBeanDefinition(String className, @Language("groovy") String classStr) {
        def classSimpleName = NameUtils.getSimpleName(className)
        def beanDefName= (classSimpleName.startsWith('$') ? '' : '$') + classSimpleName + BeanDefinitionWriter.CLASS_SUFFIX
        def packageName = NameUtils.getPackageName(className)
        String beanFullName = "${packageName}.${beanDefName}"

        def classLoader = new InMemoryByteCodeGroovyClassLoader()
        classLoader.parseClass(classStr)
        try {
            return (BeanDefinition) classLoader.loadClass(beanFullName).newInstance()
        } catch (ClassNotFoundException e) {
            return null
        }
    }

    @CompileStatic
    BeanDefinitionReference buildBeanDefinitionReference(String className, @Language("groovy") String classStr) {
        def classSimpleName = NameUtils.getSimpleName(className)
        def beanDefName= (classSimpleName.startsWith('$') ? '' : '$') + classSimpleName + BeanDefinitionWriter.CLASS_SUFFIX + BeanDefinitionReferenceWriter.REF_SUFFIX
        def packageName = NameUtils.getPackageName(className)
        String beanFullName = "${packageName}.${beanDefName}"

        def classLoader = new InMemoryByteCodeGroovyClassLoader()
        classLoader.parseClass(classStr)
        try {
            return (BeanDefinitionReference) classLoader.loadClass(beanFullName).newInstance()
        } catch (ClassNotFoundException e) {
            return null
        }
    }

    @CompileStatic
    BeanDefinition buildBeanDefinition(String packageName, String className, @Language("groovy") String classStr) {
        def beanDefName= (className.startsWith('$') ? '' : '$') + className + BeanDefinitionWriter.CLASS_SUFFIX
        String beanFullName = "${packageName}.${beanDefName}"

        def classLoader = new InMemoryByteCodeGroovyClassLoader()
        classLoader.parseClass(classStr)
        try {
            return (BeanDefinition) classLoader.loadClass(beanFullName).newInstance()
        } catch (ClassNotFoundException e) {
            return null
        }
    }

    /**
     * Builds the bean definition for an AOP proxy bean.
     * @param className The class name
     * @param cls The class source
     * @return The bean definition
     */
    protected BeanDefinition buildInterceptedBeanDefinition(String className, @Language("groovy") String cls) {
        def classSimpleName = NameUtils.getSimpleName(className)
        def beanDefName= (classSimpleName.startsWith('$') ? '' : '$') + classSimpleName + BeanDefinitionWriter.CLASS_SUFFIX + BeanDefinitionVisitor.PROXY_SUFFIX + BeanDefinitionWriter.CLASS_SUFFIX
        def packageName = NameUtils.getPackageName(className)
        String beanFullName = "${packageName}.${beanDefName}"

        ClassLoader classLoader = buildClassLoader(cls)
        return (BeanDefinition)classLoader.loadClass(beanFullName).newInstance()
    }

    /**
     * Builds the bean definition reference for an AOP proxy bean.
     * @param className The class name
     * @param cls The class source
     * @return The bean definition
     */
    protected BeanDefinitionReference buildInterceptedBeanDefinitionReference(String className, @Language("groovy") String cls) {
        def classSimpleName = NameUtils.getSimpleName(className)
        def beanDefName= (classSimpleName.startsWith('$') ? '' : '$') + classSimpleName + BeanDefinitionWriter.CLASS_SUFFIX + BeanDefinitionVisitor.PROXY_SUFFIX + BeanDefinitionWriter.CLASS_SUFFIX + BeanDefinitionReferenceWriter.REF_SUFFIX
        def packageName = NameUtils.getPackageName(className)
        String beanFullName = "${packageName}.${beanDefName}"

        ClassLoader classLoader = buildClassLoader(cls)
        return (BeanDefinitionReference)classLoader.loadClass(beanFullName).newInstance()
    }

    InMemoryByteCodeGroovyClassLoader buildClassLoader(@Language("groovy") String classStr) {
        def classLoader = new InMemoryByteCodeGroovyClassLoader(getClass().getClassLoader())
        classLoader.parseClass(classStr)
        return classLoader
    }

    AnnotationMetadata buildTypeAnnotationMetadata(String cls, @Language("groovy") String source) {
        ASTNode[] nodes = new MicronautAstBuilder().compile(source)

        ClassNode element = nodes ? nodes.find { it instanceof ClassNode && it.name == cls } : null
        def sourceUnit = Mock(SourceUnit)
        sourceUnit.getErrorCollector() >> new ErrorCollector(new CompilerConfiguration())
        GroovyAnnotationMetadataBuilder builder = new GroovyAnnotationMetadataBuilder(sourceUnit, null)
        AnnotationMetadata metadata = element != null ? builder.lookupOrBuildForType(element) : null
        AbstractAnnotationMetadataBuilder.copyToRuntime()
        return metadata
    }

    AnnotationMetadata buildMethodAnnotationMetadata(String cls, @Language("groovy") String source, String methodName) {
        ClassNode element = buildClassNode(source, cls)
        MethodNode method = element.getMethods(methodName)[0]
        GroovyAnnotationMetadataBuilder builder = new GroovyAnnotationMetadataBuilder(Stub(SourceUnit) {
            getErrorCollector() >> null
        }, null)
        AnnotationMetadata metadata = method != null ? builder.lookupOrBuildForMethod(element,  method) : null
        AbstractAnnotationMetadataBuilder.copyToRuntime()
        return metadata
    }

    AnnotationMetadata buildParameterAnnotationMetadata(String cls, @Language("groovy") String source, String methodName, String fieldName) {
        ClassNode element = buildClassNode(source, cls)
        MethodNode method = element.getMethods(methodName)[0]
        Parameter parameter = Arrays.asList(method.getParameters()).find { it.name == fieldName }
        GroovyAnnotationMetadataBuilder builder = new GroovyAnnotationMetadataBuilder(null, null)
        AnnotationMetadata metadata = method != null ? builder.lookupOrBuildForParameter(element, method, parameter) : null
        AbstractAnnotationMetadataBuilder.copyToRuntime()
        return metadata
    }

    ClassNode buildClassNode(String source, @Language("groovy") String cls) {
        ASTNode[] nodes = new MicronautAstBuilder().compile(source)

        ClassNode element = nodes ? nodes.find { it instanceof ClassNode && it.name == cls } : null
        return element
    }


    protected AnnotationMetadata writeAndLoadMetadata(String className, AnnotationMetadata toWrite) {
        def stream = new ByteArrayOutputStream()
        new AnnotationMetadataWriter(className, null, toWrite, true)
                .writeTo(stream)
        className = className + AnnotationMetadata.CLASS_NAME_SUFFIX
        ClassLoader classLoader = new ClassLoader() {
            @Override
            protected Class<?> findClass(String name) throws ClassNotFoundException {
                if (name == className) {
                    def bytes = stream.toByteArray()
                    return defineClass(name, bytes, 0, bytes.length)
                }
                return super.findClass(name)
            }
        }

        AnnotationMetadata metadata = (AnnotationMetadata) classLoader.loadClass(className).newInstance()
        return metadata
    }

    /**
     * Build and return a {@link io.micronaut.core.beans.BeanIntrospection} for the given class name and class data.
     *
     * @return the introspection if it is correct
     **/
    protected BeanIntrospection buildBeanIntrospection(String className, @Language("groovy") String cls) {
        def beanDefName= '$' + NameUtils.getSimpleName(className) + '$Introspection'
        def packageName = NameUtils.getPackageName(className)
        String beanFullName = "${packageName}.${beanDefName}"

        ClassLoader classLoader = buildClassLoader(cls)
        return (BeanIntrospection)classLoader.loadClass(beanFullName).newInstance()
    }

    /**
     * Gets a bean from the context for the given class name
     * @param context The context
     * @param className The class name
     * @return The bean instance
     */
    Object getBean(ApplicationContext context, String className, Qualifier qualifier = null) {
        context.getBean(context.classLoader.loadClass(className), qualifier)
    }

    protected ApplicationContext buildContext(@Language("groovy") String cls, boolean includeAllBeans = false) {
        InMemoryByteCodeGroovyClassLoader classLoader = buildClassLoader(cls)

        return new DefaultApplicationContext(
                ClassPathResourceLoader.defaultLoader(classLoader),"test") {
            @Override
            protected List<BeanDefinitionReference> resolveBeanDefinitionReferences() {
                def references =  classLoader.generatedClasses.keySet()
                    .stream()
                    .filter({ name -> name.endsWith(BeanDefinitionWriter.CLASS_SUFFIX + BeanDefinitionReferenceWriter.REF_SUFFIX) })
                    .map({ name -> (BeanDefinitionReference) classLoader.loadClass(name).newInstance() })
                    .collect(Collectors.toList())
                return references + (includeAllBeans ? super.resolveBeanDefinitionReferences() : [
                        new InterceptorRegistryBean(),
                        new BeanProviderDefinition(),
                        new ApplicationEventPublisherFactory<>()
                ])
            }
        }.start()
    }
}
