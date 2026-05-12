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
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
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
import io.micronaut.python.processing.util.GraalPyUtil;
import io.micronaut.python.processing.visitor.DecoratorDef;
import io.micronaut.python.processing.visitor.PythonVisitorContext;
import io.micronaut.python.processing.visitor.TypeRef;
import io.micronaut.sourcegen.model.AbstractElementBuilder;
import io.micronaut.sourcegen.model.AnnotationObjectDef;
import io.micronaut.sourcegen.model.AnnotationDef;
import org.jspecify.annotations.Nullable;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;

import io.micronaut.context.annotation.Executable;
import io.micronaut.core.annotation.AnnotationUtil;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.ElementQuery;
import io.micronaut.inject.ast.GenericPlaceholderElement;
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
import io.micronaut.sourcegen.model.InterfaceDef;
import io.micronaut.sourcegen.model.MethodDef;
import io.micronaut.sourcegen.model.ObjectDef;
import io.micronaut.sourcegen.model.ParameterDef;
import io.micronaut.sourcegen.model.StatementDef;
import io.micronaut.sourcegen.model.TypeDef;
import io.micronaut.sourcegen.model.VariableDef;
import io.micronaut.python.processing.util.ObjectHelper;

public class PythonStubGenerator implements TypeElementVisitor<Object, Object> {

    public static final TypeDef POLYGLOT_VALUE = TypeDef.of(Value.class);
    public static final TypeDef POLYGLOT_CONTEXT = TypeDef.of(Context.class);
    public static final VariableDef.StaticField CLASS_OBJECT = ClassTypeDef.of(Object.class).getStaticField("class", TypeDef.CLASS);
    public static final String AS_POLYGLOT_VALUE = "asPolyglotValue";
    public static final String FROM_POLYGLOT_VALUE = "fromPolyglotValue";
    public static final ClassTypeDef RUNTIME_UTIL = ClassTypeDef.of("io.micronaut.context.python.GraalPyRuntimeUtil");
    public static final ClassTypeDef CONTEXT_HOLDER = ClassTypeDef.of("io.micronaut.context.python.ContextHolder");
    public static final String GENERATOR_NAME = "python";
    private static final String ANN_CONFIGURATION_BUILDER = "io.micronaut.context.annotation.ConfigurationBuilder";
    private static final Set<String> ANNOTATION_PACKAGES_TO_COPY = Set.of("org.junit.jupiter.api", "io.micronaut.test.extensions.junit5.annotation");
    public static final String JUNIT_TEST = "org.junit.jupiter.api.Test";
    public static final String ANN_JSON_PROPERTY = "com.fasterxml.jackson.annotation.JsonProperty";
    public static final String ANN_JSON_CREATOR = "com.fasterxml.jackson.annotation.JsonCreator";

    private final Map<String, StubEntry> classBuilders = new LinkedHashMap<>();
    private final Map<String, InterfaceEntry> interfaceDefs = new LinkedHashMap<>();
    private final Map<String, AnnotationEntry> annotationDefs = new LinkedHashMap<>();
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
                for (InterfaceEntry entry : interfaceDefs.values()) {
                    sourceGenerator.write(entry.interfaceDef, visitorContext, entry.originatingElement);
                }
                for (AnnotationEntry entry : annotationDefs.values()) {
                    if (entry.originatingElement == null) {
                        sourceGenerator.write(entry.annotationDef, visitorContext);
                    } else {
                        sourceGenerator.write(entry.annotationDef, visitorContext, entry.originatingElement);
                    }
                }
            }
        } finally {
            classBuilders.clear();
            interfaceDefs.clear();
            annotationDefs.clear();
        }
    }

    @Override
    public void start(VisitorContext visitorContext) {
        if (visitorContext instanceof PythonVisitorContext pythonVisitorContext) {
            this.allClasses = pythonVisitorContext.getProcessingEnvironment().classes();
            pythonVisitorContext
                .getProcessingEnvironment()
                .environment()
                .decorators()
                .values()
                .stream()
                .filter(decoratorDef -> pythonVisitorContext.getClassElement(decoratorDef.annotationName()).isEmpty())
                .forEach(decoratorDef -> annotationDefs.putIfAbsent(
                    decoratorDef.annotationName(),
                    new AnnotationEntry(generateAnnotationStub(decoratorDef, pythonVisitorContext), null)
                ));
        }
    }

    @Override
    public void visitClass(ClassElement element, VisitorContext context) {
        if (context instanceof PythonVisitorContext pythonVisitorContext) {

            if (element instanceof PythonScriptElement scriptElement) {
                DecoratorDef decoratorDef = findScriptDecorator(scriptElement, pythonVisitorContext);
                if (decoratorDef != null) {
                    return;
                }
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
                    String pythonSimpleName = pythonSimpleName(classElement);
                    boolean isAopProxy = classElement.hasStereotype(InterceptorBinding.class);
                    boolean isDeclaredBean = BeanDefinitionCreatorFactory.isDeclaredBeanInMetadata(classElement) || isAopProxy;
                    Collection<ClassElement> interfaces = classElement.getInterfaces();

                    if (classElement.isInterface()) {
                        if (interfaceDefs.containsKey(classElement.getName())) {
                            return;
                        }
                        interfaceDefs.put(classElement.getName(), new InterfaceEntry(buildInterfaceDef(classElement, typeName, interfaces), classElement));
                        return;
                    }

                    var builder = ClassDef.builder(typeName)
                        .addModifiers(Modifier.PUBLIC);
                    builder.addAnnotation(Vetoed.class);

                    copyAnnotations(element, builder, ANNOTATION_PACKAGES_TO_COPY, context);
                    builder.addSuperinterface(ClassTypeDef.of("io.micronaut.context.python.ValueCoercible"));

                    // Check if this class extends another PythonClassElement
                    ClassElement superType = element.getSuperType().orElse(null);
                    boolean extendsPythonClass = superType instanceof AbstractPythonClassElement;
                    boolean extendsHostThrowable = superType != null
                        && !extendsPythonClass
                        && superType.isAssignable(Throwable.class.getName());
                    if (extendsPythonClass || extendsHostThrowable) {
                        builder.superclass(ClassTypeDef.of(superType.getName()));
                    }

                    boolean isIntrospectedBean = element.hasStereotype(Introspected.class);
                    final boolean isIntroductionBean = element.hasStereotype(Introduction.class);
                    boolean isJunit5Test = element.getEnclosedElement(ElementQuery.ALL_METHODS.onlyInstance().annotated(ann -> ann.hasDeclaredAnnotation(JUNIT_TEST))).isPresent();
                    boolean isConfigurationBuilderType = isConfigurationBuilderType(element);

                    List<PropertyElement> beanProperties = element.getBeanProperties();
                    Map<String, FieldDef> propertyFields = new LinkedHashMap<>();
                    if (isIntrospectedBean) {
                        for (PropertyElement beanProperty : beanProperties) {
                            FieldDef field = FieldDef.builder(beanProperty.getName())
                                .ofType(TypeDef.of(beanProperty.getType()))
                                .addModifiers(Modifier.PUBLIC)
                                .build();
                            builder.addField(field);
                            propertyFields.put(beanProperty.getName(), field);
                        }
                    }

                    FieldDef pythonValue = null;
                    if (!extendsPythonClass) {
                        FieldDef.FieldDefBuilder pythonValueBuilder = FieldDef.builder("graalpyInternalValue")
                            .ofType(POLYGLOT_VALUE)
                            .addModifiers(Modifier.PROTECTED);
                        if (!isIntrospectedBean && !isJunit5Test) {
                            pythonValueBuilder.addModifiers(Modifier.FINAL);
                        }
                        pythonValue = pythonValueBuilder.build();
                        builder.addField(pythonValue);
                    }

                    StubEntry stubEntry = new StubEntry(builder, classElement, propertyFields);
                    classBuilders.put(classElement.getName(), stubEntry);

                    // Track method names that have been added to avoid duplicates
                    Set<String> addedMethodNames = stubEntry.bridgedMethods();

                    for (ClassElement anInterface : interfaces) {
                        TypeDef interfaceTypeDef = parameterizedTypeDef(anInterface);
                        builder.addSuperinterface(interfaceTypeDef);
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

                    if (isDeclaredBean) {
                        if (isIntroductionBean) {
                            List<MethodElement> abstractDeclaredMethods = element.getEnclosedElements(
                                ElementQuery.ALL_METHODS
                                    .onlyAccessible()
                                    .onlyInstance()
                                    .onlyDeclared()
                                    .filter(MethodElement::isAbstract));
                            for (MethodElement method : abstractDeclaredMethods) {
                                addBridgeMethod(method, builder, context, false, false, addedMethodNames);
                            }
                        }
                    }

                    // Constructor from polyglot Value
                    final FieldDef pythonValueFinal = pythonValue;
                    if (!isIntrospectedBean && !extendsPythonClass && pythonValueFinal == null) {
                        throw new IllegalStateException("Expected graalpyInternalValue field to be initialized");
                    }

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
                                            } else if (pythonValueFinal != null) {
                                                assigns.add(aThis.field(pythonValueFinal).assign(val));
                                            }
                                            for (PropertyElement beanProperty : beanProperties) {
                                                FieldDef field = propertyFields.get(beanProperty.getName());
                                                if (field == null) {
                                                    continue;
                                                }
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
                                                return aThis.field(requireField(pythonValueFinal, "Expected graalpyInternalValue field")).assign(methodParameters.get(0));
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
                                    if (beanProperties.isEmpty() && pythonValueFinal != null) {
                                        ExpressionDef storedValue = aThis.field(requireField(pythonValueFinal, "Expected graalpyInternalValue field"));
                                        ExpressionDef newValue = CONTEXT_HOLDER.invokeStatic(
                                            isAbstractIntro ? "newIntroduction" : "newInstance",
                                            POLYGLOT_VALUE,
                                            List.of(
                                                ExpressionDef.constant(element.getPackageName()),
                                                ExpressionDef.constant(pythonSimpleName)
                                            )
                                        );
                                        return storedValue.isNonNull().doIfElse(
                                            storedValue.returning(),
                                            StatementDef.multi(
                                                aThis.field(requireField(pythonValueFinal, "Expected graalpyInternalValue field")).assign(newValue),
                                                aThis.field(requireField(pythonValueFinal, "Expected graalpyInternalValue field")).returning()
                                            )
                                        );
                                    }
                                    ExpressionDef reconstructedValue;
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
                                        reconstructedValue = CONTEXT_HOLDER.invokeStatic(
                                            "newInstance",
                                            POLYGLOT_VALUE,
                                            List.of(
                                                ExpressionDef.constant(element.getPackageName()),
                                                ExpressionDef.constant(pythonSimpleName),
                                                propsMap
                                            )
                                        );
                                    } else {
                                        // Constructor present: use positional args
                                        List<ExpressionDef> args = new ArrayList<>();
                                        args.add(ExpressionDef.constant(element.getPackageName()));
                                        args.add(ExpressionDef.constant(pythonSimpleName));
                                        for (PropertyElement beanProperty : beanProperties) {
                                            FieldDef field = propertyFields.get(beanProperty.getName());
                                            if (field == null) {
                                                continue;
                                            }
                                            ExpressionDef fieldRef = aThis.field(field);
                                            args.add(coerceTypedElementToPolyglotValue(beanProperty, fieldRef).cast(TypeDef.OBJECT));
                                        }
                                        reconstructedValue = CONTEXT_HOLDER.invokeStatic(isAbstractIntro ? "newIntroduction" : "newInstance", POLYGLOT_VALUE, args);
                                    }
                                    return reconstructedValue.returning();
                                })
                            ));
                    } else {
                        builder.addMethod(MethodDef.builder(AS_POLYGLOT_VALUE)
                            .addModifiers(Modifier.PUBLIC)
                            .returns(POLYGLOT_VALUE).build(((aThis, methodParameters) -> {
                                if (isJunit5Test) {
                                    ExpressionDef storedValue = aThis.field(requireField(pythonValueFinal, "Expected graalpyInternalValue field"));
                                    ExpressionDef newValue = CONTEXT_HOLDER.invokeStatic(
                                        "newInstance",
                                        POLYGLOT_VALUE,
                                        List.of(
                                            ExpressionDef.constant(element.getPackageName()),
                                            ExpressionDef.constant(pythonSimpleName)
                                        )
                                    );
                                    return storedValue.isNonNull().doIfElse(
                                        storedValue.returning(),
                                        StatementDef.multi(
                                            aThis.field(requireField(pythonValueFinal, "Expected graalpyInternalValue field")).assign(newValue),
                                            aThis.field(requireField(pythonValueFinal, "Expected graalpyInternalValue field")).returning()
                                        )
                                    );
                                }
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
                                return RUNTIME_UTIL.invokeStatic("isNone", TypeDef.Primitive.BOOLEAN, val)
                                    .isTrue()
                                    .doIfElse(
                                        ExpressionDef.nullValue().returning(),
                                        thisType.instantiate(methodParameters).returning()
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
                            var parameterType = constructorParameterType(parameter);
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
                            constructor.addModifiers(Modifier.PUBLIC).build(((aThis, methodParameters) -> {
                                if (isIntrospectedBean) {
                                    List<ExpressionDef> arguments = new ArrayList<>(List.of(
                                        ExpressionDef.constant(element.getPackageName()),
                                        ExpressionDef.constant(pythonSimpleName)
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
                                        ExpressionDef.constant(pythonSimpleName)
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
                                        return aThis.field(requireField(pythonValueFinal, "Expected graalpyInternalValue field")).assign(pythonInstance);
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
                        builder.addMethod(constructor.addModifiers(Modifier.PUBLIC).build(((aThis, methodParameters) -> {
                            if (isJunit5Test || isIntrospectedBean) {
                                return StatementDef.multi();
                            } else {
                                ExpressionDef pythonInstance = CONTEXT_HOLDER
                                    .invokeStatic(isAbstractIntroNoArg ? "newIntroduction" : "newInstance", POLYGLOT_VALUE,
                                        List.of(
                                            ExpressionDef.constant(element.getPackageName()),
                                            ExpressionDef.constant(pythonSimpleName)
                                        )
                                    );
                                if (extendsPythonClass) {
                                    return aThis.superRef().invokeConstructor(pythonInstance);
                                } else {
                                    return aThis.field(requireField(pythonValueFinal, "Expected graalpyInternalValue field")).assign(pythonInstance);
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
                                ann.hasAnnotation("io.micronaut.context.annotation.Mapper") ||
                                ann.hasAnnotation("io.micronaut.context.annotation.Mapper$Mapping") ||
                                ann.hasAnnotation(ANN_CONFIGURATION_BUILDER) ||
                                ann.hasAnnotation(AnnotationUtil.PRE_DESTROY) ||
                                ann.hasAnnotation(AnnotationUtil.POST_CONSTRUCT) ||
                                ann.hasStereotype(Around.class) ||
                                ann.hasStereotype(InterceptorBinding.class) ||
                                element.hasStereotype(Around.class) ||
                                ann.hasDeclaredStereotype(AnnotationUtil.SCOPE) ||
                                ann.hasDeclaredStereotype(Bean.class) ||
                                isConfigurationBuilderType));

                    boolean hasIntroductionAdviceMethod = false;
                    for (MethodElement methodElement : methodsToBridge) {
                        if (methodElement.hasStereotype(InterceptorBinding.class)) {
                            isAopProxy = true;
                        }
                        if (methodElement.hasStereotype(InterceptorBinding.class) ||
                            methodElement.hasAnnotation("io.micronaut.context.annotation.Mapper") ||
                            methodElement.hasAnnotation("io.micronaut.context.annotation.Mapper$Mapping")) {
                            hasIntroductionAdviceMethod = true;
                        }
                        addBridgeMethod(methodElement, builder, context, methodElement.hasDeclaredAnnotation(JUNIT_TEST), false, addedMethodNames);
                    }
                    if (hasIntroductionAdviceMethod) {
                        List<MethodElement> concreteDeclaredMethods = element.getEnclosedElements(
                            ElementQuery.ALL_METHODS
                                .onlyAccessible()
                                .onlyInstance()
                                .onlyDeclared()
                                .filter(method -> !method.isAbstract())
                        );
                        for (MethodElement methodElement : concreteDeclaredMethods) {
                            addBridgeMethod(methodElement, builder, context, methodElement.hasDeclaredAnnotation(JUNIT_TEST), false, addedMethodNames);
                        }
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
                                var targetValue = aThis.invoke(AS_POLYGLOT_VALUE, POLYGLOT_VALUE);
                                var targetContext = targetValue.invoke("getContext", POLYGLOT_CONTEXT);
                                List<ExpressionDef> parameters = new ArrayList<>();
                                parameters.add(ExpressionDef.constant(injectionMethod.getName()));

                                // Handle parameter conversion for Python classes
                                for (int i = 0; i < injectionMethod.getParameters().length; i++) {
                                    ParameterElement param = injectionMethod.getParameters()[i];
                                    VariableDef.MethodParameter methodParam = methodParameters.get(i);
                                    coerceParameterToPolyglotValue(param, parameters, methodParam, targetContext);
                                }

                                var invokedValue = targetValue.invoke("invokeMember", POLYGLOT_VALUE, parameters);

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

    private boolean isConfigurationBuilderType(ClassElement element) {
        for (ClassElement classElement : allClasses.values()) {
            for (PropertyElement propertyElement : classElement.getBeanProperties()) {
                if (propertyElement.hasAnnotation(ANN_CONFIGURATION_BUILDER) && sameErasedType(propertyElement.getType(), element)) {
                    return true;
                }
            }
            List<MethodElement> configurationBuilderMethods = classElement.getEnclosedElements(
                ElementQuery.ALL_METHODS.onlyDeclared().annotated(ann -> ann.hasAnnotation(ANN_CONFIGURATION_BUILDER))
            );
            for (MethodElement methodElement : configurationBuilderMethods) {
                for (ParameterElement parameter : methodElement.getParameters()) {
                    if (sameErasedType(parameter.getType(), element)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean sameErasedType(ClassElement left, ClassElement right) {
        return left.getName().equals(right.getName());
    }

    private static InterfaceDef buildInterfaceDef(AbstractPythonClassElement classElement,
                                                  String typeName,
                                                  Collection<ClassElement> interfaces) {
        InterfaceDef.InterfaceDefBuilder interfaceBuilder = InterfaceDef.builder(typeName)
            .addModifiers(Modifier.PUBLIC);
        for (GenericPlaceholderElement placeholder : classElement.getDeclaredGenericPlaceholders()) {
            interfaceBuilder.addTypeVariable(TypeDef.variable(placeholder.getVariableName()));
        }
        for (ClassElement anInterface : interfaces) {
            interfaceBuilder.addSuperinterface(parameterizedTypeDef(anInterface));
        }
        Set<String> addedMethodNames = new LinkedHashSet<>();
        for (MethodElement methodElement : classElement.getEnclosedElements(ElementQuery.ALL_METHODS.onlyDeclared().onlyAccessible().onlyInstance())) {
            String key = bridgeMethodKey(methodElement);
            if (!addedMethodNames.add(key)) {
                continue;
            }
            MethodDef.MethodDefBuilder methodBuilder = MethodDef.builder(methodElement.getName())
                .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
                .returns(TypeDef.of(methodElement.getGenericReturnType()));
            for (@NonNull ParameterElement parameter : methodElement.getParameters()) {
                methodBuilder.addParameter(ParameterDef
                    .builder(parameter.getName(), erasedType(parameter.getGenericType()))
                    .build());
            }
            interfaceBuilder.addMethod(methodBuilder.build());
        }
        return interfaceBuilder.build();
    }

    private static @Nullable DecoratorDef findScriptDecorator(PythonScriptElement scriptElement, PythonVisitorContext context) {
        return context
            .getProcessingEnvironment()
            .environment()
            .decorators()
            .get(scriptElement.getName());
    }

    private AnnotationObjectDef generateAnnotationStub(DecoratorDef decoratorDef, PythonVisitorContext visitorContext) {
        AnnotationObjectDef.AnnotationObjectDefBuilder builder = AnnotationObjectDef.builder(decoratorDef.annotationName())
            .addModifiers(Modifier.PUBLIC)
            .addAnnotation(AnnotationDef.builder(Retention.class)
                .addMember(AnnotationMetadata.VALUE_MEMBER, RetentionPolicy.RUNTIME)
                .build());

        for (DecoratorDef stereotype : decoratorDef.stereotypes()) {
            builder.addAnnotation(toAnnotationDef(stereotype, visitorContext));
        }

        Set<String> memberNames = new LinkedHashSet<>();
        memberNames.addAll(decoratorDef.memberTypes().keySet());
        memberNames.addAll(decoratorDef.members().keySet());
        for (String memberName : memberNames) {
            if (isDecoratorTargetMember(memberName, decoratorDef)) {
                continue;
            }
            TypeDef memberType = annotationMemberType(memberName, decoratorDef, visitorContext);
            AnnotationObjectDef.AnnotationMemberDefBuilder memberBuilder =
                AnnotationObjectDef.AnnotationMemberDef.builder(memberName, memberType);
            for (DecoratorDef memberDecorator : decoratorDef.memberDecorators().getOrDefault(memberName, List.of())) {
                memberBuilder.addAnnotation(toAnnotationDef(memberDecorator, visitorContext));
            }
            ExpressionDef defaultValue = annotationDefaultValue(decoratorDef.members().get(memberName), memberType, visitorContext);
            if (defaultValue != null) {
                memberBuilder.withDefault(defaultValue);
            }
            builder.addMember(memberBuilder.build());
        }

        return builder.build();
    }

    private static boolean isDecoratorTargetMember(String memberName, DecoratorDef decoratorDef) {
        Object memberValue = decoratorDef.members().get(memberName);
        return decoratorDef.memberTypes().size() <= 1
            && decoratorDef.members().size() <= 1
            && isNullAnnotationMemberValue(memberValue)
            && decoratorDef.memberTypes().get(memberName) == null
            && decoratorDef.memberDecorators().getOrDefault(memberName, List.of()).isEmpty()
            && Set.of("func", "cls", "bean").contains(memberName);
    }

    private static boolean isNullAnnotationMemberValue(@Nullable Object value) {
        return value == null || (value instanceof Value polyglotValue && polyglotValue.isNull());
    }

    private static TypeDef annotationMemberType(String memberName, DecoratorDef decoratorDef, PythonVisitorContext visitorContext) {
        TypeRef typeRef = decoratorDef.memberTypes().get(memberName);
        if (typeRef == null) {
            Object defaultValue = decoratorDef.members().get(memberName);
            if (defaultValue instanceof Integer) {
                return TypeDef.Primitive.INT;
            }
            if (defaultValue instanceof Long) {
                return TypeDef.Primitive.LONG;
            }
            if (defaultValue instanceof Float) {
                return TypeDef.Primitive.FLOAT;
            }
            if (defaultValue instanceof Double) {
                return TypeDef.Primitive.DOUBLE;
            }
            if (defaultValue instanceof Boolean) {
                return TypeDef.Primitive.BOOLEAN;
            }
            return TypeDef.STRING;
        }
        ClassElement classElement = GraalPyUtil.resolvePythonTypeToJava(typeRef, visitorContext, Map.of());
        Value defaultValue = decoratorDef.members().get(memberName);
        if (classElement.getName().equals(Object.class.getName()) && defaultValue != null && defaultValue.isString()) {
            return TypeDef.STRING;
        }
        return TypeDef.of(classElement);
    }

    private static @Nullable ExpressionDef annotationDefaultValue(
        Object defaultValue,
        TypeDef memberType,
        PythonVisitorContext visitorContext
    ) {
        if (defaultValue == null) {
            return null;
        }
        if (defaultValue instanceof Value polyglotValue) {
            if (polyglotValue.isNull()) {
                return null;
            }
            defaultValue = convertDefaultValue(polyglotValue, memberType, visitorContext);
        }
        if (defaultValue instanceof String stringValue && stringValue.startsWith("Name(")) {
            return null;
        }
        if (memberType instanceof TypeDef.Primitive) {
            return ExpressionDef.primitiveConstant(defaultValue);
        }
        return ExpressionDef.constant(defaultValue);
    }

    private static Object convertDefaultValue(Value value, TypeDef memberType, PythonVisitorContext visitorContext) {
        if (memberType.equals(TypeDef.STRING)) {
            return value.asString();
        }
        if (memberType.equals(TypeDef.Primitive.INT)) {
            return value.asInt();
        }
        if (memberType.equals(TypeDef.Primitive.LONG)) {
            return value.asLong();
        }
        if (memberType.equals(TypeDef.Primitive.FLOAT)) {
            return value.asFloat();
        }
        if (memberType.equals(TypeDef.Primitive.DOUBLE)) {
            return value.asDouble();
        }
        if (memberType.equals(TypeDef.Primitive.BOOLEAN)) {
            return value.asBoolean();
        }
        return GraalPyUtil.convertValueToJava(value, visitorContext);
    }

    private static AnnotationDef toAnnotationDef(DecoratorDef decoratorDef, PythonVisitorContext visitorContext) {
        Map<CharSequence, Object> members = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : decoratorDef.members().entrySet()) {
            String memberName = normalizeAnnotationMemberName(entry.getKey());
            Object value = convertAnnotationMemberValue(decoratorDef.annotationName(), memberName, entry.getValue(), visitorContext);
            if (value != null) {
                members.put(memberName, value);
            }
        }
        AnnotationValue<?> annotationValue = new AnnotationValue<>(decoratorDef.annotationName(), members);
        try {
            return AnnotationDef.of(annotationValue, visitorContext);
        } catch (RuntimeException e) {
            AnnotationDef.AnnotationDefBuilder builder = AnnotationDef.builder(ClassTypeDef.of(decoratorDef.annotationName()));
            members.forEach((memberName, value) -> builder.addMember(memberName.toString(), value));
            return builder.build();
        }
    }

    private static String normalizeAnnotationMemberName(Object memberName) {
        if (memberName instanceof Number number) {
            int index = number.intValue();
            return index == 0 ? AnnotationMetadata.VALUE_MEMBER : "arg" + index;
        }
        return memberName.toString();
    }

    private static @Nullable Object convertAnnotationMemberValue(
        String annotationName,
        String memberName,
        Object value,
        PythonVisitorContext visitorContext
    ) {
        ClassElement memberType = resolveAnnotationMemberType(annotationName, memberName, visitorContext);
        if (memberType != null) {
            return convertAnnotationMemberValue(value, memberType, visitorContext);
        }
        if (value instanceof Value polyglotValue) {
            return GraalPyUtil.convertValueToJava(polyglotValue, visitorContext);
        }
        if (value instanceof DecoratorDef nestedDecorator) {
            return toAnnotationDef(nestedDecorator, visitorContext);
        }
        return value;
    }

    private static Object convertAnnotationMemberValue(
        Object value,
        ClassElement memberType,
        PythonVisitorContext visitorContext
    ) {
        if (memberType.isArray()) {
            return convertAnnotationArrayMemberValue(value, memberType.fromArray(), visitorContext);
        }
        if (isEnumMember(memberType)) {
            return enumConstantName(value, visitorContext);
        }
        if (Class.class.getName().equals(memberType.getName())) {
            return annotationClassName(value, visitorContext);
        }
        if (value instanceof Value polyglotValue) {
            return GraalPyUtil.convertValueToJava(polyglotValue, memberType, visitorContext);
        }
        if (value instanceof DecoratorDef nestedDecorator) {
            return toAnnotationDef(nestedDecorator, visitorContext);
        }
        return value;
    }

    private static Object[] convertAnnotationArrayMemberValue(
        Object value,
        ClassElement componentType,
        PythonVisitorContext visitorContext
    ) {
        if (value instanceof Collection<?> collection) {
            return collection.stream()
                .map(element -> convertAnnotationMemberValue(element, componentType, visitorContext))
                .toArray();
        }
        if (value instanceof Object[] array) {
            Object[] converted = new Object[array.length];
            for (int i = 0; i < array.length; i++) {
                converted[i] = convertAnnotationMemberValue(array[i], componentType, visitorContext);
            }
            return converted;
        }
        if (value instanceof Value polyglotValue && polyglotValue.hasArrayElements()) {
            int size = Math.toIntExact(polyglotValue.getArraySize());
            Object[] converted = new Object[size];
            for (int i = 0; i < size; i++) {
                converted[i] = convertAnnotationMemberValue(polyglotValue.getArrayElement(i), componentType, visitorContext);
            }
            return converted;
        }
        return new Object[] { convertAnnotationMemberValue(value, componentType, visitorContext) };
    }

    private static boolean isEnumMember(ClassElement memberType) {
        return memberType.isEnum() || memberType.isAssignable(Enum.class);
    }

    private static String enumConstantName(Object value, PythonVisitorContext visitorContext) {
        if (value instanceof Enum<?> enumValue) {
            return enumValue.name();
        }
        if (value instanceof Value polyglotValue) {
            if (polyglotValue.isHostObject()) {
                Object hostObject = polyglotValue.asHostObject();
                if (hostObject instanceof Enum<?> enumValue) {
                    return enumValue.name();
                }
            }
            Object converted = GraalPyUtil.convertValueToJava(polyglotValue, visitorContext);
            return enumConstantName(converted, visitorContext);
        }
        String stringValue = value.toString();
        int lastDot = stringValue.lastIndexOf('.');
        return lastDot > -1 ? stringValue.substring(lastDot + 1) : stringValue;
    }

    private static String annotationClassName(Object value, PythonVisitorContext visitorContext) {
        if (value instanceof Class<?> classValue) {
            return classValue.getName();
        }
        if (value instanceof ClassElement classElement) {
            return classElement.getName();
        }
        if (value instanceof Value polyglotValue) {
            if (polyglotValue.isHostObject()) {
                Object hostObject = polyglotValue.asHostObject();
                if (hostObject instanceof Class<?> classValue) {
                    return classValue.getName();
                }
            }
            Object converted = GraalPyUtil.convertValueToJava(polyglotValue, visitorContext);
            return annotationClassName(converted, visitorContext);
        }
        return value.toString();
    }

    private static @Nullable ClassElement resolveAnnotationMemberType(
        String annotationName,
        String memberName,
        PythonVisitorContext visitorContext
    ) {
        ClassElement annotationType = visitorContext.getClassElement(annotationName).orElse(null);
        if (annotationType == null) {
            return null;
        }
        MethodElement annotationMember = annotationType
            .getEnclosedElement(ElementQuery.ALL_METHODS.onlyInstance().named(memberName))
            .orElse(null);
        return annotationMember == null ? null : annotationMember.getReturnType();
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
        if (t.isPrimitive() || t.isArray()) {
            return TypeDef.of(t);
        }
        String javaTypeName = javaTypeName(t);
        if (!t.getTypeArguments().isEmpty() || !javaTypeName.equals(t.getName())) {
            return ClassTypeDef.of(javaTypeName);
        }
        return TypeDef.of(t);
    }

    static TypeDef constructorParameterType(ParameterElement parameter) {
        ClassElement genericType = parameter.getGenericType();
        if (!genericType.getTypeArguments().isEmpty() && !(genericType instanceof AbstractPythonClassElement)) {
            return parameterizedTypeDef(genericType);
        }
        return erasedType(parameter.getType());
    }

    private static String javaTypeName(ClassElement t) {
        if (t instanceof AbstractPythonClassElement) {
            return t.getName();
        }
        return t.getName().replace('$', '.');
    }

    private static ClassTypeDef javaClassType(ClassElement t) {
        return ClassTypeDef.of(javaTypeName(t));
    }

    private static String pythonSimpleName(ClassElement element) {
        if (element instanceof AbstractPythonClassElement pythonClassElement) {
            return pythonClassElement.getNativeType().name().replace('$', '.');
        }
        return element.getSimpleName();
    }

    static void coerceParameterToPolyglotValue(
        TypedElement param,
        List<ExpressionDef> parameters,
        VariableDef.MethodParameter methodParam) {
        coerceParameterToPolyglotValue(param, parameters, methodParam, null);
    }

    static void coerceParameterToPolyglotValue(
        TypedElement param,
        List<ExpressionDef> parameters,
        VariableDef.MethodParameter methodParam,
        @Nullable ExpressionDef targetContext) {
        ExpressionDef parameter;
        ClassElement genericType = param.getGenericType();
        if (genericType.isAssignable(Map.class) && genericType.getTypeArguments().get("V") instanceof PythonClassElement) {
            parameter = RUNTIME_UTIL.invokeStatic("coerceMap", TypeDef.of(Map.class), methodParam);
        } else if (genericType.isAssignable(List.class) && genericType.getTypeArguments().get("E") instanceof PythonClassElement) {
            parameter = RUNTIME_UTIL.invokeStatic("coerceList", TypeDef.of(List.class), methodParam);
        } else {
            parameter = methodParam;
        }
        if (targetContext != null) {
            parameter = RUNTIME_UTIL.invokeStatic("coerceToContext", TypeDef.OBJECT, parameter, targetContext);
        }
        parameters.add(parameter);
    }

    private static ExpressionDef coerceTypedElementToPolyglotValue(TypedElement element, ExpressionDef expr) {
        ClassElement genericType = element.getGenericType();
        if (genericType.isAssignable(Map.class) && genericType.getTypeArguments().get("V") instanceof PythonClassElement) {
            return RUNTIME_UTIL.invokeStatic("coerceMap", TypeDef.of(Map.class), expr);
        } else if (genericType.isAssignable(List.class) && genericType.getTypeArguments().get("E") instanceof PythonClassElement) {
            return RUNTIME_UTIL.invokeStatic("coerceList", TypeDef.of(List.class), expr);
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
        String key = bridgeMethodKey(methodElement);
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
                .builder(parameter.getName(), erasedType(parameter.getGenericType())).build();
            methodBuilder.addParameter(parameterDef);
        }

        builder.addMethod(methodBuilder
            .build(((aThis, methodParameters) -> {
                var targetValue = aThis.invoke(AS_POLYGLOT_VALUE, POLYGLOT_VALUE);
                var targetContext = targetValue.invoke("getContext", POLYGLOT_CONTEXT);
                List<ExpressionDef> parameterExpressions = new ArrayList<>();
                for (int i = 0; i < parameters.length; i++) {
                    @NonNull ParameterElement parameter = parameters[i];
                    VariableDef.MethodParameter methodParameter = methodParameters.get(i);
                    coerceParameterToPolyglotValue(parameter, parameterExpressions, methodParameter, targetContext);
                }

                // Get the return type to determine appropriate conversion method
                var returnType = methodElement.getGenericReturnType();
                var invokedValue = RUNTIME_UTIL.invokeStatic(
                    "invokePythonMethod",
                    POLYGLOT_VALUE,
                    targetValue,
                    ExpressionDef.constant(pythonFunctionName),
                    TypeDef.OBJECT.array().instantiate(parameterExpressions)
                );

                if (isJunit5Test) {
                    return invokedValue;
                } else {
                    if (returnType.isVoid()) {
                        return invokedValue;
                    } else {
                        return returnConvertedValue(allClasses, returnType, invokedValue);
                    }
                }
            })));
    }

    private static String bridgeMethodKey(MethodElement methodElement) {
        StringBuilder key = new StringBuilder(methodElement.getName()).append('(');
        for (ParameterElement parameter : methodElement.getParameters()) {
            key.append(parameter.getType().getName()).append(';');
        }
        return key.append(')').toString();
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
            return returnConvertedValue(allClasses, beanProperty.getType(), invokedValue);
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
            return returnConvertedValue(allClasses, beanProperty.getType(), invokedValue);
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
            var targetValue = aThis.invoke(AS_POLYGLOT_VALUE, POLYGLOT_VALUE);
            var targetContext = targetValue.invoke("getContext", POLYGLOT_CONTEXT);
            List<ExpressionDef> parameters = new ArrayList<>();
            parameters.add(ExpressionDef.constant(beanProperty.getName()));
            coerceParameterToPolyglotValue(
                beanProperty,
                parameters,
                methodParameters.getFirst(),
                targetContext
            );
            return targetValue.invoke(
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
            var targetValue = aThis.invoke(AS_POLYGLOT_VALUE, POLYGLOT_VALUE);
            var targetContext = targetValue.invoke("getContext", POLYGLOT_CONTEXT);
            List<ExpressionDef> parameters = new ArrayList<>();
            parameters.add(ExpressionDef.constant(beanProperty.getName()));
            coerceParameterToPolyglotValue(
                beanProperty,
                parameters,
                methodParameters.getFirst(),
                targetContext
            );
            return targetValue.invoke(
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
            return returnConvertedValue(allClasses, beanProperty.getType(), invokedValue);
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
            var targetValue = aThis.field(pythonValue);
            var targetContext = targetValue.invoke("getContext", POLYGLOT_CONTEXT);
            List<ExpressionDef> parameters = new ArrayList<>();
            parameters.add(ExpressionDef.constant(beanProperty.getName()));
            coerceParameterToPolyglotValue(
                beanProperty,
                parameters,
                methodParameters.getFirst(),
                targetContext
            );
            ExpressionDef.InvokeInstanceMethod result = targetValue.invoke(
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
                    convertNullableValue(invokedValue, invokedValue.invoke("asInt", TypeDef.Primitive.INT));
                case "java.lang.Boolean" ->
                    convertNullableValue(invokedValue, invokedValue.invoke("asBoolean", TypeDef.Primitive.BOOLEAN));
                case "java.lang.Double" ->
                    convertNullableValue(invokedValue, invokedValue.invoke("asDouble", TypeDef.Primitive.DOUBLE));
                case "java.lang.Float" ->
                    convertNullableValue(invokedValue, invokedValue.invoke("asFloat", TypeDef.Primitive.FLOAT));
                case "java.lang.Long" ->
                    convertNullableValue(invokedValue, invokedValue.invoke("asLong", TypeDef.Primitive.LONG));
                case "java.lang.Short" ->
                    convertNullableValue(invokedValue, invokedValue.invoke("asShort", TypeDef.Primitive.SHORT));
                case "java.lang.Byte" ->
                    convertNullableValue(invokedValue, invokedValue.invoke("asByte", TypeDef.Primitive.BYTE));
                case "java.lang.Character" ->
                    convertNullableValue(invokedValue, invokedValue.invoke("asString", ClassTypeDef.STRING)
                        .invoke("charAt", TypeDef.Primitive.CHAR, ExpressionDef.constant(0)));
                case "java.lang.String" ->
                    convertNullableValue(invokedValue, invokedValue.invoke("asString", ClassTypeDef.STRING));
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
                        if (isGeneratedWrapperType(allClasses, returnType)) {
                            yield ClassTypeDef.of(returnType)
                                .invokeStatic(FROM_POLYGLOT_VALUE, POLYGLOT_VALUE, invokedValue);
                        } else {
                            yield RUNTIME_UTIL
                                .invokeStatic("convertValue", ClassTypeDef.OBJECT,
                                    invokedValue, javaClassType(returnType).getStaticField("class", TypeDef.CLASS))
                                .cast(erasedType(returnType));
                        }
                    }
                }
            };
        }
    }

    private static StatementDef returnConvertedValue(Map<String, ClassElement> allClasses, ClassElement returnType, ExpressionDef invokedValue) {
        return invokedValue.newLocal("pythonResult", result ->
            handleReturnType(allClasses, returnType, result).returning()
        );
    }

    private static ExpressionDef convertNullableValue(ExpressionDef value, ExpressionDef nonNullValue) {
        return RUNTIME_UTIL.invokeStatic("isNone", TypeDef.Primitive.BOOLEAN, value)
            .isTrue()
            .doIfElse(ExpressionDef.nullValue(), nonNullValue);
    }

    private static ExpressionDef toClassExpression(@Nullable ClassElement componentType) {
        ExpressionDef genericType;
        if (componentType == null) {
            genericType = CLASS_OBJECT;
        } else {
            genericType = javaClassType(componentType).getStaticField("class", TypeDef.CLASS);
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
                arguments.add(ExpressionDef.constant(pythonSimpleName(element)));
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

    private static FieldDef requireField(@Nullable FieldDef field, String message) {
        if (field == null) {
            throw new IllegalStateException(message);
        }
        return field;
    }

    private static boolean isGeneratedWrapperType(Map<String, ClassElement> allClasses, ClassElement type) {
        return allClasses.containsKey(type.getName()) && !type.isInterface();
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
                    return convertNullableValue(member, member.invoke("asInt", TypeDef.Primitive.INT));
                case "java.lang.Boolean":
                    return convertNullableValue(member, member.invoke("asBoolean", TypeDef.Primitive.BOOLEAN));
                case "java.lang.Double":
                    return convertNullableValue(member, member.invoke("asDouble", TypeDef.Primitive.DOUBLE));
                case "java.lang.Float":
                    return convertNullableValue(member, member.invoke("asFloat", TypeDef.Primitive.FLOAT));
                case "java.lang.Long":
                    return convertNullableValue(member, member.invoke("asLong", TypeDef.Primitive.LONG));
                case "java.lang.Short":
                    return convertNullableValue(member, member.invoke("asShort", TypeDef.Primitive.SHORT));
                case "java.lang.Byte":
                    return convertNullableValue(member, member.invoke("asByte", TypeDef.Primitive.BYTE));
                case "java.lang.Character":
                    return convertNullableValue(member, member.invoke("asString", ClassTypeDef.STRING).invoke("charAt", TypeDef.Primitive.CHAR, ExpressionDef.constant(0)));
                case "java.lang.String":
                    return convertNullableValue(member, member.invoke("asString", ClassTypeDef.STRING));
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
                    } else if (isGeneratedWrapperType(allClasses, type)) {
                        return ClassTypeDef.of(type).invokeStatic(FROM_POLYGLOT_VALUE, POLYGLOT_VALUE, member);
                    } else {
                        return RUNTIME_UTIL.invokeStatic("convertValue", ClassTypeDef.OBJECT, member, javaClassType(type).getStaticField("class", TypeDef.CLASS)).cast(erasedType(type));
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

    record AnnotationEntry(
        ObjectDef annotationDef,
        @Nullable Element originatingElement) {
    }

    record InterfaceEntry(
        InterfaceDef interfaceDef,
        Element originatingElement) {
    }
}
