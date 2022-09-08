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

import com.sun.source.util.JavacTask
import groovy.transform.CompileStatic
import io.micronaut.annotation.processing.*
import io.micronaut.annotation.processing.visitor.JavaElementFactory
import io.micronaut.annotation.processing.visitor.JavaVisitorContext
import io.micronaut.aop.internal.InterceptorRegistryBean
import io.micronaut.context.*
import io.micronaut.context.event.ApplicationEventPublisherFactory
import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.core.annotation.Experimental
import io.micronaut.core.annotation.NonNull
import io.micronaut.core.annotation.Nullable
import io.micronaut.core.beans.BeanIntrospection
import io.micronaut.core.convert.value.MutableConvertibleValuesMap
import io.micronaut.core.graal.GraalReflectionConfigurer
import io.micronaut.core.naming.NameUtils
import io.micronaut.inject.BeanConfiguration
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.BeanDefinitionReference
import io.micronaut.inject.annotation.AbstractAnnotationMetadataBuilder
import io.micronaut.inject.annotation.AnnotationMapper
import io.micronaut.inject.annotation.AnnotationMetadataWriter
import io.micronaut.inject.annotation.AnnotationTransformer
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.ast.GenericPlaceholderElement
import io.micronaut.inject.ast.WildcardElement
import io.micronaut.inject.provider.BeanProviderDefinition
import io.micronaut.inject.provider.JakartaProviderBeanDefinition
import io.micronaut.inject.visitor.TypeElementVisitor
import io.micronaut.inject.writer.BeanConfigurationWriter
import io.micronaut.inject.writer.BeanDefinitionReferenceWriter
import io.micronaut.inject.writer.BeanDefinitionVisitor
import io.micronaut.inject.writer.BeanDefinitionWriter
import org.intellij.lang.annotations.Language
import spock.lang.Specification

import javax.lang.model.element.Element
import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.TypeElement
import javax.lang.model.element.VariableElement
import javax.tools.JavaFileObject
import java.lang.annotation.Annotation
import java.util.stream.Collectors
import java.util.stream.StreamSupport
/**
 * Base class to extend from to allow compilation of Java sources
 * at runtime to allow testing of compile time behavior.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
abstract class AbstractTypeElementSpec extends Specification {

    /**
     * Builds a class element for the given source code.
     * @param cls The source
     * @return The class element
     */
    ClassElement buildClassElement(@Language("java") String cls) {
        TypeElementInfo typeElementInfo = buildTypeElementInfo(cls)
        TypeElement typeElement = typeElementInfo.typeElement
        def lastTask = typeElementInfo.javaParser.lastTask.get()
        def elements = lastTask.elements
        def types = lastTask.types
        def processingEnv = typeElementInfo.javaParser.processingEnv
        def messager = processingEnv.messager
        ModelUtils modelUtils = new ModelUtils(elements, types) {}
        GenericUtils genericUtils = new GenericUtils(elements, types, modelUtils) {}
        AnnotationUtils annotationUtils = new AnnotationUtils(processingEnv, elements, messager, types, modelUtils, genericUtils, processingEnv.filer) {
        }

        JavaVisitorContext visitorContext = new JavaVisitorContext(
                processingEnv,
                messager,
                elements,
                annotationUtils,
                types,
                modelUtils,
                genericUtils,
                processingEnv.filer,
                new MutableConvertibleValuesMap<Object>(),
                TypeElementVisitor.VisitorKind.ISOLATING
        )

        return new JavaElementFactory(visitorContext).newClassElement(typeElement, visitorContext.getElementAnnotationMetadataFactory())
    }

    /**
     * @param cls The class string
     * @return The annotation metadata for the class
     */
    @CompileStatic
    AnnotationMetadata buildTypeAnnotationMetadata(@Language("java") String cls) {
        AbstractAnnotationMetadataBuilder.clearMutated()
        Element element = buildTypeElement(cls)
        JavaAnnotationMetadataBuilder builder = newJavaAnnotationBuilder()
        AnnotationMetadata metadata = element != null ? builder.lookupOrBuildForType(element) : null
        AbstractAnnotationMetadataBuilder.copyToRuntime()
        return metadata
    }

    AnnotationMetadata buildMethodArgumentAnnotationMetadata(@Language("java") String cls, String methodName, String argumentName) {
        AbstractAnnotationMetadataBuilder.clearMutated()
        TypeElement element = buildTypeElement(cls)
        ExecutableElement method = (ExecutableElement)element.getEnclosedElements().find() { it.simpleName.toString() == methodName }
        VariableElement argument = method.parameters.find() { it.simpleName.toString() == argumentName }
        JavaAnnotationMetadataBuilder builder = newJavaAnnotationBuilder()
        AnnotationMetadata metadata = argument != null ? builder.lookupOrBuildForMethod(element, argument) : null
        AbstractAnnotationMetadataBuilder.copyToRuntime()
        return metadata
    }

    /**
    * Build and return a {@link BeanIntrospection} for the given class name and class data.
    *
    * @return the introspection if it is correct
    **/
    protected BeanIntrospection buildBeanIntrospection(String className, @Language("java") String cls) {
        def beanDefName= (className.startsWith('$') ? '' : '$') + NameUtils.getSimpleName(className) + '$Introspection'
        def packageName = NameUtils.getPackageName(className)
        String beanFullName = "${packageName}.${beanDefName}"

        ClassLoader classLoader = buildClassLoader(className, cls)
        return (BeanIntrospection)classLoader.loadClass(beanFullName).newInstance()
    }

    /**
     * Build and return a {@link GraalReflectionConfigurer} for the given class name and class data.
     *
     * @return the GraalReflectionConfigurer if it is correct
     **/
    protected GraalReflectionConfigurer buildReflectionConfigurer(String className, @Language("java") String cls) {
        String beanDefName = (className.startsWith('$') ? '' : '$') + NameUtils.getSimpleName(className) + GraalReflectionConfigurer.CLASS_SUFFIX
        String packageName = NameUtils.getPackageName(className)
        String beanFullName = "${packageName}.${beanDefName}"

        ClassLoader classLoader = buildClassLoader(className, cls)
        return (GraalReflectionConfigurer)classLoader.loadClass(beanFullName).newInstance()
    }

    /**
     * @param annotationExpression the annotation expression
     * @param packages the packages to import
     * @return The metadata
     */
    @CompileStatic
    AnnotationMetadata buildAnnotationMetadata(String annotationExpression, String... packages) {

        List<String> packageList = ["io.micronaut.core.annotation",
                                    "io.micronaut.inject.annotation"]
        packageList.addAll(Arrays.asList(packages))
        packageList = packageList.unique()
        return buildTypeAnnotationMetadata("""
${packageList.collect() { "import ${it}.*;" }.join(System.getProperty('line.separator'))}

${annotationExpression}
class Test {

}
""")
    }

    /**
     * Reads a generated file
     * @param filePath The file path
     * @param className The class name
     * @param code The code
     * @return The reader
     * @throws IOException
     */
    public @Nullable Reader readGenerated(@NonNull String filePath, String className, @Language("java") String code) throws IOException {
        return newJavaParser().readGenerated(filePath, className, code)
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


    /**
     * Gets a bean definition from the context for the given class name
     * @param context The context
     * @param className The class name
     * @return The bean instance
     */
    BeanDefinition<?> getBeanDefinition(ApplicationContext context, String className, Qualifier qualifier = null) {
        context.getBeanDefinition(context.classLoader.loadClass(className), qualifier)
    }

    /**
     * Builds a {@link ApplicationContext} containing only the classes produced by the given source.
     *
     * @param source The source code
     * @return The context. Should be shutdown after use
     */
    ApplicationContext buildContext(@Language("java") String source) {
        return buildContext("test.Source" + System.currentTimeMillis(), source)
    }

    /**
     * Builds a {@link ApplicationContext} containing only the classes produced by the given class.
     *
     * @param className The class name
     * @param cls The class data
     * @return The context. Should be shutdown after use
     */
    ApplicationContext buildContext(String className, @Language("java") String cls, boolean includeAllBeans = false) {
        def files = newJavaParser().generate(className, cls)
        ClassLoader classLoader = new JavaFileObjectClassLoader(files)

        def builder = ApplicationContext.builder()
        builder.classLoader(classLoader)
        builder.environments("test")
        configureContext(builder)
        return new DefaultApplicationContext((ApplicationContextConfiguration) builder) {
            @Override
            protected List<BeanDefinitionReference> resolveBeanDefinitionReferences() {
                def references = StreamSupport.stream(files.spliterator(), false)
                        .filter({ JavaFileObject jfo ->
                            jfo.kind == JavaFileObject.Kind.CLASS && jfo.name.endsWith(BeanDefinitionWriter.CLASS_SUFFIX + BeanDefinitionReferenceWriter.REF_SUFFIX + ".class")
                        })
                        .map({ JavaFileObject jfo ->
                            def name = jfo.toUri().toString().substring("mem:///CLASS_OUTPUT/".length())
                            name = name.replace('/', '.') - '.class'
                            return (BeanDefinitionReference) classLoader.loadClass(name).newInstance()
                        })
                        .collect(Collectors.toList())

                return references + (includeAllBeans ? super.resolveBeanDefinitionReferences() : getBuiltInBeanReferences())
            }
        }.start()
    }

    /**
     * @return Obtains the built in bean references required for the context to function correctly
     */
    List<BeanDefinitionReference<?>> getBuiltInBeanReferences() {
        return [
                new InterceptorRegistryBean(),
                new BeanProviderDefinition(),
                new JakartaProviderBeanDefinition(),
                new ApplicationEventPublisherFactory<>()
        ]
    }

    /**
     * Create and return a new Java parser.
     * @return The java parser to use
     */
    protected JavaParser newJavaParser() {
        def visitors = getLocalTypeElementVisitors()
        if (visitors) {
            return new JavaParser() {
                @Override
                protected TypeElementVisitorProcessor getTypeElementVisitorProcessor() {
                    return new TypeElementVisitorProcessor() {
                        @Override
                        protected Collection<TypeElementVisitor> findTypeElementVisitors() {
                            return visitors
                        }
                    }
                }

                @Override
                protected AggregatingTypeElementVisitorProcessor getAggregatingTypeElementVisitorProcessor() {
                    return new AggregatingTypeElementVisitorProcessor() {
                        @Override
                        protected Collection<TypeElementVisitor> findTypeElementVisitors() {
                            return visitors
                        }
                    }
                }

            }
        } else {
            return new JavaParser()
        }
    }

    /**
     * @param cls   The class string
     * @param methodName The method name
     * @return The annotation metadata for the method
     */
    @CompileStatic
    AnnotationMetadata buildMethodAnnotationMetadata(@Language("java") String cls, String methodName) {
        TypeElement element = buildTypeElement(cls)
        Element method = element.getEnclosedElements().find() { it.simpleName.toString() == methodName }
        JavaAnnotationMetadataBuilder builder = newJavaAnnotationBuilder()
        AnnotationMetadata metadata = method != null ? builder.lookupOrBuildForMethod(element, method) : null
        return metadata
    }

    protected TypeElement buildTypeElement(String cls) {
        List<Element> elements = []

        newJavaParser().parseLines("",
                cls
        ).each { elements.add(it) }

        def element = elements ? elements[0] : null
        return (TypeElement) element
    }

    protected TypeElementInfo buildTypeElementInfo(@Language("java") String cls) {
        List<Element> elements = []


        def parser = newJavaParser()
        parser.parseLines("",
                cls
        ).each { elements.add(it) }

        def element = elements ? elements[0] : null
        return new TypeElementInfo(
                typeElement: element,
                javaParser: parser
        )
    }

    protected BeanDefinition buildBeanDefinition(String className, @Language("java") String cls) {
        def classSimpleName = NameUtils.getSimpleName(className)
        def beanDefName = (classSimpleName.startsWith('$') ? '' : '$') + classSimpleName + BeanDefinitionWriter.CLASS_SUFFIX
        def packageName = NameUtils.getPackageName(className)
        String beanFullName = "${packageName}.${beanDefName}"

        ClassLoader classLoader = buildClassLoader(className, cls)
        try {
            return (BeanDefinition)classLoader.loadClass(beanFullName).newInstance()
        } catch (ClassNotFoundException e) {
            return null
        }
    }

    protected BeanDefinition buildBeanDefinition(String packageName, String className, @Language("java") String cls) {
        def beanDefName= (className.startsWith('$') ? '' : '$') + className + BeanDefinitionWriter.CLASS_SUFFIX
        String beanFullName = "${packageName}.${beanDefName}"

        ClassLoader classLoader = buildClassLoader(className, cls)
        try {
            return (BeanDefinition)classLoader.loadClass(beanFullName).newInstance()
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
    protected BeanDefinition buildInterceptedBeanDefinition(String className, @Language("java") String cls) {
        def classSimpleName = NameUtils.getSimpleName(className)
        def beanDefName = (classSimpleName.startsWith('$') ? '' : '$') + classSimpleName + BeanDefinitionWriter.CLASS_SUFFIX + BeanDefinitionVisitor.PROXY_SUFFIX + BeanDefinitionWriter.CLASS_SUFFIX
        def packageName = NameUtils.getPackageName(className)
        String beanFullName = "${packageName}.${beanDefName}"

        ClassLoader classLoader = buildClassLoader(className, cls)
        return (BeanDefinition)classLoader.loadClass(beanFullName).newInstance()
    }

    /**
     * Retrieve additional annotation mappers to apply
     * @param annotationName The annotation name
     * @return The mappers for the annotation
     */
    protected List<AnnotationMapper<? extends Annotation>> getLocalAnnotationMappers(@NonNull String annotationName) {
        return Collections.emptyList()
    }

    /**
     * Retrieve additional annotation transformers  to apply
     * @param annotationName The annotation name
     * @return The transformers for the annotation
     */
    protected List<AnnotationTransformer<? extends Annotation>> getLocalAnnotationTransformers(@NonNull String annotationName) {
        return Collections.emptyList()
    }

    /**
     * Retrieve additional type element visitors for this test.
     * @return the visitors
     */
    protected Collection<TypeElementVisitor> getLocalTypeElementVisitors() {
        return Collections.emptyList()
    }

    /**
     * Builds the bean definition reference for an AOP proxy bean.
     * @param className The class name
     * @param cls The class source
     * @return The bean definition
     */
    protected BeanDefinitionReference buildInterceptedBeanDefinitionReference(String className, @Language("java") String cls) {
        def classSimpleName = NameUtils.getSimpleName(className)
        def beanDefName = (classSimpleName.startsWith('$') ? '' : '$') + classSimpleName + BeanDefinitionWriter.CLASS_SUFFIX + BeanDefinitionVisitor.PROXY_SUFFIX + BeanDefinitionWriter.CLASS_SUFFIX + BeanDefinitionReferenceWriter.REF_SUFFIX
        def packageName = NameUtils.getPackageName(className)
        String beanFullName = "${packageName}.${beanDefName}"

        ClassLoader classLoader = buildClassLoader(className, cls)
        return (BeanDefinitionReference)classLoader.loadClass(beanFullName).newInstance()
    }

    protected BeanDefinitionReference buildBeanDefinitionReference(String className, @Language("java") String cls) {
        def classSimpleName = NameUtils.getSimpleName(className)
        def beanDefName= (classSimpleName.startsWith('$') ? '' : '$') + classSimpleName + BeanDefinitionWriter.CLASS_SUFFIX + BeanDefinitionReferenceWriter.REF_SUFFIX
        def packageName = NameUtils.getPackageName(className)
        String beanFullName = "${packageName}.${beanDefName}"

        ClassLoader classLoader = buildClassLoader(className, cls)
        return (BeanDefinitionReference)classLoader.loadClass(beanFullName).newInstance()
    }

    protected BeanConfiguration buildBeanConfiguration(String packageName, @Language("java") String cls) {
        ClassLoader classLoader = buildClassLoader("${packageName}.package-info", cls)
        return (BeanConfiguration)classLoader.loadClass(packageName + '.' + BeanConfigurationWriter.CLASS_SUFFIX).newInstance()
    }

    @CompileStatic
    protected ClassLoader buildClassLoader(String className, @Language("java") String cls) {
        AbstractAnnotationMetadataBuilder.clearMutated()
        Iterable<? extends JavaFileObject> files = newJavaParser().generate(className, cls)
        return new JavaFileObjectClassLoader(files)
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

    protected JavaAnnotationMetadataBuilder newJavaAnnotationBuilder() {
        JavaParser parser = newJavaParser()
        JavacTask javacTask = parser.getJavacTask()
        def elements = javacTask.elements
        def types = javacTask.types
        def processingEnv = parser.processingEnv
        def messager = processingEnv.messager
        ModelUtils modelUtils = new ModelUtils(elements, types) {}
        GenericUtils genericUtils = new GenericUtils(elements, types, modelUtils) {}
        AnnotationUtils annotationUtils = new AnnotationUtils(processingEnv, elements, messager, types, modelUtils, genericUtils, parser.filer) {
            @Override
            JavaAnnotationMetadataBuilder newAnnotationBuilder() {
                return super.newAnnotationBuilder()
            }
        }
        JavaAnnotationMetadataBuilder builder = new JavaAnnotationMetadataBuilder(elements, messager, annotationUtils, modelUtils) {
            @Override
            protected List<AnnotationMapper<? extends Annotation>> getAnnotationMappers(@NonNull String annotationName) {
                def loadedMappers = super.getAnnotationMappers(annotationName)
                def localMappers = getLocalAnnotationMappers(annotationName)
                if (localMappers) {
                    def newList = []
                    if (loadedMappers) {
                        newList.addAll(loadedMappers)
                    }
                    newList.addAll(localMappers)
                    return newList
                } else {
                    if (localMappers) {
                        return loadedMappers
                    } else {
                        return Collections.emptyList()
                    }
                }
            }

            @Override
            protected List<AnnotationTransformer<Annotation>> getAnnotationTransformers(@NonNull String annotationName) {
                def loadedTransformers = super.getAnnotationTransformers(annotationName)
                def localTransfomers = getLocalAnnotationTransformers(annotationName)
                if (localTransfomers) {
                    def newList = []
                    if (loadedTransformers) {
                        newList.addAll(loadedTransformers)
                    }
                    newList.addAll(localTransfomers)
                    return newList
                } else {
                    return loadedTransformers
                }
            }
        }
        return builder
    }

    /**
     * Allows configuring the context
     * @param contextBuilder The context builder
     */
    protected void configureContext(ApplicationContextBuilder contextBuilder) {
    }

    /**
     * Create a rough source signature of the given ClassElement, using {@link ClassElement#getBoundGenericTypes()}.
     * Can be used to test that {@link ClassElement#getBoundGenericTypes()} returns the right types in the right
     * context.
     *
     * @param classElement The class element to reconstruct
     * @param typeVarsAsDeclarations Whether type variables should be represented as declarations
     * @return a String representing the type signature.
     */
    @Experimental
    protected static String reconstructTypeSignature(ClassElement classElement, boolean typeVarsAsDeclarations = false) {
        if (classElement.isArray()) {
            return reconstructTypeSignature(classElement.fromArray()) + "[]"
        } else if (classElement.isGenericPlaceholder()) {
            def freeVar = (GenericPlaceholderElement) classElement
            def name = freeVar.variableName
            if (typeVarsAsDeclarations) {
                def bounds = freeVar.bounds
                if (reconstructTypeSignature(bounds[0]) != 'Object') {
                    name += bounds.stream().map(AbstractTypeElementSpec::reconstructTypeSignature).collect(Collectors.joining(" & ", " extends ", ""))
                }
            }
            return name
        } else if (classElement.isWildcard()) {
            def we = (WildcardElement) classElement
            if (!we.lowerBounds.isEmpty()) {
                return we.lowerBounds.stream().map(AbstractTypeElementSpec::reconstructTypeSignature).collect(Collectors.joining(" | ", "? super ", ""))
            } else if (we.upperBounds.size() == 1 && reconstructTypeSignature(we.upperBounds.get(0)) == "Object") {
                return "?"
            } else {
                return we.upperBounds.stream().map(AbstractTypeElementSpec::reconstructTypeSignature).collect(Collectors.joining(" & ", "? extends ", ""))
            }
        } else {
            def boundTypeArguments = classElement.getBoundGenericTypes()
            if (boundTypeArguments.isEmpty()) {
                return classElement.getSimpleName()
            } else {
                return classElement.getSimpleName() +
                        boundTypeArguments.stream().map(AbstractTypeElementSpec::reconstructTypeSignature).collect(Collectors.joining(", ", "<", ">"))
            }
        }
    }

    static class TypeElementInfo {
        TypeElement typeElement
        JavaParser javaParser
    }
}
