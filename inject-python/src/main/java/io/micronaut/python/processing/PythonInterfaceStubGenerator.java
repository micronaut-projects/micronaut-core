/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.python.processing;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.ReflectiveAccess;
import io.micronaut.core.annotation.Vetoed;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.Element;
import io.micronaut.inject.ast.ElementQuery;
import io.micronaut.inject.ast.GenericPlaceholderElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.ParameterElement;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.python.processing.element.AbstractPythonClassElement;
import io.micronaut.python.processing.element.PythonClassElement;
import io.micronaut.python.processing.util.PythonAnnotationTypes;
import io.micronaut.sourcegen.model.AbstractElementBuilder;
import io.micronaut.sourcegen.model.AnnotationDef;
import io.micronaut.sourcegen.model.ExpressionDef;
import io.micronaut.sourcegen.model.InterfaceDef;
import io.micronaut.sourcegen.model.MethodDef;
import io.micronaut.sourcegen.model.ParameterDef;
import io.micronaut.sourcegen.model.TypeDef;

import javax.lang.model.element.Modifier;
import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static io.micronaut.python.processing.PythonStubGenerator.POLYGLOT_VALUE;
import static io.micronaut.python.processing.PythonStubGenerator.PYTHON_CONTEXT_RUNTIME;
import static io.micronaut.python.processing.PythonStubGenerator.TYPE_ANNOTATIONS_TO_SKIP_IN_SOURCE;
import static io.micronaut.python.processing.PythonStubGenerator.addMethodTypeVariables;
import static io.micronaut.python.processing.PythonStubGenerator.bridgeMethodKey;
import static io.micronaut.python.processing.PythonStubGenerator.methodReturnType;
import static io.micronaut.python.processing.PythonStubGenerator.parameterizedTypeDef;
import static io.micronaut.python.processing.PythonStubGenerator.pythonClassAnnotation;
import static io.micronaut.python.processing.PythonStubGenerator.pythonClassReferenceExpression;
import static io.micronaut.python.processing.PythonStubGenerator.returnConvertedValue;
import static io.micronaut.python.processing.PythonStubGenerator.sourceMethodReturnType;
import static io.micronaut.python.processing.PythonStubGenerator.sourceSignatureType;

/**
 * Generates the Java interface for a Python class that {@link PythonClassElement#isInterface() compiles to
 * an interface}: a plain abstract class or {@code Protocol}, or an {@link PythonClassElement#isIntroductionInterface()
 * introduction interface} such as a declarative client or an AI service.
 *
 * <p>The runtime annotations of the Python class, its methods and their parameters are copied onto the
 * generated declarations so that frameworks which build the implementation reflectively from the interface
 * ({@link java.lang.reflect.Proxy}, {@link java.lang.reflect.Method#getAnnotation(Class)}) see them.
 * Micronaut annotations are served by the annotation metadata of the Python element and stay off the source,
 * where the Java annotation processors would otherwise process the interface a second time, unless their
 * annotation type declares with {@link ReflectiveAccess} that it is read reflectively. The annotations of
 * other libraries are copied only for the interfaces the {@link PythonReflectionGate} allows. The interface is
 * {@link Vetoed} so that the bean definition processor leaves the copied annotations alone: the introduction
 * proxy is generated from the Python element.</p>
 *
 * @since 5.2.3
 */
@Internal
final class PythonInterfaceStubGenerator {

    private static final String MICRONAUT_PACKAGE_PREFIX = "io.micronaut.";
    private static final String JAVA_LANG_PACKAGE_PREFIX = "java.lang.";
    private static final String JUNIT_PACKAGE_PREFIX = "org.junit.";

    private PythonInterfaceStubGenerator() {
    }

    /**
     * Builds the interface definition.
     *
     * @param classElement The Python class
     * @param typeName     The Java type name
     * @param interfaces   The super interfaces
     * @param allClasses   All compiled Python classes
     * @param gate         The gate deciding whether the interface carries the reflection data of its class
     * @param context      The visitor context
     * @return The interface definition
     */
    static InterfaceDef buildInterfaceDef(AbstractPythonClassElement classElement,
                                          String typeName,
                                          Collection<ClassElement> interfaces,
                                          Map<String, ClassElement> allClasses,
                                          PythonReflectionGate gate,
                                          VisitorContext context) {
        InterfaceDef.InterfaceDefBuilder interfaceBuilder = InterfaceDef.builder(typeName)
            .addModifiers(Modifier.PUBLIC)
            .addAnnotation(Vetoed.class)
            // the runtime resolves the Python class of an AOP proxy implementing the interface from it
            .addAnnotation(pythonClassAnnotation(classElement));
        for (GenericPlaceholderElement placeholder : classElement.getDeclaredGenericPlaceholders()) {
            // the bounds decide the erasure of the interface methods, which the introduction proxy implements
            List<TypeDef> bounds = placeholder.getBounds().stream()
                .filter(bound -> !Object.class.getName().equals(bound.getName()))
                .map(PythonStubGenerator::sourceSignatureType)
                .toList();
            interfaceBuilder.addTypeVariable(TypeDef.variable(placeholder.getVariableName(), bounds));
        }
        for (ClassElement anInterface : interfaces) {
            interfaceBuilder.addSuperinterface(parameterizedTypeDef(anInterface));
        }
        copyRuntimeAnnotations(classElement, interfaceBuilder, ElementType.TYPE, typeName, gate, context);
        Set<String> addedMethodNames = new LinkedHashSet<>();
        for (MethodElement methodElement : declaredInstanceMethods(classElement)) {
            if (!addedMethodNames.add(bridgeMethodKey(methodElement))) {
                continue;
            }
            MethodDef.MethodDefBuilder methodBuilder = MethodDef.builder(methodElement.getName())
                .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
                .returns(sourceMethodReturnType(methodElement, false));
            addMethodTypeVariables(methodElement, methodBuilder);
            copyRuntimeAnnotations(methodElement, methodBuilder, ElementType.METHOD, typeName, gate, context);
            for (ParameterElement parameter : methodElement.getParameters()) {
                methodBuilder.addParameter(parameterDef(parameter, typeName, gate, context));
            }
            interfaceBuilder.addMethod(methodBuilder.build());
        }
        if (classElement instanceof PythonClassElement pythonClassElement && pythonClassElement.isIntroductionInterface()) {
            for (MethodElement methodElement : classElement.getEnclosedElements(ElementQuery.ALL_METHODS.onlyDeclared().onlyAccessible().onlyStatic())) {
                if (methodElement.isAbstract() || !addedMethodNames.add(bridgeMethodKey(methodElement))) {
                    continue;
                }
                addStaticBridgeMethod(classElement, typeName, methodElement, interfaceBuilder, allClasses, gate, context);
            }
        }
        return interfaceBuilder.build();
    }

    /**
     * The instance methods the Python class declares, as the element model reports them to the bean definition
     * and proxy writers: a Python method that overrides a method of an implemented Java interface adopts the
     * signature of that method (the boxed {@code Integer} id of a repository for an {@code int} hint), and the
     * generated interface has to declare the same signature for the introduction proxy to implement it.
     */
    private static List<MethodElement> declaredInstanceMethods(AbstractPythonClassElement classElement) {
        List<MethodElement> methods = new ArrayList<>();
        for (MethodElement methodElement : classElement.getEnclosedElements(ElementQuery.ALL_METHODS.onlyAccessible().onlyInstance())) {
            if (methodElement.getDeclaringType().equals(classElement)) {
                methods.add(methodElement);
            }
        }
        return methods;
    }

    /**
     * Emits a static interface method that invokes the static Python function of the class: an introduction
     * interface can declare static helpers next to the abstract methods that the advice implements, and a
     * framework that reads the interface reflectively finds them on the Java interface.
     */
    private static void addStaticBridgeMethod(AbstractPythonClassElement classElement,
                                              String typeName,
                                              MethodElement methodElement,
                                              InterfaceDef.InterfaceDefBuilder interfaceBuilder,
                                              Map<String, ClassElement> allClasses,
                                              PythonReflectionGate gate,
                                              VisitorContext context) {
        String functionName = methodElement.getName();
        MethodDef.MethodDefBuilder methodBuilder = MethodDef.builder(functionName)
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
            .returns(methodReturnType(methodElement, false));
        addMethodTypeVariables(methodElement, methodBuilder);
        copyRuntimeAnnotations(methodElement, methodBuilder, ElementType.METHOD, typeName, gate, context);
        for (ParameterElement parameter : methodElement.getParameters()) {
            methodBuilder.addParameter(parameterDef(parameter, typeName, gate, context));
        }
        ClassElement returnType = methodElement.getGenericReturnType();
        interfaceBuilder.addMethod(methodBuilder.build((aThis, methodParameters) -> {
            List<ExpressionDef> arguments = new ArrayList<>(methodParameters.size() + 2);
            arguments.add(pythonClassReferenceExpression(classElement));
            arguments.add(ExpressionDef.constant(functionName));
            arguments.addAll(methodParameters);
            ExpressionDef invokedValue = PYTHON_CONTEXT_RUNTIME.invokeStatic("invokeStaticMethod", POLYGLOT_VALUE, arguments);
            return returnConvertedValue(allClasses, returnType, invokedValue);
        }));
    }

    private static ParameterDef parameterDef(ParameterElement parameter, String typeName, PythonReflectionGate gate, VisitorContext context) {
        ParameterDef.ParameterDefBuilder parameterBuilder = ParameterDef.builder(parameter.getName(), sourceSignatureType(parameter.getGenericType()));
        copyRuntimeAnnotations(parameter, parameterBuilder, ElementType.PARAMETER, typeName, gate, context);
        return parameterBuilder.build();
    }

    /**
     * Copies the annotations that reflection-based frameworks need to find on the generated Java
     * declaration: every annotation of a Java annotation type with {@link RetentionPolicy#RUNTIME}
     * retention that may be placed on such a declaration. Python-defined annotations and the
     * {@code java.lang} annotations that constrain a declaration ({@code @FunctionalInterface},
     * {@code @SafeVarargs}) are never copied; Micronaut annotations only when their type is annotated
     * with {@link ReflectiveAccess}, the declaration that a framework reads them reflectively (an AI
     * service, for example) rather than through the annotation metadata. The annotations of other
     * libraries are the reflection data of the generated interface, copied only when the gate allows it.
     *
     * @param element        The Python element
     * @param builder        The builder of the generated declaration
     * @param declaration    The kind of the generated declaration
     * @param typeName       The name of the generated interface
     * @param gate           The gate deciding whether the interface carries reflection data
     * @param visitorContext The visitor context
     */
    static void copyRuntimeAnnotations(Element element, AbstractElementBuilder<?> builder, ElementType declaration, String typeName, PythonReflectionGate gate, VisitorContext visitorContext) {
        AnnotationMetadata annotationMetadata = element.getAnnotationMetadata();
        for (String annotationName : annotationMetadata.getDeclaredAnnotationNames()) {
            AnnotationValue<Annotation> annotationValue = annotationMetadata.getAnnotation(annotationName);
            PythonReflectionGate.Copy copy = runtimeAnnotationCopy(annotationName, declaration, visitorContext);
            if (annotationValue == null
                || copy == PythonReflectionGate.Copy.NEVER
                || (copy == PythonReflectionGate.Copy.REFLECTIVE && !gate.allows(typeName, annotationName, element))) {
                continue;
            }
            try {
                builder.addAnnotation(AnnotationDef.of(annotationValue, visitorContext));
            } catch (RuntimeException e) {
                visitorContext.warn("Annotation @" + annotationName + " is not copied onto the generated Java declaration of ["
                    + element.getName() + "], reflection-based frameworks will not see it: " + e.getMessage(), element);
            }
        }
    }

    private static PythonReflectionGate.Copy runtimeAnnotationCopy(String annotationName, ElementType declaration, VisitorContext visitorContext) {
        if (annotationName.startsWith(JAVA_LANG_PACKAGE_PREFIX) || TYPE_ANNOTATIONS_TO_SKIP_IN_SOURCE.contains(annotationName)) {
            return PythonReflectionGate.Copy.NEVER;
        }
        ClassElement annotationType = visitorContext.getClassElement(annotationName).orElse(null);
        if (annotationType == null
            || annotationType instanceof AbstractPythonClassElement
            || !PythonAnnotationTypes.isAnnotationType(annotationType)
            || PythonAnnotationTypes.retentionPolicy(annotationType) != RetentionPolicy.RUNTIME
            || !PythonAnnotationTypes.targetsDeclaration(annotationType, declaration)) {
            return PythonReflectionGate.Copy.NEVER;
        }
        if (annotationName.startsWith(MICRONAUT_PACKAGE_PREFIX)) {
            // a Micronaut annotation declared @ReflectiveAccess is read reflectively by the module that
            // declares it: that is Micronaut's own contract with the generated type, not third-party data
            return annotationType.hasAnnotation(ReflectiveAccess.class) ? PythonReflectionGate.Copy.ALWAYS : PythonReflectionGate.Copy.NEVER;
        }
        if (annotationName.startsWith(JUNIT_PACKAGE_PREFIX)) {
            return PythonReflectionGate.Copy.ALWAYS;
        }
        return PythonReflectionGate.Copy.REFLECTIVE;
    }
}
