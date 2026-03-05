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
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import javax.lang.model.element.Modifier;

import io.micronaut.aop.Around;
import io.micronaut.aop.InterceptorBinding;
import io.micronaut.aop.Introduction;
import io.micronaut.context.annotation.Bean;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Creator;
import io.micronaut.core.annotation.Vetoed;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.inject.ast.Element;
import io.micronaut.inject.ast.TypedElement;
import io.micronaut.inject.processing.BeanDefinitionCreatorFactory;
import io.micronaut.inject.processing.ProcessingException;
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
import io.micronaut.python.processing.visitor.PythonScriptElement;
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
import io.micronaut.python.processing.util.ObjectHelper;

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
    public static final String ANN_JSON_PROPERTY = "com.fasterxml.jackson.annotation.JsonProperty";
    public static final String ANN_JSON_CREATOR = "com.fasterxml.jackson.annotation.JsonCreator";

    private final Map<String, StubEntry> classBuilders = new LinkedHashMap<>();
    private Map<String, ClassElement> allClasses = Map.of();

    @Override
    public TypeElementQuery query() {
        return TypeElementQuery.onlyClass();
    }

    @Override
    public void finish(VisitorContext visitorContext) {
        SourceGenerator sourceGenerator = SourceGenerators.findByLanguage(VisitorContext.Language.JAVA).orElse(null);
        try {
            if (sourceGenerator != null) {
                for (StubEntry entry : classBuilders.values()) {
                    ClassDef.ClassDefBuilder builder = entry.builder;
                    sourceGenerator.write(builder.build(), visitorContext, entry.originatingElement);
                }
            }
        } finally {
            classBuilders.clear();
        }
    }

    @Override
    public void start(VisitorContext visitorContext) {
        if (visitorContext instanceof PythonVisitorContext pythonVisitorContext) {
            this.allClasses = pythonVisitorContext.getProcessingEnvironment().classes();
        }
    }

    @Override
    public void visitClass(ClassElement element, VisitorContext context) {
        if (context instanceof PythonVisitorContext) {

            if (element instanceof PythonScriptElement scriptElement) {
                boolean hasRoute = !scriptElement.getEnclosedElements(
                    ElementQuery.ALL_METHODS
                        .onlyAccessible()
                        .onlyInstance()
                        .onlyDeclared()
                        .annotated(ann -> ann.hasStereotype("io.micronaut.http.annotation.HttpMethodMapping"))
                ).isEmpty();
                if (hasRoute || scriptElement.hasStereotype("io.micronaut.context.python.scope.ContextPooled")) {
                    if (classBuilders.containsKey(scriptElement.getName())) {
                        return;
                    }
                    var builder = PythonPooledStubGenerator.generatePooledScript(scriptElement, context, allClasses);
                    classBuilders.put(scriptElement.getName(), new StubEntry(builder, scriptElement, Map.of()));
                } else {
                    visitScript(scriptElement, context);
                }
            } else if (element instanceof AbstractPythonClassElement classElement) {
                if (classElement.hasStereotype("io.micronaut.context.python.scope.ContextPooled")) {
                    if (classBuilders.containsKey(classElement.getName())) {
                        return;
                    }
                    var builder = PythonPooledStubGenerator.generatePooledClass(classElement, context, allClasses);
                    classBuilders.put(classElement.getName(), new StubEntry(builder, classElement, Map.of()));
                    return;
                }

                try {
                    if (classBuilders.containsKey(classElement.getName())) {
                        return;
                    }

                    String typeName = element.getName();
                    boolean isAopProxy = classElement.hasStereotype(InterceptorBinding.class);
                    boolean isDeclaredBean = BeanDefinitionCreatorFactory.isDeclaredBeanInMetadata(classElement) || isAopProxy;

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

                    boolean isIntrospectedBean = element.hasStereotype(Introspected.class);
                    final boolean isIntroductionBean = element.hasStereotype(Introduction.class);
                    final boolean skipInterfaceMethodBridges = isDeclaredBean && isIntroductionBean && !element.getInterfaces().isEmpty();
                    if (skipInterfaceMethodBridges) {
                        builder.addModifiers(Modifier.ABSTRACT);
                    }

                    List<PropertyElement> beanProperties = element.getBeanProperties();
                    Map<String, FieldDef> propertyFields = new LinkedHashMap<>();
                    if (isIntrospectedBean) {
                        for (PropertyElement beanProperty : beanProperties) {
                            FieldDef field = FieldDef.builder(beanProperty.getName())
                                .ofType(TypeDef.of(beanProperty.getType()))
                                .addModifiers(Modifier.PRIVATE)
                                .build();
                            builder.addField(field);
                            propertyFields.put(beanProperty.getName(), field);
                        }
                    }

                    FieldDef pythonValue = null;
                    if (!isIntrospectedBean && !extendsPythonClass) {
                        pythonValue = FieldDef.builder("graalpyInternalValue")
                            .ofType(POLYGLOT_VALUE).addModifiers(Modifier.FINAL, Modifier.PROTECTED).build();
                        builder.addField(pythonValue);
                    }

                    StubEntry stubEntry = new StubEntry(builder, classElement, propertyFields);
                    classBuilders.put(classElement.getName(), stubEntry);

                    // Track method names that have been added to avoid duplicates
                    Set<String> addedMethodNames = stubEntry.bridgedMethods();

                    if (isDeclaredBean) {

                        for (ClassElement anInterface : interfaces) {
                            TypeDef interfaceTypeDef = parameterizedTypeDef(anInterface);
                            builder.addSuperinterface(interfaceTypeDef);
                            if (skipInterfaceMethodBridges) {
                                continue;
                            }
                            List<MethodElement> methods = anInterface.getMethods();
                            Set<MethodElement> methodSet = new LinkedHashSet<>();
                            for (MethodElement method : methods) {
                                if (methodSet.contains(method) || method.isDefault()) {
                                    continue;
                                }
                                if (method.hasDeclaredStereotype(InterceptorBinding.class)) {
                                    isAopProxy = true;
                                }
                                addBridgeMethod(method, builder, context, false, false, addedMethodNames);
                                methodSet.add(method);
                            }
                        }
                    }

                    boolean isJunit5Test = element.getEnclosedElement(ElementQuery.ALL_METHODS.onlyInstance().annotated(ann -> ann.hasDeclaredAnnotation(JUNIT_TEST))).isPresent();

                    // Constructor from polyglot Value
                    final FieldDef pythonValueFinal = pythonValue;

                    if (!isJunit5Test) {
                        if (isIntrospectedBean) {
                            builder.addMethod(
                                MethodDef.constructor()
                                    .addModifiers(Modifier.PUBLIC)
                                    .addParameter(ParameterDef.of("value", POLYGLOT_VALUE))
                                    .build(((aThis, methodParameters) -> {
                                            VariableDef.MethodParameter val = methodParameters.get(0);
                                            List<StatementDef> assigns = new ArrayList<>();
                                            if (extendsPythonClass) {
                                                assigns.add(aThis.superRef().invokeConstructor(val));
                                            }
                                            for (PropertyElement beanProperty : beanProperties) {
                                                FieldDef field = propertyFields.get(beanProperty.getName());
                                                ExpressionDef.InvokeInstanceMethod has = val.invoke("hasMember", TypeDef.Primitive.BOOLEAN, ExpressionDef.constant(beanProperty.getName()));
                                                ExpressionDef.InvokeInstanceMethod member = val.invoke("getMember", POLYGLOT_VALUE, ExpressionDef.constant(beanProperty.getName()));
                                                ExpressionDef valueExpr = convertValueForType(beanProperty.getType(), member);
                                                assigns.add(has.isTrue().doIfElse(
                                                    aThis.field(field).assign(valueExpr),
                                                    StatementDef.multi()
                                                ));
                                            }
                                            return StatementDef.multi(assigns);
                                        })
                                    ));
                        } else {
                            builder.addMethod(
                                MethodDef.constructor()
                                    .addModifiers(Modifier.PUBLIC)
                                    .addParameter(ParameterDef.of("value", POLYGLOT_VALUE))
                                    .build(((aThis, methodParameters) -> {
                                            if (extendsPythonClass) {
                                                return aThis.superRef().invokeConstructor(methodParameters.get(0));
                                            } else {
                                                return aThis.field(pythonValueFinal).assign(methodParameters.get(0));
                                            }
                                        })
                                    ));
                        }
                    }

                    // implement asPolyglotValue by reconstructing the Python object with current field values
                    if (isIntrospectedBean) {
                        final boolean isAbstractIntro = element.isAbstract() && isAopProxy && element.hasStereotype(Introduction.class);
                        builder.addMethod(MethodDef.builder(AS_POLYGLOT_VALUE)
                            .addModifiers(Modifier.PUBLIC)
                            .returns(POLYGLOT_VALUE).build(((aThis, methodParameters) -> {
                                    var primaryCtor = element.getPrimaryConstructor().orElse(null);
                                    if (primaryCtor == null) {
                                        // No explicit constructor: instantiate with no args and set members via map
                                        List<ExpressionDef> mapEntries = new ArrayList<>();
                                        for (PropertyElement beanProperty : beanProperties) {
                                            FieldDef field = propertyFields.get(beanProperty.getName());
                                            ExpressionDef fieldRef = aThis.field(field);
                                            mapEntries.add(ExpressionDef.constant(beanProperty.getName()));
                                            mapEntries.add(coerceTypedElementToPolyglotValue(beanProperty, fieldRef));
                                        }
                                        ExpressionDef propsMap = ClassTypeDef.of(AnnotationUtil.class)
                                            .invokeStatic("mapOf", TypeDef.of(Map.class), mapEntries);
                                        return CONTEXT_HOLDER.invokeStatic(
                                            "newInstance",
                                            POLYGLOT_VALUE,
                                            List.of(
                                                ExpressionDef.constant(element.getPackageName()),
                                                ExpressionDef.constant(element.getSimpleName()),
                                                propsMap
                                            )
                                        ).returning();
                                    } else {
                                        // Constructor present: use positional args
                                        List<ExpressionDef> args = new ArrayList<>();
                                        args.add(ExpressionDef.constant(element.getPackageName()));
                                        args.add(ExpressionDef.constant(element.getSimpleName()));
                                        for (PropertyElement beanProperty : beanProperties) {
                                            FieldDef field = propertyFields.get(beanProperty.getName());
                                            ExpressionDef fieldRef = aThis.field(field);
                                            args.add(coerceTypedElementToPolyglotValue(beanProperty, fieldRef).cast(TypeDef.OBJECT));
                                        }
                                        return CONTEXT_HOLDER.invokeStatic(isAbstractIntro ? "newIntroduction" : "newInstance", POLYGLOT_VALUE, args).returning();
                                    }
                                })
                            ));
                    } else {
                        builder.addMethod(MethodDef.builder(AS_POLYGLOT_VALUE)
                            .addModifiers(Modifier.PUBLIC)
                            .returns(POLYGLOT_VALUE).build(((aThis, methodParameters) -> {
                                if (pythonValueFinal != null) {
                                    return aThis.field(pythonValueFinal).returning();
                                } else {
                                    return aThis.field("graalpyInternalValue", POLYGLOT_VALUE).returning();
                                }
                            }))
                        );
                    }

                    // implement static factory
                    ClassTypeDef thisType = ClassTypeDef.of(typeName);

                    if (!isJunit5Test) {
                        builder.addMethod(MethodDef.builder(FROM_POLYGLOT_VALUE)
                            .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                            .addParameter(POLYGLOT_VALUE)
                            .returns(thisType)
                            .build(((aThis, methodParameters) -> {
                                var val = methodParameters.get(0);
                                return val.invoke("isNull", TypeDef.Primitive.BOOLEAN)
                                    .isTrue()
                                    .doIfElse(
                                        ExpressionDef.nullValue().returning(),
                                        skipInterfaceMethodBridges
                                            ? val.invoke("as", thisType, thisType.getStaticField("class", TypeDef.CLASS)).returning()
                                            : thisType.instantiate(methodParameters).returning()
                                    );
                            }))
                        );
                    }

                    // Check if there's a primary constructor with parameters for dependency injection
                    var pythonConstructor = element.getPrimaryConstructor().orElse(null);

                    Optional<ClassElement> jsonCreatorClassElement = context.getClassElement(ANN_JSON_CREATOR);
                    Optional<ClassElement> jsonPropertyElement = context.getClassElement(ANN_JSON_PROPERTY);
                    if (pythonConstructor != null && pythonConstructor.getParameters().length > 0) {
                        MethodDef.MethodDefBuilder constructor = MethodDef.constructor();
                        @NonNull ParameterElement[] parameters = pythonConstructor.getParameters();
                        for (@NonNull ParameterElement parameter : parameters) {
                            ClassElement t = parameter.getType();
                            var parameterType = erasedType(t);
                            ParameterDef.ParameterDefBuilder pb = ParameterDef
                                .builder(parameter.getName(), parameterType);
                            if (jsonPropertyElement.isPresent() && isIntrospectedBean && !parameter.hasDeclaredAnnotation(ANN_JSON_PROPERTY)) {
                                pb.addAnnotation(AnnotationDef.builder(ClassTypeDef.of(jsonPropertyElement.get())).addMember(AnnotationMetadata.VALUE_MEMBER, parameter.getName()).build());
                            }
                            ParameterDef parameterDef = pb.build();
                            constructor.addParameter(parameterDef);
                        }

                        if (isIntrospectedBean) {
                            jsonCreatorClassElement.ifPresent(t ->
                                constructor.addAnnotation(t.getName())
                            );
                        }
                        final boolean isAbstractIntroCtor = element.isAbstract() && isAopProxy && element.hasStereotype(Introduction.class);
                        builder.addMethod(
                            constructor.build(((aThis, methodParameters) -> {
                                if (isIntrospectedBean) {
                                    List<ExpressionDef> arguments = new ArrayList<>(List.of(
                                        ExpressionDef.constant(element.getPackageName()),
                                        ExpressionDef.constant(element.getSimpleName())
                                    ));
                                    for (int i = 0; i < parameters.length; i++) {
                                        @NonNull ParameterElement parameter = parameters[i];
                                        VariableDef.MethodParameter methodParameter = methodParameters.get(i);
                                        coerceParameterToPolyglotValue(parameter, arguments, methodParameter);
                                        int lastArgIndex = arguments.size() - 1;
                                        arguments.set(lastArgIndex, arguments.get(lastArgIndex).cast(TypeDef.OBJECT));
                                    }
                                    ExpressionDef pythonInstance = CONTEXT_HOLDER.invokeStatic(
                                        isAbstractIntroCtor ? "newIntroduction" : "newInstance",
                                        POLYGLOT_VALUE,
                                        arguments
                                    );
                                    return aThis.invokeConstructor(pythonInstance);
                                } else {
                                    List<ExpressionDef> arguments = new ArrayList<>(List.of(
                                        ExpressionDef.constant(element.getPackageName()),
                                        ExpressionDef.constant(element.getSimpleName())
                                    ));
                                    for (int i = 0; i < parameters.length; i++) {
                                        @NonNull ParameterElement parameter = parameters[i];
                                        VariableDef.MethodParameter methodParameter = methodParameters.get(i);
                                        coerceParameterToPolyglotValue(parameter, arguments, methodParameter);
                                        int lastArgIndex = arguments.size() - 1;
                                        arguments.set(lastArgIndex, arguments.get(lastArgIndex).cast(TypeDef.OBJECT));
                                    }
                                    ExpressionDef pythonInstance = CONTEXT_HOLDER.invokeStatic(
                                        isAbstractIntroCtor ? "newIntroduction" : "newInstance",
                                        POLYGLOT_VALUE,
                                        arguments
                                    );
                                    if (extendsPythonClass) {
                                        return aThis.superRef().invokeConstructor(pythonInstance);
                                    } else {
                                        return aThis.field(pythonValueFinal).assign(pythonInstance);
                                    }
                                }
                            }))
                        );
                    } else {
                        if (isIntrospectedBean && pythonConstructor != null && pythonConstructor.getParameters().length != 0) {
                                // add default constructor
                                MethodDef.MethodDefBuilder defaultConstructor = MethodDef.constructor().addModifiers(Modifier.PUBLIC);
                                jsonCreatorClassElement.ifPresent(t ->
                                    defaultConstructor.addAnnotation(t.getName())
                                );

                                builder.addMethod(defaultConstructor.build());
                        }

                        MethodDef.MethodDefBuilder constructor = MethodDef.constructor();
                        final boolean isAbstractIntroNoArg = element.isAbstract() && isAopProxy && element.hasStereotype(Introduction.class);
                        builder.addMethod(constructor.build(((aThis, methodParameters) -> {
                            if (isIntrospectedBean) {
                                return StatementDef.multi();
                            } else {
                                ExpressionDef pythonInstance = CONTEXT_HOLDER
                                    .invokeStatic(isAbstractIntroNoArg ? "newIntroduction" : "newInstance", POLYGLOT_VALUE,
                                        List.of(
                                            ExpressionDef.constant(element.getPackageName()),
                                            ExpressionDef.constant(element.getSimpleName())
                                        )
                                    );
                                if (extendsPythonClass) {
                                    return aThis.superRef().invokeConstructor(pythonInstance);
                                } else {
                                    return aThis.field(pythonValueFinal).assign(pythonInstance);
                                }
                            }
                        })));
                    }

                    List<MethodElement> methodsToBridge = element.getEnclosedElements(
                        ElementQuery.ALL_METHODS
                            .onlyAccessible()
                            .onlyInstance()
                            .onlyDeclared()
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
                        addBridgeMethod(methodElement, builder, context, methodElement.hasDeclaredAnnotation(JUNIT_TEST), false, addedMethodNames);
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
                                List<ExpressionDef> parameters = new ArrayList<>();
                                parameters.add(ExpressionDef.constant(injectionMethod.getName()));

                                // Handle parameter conversion for Python classes
                                for (int i = 0; i < injectionMethod.getParameters().length; i++) {
                                    ParameterElement param = injectionMethod.getParameters()[i];
                                    VariableDef.MethodParameter methodParam = methodParameters.get(i);
                                    coerceParameterToPolyglotValue(param, parameters, methodParam);
                                }

                                var invokedValue = aThis.invoke(AS_POLYGLOT_VALUE, POLYGLOT_VALUE)
                                    .invoke("invokeMember", POLYGLOT_VALUE, parameters);

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
                        addCreatorFactoryMethod(creatorMethod, builder, element);
                    }

                    for (PropertyElement beanProperty : beanProperties) {
                        FieldDef field = propertyFields.get(beanProperty.getName());
                        if (isIntrospectedBean) {
                            if (field == null) {
                                continue;
                            }
                            addSetterPojo(beanProperty, builder, field);
                            addGetterPojo(beanProperty, builder, field);
                        } else {
                             addSetterDynamic(beanProperty, builder);
                             addGetterDynamic(beanProperty, builder);
                             beanProperty.getWriteMethod().ifPresent(m -> {
                                 String beanStyle = beanSetterName(beanProperty.getName());
                                 if (!m.getName().equals(beanStyle)) {
                                     addNamedSetterDynamic(beanProperty, builder);
                                 }
                             });
                             beanProperty.getReadMethod().ifPresent(m -> {
                                 String beanStyle = beanGetterName(beanProperty.getName(), beanProperty.getType());
                                 if (!m.getName().equals(beanStyle)) {
                                     addNamedGetterDynamic(beanProperty, builder);
                                 }
                             });
                         }
                     }

                     if (isIntrospectedBean) {
                         ObjectHelper.addObjectMethods(builder, ClassTypeDef.of(typeName), beanProperties, propertyFields);
                     }

                 } catch (ProcessingException e) {
                    throw e;
                } catch (Exception e) {
                    context.fail("Failed to generate stub for Python type [" + element.getSimpleName() + "]: " + e.getMessage(), null);
                }

            }
        }
    }

    private static TypeDef parameterizedTypeDef(ClassElement anInterface) {
        Map<String, ClassElement> typeArguments = anInterface.getTypeArguments();
        TypeDef interfaceTypeDef = TypeDef.of(anInterface);
        if (!typeArguments.isEmpty()) {
            Map<String, TypeDef> resolved = new LinkedHashMap<>(typeArguments.size());
            for (Map.Entry<String, ClassElement> entry : typeArguments.entrySet()) {
                resolved.put(entry.getKey(), parameterizedTypeDef(entry.getValue()));
            }
            interfaceTypeDef = ClassTypeDef.of(anInterface,
                resolved,
                false
            );
        }
        return interfaceTypeDef;
    }

    private void visitScript(PythonScriptElement scriptElement, VisitorContext context) {
        try {
            if (classBuilders.containsKey(scriptElement.getName())) {
                return;
            }

            String typeName = scriptElement.getName();

            var builder = ClassDef.builder(scriptElement.getPackageName() + "." + scriptElement.getSimpleName())
                .addModifiers(Modifier.PUBLIC);
            builder.addAnnotation(Vetoed.class);
            builder.addSuperinterface(ClassTypeDef.of("io.micronaut.context.python.ValueCoercible"));

            // Scripts are singletons - add a static instance field
            ClassTypeDef thisType = ClassTypeDef.of(typeName);
            FieldDef instanceField = FieldDef.builder("INSTANCE")
                .ofType(thisType)
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .build();

            // Add the GraalPy value field
            FieldDef pythonValue = FieldDef.builder("graalpyInternalValue")
                .ofType(POLYGLOT_VALUE)
                .addModifiers(Modifier.PRIVATE, Modifier.STATIC)
                .build();

            // Default constructor for classes without __init__ or with no parameters
            MethodDef.MethodDefBuilder constructor = MethodDef.constructor();
            builder.addMethod(
                constructor.build(((aThis, methodParameters) -> {
                    String name = scriptElement.getNativeType().name();
                    if (name.endsWith(".py")) {
                        name = name.substring(0, name.length() - 3);
                    }
                    ExpressionDef pythonInstance = CONTEXT_HOLDER
                        .invokeStatic("findScript", POLYGLOT_VALUE,
                            List.of(
                                ExpressionDef.constant(scriptElement.getPackageName()),
                                ExpressionDef.constant(name)
                            )
                        );

                    return aThis.field(pythonValue).assign(pythonInstance);
                }))
            );

            builder.addField(instanceField);
            builder.addField(pythonValue);

            StubEntry stubEntry = new StubEntry(builder, scriptElement, Map.of());
            classBuilders.put(scriptElement.getName(), stubEntry);

            // Track method names that have been added to avoid duplicates
            Set<String> addedMethodNames = stubEntry.bridgedMethods();

            // implement asPolyglotValue
            builder.addMethod(MethodDef.builder(AS_POLYGLOT_VALUE)
                .addModifiers(Modifier.PUBLIC)
                .returns(POLYGLOT_VALUE)
                .build(((aThis, methodParameters) ->
                    thisType.getStaticField("graalpyInternalValue", POLYGLOT_VALUE).returning())
                ));

            // implement static factory
            builder.addMethod(MethodDef.builder(FROM_POLYGLOT_VALUE)
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .addParameter(POLYGLOT_VALUE)
                .returns(thisType)
                .build(((aThis, methodParameters) ->
                    thisType.getStaticField("INSTANCE", thisType).returning()))
            );

            // Get the singleton instance
            builder.addMethod(MethodDef.builder("getInstance")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .returns(thisType)
                .build(((aThis, methodParameters) ->
                    thisType.getStaticField("INSTANCE", thisType).returning()))
            );

            List<MethodElement> methodsToBridge = scriptElement.getEnclosedElements(
                ElementQuery.ALL_METHODS
                    .onlyAccessible()
                    .onlyInstance()
                    .onlyDeclared()
                    .annotated(ann ->
                        ann.hasStereotype(Executable.class) ||
                            ann.hasAnnotation(AnnotationUtil.PRE_DESTROY) ||
                            ann.hasAnnotation(AnnotationUtil.POST_CONSTRUCT) ||
                            ann.hasStereotype(Around.class) ||
                            ann.hasDeclaredStereotype(AnnotationUtil.SCOPE) ||
                            ann.hasDeclaredStereotype(Bean.class)));

            for (MethodElement methodElement : methodsToBridge) {
                addBridgeMethod(
                    methodElement,
                    builder,
                    context,
                    false,
                    true,
                    addedMethodNames
                );
            }

            // Find injection fields (script attributes)
            List<PropertyElement> beanProperties = scriptElement.getBeanProperties();
            for (PropertyElement beanProperty : beanProperties) {
                if (beanProperty.hasStereotype(AnnotationUtil.INJECT)) {
                    // scripts rely on polyglot value; keep old behavior
                    addSetterScript(beanProperty, builder, pythonValue);
                }

                if (beanProperty.hasStereotype(Bean.class)) {
                    addGetterScript(beanProperty, builder, pythonValue);
                }
            }

        } catch (ProcessingException e) {
            throw e;
        } catch (Exception e) {
            context.fail("Failed to generate stub for Python script [" + scriptElement.getSimpleName() + "]: " + e.getMessage(), null);
        }
    }

    static TypeDef erasedType(ClassElement t) {
        var parameterType = !t.getTypeArguments().isEmpty() ? ClassTypeDef.of(t.getName()) : TypeDef.of(t);
        return parameterType;
    }

    static void coerceParameterToPolyglotValue(
        TypedElement param,
        List<ExpressionDef> parameters,
        VariableDef.MethodParameter methodParam) {
        ClassElement genericType = param.getGenericType();
        if (genericType.isAssignable(Map.class) && genericType.getTypeArguments().get("V") instanceof PythonClassElement) {
            parameters.add(RUNTIME_UTIL.invokeStatic("coerceMap", TypeDef.of(Map.class), methodParam));
        } else if (genericType.isAssignable(List.class) && genericType.getTypeArguments().get("E") instanceof PythonClassElement) {
            parameters.add(RUNTIME_UTIL.invokeStatic("coerceList", TypeDef.of(List.class), methodParam));
        } else if (genericType instanceof PythonClassElement pce) {
            boolean isPooled = pce.hasStereotype("io.micronaut.context.python.scope.ContextPooled");
            if (isPooled) {
                // For pooled Python classes, inject the Java stub (host object), not the polyglot Value.
                // This ensures method calls go through the Java wrapper which proxies to pooled contexts.
                parameters.add(methodParam);
            } else if (param.hasAnnotation("jakarta.annotation.Nullable")) {
                parameters.add(methodParam.isNull().doIfElse(
                        ExpressionDef.nullValue(),
                        methodParam.invoke(AS_POLYGLOT_VALUE, POLYGLOT_VALUE)
                ));
            } else {
                parameters.add(methodParam.invoke(AS_POLYGLOT_VALUE, POLYGLOT_VALUE));
            }
        } else {
            parameters.add(methodParam);
        }
    }

    private static ExpressionDef coerceTypedElementToPolyglotValue(TypedElement element, ExpressionDef expr) {
        ClassElement genericType = element.getGenericType();
        if (genericType.isAssignable(Map.class) && genericType.getTypeArguments().get("V") instanceof PythonClassElement) {
            return RUNTIME_UTIL.invokeStatic("coerceMap", TypeDef.of(Map.class), expr);
        } else if (genericType.isAssignable(List.class) && genericType.getTypeArguments().get("E") instanceof PythonClassElement) {
            return RUNTIME_UTIL.invokeStatic("coerceList", TypeDef.of(List.class), expr);
        } else if (genericType instanceof PythonClassElement) {
            return expr.isNull().doIfElse(
                ExpressionDef.nullValue(),
                expr.invoke(AS_POLYGLOT_VALUE, POLYGLOT_VALUE)
            );
        } else {
            return expr;
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

    private void addBridgeMethod(
        MethodElement methodElement,
        ClassDef.ClassDefBuilder builder,
        VisitorContext visitorContext,
        boolean isJunit5Test,
        boolean isScript,
        Set<String> addedMethodNames) {
        String pythonFunctionName = methodElement.getName();
        String key = methodElement.getDescription(true);
        // Check if method name has already been added to avoid duplicates
        if (addedMethodNames.contains(key)) {
            return;
        }

        addedMethodNames.add(key);

        if (methodElement.hasDeclaredStereotype(Bean.class)) {
            // verify return type exists
            ClassElement genericReturnType = methodElement.getGenericReturnType();
            if (genericReturnType.isVoid()) {
                throw new ProcessingException(methodElement, "Factory methods declared with @Bean must specify a return type. For example: @Bean\n" +
                    "    def foo(self) -> Foo:");
            }

            String preDestroy = methodElement.stringValue(Bean.class, "preDestroy").orElse(null);
            if (preDestroy != null && genericReturnType instanceof AbstractPythonClassElement) {
                StubEntry stubEntry = this.classBuilders.get(genericReturnType.getName());
                if (stubEntry != null) {
                    MethodElement preDestroyMethod = stubEntry.originatingElement
                        .findMethod(preDestroy).orElse(null);
                    if (preDestroyMethod == null) {
                        throw new ProcessingException(methodElement, "Pre-destroy method referenced [" + preDestroy + "] not found in " + stubEntry.originatingElement.getName());
                    } else {
                        addBridgeMethod(
                            preDestroyMethod,
                            stubEntry.builder,
                            visitorContext,
                            false,
                            false, stubEntry.bridgedMethods
                        );
                    }
                }
            }
        }

        MethodDef.MethodDefBuilder methodBuilder = MethodDef.builder(pythonFunctionName)
            .addModifiers(Modifier.PUBLIC)
            .returns(isJunit5Test ? TypeDef.Primitive.VOID : TypeDef.of(methodElement.getGenericReturnType()));

        copyAnnotations(methodElement, methodBuilder, ANNOTATION_PACKAGES_TO_COPY, visitorContext);
        @NonNull ParameterElement[] parameters = methodElement.getParameters();
        for (@NonNull ParameterElement parameter : parameters) {
            ParameterDef parameterDef = ParameterDef
                .builder(parameter.getName(), TypeDef.of(parameter.getGenericType())).build();
            methodBuilder.addParameter(parameterDef);
        }

        builder.addMethod(methodBuilder
            .build(((aThis, methodParameters) -> {
                List<ExpressionDef> parameterExpressions = new ArrayList<>();
                for (int i = 0; i < parameters.length; i++) {
                    @NonNull ParameterElement parameter = parameters[i];
                    VariableDef.MethodParameter methodParameter = methodParameters.get(i);
                    coerceParameterToPolyglotValue(parameter, parameterExpressions, methodParameter);
                }

                // Get the return type to determine appropriate conversion method
                var returnType = methodElement.getGenericReturnType();
                var invokedValue = aThis.invoke(AS_POLYGLOT_VALUE, POLYGLOT_VALUE)
                    .invoke("getMember", POLYGLOT_VALUE, ExpressionDef.constant(pythonFunctionName))
                    .invoke("execute", POLYGLOT_VALUE, parameterExpressions);

                if (isJunit5Test) {
                    return invokedValue;
                } else {
                    ExpressionDef expressionDef = handleReturnType(allClasses, returnType, invokedValue);
                    if (returnType.isVoid()) {
                        return invokedValue;
                    } else {
                        return expressionDef.returning();
                    }
                }
            })));
    }

    private static String beanGetterName(String name, ClassElement type) {
        return "get" + NameUtils.capitalize(name);
    }

    private static String beanSetterName(String name) {
        return "set" + NameUtils.capitalize(name);
    }

    private void addGetterPojo(PropertyElement beanProperty, ClassDef.ClassDefBuilder builder, FieldDef field) {
        TypeDef propertyType = TypeDef.of(beanProperty.getType());
        Optional<MethodElement> rm = beanProperty.getReadMethod();
        boolean isSynthetic = rm.map(MethodElement::isSynthetic).orElse(true);
        String getterName = isSynthetic ? beanGetterName(beanProperty.getName(), beanProperty.getType()) :
            rm.get().getName();

        MethodDef.MethodDefBuilder getterBuilder = MethodDef
            .builder(getterName)
            .addModifiers(Modifier.PUBLIC)
            .returns(propertyType);

        builder.addMethod(getterBuilder.build(((aThis, methodParameters) -> aThis.field(field).returning())));
    }

    private static void addSetterPojo(PropertyElement beanProperty, ClassDef.ClassDefBuilder builder, FieldDef field) {
        TypeDef returnType = TypeDef.VOID;
        Optional<MethodElement> wm = beanProperty.getWriteMethod();
        boolean isSynthetic = wm.map(MethodElement::isSynthetic).orElse(true);
        String setterName = isSynthetic ? beanSetterName(beanProperty.getName()) :
                                            wm.get().getName();
        MethodDef.MethodDefBuilder propertySetter = MethodDef
            .builder(setterName)
            .addModifiers(Modifier.PUBLIC)
            .returns(returnType);

        propertySetter.addParameter(TypeDef.of(beanProperty.getType()));

        builder.addMethod(propertySetter.build(((aThis, methodParameters) -> aThis.field(field).assign(methodParameters.getFirst()))));
    }

    private void addGetterDynamic(PropertyElement beanProperty, ClassDef.ClassDefBuilder builder) {
        TypeDef propertyType = TypeDef.of(beanProperty.getType());
        String getterName = beanGetterName(beanProperty.getName(), beanProperty.getType());
        MethodDef.MethodDefBuilder getterBuilder = MethodDef
            .builder(getterName)
            .addModifiers(Modifier.PUBLIC)
            .returns(propertyType);

        builder.addMethod(getterBuilder.build(((aThis, methodParameters) -> {
            var invokedValue = aThis.invoke(AS_POLYGLOT_VALUE, POLYGLOT_VALUE).invoke(
                "getMember",
                POLYGLOT_VALUE,
                ExpressionDef.constant(beanProperty.getName())
            );
            return handleReturnType(allClasses, beanProperty.getType(), invokedValue).returning();
        })));
    }

    private void addNamedGetterDynamic(PropertyElement beanProperty, ClassDef.ClassDefBuilder builder) {
        TypeDef propertyType = TypeDef.of(beanProperty.getType());
        String getterName = beanProperty.getReadMethod().map(MethodElement::getName).orElse(beanProperty.getName());
        MethodDef.MethodDefBuilder getterBuilder = MethodDef
            .builder(getterName)
            .addModifiers(Modifier.PUBLIC)
            .returns(propertyType);

        builder.addMethod(getterBuilder.build(((aThis, methodParameters) -> {
            var invokedValue = aThis.invoke(AS_POLYGLOT_VALUE, POLYGLOT_VALUE).invoke(
                "getMember",
                POLYGLOT_VALUE,
                ExpressionDef.constant(beanProperty.getName())
            );
            return handleReturnType(allClasses, beanProperty.getType(), invokedValue).returning();
        })));
    }

    private void addSetterDynamic(PropertyElement beanProperty, ClassDef.ClassDefBuilder builder) {
        TypeDef returnType = TypeDef.VOID;
        String setterName = beanSetterName(beanProperty.getName());
        MethodDef.MethodDefBuilder propertySetter = MethodDef
            .builder(setterName)
            .addModifiers(Modifier.PUBLIC)
            .returns(returnType);

        propertySetter.addParameter(TypeDef.of(beanProperty.getType()));

        builder.addMethod(propertySetter.build(((aThis, methodParameters) -> {
            List<ExpressionDef> parameters = new ArrayList<>();
            parameters.add(ExpressionDef.constant(beanProperty.getName()));
            coerceParameterToPolyglotValue(
                beanProperty,
                parameters,
                methodParameters.getFirst()
            );
            return aThis.invoke(AS_POLYGLOT_VALUE, POLYGLOT_VALUE).invoke(
                "putMember",
                TypeDef.VOID,
                parameters
            );
        })));
    }

    private void addNamedSetterDynamic(PropertyElement beanProperty, ClassDef.ClassDefBuilder builder) {
        TypeDef returnType = TypeDef.VOID;
        String setterName = beanProperty.getWriteMethod().map(MethodElement::getName).orElse(beanProperty.getName());
        MethodDef.MethodDefBuilder propertySetter = MethodDef
            .builder(setterName)
            .addModifiers(Modifier.PUBLIC)
            .returns(returnType);

        propertySetter.addParameter(TypeDef.of(beanProperty.getType()));

        builder.addMethod(propertySetter.build(((aThis, methodParameters) -> {
            List<ExpressionDef> parameters = new ArrayList<>();
            parameters.add(ExpressionDef.constant(beanProperty.getName()));
            coerceParameterToPolyglotValue(
                beanProperty,
                parameters,
                methodParameters.getFirst()
            );
            return aThis.invoke(AS_POLYGLOT_VALUE, POLYGLOT_VALUE).invoke(
                "putMember",
                TypeDef.VOID,
                parameters
            );
        })));
    }

    // Script-specific accessors still use polyglot value
    private void addGetterScript(PropertyElement beanProperty, ClassDef.ClassDefBuilder builder, FieldDef pythonValue) {
        TypeDef propertyType = TypeDef.of(beanProperty.getType());
        String getterName = beanProperty.getReadMethod().map(MethodElement::getName).orElse(beanProperty.getName());
        MethodDef.MethodDefBuilder getterBuilder = MethodDef
            .builder(getterName)
            .addModifiers(Modifier.PUBLIC)
            .returns(propertyType);

        builder.addMethod(getterBuilder.build(((aThis, methodParameters) -> {
            var invokedValue = aThis.field(pythonValue).invoke(
                "getMember",
                POLYGLOT_VALUE,
                ExpressionDef.constant(beanProperty.getName())
            );
            return handleReturnType(allClasses, beanProperty.getType(), invokedValue).returning();
        })));
    }

    private static void addSetterScript(PropertyElement beanProperty, ClassDef.ClassDefBuilder builder, FieldDef pythonValue) {
        TypeDef returnType = beanProperty.getWriteMethod()
            .map(MethodElement::getReturnType)
            .map(TypeDef::of).orElse(TypeDef.VOID);
        String setterName = beanProperty.getWriteMethod().map(MethodElement::getName).orElse(beanProperty.getName());
        MethodDef.MethodDefBuilder propertySetter = MethodDef
            .builder(setterName)
            .addModifiers(Modifier.PUBLIC)
            .returns(returnType);

        propertySetter.addParameter(TypeDef.of(beanProperty.getType()));

        builder.addMethod(propertySetter.build(((aThis, methodParameters) -> {
            List<ExpressionDef> parameters = new ArrayList<>();
            parameters.add(ExpressionDef.constant(beanProperty.getName()));
            coerceParameterToPolyglotValue(
                beanProperty,
                parameters,
                methodParameters.getFirst()
            );
            ExpressionDef.InvokeInstanceMethod result = aThis.field(pythonValue).invoke(
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

    static ExpressionDef handleReturnType(Map<String, ClassElement> allClasses, ClassElement returnType, ExpressionDef invokedValue) {
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
                    invokedValue.invoke("asInt", TypeDef.Primitive.INT);
                case "java.lang.Boolean" ->
                    invokedValue.invoke("asBoolean", TypeDef.Primitive.BOOLEAN);
                case "java.lang.Double" ->
                    invokedValue.invoke("asDouble", TypeDef.Primitive.DOUBLE);
                case "java.lang.Float" ->
                    invokedValue.invoke("asFloat", TypeDef.Primitive.FLOAT);
                case "java.lang.Long" ->
                    invokedValue.invoke("asLong", TypeDef.Primitive.LONG);
                case "java.lang.Short" ->
                    invokedValue.invoke("asShort", TypeDef.Primitive.SHORT);
                case "java.lang.Byte" ->
                    invokedValue.invoke("asByte", TypeDef.Primitive.BYTE);
                case "java.lang.Character" -> invokedValue.invoke("asString", ClassTypeDef.STRING)
                    .invoke("charAt", TypeDef.Primitive.CHAR, ExpressionDef.constant(0));
                case "java.lang.String" ->
                    invokedValue.invoke("asString", ClassTypeDef.STRING);
                default -> {
                    // Check for collection types
                    if (returnType.isAssignable(List.class)) {
                        ClassElement componentType = returnType.getFirstTypeArgument().orElse(null);
                        ExpressionDef genericType = toClassExpression(componentType);
                        yield RUNTIME_UTIL
                            .invokeStatic("convertList", ClassTypeDef.of(List.class),
                                invokedValue, genericType);
                    } else if (returnType.isAssignable(Map.class)) {
                        Map<String, ClassElement> typeArguments = returnType.getTypeArguments();
                        ExpressionDef keyType = toClassExpression(typeArguments.get("K"));
                        ExpressionDef valueType = toClassExpression(typeArguments.get("V"));
                        yield RUNTIME_UTIL
                            .invokeStatic("convertMap", ClassTypeDef.of(Map.class),
                                invokedValue, keyType, valueType);
                    } else if (returnType.isAssignable(Set.class)) {
                        ClassElement componentType = returnType.getFirstTypeArgument().orElse(null);
                        ExpressionDef genericType = toClassExpression(componentType);

                        yield RUNTIME_UTIL
                            .invokeStatic("convertSet", ClassTypeDef.of(Set.class),
                                invokedValue, genericType);
                    } else if (returnType.isAssignable(java.util.Optional.class)) {
                        ClassElement componentType = returnType.getFirstTypeArgument().orElse(null);
                        ExpressionDef genericType = toClassExpression(componentType);

                        yield RUNTIME_UTIL
                            .invokeStatic("convertOptional", ClassTypeDef.of(java.util.Optional.class),
                                invokedValue, genericType);
                    } else {
                        if (allClasses.containsKey(returnType.getName())) {
                            yield ClassTypeDef.of(returnType)
                                .invokeStatic(FROM_POLYGLOT_VALUE, POLYGLOT_VALUE, invokedValue);
                        } else {
                            yield RUNTIME_UTIL
                                .invokeStatic("convertValue", ClassTypeDef.OBJECT,
                                    invokedValue, ClassTypeDef.of(returnType.getRawClassElement().getName()).getStaticField("class", TypeDef.CLASS))
                                .cast(TypeDef.of(returnType));
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

    private void addCreatorFactoryMethod(MethodElement creatorMethod, ClassDef.ClassDefBuilder builder, ClassElement element) {
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

    private static ExpressionDef convertPrimitive(ClassElement returnType, ExpressionDef invokedValue) {
        String primitiveTypeName = returnType.getName();
        return switch (primitiveTypeName) {
            case "int", "java.lang.Integer" ->
                invokedValue.invoke("asInt", TypeDef.Primitive.INT);
            case "boolean", "java.lang.Boolean" ->
                invokedValue.invoke("asBoolean", TypeDef.Primitive.BOOLEAN);
            case "double", "java.lang.Double" ->
                invokedValue.invoke("asDouble", TypeDef.Primitive.DOUBLE);
            case "float", "java.lang.Float" ->
                invokedValue.invoke("asFloat", TypeDef.Primitive.FLOAT);
            case "long", "java.lang.Long" ->
                invokedValue.invoke("asLong", TypeDef.Primitive.LONG);
            case "short", "java.lang.Short" ->
                invokedValue.invoke("asShort", TypeDef.Primitive.SHORT);
            case "byte", "java.lang.Byte" ->
                invokedValue.invoke("asByte", TypeDef.Primitive.BYTE);
            case "char", "java.lang.Character" ->
                invokedValue.invoke("asString", ClassTypeDef.STRING)
                    .invoke("charAt", TypeDef.Primitive.CHAR, ExpressionDef.constant(0));
            default -> invokedValue.invoke("asString", ClassTypeDef.STRING);
        };
    }

    private ExpressionDef convertValueForType(ClassElement type, ExpressionDef member) {
        if (type.isPrimitive()) {
            return switch (type.getName()) {
                case "int" -> member.invoke("asInt", TypeDef.Primitive.INT);
                case "boolean" -> member.invoke("asBoolean", TypeDef.Primitive.BOOLEAN);
                case "double" -> member.invoke("asDouble", TypeDef.Primitive.DOUBLE);
                case "float" -> member.invoke("asFloat", TypeDef.Primitive.FLOAT);
                case "long" -> member.invoke("asLong", TypeDef.Primitive.LONG);
                case "short" -> member.invoke("asShort", TypeDef.Primitive.SHORT);
                case "byte" -> member.invoke("asByte", TypeDef.Primitive.BYTE);
                case "char" -> member.invoke("asString", ClassTypeDef.STRING).invoke("charAt", TypeDef.Primitive.CHAR, ExpressionDef.constant(0));
                default -> member.invoke("asString", ClassTypeDef.STRING);
            };
        } else {
            String referenceTypeName = type.getName();
            switch (referenceTypeName) {
                case "java.lang.Integer":
                    return member.invoke("asInt", TypeDef.Primitive.INT);
                case "java.lang.Boolean":
                    return member.invoke("asBoolean", TypeDef.Primitive.BOOLEAN);
                case "java.lang.Double":
                    return member.invoke("asDouble", TypeDef.Primitive.DOUBLE);
                case "java.lang.Float":
                    return member.invoke("asFloat", TypeDef.Primitive.FLOAT);
                case "java.lang.Long":
                    return member.invoke("asLong", TypeDef.Primitive.LONG);
                case "java.lang.Short":
                    return member.invoke("asShort", TypeDef.Primitive.SHORT);
                case "java.lang.Byte":
                    return member.invoke("asByte", TypeDef.Primitive.BYTE);
                case "java.lang.Character":
                    return member.invoke("asString", ClassTypeDef.STRING).invoke("charAt", TypeDef.Primitive.CHAR, ExpressionDef.constant(0));
                case "java.lang.String":
                    return member.invoke("asString", ClassTypeDef.STRING);
                default:
                    if (type.isAssignable(List.class)) {
                        ClassElement componentType = type.getFirstTypeArgument().orElse(null);
                        ExpressionDef genericType = toClassExpression(componentType);
                        return RUNTIME_UTIL.invokeStatic("convertList", ClassTypeDef.of(List.class), member, genericType);
                    } else if (type.isAssignable(Map.class)) {
                        Map<String, ClassElement> typeArguments = type.getTypeArguments();
                        ExpressionDef keyType = toClassExpression(typeArguments.get("K"));
                        ExpressionDef valueType = toClassExpression(typeArguments.get("V"));
                        return RUNTIME_UTIL.invokeStatic("convertMap", ClassTypeDef.of(Map.class), member, keyType, valueType);
                    } else if (type.isAssignable(Set.class)) {
                        ClassElement componentType = type.getFirstTypeArgument().orElse(null);
                        ExpressionDef genericType = toClassExpression(componentType);
                        return RUNTIME_UTIL.invokeStatic("convertSet", ClassTypeDef.of(Set.class), member, genericType);
                    } else if (type.isAssignable(java.util.Optional.class)) {
                        ClassElement componentType = type.getFirstTypeArgument().orElse(null);
                        ExpressionDef genericType = toClassExpression(componentType);
                        return RUNTIME_UTIL.invokeStatic("convertOptional", ClassTypeDef.of(java.util.Optional.class), member, genericType);
                    } else if (allClasses.containsKey(type.getName())) {
                        return ClassTypeDef.of(type).invokeStatic(FROM_POLYGLOT_VALUE, POLYGLOT_VALUE, member);
                    } else {
                        return RUNTIME_UTIL.invokeStatic("convertValue", ClassTypeDef.OBJECT, member, ClassTypeDef.of(type.getRawClassElement().getName()).getStaticField("class", TypeDef.CLASS)).cast(TypeDef.of(type));
                    }
            }
        }
    }

    @Override
    public VisitorKind getVisitorKind() {
        return VisitorKind.ISOLATING;
    }

    record StubEntry(
        ClassDef.ClassDefBuilder builder,
        ClassElement originatingElement,
        Map<String, FieldDef> propertyFields,
        Set<String> bridgedMethods) {
        public StubEntry(ClassDef.ClassDefBuilder builder, ClassElement originatingElement, Map<String, FieldDef> propertyFields) {
            this(builder, originatingElement, propertyFields, new HashSet<>());
        }
    }
}
