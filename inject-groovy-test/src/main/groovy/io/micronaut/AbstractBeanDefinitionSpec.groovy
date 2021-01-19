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
package io.micronaut

import groovy.transform.CompileStatic
import io.micronaut.ast.groovy.utils.AstAnnotationUtils
import io.micronaut.ast.groovy.utils.ExtendedParameter
import io.micronaut.ast.groovy.visitor.GroovyClassElement
import io.micronaut.ast.groovy.visitor.GroovyVisitorContext
import io.micronaut.context.ApplicationContext
import io.micronaut.context.DefaultApplicationContext
import io.micronaut.core.beans.BeanIntrospection
import io.micronaut.core.io.scan.ClassPathResourceLoader
import io.micronaut.inject.BeanDefinitionReference
import io.micronaut.inject.ast.ClassElement
import org.codehaus.groovy.ast.ASTNode
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.MethodNode
import org.codehaus.groovy.ast.Parameter
import org.codehaus.groovy.ast.builder.AstBuilder
import io.micronaut.ast.groovy.annotation.GroovyAnnotationMetadataBuilder
import io.micronaut.ast.groovy.utils.InMemoryByteCodeGroovyClassLoader
import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.core.naming.NameUtils
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.annotation.AnnotationMetadataWriter
import org.codehaus.groovy.control.CompilationUnit
import org.codehaus.groovy.control.CompilerConfiguration
import org.codehaus.groovy.control.ErrorCollector
import org.codehaus.groovy.control.SourceUnit
import spock.lang.Specification

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
    ClassElement buildClassElement(String source) {
        def builder = new AstBuilder()
        ASTNode[] nodes = builder.buildFromString(source)
        def lastNode = nodes ? nodes[-1] : null
        ClassNode cn = lastNode instanceof ClassNode ? lastNode : null
        if (cn != null) {
            def cc = new CompilerConfiguration()
            def sourceUnit = new SourceUnit("test", source, cc, new GroovyClassLoader(), new ErrorCollector(cc))
            def compilationUnit = new CompilationUnit()
            def metadata = AstAnnotationUtils.getAnnotationMetadata(sourceUnit, compilationUnit, cn)
            return new GroovyClassElement(
                    new GroovyVisitorContext(sourceUnit, compilationUnit ), cn, metadata
            )
        } else {
            throw new IllegalArgumentException("No class found in passed source code")
        }
    }

    @CompileStatic
    BeanDefinition buildBeanDefinition(String className, String classStr) {
        def beanDefName= '$' + NameUtils.getSimpleName(className) + 'Definition'
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

    InMemoryByteCodeGroovyClassLoader buildClassLoader(String classStr) {
        def classLoader = new InMemoryByteCodeGroovyClassLoader()
        classLoader.parseClass(classStr)
        return classLoader
    }

    AnnotationMetadata buildTypeAnnotationMetadata(String cls, String source) {
        ASTNode[] nodes = new AstBuilder().buildFromString(source)

        ClassNode element = nodes ? nodes.find { it instanceof ClassNode && it.name == cls } : null
        GroovyAnnotationMetadataBuilder builder = new GroovyAnnotationMetadataBuilder(null, null)
        AnnotationMetadata metadata = element != null ? builder.build(element) : null
        return metadata
    }

    AnnotationMetadata buildMethodAnnotationMetadata(String cls, String source, String methodName) {
        ClassNode element = buildClassNode(source, cls)
        MethodNode method = element.getMethods(methodName)[0]
        GroovyAnnotationMetadataBuilder builder = new GroovyAnnotationMetadataBuilder(null, null)
        AnnotationMetadata metadata = method != null ? builder.build(method) : null
        return metadata
    }

    AnnotationMetadata buildFieldAnnotationMetadata(String cls, String source, String methodName, String fieldName) {
        ClassNode element = buildClassNode(source, cls)
        MethodNode method = element.getMethods(methodName)[0]
        Parameter parameter = Arrays.asList(method.getParameters()).find { it.name == fieldName }
        GroovyAnnotationMetadataBuilder builder = new GroovyAnnotationMetadataBuilder(null, null)
        AnnotationMetadata metadata = method != null ? builder.build(new ExtendedParameter(method, parameter)) : null
        return metadata
    }

    ClassNode buildClassNode(String source, String cls) {
        ASTNode[] nodes = new AstBuilder().buildFromString(source)

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
    protected BeanIntrospection buildBeanIntrospection(String className, String cls) {
        def beanDefName= '$' + NameUtils.getSimpleName(className) + '$Introspection'
        def packageName = NameUtils.getPackageName(className)
        String beanFullName = "${packageName}.${beanDefName}"

        ClassLoader classLoader = buildClassLoader(cls)
        return (BeanIntrospection)classLoader.loadClass(beanFullName).newInstance()
    }

    protected ApplicationContext buildContext(String className, String cls) {
        InMemoryByteCodeGroovyClassLoader classLoader = buildClassLoader(cls)

        return new DefaultApplicationContext(
                ClassPathResourceLoader.defaultLoader(classLoader),"test") {
            @Override
            protected List<BeanDefinitionReference> resolveBeanDefinitionReferences() {
                return classLoader.generatedClasses.keySet().findAll {
                    it.endsWith("DefinitionClass")
                }.collect {
                    classLoader.loadClass(it).newInstance()
                }
            }
        }.start()
    }
}
