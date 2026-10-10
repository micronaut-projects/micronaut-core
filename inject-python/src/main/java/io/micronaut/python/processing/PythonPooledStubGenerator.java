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

import io.micronaut.aop.Around;
import io.micronaut.context.annotation.Bean;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationUtil;
import io.micronaut.core.annotation.Vetoed;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.ElementQuery;
import io.micronaut.inject.ast.MemberElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.ParameterElement;
import io.micronaut.inject.ast.TypedElement;
import io.micronaut.inject.ast.PropertyElement;
import io.micronaut.inject.processing.ProcessingException;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.python.processing.element.PythonMethodElement;
import io.micronaut.python.processing.staticcompile.StaticCompilationPlan;
import io.micronaut.python.processing.staticcompile.StaticBodyGenerator;
import io.micronaut.python.processing.staticcompile.Ir;
import io.micronaut.python.processing.element.AbstractPythonClassElement;
import io.micronaut.core.util.StringUtils;
import io.micronaut.python.processing.element.PythonScriptElement;
import io.micronaut.sourcegen.model.ClassDef;
import io.micronaut.sourcegen.model.ClassTypeDef;
import io.micronaut.sourcegen.model.ExpressionDef;
import io.micronaut.sourcegen.model.FieldDef;
import io.micronaut.sourcegen.model.MethodDef;
import io.micronaut.sourcegen.model.ParameterDef;
import io.micronaut.sourcegen.model.StatementDef;
import io.micronaut.sourcegen.model.TypeDef;
import org.jspecify.annotations.Nullable;

import javax.lang.model.element.Modifier;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;

import static io.micronaut.python.processing.PythonStubGenerator.AS_POLYGLOT_VALUE;
import static io.micronaut.python.processing.PythonStubGenerator.PYTHON_CONTEXT_RUNTIME;
import static io.micronaut.python.processing.PythonStubGenerator.FROM_POLYGLOT_VALUE;
import static io.micronaut.python.processing.PythonStubGenerator.POLYGLOT_VALUE;
import static io.micronaut.python.processing.PythonStubGenerator.PYTHON_ASYNCIO_RUNTIME;
import static io.micronaut.python.processing.PythonStubGenerator.addReferencedPythonClassReferenceFields;
import static io.micronaut.python.processing.PythonStubGenerator.convertedElementPublisher;
import static io.micronaut.python.processing.PythonStubGenerator.erasedType;
import static io.micronaut.python.processing.PythonStubGenerator.returnConvertedValue;
import static io.micronaut.python.processing.PythonStubGenerator.PUBLISHER;
import static io.micronaut.python.processing.PythonStubGenerator.isAsyncGeneratorPythonMethod;
import static io.micronaut.python.processing.PythonStubGenerator.isAsyncPythonMethod;
import static io.micronaut.python.processing.PythonStubGenerator.pythonClassReference;
import static io.micronaut.python.processing.PythonStubGenerator.pythonClassAnnotation;
import static io.micronaut.python.processing.PythonStubGenerator.pythonClassReferenceField;
import static io.micronaut.python.processing.PythonStubGenerator.propertyType;

final class PythonPooledStubGenerator {
    /**
     * The option naming the dependencies whose pooling cost this compilation already accepts, or
     * {@code false} to report none of them. Camel case in the tail because javac accepts only a
     * dot-separated sequence of identifiers as the key of a {@code -A} option. Under the prefix of
     * {@code PythonPoolConfiguration}, which declares it, so the configuration validation of an
     * application that sets it in its own configuration knows the property.
     */
    static final String IGNORE_OPTION = "micronaut.python.pool.ignoreDependencies";
    /** The name {@link #IGNORE_OPTION} was released under (5.2.11), still read when it is not set. */
    static final String LEGACY_IGNORE_OPTION = "micronaut.python.pooled.ignoreDependencies";

    private static final ClassTypeDef POLYGLOT_CONTEXT = ClassTypeDef.of("org.graalvm.polyglot.Context");
    private static final String CONTEXT_POOLED = "io.micronaut.context.python.scope.ContextPooled";
    private static final ClassTypeDef POOLED_INSTANCE = ClassTypeDef.of("io.micronaut.context.python.PythonPooledInstance");
    /** The entry points a pooled bean that owns its per-context values is generated against. */
    private static final ClassTypeDef PYTHON_POOLED_RUNTIME = ClassTypeDef.of("io.micronaut.context.python.PythonPooledRuntime");
    private static final String POOLED_INSTANCE_FIELD = "graalpyPooledInstance";
    private static final String FROM_POOLED_VALUE_FACTORY = "fromPooledValueFactory";
    private static final TypeDef VALUE_FACTORY = TypeDef.parameterized(
        ClassTypeDef.of("java.util.function.Function"), POLYGLOT_CONTEXT, POLYGLOT_VALUE);
    private static final String REPORTED_POOLED_DEPENDENCIES = "micronaut.python.reported-pooled-dependencies";

    /** The marker for "report nothing", which {@code false} selects. */
    private static final String ALL = "*";

    /**
     * Warns about a pooled type whose Python dependency is pinned to a single context.
     *
     * <p>A pooled type exists once per context, so it gains from holding only references that
     * can exist in whichever context serves a call. A singleton Python bean cannot: it is one
     * instance living in one context, and a pooled instance elsewhere holding a reference to it
     * puts that context's work back through the owning one. That is the cost pooling is there
     * to avoid, and nothing else reports it -- the application runs, and is only slower under
     * concurrency, by more the more contexts there are.
     *
     * <p>A dependency that is not a Python type is unrestricted: a Java bean has no context
     * affinity and no interpreter lock to contend for, so a pooled type may hold as many as it
     * likes.
     *
     * <p>This is a warning rather than an error on purpose. The shape already exists and works:
     * a route module is pooled and commonly injects singleton services, so rejecting it would
     * break code that compiles today for the sake of a performance characteristic. The warning
     * names the cost and leaves the choice.
     *
     * @param context The visitor context, for reporting
     * @param element The pooled Python type
     * @param dependencies Its injected dependencies
     */
    private static void warnAboutContextBoundDependencies(VisitorContext context,
                                                          ClassElement element,
                                                          List<? extends TypedElement> dependencies) {
        Set<String> ignored = ignoredPooledDependencies(context);
        if (ignored.contains(ALL)) {
            return;
        }
        for (TypedElement dependency : dependencies) {
            ClassElement dependencyType = dependency.getGenericType();
            if (!isPythonType(dependencyType) || dependencyType.hasStereotype(CONTEXT_POOLED)) {
                continue;
            }
            if (ignored.contains(dependencyType.getSimpleName()) || ignored.contains(dependencyType.getName())) {
                continue;
            }
            if (dependencyType.hasStereotype(AnnotationUtil.SINGLETON)) {
                if (!alreadyReported(context, element, dependency)) {
                    context.warn("The pooled type [" + element.getSimpleName() + "] depends on the singleton Python bean ["
                        + dependencyType.getSimpleName() + "] through [" + dependency.getName() + "]. A pooled type exists "
                        + "once per context and a singleton Python bean exists once in one context, so calls through this "
                        + "dependency run in that one context however many the pool has, and the gain from pooling is lost "
                        + "for them. Make [" + dependencyType.getSimpleName() + "] pooled as well, or give it a scope that "
                        + "allows an instance per context. A dependency on a Java type has no such cost.", element);
                }
            }
        }
    }

    /**
     * The dependencies this compilation does not want reported.
     *
     * <p>The warning names a cost, not a fault, and the shape it names is sometimes the one the
     * application wants: a bean that is reached once per request rather than per row, or one whose
     * work is a Java call anyway, loses little by living in a single context. Until this option
     * existed there was no way to say so, and a warning that cannot be acted on or silenced is one
     * that gets ignored wholesale -- including the pairing that does matter.
     *
     * <p>Read from {@code -A}{@value #IGNORE_OPTION}{@code =...}, a comma-separated list of the
     * dependency types to leave unreported, by simple or qualified name. The value {@code false}
     * turns the warning off altogether. Following {@code PythonReflectionGate}, the same name is
     * accepted as a system property of the compiler JVM, so a build that cannot pass {@code -A}
     * options has a way in. The name it was released under, {@value #LEGACY_IGNORE_OPTION}, is read
     * the same ways when the current one is not set.
     *
     * @param context The visitor context
     * @return The dependency names to skip, or a set containing {@link #ALL}
     */
    private static Set<String> ignoredPooledDependencies(VisitorContext context) {
        String value = ignoreOptionValue(context, IGNORE_OPTION);
        if (StringUtils.isEmpty(value)) {
            value = ignoreOptionValue(context, LEGACY_IGNORE_OPTION);
        }
        if (StringUtils.isEmpty(value)) {
            return Set.of();
        }
        if (StringUtils.FALSE.equalsIgnoreCase(value.trim())) {
            return Set.of(ALL);
        }
        Set<String> names = new HashSet<>();
        for (String name : value.split(",")) {
            String trimmed = name.trim();
            if (!trimmed.isEmpty()) {
                names.add(trimmed);
            }
        }
        return names;
    }

    private static @Nullable String ignoreOptionValue(VisitorContext context, String option) {
        String value = context.getOptions().get(option);
        return StringUtils.isEmpty(value) ? System.getProperty(option) : value;
    }

    /**
     * Whether this pairing has already been reported during this compilation.
     *
     * <p>Main and test sources are processed in separate rounds that visit the same modules, so
     * without this every warning is emitted twice. Recorded on the visitor context rather than in
     * static state, so the set belongs to the compilation and cannot outlive it in a daemon.
     *
     * @param context The visitor context
     * @param element The pooled type
     * @param dependency The dependency warned about
     * @return Whether the pairing was reported before this call
     */
    @SuppressWarnings("unchecked")
    private static boolean alreadyReported(VisitorContext context, ClassElement element, TypedElement dependency) {
        Set<String> reported = context.get(REPORTED_POOLED_DEPENDENCIES, Set.class)
            .map(set -> (Set<String>) set)
            .orElse(null);
        if (reported == null) {
            reported = ConcurrentHashMap.newKeySet();
            context.put(REPORTED_POOLED_DEPENDENCIES, reported);
        }
        return !reported.add(element.getName() + " -> " + dependency.getName());
    }

    /**
     * A module's injected members.
     *
     * <p>Queried as members rather than through {@code ALL_FIELDS}, which returns none of them:
     * a module-level {@code Annotated[T, Inject]} is the shape {@code PythonScriptElement} looks
     * for when it decides the module is a bean, so this asks the same way.
     *
     * @param scriptElement The module
     * @return Its injected members, those of them that have a type
     */
    private static List<TypedElement> injectedMembers(PythonScriptElement scriptElement) {
        return scriptElement.getEnclosedElements(ElementQuery.of(MemberElement.class))
            .stream()
            .filter(member -> member.hasStereotype(AnnotationUtil.INJECT))
            .filter(TypedElement.class::isInstance)
            .map(TypedElement.class::cast)
            .toList();
    }

    /**
     * Whether a type has a Python implementation, and so belongs to a context.
     *
     * <p>Being declared in Python is not enough. A {@code Protocol} or an otherwise abstract
     * Python type -- a Micronaut Data repository, a bean mapper, any introduction interface -- is
     * implemented by generated Java. It has no Python instance, so no context it lives in and no
     * interpreter lock to contend for, and warning about one would be noise. It also cannot be
     * pooled: a pooled type is instantiated once per context, and a {@code Protocol} refuses
     * instantiation outright.
     *
     * @param type The type
     * @return Whether it is a Python type with a Python implementation
     */
    private static boolean isPythonType(ClassElement type) {
        if (!(type instanceof AbstractPythonClassElement) && !(type instanceof PythonScriptElement)) {
            return false;
        }
        return !type.isInterface() && !type.isAbstract();
    }

    static ClassDef.ClassDefBuilder generatePooledClass(AbstractPythonClassElement element,
                                                        VisitorContext context,
                                                        Map<String, ClassElement> allClasses) {
        String typeName = element.getName();
        var builder = ClassDef.builder(typeName).addModifiers(Modifier.PUBLIC);
        FieldDef pythonClassReference = pythonClassReferenceField("__PYTHON_CLASS_REFERENCE", element);
        builder.addField(pythonClassReference);
        builder.addAnnotation(Vetoed.class);
        builder.addAnnotation(pythonClassAnnotation(element));
        builder.addSuperinterface(ClassTypeDef.of("io.micronaut.context.python.PooledValueCoercible"));

        ClassElement superType = element.getSuperType().orElse(null);
        if (superType instanceof AbstractPythonClassElement) {
            builder.superclass(ClassTypeDef.of(PythonStubGenerator.javaTypeName(superType)));
        }

        Map<String, FieldDef> injectedParameterFields = addInjectedParameterFields(element, builder);
        List<PropertyElement> beanProperties = element.getBeanProperties().stream()
            .filter(property -> !injectedParameterFields.containsKey(property.getName()))
            .toList();
        if (!beanProperties.isEmpty()) {
            throw new ProcessingException(element, "@Pooled does not support introspected bean properties on Python classes.");
        }
        var pythonConstructor = element.getPrimaryConstructor().orElse(null);
        if (pythonConstructor != null) {
            warnAboutContextBoundDependencies(context, element, List.of(pythonConstructor.getParameters()));
        }

        ClassTypeDef thisType = ClassTypeDef.of(typeName);
        ParameterElement[] constructorParameters = pythonConstructor == null
            ? new ParameterElement[0]
            : pythonConstructor.getParameters();
        boolean hasConstructorArguments = constructorParameters.length > 0;

        // Both shapes carry the holder and the factory: with constructor arguments it holds the
        // per-context instances the bean owns, and without them it is null unless the bean is
        // AOP-proxied, when it holds one Python proxy per context. Only what they do with it differs.
        FieldDef pooledInstanceField = pooledInstanceField();
        builder.addField(pooledInstanceField);
        addPooledValueFactory(builder, thisType, pooledInstanceField, typeName);
        FieldDef pooledInstance = pooledInstanceField;
        if (hasConstructorArguments) {
            // The bean owns its per-context instances, because the pool's cache is keyed by class
            // and two pooled beans of one class can hold different dependencies. The arguments are
            // captured once, at injection, and used to construct in whichever context serves a call.
            MethodDef.MethodDefBuilder ctor = MethodDef.constructor();
            for (ParameterElement parameter : constructorParameters) {
                ctor.addParameter(ParameterDef.builder(parameter.getName(), erasedType(parameter.getGenericType())).build());
            }
            builder.addMethod(ctor.build(((aThis, params) -> aThis.field(pooledInstance).assign(
                POOLED_INSTANCE.instantiate(
                    List.of(
                        pythonClassReference(element, pythonClassReference),
                        aThis,
                        TypeDef.OBJECT.array().instantiate(params)
                    )
                )
            ))));

            builder.addMethod(MethodDef.builder(AS_POLYGLOT_VALUE)
                .addModifiers(Modifier.PUBLIC)
                .returns(POLYGLOT_VALUE)
                .build(((aThis, params) -> PYTHON_POOLED_RUNTIME
                    .invokeStatic("findPooledInstance", POLYGLOT_VALUE, List.of(aThis.field(pooledInstance))).returning())));

            builder.addMethod(MethodDef.builder(AS_POLYGLOT_VALUE)
                .addModifiers(Modifier.PUBLIC)
                .addParameter(POLYGLOT_CONTEXT)
                .returns(POLYGLOT_VALUE)
                .build(((aThis, params) -> PYTHON_POOLED_RUNTIME
                    .invokeStatic("findPooledInstance", POLYGLOT_VALUE, List.of(
                        aThis.field(pooledInstance),
                        params.getFirst()
                    )).returning())));

            // A wrapper for a value rather than for injected dependencies. The AOP machinery needs
            // this: a proxy target is a Python object created for one context and handed to Java to
            // be wrapped, and `box` looks for exactly this constructor. The wrapper stands for that
            // object in that context and is not asked for another, which is why the holder may
            // carry no arguments.
            MethodDef.MethodDefBuilder valueCtor = MethodDef.constructor()
                .addModifiers(Modifier.PUBLIC)
                .addParameter(ParameterDef.builder("value", POLYGLOT_VALUE).build());
            builder.addMethod(valueCtor.build(((aThis, params) -> aThis.field(pooledInstance).assign(
                POOLED_INSTANCE.invokeStatic("seeded", POOLED_INSTANCE, List.of(
                    pythonClassReference(element, pythonClassReference),
                    params.getFirst()
                ))
            ))));

            builder.addMethod(MethodDef.builder(FROM_POLYGLOT_VALUE)
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .addParameter(POLYGLOT_VALUE)
                .returns(thisType)
                .build(((aThis, methodParameters) -> thisType.instantiate(methodParameters.getFirst()).returning())));

        } else {
            // The pool's per-class cache serves this bean, so the holder stays null.
            MethodDef.MethodDefBuilder ctor = MethodDef.constructor();
            builder.addMethod(ctor.build(((aThis, params) ->
                aThis.field(pooledInstance).assign(ExpressionDef.nullValue()))));

            builder.addMethod(MethodDef.builder(AS_POLYGLOT_VALUE)
                .addModifiers(Modifier.PUBLIC)
                .returns(POLYGLOT_VALUE)
                .build(((aThis, params) -> PYTHON_POOLED_RUNTIME
                    .invokeStatic("findPooledClass", POLYGLOT_VALUE, List.of(
                        aThis.field(pooledInstance),
                        pythonClassReference(element, pythonClassReference)
                    )).returning())));

            builder.addMethod(MethodDef.builder(AS_POLYGLOT_VALUE)
                .addModifiers(Modifier.PUBLIC)
                .addParameter(POLYGLOT_CONTEXT)
                .returns(POLYGLOT_VALUE)
                .build(((aThis, params) -> PYTHON_POOLED_RUNTIME
                    .invokeStatic("findPooledClass", POLYGLOT_VALUE, List.of(
                        aThis.field(pooledInstance),
                        pythonClassReference(element, pythonClassReference),
                        params.getFirst()
                    )).returning())));

            builder.addMethod(MethodDef.builder(FROM_POLYGLOT_VALUE)
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .addParameter(POLYGLOT_VALUE)
                .returns(thisType)
                .build(((aThis, methodParameters) -> thisType.instantiate().returning())));
        }

        List<MethodElement> methodsToBridge = element.getEnclosedElements(
            ElementQuery.ALL_METHODS
                .onlyAccessible()
                .onlyInstance()
                .onlyDeclared()
                .annotated(ann -> ann.hasStereotype(Around.class)
                    || ann.hasStereotype(AnnotationUtil.SCOPE)
                    || isDeclaredBeanMethod(ann)
                    || ann.hasStereotype("io.micronaut.context.annotation.Executable"))
        );
        addReferencedPythonClassReferenceFields(builder, element, methodsToBridge);

        for (MethodElement methodElement : methodsToBridge) {
            addBridgeMethodPooledClass(methodElement, builder, element, allClasses, pooledInstanceField, hasConstructorArguments,
                injectedParameterFields);
        }

        return builder;
    }

    static ClassDef.ClassDefBuilder generatePooledScript(PythonScriptElement scriptElement,
                                                         VisitorContext context,
                                                         Map<String, ClassElement> allClasses) {
        String typeName = scriptElement.getName();
        var builder = ClassDef.builder(scriptElement.getPackageName() + "." + scriptElement.getSimpleName())
            .addModifiers(Modifier.PUBLIC);
        builder.addAnnotation(Vetoed.class);
        builder.addSuperinterface(ClassTypeDef.of("io.micronaut.context.python.PooledValueCoercible"));

        // A route module is the common pooled type with dependencies, and typically injects
        // services. Those are what the warning is about.
        warnAboutContextBoundDependencies(context, scriptElement, injectedMembers(scriptElement));

        // Null unless the module is AOP-proxied, in which case it holds one Python proxy per
        // context and every bridged call goes through that rather than the pool's module cache.
        FieldDef pooledInstanceField = pooledInstanceField();
        builder.addField(pooledInstanceField);

        MethodDef.MethodDefBuilder ctor = MethodDef.constructor();
        builder.addMethod(ctor.build(((aThis, params) ->
            aThis.field(pooledInstanceField).assign(ExpressionDef.nullValue()))));

        ClassTypeDef thisType = ClassTypeDef.of(typeName);
        String name = scriptElement.getNativeType().name();
        if (name.endsWith(".py")) {
            name = name.substring(0, name.length() - 3);
        }

        String pkg = scriptElement.getPackageName();
        String script = name;

        builder.addMethod(MethodDef.builder(AS_POLYGLOT_VALUE)
            .addModifiers(Modifier.PUBLIC)
            .returns(POLYGLOT_VALUE)
            .build(((aThis, params) -> PYTHON_POOLED_RUNTIME
                .invokeStatic("findPooledScript", POLYGLOT_VALUE,
                    List.of(aThis.field(pooledInstanceField), ExpressionDef.constant(pkg), ExpressionDef.constant(script)))
                .returning())));

        builder.addMethod(MethodDef.builder(AS_POLYGLOT_VALUE)
            .addModifiers(Modifier.PUBLIC)
            .addParameter(POLYGLOT_CONTEXT)
            .returns(POLYGLOT_VALUE)
            .build(((aThis, params) -> PYTHON_POOLED_RUNTIME
                .invokeStatic("findPooledScript", POLYGLOT_VALUE,
                    List.of(aThis.field(pooledInstanceField), ExpressionDef.constant(pkg), ExpressionDef.constant(script), params.getFirst()))
                .returning())));

        builder.addMethod(MethodDef.builder(FROM_POLYGLOT_VALUE)
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
            .addParameter(POLYGLOT_VALUE)
            .returns(thisType)
            .build(((aThis, methodParameters) -> thisType.instantiate().returning())));

        addPooledValueFactory(builder, thisType, pooledInstanceField, typeName);

        List<MethodElement> methodsToBridge = scriptElement.getEnclosedElements(
            ElementQuery.ALL_METHODS
                .onlyAccessible()
                .onlyInstance()
                .onlyDeclared()
                .annotated(ann -> ann.hasStereotype(Around.class)
                    || ann.hasStereotype(AnnotationUtil.SCOPE)
                    || isDeclaredBeanMethod(ann)
                    || ann.hasStereotype("io.micronaut.context.annotation.Executable"))
        );
        boolean hasAsyncBridgeMethod = methodsToBridge.stream().anyMatch(PythonStubGenerator::isAsyncPythonMethod);

        for (MethodElement methodElement : methodsToBridge) {
            addBridgeMethodPooledScript(scriptElement, methodElement, builder, pkg, script, allClasses, context, pooledInstanceField);
        }

        List<PropertyElement> beanProperties = scriptElement.getBeanProperties();
        for (PropertyElement beanProperty : beanProperties) {
            if (beanProperty.hasStereotype(AnnotationUtil.INJECT)) {
                FieldDef injected = PythonStubGenerator.injectedField(builder, beanProperty, propertyType(beanProperty));
                addSetterScriptPooled(beanProperty, builder, pkg, script, hasAsyncBridgeMethod, injected);
            }
            if (beanProperty.hasStereotype(Bean.class)) {
                addGetterScriptPooled(beanProperty, builder, pkg, script, allClasses);
            }
        }

        return builder;
    }

    /**
     * Stores the beans of the parameters injected with a bean ({@code ctx: ApplicationContext = Inject()}) in fields of
     * the generated class, not in a Python instance: a pooled class has one instance per context, and the bridge passes
     * the field to whichever instance a call runs on, as it passes the constructor arguments. The bean definition
     * injects the attribute the processor declares for the parameter through the setter written here.
     *
     * @param element The pooled class
     * @param builder The generated class
     * @return The field of each injected attribute, by its name
     */
    private static Map<String, FieldDef> addInjectedParameterFields(AbstractPythonClassElement element,
                                                                    ClassDef.ClassDefBuilder builder) {
        Map<String, FieldDef> fields = new LinkedHashMap<>();
        for (MethodElement method : element.getEnclosedElements(ElementQuery.ALL_METHODS.onlyDeclared())) {
            if (!(method instanceof PythonMethodElement pythonMethod)) {
                continue;
            }
            for (PythonMethodElement.InjectedArgument injected : pythonMethod.injectedArguments()) {
                PropertyElement property = element.getBeanProperties().stream()
                    .filter(candidate -> candidate.getName().equals(injected.attribute()))
                    .findFirst()
                    .orElse(null);
                if (property == null || fields.containsKey(injected.attribute())) {
                    continue;
                }
                TypeDef type = erasedType(property.getGenericType());
                FieldDef field = FieldDef.builder(injected.attribute(), type).addModifiers(Modifier.PRIVATE, Modifier.VOLATILE).build();
                builder.addField(field);
                List<String> setterNames = new ArrayList<>();
                setterNames.add(PythonStubGenerator.beanSetterName(injected.attribute()));
                property.getWriteMethod().map(MethodElement::getName)
                    .filter(name -> !setterNames.contains(name))
                    .ifPresent(setterNames::add);
                for (String setterName : setterNames) {
                    builder.addMethod(MethodDef.builder(setterName)
                        .addModifiers(Modifier.PUBLIC)
                        .returns(TypeDef.VOID)
                        .addParameter(ParameterDef.builder(injected.attribute(), type).build())
                        .build((aThis, params) -> aThis.field(field).assign(params.getFirst())));
                }
                fields.put(injected.attribute(), field);
            }
        }
        return fields;
    }

    private static void addBridgeMethodPooledClass(MethodElement methodElement,
                                                   ClassDef.ClassDefBuilder builder,
                                                   AbstractPythonClassElement element,
                                                   Map<String, ClassElement> allClasses,
                                                   FieldDef pooledInstanceField,
                                                   boolean ownsInstances,
                                                   Map<String, FieldDef> injectedParameterFields) {
        String pythonFunctionName = methodElement.getName();
        MethodDef.MethodDefBuilder methodBuilder = MethodDef.builder(pythonFunctionName)
            .addModifiers(Modifier.PUBLIC)
            .returns(TypeDef.of(methodElement.getGenericReturnType()));

        for (ParameterElement parameter : methodElement.getParameters()) {
            var parameterType = erasedType(parameter.getGenericType());
            methodBuilder.addParameter(ParameterDef.builder(parameter.getName(), parameterType).build());
        }

        builder.addMethod(methodBuilder.build(((aThis, methodParameters) -> {
            List<ExpressionDef> parameterExpressions = new ArrayList<>();
            for (int i = 0; i < methodElement.getParameters().length; i++) {
                parameterExpressions.add(methodParameters.get(i));
            }
            if (methodElement instanceof PythonMethodElement pythonMethod) {
                // a parameter injected with a bean (ctx: ApplicationContext = Inject()): the bean the setter stored
                for (PythonMethodElement.InjectedArgument injected : pythonMethod.injectedArguments()) {
                    int index = Math.min(injected.position(), parameterExpressions.size());
                    parameterExpressions.add(index, aThis.field(injectedParameterFields.get(injected.attribute())));
                }
            }
            // A bean that owns its per-context instances is invoked through the holder. One without
            // arguments passes the holder too, so that a proxy takes over when there is one, and
            // otherwise goes through the pool's class cache.
            List<ExpressionDef> args = new ArrayList<>();
            args.add(aThis.field(pooledInstanceField));
            if (!ownsInstances) {
                args.add(pythonClassReference(element, element));
            }
            args.add(ExpressionDef.constant(pythonFunctionName));
            args.addAll(parameterExpressions);
            if (isAsyncGeneratorPythonMethod(methodElement)) {
                return convertedElementPublisher(allClasses, methodElement.getGenericReturnType(),
                    PYTHON_POOLED_RUNTIME.invokeStatic(
                        ownsInstances ? "invokePooledInstancePublisher" : "invokePooledPublisher",
                        ClassTypeDef.of(PUBLISHER), args)).returning();
            }
            if (isAsyncPythonMethod(methodElement) && !methodElement.getReturnType().isVoid()) {
                // the coroutine is driven while the pooled context is still leased to this call
                return completionStage(methodElement, PYTHON_POOLED_RUNTIME.invokeStatic(
                    ownsInstances ? "invokePooledInstanceAsync" : "invokePooledAsync",
                    TypeDef.of(CompletionStage.class), args));
            }
            var invoked = PYTHON_POOLED_RUNTIME.invokeStatic(
                ownsInstances ? "invokePooledInstance" : "invokePooled", POLYGLOT_VALUE, args);
            return bridgeReturnValue(allClasses, methodElement, invoked);
        })));

    }

    private static void addBridgeMethodPooledScript(PythonScriptElement scriptElement,
                                                    MethodElement methodElement,
                                                    ClassDef.ClassDefBuilder builder,
                                                    String pkg,
                                                    String script,
                                                    Map<String, ClassElement> allClasses,
                                                    VisitorContext context,
                                                    FieldDef pooledInstanceField) {
        String pythonFunctionName = methodElement.getName();
        MethodDef.MethodDefBuilder methodBuilder = MethodDef.builder(pythonFunctionName)
            .addModifiers(Modifier.PUBLIC)
            .returns(TypeDef.of(methodElement.getGenericReturnType()));

        for (ParameterElement parameter : methodElement.getParameters()) {
            var parameterType = erasedType(parameter.getGenericType());
            methodBuilder.addParameter(ParameterDef.builder(parameter.getName(), parameterType).build());
        }

        StaticCompilationPlan plan = PythonStubGenerator.staticCompilationPlan(context);
        Ir.CompiledBody compiledBody = plan == null || methodElement.isStatic() || isAsyncPythonMethod(methodElement)
            ? null : plan.body(scriptElement.getName(), pythonFunctionName);
        if (compiledBody != null && compiledBody.parameterNames().size() == methodElement.getParameters().length) {
            // the body runs as Java, outside any Python context: no context is leased for the call
            if (compiledBody.span() != null) {
                methodBuilder.addJavadoc("Compiled from " + compiledBody.span().location());
            }
            builder.addMethod(methodBuilder.build((aThis, methodParameters) ->
                StaticBodyGenerator.generate(compiledBody, methodParameters, PythonStubGenerator.pooledScriptAccess(aThis), plan.trace())));
            return;
        }

        builder.addMethod(methodBuilder.build(((aThis, methodParameters) -> {
            List<ExpressionDef> parameterExpressions = new ArrayList<>();
            for (int i = 0; i < methodElement.getParameters().length; i++) {
                parameterExpressions.add(methodParameters.get(i));
            }
            if (methodElement instanceof PythonMethodElement pythonMethod) {
                // a parameter injected with a bean (ctx: ApplicationContext = Inject()): the bean injected into the
                // module attribute the processor declares, read through the pool's module cache as a property is
                for (PythonMethodElement.InjectedArgument injected : pythonMethod.injectedArguments()) {
                    int index = Math.min(injected.position(), parameterExpressions.size());
                    parameterExpressions.add(index, PYTHON_CONTEXT_RUNTIME.invokeStatic("pooledScriptAttribute", POLYGLOT_VALUE,
                        List.of(ExpressionDef.constant(pkg), ExpressionDef.constant(script), ExpressionDef.constant(injected.attribute()))));
                }
            }
            List<ExpressionDef> args = new ArrayList<>();
            // the holder first, so a proxy takes over when the module is advised
            args.add(aThis.field(pooledInstanceField));
            args.add(ExpressionDef.constant(pkg));
            args.add(ExpressionDef.constant(script));
            args.add(ExpressionDef.constant(pythonFunctionName));
            args.addAll(parameterExpressions);
            if (isAsyncGeneratorPythonMethod(methodElement)) {
                return convertedElementPublisher(allClasses, methodElement.getGenericReturnType(),
                    PYTHON_POOLED_RUNTIME.invokeStatic("invokePooledScriptPublisher", ClassTypeDef.of(PUBLISHER), args)).returning();
            }
            if (isAsyncPythonMethod(methodElement) && !methodElement.getReturnType().isVoid()) {
                return completionStage(methodElement, PYTHON_POOLED_RUNTIME.invokeStatic("invokePooledScriptAsync", TypeDef.of(CompletionStage.class), args));
            }
            var invoked = PYTHON_POOLED_RUNTIME.invokeStatic("invokePooledScript", POLYGLOT_VALUE, args);
            return bridgeReturnValue(allClasses, methodElement, invoked);
        })));

    }

    private static StatementDef completionStage(MethodElement methodElement, ExpressionDef stage) {
        return stage.cast(TypeDef.of(methodElement.getGenericReturnType())).returning();
    }

    private static StatementDef bridgeReturnValue(Map<String, ClassElement> allClasses,
                                                  MethodElement methodElement,
                                                  ExpressionDef.InvokeStaticMethod invoked) {
        if (methodElement.getReturnType().isVoid()) {
            return invoked;
        }
        if (isAsyncGeneratorPythonMethod(methodElement)) {
            return invoked.newLocal("pythonAsyncGenerator", pythonAsyncGenerator ->
                convertedElementPublisher(allClasses, methodElement.getGenericReturnType(), PYTHON_ASYNCIO_RUNTIME.invokeStatic(
                    "generatorToPublisher",
                    ClassTypeDef.of(PUBLISHER),
                    pythonAsyncGenerator
                )).returning()
            );
        }
        if (isAsyncPythonMethod(methodElement)) {
            return invoked.newLocal("pythonCoroutine", pythonCoroutine ->
                PYTHON_ASYNCIO_RUNTIME.invokeStatic(
                    "toCompletionStage",
                    TypeDef.of(CompletionStage.class),
                    pythonCoroutine
                ).cast(TypeDef.of(CompletionStage.class)).cast(TypeDef.of(methodElement.getGenericReturnType())).returning()
            );
        }
        return returnConvertedValue(allClasses, methodElement.getGenericReturnType(), invoked);
    }

    private static void addGetterScriptPooled(PropertyElement beanProperty,
                                              ClassDef.ClassDefBuilder builder,
                                              String pkg,
                                              String script,
                                              Map<String, ClassElement> allClasses) {
        TypeDef propertyType = propertyType(beanProperty);
        String getterName = beanProperty.getReadMethod().map(MethodElement::getName).orElse(beanProperty.getName());
        MethodDef.MethodDefBuilder getterBuilder = MethodDef
            .builder(getterName)
            .addModifiers(Modifier.PUBLIC)
            .returns(propertyType);

        builder.addMethod(getterBuilder.build(((aThis, methodParameters) -> {
            // a property read takes no holder, so it stays on the entry point without one: reading a
            // module attribute goes through the pool's module cache, as it did before holders existed
            var invoked = PYTHON_CONTEXT_RUNTIME.invokeStatic("invokePooledScript", POLYGLOT_VALUE,
                List.of(ExpressionDef.constant(pkg), ExpressionDef.constant(script), ExpressionDef.constant(beanProperty.getName())));
            return returnConvertedValue(allClasses, beanProperty.getGenericType(), invoked);
        })));
    }

    private static void addSetterScriptPooled(PropertyElement beanProperty,
                                              ClassDef.ClassDefBuilder builder,
                                              String pkg,
                                              String script,
                                              boolean adaptAsyncMembers,
                                              FieldDef injected) {
        TypeDef returnType = beanProperty.getWriteMethod().map(MethodElement::getReturnType).map(TypeDef::of).orElse(TypeDef.VOID);
        String setterName = beanProperty.getWriteMethod().map(MethodElement::getName).orElse(beanProperty.getName());
        MethodDef.MethodDefBuilder propertySetter = MethodDef
            .builder(setterName)
            .addModifiers(Modifier.PUBLIC)
            .returns(returnType);

        propertySetter.addParameter(propertyType(beanProperty));

        builder.addMethod(propertySetter.build(((aThis, methodParameters) -> {
            List<ExpressionDef> parameters = new ArrayList<>();
            parameters.add(ExpressionDef.constant(pkg));
            parameters.add(ExpressionDef.constant(script));
            parameters.add(ExpressionDef.constant(beanProperty.getName()));
            parameters.add(methodParameters.getFirst());
            var result = PYTHON_CONTEXT_RUNTIME.invokeStatic(adaptAsyncMembers ? "injectPooledScriptAsync" : "injectPooledScript", TypeDef.VOID, parameters);
            StatementDef remember = aThis.field(injected).assign(methodParameters.getFirst());
            if (returnType.equals(TypeDef.VOID)) {
                return StatementDef.multi(remember, result);
            } else {
                return StatementDef.multi(remember, result, ExpressionDef.nullValue().returning());
            }
        })));
    }

    /**
     * The field holding this bean's per-context Python values.
     *
     * <p>Null unless the bean needs values of its own rather than the pool's per-class or
     * per-module cache: it has constructor arguments, so two beans of the class are not
     * interchangeable, or it is AOP-proxied, so its value is one Python proxy per context.
     *
     * @return The field
     */
    private static FieldDef pooledInstanceField() {
        return FieldDef.builder(POOLED_INSTANCE_FIELD, POOLED_INSTANCE)
            .addModifiers(Modifier.PRIVATE, Modifier.FINAL)
            .build();
    }

    /**
     * Adds the constructor and factory an AOP-proxied pooled bean is built through.
     *
     * <p>A Python proxy belongs to the context it was created in, so one proxy cannot serve a bean
     * that exists in every context. The proxy creator hands over a function that builds a proxy in
     * whichever context asks; this is the one wrapper Micronaut injects, and every call it bridges
     * goes through that function's value rather than through the pool's shared instance -- which is
     * what makes the advice run at all.
     *
     * @param builder The class being generated
     * @param thisType Its type
     * @param holder The per-context value field
     * @param displayName What to call the bean in a diagnostic
     */
    private static void addPooledValueFactory(ClassDef.ClassDefBuilder builder,
                                              ClassTypeDef thisType,
                                              FieldDef holder,
                                              String displayName) {
        builder.addMethod(MethodDef.constructor()
            .addModifiers(Modifier.PRIVATE)
            .addParameter(ParameterDef.builder("pooledInstance", POOLED_INSTANCE).build())
            .build(((aThis, params) -> aThis.field(holder).assign(params.getFirst()))));

        builder.addMethod(MethodDef.builder(FROM_POOLED_VALUE_FACTORY)
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
            // The proxy creator looks this up reflectively, as `box` does the (Value) constructor;
            // see the note there about what a native image would need.
            .addParameter(ParameterDef.builder("valueFactory", VALUE_FACTORY).build())
            .returns(thisType)
            .build(((aThis, methodParameters) -> thisType.instantiate(
                POOLED_INSTANCE.invokeStatic("producedBy", POOLED_INSTANCE, List.of(
                    ExpressionDef.constant(displayName),
                    methodParameters.getFirst()
                ))
            ).returning())));
    }

    private static boolean isDeclaredBeanMethod(AnnotationMetadata annotationMetadata) {
        // Visitors can add @Bean directly to Python methods after metadata parsing. Those
        // methods still need Java bridge methods so generated bean definitions can call them.
        return annotationMetadata.hasDeclaredAnnotation(Bean.class)
            || annotationMetadata.hasDeclaredStereotype(Bean.class);
    }
}
