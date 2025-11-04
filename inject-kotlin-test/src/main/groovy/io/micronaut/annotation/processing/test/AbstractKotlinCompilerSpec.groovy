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
package io.micronaut.annotation.processing.test

import io.micronaut.context.ApplicationContext
import io.micronaut.context.Qualifier
import io.micronaut.core.annotation.Experimental
import io.micronaut.core.annotation.NonNull
import io.micronaut.core.beans.BeanIntrospection
import io.micronaut.core.naming.NameUtils
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.ast.GenericPlaceholderElement
import io.micronaut.inject.ast.WildcardElement
import org.intellij.lang.annotations.Language
import spock.lang.Specification

import java.util.function.Consumer
import java.util.function.Function
import java.util.stream.Collectors

class AbstractKotlinCompilerSpec extends Specification {
    protected ClassLoader buildClassLoader(String className, @Language("kotlin") String cls) {
        KotlinCompiler.buildClassLoader(className, cls)
    }

    /**
     * Build and return a {@link io.micronaut.core.beans.BeanIntrospection} for the given class name and class data.
     *
     * @return the introspection if it is correct
     * */
    protected BeanIntrospection buildBeanIntrospection(String className, @Language("kotlin") String cls) {
        def beanDefName = '$' + NameUtils.getSimpleName(className) + '$Introspection'
        def packageName = NameUtils.getPackageName(className)
        String beanFullName = "${packageName}.${beanDefName}"

        ClassLoader classLoader = buildClassLoader(className, cls)
        return (BeanIntrospection) classLoader.loadClass(beanFullName).newInstance()
    }

    /**
     * Build and return a {@link io.micronaut.core.beans.BeanIntrospection} for the given class name and class data.
     *
     * @return the introspection if it is correct
     */
    protected ApplicationContext buildContext(String className, @Language("kotlin") String cls, boolean includeAllBeans = false) {
        KotlinCompiler.buildContext(cls, includeAllBeans)
    }

    /**
     * Build and return a {@link io.micronaut.core.beans.BeanIntrospection} for the given class name and class data.
     *
     * @return the introspection if it is correct
     */
    protected ApplicationContext buildContext(@Language("kotlin") String cls, boolean includeAllBeans = false) {
        KotlinCompiler.buildContext(cls, includeAllBeans)
    }

    /**
     * Builds a class element for the given source code.
     * @param cls The source
     * @return The class element
     */
    ClassElement buildClassElement(String className, @Language("kotlin") String cls) {
        List<ClassElement> elements = []
        KotlinCompiler.compile(className, cls,  {
            elements.add(it)
        })
        return elements.find { it.name == className }
    }

    /**
     * Builds a class element for the given source code.
     * @param cls The source
     * @return The class element
     */
    ClassElement buildClassElementJava(String className, @Language("java") String cls) {
        List<ClassElement> elements = []
        KotlinCompiler.compileJava(className, cls,  {
            elements.add(it)
        })
        return elements.find { it.name == className }
    }

    /**
     * Builds a class element for the given source code.
     * @param cls The source
     * @return The class element
     */
    boolean buildClassElement(String className, @Language("kotlin") String cls, @NonNull Consumer<ClassElement> processor) {
        boolean invoked = false
        KotlinCompiler.compile(className, cls) {
            if (it.name == className) {
                processor.accept(it)
                invoked = true
            }
        }
        return true
    }

    /**
     * Builds a class element for the given source code.
     * @param cls The source
     * @return The class element
     */
    ClassElement buildClassElementTransformed(String className, @Language("kotlin") String cls, @NonNull Function<ClassElement, ClassElement> processor) {
        List<ClassElement> elements = []
        KotlinCompiler.compile(className, cls,  {
            if (it.name == className) {
                elements.add(processor.apply(it))
            }
        })
        return elements.first()
    }

    /**
     * Builds a class element for the given source code.
     * @param cls The source
     * @return The result
     */
    <T> T buildClassElementTransformedJava(String className, @Language("java") String cls, @NonNull Function<ClassElement, T> processor) {
        List<T> elements = []
        KotlinCompiler.compileJava(className, cls,  {
            if (it.name == className) {
                elements.add(processor.apply(it))
            }
        })
        return elements.first()
    }

    /**
     * Builds a class element for the given source code.
     * @param cls The source
     * @return The class element
     */
    <V> V buildClassElementMapped(String className, @Language("kotlin") String cls, @NonNull Function<ClassElement, V> processor) {
        List<V> elements = []
        KotlinCompiler.compile(className, cls,  {
            if (it.name == className) {
                elements.add(processor.apply(it))
            }
        })
        return elements.first()
    }

    Object getBean(ApplicationContext context, String className, Qualifier qualifier = null) {
        context.getBean(context.classLoader.loadClass(className), qualifier)
    }

    /**
     * Gets a bean definition from the context for the given class name
     * @param context The context
     * @param className The class name
     * @return The bean instance
     */
    BeanDefinition<?> getBeanDefinition(ApplicationContext context, String className, Qualifier qualifier = null) {
        context.getBeanDefinition(context.classLoader.loadClass(className), qualifier)
    }

    protected BeanDefinition buildBeanDefinition(String className, @Language("kotlin") String cls) {
        KotlinCompiler.buildBeanDefinition(className, cls)
    }

    /**
     * Create a rough source signature of the given ClassElement, using {@link io.micronaut.inject.ast.ClassElement#getBoundGenericTypes()}.
     * Can be used to test that {@link io.micronaut.inject.ast.ClassElement#getBoundGenericTypes()} returns the right types in the right
     * context.
     *
     * @param classElement The class element to reconstruct
     * @param typeVarsAsDeclarations Whether type variables should be represented as declarations
     * @return a String representing the type signature.
     */
    @Experimental
    protected static String reconstructTypeSignature(ClassElement classElement, boolean typeVarsAsDeclarations = false) {
        if (classElement.isArray()) {
            return "Array<" + reconstructTypeSignature(classElement.fromArray()) + ">"
        } else if (classElement.isGenericPlaceholder()) {
            def genericPlaceholderElement = classElement as GenericPlaceholderElement
            def name = genericPlaceholderElement.variableName
            if (typeVarsAsDeclarations) {
                def bounds = genericPlaceholderElement.bounds
                if (reconstructTypeSignature(bounds[0]) != 'Object') {
                    name += bounds.stream().map(AbstractKotlinCompilerSpec::reconstructTypeSignature).collect(Collectors.joining(" & ", " : ", ""))
                }
            } else if (genericPlaceholderElement.resolved) {
                return reconstructTypeSignature(genericPlaceholderElement.resolved.get())
            }
            return name
        } else if (classElement.isWildcard()) {
            def we = classElement as WildcardElement
            if (we.isRawType()) {
                return "*"
            }
            if (!we.lowerBounds.isEmpty()) {
                return we.lowerBounds.stream().map(AbstractKotlinCompilerSpec::reconstructTypeSignature).collect(Collectors.joining(" | ", "in ", ""))
            } else {
                return we.upperBounds.stream().map(AbstractKotlinCompilerSpec::reconstructTypeSignature).collect(Collectors.joining(" & ", "out ", ""))
            }
        } else {
            def typeArguments = classElement.getTypeArguments().values()
            def simpleName = getSimpleName(classElement)
            if (typeArguments.isEmpty()) {
                return simpleName
            } else {
                return simpleName + typeArguments.stream().map(AbstractKotlinCompilerSpec::reconstructTypeSignature).collect(Collectors.joining(", ", "<", ">"))
            }
        }
    }

    private static String getSimpleName(ClassElement classElement) {
        if (classElement.getName() == Object.class.name) {
            return "Any"
        }
        classElement.getSimpleName()
    }
}
