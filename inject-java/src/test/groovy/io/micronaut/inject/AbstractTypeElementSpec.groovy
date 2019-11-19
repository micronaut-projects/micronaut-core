/*
 * Copyright 2017-2019 original authors
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
package io.micronaut.inject

import com.sun.tools.javac.model.JavacElements
import com.sun.tools.javac.processing.JavacProcessingEnvironment
import com.sun.tools.javac.util.Context
import groovy.transform.CompileStatic
import io.micronaut.annotation.processing.AnnotationUtils
import io.micronaut.annotation.processing.GenericUtils
import io.micronaut.annotation.processing.ModelUtils
import io.micronaut.context.ApplicationContext
import io.micronaut.context.DefaultApplicationContext
import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.core.beans.BeanIntrospection
import io.micronaut.core.io.scan.ClassPathResourceLoader
import io.micronaut.core.naming.NameUtils
import io.micronaut.inject.annotation.AnnotationMetadataWriter
import io.micronaut.annotation.processing.JavaAnnotationMetadataBuilder
import io.micronaut.inject.writer.BeanConfigurationWriter
import io.micronaut.support.Parser
import spock.lang.Specification

import javax.lang.model.element.Element
import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.TypeElement
import javax.lang.model.element.VariableElement
import javax.tools.JavaFileObject

/**
 * @author Graeme Rocher
 * @since 1.0
 */
abstract class AbstractTypeElementSpec extends Specification {
    @CompileStatic
    AnnotationMetadata buildTypeAnnotationMetadata(String cls) {
        Element element = buildTypeElement(cls)
        JavaAnnotationMetadataBuilder builder = newJavaAnnotationBuilder()
        AnnotationMetadata metadata = element != null ? builder.build(element) : null
        return metadata
    }



    AnnotationMetadata buildMethodAnnotationMetadata(String cls, String methodName) {
        TypeElement element = buildTypeElement(cls)
        Element method = element.getEnclosedElements().find() { it.simpleName.toString() == methodName }
        JavaAnnotationMetadataBuilder builder = newJavaAnnotationBuilder()
        AnnotationMetadata metadata = method != null ? builder.build(method) : null
        return metadata
    }

    AnnotationMetadata buildDeclaredMethodAnnotationMetadata(String cls, String methodName) {
        TypeElement element = buildTypeElement(cls)
        Element method = element.getEnclosedElements().find() { it.simpleName.toString() == methodName }
        JavaAnnotationMetadataBuilder builder = newJavaAnnotationBuilder()
        AnnotationMetadata metadata = method != null ? builder.buildDeclared(method) : null
        return metadata
    }

    AnnotationMetadata buildMethodArgumentAnnotationMetadata(String cls, String methodName, String argumentName) {
        TypeElement element = buildTypeElement(cls)
        ExecutableElement method = (ExecutableElement)element.getEnclosedElements().find() { it.simpleName.toString() == methodName }
        VariableElement argument = method.parameters.find() { it.simpleName.toString() == argumentName }
        JavaAnnotationMetadataBuilder builder = newJavaAnnotationBuilder()
        AnnotationMetadata metadata = argument != null ? builder.build(argument) : null
        return metadata
    }

    protected TypeElement buildTypeElement(String cls) {
        List<Element> elements = Parser.parseLines("",
                cls
        ).toList()

        def element = elements ? elements[0] : null
        assert element instanceof TypeElement
        return element
    }

    protected BeanDefinition buildBeanDefinition(String className, String cls) {
        def beanDefName= '$' + NameUtils.getSimpleName(className) + 'Definition'
        def packageName = NameUtils.getPackageName(className)
        String beanFullName = "${packageName}.${beanDefName}"

        ClassLoader classLoader = buildClassLoader(className, cls)
        try {
            return (BeanDefinition) classLoader.loadClass(beanFullName).newInstance()
        } catch (ClassNotFoundException e) {
            return null
        }
    }

    protected ApplicationContext buildContext(String className, String cls) {
        def files = Parser.generate(className, cls)
        ClassLoader classLoader = new ClassLoader() {
            @Override
            protected Class<?> findClass(String name) throws ClassNotFoundException {
                String fileName = name.replace('.', '/') + '.class'
                JavaFileObject generated = files.find { it.name.endsWith(fileName) }
                if (generated != null) {
                    def bytes = generated.openInputStream().bytes
                    return defineClass(name, bytes, 0, bytes.length)
                }
                return super.findClass(name)
            }
        }

        return new DefaultApplicationContext(ClassPathResourceLoader.defaultLoader(classLoader),"test") {
            @Override
            protected List<BeanDefinitionReference> resolveBeanDefinitionReferences() {
                files.findAll { JavaFileObject jfo ->
                    jfo.kind == JavaFileObject.Kind.CLASS && jfo.name.endsWith("DefinitionClass.class")
                }.collect { JavaFileObject jfo ->
                    def name = jfo.toUri().toString().substring("mem:///CLASS_OUTPUT/".length())
                    name = name.replace('/', '.') - '.class'
                    return classLoader.loadClass(name).newInstance()
                } as List<BeanDefinitionReference>
            }
        }.start()
    }

    protected BeanDefinitionReference buildBeanDefinitionReference(String className, String cls) {
        def beanDefName= '$' + NameUtils.getSimpleName(className) + 'DefinitionClass'
        def packageName = NameUtils.getPackageName(className)
        String beanFullName = "${packageName}.${beanDefName}"

        ClassLoader classLoader = buildClassLoader(className, cls)
        return (BeanDefinitionReference)classLoader.loadClass(beanFullName).newInstance()
    }
    protected BeanConfiguration buildBeanConfiguration(String packageName, String cls) {
        ClassLoader classLoader = buildClassLoader("${packageName}.package-info", cls)
        return (BeanConfiguration)classLoader.loadClass(packageName + '.' + BeanConfigurationWriter.CLASS_SUFFIX).newInstance()
    }

    protected ClassLoader buildClassLoader(String className, String cls) {
        def files = Parser.generate(className, cls)
        ClassLoader classLoader = new ClassLoader() {
            @Override
            protected Class<?> findClass(String name) throws ClassNotFoundException {
                String fileName = name.replace('.', '/') + '.class'
                JavaFileObject generated = files.find { it.name.endsWith(fileName) }
                if (generated != null) {
                    def bytes = generated.openInputStream().bytes
                    return defineClass(name, bytes, 0, bytes.length)
                }
                return super.findClass(name)
            }
        }
        classLoader
    }

    protected AnnotationMetadata writeAndLoadMetadata(String className, AnnotationMetadata toWrite) {
        def stream = new ByteArrayOutputStream()
        new AnnotationMetadataWriter(className, toWrite, true)
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

        ClassLoader classLoader = buildClassLoader(className, cls)
        return (BeanIntrospection)classLoader.loadClass(beanFullName).newInstance()
    }

    @CompileStatic
    private static JavaAnnotationMetadataBuilder newJavaAnnotationBuilder() {
        def env = JavacProcessingEnvironment.instance(new Context())
        def elements = JavacElements.instance(new Context())
        ModelUtils modelUtils = new ModelUtils(elements, env.typeUtils) {}
        GenericUtils genericUtils = new GenericUtils(elements, env.typeUtils, modelUtils) {}
        AnnotationUtils annotationUtils = new AnnotationUtils(env, elements, env.messager, env.typeUtils, modelUtils, genericUtils, env.filer) {
        }
        JavaAnnotationMetadataBuilder builder = new JavaAnnotationMetadataBuilder(elements, env.messager, annotationUtils, modelUtils)
        return builder
    }
}
