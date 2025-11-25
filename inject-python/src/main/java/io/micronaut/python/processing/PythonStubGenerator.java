/*
 * Copyright 2017-2025 original authors
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

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.lang.model.element.Modifier;

import io.micronaut.aop.Around;
import io.micronaut.aop.InterceptorBinding;
import io.micronaut.aop.Introduction;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.ConfigurationReader;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Vetoed;
import io.micronaut.inject.ast.Element;
import io.micronaut.inject.ast.PrimitiveElement;
import io.micronaut.inject.ast.TypedElement;
import io.micronaut.python.processing.visitor.PythonVisitorContext;
import io.micronaut.sourcegen.model.AbstractElementBuilder;
import io.micronaut.sourcegen.model.AnnotationDef;
import org.graalvm.polyglot.Value;

import io.micronaut.context.annotation.Executable;
import io.micronaut.core.annotation.AnnotationUtil;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.ElementQuery;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.ParameterElement;
import io.micronaut.inject.ast.PropertyElement;
import io.micronaut.inject.visitor.TypeElementQuery;
import io.micronaut.inject.visitor.TypeElementVisitor;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.python.processing.visitor.AbstractPythonClassElement;
import io.micronaut.python.processing.visitor.PythonClassElement;
import io.micronaut.sourcegen.generator.SourceGenerator;
import io.micronaut.sourcegen.generator.SourceGenerators;
import io.micronaut.sourcegen.model.ClassDef;
import io.micronaut.sourcegen.model.ClassTypeDef;
import io.micronaut.sourcegen.model.ExpressionDef;
import io.micronaut.sourcegen.model.FieldDef;
import io.micronaut.sourcegen.model.MethodDef;
import io.micronaut.sourcegen.model.ParameterDef;
import io.micronaut.sourcegen.model.StatementDef;
import io.micronaut.sourcegen.model.TypeDef;
import io.micronaut.sourcegen.model.VariableDef;

public class PythonStubGenerator implements TypeElementVisitor<Object, Object> {

    public static final TypeDef POLYGLOT_VALUE = TypeDef.of(Value.class);
    public static final VariableDef.StaticField CLASS_OBJECT = ClassTypeDef.of(Object.class).getStaticField("class", TypeDef.CLASS);
    public static final String AS_POLYGLOT_VALUE = "asPolyglotValue";
    public static final String FROM_POLYGLOT_VALUE = "fromPolyglotValue";
    public static final ClassTypeDef RUNTIME_UTIL = ClassTypeDef.of("io.micronaut.context.python.GraalPyRuntimeUtil");
    public static final ClassTypeDef CONTEXT_HOLDER = ClassTypeDef.of("io.micronaut.context.python.ContextHolder");
    public static final String GENERATOR_NAME = "python";
    private static final Set<String> ANNOTATION_PACKAGES_TO_COPY = Set.of("org.junit.jupiter.api", "io.micronaut.test.extensions.junit5.annotation");
    public static final String JUNIT_TEST = "org.junit.jupiter.api.Test";

    private final Map<String, AbstractPythonClassElement> classElements = new LinkedHashMap<>();
    private Map<String, ClassElement> allClasses = Map.of();

    @Override
    public TypeElementQuery query() {
        return TypeElementQuery.onlyClass();
    }

    @Override
    public void finish(VisitorContext visitorContext) {
        classElements.clear();
    }

    @Override
    public void start(VisitorContext visitorContext) {
        if (visitorContext instanceof PythonVisitorContext pythonVisitorContext) {
            this.allClasses = pythonVisitorContext.getProcessingEnvironment().classes();
        }
    }

    @Override
    public void visitClass(ClassElement element, VisitorContext context) {
        if (context instanceof PythonVisitorContext pythonVisitorContext) {

            if (element instanceof AbstractPythonClassElement classElement) {
                SourceGenerator sourceGenerator = SourceGenerators.findByLanguage(VisitorContext.Language.JAVA).orElse(null);
                if (sourceGenerator != null) {

                    try {
                        if (classElements.containsKey(classElement.getName())) {
                            return;
                        }

                        classElements.put(classElement.getName(), classElement);

                        String typeName = element.getName();
                        boolean isAopProxy = classElement.hasStereotype(InterceptorBinding.class);

                        var builder = ClassDef.builder(typeName)
                            .addModifiers(Modifier.PUBLIC);
                        builder.addAnnotation(Vetoed.class);

                        copyAnnotations(element, builder, ANNOTATION_PACKAGES_TO_COPY, context);
                        Collection<ClassElement> interfaces = classElement.getInterfaces();
                        builder.addSuperinterface(ClassTypeDef.of("io.micronaut.context.python.ValueCoercible"));

                        // Check if this class extends another PythonClassElement
                        ClassElement superType = element.getSuperType().orElse(null);
                        boolean extendsPythonClass = superType instanceof AbstractPythonClassElement;

                        ClassTypeDef superClassType;
                        if (extendsPythonClass) {
                            superClassType = ClassTypeDef.of(superType.getName());
                            builder.superclass(superClassType);
                        }

                        FieldDef pythonValue;
                        if (!extendsPythonClass) {
                            // Only add the field for root classes (not extending other Python classes)
                            pythonValue = FieldDef.builder("graalpyInternalValue")
                                .ofType(POLYGLOT_VALUE).addModifiers(Modifier.FINAL, Modifier.PROTECTED).build();
                            builder.addField(pythonValue);
                        } else {
                            pythonValue = null;
                        }

                        // Track method names that have been added to avoid duplicates
                        Set<String> addedMethodNames = new LinkedHashSet<>();

                        for (ClassElement anInterface : interfaces) {
                            builder.addSuperinterface(TypeDef.of(anInterface));
                            List<MethodElement> methods = anInterface.getMethods();
                            Set<MethodElement> methodSet = new LinkedHashSet<>();
                            for (MethodElement method : methods) {
                                if (methodSet.contains(method)) {
                                    continue;
                                }
                                if (method.hasDeclaredStereotype(InterceptorBinding.class)) {
                                    isAopProxy = true;
                                }
                                addBridgeMethod(method, builder, pythonValue, context, false, addedMethodNames);
                                methodSet.add(method);
                            }
                        }

                        boolean isJunit5Test = element.getEnclosedElement(ElementQuery.ALL_METHODS.onlyInstance().annotated(ann -> ann.hasDeclaredAnnotation(JUNIT_TEST))).isPresent();

                        // Only add asPolyglotValue and fromPolyglotValue for root classes
                        if (!isJunit5Test) {
                            builder.addMethod(
                                MethodDef.constructor()
                                    .addParameter(ParameterDef.of("value", POLYGLOT_VALUE))
                                    .build(((aThis, methodParameters) -> {
                                            if (extendsPythonClass) {
                                                // For child classes, call super with the polyglot value
                                                return aThis.superRef().invokeConstructor(methodParameters.get(0));
                                            } else {
                                                // Assign to field for root classes
                                                return aThis.field(pythonValue).assign(methodParameters.get(0));
                                            }
                                        })
                                    ));
                        }

                        // implement asPolygotValue
                        builder.addMethod(MethodDef.builder(AS_POLYGLOT_VALUE)
                            .addModifiers(Modifier.PUBLIC)
                            .returns(POLYGLOT_VALUE).build(((aThis, methodParameters) ->
                                toFieldRef(pythonValue, aThis).returning())
                            ));

                        // implement static factory
                        ClassTypeDef thisType = ClassTypeDef.of(typeName);

                        if (!isJunit5Test) {
                            builder.addMethod(MethodDef.builder(FROM_POLYGLOT_VALUE)
                                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                                .addParameter(POLYGLOT_VALUE)
                                .returns(thisType).build(((aThis, methodParameters) ->
                                    thisType.instantiate(methodParameters).returning()))
                            );
                        }


                        // Check if there's a primary constructor with parameters for dependency injection
                        var pythonConstructor = element.getPrimaryConstructor().orElse(null);

                        String instantiateMethod = element.isAbstract() && isAopProxy && element.hasStereotype(Introduction.class) ? "newIntroduction" : "newInstance";
                        if (pythonConstructor != null && pythonConstructor.getParameters().length > 0) {
                            // Generate constructor with dependency injection parameters
                            MethodDef.MethodDefBuilder constructor = MethodDef.constructor();
                            @NonNull ParameterElement[] parameters = pythonConstructor.getParameters();
                            for (@NonNull ParameterElement parameter : parameters) {
                                ClassElement t = parameter.getType();
                                var parameterType = erasedType(t);
                                ParameterDef parameterDef = ParameterDef
                                    .builder(parameter.getName(), parameterType).build();
                                constructor.addParameter(parameterDef);
                            }

                            builder.addMethod(
                                constructor.build(((aThis, methodParameters) -> {
                                    // Create the Python object by calling constructor with parameters
                                    List<ExpressionDef> arguments = new ArrayList<>(List.of(
                                        ExpressionDef.constant(element.getPackageName()),
                                        ExpressionDef.constant(element.getSimpleName())
                                    ));
                                    for (int i = 0; i < parameters.length; i++) {
                                        @NonNull ParameterElement parameter = parameters[i];
                                        VariableDef.MethodParameter methodParameter = methodParameters.get(i);
                                        coerceParameterToPolyglotValue(parameter, arguments, methodParameter);
                                    }
                                    // Pass constructor parameters directly to newInstance
                                    ExpressionDef pythonInstance = CONTEXT_HOLDER.invokeStatic(
                                        instantiateMethod,
                                        POLYGLOT_VALUE,
                                        arguments
                                    );

                                    if (extendsPythonClass) {
                                        // For child classes, call super with the polyglot value
                                        return aThis.superRef().invokeConstructor(pythonInstance);
                                    } else {
                                        // Assign to field for root classes
                                        return aThis.field(pythonValue).assign(pythonInstance);
                                    }
                                }))
                            );
                        } else {
                            // Default constructor for classes without __init__ or with no parameters
                            MethodDef.MethodDefBuilder constructor = MethodDef.constructor();
                            builder.addMethod(
                                constructor.build(((aThis, methodParameters) -> {
                                    ExpressionDef pythonInstance = CONTEXT_HOLDER
                                        .invokeStatic(instantiateMethod, POLYGLOT_VALUE,
                                            List.of(
                                                ExpressionDef.constant(element.getPackageName()),
                                                ExpressionDef.constant(element.getSimpleName())
                                            )
                                        );

                                    if (extendsPythonClass) {
                                        // For child classes, call super with the polyglot value
                                        return aThis.superRef().invokeConstructor(pythonInstance);
                                    } else {
                                        // Assign to field for root classes
                                        return aThis.field(pythonValue).assign(pythonInstance);
                                    }
                                }))
                            );
                        }

                        List<MethodElement> methodsToBridge = element.getEnclosedElements(
                            ElementQuery.ALL_METHODS
                                .onlyAccessible()
                                .onlyInstance()
                                .annotated(ann -> isJunit5Test ||
                                    ann.hasStereotype(Executable.class) ||
                                    ann.hasAnnotation(AnnotationUtil.PRE_DESTROY) ||
                                    ann.hasAnnotation(AnnotationUtil.POST_CONSTRUCT) ||
                                    ann.hasStereotype(Around.class) ||
                                    element.hasStereotype(Around.class) ||
                                    ann.hasDeclaredStereotype(AnnotationUtil.SCOPE) ||
                                    ann.hasDeclaredStereotype(Bean.class)));


                        for (MethodElement methodElement : methodsToBridge) {
                            if (methodElement.hasDeclaredStereotype(InterceptorBinding.class)) {
                                isAopProxy = true;
                            }
                            addBridgeMethod(methodElement, builder, pythonValue, context, methodElement.hasDeclaredAnnotation(JUNIT_TEST), addedMethodNames);
                        }

                        // Find injection methods (annotated with @Inject)
                        List<MethodElement> injectionMethods = element.getEnclosedElements(
                            ElementQuery.ALL_METHODS
                                .onlyAccessible()
                                .onlyInstance()
                                .filter(method -> !methodsToBridge.contains(method))
                                .annotated(ann ->
                                    ann.hasStereotype(AnnotationUtil.INJECT)
                                ));

                        // Generate methods for injection
                        for (MethodElement injectionMethod : injectionMethods) {
                            MethodDef.MethodDefBuilder injectionMethodBuilder = MethodDef.builder(injectionMethod.getName());
                            if (!injectionMethod.getReturnType().isVoid()) {
                                injectionMethodBuilder.returns(TypeDef.of(injectionMethod.getReturnType()));
                            }

                            for (@NonNull ParameterElement parameter : injectionMethod.getParameters()) {
                                var parameterType = TypeDef.of(parameter.getType());
                                ParameterDef parameterDef = ParameterDef
                                    .builder(parameter.getName(), parameterType).build();
                                injectionMethodBuilder.addParameter(parameterDef);
                            }

                            builder.addMethod(injectionMethodBuilder
                                .build(((aThis, methodParameters) -> {
                                    // For child classes, pythonValue will be null, so we need to create a field reference to the inherited field
                                    VariableDef.Field pythonValueField = toFieldRef(pythonValue, aThis);
                                    List<ExpressionDef> parameters = new ArrayList<>();
                                    parameters.add(ExpressionDef.constant(injectionMethod.getName()));

                                    // Handle parameter conversion for Python classes
                                    for (int i = 0; i < injectionMethod.getParameters().length; i++) {
                                        ParameterElement param = injectionMethod.getParameters()[i];
                                        VariableDef.MethodParameter methodParam = methodParameters.get(i);
                                        coerceParameterToPolyglotValue(param, parameters, methodParam);
                                    }

                                    // Call the Python injection method
                                    var invokedValue = pythonValueField.invoke(
                                        "invokeMember",
                                        POLYGLOT_VALUE,
                                        parameters
                                    );

                                    // For injection methods, just invoke without explicit return
                                    ClassElement returnType = injectionMethod.getReturnType();
                                    if (returnType.isVoid()) {
                                        return invokedValue;
                                    } else {
                                        return StatementDef.multi(
                                            invokedValue,
                                            ExpressionDef.nullValue().returning()
                                        );
                                    }
                                })));
                        }

                        // Find static factory methods (annotated with @Creator)
                        List<MethodElement> staticCreatorMethod = element.getEnclosedElements(
                            ElementQuery.ALL_METHODS
                                .onlyAccessible()
                                .onlyStatic()
                                .annotated(ann -> ann.hasStereotype("io.micronaut.core.annotation.Creator"))
                        );

                        // Generate static factory methods for @Creator methods
                        for (MethodElement creatorMethod : staticCreatorMethod) {
                            addCreatorFactoryMethod(creatorMethod, builder, element, context);
                        }

                        // Find injection fields (with Annotated[Type, Inject] syntax)
                        // For now, we'll look for fields that have any annotation and check for Inject in metadata
                        List<PropertyElement> beanProperties = element.getBeanProperties();
                        for (PropertyElement beanProperty : beanProperties) {
                            boolean isIntrospected = element.hasStereotype(Introspected.class) || element.hasStereotype(ConfigurationReader.class);
                            if (isIntrospected || beanProperty.hasStereotype(AnnotationUtil.INJECT)) {
                                addSetter(beanProperty, builder, pythonValue);
                            }

                            if (isIntrospected) {
                                addGetter(beanProperty, builder, pythonValue);
                            }
                        }

                        if (isAopProxy) {
                            builder.addSuperinterface(ClassTypeDef.of("io.micronaut.context.python.aop.PythonAopSetup"));
                        }

                        sourceGenerator.write(builder.build(), context, element);
                    } catch (Exception e) {
                        context.fail("Failed to generate stub for Python type [" + element.getSimpleName() + "]: " + e.getMessage(), null);
                    }

                }
            }
        }
    }

    private static TypeDef erasedType(ClassElement t) {
        var parameterType = !t.getTypeArguments().isEmpty() ? ClassTypeDef.of(t.getName()) : TypeDef.of(t);
        return parameterType;
    }

    private static void coerceParameterToPolyglotValue(
        TypedElement param,
        List<ExpressionDef> parameters,
        VariableDef.MethodParameter methodParam) {
        ClassElement genericType = param.getGenericType();
        if (genericType.isAssignable(Map.class) && genericType.getTypeArguments().get("V") instanceof PythonClassElement) {
            parameters.add(RUNTIME_UTIL.invokeStatic("coerceMap", TypeDef.of(Map.class), methodParam));
        } else if (genericType.isAssignable(List.class) && genericType.getTypeArguments().get("E") instanceof PythonClassElement) {
            parameters.add(RUNTIME_UTIL.invokeStatic("coerceList", TypeDef.of(List.class), methodParam));
        } else if (genericType instanceof PythonClassElement) {
            if (param.hasAnnotation("jakarta.annotation.Nullable")) {
                // Handle nullable Python class parameters
                parameters.add(
                    methodParam.isNull().doIfElse(
                        ExpressionDef.nullValue(),
                        methodParam.invoke(AS_POLYGLOT_VALUE, POLYGLOT_VALUE)
                    )
                );
            } else {
                parameters.add(methodParam.invoke(AS_POLYGLOT_VALUE, POLYGLOT_VALUE));
            }
        } else {
            parameters.add(methodParam);
        }
    }

    private void copyAnnotations(Element element, AbstractElementBuilder<?> builder, Set<String> annotationPackagesToCopy, VisitorContext visitorContext) {
        AnnotationMetadata annotationMetadata = element.getAnnotationMetadata();
        Set<String> annotationNames = annotationMetadata.getDeclaredAnnotationNames();
        for (String annotationName : annotationNames) {
            if (annotationName.equals("io.micronaut.context.annotation.PropertySource") || annotationPackagesToCopy.stream().anyMatch(annotationName::startsWith)) {
                AnnotationValue<Annotation> av = annotationMetadata.getAnnotation(annotationName);
                if (av != null) {
                    builder.addAnnotation(AnnotationDef.of(av, visitorContext));
                }
            }
        }
    }

    private void addBridgeMethod(MethodElement methodElement, ClassDef.ClassDefBuilder builder, FieldDef pythonValue, VisitorContext visitorContext, boolean isJunit5Test, Set<String> addedMethodNames) {
        String pythonFunctionName = methodElement.getName();
        String key = methodElement.getDescription(true);
        // Check if method name has already been added to avoid duplicates
        if (addedMethodNames.contains(key)) {
            return;
        }

        addedMethodNames.add(key);

        MethodDef.MethodDefBuilder methodBuilder = MethodDef.builder(pythonFunctionName)
            .addModifiers(Modifier.PUBLIC)
            .returns(isJunit5Test ? TypeDef.Primitive.VOID : TypeDef.of(methodElement.getReturnType()));

        copyAnnotations(methodElement, methodBuilder, ANNOTATION_PACKAGES_TO_COPY, visitorContext);
        for (@NonNull ParameterElement parameter : methodElement.getParameters()) {
            var parameterType = erasedType(parameter.getType());
            ParameterDef parameterDef = ParameterDef
                .builder(parameter.getName(), parameterType).build();
            methodBuilder.addParameter(parameterDef);
        }

        builder.addMethod(methodBuilder
            .build(((aThis, methodParameters) -> {
                // For child classes, pythonValue will be null, so we need to create a field reference to the inherited field
                VariableDef.Field pythonValueField = toFieldRef(pythonValue, aThis);
                List<ExpressionDef> parameters = new ArrayList<>();
                parameters.add(ExpressionDef.constant(pythonFunctionName));
                parameters.addAll(methodParameters);

                // Get the return type to determine appropriate conversion method
                var returnType = methodElement.getReturnType();
                var invokedValue = pythonValueField.invoke(
                    "invokeMember",
                    POLYGLOT_VALUE,
                    parameters
                );

                if (isJunit5Test) {
                    return invokedValue;
                } else {
                    return handleReturnType(returnType, invokedValue);
                }
            })));
    }

    private static VariableDef.Field toFieldRef(FieldDef pythonValue, VariableDef.This aThis) {
        VariableDef.Field pythonValueField;
        if (pythonValue != null) {
            pythonValueField = aThis.field(pythonValue);
        } else {
            // For child classes, access the inherited field
            pythonValueField = aThis.field("graalpyInternalValue", POLYGLOT_VALUE);
        }
        return pythonValueField;
    }

    private void addGetter(PropertyElement beanProperty, ClassDef.ClassDefBuilder builder, FieldDef pythonValue) {
        TypeDef propertyType = TypeDef.of(beanProperty.getType());
        MethodDef.MethodDefBuilder getterBuilder = MethodDef.builder(beanProperty.getName())
            .returns(propertyType);

        builder.addMethod(getterBuilder.build(((aThis, methodParameters) -> {
            // For child classes, pythonValue will be null, so we need to create a field reference to the inherited field
            VariableDef.Field pythonValueField = toFieldRef(pythonValue, aThis);

            // Get the return type to determine appropriate conversion method
            var invokedValue = pythonValueField.invoke(
                "getMember",
                POLYGLOT_VALUE,
                ExpressionDef.constant(beanProperty.getName()
                ));

            return handleReturnType(beanProperty.getType(), invokedValue);
        })));
    }

    private static void addSetter(PropertyElement beanProperty, ClassDef.ClassDefBuilder builder, FieldDef pythonValue) {
        TypeDef returnType = beanProperty.getWriteMethod()
            .map(MethodElement::getReturnType)
            .map(TypeDef::of).orElse(TypeDef.VOID);
        MethodDef.MethodDefBuilder propertySetter = MethodDef
            .builder(beanProperty.getName())
            .returns(returnType);

        propertySetter.addParameter(TypeDef.of(beanProperty.getType()));

        builder.addMethod(propertySetter.build(((aThis, methodParameters) -> {
            // For child classes, pythonValue will be null, so we need to create a field reference to the inherited field
            VariableDef.Field pythonValueField = toFieldRef(pythonValue, aThis);
            List<ExpressionDef> parameters = new ArrayList<>();
            parameters.add(ExpressionDef.constant(beanProperty.getName()));
            coerceParameterToPolyglotValue(
                beanProperty,
                parameters,
                methodParameters.getFirst()
            );
            // Call the Python injection method
            ExpressionDef.InvokeInstanceMethod result = pythonValueField.invoke(
                "putMember",
                TypeDef.VOID,
                parameters
            );
            if (returnType.equals(TypeDef.VOID)) {
                return result;
            } else {
                return StatementDef.multi(
                    result,
                    ExpressionDef.nullValue().returning()
                );
            }

        })));
    }

    private StatementDef handleReturnType(ClassElement returnType, ExpressionDef.InvokeInstanceMethod invokedValue) {
        // Choose appropriate conversion method based on return type
        if (returnType.isVoid()) {
            // For void methods, just invoke the Python method without returning
            return invokedValue;
        } else if (returnType.isPrimitive()) {
            return convertPrimitive(returnType, invokedValue);
        } else {
            // Handle boxed types and other reference types
            String referenceTypeName = returnType.getName();
            return switch (referenceTypeName) {
                case "java.lang.Integer" ->
                    invokedValue.invoke("asInt", TypeDef.Primitive.INT).returning();
                case "java.lang.Boolean" ->
                    invokedValue.invoke("asBoolean", TypeDef.Primitive.BOOLEAN).returning();
                case "java.lang.Double" ->
                    invokedValue.invoke("asDouble", TypeDef.Primitive.DOUBLE).returning();
                case "java.lang.Float" ->
                    invokedValue.invoke("asFloat", TypeDef.Primitive.FLOAT).returning();
                case "java.lang.Long" ->
                    invokedValue.invoke("asLong", TypeDef.Primitive.LONG).returning();
                case "java.lang.Short" ->
                    invokedValue.invoke("asShort", TypeDef.Primitive.SHORT).returning();
                case "java.lang.Byte" ->
                    invokedValue.invoke("asByte", TypeDef.Primitive.BYTE).returning();
                case "java.lang.Character" -> invokedValue.invoke("asString", ClassTypeDef.STRING)
                    .invoke("charAt", TypeDef.Primitive.CHAR, ExpressionDef.constant(0)).returning();
                case "java.lang.String" ->
                    invokedValue.invoke("asString", ClassTypeDef.STRING).returning();
                default -> {
                    // Check for collection types
                    if (returnType.isAssignable(List.class)) {
                        ClassElement componentType = returnType.getFirstTypeArgument().orElse(null);
                        ExpressionDef genericType = toClassExpression(componentType);
                        yield RUNTIME_UTIL
                            .invokeStatic("convertList", ClassTypeDef.of(List.class),
                                invokedValue, genericType)
                            .returning();
                    } else if (returnType.isAssignable(Map.class)) {
                        Map<String, ClassElement> typeArguments = returnType.getTypeArguments();
                        ExpressionDef keyType = toClassExpression(typeArguments.get("K"));
                        ExpressionDef valueType = toClassExpression(typeArguments.get("V"));
                        yield RUNTIME_UTIL
                            .invokeStatic("convertMap", ClassTypeDef.of(Map.class),
                                invokedValue, keyType, valueType)
                            .returning();
                    } else if (returnType.isAssignable(Set.class)) {
                        ClassElement componentType = returnType.getFirstTypeArgument().orElse(null);
                        ExpressionDef genericType = toClassExpression(componentType);

                        yield RUNTIME_UTIL
                            .invokeStatic("convertSet", ClassTypeDef.of(Set.class),
                                invokedValue, genericType)
                            .returning();
                    } else if (returnType.isAssignable(java.util.Optional.class)) {
                        ClassElement componentType = returnType.getFirstTypeArgument().orElse(null);
                        ExpressionDef genericType = toClassExpression(componentType);

                        yield RUNTIME_UTIL
                            .invokeStatic("convertOptional", ClassTypeDef.of(java.util.Optional.class),
                                invokedValue, genericType)
                            .returning();
                    } else {
                        if (allClasses.containsKey(returnType.getName())) {
                            yield ClassTypeDef.of(returnType)
                                .invokeStatic(FROM_POLYGLOT_VALUE, POLYGLOT_VALUE, invokedValue)
                                .returning();
                        } else {
                            yield RUNTIME_UTIL
                                .invokeStatic("convertValue", ClassTypeDef.OBJECT,
                                    invokedValue, ClassTypeDef.of(returnType).getStaticField("class", TypeDef.CLASS))
                                .returning();
                        }
                    }
                }
            };
        }
    }

    private static ExpressionDef toClassExpression(ClassElement componentType) {
        ExpressionDef genericType;
        if (componentType == null) {
            genericType = CLASS_OBJECT;
        } else {
            genericType = ClassTypeDef.of(componentType).getStaticField("class", TypeDef.CLASS);
        }
        return genericType;
    }

    private void addCreatorFactoryMethod(MethodElement creatorMethod, ClassDef.ClassDefBuilder builder, ClassElement element, VisitorContext context) {
        String pythonMethodName = creatorMethod.getName();
        ClassTypeDef thisType = ClassTypeDef.of(element.getName());

        MethodDef.MethodDefBuilder factoryMethodBuilder = MethodDef.builder(pythonMethodName)
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
            .returns(thisType);

        // Add parameters
        for (int i = 0; i < creatorMethod.getParameters().length; i++) {
            @NonNull ParameterElement parameter = creatorMethod.getParameters()[i];
            // first parameter in Python is the class, skip it
            if (i == 0) {
                continue;
            }
            var parameterType = TypeDef.of(parameter.getType());
            ParameterDef parameterDef = ParameterDef
                .builder(parameter.getName(), parameterType).build();
            factoryMethodBuilder.addParameter(parameterDef);
        }

        builder.addMethod(factoryMethodBuilder
            .build(((aThis, methodParameters) -> {
                // Call the Python static method via CONTEXT_HOLDER
                List<ExpressionDef> arguments = new ArrayList<>();
                arguments.add(ExpressionDef.constant(element.getPackageName()));
                arguments.add(ExpressionDef.constant(element.getSimpleName()));
                arguments.add(ExpressionDef.constant(pythonMethodName));

                // Add method parameters
                for (VariableDef.MethodParameter methodParam : methodParameters) {
                    arguments.add(methodParam);
                }

                // Call invokeStaticMethod and convert the result
                ExpressionDef pythonResult = CONTEXT_HOLDER.invokeStatic(
                    "invokeStaticMethod",
                    POLYGLOT_VALUE,
                    arguments
                );

                // Convert the result back to the Java type
                return thisType.invokeStatic(FROM_POLYGLOT_VALUE, POLYGLOT_VALUE, pythonResult).returning();
            })));
    }

    private static StatementDef convertPrimitive(ClassElement returnType, ExpressionDef.InvokeInstanceMethod invokedValue) {
        String primitiveTypeName = returnType.getName();
        return switch (primitiveTypeName) {
            case "int", "java.lang.Integer" ->
                invokedValue.invoke("asInt", TypeDef.Primitive.INT).returning();
            case "boolean", "java.lang.Boolean" ->
                invokedValue.invoke("asBoolean", TypeDef.Primitive.BOOLEAN).returning();
            case "double", "java.lang.Double" ->
                invokedValue.invoke("asDouble", TypeDef.Primitive.DOUBLE).returning();
            case "float", "java.lang.Float" ->
                invokedValue.invoke("asFloat", TypeDef.Primitive.FLOAT).returning();
            case "long", "java.lang.Long" ->
                invokedValue.invoke("asLong", TypeDef.Primitive.LONG).returning();
            case "short", "java.lang.Short" ->
                invokedValue.invoke("asShort", TypeDef.Primitive.SHORT).returning();
            case "byte", "java.lang.Byte" ->
                invokedValue.invoke("asByte", TypeDef.Primitive.BYTE).returning();
            case "char", "java.lang.Character" ->
                invokedValue.invoke("asString", ClassTypeDef.STRING)
                    .invoke("charAt", TypeDef.Primitive.CHAR, ExpressionDef.constant(0)).returning();
            default -> invokedValue.invoke("asString", ClassTypeDef.STRING).returning();
        };
    }

    @Override
    public VisitorKind getVisitorKind() {
        return VisitorKind.ISOLATING;
    }
}
