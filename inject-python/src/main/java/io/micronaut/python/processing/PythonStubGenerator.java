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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

import javax.lang.model.element.Modifier;
import javax.lang.model.element.VariableElement;

import io.micronaut.annotation.processing.visitor.ElementProvider;
import io.micronaut.aop.Around;
import io.micronaut.aop.InterceptorBinding;
import io.micronaut.aop.Introduction;
import io.micronaut.context.annotation.Bean;
import io.micronaut.core.annotation.AnnotationClassValue;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Vetoed;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.expressions.parser.ast.util.TypeDescriptors;
import io.micronaut.inject.ast.Element;
import io.micronaut.inject.ast.EnumElement;
import io.micronaut.inject.ast.TypedElement;
import io.micronaut.inject.processing.BeanDefinitionCreatorFactory;
import io.micronaut.inject.processing.ProcessingException;
import io.micronaut.python.processing.util.GraalPyUtil;
import io.micronaut.python.processing.visitor.ArgumentDef;
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
import io.micronaut.inject.ast.ConstructorElement;
import io.micronaut.inject.ast.ElementQuery;
import io.micronaut.inject.ast.GenericPlaceholderElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.ParameterElement;
import io.micronaut.inject.ast.PropertyElement;
import io.micronaut.inject.ast.WildcardElement;
import io.micronaut.inject.visitor.TypeElementQuery;
import io.micronaut.inject.visitor.TypeElementVisitor;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.python.processing.visitor.AbstractPythonClassElement;
import io.micronaut.python.processing.visitor.PythonClassElement;
import io.micronaut.python.processing.visitor.PythonMethodElement;
import io.micronaut.python.processing.visitor.PythonScriptElement;
import io.micronaut.sourcegen.generator.SourceGenerator;
import io.micronaut.sourcegen.generator.SourceGenerators;
import io.micronaut.sourcegen.model.ClassDef;
import io.micronaut.sourcegen.model.ClassTypeDef;
import io.micronaut.sourcegen.model.EnumDef;
import io.micronaut.sourcegen.model.ExpressionDef;
import io.micronaut.sourcegen.model.FieldDef;
import io.micronaut.sourcegen.model.InterfaceDef;
import io.micronaut.sourcegen.model.MethodDef;
import io.micronaut.sourcegen.model.ObjectDef;
import io.micronaut.sourcegen.model.ObjectDefBuilder;
import io.micronaut.sourcegen.model.ParameterDef;
import io.micronaut.sourcegen.model.StatementDef;
import io.micronaut.sourcegen.model.TypeDef;
import io.micronaut.sourcegen.model.VariableDef;
import io.micronaut.python.processing.util.ObjectHelper;

/**
 * Generates Java stubs for Python classes, scripts, enums, interfaces, and annotations.
 */
@SuppressWarnings({"FileLength", "checkstyle:DeclarationOrder"})
public class PythonStubGenerator implements TypeElementVisitor<Object, Object> {

    public static final TypeDef POLYGLOT_VALUE = TypeDef.of(Value.class);
    public static final TypeDef POLYGLOT_CONTEXT = TypeDef.of(Context.class);
    public static final VariableDef.StaticField CLASS_OBJECT = ClassTypeDef.of(Object.class).getStaticField("class", TypeDef.CLASS);
    public static final String AS_POLYGLOT_VALUE = "asPolyglotValue";
    public static final String FROM_POLYGLOT_VALUE = "fromPolyglotValue";
    public static final ClassTypeDef RUNTIME_UTIL = ClassTypeDef.of("io.micronaut.context.python.GraalPyRuntimeUtil");
    public static final ClassTypeDef CONTEXT_HOLDER = ClassTypeDef.of("io.micronaut.context.python.ContextHolder");
    public static final ClassTypeDef POLYGLOT_VALUE_CONVERTER = ClassTypeDef.of("io.micronaut.context.python.PolyglotValueConverter");
    public static final String GENERATOR_NAME = "python";
    private static final String HTTP_RESPONSE = "io.micronaut.http.HttpResponse";
    private static final String PUBLISHER = "org.reactivestreams.Publisher";
    private static final String ANN_CONFIGURATION_BUILDER = "io.micronaut.context.annotation.ConfigurationBuilder";
    private static final String ANN_CONFIGURATION_INJECT = "io.micronaut.context.annotation.ConfigurationInject";
    private static final String ANN_CONFIGURATION_READER = "io.micronaut.context.annotation.ConfigurationReader";
    private static final String ANN_ANNOTATION_EXPRESSION_CONTEXT = "io.micronaut.context.annotation.AnnotationExpressionContext";
    private static final String ANN_CONSTRAINT = "jakarta.validation.Constraint";
    private static final String ANN_VALID = "jakarta.validation.Valid";
    private static final Set<String> ANNOTATION_PACKAGES_TO_COPY = Set.of("org.junit.jupiter.api", "io.micronaut.test.extensions.junit5.annotation");
    private static final Set<String> TYPE_ANNOTATIONS_TO_SKIP_IN_SOURCE = Set.of(
        "io.micronaut.core.annotation.NonNull",
        "io.micronaut.core.annotation.Nullable",
        "jakarta.annotation.Nonnull",
        "jakarta.annotation.Nullable",
        "javax.annotation.Nonnull",
        "javax.annotation.Nullable",
        "org.jspecify.annotations.NonNull",
        "org.jspecify.annotations.Nullable"
    );
    private final Map<String, StubEntry> classBuilders = new LinkedHashMap<>();
    private final Map<String, EnumEntry> enumDefs = new LinkedHashMap<>();
    private final Map<String, InterfaceEntry> interfaceDefs = new LinkedHashMap<>();
    private final Map<String, AnnotationEntry> annotationDefs = new LinkedHashMap<>();
    private Map<String, ClassElement> allClasses = Map.of();

    public static final String JUNIT_TEST = "org.junit.jupiter.api.Test";
    public static final String ANN_JSON_PROPERTY = "com.fasterxml.jackson.annotation.JsonProperty";
    public static final String ANN_JSON_CREATOR = "com.fasterxml.jackson.annotation.JsonCreator";

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
                for (EnumEntry entry : enumDefs.values()) {
                    sourceGenerator.write(entry.enumDef, visitorContext, entry.originatingElement);
                    sourceGenerator.write(entry.converterDef, visitorContext, entry.originatingElement);
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
            enumDefs.clear();
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
                .filter(decoratorDef -> shouldGenerateAnnotationStub(decoratorDef, pythonVisitorContext))
                .forEach(decoratorDef -> annotationDefs.putIfAbsent(
                    decoratorDef.annotationName(),
                    new AnnotationEntry(generateAnnotationStub(decoratorDef, pythonVisitorContext), null)
                ));
        }
    }

    private static boolean shouldGenerateAnnotationStub(DecoratorDef decoratorDef, PythonVisitorContext context) {
        var javaVisitorContext = context.getJavaVisitorContext();
        return javaVisitorContext == null || javaVisitorContext.getClassElement(decoratorDef.annotationName()).isEmpty();
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
                if (classElement.isEnum()) {
                    if (enumDefs.containsKey(classElement.getName())) {
                        return;
                    }
                    enumDefs.put(
                        classElement.getName(),
                        new EnumEntry(buildEnumDef(classElement, context), buildEnumConverterDef(classElement), classElement)
                    );
                    return;
                }

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
                    for (GenericPlaceholderElement placeholder : classElement.getDeclaredGenericPlaceholders()) {
                        builder.addTypeVariable(TypeDef.variable(placeholder.getVariableName()));
                    }
                    builder.addAnnotation(Vetoed.class);

                    copyAnnotations(element, builder, ANNOTATION_PACKAGES_TO_COPY, context);
                    ClassElement superType = element.getSuperType().orElse(null);
                    boolean extendsPythonClass = superType instanceof AbstractPythonClassElement;
                    boolean extendsHostClass = superType != null
                        && !extendsPythonClass
                        && !Object.class.getName().equals(superType.getName())
                        && !superType.isInterface();
                    if (!extendsPythonClass) {
                        builder.addSuperinterface(ClassTypeDef.of("io.micronaut.context.python.ValueCoercible"));
                    }

                    // Check if this class extends another PythonClassElement
                    if (extendsPythonClass || extendsHostClass) {
                        builder.superclass(parameterizedClassTypeDef(superType));
                    }

                    boolean isIntrospectedBean = element.hasStereotype(Introspected.class);
                    final boolean isIntroductionBean = element.hasStereotype(Introduction.class);
                    boolean isJunit5Test = element.getEnclosedElement(ElementQuery.ALL_METHODS.onlyInstance().annotated(ann -> ann.hasDeclaredAnnotation(JUNIT_TEST))).isPresent();
                    boolean isConfigurationBuilderType = isConfigurationBuilderType(element);

                    List<PropertyElement> beanProperties = element.getBeanProperties();
                    boolean hasDynamicBeanProperties = beanProperties.stream().anyMatch(PythonStubGenerator::isDynamicBeanProperty);
                    if (!isIntrospectedBean
                        && !isPythonDataclass(element)
                        && !hasConfigurationInjectConstructor(element)
                        && requiresValidationIntrospection(element, beanProperties)) {
                        // Runtime bean validation looks up a BeanIntrospection for the generated
                        // Java stub class. Python configuration metadata alone is enough for bean
                        // definition generation, but validation still needs this source trigger so
                        // DefaultValidator can inspect constrained configuration properties.
                        builder.addAnnotation(Introspected.class);
                    }
                    Map<String, FieldDef> propertyFields = new LinkedHashMap<>();
                    if (isIntrospectedBean) {
                        for (PropertyElement beanProperty : beanProperties) {
                            FieldDef field = FieldDef.builder(beanProperty.getName())
                                .ofType(propertySourceType(beanProperty))
                                .addModifiers(Modifier.PUBLIC)
                                .build();
                            builder.addField(field);
                            propertyFields.put(beanProperty.getName(), field);
                        }
                    }
                    FieldDef pythonValue = null;
                    if (!extendsPythonClass || isIntrospectedBean) {
                        FieldDef.FieldDefBuilder pythonValueBuilder = FieldDef.builder("graalpyInternalValue")
                            .ofType(POLYGLOT_VALUE)
                            .addModifiers(Modifier.PROTECTED);
                        if (!isIntrospectedBean && !isJunit5Test) {
                            pythonValueBuilder.addModifiers(Modifier.FINAL);
                        }
                        pythonValue = pythonValueBuilder.build();
                        builder.addField(pythonValue);
                    }
                    FieldDef pythonValueSyncing = null;
                    if (isIntrospectedBean && pythonValue != null) {
                        pythonValueSyncing = FieldDef.builder("graalpyInternalValueSyncing")
                            .ofType(TypeDef.Primitive.BOOLEAN)
                            .addModifiers(Modifier.PRIVATE)
                            .build();
                        builder.addField(pythonValueSyncing);
                    }

                    StubEntry stubEntry = new StubEntry(builder, classElement, propertyFields);
                    classBuilders.put(classElement.getName(), stubEntry);

                    // Track method names that have been added to avoid duplicates
                    Set<String> addedMethodNames = stubEntry.bridgedMethods();

                    for (ClassElement anInterface : interfaces) {
                        TypeDef interfaceTypeDef = parameterizedTypeDef(anInterface);
                        builder.addSuperinterface(interfaceTypeDef);
                        List<MethodElement> methods = anInterface.getRawClassElement().getMethods();
                        List<MethodElement> resolvedMethods = anInterface.getMethods();
                        Set<MethodElement> methodSet = new LinkedHashSet<>();
                        for (int i = 0; i < methods.size(); i++) {
                            MethodElement method = methods.get(i);
                            if (methodSet.contains(method) || method.isDefault()) {
                                continue;
                            }
                            MethodElement resolvedMethod = resolvedInterfaceMethod(method, resolvedMethods, i);
                            MethodElement interfaceMethod = withOwningInterface(resolvedMethod, anInterface);
                            MethodElement bridgeMethod = resolveDeclaredBridgeMethod(element, interfaceMethod);
                            if (interfaceMethod.hasDeclaredStereotype(InterceptorBinding.class) || bridgeMethod.hasDeclaredStereotype(InterceptorBinding.class)) {
                                isAopProxy = true;
                            }
                            ClassElement returnTypeOverride = resolveInterfaceBridgeReturnType(interfaceMethod, anInterface);
                            // The generated Java stub must implement the Java interface signature,
                            // not the Python source annotation signature. Python annotations are
                            // often raw while Java interfaces may declare parameterized or wildcard
                            // forms. Use the raw declaring method as the source signature and apply
                            // the resolved interface arguments separately; using the already-resolved
                            // method would collapse method variables such as CrudRepository's
                            // <S extends E> into the entity type and produce same-erasure methods
                            // that fail to override.
                            Map<String, ClassElement> signatureTypeArguments = resolvedInterfaceMethodTypeArguments(anInterface, method);
                            addBridgeMethod(bridgeMethod, builder, context, false, false, addedMethodNames, returnTypeOverride, method, interfaceMethod, signatureTypeArguments);
                            methodSet.add(method);
                        }
                    }
                    if (extendsHostClass) {
                        List<MethodElement> abstractHostMethods = superType.getEnclosedElements(
                            ElementQuery.ALL_METHODS
                                .onlyAccessible()
                                .onlyInstance()
                                .filter(MethodElement::isAbstract));
                        for (MethodElement method : abstractHostMethods) {
                            addBridgeMethod(method, builder, context, false, false, addedMethodNames);
                        }
                        List<MethodElement> hostMethods = superType.getEnclosedElements(
                            ElementQuery.ALL_METHODS
                                .onlyAccessible()
                                .onlyInstance()
                                .filter(method -> !method.isAbstract() && !method.isFinal() && !method.isStatic()));
                        List<MethodElement> declaredMethods = element.getEnclosedElements(
                            ElementQuery.ALL_METHODS
                                .onlyAccessible()
                                .onlyInstance()
                                .onlyDeclared());
                        for (MethodElement hostMethod : hostMethods) {
                            if (declaredMethods.stream().anyMatch(declaredMethod -> overridesHostMethod(declaredMethod, hostMethod))) {
                                addBridgeMethod(hostMethod, builder, context, false, false, addedMethodNames);
                            }
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
                    final FieldDef pythonValueSyncingFinal = pythonValueSyncing;
                    if (!isIntrospectedBean && !extendsPythonClass && pythonValueFinal == null) {
                        throw new IllegalStateException("Expected graalpyInternalValue field to be initialized");
                    }

                    if (!isJunit5Test && !extendsHostClass) {
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
                                                if (pythonValueFinal != null) {
                                                    assigns.add(aThis.field(pythonValueFinal).assign(val));
                                                }
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
                                                ExpressionDef valueExpr = convertValueForType(beanProperty.getGenericType(), member);
                                                if (isCollectionLike(beanProperty.getGenericType())) {
                                                    assigns.add(aThis.field(field).assign(valueExpr));
                                                } else {
                                                    assigns.add(has.isTrue().doIfElse(
                                                        aThis.field(field).assign(valueExpr),
                                                        StatementDef.multi()
                                                    ));
                                                }
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
                    if (!isJunit5Test && extendsHostClass && superType.isAssignable(Throwable.class)) {
                        // Python exceptions raised from GraalPy can surface as host adapter exceptions.
                        // The runtime remaps those adapters back to the generated Throwable subtype
                        // through this Value constructor so Micronaut exception handlers can match it.
                        builder.addMethod(
                            MethodDef.constructor()
                                .addModifiers(Modifier.PUBLIC)
                                .addParameter(ParameterDef.of("value", POLYGLOT_VALUE))
                                .build((aThis, methodParameters) ->
                                    aThis.field(requireField(pythonValueFinal, "Expected graalpyInternalValue field")).assign(methodParameters.get(0))
                                )
                        );
                    }

                    // implement asPolyglotValue by reconstructing the Python object with current field values
                    if (isIntrospectedBean) {
                        final boolean isAbstractIntro = element.isAbstract() && isAopProxy && element.hasStereotype(Introduction.class);
                        final boolean isFrozenDataclass = isFrozenPythonDataclass(element);
                        builder.addMethod(MethodDef.builder(AS_POLYGLOT_VALUE)
                            .addModifiers(Modifier.PUBLIC)
                            .returns(POLYGLOT_VALUE).build(((aThis, methodParameters) -> {
                                    if (hasDynamicBeanProperties && pythonValueFinal != null) {
                                        ExpressionDef storedValue = aThis.field(requireField(pythonValueFinal, "Expected graalpyInternalValue field"));
                                        return storedValue.isNonNull().doIfElse(
                                            storedValue.returning(),
                                            ExpressionDef.nullValue().returning()
                                        );
                                    }
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
                                    if (isAbstractIntro) {
                                        List<ExpressionDef> arguments = new ArrayList<>(List.of(
                                            ExpressionDef.constant(element.getPackageName()),
                                            ExpressionDef.constant(pythonSimpleName)
                                        ));
                                        var primaryCtor = element.getPrimaryConstructor().orElse(null);
                                        if (primaryCtor != null) {
                                            for (PropertyElement beanProperty : beanProperties) {
                                                FieldDef field = propertyFields.get(beanProperty.getName());
                                                if (field == null) {
                                                    continue;
                                                }
                                                ExpressionDef fieldRef = aThis.field(field);
                                                arguments.add(coerceTypedElementToPolyglotValue(beanProperty, fieldRef).cast(TypeDef.OBJECT));
                                            }
                                        }
                                        reconstructedValue = CONTEXT_HOLDER.invokeStatic(
                                            "newIntroduction",
                                            POLYGLOT_VALUE,
                                            arguments
                                        );
                                    } else if (isFrozenDataclass) {
                                        List<ExpressionDef> mapEntries = new ArrayList<>();
                                        for (PropertyElement beanProperty : beanProperties) {
                                            FieldDef field = propertyFields.get(beanProperty.getName());
                                            if (field == null) {
                                                continue;
                                            }
                                            ExpressionDef fieldRef = aThis.field(field);
                                            mapEntries.add(ExpressionDef.constant(beanProperty.getName()));
                                            mapEntries.add(coerceTypedElementToPolyglotValue(beanProperty, fieldRef));
                                        }
                                        ExpressionDef propsMap = ClassTypeDef.of(AnnotationUtil.class)
                                            .invokeStatic("mapOf", TypeDef.of(Map.class), mapEntries);
                                        reconstructedValue = CONTEXT_HOLDER.invokeStatic(
                                            "newFrozenDataclassInstance",
                                            POLYGLOT_VALUE,
                                            List.of(
                                                ExpressionDef.constant(element.getPackageName()),
                                                ExpressionDef.constant(pythonSimpleName),
                                                propsMap
                                            )
                                        );
                                    } else {
                                        reconstructedValue = CONTEXT_HOLDER.invokeStatic(
                                            "newUninitializedInstance",
                                            POLYGLOT_VALUE,
                                            List.of(
                                                ExpressionDef.constant(element.getPackageName()),
                                                ExpressionDef.constant(pythonSimpleName)
                                            )
                                        );
                                    }
                                    if (pythonValueFinal != null) {
                                        if (isFrozenDataclass && !isAbstractIntro) {
                                            return reconstructedValue.returning();
                                        }
                                        FieldDef pythonValueField = requireField(pythonValueFinal, "Expected graalpyInternalValue field");
                                        ExpressionDef storedValue = aThis.field(pythonValueField);
                                        List<StatementDef> syncStatements = new ArrayList<>();
                                        if (pythonValueSyncingFinal != null) {
                                            syncStatements.add(aThis.field(pythonValueSyncingFinal).assign(ExpressionDef.trueValue()));
                                        }
                                        for (PropertyElement beanProperty : beanProperties) {
                                            FieldDef field = propertyFields.get(beanProperty.getName());
                                            if (field == null) {
                                                continue;
                                            }
                                            ExpressionDef fieldRef = aThis.field(field);
                                            syncStatements.add((StatementDef) RUNTIME_UTIL.invokeStatic(
                                                "putMember",
                                                TypeDef.VOID,
                                                storedValue,
                                                ExpressionDef.constant(beanProperty.getName()),
                                                coerceTypedElementToPolyglotValue(beanProperty, fieldRef).cast(TypeDef.OBJECT)
                                            ));
                                        }
                                        if (pythonValueSyncingFinal != null) {
                                            syncStatements.add(aThis.field(pythonValueSyncingFinal).assign(ExpressionDef.falseValue()));
                                        }
                                        syncStatements.add(storedValue.returning());
                                        StatementDef syncBody = StatementDef.multi(syncStatements);
                                        StatementDef existingValueBody = syncBody;
                                        if (pythonValueSyncingFinal != null) {
                                            existingValueBody = aThis.field(pythonValueSyncingFinal)
                                                .isTrue()
                                                .doIfElse(storedValue.returning(), syncBody);
                                        }
                                        return storedValue.isNonNull().doIfElse(
                                            existingValueBody,
                                            StatementDef.multi(
                                                aThis.field(pythonValueField).assign(reconstructedValue),
                                                syncBody
                                            )
                                        );
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
                                } else if (extendsPythonClass) {
                                    return aThis.superRef().invoke(AS_POLYGLOT_VALUE, POLYGLOT_VALUE).returning();
                                } else {
                                    return aThis.field("graalpyInternalValue", POLYGLOT_VALUE).returning();
                                }
                            }))
                        );
                    }

                    // implement static factory
                    ClassTypeDef thisType = ClassTypeDef.of(typeName);

                    if (!isJunit5Test && !extendsHostClass) {
                        builder.addMethod(MethodDef.builder(FROM_POLYGLOT_VALUE)
                            .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                            .addParameter(POLYGLOT_VALUE)
                            .returns(thisType)
                            .build((aThis, methodParameters) -> fromPolyglotValueBody(thisType, methodParameters.get(0)))
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
                        final int requiredConstructorParameterCount = requiredConstructorParameterCount(parameters);
                        final boolean hasDefaultedConstructorParameters = requiredConstructorParameterCount < parameters.length;
                        final boolean constructorParametersBackedByFields = constructorParametersBackedByFields(parameters, propertyFields);
                        builder.addMethod(
                            constructor.addModifiers(Modifier.PUBLIC).build(((aThis, methodParameters) -> {
                                if (isIntrospectedBean && (constructorParametersBackedByFields || hasDynamicBeanProperties)) {
                                    List<StatementDef> assignments = new ArrayList<>();
                                    if (hasDynamicBeanProperties) {
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
                                        assignments.add(aThis.field(requireField(pythonValueFinal, "Expected graalpyInternalValue field")).assign(
                                            CONTEXT_HOLDER.invokeStatic(
                                                isAbstractIntroCtor ? "newIntroduction" : "newInstance",
                                                POLYGLOT_VALUE,
                                                arguments
                                            )
                                        ));
                                    }
                                    for (int i = 0; i < parameters.length; i++) {
                                        @NonNull ParameterElement parameter = parameters[i];
                                        VariableDef.MethodParameter methodParameter = methodParameters.get(i);
                                        PropertyElement beanProperty = findBeanProperty(beanProperties, parameter.getName());
                                        if (beanProperty == null) {
                                            continue;
                                        }
                                        FieldDef field = propertyFields.get(beanProperty.getName());
                                        if (field == null) {
                                            continue;
                                        }
                                        ExpressionDef parameterValue = methodParameter;
                                        ExpressionDef defaultValue = defaultedConstructorParameterValue(parameter);
                                        if (defaultValue != null) {
                                            parameterValue = parameterValue.isNull().doIfElse(defaultValue, parameterValue);
                                        }
                                        assignments.add(aThis.field(field).assign(convertPojoSetterValue(beanProperty, parameterValue)));
                                    }
                                    return StatementDef.multi(assignments);
                                } else {
                                    List<ExpressionDef> arguments = new ArrayList<>(List.of(
                                        ExpressionDef.constant(element.getPackageName()),
                                        ExpressionDef.constant(pythonSimpleName)
                                    ));
                                    if (hasDefaultedConstructorParameters) {
                                        arguments.add(ExpressionDef.constant(requiredConstructorParameterCount));
                                    }
                                    for (int i = 0; i < parameters.length; i++) {
                                        @NonNull ParameterElement parameter = parameters[i];
                                        VariableDef.MethodParameter methodParameter = methodParameters.get(i);
                                        coerceParameterToPolyglotValue(parameter, arguments, methodParameter);
                                        int lastArgIndex = arguments.size() - 1;
                                        arguments.set(lastArgIndex, arguments.get(lastArgIndex).cast(TypeDef.OBJECT));
                                    }
                                    ExpressionDef pythonInstance = CONTEXT_HOLDER.invokeStatic(
                                        constructorFactoryMethod(isAbstractIntroCtor, hasDefaultedConstructorParameters),
                                        POLYGLOT_VALUE,
                                        arguments
                                    );
                                    if (isIntrospectedBean) {
                                        return aThis.invokeConstructor(pythonInstance);
                                    } else if (extendsPythonClass) {
                                        return aThis.superRef().invokeConstructor(pythonInstance);
                                    } else if (extendsHostClass) {
                                        List<ExpressionDef> superArguments = superConstructorArguments(superType, parameters, methodParameters);
                                        return StatementDef.multi(
                                            aThis.superRef().invokeConstructor(superArguments),
                                            aThis.field(requireField(pythonValueFinal, "Expected graalpyInternalValue field")).assign(pythonInstance)
                                        );
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

                    boolean isAnnotationExpressionContextType = isAnnotationExpressionContextType(element, pythonVisitorContext);
                    Predicate<AnnotationMetadata> bridgeMethodFilter = ann -> isJunit5Test ||
                        isAnnotationExpressionContextType ||
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
                        isDeclaredBeanMethod(ann) ||
                        isConfigurationBuilderType;
                    List<MethodElement> methodsToBridge = new ArrayList<>(element.getEnclosedElements(
                        ElementQuery.ALL_METHODS
                            .onlyAccessible()
                            .onlyInstance()
                            .onlyDeclared()
                            .annotated(bridgeMethodFilter)));
                    methodsToBridge.addAll(element.getEnclosedElements(
                        ElementQuery.ALL_METHODS
                            .onlyAccessible()
                            .onlyStatic()
                            .onlyDeclared()
                            .annotated(bridgeMethodFilter)));

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
                    if (isIntrospectedBean) {
                        List<MethodElement> concreteDeclaredMethods = element.getEnclosedElements(
                            ElementQuery.ALL_METHODS
                                .onlyAccessible()
                                .onlyInstance()
                                .onlyDeclared()
                                .filter(method -> shouldBridgeDeclaredPythonMethod(method, beanProperties))
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
                                ann.hasStereotype(AnnotationUtil.INJECT) ||
                                    ann.hasAnnotation(ANN_CONFIGURATION_INJECT)
                            ));

                    for (MethodElement injectionMethod : injectionMethods) {
                        MethodDef.MethodDefBuilder injectionMethodBuilder = MethodDef.builder(injectionMethod.getName());
                        if (!injectionMethod.getReturnType().isVoid()) {
                            injectionMethodBuilder.returns(TypeDef.of(injectionMethod.getReturnType()));
                        }

                        for (@NonNull ParameterElement parameter : injectionMethod.getParameters()) {
                            var parameterType = sourceSignatureType(parameter.getGenericType());
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
                            if (isDynamicBeanProperty(beanProperty)) {
                                beanProperty.getWriteMethod().ifPresent(m -> addNamedSetterDynamic(beanProperty, builder, context));
                                beanProperty.getReadMethod().ifPresent(m -> addNamedGetterDynamic(beanProperty, builder));
                            } else {
                                addSetterPojo(beanProperty, builder, field);
                                addGetterPojo(beanProperty, builder, field);
                            }
                        } else {
                            addSetterDynamic(beanProperty, builder, context);
                            addGetterDynamic(beanProperty, builder);
                            beanProperty.getWriteMethod().ifPresent(m -> {
                                String beanStyle = beanSetterName(beanProperty.getName());
                                if (!m.getName().equals(beanStyle)) {
                                    addNamedSetterDynamic(beanProperty, builder, context);
                                }
                            });
                            beanProperty.getReadMethod().ifPresent(m -> {
                                String beanStyle = beanGetterName(beanProperty.getName());
                                String booleanBeanStyle = booleanBeanGetterName(beanProperty.getName());
                                if (!m.getName().equals(beanStyle) && (!isBooleanProperty(beanProperty) || !m.getName().equals(booleanBeanStyle))) {
                                    addNamedGetterDynamic(beanProperty, builder);
                                }
                            });
                        }
                    }

                    if (isIntrospectedBean) {
                        ObjectHelper.addObjectMethods(builder, ClassTypeDef.of(typeName), beanProperties, propertyFields);
                    }

                    if (!beanProperties.isEmpty()) {
                        builder.addSuperinterface(ClassTypeDef.of("io.micronaut.context.python.ValueCoercible.GeneratedPropertyMembers"));
                        addValueCoerciblePropertyMemberNames(builder, beanProperties, propertyFields);
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
        Map<String, ClassElement> typeArguments = resolvedTypeArguments(anInterface);
        TypeDef interfaceTypeDef = javaClassType(anInterface);
        List<? extends GenericPlaceholderElement> declaredPlaceholders = anInterface.getDeclaredGenericPlaceholders();
        if (!typeArguments.isEmpty()) {
            List<TypeDef> resolvedTypeArguments = new ArrayList<>(typeArguments.size());
            int index = 0;
            for (Map.Entry<String, ClassElement> entry : typeArguments.entrySet()) {
                GenericPlaceholderElement placeholder = placeholderFor(declaredPlaceholders, entry.getKey(), index++);
                resolvedTypeArguments.add(sourceTypeArgument(entry.getValue(), placeholder));
            }
            interfaceTypeDef = TypeDef.parameterized(javaClassType(anInterface), resolvedTypeArguments);
        }
        return withTypeAnnotations(interfaceTypeDef, anInterface);
    }

    private static MethodElement resolvedInterfaceMethod(MethodElement rawMethod, List<MethodElement> resolvedMethods, int index) {
        if (index < resolvedMethods.size() && resolvedMethods.get(index).getName().equals(rawMethod.getName())) {
            return resolvedMethods.get(index);
        }
        for (MethodElement resolvedMethod : resolvedMethods) {
            if (resolvedMethod.getName().equals(rawMethod.getName())
                && resolvedMethod.getParameters().length == rawMethod.getParameters().length) {
                return resolvedMethod;
            }
        }
        return rawMethod;
    }

    private static boolean overridesHostMethod(MethodElement declaredMethod, MethodElement hostMethod) {
        return declaredMethod.getName().equals(hostMethod.getName())
            && declaredMethod.getParameters().length == hostMethod.getParameters().length;
    }

    private static Map<String, ClassElement> resolvedTypeArguments(ClassElement classElement) {
        Map<String, ClassElement> typeArguments = classElement.getTypeArguments();
        if (!typeArguments.isEmpty()) {
            return typeArguments;
        }
        List<? extends ClassElement> boundTypes = classElement.getBoundGenericTypes();
        if (boundTypes.isEmpty()) {
            return Map.of();
        }
        List<? extends GenericPlaceholderElement> placeholders = classElement.getDeclaredGenericPlaceholders();
        if (placeholders.isEmpty()) {
            placeholders = classElement.getRawClassElement().getDeclaredGenericPlaceholders();
        }
        if (placeholders.size() != boundTypes.size()) {
            return Map.of();
        }
        Map<String, ClassElement> resolved = new LinkedHashMap<>(boundTypes.size());
        for (int i = 0; i < placeholders.size(); i++) {
            resolved.put(placeholders.get(i).getVariableName(), boundTypes.get(i));
        }
        return resolved;
    }

    private static Map<String, ClassElement> resolvedInterfaceMethodTypeArguments(ClassElement anInterface, MethodElement method) {
        ClassElement declaringType = method.getDeclaringType();
        Map<String, ClassElement> inheritedTypeArguments = resolveInheritedInterfaceTypeArguments(anInterface, declaringType.getName(), new HashSet<>());
        if (!inheritedTypeArguments.isEmpty()) {
            return inheritedTypeArguments;
        }
        Map<String, ClassElement> directTypeArguments = resolvedTypeArguments(anInterface);
        Map<String, ClassElement> declaringTypeArguments = anInterface.getTypeArguments(declaringType.getName());
        if (declaringTypeArguments.isEmpty()) {
            declaringTypeArguments = anInterface.getAllTypeArguments().getOrDefault(declaringType.getName(), Map.of());
        }
        if (declaringTypeArguments.isEmpty()) {
            declaringTypeArguments = anInterface.getTypeArguments(declaringType.getRawClassElement().getName());
        }
        if (declaringTypeArguments.isEmpty()) {
            declaringTypeArguments = anInterface.getAllTypeArguments().getOrDefault(declaringType.getRawClassElement().getName(), Map.of());
        }
        if (directTypeArguments.isEmpty()) {
            return declaringTypeArguments;
        }
        if (declaringTypeArguments.isEmpty()) {
            return directTypeArguments;
        }
        Map<String, ClassElement> resolved = new LinkedHashMap<>(directTypeArguments);
        resolved.putAll(declaringTypeArguments);
        return resolved;
    }

    private static Map<String, ClassElement> resolveInheritedInterfaceTypeArguments(
        ClassElement type,
        String targetTypeName,
        Set<String> visitedTypes
    ) {
        String visitedKey = type.getName() + resolvedTypeArguments(type);
        if (!visitedTypes.add(visitedKey)) {
            return Map.of();
        }
        Map<String, ClassElement> typeArguments = resolvedTypeArguments(type);
        if (sameRawTypeName(type, targetTypeName)) {
            return typeArguments;
        }
        for (ClassElement anInterface : type.getInterfaces()) {
            ClassElement resolvedInterface = withSubstitutedTypeArguments(anInterface, typeArguments);
            Map<String, ClassElement> resolvedTypeArguments = resolveInheritedInterfaceTypeArguments(resolvedInterface, targetTypeName, visitedTypes);
            if (!resolvedTypeArguments.isEmpty()) {
                return resolvedTypeArguments;
            }
        }
        return Map.of();
    }

    private static ClassElement withSubstitutedTypeArguments(
        ClassElement type,
        Map<String, ClassElement> replacements
    ) {
        Map<String, ClassElement> typeArguments = substituteTypeArguments(resolvedTypeArguments(type), replacements);
        if (typeArguments.isEmpty()) {
            return type;
        }
        try {
            return type.withTypeArguments(typeArguments);
        } catch (UnsupportedOperationException e) {
            return type;
        }
    }

    private static Map<String, ClassElement> substituteTypeArguments(
        Map<String, ClassElement> typeArguments,
        Map<String, ClassElement> replacements
    ) {
        if (typeArguments.isEmpty() || replacements.isEmpty()) {
            return typeArguments;
        }
        Map<String, ClassElement> substituted = new LinkedHashMap<>(typeArguments.size());
        for (Map.Entry<String, ClassElement> entry : typeArguments.entrySet()) {
            substituted.put(entry.getKey(), substituteTypeArgument(entry.getValue(), replacements));
        }
        return substituted;
    }

    private static ClassElement substituteTypeArgument(
        ClassElement type,
        Map<String, ClassElement> replacements
    ) {
        if (type instanceof GenericPlaceholderElement placeholder) {
            ClassElement replacement = replacements.get(placeholder.getVariableName());
            if (replacement != null) {
                return replacement;
            }
        }
        if (type instanceof WildcardElement) {
            return type;
        }
        Map<String, ClassElement> typeArguments = type.getTypeArguments();
        if (typeArguments.isEmpty()) {
            return type;
        }
        Map<String, ClassElement> substituted = substituteTypeArguments(typeArguments, replacements);
        if (substituted.equals(typeArguments)) {
            return type;
        }
        try {
            return type.withTypeArguments(substituted);
        } catch (UnsupportedOperationException e) {
            return type;
        }
    }

    private static boolean sameRawTypeName(ClassElement type, String targetTypeName) {
        if (type.getName().equals(targetTypeName)) {
            return true;
        }
        try {
            return type.getRawClassElement().getName().equals(targetTypeName);
        } catch (UnsupportedOperationException e) {
            return false;
        }
    }

    private static TypeDef withTypeAnnotations(TypeDef typeDef, ClassElement classElement) {
        AnnotationMetadata annotationMetadata = classElement.getTypeAnnotationMetadata();
        if (annotationMetadata.isEmpty()) {
            return typeDef;
        }
        List<AnnotationDef> annotationDefs = new ArrayList<>();
        for (String annotationName : annotationMetadata.getDeclaredAnnotationNames()) {
            if (TYPE_ANNOTATIONS_TO_SKIP_IN_SOURCE.contains(annotationName)) {
                continue;
            }
            AnnotationValue<?> annotationValue = annotationMetadata.getDeclaredAnnotation(annotationName);
            if (annotationValue != null) {
                annotationDefs.add(buildAnnotationDef(annotationValue.getAnnotationName(), (Map) annotationValue.getValues()));
            }
        }
        if (annotationDefs.isEmpty()) {
            return typeDef;
        }
        return typeDef.annotated(annotationDefs);
    }

    static TypeDef propertyType(PropertyElement beanProperty) {
        ClassElement genericType = beanProperty.getGenericType();
        if (!genericType.getTypeArguments().isEmpty() && !(genericType instanceof AbstractPythonClassElement)) {
            return parameterizedTypeDef(genericType);
        }
        return TypeDef.of(beanProperty.getType());
    }

    private static TypeDef sourceSignatureType(ClassElement anInterface) {
        return sourceSignatureType(anInterface, false, Map.of());
    }

    private static TypeDef sourceSignatureType(
        ClassElement anInterface,
        boolean typeArgument,
        Map<String, ClassElement> signatureTypeArguments
    ) {
        if (anInterface instanceof WildcardElement wildcardElement) {
            if (!wildcardElement.getLowerBounds().isEmpty()) {
                return TypeDef.wildcardSupertypeOf(sourceSignatureType(wildcardElement.getLowerBounds().getFirst(), true, signatureTypeArguments));
            }
            if (!wildcardElement.getUpperBounds().isEmpty()) {
                ClassElement upperBound = wildcardElement.getUpperBounds().getFirst();
                if (!Object.class.getName().equals(upperBound.getName())) {
                    return TypeDef.wildcardSubtypeOf(sourceSignatureType(upperBound, true, signatureTypeArguments));
                }
            }
            return TypeDef.wildcard();
        }
        if (anInterface instanceof GenericPlaceholderElement placeholder) {
            ClassElement resolvedTypeArgument = resolveMappedTypeArgument(signatureTypeArguments.get(placeholder.getVariableName()));
            if (resolvedTypeArgument != null
                && (!(resolvedTypeArgument instanceof GenericPlaceholderElement) || !placeholder.equals(resolvedTypeArgument))) {
                return sourceSignatureType(resolvedTypeArgument, typeArgument, signatureTypeArguments);
            }
            if (placeholder.getDeclaringElement().filter(MethodElement.class::isInstance).isPresent()) {
                return TypeDef.variable(placeholder.getVariableName());
            }
            Optional<ClassElement> resolved = placeholder.getResolved();
            if (resolved.isPresent() && !placeholder.equals(resolved.get())) {
                return sourceSignatureType(resolved.get(), typeArgument, signatureTypeArguments);
            }
            if (placeholder.isRawType()) {
                return sourceSignatureType(firstBound(placeholder), typeArgument, signatureTypeArguments);
            }
            return TypeDef.variable(placeholder.getVariableName());
        }
        if (anInterface.isPrimitive()) {
            TypeDef primitiveType = TypeDef.of(anInterface);
            return typeArgument ? TypeDescriptors.toBoxedIfNecessary(primitiveType) : primitiveType;
        }
        if (anInterface.isArray()) {
            return TypeDef.of(anInterface);
        }
        if (anInterface.isRawType()) {
            return javaClassType(anInterface);
        }
        Map<String, ClassElement> typeArguments = resolvedTypeArguments(anInterface);
        TypeDef interfaceTypeDef = javaClassType(anInterface);
        List<? extends GenericPlaceholderElement> declaredPlaceholders = anInterface.getDeclaredGenericPlaceholders();
        if (!typeArguments.isEmpty()) {
            List<TypeDef> resolvedTypeArguments = new ArrayList<>(typeArguments.size());
            int index = 0;
            for (Map.Entry<String, ClassElement> entry : typeArguments.entrySet()) {
                GenericPlaceholderElement placeholder = placeholderFor(declaredPlaceholders, entry.getKey(), index++);
                resolvedTypeArguments.add(sourceTypeArgument(entry.getValue(), placeholder, signatureTypeArguments));
            }
            interfaceTypeDef = TypeDef.parameterized(javaClassType(anInterface), resolvedTypeArguments);
        }
        return interfaceTypeDef;
    }

    private static TypeDef bridgeSignatureType(
        ClassElement signatureType,
        @Nullable ClassElement resolvedType,
        Map<String, ClassElement> signatureTypeArguments
    ) {
        if (signatureType instanceof WildcardElement wildcardElement) {
            if (!wildcardElement.getLowerBounds().isEmpty()) {
                return TypeDef.wildcardSupertypeOf(bridgeSignatureType(wildcardElement.getLowerBounds().getFirst(), resolvedType, signatureTypeArguments));
            }
            if (!wildcardElement.getUpperBounds().isEmpty()) {
                ClassElement upperBound = wildcardElement.getUpperBounds().getFirst();
                if (!Object.class.getName().equals(upperBound.getName())) {
                    return TypeDef.wildcardSubtypeOf(bridgeSignatureType(upperBound, resolvedType, signatureTypeArguments));
                }
            }
            if (resolvedType != null && !isObjectType(resolvedType)) {
                return TypeDef.wildcardSubtypeOf(sourceSignatureType(resolvedType, true, signatureTypeArguments));
            }
            return TypeDef.wildcard();
        }
        if (signatureType instanceof GenericPlaceholderElement placeholder) {
            ClassElement resolvedTypeArgument = resolveMappedTypeArgument(signatureTypeArguments.get(placeholder.getVariableName()));
            if (resolvedTypeArgument != null
                && (!(resolvedTypeArgument instanceof GenericPlaceholderElement) || !placeholder.equals(resolvedTypeArgument))) {
                return sourceSignatureType(resolvedTypeArgument, false, signatureTypeArguments);
            }
            if (placeholder.getDeclaringElement().filter(MethodElement.class::isInstance).isPresent()) {
                return TypeDef.variable(placeholder.getVariableName());
            }
            if (resolvedType != null && !isObjectType(resolvedType)) {
                return sourceSignatureType(resolvedType, false, signatureTypeArguments);
            }
            Optional<ClassElement> resolved = placeholder.getResolved();
            if (resolved.isPresent() && !placeholder.equals(resolved.get())) {
                return sourceSignatureType(resolved.get(), false, signatureTypeArguments);
            }
            if (placeholder.isRawType()) {
                return sourceSignatureType(firstBound(placeholder), false, signatureTypeArguments);
            }
            return TypeDef.variable(placeholder.getVariableName());
        }
        if (signatureType.isRawType()) {
            return javaClassType(signatureType);
        }
        Map<String, ClassElement> typeArguments = signatureType.getTypeArguments();
        if (!typeArguments.isEmpty()) {
            if (Class.class.getName().equals(signatureType.getName())) {
                ClassElement resolvedClassType = firstNonObjectTypeArgument(resolvedType)
                    .orElseGet(() -> signatureTypeArguments.get("T"));
                if (resolvedClassType != null && !isObjectType(resolvedClassType)) {
                    return TypeDef.parameterized(
                        ClassTypeDef.of(Class.class),
                        List.of(sourceSignatureType(resolvedClassType, true, signatureTypeArguments))
                    );
                }
            }
            List<? extends GenericPlaceholderElement> declaredPlaceholders = signatureType.getDeclaredGenericPlaceholders();
            Map<String, ClassElement> resolvedTypeArguments = resolvedType == null ? Map.of() : resolvedType.getTypeArguments();
            List<TypeDef> resolvedTypeDefs = new ArrayList<>(typeArguments.size());
            int index = 0;
            for (Map.Entry<String, ClassElement> entry : typeArguments.entrySet()) {
                GenericPlaceholderElement placeholder = placeholderFor(declaredPlaceholders, entry.getKey(), index++);
                ClassElement resolvedTypeArgument = resolvedTypeArguments.get(entry.getKey());
                if (resolvedTypeArgument == null && index <= resolvedTypeArguments.size()) {
                    resolvedTypeArgument = resolvedTypeArguments.values().stream().skip(index - 1L).findFirst().orElse(null);
                }
                if (resolvedTypeArgument == null && isObjectType(entry.getValue())) {
                    resolvedTypeArgument = signatureTypeArguments.get(entry.getKey());
                }
                if (resolvedTypeArgument == null
                    && isObjectType(entry.getValue())
                    && typeArguments.size() == 1
                    && signatureTypeArguments.size() == 1) {
                    resolvedTypeArgument = signatureTypeArguments.values().iterator().next();
                }
                resolvedTypeDefs.add(bridgeTypeArgument(entry.getValue(), resolvedTypeArgument, placeholder, signatureTypeArguments));
            }
            return TypeDef.parameterized(javaClassType(signatureType), resolvedTypeDefs);
        }
        if (isObjectType(signatureType) && resolvedType != null && !isObjectType(resolvedType)) {
            return sourceSignatureType(resolvedType, false, signatureTypeArguments);
        }
        if (resolvedType != null
            && !signatureType.getName().equals(resolvedType.getName())
            && isResolvedInterfaceTypeArgument(resolvedType, signatureTypeArguments)) {
            return sourceSignatureType(resolvedType, false, signatureTypeArguments);
        }
        return sourceSignatureType(signatureType, false, signatureTypeArguments);
    }

    private static boolean objectTypeArguments(Map<String, ClassElement> typeArguments) {
        return !typeArguments.isEmpty() && typeArguments.values().stream().allMatch(PythonStubGenerator::isObjectType);
    }

    private static boolean requiresValidationIntrospection(ClassElement element, List<PropertyElement> beanProperties) {
        if (!element.hasStereotype(ANN_CONFIGURATION_READER) && !element.hasAnnotation(ANN_CONFIGURATION_READER)) {
            return false;
        }
        if (hasValidationAnnotation(element.getAnnotationMetadata())) {
            return true;
        }
        for (PropertyElement property : beanProperties) {
            if (hasValidationAnnotation(property.getAnnotationMetadata())
                || hasValidationAnnotation(property.getGenericType())) {
                return true;
            }
        }
        return element.getPrimaryConstructor()
            .map(constructor -> {
                for (ParameterElement parameter : constructor.getParameters()) {
                    if (hasValidationAnnotation(parameter.getAnnotationMetadata())
                        || hasValidationAnnotation(parameter.getGenericType())) {
                        return true;
                    }
                }
                return false;
            })
            .orElse(false);
    }

    private static boolean isPythonDataclass(ClassElement element) {
        if (element instanceof AbstractPythonClassElement pythonClassElement) {
            return pythonClassElement.getNativeType()
                .decorators()
                .stream()
                .anyMatch(decorator -> "dataclass".equals(decorator.name()) || "dataclasses.dataclass".equals(decorator.name()));
        }
        return false;
    }

    private static int requiredConstructorParameterCount(ParameterElement[] parameters) {
        int requiredParameterCount = parameters.length;
        while (requiredParameterCount > 0 && hasDefaultValue(parameters[requiredParameterCount - 1])) {
            requiredParameterCount--;
        }
        return requiredParameterCount;
    }

    private static boolean constructorParametersBackedByFields(ParameterElement[] parameters, Map<String, FieldDef> propertyFields) {
        for (ParameterElement parameter : parameters) {
            if (!propertyFields.containsKey(parameter.getName())) {
                return false;
            }
        }
        return true;
    }

    private static List<ExpressionDef> superConstructorArguments(ClassElement superType,
                                                                 ParameterElement[] parameters,
                                                                 List<VariableDef.MethodParameter> methodParameters) {
        return superType.getAccessibleConstructors()
            .stream()
            .sorted((left, right) -> Integer.compare(right.getParameters().length, left.getParameters().length))
            .filter(constructor -> matchesConstructorPrefix(constructor, parameters))
            .findFirst()
            .map(constructor -> new ArrayList<ExpressionDef>(methodParameters.subList(0, constructor.getParameters().length)))
            .orElseGet(() -> new ArrayList<>(methodParameters));
    }

    private static boolean matchesConstructorPrefix(ConstructorElement constructor, ParameterElement[] parameters) {
        ParameterElement[] superParameters = constructor.getParameters();
        if (superParameters.length > parameters.length) {
            return false;
        }
        for (int i = 0; i < superParameters.length; i++) {
            if (!superParameters[i].getType().isAssignable(parameters[i].getType())) {
                return false;
            }
        }
        return true;
    }

    private static boolean hasDefaultValue(ParameterElement parameter) {
        return parameter.getNativeType() instanceof ArgumentDef argumentDef && argumentDef.hasDefaultValue();
    }

    private static @Nullable ExpressionDef defaultedConstructorParameterValue(ParameterElement parameter) {
        ClassElement type = parameter.getGenericType();
        if (type.isAssignable(List.class) && hasDataclassDefaultFactory(parameter, "list")) {
            return ClassTypeDef.of(ArrayList.class).instantiate().cast(constructorParameterType(parameter));
        }
        if (type.isAssignable(Map.class) && hasDataclassDefaultFactory(parameter, "dict")) {
            return ClassTypeDef.of(LinkedHashMap.class).instantiate().cast(constructorParameterType(parameter));
        }
        if (type.isAssignable(Set.class) && hasDataclassDefaultFactory(parameter, "set")) {
            return ClassTypeDef.of(LinkedHashSet.class).instantiate().cast(constructorParameterType(parameter));
        }
        return null;
    }

    private static boolean hasDataclassDefaultFactory(ParameterElement parameter, String factoryName) {
        if (!(parameter.getNativeType() instanceof ArgumentDef argumentDef)) {
            return false;
        }
        if (argumentDef.defaultValue() instanceof String defaultFactoryName) {
            return factoryName.equals(defaultFactoryName) || defaultFactoryName.endsWith("." + factoryName);
        }
        if (!(argumentDef.defaultValue() instanceof Value defaultValue)) {
            return false;
        }
        if (!defaultValue.hasMember("default_factory")) {
            return false;
        }
        Value defaultFactory = defaultValue.getMember("default_factory");
        if (defaultFactory == null || !defaultFactory.hasMember("__name__")) {
            return false;
        }
        Value name = defaultFactory.getMember("__name__");
        return name.isString() && factoryName.equals(name.asString());
    }

    private static boolean isFrozenPythonDataclass(ClassElement element) {
        return element instanceof AbstractPythonClassElement pythonClassElement
            && pythonClassElement.getNativeType().frozenDataclass();
    }

    private static String constructorFactoryMethod(boolean introduction, boolean hasDefaultedParameters) {
        if (introduction) {
            return hasDefaultedParameters ? "newIntroductionWithDefaultedTrailingNulls" : "newIntroduction";
        }
        return hasDefaultedParameters ? "newInstanceWithDefaultedTrailingNulls" : "newInstance";
    }

    private static boolean hasConfigurationInjectConstructor(ClassElement element) {
        return element.getPrimaryConstructor()
            .map(constructor -> constructor.hasStereotype(ANN_CONFIGURATION_INJECT) || constructor.hasAnnotation(ANN_CONFIGURATION_INJECT))
            .orElse(false);
    }

    private static boolean hasValidationAnnotation(AnnotationMetadata metadata) {
        return metadata.hasStereotype(ANN_CONSTRAINT) || metadata.hasAnnotation(ANN_VALID);
    }

    private static boolean hasValidationAnnotation(ClassElement classElement) {
        if (hasValidationAnnotation(classElement.getAnnotationMetadata())) {
            return true;
        }
        for (ClassElement typeArgument : classElement.getTypeArguments().values()) {
            if (hasValidationAnnotation(typeArgument)) {
                return true;
            }
        }
        return false;
    }

    private static Optional<ClassElement> firstNonObjectTypeArgument(@Nullable ClassElement type) {
        if (type == null) {
            return Optional.empty();
        }
        return type.getTypeArguments().values()
            .stream()
            .filter(typeArgument -> !isObjectType(typeArgument))
            .findFirst();
    }

    private static @Nullable ClassElement resolveMappedTypeArgument(@Nullable ClassElement typeArgument) {
        if (typeArgument instanceof GenericPlaceholderElement placeholder) {
            Optional<ClassElement> resolved = placeholder.getResolved();
            if (resolved.isPresent() && !placeholder.equals(resolved.get())) {
                return resolved.get();
            }
            if (placeholder.isRawType()) {
                return firstBound(placeholder);
            }
        }
        return typeArgument;
    }

    private static boolean isResolvedInterfaceTypeArgument(
        ClassElement resolvedType,
        Map<String, ClassElement> signatureTypeArguments
    ) {
        return signatureTypeArguments.values()
            .stream()
            .anyMatch(typeArgument -> typeArgument.getName().equals(resolvedType.getName()));
    }

    private static TypeDef bridgeTypeArgument(
        ClassElement signatureTypeArgument,
        @Nullable ClassElement resolvedTypeArgument,
        @Nullable GenericPlaceholderElement placeholder,
        Map<String, ClassElement> signatureTypeArguments
    ) {
        if (placeholder != null && isDeclaredPlaceholderArgument(signatureTypeArgument, placeholder)) {
            return bridgeSignatureType(firstBound(placeholder), resolvedTypeArgument, signatureTypeArguments);
        }
        return bridgeSignatureType(signatureTypeArgument, resolvedTypeArgument, signatureTypeArguments);
    }

    private static boolean isDeclaredPlaceholderArgument(
        ClassElement typeArgument,
        @Nullable GenericPlaceholderElement placeholder
    ) {
        if (placeholder == null || !(typeArgument instanceof GenericPlaceholderElement argumentPlaceholder)) {
            return false;
        }
        if (!placeholder.getVariableName().equals(argumentPlaceholder.getVariableName())) {
            return false;
        }
        return argumentPlaceholder.equals(placeholder)
            || argumentPlaceholder.getDeclaringElement().equals(placeholder.getDeclaringElement());
    }

    private static TypeDef sourceTypeArgument(ClassElement typeArgument, @Nullable GenericPlaceholderElement placeholder) {
        return sourceTypeArgument(typeArgument, placeholder, Map.of());
    }

    private static TypeDef sourceTypeArgument(
        ClassElement typeArgument,
        @Nullable GenericPlaceholderElement placeholder,
        Map<String, ClassElement> resolvedTypeArguments
    ) {
        if (typeArgument instanceof GenericPlaceholderElement
            && (placeholder == null || !isDeclaredPlaceholderArgument(typeArgument, placeholder))) {
            return sourceSignatureType(typeArgument, true, resolvedTypeArguments);
        }
        if (placeholder != null) {
            if (isDeclaredPlaceholderArgument(typeArgument, placeholder)) {
                return sourceSignatureType(firstBound(placeholder), true, resolvedTypeArguments);
            }
            if (isObjectType(typeArgument)) {
                Optional<ClassElement> bound = firstNonObjectBound(placeholder);
                if (bound.isPresent()) {
                    return sourceSignatureType(bound.get(), true, resolvedTypeArguments);
                }
            }
        }
        return sourceSignatureType(typeArgument, true, resolvedTypeArguments);
    }

    private static @Nullable GenericPlaceholderElement placeholderFor(
        List<? extends GenericPlaceholderElement> placeholders,
        String variableName,
        int index
    ) {
        for (GenericPlaceholderElement placeholder : placeholders) {
            if (placeholder.getVariableName().equals(variableName)) {
                return placeholder;
            }
        }
        return index < placeholders.size() ? placeholders.get(index) : null;
    }

    private static Optional<ClassElement> firstNonObjectBound(GenericPlaceholderElement placeholder) {
        for (ClassElement bound : placeholder.getBounds()) {
            if (!isObjectType(bound)) {
                return Optional.of(bound);
            }
        }
        return Optional.empty();
    }

    private static boolean isObjectType(ClassElement classElement) {
        return Object.class.getName().equals(classElement.getName())
            && classElement.getTypeArguments().isEmpty()
            && !classElement.isArray();
    }

    private static TypeDef propertySourceType(PropertyElement beanProperty) {
        return sourceSignatureType(beanProperty.getGenericType());
    }

    private static ClassTypeDef parameterizedClassTypeDef(ClassElement classElement) {
        TypeDef typeDef = parameterizedTypeDef(classElement);
        if (typeDef instanceof ClassTypeDef classTypeDef) {
            return classTypeDef;
        }
        return javaClassType(classElement);
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

    private static boolean isAnnotationExpressionContextType(ClassElement element, PythonVisitorContext context) {
        Set<String> typeNames = new HashSet<>();
        typeNames.add(element.getName());
        typeNames.add(element.getCanonicalName());
        typeNames.add(element.getSimpleName());
        for (DecoratorDef decoratorDef : context.getProcessingEnvironment().environment().decorators().values()) {
            if (referencesAnnotationExpressionContextType(decoratorDef, typeNames)) {
                return true;
            }
        }
        return false;
    }

    private static boolean referencesAnnotationExpressionContextType(DecoratorDef decoratorDef, Set<String> typeNames) {
        if (ANN_ANNOTATION_EXPRESSION_CONTEXT.equals(decoratorDef.annotationName())) {
            for (Object memberValue : decoratorDef.members().values()) {
                if (matchesTypeName(memberValue, typeNames)) {
                    return true;
                }
            }
        }
        for (DecoratorDef stereotype : decoratorDef.stereotypes()) {
            if (referencesAnnotationExpressionContextType(stereotype, typeNames)) {
                return true;
            }
        }
        for (List<DecoratorDef> memberDecorators : decoratorDef.memberDecorators().values()) {
            for (DecoratorDef memberDecorator : memberDecorators) {
                if (referencesAnnotationExpressionContextType(memberDecorator, typeNames)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean matchesTypeName(Object value, Set<String> typeNames) {
        if (value instanceof Value polyglotValue) {
            if (polyglotValue.isNull()) {
                return false;
            }
            if (polyglotValue.isHostObject()) {
                return matchesTypeName(polyglotValue.asHostObject(), typeNames);
            }
            return polyglotValue.isString() && typeNames.contains(polyglotValue.asString());
        }
        if (value instanceof Class<?> classValue) {
            return typeNames.contains(classValue.getName());
        }
        if (value instanceof ClassElement classElement) {
            return typeNames.contains(classElement.getName()) || typeNames.contains(classElement.getCanonicalName());
        }
        return value != null && typeNames.contains(value.toString());
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
                .returns(sourceMethodReturnType(methodElement, false));
            addMethodTypeVariables(methodElement, methodBuilder);
            for (@NonNull ParameterElement parameter : methodElement.getParameters()) {
                methodBuilder.addParameter(ParameterDef
                    .builder(parameter.getName(), sourceSignatureType(parameter.getGenericType()))
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
            if (shouldEmitAnnotationReference(stereotype, visitorContext)) {
                builder.addAnnotation(toAnnotationDef(stereotype, visitorContext));
            }
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
                if (shouldEmitAnnotationReference(memberDecorator, visitorContext)) {
                    memberBuilder.addAnnotation(toAnnotationDef(memberDecorator, visitorContext));
                }
            }
            ExpressionDef defaultValue = annotationDefaultValue(decoratorDef.members().get(memberName), memberType, visitorContext);
            if (defaultValue != null) {
                memberBuilder.withDefault(defaultValue);
            }
            builder.addMember(memberBuilder.build());
        }

        return builder.build();
    }

    private static boolean shouldEmitAnnotationReference(DecoratorDef decoratorDef, PythonVisitorContext visitorContext) {
        String annotationName = decoratorDef.annotationName();
        var javaVisitorContext = visitorContext.getJavaVisitorContext();
        if (javaVisitorContext != null && javaVisitorContext.getClassElement(annotationName).isPresent()) {
            return true;
        }
        return visitorContext.getProcessingEnvironment().environment().decorators().containsKey(annotationName);
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
        TypeDef annotationArrayType = annotationArrayMemberType(typeRef, visitorContext);
        if (annotationArrayType != null) {
            return annotationArrayType;
        }
        ClassElement classElement = GraalPyUtil.resolvePythonTypeToJava(typeRef, visitorContext, Map.of());
        Value defaultValue = decoratorDef.members().get(memberName);
        if (classElement.getName().equals(Object.class.getName()) && defaultValue != null && defaultValue.isString()) {
            return TypeDef.STRING;
        }
        return TypeDef.of(classElement);
    }

    private static @Nullable TypeDef annotationArrayMemberType(TypeRef typeRef, PythonVisitorContext visitorContext) {
        if (!isPythonListType(typeRef.name()) || typeRef.typeArguments().size() != 1) {
            return null;
        }
        TypeRef componentType = typeRef.typeArguments().getFirst();
        if (isClassLiteralType(componentType)) {
            return ClassTypeDef.of(Class.class).array();
        }
        ClassElement componentElement = GraalPyUtil.resolvePythonTypeToJava(componentType, visitorContext, Map.of());
        return TypeDef.of(componentElement).array();
    }

    private static boolean isPythonListType(String typeName) {
        return "list".equals(typeName) || "List".equals(typeName) || "typing.List".equals(typeName);
    }

    private static boolean isClassLiteralType(TypeRef typeRef) {
        return "type".equals(typeRef.name())
            || "typing.Type".equals(typeRef.name())
            || "Class".equals(typeRef.name())
            || Class.class.getName().equals(typeRef.name());
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
            if (memberType instanceof TypeDef.Array arrayType
                && polyglotValue.hasArrayElements()
                && polyglotValue.getArraySize() == 0) {
                return new ExpressionDef.Constant(arrayType, new Object[0]);
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
        if (members.values().stream().anyMatch(PythonStubGenerator::requiresLiteralAnnotationValue)) {
            return buildAnnotationDef(decoratorDef.annotationName(), members);
        }
        AnnotationValue<?> annotationValue = new AnnotationValue<>(decoratorDef.annotationName(), members);
        try {
            return AnnotationDef.of(annotationValue, visitorContext);
        } catch (RuntimeException e) {
            return buildAnnotationDef(decoratorDef.annotationName(), members);
        }
    }

    private static AnnotationDef buildAnnotationDef(String annotationName, Map<CharSequence, Object> members) {
        AnnotationDef.AnnotationDefBuilder builder = AnnotationDef.builder(ClassTypeDef.of(annotationName));
        members.forEach((memberName, value) -> addAnnotationDefMember(builder, memberName.toString(), value));
        return builder.build();
    }

    @SuppressWarnings("unchecked")
    private static void addAnnotationDefMember(AnnotationDef.AnnotationDefBuilder builder, String memberName, Object value) {
        Object normalized = normalizeAnnotationDefMember(value);
        if (normalized instanceof Collection<?> collection) {
            builder.addMember(memberName, (Collection<Object>) collection);
        } else {
            builder.addMember(memberName, normalized);
        }
    }

    private static Object normalizeAnnotationDefMember(Object value) {
        if (value instanceof Object[] array) {
            if (array.length == 0) {
                return EmptyAnnotationArray.INSTANCE;
            }
            List<Object> values = new ArrayList<>(array.length);
            for (Object element : array) {
                values.add(normalizeAnnotationDefMember(element));
            }
            return values;
        }
        if (value instanceof Collection<?> collection) {
            if (collection.isEmpty()) {
                return EmptyAnnotationArray.INSTANCE;
            }
            List<Object> values = new ArrayList<>(collection.size());
            for (Object element : collection) {
                values.add(normalizeAnnotationDefMember(element));
            }
            return values;
        }
        return value;
    }

    private static boolean containsSourcegenAnnotationValue(Object value) {
        if (value instanceof VariableDef || value instanceof ClassTypeDef) {
            return true;
        }
        if (value instanceof Object[] array) {
            for (Object element : array) {
                if (containsSourcegenAnnotationValue(element)) {
                    return true;
                }
            }
            return false;
        }
        if (value instanceof Collection<?> collection) {
            for (Object element : collection) {
                if (containsSourcegenAnnotationValue(element)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean requiresLiteralAnnotationValue(Object value) {
        return containsSourcegenAnnotationValue(value) || isEmptyArrayOrCollection(value);
    }

    private static boolean isEmptyArrayOrCollection(Object value) {
        if (value instanceof Object[] array) {
            return array.length == 0;
        }
        if (value instanceof Collection<?> collection) {
            return collection.isEmpty();
        }
        return false;
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
            return enumConstantValue(value, memberType, visitorContext);
        }
        if (Class.class.getName().equals(memberType.getName())) {
            return annotationClassLiteralValue(value, visitorContext);
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
        if (Class.class.getName().equals(componentType.getName())) {
            return convertAnnotationClassArrayMemberValue(value, visitorContext);
        }
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

    private static Object[] convertAnnotationClassArrayMemberValue(
        Object value,
        PythonVisitorContext visitorContext
    ) {
        List<Object> converted = new ArrayList<>();
        collectAnnotationClassValues(value, visitorContext, converted);
        if (converted.stream().allMatch(Class.class::isInstance)) {
            return converted.toArray(Class[]::new);
        }
        if (converted.stream().allMatch(String.class::isInstance)) {
            return convertedAnnotationClassStrings(converted, visitorContext);
        }
        return converted.toArray();
    }

    private static Object[] convertedAnnotationClassStrings(
        List<Object> converted,
        PythonVisitorContext visitorContext
    ) {
        List<String> classNames = new ArrayList<>(converted.size());
        for (Object value : converted) {
            classNames.add(rawClassName((String) value, visitorContext));
        }
        List<VariableDef.StaticField> classLiterals = new ArrayList<>(converted.size());
        for (String className : classNames) {
            ClassElement classElement = visitorContext.getClassElement(className).orElse(null);
            if (classElement == null) {
                return classNames.toArray(String[]::new);
            }
            classLiterals.add(rawClassLiteral(classElement));
        }
        return classLiterals.toArray();
    }

    private static void collectAnnotationClassValues(
        Object value,
        PythonVisitorContext visitorContext,
        List<Object> converted
    ) {
        if (value instanceof Collection<?> collection) {
            for (Object element : collection) {
                collectAnnotationClassValues(element, visitorContext, converted);
            }
            return;
        }
        if (value instanceof Object[] array) {
            for (Object element : array) {
                collectAnnotationClassValues(element, visitorContext, converted);
            }
            return;
        }
        if (value instanceof Value polyglotValue && polyglotValue.hasArrayElements()) {
            int size = Math.toIntExact(polyglotValue.getArraySize());
            for (int i = 0; i < size; i++) {
                collectAnnotationClassValues(polyglotValue.getArrayElement(i), visitorContext, converted);
            }
            return;
        }
        Object classValue = annotationClassLiteralValue(value, visitorContext);
        if (classValue != null) {
            converted.add(classValue);
        }
    }

    private static boolean isEnumMember(ClassElement memberType) {
        return memberType.isEnum() || memberType.isAssignable(Enum.class);
    }

    private static VariableDef.StaticField enumConstantValue(Object value, ClassElement memberType, PythonVisitorContext visitorContext) {
        ClassTypeDef enumType = rawClassType(memberType);
        return enumType.getStaticField(enumConstantName(value, visitorContext), enumType);
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

    private static @Nullable Object annotationClassLiteralValue(Object value, PythonVisitorContext visitorContext) {
        if (value instanceof String stringValue) {
            return rawTypeName(stringValue);
        }
        if (value instanceof Value polyglotValue && polyglotValue.isString()) {
            return rawTypeName(polyglotValue.asString());
        }
        String className = annotationClassName(value, visitorContext);
        if (className == null) {
            return null;
        }
        String resolvedClassName = rawClassName(className, visitorContext);
        return switch (resolvedClassName) {
            case "boolean" -> boolean.class;
            case "byte" -> byte.class;
            case "char" -> char.class;
            case "double" -> double.class;
            case "float" -> float.class;
            case "int" -> int.class;
            case "long" -> long.class;
            case "short" -> short.class;
            case "void" -> void.class;
            default -> {
                ClassElement classElement = visitorContext.getClassElement(resolvedClassName).orElse(null);
                yield classElement == null ? resolvedClassName : rawClassLiteral(classElement);
            }
        };
    }

    private static VariableDef.StaticField rawClassLiteral(ClassElement classElement) {
        return rawClassType(classElement).getStaticField("class", TypeDef.of(Class.class));
    }

    private static ClassTypeDef rawClassType(ClassElement classElement) {
        ClassElement rawClassElement = classElement.getRawClassElement();
        return ClassTypeDef.of(rawTypeName(javaTypeName(rawClassElement)), rawClassElement.isInner());
    }

    private static @Nullable String annotationClassName(Object value, PythonVisitorContext visitorContext) {
        if (value == null) {
            return null;
        }
        if (value instanceof AnnotationClassValue<?> annotationClassValue) {
            return rawClassName(annotationClassValue.getName(), visitorContext);
        }
        if (value instanceof Class<?> classValue) {
            return classValue.getName();
        }
        if (value instanceof ClassElement classElement) {
            return rawClassName(classElement.getRawClassElement().getName(), visitorContext);
        }
        if (value instanceof Value polyglotValue) {
            if (polyglotValue.isHostObject()) {
                Object hostObject = polyglotValue.asHostObject();
                if (hostObject instanceof Class<?> classValue) {
                    return classValue.getName();
                }
                return annotationClassName(hostObject, visitorContext);
            }
            if (polyglotValue.isString()) {
                return rawClassName(polyglotValue.asString(), visitorContext);
            }
            Object converted = GraalPyUtil.convertValueToJava(polyglotValue, visitorContext);
            if (converted == polyglotValue) {
                return rawClassName(polyglotValue.toString(), visitorContext);
            }
            return annotationClassName(converted, visitorContext);
        }
        return rawClassName(value.toString(), visitorContext);
    }

    private static String rawClassName(String typeName, PythonVisitorContext visitorContext) {
        String rawTypeName = rawTypeName(typeName);
        return visitorContext.getClassElement(rawTypeName)
            .map(classElement -> rawTypeName(classElement.getRawClassElement().getName()))
            .orElse(rawTypeName);
    }

    private static String rawTypeName(String typeName) {
        int genericStart = typeName.indexOf('<');
        return genericStart > -1 ? typeName.substring(0, genericStart) : typeName;
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
                            isDeclaredBeanMethod(ann)));

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
        if (t instanceof GenericPlaceholderElement placeholder) {
            return erasedType(resolvedOrFirstBound(placeholder));
        }
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

    static TypeDef methodReturnType(MethodElement methodElement, boolean isJunit5Test) {
        return methodReturnType(methodElement.getGenericReturnType(), isJunit5Test);
    }

    static TypeDef methodReturnType(MethodElement methodElement, boolean isJunit5Test, @Nullable ClassElement returnTypeOverride) {
        return methodReturnType(returnTypeOverride == null ? methodElement.getGenericReturnType() : returnTypeOverride, isJunit5Test);
    }

    static TypeDef methodReturnType(ClassElement genericReturnType, boolean isJunit5Test) {
        if (isJunit5Test) {
            return TypeDef.Primitive.VOID;
        }
        if (!genericReturnType.getTypeArguments().isEmpty()) {
            return parameterizedTypeDef(genericReturnType);
        }
        return sourceSignatureType(genericReturnType);
    }

    static TypeDef sourceMethodReturnType(MethodElement methodElement, boolean isJunit5Test) {
        if (isJunit5Test) {
            return TypeDef.Primitive.VOID;
        }
        return sourceSignatureType(methodElement.getGenericReturnType());
    }

    private static void addMethodTypeVariables(MethodElement methodElement, MethodDef.MethodDefBuilder methodBuilder) {
        addMethodTypeVariables(methodElement, methodBuilder, Map.of());
    }

    private static void addMethodTypeVariables(
        MethodElement methodElement,
        MethodDef.MethodDefBuilder methodBuilder,
        Map<String, ClassElement> resolvedTypeArguments
    ) {
        addMethodTypeVariables(methodElement, methodBuilder, resolvedTypeArguments, Map.of());
    }

    private static void addMethodTypeVariables(
        MethodElement methodElement,
        MethodDef.MethodDefBuilder methodBuilder,
        Map<String, ClassElement> resolvedTypeArguments,
        Map<String, ClassElement> inferredMethodBounds
    ) {
        for (GenericPlaceholderElement placeholder : methodElement.getDeclaredTypeVariables()) {
            List<TypeDef> bounds = placeholder.getBounds().stream()
                .filter(bound -> !Object.class.getName().equals(bound.getName()))
                .map(bound -> sourceSignatureType(bound, true, resolvedTypeArguments))
                .toList();
            ClassElement inferredBound = inferredMethodBounds.get(placeholder.getVariableName());
            if (bounds.isEmpty() && inferredBound != null && !isObjectType(inferredBound)) {
                bounds = List.of(sourceSignatureType(inferredBound, true, resolvedTypeArguments));
            }
            methodBuilder.addTypeVariable(
                TypeDef.variable(
                    placeholder.getVariableName(),
                    bounds
                )
            );
        }
    }

    private static Map<String, ClassElement> withoutDeclaredMethodTypeVariables(
        Map<String, ClassElement> signatureTypeArguments,
        MethodElement signatureMethod
    ) {
        List<? extends GenericPlaceholderElement> methodTypeVariables = signatureMethod.getDeclaredTypeVariables();
        if (signatureTypeArguments.isEmpty() || methodTypeVariables.isEmpty()) {
            return signatureTypeArguments;
        }
        Map<String, ClassElement> resolved = new LinkedHashMap<>(signatureTypeArguments);
        for (GenericPlaceholderElement methodTypeVariable : methodTypeVariables) {
            resolved.remove(methodTypeVariable.getVariableName());
        }
        return resolved;
    }

    private static Map<String, ClassElement> inferMethodTypeBounds(
        MethodElement signatureMethod,
        MethodElement resolvedMethod
    ) {
        if (signatureMethod.getDeclaredTypeVariables().isEmpty()) {
            return Map.of();
        }
        Map<String, ClassElement> bounds = new LinkedHashMap<>();
        inferMethodTypeBounds(signatureMethod.getGenericReturnType(), resolvedMethod.getGenericReturnType(), bounds);
        ParameterElement[] signatureParameters = signatureMethod.getParameters();
        ParameterElement[] resolvedParameters = resolvedMethod.getParameters();
        for (int i = 0; i < signatureParameters.length && i < resolvedParameters.length; i++) {
            inferMethodTypeBounds(signatureParameters[i].getGenericType(), resolvedParameters[i].getGenericType(), bounds);
        }
        return bounds;
    }

    private static void inferMethodTypeBounds(
        ClassElement signatureType,
        ClassElement resolvedType,
        Map<String, ClassElement> bounds
    ) {
        if (signatureType instanceof GenericPlaceholderElement placeholder
            && placeholder.getDeclaringElement().filter(MethodElement.class::isInstance).isPresent()) {
            ClassElement bound = resolvedType instanceof GenericPlaceholderElement resolvedPlaceholder
                ? resolvedOrFirstBound(resolvedPlaceholder)
                : resolvedType;
            if (!isObjectType(bound)) {
                bounds.putIfAbsent(placeholder.getVariableName(), bound);
            }
            return;
        }
        Map<String, ClassElement> signatureTypeArguments = signatureType.getTypeArguments();
        Map<String, ClassElement> resolvedTypeArguments = resolvedType.getTypeArguments();
        if (!signatureTypeArguments.isEmpty() && !resolvedTypeArguments.isEmpty()) {
            int index = 0;
            for (Map.Entry<String, ClassElement> entry : signatureTypeArguments.entrySet()) {
                ClassElement resolvedTypeArgument = resolvedTypeArguments.get(entry.getKey());
                if (resolvedTypeArgument == null && index < resolvedTypeArguments.size()) {
                    resolvedTypeArgument = resolvedTypeArguments.values().stream().skip(index).findFirst().orElse(null);
                }
                if (resolvedTypeArgument != null) {
                    inferMethodTypeBounds(entry.getValue(), resolvedTypeArgument, bounds);
                }
                index++;
            }
        }
    }

    private static String javaTypeName(ClassElement t) {
        if (t instanceof AbstractPythonClassElement) {
            return t.getName();
        }
        return t.getName().replace('$', '.');
    }

    private static ClassTypeDef javaClassType(ClassElement t) {
        if (t instanceof GenericPlaceholderElement placeholder) {
            return javaClassType(resolvedOrFirstBound(placeholder));
        }
        return ClassTypeDef.of(javaTypeName(t));
    }

    private static ClassElement resolvedOrFirstBound(GenericPlaceholderElement placeholder) {
        if (placeholder.isRawType()) {
            return firstBound(placeholder);
        }
        return placeholder.getResolved().orElseGet(() -> firstBound(placeholder));
    }

    private static ClassElement firstBound(GenericPlaceholderElement placeholder) {
        List<? extends ClassElement> bounds = placeholder.getBounds();
        if (bounds.isEmpty()) {
            return ClassElement.of(Object.class);
        }
        return bounds.getFirst();
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
        ExpressionDef parameter = coerceTypedElementToPolyglotValue(param, methodParam);
        if (targetContext != null) {
            parameter = RUNTIME_UTIL.invokeStatic("coerceToContext", TypeDef.OBJECT, parameter, targetContext, classLiteral(param.getGenericType()));
        }
        parameters.add(parameter);
    }

    private static ExpressionDef coerceTypedElementToPolyglotValue(TypedElement element, ExpressionDef expr) {
        ClassElement genericType = element.getGenericType();
        if (genericType.isAssignable(Map.class) && genericType.getTypeArguments().get("V") instanceof PythonClassElement) {
            return RUNTIME_UTIL.invokeStatic("coerceMap", TypeDef.of(Map.class), expr);
        } else if (genericType.isAssignable(List.class) && genericType.getTypeArguments().get("E") instanceof PythonClassElement) {
            return RUNTIME_UTIL.invokeStatic("coerceList", TypeDef.of(List.class), expr);
        } else if (genericType instanceof PythonClassElement) {
            return RUNTIME_UTIL.invokeStatic("coerceValue", TypeDef.OBJECT, expr);
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

    /**
     * Builds the generated Java enum definition for a Python enum.
     *
     * @param classElement The Python enum element
     * @param context The visitor context
     * @return The generated enum definition
     */
    EnumDef buildEnumDef(AbstractPythonClassElement classElement, VisitorContext context) {
        ClassTypeDef thisType = ClassTypeDef.of(classElement.getName());
        EnumDef.EnumDefBuilder enumBuilder = EnumDef.builder(classElement.getName())
            .addModifiers(Modifier.PUBLIC)
            .addAnnotation(Vetoed.class)
            .addSuperinterface(ClassTypeDef.of("io.micronaut.context.python.ValueCoercible"));
        copyAnnotations(classElement, enumBuilder, ANNOTATION_PACKAGES_TO_COPY, context);
        List<String> enumConstants = classElement instanceof EnumElement enumElement ? enumElement.values() : List.of();
        for (String enumConstant : enumConstants) {
            enumBuilder.addEnumConstant(enumConstant);
        }
        enumBuilder.addMethod(MethodDef.builder(AS_POLYGLOT_VALUE)
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC)
            .returns(POLYGLOT_VALUE)
            .build((aThis, parameters) -> CONTEXT_HOLDER.invokeStatic(
                "enumValue",
                POLYGLOT_VALUE,
                ExpressionDef.constant(classElement.getPackageName()),
                ExpressionDef.constant(pythonSimpleName(classElement)),
                aThis.invoke("name", TypeDef.STRING)
            ).returning()));
        enumBuilder.addMethod(MethodDef.builder(FROM_POLYGLOT_VALUE)
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
            .addParameter(POLYGLOT_VALUE)
            .returns(thisType)
            .build((aThis, methodParameters) -> RUNTIME_UTIL.invokeStatic(
                "convertValue",
                thisType,
                methodParameters.get(0),
                thisType.getStaticField("class", TypeDef.CLASS)
            ).returning()));
        Set<String> addedMethodNames = new LinkedHashSet<>();
        MethodElement jsonValueMethod = enumJsonValueMethod(classElement);
        for (MethodElement methodElement : classElement.getEnclosedElements(ElementQuery.ALL_METHODS.onlyInstance().onlyDeclared())) {
            addBridgeMethod(methodElement, enumBuilder, context, false, false, addedMethodNames);
        }
        if (jsonValueMethod != null && !"toString".equals(jsonValueMethod.getName()) && addedMethodNames.add("toString()")) {
            enumBuilder.addMethod(MethodDef.builder("toString")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .returns(TypeDef.STRING)
                .build((aThis, parameters) -> aThis.invoke(jsonValueMethod.getName(), TypeDef.STRING).returning()));
        }
        return enumBuilder.build();
    }

    private static ClassDef buildEnumConverterDef(AbstractPythonClassElement classElement) {
        ClassTypeDef thisType = ClassTypeDef.of(classElement.getName());
        TypeDef typeConverterType = TypeDef.parameterized(
            ClassTypeDef.of("io.micronaut.core.convert.TypeConverter"),
            ClassTypeDef.of(CharSequence.class),
            thisType
        );
        ClassDef.ClassDefBuilder builder = ClassDef.builder(classElement.getName() + "TypeConverter")
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addAnnotation(ClassTypeDef.of("jakarta.inject.Singleton"))
            .addSuperinterface(typeConverterType);
        builder.addMethod(MethodDef.builder("convert")
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC)
            .addParameter("object", ClassTypeDef.of(CharSequence.class))
            .addParameter("targetType", TypeDef.parameterized(Class.class, thisType))
            .addParameter("context", ClassTypeDef.of("io.micronaut.core.convert.ConversionContext"))
            .returns(TypeDef.parameterized(Optional.class, thisType))
            .build((aThis, methodParameters) -> enumConverterBody(classElement, thisType, methodParameters.getFirst())));
        return builder.build();
    }

    private static StatementDef enumConverterBody(AbstractPythonClassElement classElement, ClassTypeDef thisType, VariableDef.MethodParameter object) {
        List<StatementDef> statements = new ArrayList<>();
        List<String> enumConstants = classElement instanceof EnumElement enumElement ? enumElement.values() : List.of();
        for (String enumConstant : enumConstants) {
            ExpressionDef enumName = ExpressionDef.constant(enumConstant);
            ExpressionDef enumValue = RUNTIME_UTIL.invokeStatic(
                "enumStringValue",
                TypeDef.STRING,
                CONTEXT_HOLDER.invokeStatic(
                    "enumValue",
                    POLYGLOT_VALUE,
                    ExpressionDef.constant(classElement.getPackageName()),
                    ExpressionDef.constant(pythonSimpleName(classElement)),
                    enumName
                )
            );
            ExpressionDef enumField = thisType.getStaticField(enumConstant, thisType);
            StatementDef returnMatch = ClassTypeDef.of(Optional.class)
                .invokeStatic("of", TypeDef.parameterized(Optional.class, thisType), enumField)
                .returning();
            statements.add(enumName.invoke("contentEquals", TypeDef.Primitive.BOOLEAN, object).isTrue().doIf(returnMatch));
            statements.add(enumValue.invoke("contentEquals", TypeDef.Primitive.BOOLEAN, object).isTrue().doIf(returnMatch));
        }
        statements.add(ClassTypeDef.of(Optional.class)
            .invokeStatic("empty", TypeDef.parameterized(Optional.class, thisType))
            .returning());
        return StatementDef.multi(statements);
    }

    private static @Nullable MethodElement enumJsonValueMethod(ClassElement classElement) {
        for (MethodElement methodElement : classElement.getEnclosedElements(ElementQuery.ALL_METHODS.onlyInstance().onlyDeclared())) {
            if (methodElement.hasAnnotation("com.fasterxml.jackson.annotation.JsonValue")
                && methodElement.getParameters().length == 0
                && String.class.getName().equals(methodElement.getReturnType().getName())) {
                return methodElement;
            }
        }
        return null;
    }

    private void addBridgeMethod(
        MethodElement methodElement,
        ObjectDefBuilder<?> builder,
        VisitorContext visitorContext,
        boolean isJunit5Test,
        boolean isScript,
        Set<String> addedMethodNames) {
        addBridgeMethod(methodElement, builder, visitorContext, isJunit5Test, isScript, addedMethodNames, null);
    }

    private void addBridgeMethod(
        MethodElement methodElement,
        ObjectDefBuilder<?> builder,
        VisitorContext visitorContext,
        boolean isJunit5Test,
        boolean isScript,
        Set<String> addedMethodNames,
        @Nullable ClassElement returnTypeOverride) {
        addBridgeMethod(methodElement, builder, visitorContext, isJunit5Test, isScript, addedMethodNames, returnTypeOverride, methodElement, Map.of());
    }

    private void addBridgeMethod(
        MethodElement methodElement,
        ObjectDefBuilder<?> builder,
        VisitorContext visitorContext,
        boolean isJunit5Test,
        boolean isScript,
        Set<String> addedMethodNames,
        @Nullable ClassElement returnTypeOverride,
        MethodElement signatureMethod,
        Map<String, ClassElement> signatureTypeArguments) {
        addBridgeMethod(methodElement, builder, visitorContext, isJunit5Test, isScript, addedMethodNames, returnTypeOverride, signatureMethod, signatureMethod, signatureTypeArguments);
    }

    private void addBridgeMethod(
        MethodElement methodElement,
        ObjectDefBuilder<?> builder,
        VisitorContext visitorContext,
        boolean isJunit5Test,
        boolean isScript,
        Set<String> addedMethodNames,
        @Nullable ClassElement returnTypeOverride,
        MethodElement signatureMethod,
        MethodElement resolvedSignatureMethod,
        Map<String, ClassElement> signatureTypeArguments) {
        String pythonFunctionName = methodElement.getName();
        String key = bridgeMethodKey(methodElement);
        // Check if method name has already been added to avoid duplicates
        if (addedMethodNames.contains(key)) {
            return;
        }

        addedMethodNames.add(key);

        if (isDeclaredBeanMethod(methodElement.getAnnotationMetadata())) {
            // verify return type exists
            if (methodElement instanceof PythonMethodElement pythonMethodElement
                && pythonMethodElement.getNativeType().returnType().typeAnnotation() == null) {
                throw new ProcessingException(methodElement, "Factory methods declared with @Bean must specify a return type. For example: @Bean\n" +
                    "    def foo(self) -> Foo:");
            }
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

        Map<String, ClassElement> inferredMethodBounds = inferMethodTypeBounds(signatureMethod, methodElement);
        Map<String, ClassElement> bridgeSignatureTypeArguments = withoutDeclaredMethodTypeVariables(signatureTypeArguments, signatureMethod);
        ClassElement effectiveReturnType = effectiveBridgeReturnType(methodElement, returnTypeOverride);
        TypeDef methodSourceReturnType = bridgeSourceReturnType(methodElement, signatureMethod, resolvedSignatureMethod, effectiveReturnType, returnTypeOverride, isJunit5Test, bridgeSignatureTypeArguments);
        boolean returnsMethodTypeVariable = !(methodElement instanceof PythonMethodElement) && !signatureMethod.getDeclaredTypeVariables().isEmpty();
        MethodDef.MethodDefBuilder methodBuilder = MethodDef.builder(pythonFunctionName)
            .returns(methodSourceReturnType);
        if (methodElement.isStatic()) {
            methodBuilder.addModifiers(Modifier.PUBLIC, Modifier.STATIC);
        } else {
            methodBuilder.addModifiers(Modifier.PUBLIC);
        }
        addMethodTypeVariables(signatureMethod, methodBuilder, bridgeSignatureTypeArguments, inferredMethodBounds);

        copyAnnotations(methodElement, methodBuilder, ANNOTATION_PACKAGES_TO_COPY, visitorContext);
        @NonNull ParameterElement[] parameters = methodElement.getParameters();
        @NonNull ParameterElement[] signatureParameters = signatureMethod.getParameters();
        @NonNull ParameterElement[] resolvedSignatureParameters = resolvedSignatureMethod.getParameters();
        for (int i = 0; i < parameters.length; i++) {
            @NonNull ParameterElement parameter = parameters[i];
            ParameterElement signatureParameter = i < signatureParameters.length ? signatureParameters[i] : parameter;
            ParameterElement resolvedSignatureParameter = i < resolvedSignatureParameters.length ? resolvedSignatureParameters[i] : signatureParameter;
            ParameterDef parameterDef = ParameterDef
                .builder(parameter.getName(), bridgeSourceParameterType(signatureMethod, signatureParameter, resolvedSignatureParameter, parameter, bridgeSignatureTypeArguments)).build();
            methodBuilder.addParameter(parameterDef);
        }

        builder.addMethod(methodBuilder
            .build(((aThis, methodParameters) -> {
                List<ExpressionDef> parameterExpressions = new ArrayList<>();
                ExpressionDef invokedValue;
                if (methodElement.isStatic()) {
                    List<ExpressionDef> arguments = new ArrayList<>();
                    ClassElement declaringType = methodElement.getDeclaringType();
                    arguments.add(ExpressionDef.constant(declaringType.getPackageName()));
                    arguments.add(ExpressionDef.constant(pythonSimpleName(declaringType)));
                    arguments.add(ExpressionDef.constant(pythonFunctionName));
                    arguments.addAll(methodParameters);
                    invokedValue = CONTEXT_HOLDER.invokeStatic(
                        "invokeStaticMethod",
                        POLYGLOT_VALUE,
                        arguments
                    );
                } else {
                    var targetValue = aThis.invoke(AS_POLYGLOT_VALUE, POLYGLOT_VALUE);
                    var targetContext = targetValue.invoke("getContext", POLYGLOT_CONTEXT);
                    for (int i = 0; i < parameters.length; i++) {
                        @NonNull ParameterElement parameter = parameters[i];
                        VariableDef.MethodParameter methodParameter = methodParameters.get(i);
                        coerceParameterToPolyglotValue(parameter, parameterExpressions, methodParameter, targetContext);
                    }
                    invokedValue = RUNTIME_UTIL.invokeStatic(
                        "invokePythonMethod",
                        POLYGLOT_VALUE,
                        targetValue,
                        ExpressionDef.constant(pythonFunctionName),
                        TypeDef.OBJECT.array().instantiate(parameterExpressions)
                    );
                }

                if (isJunit5Test) {
                    return (StatementDef) invokedValue;
                } else {
                    if (effectiveReturnType.isVoid()) {
                        return (StatementDef) invokedValue;
                    } else {
                        boolean bridgeSignature = signatureMethod != methodElement
                            || !bridgeSignatureTypeArguments.isEmpty()
                            || returnTypeOverride != null
                            || returnsMethodTypeVariable;
                        return returnConvertedValue(allClasses, effectiveReturnType, invokedValue, bridgeSignature ? methodSourceReturnType : null);
                    }
                }
            })));
    }

    private static TypeDef bridgeSourceReturnType(
        MethodElement methodElement,
        MethodElement signatureMethod,
        MethodElement resolvedSignatureMethod,
        ClassElement effectiveReturnType,
        @Nullable ClassElement returnTypeOverride,
        boolean isJunit5Test,
        Map<String, ClassElement> signatureTypeArguments
    ) {
        if (methodElement instanceof PythonMethodElement && signatureMethod == methodElement && signatureTypeArguments.isEmpty()) {
            return methodReturnType(effectiveReturnType, isJunit5Test);
        }
        if (returnTypeOverride != null) {
            return methodReturnType(returnTypeOverride, isJunit5Test);
        }
        if (isJunit5Test) {
            return TypeDef.Primitive.VOID;
        }
        ClassElement resolvedReturnType = signatureMethod == resolvedSignatureMethod
            ? methodElement.getGenericReturnType()
            : resolvedSignatureMethod.getGenericReturnType();
        return bridgeSignatureType(signatureMethod.getGenericReturnType(), resolvedReturnType, signatureTypeArguments);
    }

    private static TypeDef bridgeSourceParameterType(
        MethodElement signatureMethod,
        ParameterElement signatureParameter,
        ParameterElement resolvedSignatureParameter,
        ParameterElement parameter,
        Map<String, ClassElement> signatureTypeArguments
    ) {
        ClassElement signatureType = signatureParameter.getGenericType();
        if (isRawGenericParameterSignature(signatureParameter, signatureType)) {
            return javaClassType(signatureType);
        }
        List<? extends GenericPlaceholderElement> methodTypeVariables = signatureMethod.getDeclaredTypeVariables();
        if (Class.class.getName().equals(signatureType.getName()) && methodTypeVariables.size() == 1) {
            return TypeDef.parameterized(
                ClassTypeDef.of(Class.class),
                List.of(TypeDef.variable(methodTypeVariables.getFirst().getVariableName()))
            );
        }
        ClassElement resolvedParameterType = signatureParameter == resolvedSignatureParameter
            ? parameter.getGenericType()
            : resolvedSignatureParameter.getGenericType();
        return bridgeSignatureType(signatureParameter.getGenericType(), resolvedParameterType, signatureTypeArguments);
    }

    private static boolean isRawGenericParameterSignature(ParameterElement signatureParameter, ClassElement signatureType) {
        if (signatureType instanceof GenericPlaceholderElement || signatureType instanceof WildcardElement) {
            return false;
        }
        Map<String, ClassElement> typeArguments = signatureType.getTypeArguments();
        if (typeArguments.isEmpty() || !objectTypeArguments(typeArguments)) {
            return false;
        }
        VariableElement variableElement = nativeVariableElement(signatureParameter);
        if (variableElement != null) {
            String declaredType = variableElement.asType().toString();
            return !declaredType.contains("<") && sameRawTypeName(signatureType, declaredType);
        }
        return signatureType.isRawType();
    }

    private static @Nullable VariableElement nativeVariableElement(ParameterElement parameter) {
        Object nativeType = parameter.getNativeType();
        if (nativeType instanceof VariableElement variableElement) {
            return variableElement;
        }
        if (nativeType instanceof ElementProvider elementProvider && elementProvider.element() instanceof VariableElement variableElement) {
            return variableElement;
        }
        return null;
    }

    private static ClassElement effectiveBridgeReturnType(MethodElement methodElement, @Nullable ClassElement returnTypeOverride) {
        ClassElement returnType = returnTypeOverride == null ? methodElement.getGenericReturnType() : returnTypeOverride;
        if (returnType instanceof GenericPlaceholderElement placeholder) {
            return resolvedOrFirstBound(placeholder);
        }
        return returnType;
    }

    private static String bridgeMethodKey(MethodElement methodElement) {
        StringBuilder key = new StringBuilder(methodElement.getName()).append('(');
        for (ParameterElement parameter : methodElement.getParameters()) {
            key.append(parameter.getType().getName()).append(';');
        }
        return key.append(')').toString();
    }

    private static boolean shouldBridgeDeclaredPythonMethod(MethodElement methodElement, List<PropertyElement> beanProperties) {
        if (methodElement.isAbstract()
            || methodElement.isStatic()
            || methodElement.isSynthetic()
            || methodElement.isPrivate()) {
            return false;
        }
        String methodName = methodElement.getName();
        if (AS_POLYGLOT_VALUE.equals(methodName)) {
            return false;
        }
        for (PropertyElement beanProperty : beanProperties) {
            if (beanProperty.getReadMethod().map(MethodElement::getName).filter(methodName::equals).isPresent()
                || beanProperty.getWriteMethod().map(MethodElement::getName).filter(methodName::equals).isPresent()) {
                return false;
            }
        }
        return true;
    }

    private static boolean isDynamicBeanProperty(PropertyElement beanProperty) {
        return beanProperty.getReadMethod().filter(method -> !method.isSynthetic()).isPresent()
            || beanProperty.getWriteMethod().filter(method -> !method.isSynthetic()).isPresent();
    }

    private static MethodElement resolveDeclaredBridgeMethod(ClassElement element, MethodElement interfaceMethod) {
        for (MethodElement method : element.getEnclosedElements(ElementQuery.ALL_METHODS.onlyDeclared().onlyInstance())) {
            // Python annotations may omit the return type even when a Java interface
            // requires one. Keep the declared Python method so its parameter metadata
            // can guide the generated Java signature; the bridge return type is still
            // derived from the Java interface metadata.
            if (hasCompatibleBridgeSignature(method, interfaceMethod)) {
                return method;
            }
        }
        return interfaceMethod;
    }

    private static MethodElement withOwningInterface(MethodElement method, ClassElement anInterface) {
        if (method instanceof PythonMethodElement) {
            return method;
        }
        try {
            return method.withNewOwningType(anInterface);
        } catch (RuntimeException e) {
            return method;
        }
    }

    private static @Nullable ClassElement resolveInterfaceBridgeReturnType(MethodElement method, ClassElement anInterface) {
        Map<String, ClassElement> typeArguments = anInterface.getTypeArguments();
        if (typeArguments.isEmpty()) {
            return null;
        }
        ClassElement genericReturnType = method.getGenericReturnType();
        if ("getAnnotationType".equals(method.getName())) {
            ClassElement annotationType = annotationTypeArgument(typeArguments);
            if (annotationType != null) {
                return ClassElement.of(Class.class, AnnotationMetadata.EMPTY_METADATA, Map.of("T", annotationType));
            }
        }
        ClassElement resolvedReturnType = resolveInterfaceType(genericReturnType, typeArguments);
        if (resolvedReturnType != null) {
            return resolvedReturnType;
        }
        if (genericReturnType instanceof GenericPlaceholderElement placeholder) {
            return typeArguments.get(placeholder.getVariableName());
        }
        if (genericReturnType.isVoid() || !Object.class.getName().equals(genericReturnType.getName())) {
            return null;
        }
        resolvedReturnType = null;
        for (ClassElement typeArgument : typeArguments.values()) {
            resolvedReturnType = typeArgument;
        }
        return resolvedReturnType;
    }

    private static @Nullable ClassElement annotationTypeArgument(Map<String, ClassElement> typeArguments) {
        ClassElement annotationType = typeArguments.get("A");
        if (annotationType == null) {
            for (ClassElement typeArgument : typeArguments.values()) {
                if (typeArgument.isAssignable(Annotation.class)) {
                    annotationType = typeArgument;
                    break;
                }
            }
        }
        if (annotationType instanceof GenericPlaceholderElement placeholder) {
            return resolvedOrFirstBound(placeholder);
        }
        return annotationType;
    }

    private static @Nullable ClassElement resolveInterfaceType(ClassElement type, Map<String, ClassElement> interfaceTypeArguments) {
        if (type instanceof GenericPlaceholderElement placeholder) {
            return interfaceTypeArguments.get(placeholder.getVariableName());
        }
        Map<String, ClassElement> typeArguments = type.getTypeArguments();
        if (typeArguments.isEmpty()) {
            return null;
        }
        Map<String, ClassElement> resolvedTypeArguments = new LinkedHashMap<>(typeArguments.size());
        boolean resolvedAny = false;
        for (Map.Entry<String, ClassElement> entry : typeArguments.entrySet()) {
            ClassElement typeArgument = entry.getValue();
            ClassElement resolvedTypeArgument = resolveInterfaceType(typeArgument, interfaceTypeArguments);
            if (resolvedTypeArgument == null && typeArguments.size() == 1 && interfaceTypeArguments.size() == 1 && Object.class.getName().equals(typeArgument.getName())) {
                resolvedTypeArgument = interfaceTypeArguments.values().iterator().next();
            }
            if (resolvedTypeArgument == null) {
                resolvedTypeArguments.put(entry.getKey(), typeArgument);
            } else {
                resolvedAny = true;
                resolvedTypeArguments.put(entry.getKey(), resolvedTypeArgument);
            }
        }
        if (!resolvedAny) {
            return null;
        }
        try {
            return type.withTypeArguments(resolvedTypeArguments);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static boolean hasCompatibleBridgeSignature(MethodElement method, MethodElement interfaceMethod) {
        if (!method.getName().equals(interfaceMethod.getName())) {
            return false;
        }
        ParameterElement[] parameters = method.getParameters();
        ParameterElement[] interfaceParameters = interfaceMethod.getParameters();
        if (parameters.length != interfaceParameters.length) {
            return false;
        }
        for (int i = 0; i < parameters.length; i++) {
            if (!hasCompatibleBridgeParameter(parameters[i], interfaceParameters[i])) {
                return false;
            }
        }
        return true;
    }

    private static boolean hasCompatibleBridgeParameter(ParameterElement parameter, ParameterElement interfaceParameter) {
        String parameterTypeName = parameter.getType().getName();
        String interfaceTypeName = interfaceParameter.getType().getName();
        if (parameterTypeName.equals(interfaceTypeName)) {
            return true;
        }
        String parameterGenericTypeName = parameter.getGenericType().getName();
        String interfaceGenericTypeName = interfaceParameter.getGenericType().getName();
        if (parameterGenericTypeName.equals(interfaceGenericTypeName)) {
            return true;
        }
        if (Object.class.getName().equals(interfaceTypeName) && parameterTypeName.equals(interfaceGenericTypeName)) {
            return true;
        }
        return Class.class.getName().equals(interfaceTypeName) && Object.class.getName().equals(parameterTypeName);
    }

    private static String beanGetterName(String name) {
        return "get" + NameUtils.capitalize(name);
    }

    private static String booleanBeanGetterName(String name) {
        return "is" + NameUtils.capitalize(name);
    }

    private static String beanSetterName(String name) {
        return "set" + NameUtils.capitalize(name);
    }

    private void addValueCoerciblePropertyMemberNames(
        ClassDef.ClassDefBuilder builder,
        List<PropertyElement> beanProperties,
        Map<String, FieldDef> propertyFields
    ) {
        // Precompute JavaBean accessor aliases for ValueCoercible. The runtime proxy only consults
        // these generated tables, avoiding reflection over generated wrapper methods.
        Map<String, String> getterMappings = new LinkedHashMap<>();
        Map<String, String> setterMappings = new LinkedHashMap<>();
        Map<String, PropertyElement> setterProperties = new LinkedHashMap<>();
        for (PropertyElement beanProperty : beanProperties) {
            String propertyName = beanProperty.getName();
            setterProperties.put(propertyName, beanProperty);
            String beanGetter = beanGetterName(propertyName);
            getterMappings.put(beanGetter, propertyName);
            if (isBooleanProperty(beanProperty)) {
                getterMappings.put(booleanBeanGetterName(propertyName), propertyName);
            }
            beanProperty.getReadMethod().ifPresent(method -> getterMappings.put(method.getName(), propertyName));

            String beanSetter = beanSetterName(propertyName);
            setterMappings.put(beanSetter, propertyName);
            setterProperties.put(beanSetter, beanProperty);
            beanProperty.getWriteMethod().ifPresent(method -> {
                setterMappings.put(method.getName(), propertyName);
                setterProperties.put(method.getName(), beanProperty);
            });
        }

        builder.addMethod(MethodDef.builder("micronautValueCoercibleGetterPropertyName")
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC)
            .addParameter("key", TypeDef.STRING)
            .returns(TypeDef.STRING)
            .build((aThis, methodParameters) -> propertyNameMatchBody(methodParameters.getFirst(), getterMappings)));
        builder.addMethod(MethodDef.builder("micronautValueCoercibleSetterPropertyName")
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC)
            .addParameter("key", TypeDef.STRING)
            .returns(TypeDef.STRING)
            .build((aThis, methodParameters) -> propertyNameMatchBody(methodParameters.getFirst(), setterMappings)));
        builder.addMethod(MethodDef.builder("micronautValueCoercibleSetMember")
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC)
            .addParameter("key", TypeDef.STRING)
            .addParameter("value", POLYGLOT_VALUE)
            .returns(TypeDef.Primitive.BOOLEAN)
            .build((aThis, methodParameters) -> propertySetterBody(aThis, methodParameters.getFirst(), methodParameters.get(1), setterProperties, propertyFields)));
    }

    private static StatementDef propertyNameMatchBody(VariableDef.MethodParameter key, Map<String, String> mappings) {
        List<StatementDef> statements = new ArrayList<>(mappings.size() + 1);
        for (Map.Entry<String, String> entry : mappings.entrySet()) {
            statements.add(
                ExpressionDef.constant(entry.getKey())
                    .invoke("equals", TypeDef.Primitive.BOOLEAN, key)
                    .isTrue()
                    .doIf(ExpressionDef.constant(entry.getValue()).returning())
            );
        }
        statements.add(ExpressionDef.nullValue().returning());
        return StatementDef.multi(statements);
    }

    private StatementDef propertySetterBody(
        VariableDef.This aThis,
        VariableDef.MethodParameter key,
        VariableDef.MethodParameter value,
        Map<String, PropertyElement> mappings,
        Map<String, FieldDef> propertyFields
    ) {
        List<StatementDef> statements = new ArrayList<>(mappings.size() + 1);
        for (Map.Entry<String, PropertyElement> entry : mappings.entrySet()) {
            PropertyElement beanProperty = entry.getValue();
            FieldDef field = propertyFields.get(beanProperty.getName());
            if (field == null) {
                continue;
            }
            ExpressionDef convertedValue = convertRuntimeValue(beanProperty.getGenericType(), value);
            statements.add(
                ExpressionDef.constant(entry.getKey())
                    .invoke("equals", TypeDef.Primitive.BOOLEAN, key)
                    .isTrue()
                    .doIf(StatementDef.multi(
                        aThis.field(field).assign(convertedValue),
                        RUNTIME_UTIL.invokeStatic(
                            "putMember",
                            TypeDef.VOID,
                            aThis.invoke(AS_POLYGLOT_VALUE, POLYGLOT_VALUE),
                            ExpressionDef.constant(beanProperty.getName()),
                            coerceTypedElementToPolyglotValue(beanProperty, aThis.field(field)).cast(TypeDef.OBJECT)
                        ),
                        ExpressionDef.trueValue().returning()
                    ))
            );
        }
        statements.add(ExpressionDef.falseValue().returning());
        return StatementDef.multi(statements);
    }

    private void addGetterPojo(PropertyElement beanProperty, ClassDef.ClassDefBuilder builder, FieldDef field) {
        TypeDef propertyType = propertySourceType(beanProperty);
        Optional<MethodElement> rm = beanProperty.getReadMethod();
        boolean isSynthetic = rm.map(MethodElement::isSynthetic).orElse(true);
        String getterName = isSynthetic ? beanGetterName(beanProperty.getName()) :
            rm.get().getName();
        addGetterPojo(beanProperty, builder, field, propertyType, getterName);

        String booleanGetterName = booleanBeanGetterName(beanProperty.getName());
        if (isBooleanProperty(beanProperty) && !booleanGetterName.equals(getterName)) {
            addGetterPojo(beanProperty, builder, field, propertyType, booleanGetterName);
        }
    }

    private static void addGetterPojo(
        PropertyElement beanProperty,
        ClassDef.ClassDefBuilder builder,
        FieldDef field,
        TypeDef propertyType,
        String getterName
    ) {
        MethodDef.MethodDefBuilder getterBuilder = MethodDef
            .builder(getterName)
            .addModifiers(Modifier.PUBLIC)
            .returns(propertyType);

        builder.addMethod(getterBuilder.build(((aThis, methodParameters) -> aThis.field(field).returning())));
    }

    private static boolean isBooleanProperty(PropertyElement beanProperty) {
        TypeDef sourceType = propertySourceType(beanProperty);
        if (sourceType instanceof TypeDef.Primitive primitive && boolean.class.getName().equals(primitive.name())) {
            return true;
        }
        if (sourceType instanceof ClassTypeDef classTypeDef) {
            String sourceTypeName = classTypeDef.getName();
            if (boolean.class.getName().equals(sourceTypeName) || Boolean.class.getName().equals(sourceTypeName)) {
                return true;
            }
        }
        String typeName = beanProperty.getType().getName();
        return "boolean".equals(typeName) || Boolean.class.getName().equals(typeName);
    }

    private void addSetterPojo(PropertyElement beanProperty, ClassDef.ClassDefBuilder builder, FieldDef field) {
        TypeDef returnType = TypeDef.VOID;
        Optional<MethodElement> wm = beanProperty.getWriteMethod();
        boolean isSynthetic = wm.map(MethodElement::isSynthetic).orElse(true);
        String setterName = isSynthetic ? beanSetterName(beanProperty.getName()) :
                                            wm.get().getName();
        MethodDef.MethodDefBuilder propertySetter = MethodDef
            .builder(setterName)
            .addModifiers(Modifier.PUBLIC)
            .returns(returnType);

        propertySetter.addParameter(propertySourceType(beanProperty));

        builder.addMethod(propertySetter.build(((aThis, methodParameters) ->
            aThis.field(field).assign(convertPojoSetterValue(beanProperty, methodParameters.getFirst()))
        )));
    }

    private ExpressionDef convertPojoSetterValue(PropertyElement beanProperty, ExpressionDef methodParameter) {
        ClassElement genericType = beanProperty.getGenericType();
        if (genericType.isAssignable(List.class)) {
            ClassElement componentType = genericType.getFirstTypeArgument().orElse(null);
            if (componentType != null && isGeneratedWrapperType(allClasses, componentType)) {
                return uncheckedCast(RUNTIME_UTIL.invokeStatic(
                    "convertList",
                    List.of(ClassTypeDef.of(List.class), POLYGLOT_VALUE_CONVERTER),
                    ClassTypeDef.of(List.class),
                    methodParameter,
                    generatedWrapperConverter(componentType)
                ), genericType);
            }
        }
        return methodParameter;
    }

    private static @Nullable PropertyElement findBeanProperty(List<PropertyElement> beanProperties, String name) {
        for (PropertyElement beanProperty : beanProperties) {
            if (beanProperty.getName().equals(name)) {
                return beanProperty;
            }
        }
        return null;
    }

    private void addGetterDynamic(PropertyElement beanProperty, ClassDef.ClassDefBuilder builder) {
        TypeDef propertyType = propertySourceType(beanProperty);
        String getterName = beanGetterName(beanProperty.getName());
        addGetterDynamic(beanProperty, builder, propertyType, getterName);

        String booleanGetterName = booleanBeanGetterName(beanProperty.getName());
        if (isBooleanProperty(beanProperty) && !booleanGetterName.equals(getterName)) {
            addGetterDynamic(beanProperty, builder, propertyType, booleanGetterName);
        }
    }

    private void addGetterDynamic(
        PropertyElement beanProperty,
        ClassDef.ClassDefBuilder builder,
        TypeDef propertyType,
        String getterName
    ) {
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
            return returnConvertedValue(allClasses, beanProperty.getGenericType(), invokedValue);
        })));
    }

    private void addNamedGetterDynamic(PropertyElement beanProperty, ClassDef.ClassDefBuilder builder) {
        TypeDef propertyType = propertySourceType(beanProperty);
        String getterName = beanProperty.getReadMethod().map(MethodElement::getName).orElse(beanProperty.getName());
        addGetterDynamic(beanProperty, builder, propertyType, getterName);

        String booleanGetterName = booleanBeanGetterName(beanProperty.getName());
        if (isBooleanProperty(beanProperty) && !booleanGetterName.equals(getterName)) {
            addGetterDynamic(beanProperty, builder, propertyType, booleanGetterName);
        }
    }

    private void addSetterDynamic(PropertyElement beanProperty, ClassDef.ClassDefBuilder builder, VisitorContext visitorContext) {
        TypeDef returnType = TypeDef.VOID;
        String setterName = beanSetterName(beanProperty.getName());
        MethodDef.MethodDefBuilder propertySetter = MethodDef
            .builder(setterName)
            .addModifiers(Modifier.PUBLIC)
            .returns(returnType);
        beanProperty.getWriteMethod().ifPresent(method -> copyAnnotations(method, propertySetter, ANNOTATION_PACKAGES_TO_COPY, visitorContext));

        propertySetter.addParameter(propertySourceType(beanProperty));

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

    private void addNamedSetterDynamic(PropertyElement beanProperty, ClassDef.ClassDefBuilder builder, VisitorContext visitorContext) {
        TypeDef returnType = TypeDef.VOID;
        String setterName = beanProperty.getWriteMethod().map(MethodElement::getName).orElse(beanProperty.getName());
        MethodDef.MethodDefBuilder propertySetter = MethodDef
            .builder(setterName)
            .addModifiers(Modifier.PUBLIC)
            .returns(returnType);
        beanProperty.getWriteMethod().ifPresent(method -> copyAnnotations(method, propertySetter, ANNOTATION_PACKAGES_TO_COPY, visitorContext));

        propertySetter.addParameter(propertySourceType(beanProperty));

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
        TypeDef propertyType = propertySourceType(beanProperty);
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
            return returnConvertedValue(allClasses, beanProperty.getGenericType(), invokedValue);
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

        propertySetter.addParameter(propertySourceType(beanProperty));

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
        } else if (returnType.isArray()) {
            return convertRuntimeValue(returnType, invokedValue);
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
                case "java.lang.Object" ->
                    RUNTIME_UTIL.invokeStatic("convertObject", ClassTypeDef.OBJECT, invokedValue);
                default -> {
                    // Check for collection types
                    if (returnType.isAssignable(List.class)) {
                        ClassElement componentType = returnType.getFirstTypeArgument().orElse(null);
                        if (componentType != null && isGeneratedWrapperType(allClasses, componentType)) {
                            yield uncheckedCast(RUNTIME_UTIL.invokeStatic(
                                "convertList",
                                List.of(POLYGLOT_VALUE, POLYGLOT_VALUE_CONVERTER),
                                ClassTypeDef.of(List.class),
                                invokedValue,
                                generatedWrapperConverter(componentType)
                            ), returnType);
                        }
                        ExpressionDef genericType = toClassExpression(componentType);
                        yield uncheckedCast(RUNTIME_UTIL
                            .invokeStatic("convertList", ClassTypeDef.of(List.class),
                                invokedValue, genericType), returnType);
                    } else if (returnType.isAssignable(Map.class)) {
                        Map<String, ClassElement> typeArguments = returnType.getTypeArguments();
                        ExpressionDef keyType = toClassExpression(typeArguments.get("K"));
                        ExpressionDef valueType = toClassExpression(typeArguments.get("V"));
                        yield uncheckedCast(RUNTIME_UTIL
                            .invokeStatic("convertMap", ClassTypeDef.of(Map.class),
                                invokedValue, keyType, valueType), returnType);
                    } else if (returnType.isAssignable(Set.class)) {
                        ClassElement componentType = returnType.getFirstTypeArgument().orElse(null);
                        ExpressionDef genericType = toClassExpression(componentType);

                        yield uncheckedCast(RUNTIME_UTIL
                            .invokeStatic("convertSet", ClassTypeDef.of(Set.class),
                                invokedValue, genericType), returnType);
                    } else if (returnType.isAssignable(java.util.Optional.class)) {
                        ClassElement componentType = returnType.getFirstTypeArgument().orElse(null);
                        ExpressionDef genericType = toClassExpression(componentType);

                        yield uncheckedCast(RUNTIME_UTIL
                            .invokeStatic("convertOptional", ClassTypeDef.of(java.util.Optional.class),
                                invokedValue, genericType), returnType);
                    } else if (returnType.isAssignable(PUBLISHER)) {
                        ClassElement componentType = returnType.getFirstTypeArgument().orElse(null);
                        yield uncheckedCast(RUNTIME_UTIL
                            .invokeStatic("convertPublisher", ClassTypeDef.of(PUBLISHER),
                                invokedValue, toClassExpression(componentType)), returnType);
                    } else if (returnType.isAssignable(HTTP_RESPONSE)) {
                        ClassElement bodyType = returnType.getFirstTypeArgument().orElse(null);
                        if (bodyType == null || Object.class.getName().equals(bodyType.getName())) {
                            yield RUNTIME_UTIL
                                .invokeStatic("convertHttpResponse", ClassTypeDef.OBJECT,
                                    invokedValue, CLASS_OBJECT)
                                .cast(ClassTypeDef.of(HTTP_RESPONSE));
                        }
                        yield RUNTIME_UTIL
                            .invokeStatic("convertHttpResponse", ClassTypeDef.OBJECT,
                                invokedValue, toClassExpression(bodyType))
                            .cast(ClassTypeDef.of(HTTP_RESPONSE));
                    } else {
                        if (isGeneratedWrapperType(allClasses, returnType)) {
                            yield javaClassType(returnType)
                                .invokeStatic(FROM_POLYGLOT_VALUE, POLYGLOT_VALUE, invokedValue);
                        } else {
                            yield convertRuntimeValue(returnType, invokedValue);
                        }
                    }
                }
            };
        }
    }

    private static StatementDef returnConvertedValue(Map<String, ClassElement> allClasses, ClassElement returnType, ExpressionDef invokedValue) {
        return returnConvertedValue(allClasses, returnType, invokedValue, null);
    }

    private static StatementDef returnConvertedValue(
        Map<String, ClassElement> allClasses,
        ClassElement returnType,
        ExpressionDef invokedValue,
        @Nullable TypeDef castType
    ) {
        if (returnType.isVoid() || TypeDef.VOID.equals(castType) || TypeDef.Primitive.VOID.equals(castType)) {
            return (StatementDef) invokedValue;
        }
        return invokedValue.newLocal("pythonResult", result ->
            castReturnValue(handleReturnType(allClasses, returnType, result), castType).returning()
        );
    }

    private static ExpressionDef castReturnValue(ExpressionDef expression, @Nullable TypeDef castType) {
        if (castType == null) {
            return expression;
        }
        return RUNTIME_UTIL.invokeStatic("asObject", castType, expression);
    }

    private static StatementDef fromPolyglotValueBody(ClassTypeDef thisType, VariableDef.MethodParameter value) {
        return StatementDef.multi(
            RUNTIME_UTIL.invokeStatic("isNone", TypeDef.Primitive.BOOLEAN, value)
                .isTrue()
                .doIf(ExpressionDef.nullValue().returning()),
            RUNTIME_UTIL.invokeStatic(
                    "unwrapHostObject",
                    TypeDef.OBJECT,
                    value,
                    thisType.getStaticField("class", TypeDef.CLASS)
                )
                .newLocal("hostObject", hostObject ->
                    hostObject.isNonNull().doIf(hostObject.cast(thisType).returning())
                ),
            thisType.instantiate(value).returning()
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
        } else if (componentType instanceof GenericPlaceholderElement placeholder) {
            genericType = classLiteral(resolvedOrFirstBound(placeholder));
        } else {
            genericType = classLiteral(componentType);
        }
        return genericType;
    }

    private static ExpressionDef uncheckedCast(ExpressionDef expression, ClassElement targetType) {
        return RUNTIME_UTIL.invokeStatic("asObject", TypeDef.OBJECT, expression).cast(sourceSignatureType(targetType));
    }

    private static ExpressionDef convertRuntimeValue(ClassElement targetType, ExpressionDef value) {
        return RUNTIME_UTIL
            .invokeStatic("convertValue", ClassTypeDef.OBJECT,
                value, classLiteral(targetType))
            .cast(sourceSignatureType(targetType));
    }

    private static ExpressionDef classLiteral(ClassElement targetType) {
        if (targetType instanceof GenericPlaceholderElement placeholder) {
            return ExpressionDef.constant(erasedType(resolvedOrFirstBound(placeholder)));
        }
        return ExpressionDef.constant(erasedType(targetType));
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
        return !type.isInterface() && !type.isEnum() && (
            allClasses.containsKey(type.getName()) ||
                type.isAssignable("io.micronaut.context.python.ValueCoercible")
        );
    }

    private static boolean isCollectionLike(ClassElement type) {
        return type.isAssignable(List.class) || type.isAssignable(Map.class) || type.isAssignable(Set.class);
    }

    private ExpressionDef convertValueForType(ClassElement type, ExpressionDef member) {
        if (type.isArray()) {
            return convertRuntimeValue(type, member);
        } else if (type.isPrimitive()) {
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
                        if (componentType != null && isGeneratedWrapperType(allClasses, componentType)) {
                            return RUNTIME_UTIL.invokeStatic(
                                "convertList",
                                List.of(POLYGLOT_VALUE, POLYGLOT_VALUE_CONVERTER),
                                ClassTypeDef.of(List.class),
                                member,
                                generatedWrapperConverter(componentType)
                            );
                        }
                        ExpressionDef genericType = toClassExpression(componentType);
                        return uncheckedCast(RUNTIME_UTIL.invokeStatic("convertList", ClassTypeDef.of(List.class), member, genericType), type);
                    } else if (type.isAssignable(Map.class)) {
                        Map<String, ClassElement> typeArguments = type.getTypeArguments();
                        ExpressionDef keyType = toClassExpression(typeArguments.get("K"));
                        ExpressionDef valueType = toClassExpression(typeArguments.get("V"));
                        return uncheckedCast(RUNTIME_UTIL.invokeStatic("convertMap", ClassTypeDef.of(Map.class), member, keyType, valueType), type);
                    } else if (type.isAssignable(Set.class)) {
                        ClassElement componentType = type.getFirstTypeArgument().orElse(null);
                        ExpressionDef genericType = toClassExpression(componentType);
                        return uncheckedCast(RUNTIME_UTIL.invokeStatic("convertSet", ClassTypeDef.of(Set.class), member, genericType), type);
                    } else if (type.isAssignable(java.util.Optional.class)) {
                        ClassElement componentType = type.getFirstTypeArgument().orElse(null);
                        ExpressionDef genericType = toClassExpression(componentType);
                        return uncheckedCast(RUNTIME_UTIL.invokeStatic("convertOptional", ClassTypeDef.of(java.util.Optional.class), member, genericType), type);
                    } else if (isGeneratedWrapperType(allClasses, type)) {
                        return javaClassType(type).invokeStatic(FROM_POLYGLOT_VALUE, POLYGLOT_VALUE, member);
                    } else {
                        return convertRuntimeValue(type, member);
                    }
            }
        }
    }

    private static ExpressionDef generatedWrapperConverter(ClassElement componentType) {
        MethodDef convertMethod = MethodDef.builder("convert")
            .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
            .addParameter(ParameterDef.of("element", POLYGLOT_VALUE))
            .returns(TypeDef.OBJECT)
            .build();
        MethodDef implementation = MethodDef.override(convertMethod)
            .build((aThis, methodParameters) -> ClassTypeDef.of(componentType)
                .invokeStatic(FROM_POLYGLOT_VALUE, POLYGLOT_VALUE, methodParameters.get(0))
                .returning());
        return new ExpressionDef.Lambda(POLYGLOT_VALUE_CONVERTER, convertMethod, implementation);
    }

    private static boolean isDeclaredBeanMethod(AnnotationMetadata annotationMetadata) {
        // Visitors can add @Bean directly to Python methods after metadata parsing. Those
        // methods still need Java bridge methods so generated bean definitions can call them.
        return annotationMetadata.hasDeclaredAnnotation(Bean.class)
            || annotationMetadata.hasDeclaredStereotype(Bean.class);
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

    record EnumEntry(
        EnumDef enumDef,
        ClassDef converterDef,
        Element originatingElement) {
    }

    record InterfaceEntry(
        InterfaceDef interfaceDef,
        Element originatingElement) {
    }

    private static final class EmptyAnnotationArray {
        private static final EmptyAnnotationArray INSTANCE = new EmptyAnnotationArray();

        private EmptyAnnotationArray() {
        }

        @Override
        public String toString() {
            return "{}";
        }
    }
}
