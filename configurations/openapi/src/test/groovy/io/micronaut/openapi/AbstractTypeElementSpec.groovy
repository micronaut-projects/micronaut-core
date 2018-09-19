/*
 * Copyright 2017-2018 original authors
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
package io.micronaut.openapi

import com.sun.tools.javac.model.JavacElements
import com.sun.tools.javac.util.Context
import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.core.naming.NameUtils
import io.micronaut.inject.BeanConfiguration
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.BeanDefinitionReference
import io.micronaut.inject.annotation.AnnotationMetadataWriter
import io.micronaut.inject.annotation.JavaAnnotationMetadataBuilder
import io.micronaut.inject.writer.BeanConfigurationWriter
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
    AnnotationMetadata buildTypeAnnotationMetadata(String cls) {
        Element element = buildTypeElement(cls)
        JavaAnnotationMetadataBuilder builder = new JavaAnnotationMetadataBuilder(JavacElements.instance(new Context()))
        AnnotationMetadata metadata = element != null ? builder.build(element) : null
        return metadata
    }

    AnnotationMetadata buildMethodAnnotationMetadata(String cls, String methodName) {
        TypeElement element = buildTypeElement(cls)
        Element method = element.getEnclosedElements().find() { it.simpleName.toString() == methodName }
        JavaAnnotationMetadataBuilder builder = new JavaAnnotationMetadataBuilder(JavacElements.instance(new Context()))
        AnnotationMetadata metadata = method != null ? builder.build(method) : null
        return metadata
    }

    AnnotationMetadata buildFieldAnnotationMetadata(String cls, String methodName, String fieldName) {
        TypeElement element = buildTypeElement(cls)
        ExecutableElement method = (ExecutableElement)element.getEnclosedElements().find() { it.simpleName.toString() == methodName }
        VariableElement argument = method.parameters.find() { it.simpleName.toString() == fieldName }
        JavaAnnotationMetadataBuilder builder = new JavaAnnotationMetadataBuilder(JavacElements.instance(new Context()))
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
        return (BeanDefinition)classLoader.loadClass(beanFullName).newInstance()
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
        new AnnotationMetadataWriter(className, toWrite)
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
}
