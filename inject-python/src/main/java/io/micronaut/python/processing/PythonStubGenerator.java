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

import io.micronaut.core.annotation.Experimental;
import java.io.Serializable;
import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletionStage;
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
import io.micronaut.inject.ast.FieldElement;
import io.micronaut.inject.ast.TypedElement;
import io.micronaut.inject.processing.BeanDefinitionCreatorFactory;
import io.micronaut.inject.processing.ProcessingException;
import io.micronaut.python.processing.model.ArgumentDef;
import io.micronaut.python.processing.model.DecoratorDef;
import io.micronaut.python.processing.model.DefaultFactoryDef;
import io.micronaut.python.processing.visitor.PythonVisitorContext;
import io.micronaut.sourcegen.model.AbstractElementBuilder;
import io.micronaut.sourcegen.model.AnnotationDef;
import org.jspecify.annotations.Nullable;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;

import io.micronaut.context.annotation.Executable;
import io.micronaut.context.annotation.Factory;
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
import io.micronaut.python.processing.element.AbstractPythonClassElement;
import io.micronaut.python.processing.element.PythonClassElement;
import io.micronaut.python.processing.element.PythonMethodElement;
import io.micronaut.python.processing.element.PythonPropertyElement;
import io.micronaut.python.processing.element.PythonScriptElement;
import io.micronaut.python.processing.model.ScriptDef;
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
import io.micronaut.python.processing.util.PythonAnnotationTypes;
import io.micronaut.python.processing.util.PythonJavaTypes;

/**
 * Generates Java stubs for Python classes, scripts, enums, interfaces, and annotations.
 */
@SuppressWarnings({"FileLength", "checkstyle:DeclarationOrder"})
@Experimental
public class PythonStubGenerator implements TypeElementVisitor<Object, Object> {

    public static final TypeDef POLYGLOT_VALUE = TypeDef.of(Value.class);

    private static final String MEMBER_PRE_DESTROY = "preDestroy";
    private static final String CLASS_FIELD = "class";
    public static final TypeDef POLYGLOT_CONTEXT = TypeDef.of(Context.class);
    public static final VariableDef.StaticField CLASS_OBJECT = ClassTypeDef.of(Object.class).getStaticField(CLASS_FIELD, TypeDef.CLASS);
    public static final String AS_POLYGLOT_VALUE = "asPolyglotValue";
    private static final String SYNC_SNAPSHOT_FIELD_PREFIX = "graalpyInternalSynced_";
    private static final String MEMBER_LOCAL_PREFIX = "pythonMember_";
    private static final String BOOLEAN_TYPE = "boolean";
    private static final String SHORT_TYPE = "short";
    private static final String DOUBLE_TYPE = "double";
    private static final String FLOAT_TYPE = "float";
    private static final String JAVA_LANG_SHORT = "java.lang.Short";
    private static final String JAVA_LANG_DOUBLE = "java.lang.Double";
    private static final String JAVA_LANG_FLOAT = "java.lang.Float";
    private static final String AS_SHORT = "asShort";
    private static final String AS_DOUBLE = "asDouble";
    private static final String AS_FLOAT = "asFloat";
    private static final String JAVA_LANG_STRING = "java.lang.String";
    private static final String SERIAL_VERSION_UID = "serialVersionUID";
    private static final String AS_OBJECT_METHOD = "asObject";
    private static final Set<String> IMMUTABLE_PROPERTY_TYPES = Set.of(
        String.class.getName(), Boolean.class.getName(), Byte.class.getName(), Short.class.getName(),
        Integer.class.getName(), Long.class.getName(), Float.class.getName(), Double.class.getName(),
        Character.class.getName(), "java.math.BigDecimal", "java.math.BigInteger", "java.util.UUID",
        "java.net.URI", "java.time.Instant", "java.time.LocalDate", "java.time.LocalDateTime",
        "java.time.LocalTime", "java.time.ZonedDateTime", "java.time.OffsetDateTime", "java.time.Duration",
        "java.time.Period"
    );
    public static final String RECONSTRUCT_POLYGLOT_VALUE = "reconstructPolyglotValue";
    public static final String FROM_POLYGLOT_VALUE = "fromPolyglotValue";
    public static final ClassTypeDef PYTHON_COERCION = ClassTypeDef.of("io.micronaut.context.python.PythonCoercion");
    public static final ClassTypeDef PYTHON_CONVERSION = ClassTypeDef.of("io.micronaut.context.python.PythonConversion");
    public static final ClassTypeDef PYTHON_EXCEPTIONS = ClassTypeDef.of("io.micronaut.context.python.PythonExceptions");
    public static final ClassTypeDef PYTHON_HTTP_CONVERSION = ClassTypeDef.of("io.micronaut.context.python.PythonHttpConversion");
    public static final ClassTypeDef PYTHON_INVOCATION = ClassTypeDef.of("io.micronaut.context.python.PythonInvocation");
    public static final ClassTypeDef PYTHON_ASYNCIO_RUNTIME = ClassTypeDef.of("io.micronaut.context.python.PythonAsyncioRuntime");
    public static final ClassTypeDef PYTHON_CONTEXT_RUNTIME = ClassTypeDef.of("io.micronaut.context.python.PythonContextRuntime");
    public static final ClassTypeDef PYTHON_CLASS_REFERENCE = ClassTypeDef.of("io.micronaut.context.python.PythonContextRuntime.PythonClassReference");
    public static final ClassTypeDef PYTHON_CLASS_ANNOTATION = ClassTypeDef.of("io.micronaut.context.python.annotation.PythonClass");
    public static final ClassTypeDef PYTHON_MODULE_ANNOTATION = ClassTypeDef.of("io.micronaut.context.python.annotation.PythonModule");
    public static final ClassTypeDef POLYGLOT_VALUE_CONVERTER = ClassTypeDef.of("io.micronaut.context.python.PolyglotValueConverter");
    public static final String GENERATOR_NAME = "python";
    private static final String HTTP_RESPONSE = "io.micronaut.http.HttpResponse";
    private static final String PUBLISHER = "org.reactivestreams.Publisher";
    private static final String GET_MEMBER = "getMember";
    private static final String MAP_OF = "mapOf";
    private static final String ENUM_VALUE = "enumValue";
    private static final String TARGET_VALUE = "targetValue";
    private static final String REMEMBER_POOLED_VALUE = "rememberPooledValue";
    private static final String COERCE_MAP = "coerceMap";
    private static final String COERCE_LIST = "coerceList";
    private static final String ANN_CONFIGURATION_BUILDER = "io.micronaut.context.annotation.ConfigurationBuilder";
    private static final String NEW_UNINITIALIZED_INSTANCE = "newUninitializedInstance";
    private static final String ANN_CONFIGURATION_INJECT = "io.micronaut.context.annotation.ConfigurationInject";
    private static final String ANN_CONFIGURATION_READER = "io.micronaut.context.annotation.ConfigurationReader";
    private static final String ANN_ANNOTATION_EXPRESSION_CONTEXT = "io.micronaut.context.annotation.AnnotationExpressionContext";
    private static final String ANN_CONSTRAINT = "jakarta.validation.Constraint";
    private static final String ANN_VALID = "jakarta.validation.Valid";
    /**
     * Micronaut annotations that are read reflectively from the generated Java class rather than from
     * the annotation metadata of the Python element: the JUnit 5 extension looks {@code @MicronautTest}
     * and the {@code @Property} container up on the test class. Every other Micronaut annotation is
     * served by the annotation metadata, so it stays off the generated source.
     */
    private static final Set<String> MICRONAUT_ANNOTATION_PACKAGES_TO_COPY = Set.of("io.micronaut.test.extensions.junit5.annotation");
    private static final Set<String> MICRONAUT_ANNOTATIONS_TO_COPY = Set.of("io.micronaut.context.annotation.PropertySource");
    private static final String MICRONAUT_PACKAGE_PREFIX = "io.micronaut.";
    /**
     * The dependency injection and common annotations Micronaut processes itself ({@code @Singleton},
     * {@code @Inject}, {@code @Named}, {@code @PostConstruct}, ...): served by the annotation metadata like
     * the Micronaut annotations, and kept off the generated source so that the vetoed generated class is
     * not taken for an annotated bean class by the visitors of the Java processing round.
     */
    private static final Set<String> INJECTION_ANNOTATION_PACKAGE_PREFIXES = Set.of("jakarta.inject.", "javax.inject.", "jakarta.annotation.", "javax.annotation.");
    private static final String JAVA_LANG_PACKAGE_PREFIX = "java.lang.";
    private static final String WITH_VARARGS = "withVarargs";
    private static final String PYTHON_METHOD_KEY_PREFIX = "python:";
    static final Set<String> TYPE_ANNOTATIONS_TO_SKIP_IN_SOURCE = Set.of(
        "io.micronaut.core.annotation.NonNull",
        "io.micronaut.core.annotation.Nullable",
        "jakarta.annotation.Nonnull",
        "jakarta.annotation.Nullable",
        "javax.annotation.Nonnull",
        "javax.annotation.Nullable",
        "org.jspecify.annotations.NonNull",
        "org.jspecify.annotations.Nullable"
    );
    private final Map<String, Boolean> copiedRuntimeAnnotations = new HashMap<>();
    private final Map<String, StubEntry> classBuilders = new LinkedHashMap<>();
    private final Map<String, EnumEntry> enumDefs = new LinkedHashMap<>();
    private final Map<String, InterfaceEntry> interfaceDefs = new LinkedHashMap<>();
    private final Map<String, AnnotationEntry> annotationDefs = new LinkedHashMap<>();
    private Map<String, ClassElement> allClasses = Map.of();

    public static final String JUNIT_TEST = "org.junit.jupiter.api.Test";
    private static final String ANN_MICRONAUT_TEST = "io.micronaut.test.extensions.junit5.annotation.MicronautTest";
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
                .filter(decoratorDef -> PythonAnnotationStubGenerator.shouldGenerateAnnotationStub(decoratorDef, pythonVisitorContext))
                .forEach(decoratorDef -> annotationDefs.putIfAbsent(
                    decoratorDef.annotationName(),
                    new AnnotationEntry(PythonAnnotationStubGenerator.generateAnnotationStub(decoratorDef, pythonVisitorContext), null)
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
                if (scriptElement.hasStereotype("io.micronaut.context.python.scope.ContextPooled")) {
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
                    boolean isAopProxy = classElement.hasStereotype(InterceptorBinding.class);
                    boolean isDeclaredBean = BeanDefinitionCreatorFactory.isDeclaredBeanInMetadata(classElement) || isAopProxy;
                    Collection<ClassElement> interfaces = classElement.getInterfaces();

                    if (classElement.isInterface()) {
                        if (interfaceDefs.containsKey(classElement.getName())) {
                            return;
                        }
                        interfaceDefs.put(classElement.getName(), new InterfaceEntry(PythonInterfaceStubGenerator.buildInterfaceDef(classElement, typeName, interfaces, allClasses, context), classElement));
                        return;
                    }

                    var builder = ClassDef.builder(typeName)
                        .addModifiers(Modifier.PUBLIC);
                    FieldDef pythonClassReference = pythonClassReferenceField("__PYTHON_CLASS_REFERENCE", classElement);
                    builder.addField(pythonClassReference);
                    for (GenericPlaceholderElement placeholder : classElement.getDeclaredGenericPlaceholders()) {
                        builder.addTypeVariable(TypeDef.variable(placeholder.getVariableName()));
                    }
                    builder.addAnnotation(Vetoed.class);
                    builder.addAnnotation(pythonClassAnnotation(classElement));

                    copyRuntimeAnnotations(element, builder, ElementType.TYPE, context);
                    ClassElement superType = element.getSuperType().orElse(null);
                    boolean extendsPythonClass = superType instanceof AbstractPythonClassElement;
                    boolean extendsHostClass = superType != null
                        && !extendsPythonClass
                        && !Object.class.getName().equals(superType.getName())
                        && !superType.isInterface();
                    boolean isIntrospectedBean = element.hasStereotype(Introspected.class);

                    // Check if this class extends another PythonClassElement
                    if (extendsPythonClass || extendsHostClass) {
                        builder.superclass(parameterizedClassTypeDef(superType));
                    }

                    final boolean isIntroductionBean = element.hasStereotype(Introduction.class);
                    boolean isJunit5Test = element.getEnclosedElement(ElementQuery.ALL_METHODS.onlyInstance().annotated(ann -> ann.hasDeclaredAnnotation(JUNIT_TEST))).isPresent();
                    boolean isConfigurationBuilderType = isConfigurationBuilderType(element);

                    List<PropertyElement> beanProperties = element.getBeanProperties();
                    boolean hasDynamicBeanProperties = beanProperties.stream().anyMatch(PythonStubGenerator::isDynamicBeanProperty);
                    // Declared attributes are fully represented by generated Java fields. Custom
                    // Python properties may depend on hidden state that cannot be reconstructed in
                    // another context from the BeanIntrospection alone.
                    boolean isReconstructibleBean = isIntrospectedBean && !hasDynamicBeanProperties;
                    if (isReconstructibleBean) {
                        builder.addSuperinterface(ClassTypeDef.of("io.micronaut.context.python.PooledValueCoercible"));
                        if (isSerializableStub(superType, extendsPythonClass, extendsHostClass) && !declaresInterface(interfaces, Serializable.class)) {
                            // The Java fields carry the introspected properties; the Python object is
                            // transient and rebuilt from them on the first use after deserialization.
                            // A class listing Serializable among its bases declares it itself.
                            builder.addSuperinterface(ClassTypeDef.of(Serializable.class));
                            // a fixed UID: the serialized form is the property values, not the shape of the stub
                            builder.addField(FieldDef.builder(SERIAL_VERSION_UID)
                                .ofType(TypeDef.Primitive.LONG)
                                .addModifiers(Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
                                .initializer(ExpressionDef.constant(1L))
                                .build());
                        }
                    } else if (!extendsPythonClass) {
                        builder.addSuperinterface(ClassTypeDef.of("io.micronaut.context.python.ValueCoercible"));
                    }
                    boolean hasConfigurationBuilderProperty = beanProperties.stream()
                        .anyMatch(PythonStubGenerator::isConfigurationBuilderProperty);
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
                    StateFields state = addStateFields(builder, element, beanProperties, isIntrospectedBean, extendsPythonClass, isJunit5Test, hasDynamicBeanProperties, context);
                    Map<String, FieldDef> propertyFields = state.propertyFields();
                    Map<String, FieldDef> syncSnapshotFields = state.syncSnapshotFields();
                    FieldDef pythonValue = state.pythonValue();
                    FieldDef pythonValueSyncing = state.pythonValueSyncing();
                    StubEntry stubEntry = new StubEntry(builder, classElement, propertyFields);
                    classBuilders.put(classElement.getName(), stubEntry);

                    // Track method names that have been added to avoid duplicates
                    Set<String> addedMethodNames = stubEntry.bridgedMethods();

                    isAopProxy = addInterfaceAndHostBridges(classElement, element, builder, context, addedMethodNames, superType, extendsHostClass, isDeclaredBean, isIntroductionBean, isAopProxy, beanProperties);
                    // Constructor from polyglot Value
                    final FieldDef pythonValueFinal = pythonValue;
                    final FieldDef pythonValueSyncingFinal = pythonValueSyncing;
                    if (!isIntrospectedBean && !extendsPythonClass && pythonValueFinal == null) {
                        throw new IllegalStateException("Expected graalpyInternalValue field to be initialized");
                    }

                    ClassStubModel model = new ClassStubModel(builder, element, classElement, context, pythonVisitorContext, typeName, isAopProxy, pythonClassReference, superType, extendsPythonClass, extendsHostClass, isIntrospectedBean, isJunit5Test, beanProperties, hasDynamicBeanProperties, isReconstructibleBean, hasConfigurationBuilderProperty, propertyFields, syncSnapshotFields, pythonValueFinal, pythonValueSyncingFinal);
                    addValueConstructors(model);
                    addPolyglotValueMethods(model);
                    addFactoryMethods(model);
                    BridgedMethods bridged = addBridgeMethods(model, addedMethodNames, isConfigurationBuilderType);
                    List<MethodElement> methodsToBridge = bridged.methodsToBridge();
                    boolean hasAsyncBridgeMethod = bridged.hasAsyncBridgeMethod();
                    addInjectionMethods(model, methodsToBridge);
                    addCreatorsAndPropertyAccessors(model, hasAsyncBridgeMethod);
                } catch (ProcessingException e) {
                    throw e;
                } catch (Exception e) {
                    context.fail("Failed to generate stub for Python type [" + element.getSimpleName() + "]: " + e.getMessage(), null);
                }

            }
        }
    }

    /**
     * Emits the property, snapshot and Python value fields of a class stub.
     */
    @SuppressWarnings("java:S107") // the flags describe the generated state fields; a state record would obscure the call sites
    private StateFields addStateFields(ClassDef.ClassDefBuilder builder, ClassElement element, List<PropertyElement> beanProperties, boolean isIntrospectedBean, boolean extendsPythonClass, boolean isJunit5Test, boolean hasDynamicBeanProperties, VisitorContext context) {
        Map<String, FieldDef> propertyFields = new LinkedHashMap<>();
        // Last value written to the Python object for every property. The generated
        // asPolyglotValue() only re-syncs when one of these differs from the field, so a bridge
        // call on an unchanged dataclass costs no guest writes. Only classes whose properties are
        // all immutable-typed can be tracked: a collection or nested wrapper may change in place.
        Map<String, FieldDef> syncSnapshotFields = new LinkedHashMap<>();
        if (isIntrospectedBean) {
            boolean trackSyncState = !isFrozenPythonDataclass(element)
                && !hasDynamicBeanProperties
                && beanProperties.stream().allMatch(PythonStubGenerator::isImmutablePropertyType);
            for (PropertyElement beanProperty : beanProperties) {
                FieldDef.FieldDefBuilder fieldBuilder = FieldDef.builder(beanProperty.getName())
                    .ofType(propertySourceType(beanProperty))
                    .addModifiers(Modifier.PUBLIC);
                // A declared attribute is the field of the generated class: its runtime annotations
                // (@Id, @Column, @XmlElement, ...) go with it so that field access finds them.
                attributeField(beanProperty).ifPresent(pythonField -> copyRuntimeAnnotations(pythonField, fieldBuilder, ElementType.FIELD, context));
                FieldDef field = fieldBuilder.build();
                builder.addField(field);
                propertyFields.put(beanProperty.getName(), field);
                if (trackSyncState) {
                    FieldDef snapshot = FieldDef.builder(SYNC_SNAPSHOT_FIELD_PREFIX + beanProperty.getName())
                        .ofType(propertySourceType(beanProperty))
                        .addModifiers(Modifier.PRIVATE, Modifier.TRANSIENT)
                        .build();
                    builder.addField(snapshot);
                    syncSnapshotFields.put(beanProperty.getName(), snapshot);
                }
            }
        }
        FieldDef pythonValue = null;
        if (!extendsPythonClass || isIntrospectedBean) {
            // The Python state is transient: it is neither a persistent attribute of an entity nor
            // serializable, and reflection-based frameworks (JPA field access, Java serialization)
            // skip transient fields.
            FieldDef.FieldDefBuilder pythonValueBuilder = FieldDef.builder("graalpyInternalValue")
                .ofType(POLYGLOT_VALUE)
                .addModifiers(Modifier.PROTECTED, Modifier.TRANSIENT);
            if (!isIntrospectedBean && !isJunit5Test) {
                pythonValueBuilder.addModifiers(Modifier.FINAL);
            }
            pythonValue = pythonValueBuilder.build();
            builder.addField(pythonValue);
        }
        FieldDef pythonValueSyncing = null;
        if (isIntrospectedBean) {
            pythonValueSyncing = FieldDef.builder("graalpyInternalValueSyncing")
                .ofType(TypeDef.Primitive.BOOLEAN)
                .addModifiers(Modifier.PRIVATE, Modifier.TRANSIENT)
                .build();
            builder.addField(pythonValueSyncing);
        }

        return new StateFields(propertyFields, syncSnapshotFields, pythonValue, pythonValueSyncing);
    }

    /**
     * Bridges the methods of implemented Java interfaces, overridden host methods and abstract introduction methods; returns whether an interceptor binding was found on the way.
     */
    @SuppressWarnings("java:S107") // the flags describe one bean kind; a record for them is a refactoring of its own
    private boolean addInterfaceAndHostBridges(AbstractPythonClassElement classElement, ClassElement element, ClassDef.ClassDefBuilder builder, VisitorContext context, Set<String> addedMethodNames, @Nullable ClassElement superType, boolean extendsHostClass, boolean isDeclaredBean, boolean isIntroductionBean, boolean isAopProxy, List<PropertyElement> beanProperties) {
        Collection<ClassElement> interfaces = classElement.getInterfaces();
        for (ClassElement anInterface : interfaces) {
            TypeDef interfaceTypeDef = parameterizedTypeDef(anInterface);
            builder.addSuperinterface(interfaceTypeDef);
            List<MethodElement> methods = anInterface.getRawClassElement().getMethods();
            List<MethodElement> resolvedMethods = anInterface.getMethods();
            Set<MethodElement> methodSet = new LinkedHashSet<>();
            for (int i = 0; i < methods.size(); i++) {
                MethodElement method = methods.get(i);
                // A static interface method (Predicate.not(Predicate<? super T>)) is neither inherited nor
                // implemented by the Python class: copying it would only reproduce its signature in the stub.
                if (!methodSet.contains(method) && !method.isStatic()) {
                    MethodElement resolvedMethod = resolvedInterfaceMethod(method, resolvedMethods, i);
                    MethodElement interfaceMethod = withOwningInterface(resolvedMethod, anInterface);
                    MethodElement bridgeMethod = resolveDeclaredBridgeMethod(element, interfaceMethod);
                    if (!(method.isDefault() && bridgeMethod == interfaceMethod && !declaresOverride(element, interfaceMethod))
                        && !(bridgeMethod == interfaceMethod && isImplementedByPropertyAccessor(interfaceMethod, beanProperties))) {
                        if (interfaceMethod.hasDeclaredStereotype(InterceptorBinding.class) || bridgeMethod.hasDeclaredStereotype(InterceptorBinding.class)) {
                            isAopProxy = true;
                        }
                        ClassElement returnTypeOverride = resolveInterfaceBridgeReturnType(interfaceMethod, anInterface);
                        if (returnTypeOverride == null && bridgeMethod != interfaceMethod) {
                            returnTypeOverride = resolveDeclaredBridgeReturnType(bridgeMethod, interfaceMethod);
                        }
                        // The generated Java stub must implement the Java interface signature,
                        // not the Python source annotation signature. Python annotations are
                        // often raw while Java interfaces may declare parameterized or wildcard
                        // forms. Use the raw declaring method as the source signature and apply
                        // the resolved interface arguments separately; using the already-resolved
                        // method would collapse method variables such as CrudRepository's
                        // <S extends E> into the entity type and produce same-erasure methods
                        // that fail to override.
                        Map<String, ClassElement> signatureTypeArguments = resolvedInterfaceMethodTypeArguments(anInterface, method);
                        // A method of an introduction interface that the Python class does not declare is implemented by
                        // the proxy alone, as in Java where only the proxy class implements the introduced interface.
                        boolean introduced = bridgeMethod == interfaceMethod
                            && classElement instanceof PythonClassElement pythonClassElement
                            && pythonClassElement.isIntroductionInterface(anInterface);
                        addBridgeMethod(
                            BridgeMethodSpec.of(bridgeMethod, element).returnType(returnTypeOverride).signature(method, interfaceMethod, signatureTypeArguments).introduced(introduced),
                            builder, context, addedMethodNames);
                    }
                    methodSet.add(method);
                }
            }
        }
        if (extendsHostClass) {
            List<MethodElement> abstractHostMethods = superType.getEnclosedElements(
                ElementQuery.ALL_METHODS
                    .onlyAccessible()
                    .onlyInstance()
                    .filter(MethodElement::isAbstract));
            for (MethodElement method : abstractHostMethods) {
                addBridgeMethod(BridgeMethodSpec.of(method, element), builder, context, addedMethodNames);
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
                    addBridgeMethod(BridgeMethodSpec.of(hostMethod, element), builder, context, addedMethodNames);
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
                    addBridgeMethod(BridgeMethodSpec.of(method, element), builder, context, addedMethodNames);
                }
            }
        }

        return isAopProxy;
    }

    /**
     * Emits the constructors that wrap an existing Python value.
     */
    private void addValueConstructors(ClassStubModel model) {
        ClassDef.ClassDefBuilder builder = model.builder();
        boolean isJunit5Test = model.isJunit5Test();
        boolean extendsHostClass = model.extendsHostClass();
        boolean isIntrospectedBean = model.isIntrospectedBean();
        boolean extendsPythonClass = model.extendsPythonClass();
        @Nullable FieldDef pythonValueFinal = model.pythonValue();
        List<PropertyElement> beanProperties = model.beanProperties();
        Map<String, FieldDef> propertyFields = model.propertyFields();
        Map<String, FieldDef> syncSnapshotFields = model.syncSnapshotFields();
        @Nullable ClassElement superType = model.superType();
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
                                    assigns.add(aThis.superRef().invokeSuperConstructor(val));
                                    if (pythonValueFinal != null) {
                                        assigns.add(aThis.field(pythonValueFinal).assign(val));
                                    }
                                } else if (pythonValueFinal != null) {
                                    assigns.add(aThis.field(pythonValueFinal).assign(val));
                                }
                                assigns.addAll(polyglotValuePropertyAssignments(aThis, val, beanProperties, propertyFields, syncSnapshotFields));
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
                                    return aThis.superRef().invokeSuperConstructor(methodParameters.get(0));
                                } else {
                                    return aThis.field(pythonValueField(model)).assign(methodParameters.get(0));
                                }
                            })
                        ));
            }
        }
        if (!isJunit5Test && extendsHostClass && superType.isAssignable(Throwable.class)) {
            // A Python exception raised from GraalPy is remapped by the runtime to the generated
            // Throwable subtype through this Value constructor so Micronaut exception handlers can
            // match it. The Java base receives the arguments of the Python super().__init__(...)
            // call, read back from the exception's args, so the message and the state of the base
            // survive the crossing into Java.
            ThrowableSuperConstructor superConstructor = ThrowableSuperConstructor.resolve(model.element(), superType, model.pythonVisitorContext());
            builder.addMethod(
                MethodDef.constructor()
                    .addModifiers(Modifier.PUBLIC)
                    .addParameter(ParameterDef.of("value", POLYGLOT_VALUE))
                    .build((aThis, methodParameters) -> {
                        VariableDef.MethodParameter value = methodParameters.get(0);
                        return StatementDef.multi(
                            aThis.superRef().invokeSuperConstructor(superConstructor.arguments(value, this::convertValueForType)),
                            aThis.field(pythonValueField(model)).assign(value),
                            PYTHON_EXCEPTIONS.invokeStatic("attachCause", TypeDef.VOID, aThis, value)
                        );
                    })
            );
        }

    }

    /**
     * Bridges the declared methods that Micronaut needs to see (executable, advised, lifecycle, mapper, configuration builder) and returns them.
     */
    private BridgedMethods addBridgeMethods(ClassStubModel model, Set<String> addedMethodNames, boolean isConfigurationBuilderType) {
        ClassElement element = model.element();
        PythonVisitorContext pythonVisitorContext = model.pythonVisitorContext();
        boolean isJunit5Test = model.isJunit5Test();
        ClassDef.ClassDefBuilder builder = model.builder();
        VisitorContext context = model.context();
        List<PropertyElement> beanProperties = model.beanProperties();
        boolean isIntrospectedBean = model.isIntrospectedBean();
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
        addReferencedPythonClassReferenceFields(builder, element, methodsToBridge);
        methodsToBridge.addAll(element.getEnclosedElements(
            ElementQuery.ALL_METHODS
                .onlyAccessible()
                .onlyStatic()
                .onlyDeclared()
                .annotated(bridgeMethodFilter)));
        // an async method that is not bridged still runs in an event-loop context when another Python bean awaits
        // it, and needs the injected members adapted as a bridged one does
        boolean hasAsyncBridgeMethod = methodsToBridge.stream().anyMatch(PythonStubGenerator::isAsyncPythonMethod)
            || element.getEnclosedElements(ElementQuery.ALL_METHODS.onlyInstance().onlyDeclared())
                .stream()
                .anyMatch(PythonStubGenerator::isAsyncPythonMethod);

        boolean hasIntroductionAdviceMethod = false;
        for (MethodElement methodElement : methodsToBridge) {
            if (methodElement.hasStereotype(InterceptorBinding.class) ||
                methodElement.hasAnnotation("io.micronaut.context.annotation.Mapper") ||
                methodElement.hasAnnotation("io.micronaut.context.annotation.Mapper$Mapping")) {
                hasIntroductionAdviceMethod = true;
            }
            addBridgeMethod(BridgeMethodSpec.of(methodElement, element).junit5Test(methodElement.hasDeclaredAnnotation(JUNIT_TEST)), builder, context, addedMethodNames);
        }
        // A class can name its own pre-destroy callback with @Bean(preDestroy), the class-level counterpart of the
        // factory case handled in addBridgeMethod. The generated bean definition invokes the callback directly on the
        // stub, so it needs a bridge even though it carries no annotation of its own. A name that resolves to nothing
        // is reported by DeclaredBeanElementCreator.
        element.stringValue(Bean.class, MEMBER_PRE_DESTROY)
            .filter(name -> !name.isEmpty())
            .flatMap(name -> element.getEnclosedElement(
                ElementQuery.ALL_METHODS
                    .onlyAccessible()
                    .onlyInstance()
                    .named(name)
                    .filter(method -> !method.hasParameters())))
            .ifPresent(method -> addBridgeMethod(BridgeMethodSpec.of(method, element), builder, context, addedMethodNames));
        if (hasIntroductionAdviceMethod) {
            List<MethodElement> concreteDeclaredMethods = element.getEnclosedElements(
                ElementQuery.ALL_METHODS
                    .onlyAccessible()
                    .onlyInstance()
                    .onlyDeclared()
                    .filter(method -> !method.isAbstract())
            );
            for (MethodElement methodElement : concreteDeclaredMethods) {
                addBridgeMethod(BridgeMethodSpec.of(methodElement, element).junit5Test(methodElement.hasDeclaredAnnotation(JUNIT_TEST)), builder, context, addedMethodNames);
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
                addBridgeMethod(BridgeMethodSpec.of(methodElement, element).junit5Test(methodElement.hasDeclaredAnnotation(JUNIT_TEST)), builder, context, addedMethodNames);
            }
        }

        return new BridgedMethods(methodsToBridge, hasAsyncBridgeMethod);
    }

    /**
     * Emits @Creator factories, property getters and setters, Object methods and the property member bridge.
     */
    private void addCreatorsAndPropertyAccessors(ClassStubModel model, boolean hasAsyncBridgeMethod) {
        ClassElement element = model.element();
        ClassDef.ClassDefBuilder builder = model.builder();
        List<PropertyElement> beanProperties = model.beanProperties();
        Map<String, FieldDef> propertyFields = model.propertyFields();
        boolean isIntrospectedBean = model.isIntrospectedBean();
        VisitorContext context = model.context();
        String typeName = model.typeName();
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
                    beanProperty.getWriteMethod().ifPresent(m -> addNamedSetterDynamic(beanProperty, builder, context, hasAsyncBridgeMethod));
                    beanProperty.getReadMethod().ifPresent(m -> addNamedGetterDynamic(beanProperty, builder, true, context));
                } else {
                    addSetterPojo(beanProperty, builder, field, context);
                    addGetterPojo(beanProperty, builder, field, context);
                }
            } else {
                addSetterDynamic(beanProperty, builder, context, hasAsyncBridgeMethod);
                addGetterDynamic(beanProperty, builder);
                beanProperty.getWriteMethod().ifPresent(m -> {
                    String beanStyle = beanSetterName(beanProperty.getName());
                    if (!m.getName().equals(beanStyle)) {
                        addNamedSetterDynamic(beanProperty, builder, context, hasAsyncBridgeMethod);
                    }
                });
                beanProperty.getReadMethod().ifPresent(m -> {
                    String beanStyle = beanGetterName(beanProperty.getName());
                    String booleanBeanStyle = booleanBeanGetterName(beanProperty.getName());
                    if (!m.getName().equals(beanStyle) && (!isBooleanProperty(beanProperty) || !m.getName().equals(booleanBeanStyle))) {
                        // addGetterDynamic already emitted the is-prefixed alias of a boolean property
                        addNamedGetterDynamic(beanProperty, builder, false, context);
                    }
                });
            }
        }

        if (isIntrospectedBean) {
            ObjectHelper.addObjectMethods(builder, ClassTypeDef.of(typeName), beanProperties, propertyFields);
        }

        if (!beanProperties.isEmpty()) {
            builder.addSuperinterface(ClassTypeDef.of("io.micronaut.context.python.ValueCoercible.GeneratedPropertyMembers"));
            addValueCoerciblePropertyMembers(builder, beanProperties, propertyFields);
        }

    }

    /**
     * Part of {@link #visitClass} extracted verbatim; locals come from the model.
     */
    private void addPolyglotValueMethods(ClassStubModel model) {
        ClassDef.ClassDefBuilder builder = model.builder();
        boolean isAopProxy = model.isAopProxy();
        FieldDef pythonClassReference = model.pythonClassReference();
        boolean extendsPythonClass = model.extendsPythonClass();
        boolean isIntrospectedBean = model.isIntrospectedBean();
        boolean isJunit5Test = model.isJunit5Test();
        List<PropertyElement> beanProperties = model.beanProperties();
        boolean hasDynamicBeanProperties = model.hasDynamicBeanProperties();
        boolean isReconstructibleBean = model.isReconstructibleBean();
        Map<String, FieldDef> propertyFields = model.propertyFields();
        Map<String, FieldDef> syncSnapshotFields = model.syncSnapshotFields();
        @Nullable FieldDef pythonValueFinal = model.pythonValue();
        @Nullable FieldDef pythonValueSyncingFinal = model.pythonValueSyncing();
        ClassElement element = model.element();
        // implement asPolyglotValue by reconstructing the Python object with current field values
        if (isIntrospectedBean) {
            final boolean isAbstractIntro = element.isAbstract() && isAopProxy && element.hasStereotype(Introduction.class);
            final boolean isFrozenDataclass = isFrozenPythonDataclass(element);
            builder.addMethod(MethodDef.builder(AS_POLYGLOT_VALUE)
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .returns(POLYGLOT_VALUE).build(((aThis, methodParameters) -> {
                        if (hasDynamicBeanProperties && pythonValueFinal != null) {
                            ExpressionDef storedValue = aThis.field(pythonValueField(model));
                            return storedValue.isNonNull().doIfElse(
                                storedValue.returning(),
                                ExpressionDef.nullValue().returning()
                            );
                        }
                        if (beanProperties.isEmpty() && pythonValueFinal != null) {
                            ExpressionDef storedValue = aThis.field(pythonValueField(model));
                            ExpressionDef newValue = PYTHON_CONTEXT_RUNTIME.invokeStatic(
                                isAbstractIntro ? "newIntroduction" : "newInstance",
                                POLYGLOT_VALUE,
                                List.of(pythonClassReference(element, pythonClassReference))
                            );
                            return storedValue.isNonNull().doIfElse(
                                storedValue.returning(),
                                StatementDef.multi(
                                    aThis.field(pythonValueField(model)).assign(newValue),
                                    aThis.field(pythonValueField(model)).returning()
                                )
                            );
                        }
                        ExpressionDef reconstructedValue;
                        if (isAbstractIntro) {
                            List<ExpressionDef> arguments = new ArrayList<>(List.of(pythonClassReference(element, pythonClassReference)));
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
                            reconstructedValue = PYTHON_CONTEXT_RUNTIME.invokeStatic(
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
                                .invokeStatic(MAP_OF, TypeDef.of(Map.class), mapEntries);
                            reconstructedValue = PYTHON_CONTEXT_RUNTIME.invokeStatic(
                                "newFrozenDataclassInstance",
                                POLYGLOT_VALUE,
                                List.of(
                                    pythonClassReference(element, pythonClassReference),
                                    propsMap
                                )
                            );
                        } else {
                            reconstructedValue = PYTHON_CONTEXT_RUNTIME.invokeStatic(
                                NEW_UNINITIALIZED_INSTANCE,
                                POLYGLOT_VALUE,
                                List.of(
                                    pythonClassReference(element, pythonClassReference)
                                )
                            );
                        }
                        if (pythonValueFinal != null) {
                            if (isFrozenDataclass && !isAbstractIntro) {
                                return reconstructedValue.returning();
                            }
                            FieldDef pythonValueField = pythonValueField(model);
                            ExpressionDef storedValue = aThis.field(pythonValueField);
                            // Full sync: every field is written (used when the Python object is created).
                            List<StatementDef> syncStatements = new ArrayList<>();
                            // Incremental sync: a tracked field is written only when it changed since the
                            // last write, so attribute changes made in Python survive on untouched fields.
                            List<StatementDef> incrementalSyncStatements = new ArrayList<>();
                            if (pythonValueSyncingFinal != null) {
                                StatementDef syncing = aThis.field(pythonValueSyncingFinal).assign(ExpressionDef.trueValue());
                                syncStatements.add(syncing);
                                incrementalSyncStatements.add(syncing);
                            }
                            boolean tracked = false;
                            for (PropertyElement beanProperty : beanProperties) {
                                FieldDef field = propertyFields.get(beanProperty.getName());
                                if (field == null) {
                                    continue;
                                }
                                ExpressionDef fieldRef = aThis.field(field);
                                StatementDef write = propertyWrite(aThis, storedValue, beanProperty, field);
                                FieldDef snapshot = syncSnapshotFields.get(beanProperty.getName());
                                if (snapshot == null) {
                                    syncStatements.add(write);
                                    incrementalSyncStatements.add(write);
                                } else {
                                    tracked = true;
                                    StatementDef remember = aThis.field(snapshot).assign(fieldRef);
                                    syncStatements.add(write);
                                    syncStatements.add(remember);
                                    incrementalSyncStatements.add(
                                        fieldRef.notEqualsReferentially(aThis.field(snapshot))
                                            .doIf(StatementDef.multi(write, remember))
                                    );
                                }
                            }
                            if (pythonValueSyncingFinal != null) {
                                StatementDef synced = aThis.field(pythonValueSyncingFinal).assign(ExpressionDef.falseValue());
                                syncStatements.add(synced);
                                incrementalSyncStatements.add(synced);
                            }
                            syncStatements.add(storedValue.returning());
                            incrementalSyncStatements.add(storedValue.returning());
                            StatementDef syncBody = StatementDef.multi(syncStatements);
                            StatementDef existingValueBody = tracked ? StatementDef.multi(incrementalSyncStatements) : syncBody;
                            if (pythonValueSyncingFinal != null) {
                                existingValueBody = aThis.field(pythonValueSyncingFinal)
                                    .isTrue()
                                    .doIfElse(storedValue.returning(), existingValueBody);
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
            if (isReconstructibleBean) {
                builder.addMethod(MethodDef.builder(AS_POLYGLOT_VALUE)
                    .addAnnotation(Override.class)
                    .addModifiers(Modifier.PUBLIC)
                    .addParameter(POLYGLOT_CONTEXT)
                    .returns(POLYGLOT_VALUE)
                    .build((aThis, methodParameters) -> PYTHON_COERCION.invokeStatic(
                        "coercePooledValue",
                        POLYGLOT_VALUE,
                        aThis,
                        methodParameters.getFirst()
                    ).returning()));
                builder.addMethod(MethodDef.builder(RECONSTRUCT_POLYGLOT_VALUE)
                    .addAnnotation(Override.class)
                    .addModifiers(Modifier.PUBLIC)
                    .addParameter(POLYGLOT_CONTEXT)
                    .returns(POLYGLOT_VALUE).build(((aThis, methodParameters) -> {
                    ExpressionDef targetContext = methodParameters.getFirst();
                    StatementDef reconstructedBody;
                    if (isAbstractIntro) {
                        List<ExpressionDef> arguments = new ArrayList<>(List.of(targetContext, pythonClassReference(element, pythonClassReference)));
                        for (PropertyElement beanProperty : beanProperties) {
                            FieldDef field = propertyFields.get(beanProperty.getName());
                            if (field != null) {
                                arguments.add(coerceTypedElementToPolyglotValue(beanProperty, aThis.field(field), targetContext).cast(TypeDef.OBJECT));
                            }
                        }
                        ExpressionDef introduction = PYTHON_CONTEXT_RUNTIME.invokeStatic("newIntroduction", POLYGLOT_VALUE, arguments);
                        reconstructedBody = introduction.newLocal(TARGET_VALUE, targetValue -> StatementDef.multi(
                            PYTHON_COERCION.invokeStatic(
                                REMEMBER_POOLED_VALUE, TypeDef.VOID, aThis, targetContext, targetValue
                            ),
                            targetValue.returning()
                        ));
                    } else if (beanProperties.isEmpty()) {
                        ExpressionDef instance = PYTHON_CONTEXT_RUNTIME.invokeStatic("newInstance", POLYGLOT_VALUE,
                            List.of(targetContext, pythonClassReference(element, pythonClassReference)));
                        reconstructedBody = instance.newLocal(TARGET_VALUE, targetValue -> StatementDef.multi(
                            PYTHON_COERCION.invokeStatic(
                                REMEMBER_POOLED_VALUE, TypeDef.VOID, aThis, targetContext, targetValue
                            ),
                            targetValue.returning()
                        ));
                    } else {
                        ExpressionDef instance = PYTHON_CONTEXT_RUNTIME.invokeStatic(NEW_UNINITIALIZED_INSTANCE, POLYGLOT_VALUE,
                            List.of(targetContext, pythonClassReference(element, pythonClassReference)));
                        reconstructedBody = instance.newLocal(TARGET_VALUE, targetValue -> {
                            List<StatementDef> statements = new ArrayList<>();
                            statements.add(PYTHON_COERCION.invokeStatic(
                                REMEMBER_POOLED_VALUE, TypeDef.VOID, aThis, targetContext, targetValue
                            ));
                            List<ExpressionDef> memberNames = new ArrayList<>();
                            List<ExpressionDef> memberValues = new ArrayList<>();
                            for (PropertyElement beanProperty : beanProperties) {
                                FieldDef field = propertyFields.get(beanProperty.getName());
                                if (field == null) {
                                    continue;
                                }
                                ExpressionDef propertyValue = coerceTypedElementToPolyglotValue(
                                    beanProperty, aThis.field(field), targetContext
                                ).cast(TypeDef.OBJECT);
                                if (isFrozenDataclass) {
                                    statements.add(PYTHON_CONTEXT_RUNTIME.invokeStatic(
                                        "setInstanceProperty",
                                        TypeDef.VOID,
                                        targetValue,
                                        ExpressionDef.constant(beanProperty.getName()),
                                        propertyValue
                                    ));
                                } else {
                                    memberNames.add(ExpressionDef.constant(beanProperty.getName()));
                                    memberValues.add(propertyValue);
                                }
                            }
                            if (!memberNames.isEmpty()) {
                                statements.add(putMembers(targetValue, memberNames, memberValues));
                            }
                            statements.add(targetValue.returning());
                            return StatementDef.multi(statements);
                        });
                    }
                    if (pythonValueFinal != null) {
                        ExpressionDef storedValue = aThis.field(pythonValueField(model));
                        List<StatementDef> reuseStatements = new ArrayList<>();
                        reuseStatements.add(PYTHON_COERCION.invokeStatic(
                            REMEMBER_POOLED_VALUE, TypeDef.VOID, aThis, targetContext, storedValue
                        ));
                        if (!isFrozenDataclass) {
                            List<ExpressionDef> memberNames = new ArrayList<>();
                            List<ExpressionDef> memberValues = new ArrayList<>();
                            for (PropertyElement beanProperty : beanProperties) {
                                FieldDef field = propertyFields.get(beanProperty.getName());
                                if (field != null) {
                                    if (isSharedCollectionProperty(beanProperty)) {
                                        reuseStatements.add(propertyWrite(aThis, storedValue, beanProperty, field));
                                        continue;
                                    }
                                    memberNames.add(ExpressionDef.constant(beanProperty.getName()));
                                    memberValues.add(coerceTypedElementToPolyglotValue(
                                        beanProperty, aThis.field(field), targetContext
                                    ).cast(TypeDef.OBJECT));
                                }
                            }
                            if (!memberNames.isEmpty()) {
                                reuseStatements.add(putMembers(storedValue, memberNames, memberValues));
                            }
                        }
                        reuseStatements.add(storedValue.returning());
                        return PYTHON_COERCION.invokeStatic("isValueInContext", TypeDef.Primitive.BOOLEAN, storedValue, targetContext).isTrue()
                            .doIfElse(StatementDef.multi(reuseStatements), reconstructedBody);
                    }
                    return reconstructedBody;
                    })
                ));
            }
        } else {
            builder.addMethod(MethodDef.builder(AS_POLYGLOT_VALUE)
                .addModifiers(Modifier.PUBLIC)
                .returns(POLYGLOT_VALUE).build(((aThis, methodParameters) -> {
                    if (isJunit5Test) {
                        ExpressionDef storedValue = aThis.field(pythonValueField(model));
                        ExpressionDef newValue = PYTHON_CONTEXT_RUNTIME.invokeStatic(
                            "newInstance",
                            POLYGLOT_VALUE,
                            List.of(
                                pythonClassReference(element, pythonClassReference)
                            )
                        );
                        return storedValue.isNonNull().doIfElse(
                            storedValue.returning(),
                            StatementDef.multi(
                                aThis.field(pythonValueField(model)).assign(newValue),
                                aThis.field(pythonValueField(model)).returning()
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

    }

    /**
     * Part of {@link #visitClass} extracted verbatim; locals come from the model.
     */
    private void addFactoryMethods(ClassStubModel model) {
        ClassDef.ClassDefBuilder builder = model.builder();
        String typeName = model.typeName();
        boolean isAopProxy = model.isAopProxy();
        FieldDef pythonClassReference = model.pythonClassReference();
        @Nullable ClassElement superType = model.superType();
        boolean extendsPythonClass = model.extendsPythonClass();
        boolean extendsHostClass = model.extendsHostClass();
        boolean isIntrospectedBean = model.isIntrospectedBean();
        boolean isJunit5Test = model.isJunit5Test();
        boolean extendsThrowable = !isJunit5Test && extendsHostClass && superType.isAssignable(Throwable.class);
        List<PropertyElement> beanProperties = model.beanProperties();
        boolean hasDynamicBeanProperties = model.hasDynamicBeanProperties();
        boolean hasConfigurationBuilderProperty = model.hasConfigurationBuilderProperty();
        Map<String, FieldDef> propertyFields = model.propertyFields();
        Map<String, FieldDef> syncSnapshotFields = model.syncSnapshotFields();
        @Nullable FieldDef pythonValueFinal = model.pythonValue();
        ClassElement element = model.element();
        VisitorContext context = model.context();
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
            // A dataclass takes the fields of its dataclass base first; an introspected base is constructed from
            // them. Any other Python base only wraps the Python object, which then has to exist up front.
            final int[] superConstructorParameterIndexes = extendsPythonClass ? pythonSuperConstructorParameterIndexes(superType, parameters) : null;
            final boolean requiresPythonInstance = extendsPythonClass && superConstructorParameterIndexes == null;
                builder.addMethod(
                constructor.addModifiers(Modifier.PUBLIC).build(((aThis, methodParameters) -> {
                    if (isIntrospectedBean && (constructorParametersBackedByFields || hasDynamicBeanProperties)) {
                        if (hasConfigurationBuilderProperty || hasDynamicBeanProperties || requiresPythonInstance) {
                            List<ExpressionDef> arguments = new ArrayList<>(List.of(pythonClassReference(element, pythonClassReference)));
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
                            ExpressionDef pythonInstance = PYTHON_CONTEXT_RUNTIME.invokeStatic(
                                constructorFactoryMethod(isAbstractIntroCtor, hasDefaultedConstructorParameters),
                                POLYGLOT_VALUE,
                                arguments
                            );
                            if (extendsHostClass) {
                                List<ExpressionDef> superArguments = superConstructorArguments(superType, parameters, methodParameters);
                                return StatementDef.multi(
                                    aThis.superRef().invokeSuperConstructor(superArguments),
                                    initializeFromPolyglotValue(aThis, pythonInstance, beanProperties, propertyFields, syncSnapshotFields, pythonValueFinal, false)
                                );
                            }
                            return initializeFromPolyglotValue(aThis, pythonInstance, beanProperties, propertyFields, syncSnapshotFields, pythonValueFinal, extendsPythonClass);
                        }
                        List<StatementDef> assignments = new ArrayList<>();
                        if (extendsHostClass) {
                            assignments.add(aThis.superRef().invokeSuperConstructor(superConstructorArguments(superType, parameters, methodParameters)));
                        } else if (extendsPythonClass) {
                            List<ExpressionDef> superArguments = new ArrayList<>(superConstructorParameterIndexes.length);
                            for (int index : superConstructorParameterIndexes) {
                                superArguments.add(methodParameters.get(index));
                            }
                            assignments.add(aThis.superRef().invokeSuperConstructor(superArguments));
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
                        List<ExpressionDef> arguments = new ArrayList<>(List.of(pythonClassReference(element, pythonClassReference)));
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
                        ExpressionDef pythonInstance = PYTHON_CONTEXT_RUNTIME.invokeStatic(
                            constructorFactoryMethod(isAbstractIntroCtor, hasDefaultedConstructorParameters),
                            POLYGLOT_VALUE,
                            arguments
                        );
                        if (isIntrospectedBean && !extendsHostClass) {
                            return initializeFromPolyglotValue(aThis, pythonInstance, beanProperties, propertyFields, syncSnapshotFields, pythonValueFinal, extendsPythonClass);
                        } else if (extendsPythonClass) {
                            return aThis.superRef().invokeSuperConstructor(pythonInstance);
                        } else if (extendsThrowable) {
                            // the Value constructor forwards the Python super().__init__ arguments to the Java base
                            return invokeValueConstructor(aThis, pythonInstance);
                        } else if (extendsHostClass) {
                            List<ExpressionDef> superArguments = superConstructorArguments(superType, parameters, methodParameters);
                            return StatementDef.multi(
                                aThis.superRef().invokeSuperConstructor(superArguments),
                                aThis.field(pythonValueField(model)).assign(pythonInstance)
                            );
                        } else {
                            return aThis.field(pythonValueField(model)).assign(pythonInstance);
                        }
                    }
                }))
            );
            if (isIntrospectedBean
                && constructorParametersBackedByFields
                && !hasDynamicBeanProperties
                && !hasConfigurationBuilderProperty
                && !extendsHostClass
                && !extendsPythonClass) {
                addNoArgumentConstructor(builder, parameters, beanProperties, propertyFields);
            }
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
                if (isJunit5Test) {
                    return StatementDef.multi();
                } else if (isIntrospectedBean && hasConfigurationBuilderProperty) {
                    ExpressionDef pythonInstance = PYTHON_CONTEXT_RUNTIME.invokeStatic(
                        isAbstractIntroNoArg ? "newIntroduction" : "newInstance",
                        POLYGLOT_VALUE,
                        List.of(pythonClassReference(element, pythonClassReference))
                    );
                    if (extendsHostClass) {
                        return StatementDef.multi(
                            aThis.superRef().invokeSuperConstructor(superConstructorArguments(superType, new ParameterElement[0], methodParameters)),
                            initializeFromPolyglotValue(aThis, pythonInstance, beanProperties, propertyFields, syncSnapshotFields, pythonValueFinal, false)
                        );
                    }
                    return initializeFromPolyglotValue(aThis, pythonInstance, beanProperties, propertyFields, syncSnapshotFields, pythonValueFinal, extendsPythonClass);
                } else if (isIntrospectedBean) {
                    // Keep ordinary introspected beans lazy; resolving their Python class here
                    // would break beans whose Python constructor requires arguments.
                    return StatementDef.multi();
                } else {
                    ExpressionDef pythonInstance = PYTHON_CONTEXT_RUNTIME
                        .invokeStatic(isAbstractIntroNoArg ? "newIntroduction" : "newInstance", POLYGLOT_VALUE,
                            List.of(
                                pythonClassReference(element, pythonClassReference)
                            )
                        );
                    if (extendsPythonClass) {
                        return aThis.superRef().invokeSuperConstructor(pythonInstance);
                    } else if (extendsThrowable) {
                        return invokeValueConstructor(aThis, pythonInstance);
                    } else {
                        return aThis.field(pythonValueField(model)).assign(pythonInstance);
                    }
                }
            })));
        }

    }

    /**
     * {@code this(value)}: delegates to the constructor that wraps an existing Python value.
     */
    private static StatementDef invokeValueConstructor(VariableDef.This aThis, ExpressionDef value) {
        MethodDef valueConstructor = MethodDef.constructor()
            .addParameter(ParameterDef.of("value", POLYGLOT_VALUE))
            .build();
        return new ExpressionDef.InvokeInstanceMethod(aThis, valueConstructor, List.of(value));
    }

    /**
     * Part of {@link #visitClass} extracted verbatim; locals come from the model.
     */
    private void addInjectionMethods(ClassStubModel model, List<MethodElement> methodsToBridge) {
        ClassDef.ClassDefBuilder builder = model.builder();
        ClassElement element = model.element();
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

    }

    static TypeDef parameterizedTypeDef(ClassElement anInterface) {
        Map<String, ClassElement> typeArguments = resolvedTypeArguments(anInterface);
        TypeDef interfaceTypeDef = javaClassType(anInterface);
        List<? extends GenericPlaceholderElement> declaredPlaceholders = anInterface.getDeclaredGenericPlaceholders();
        if (!typeArguments.isEmpty() && !rendersRaw(anInterface, typeArguments, declaredPlaceholders)) {
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
                annotationDefs.add(PythonAnnotationStubGenerator.buildAnnotationDef(annotationValue.getAnnotationName(), annotationValue.getValues()));
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

    static TypeDef sourceSignatureType(ClassElement anInterface) {
        return sourceSignatureType(anInterface, false, Map.of());
    }

    private static TypeDef sourceSignatureType(
        ClassElement anInterface,
        boolean typeArgument,
        Map<String, ClassElement> signatureTypeArguments
    ) {
        if (anInterface.isArray() && (anInterface instanceof GenericPlaceholderElement || anInterface instanceof WildcardElement)) {
            // E[] / E... keeps its array shape around the resolved type variable
            return sourceSignatureType(anInterface.fromArray(), typeArgument, signatureTypeArguments).array(anInterface.getArrayDimensions());
        }
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
            if (resolvedTypeArgument != null && !samePlaceholderType(placeholder, resolvedTypeArgument)) {
                return sourceSignatureType(resolvedTypeArgument, typeArgument, signatureTypeArguments);
            }
            if (isMethodTypeVariable(placeholder)) {
                return TypeDef.variable(placeholder.getVariableName());
            }
            Optional<ClassElement> resolved = placeholder.getResolved();
            if (resolved.isPresent() && !samePlaceholderType(placeholder, resolved.get())) {
                return sourceSignatureType(resolved.get(), typeArgument, signatureTypeArguments);
            }
            if (placeholder.isRawType()) {
                return boundSignatureType(placeholder, typeArgument, signatureTypeArguments);
            }
            return TypeDef.variable(placeholder.getVariableName());
        }
        if (anInterface.isArray()) {
            return TypeDef.of(anInterface);
        }
        if (anInterface.isPrimitive()) {
            TypeDef primitiveType = TypeDef.of(anInterface);
            if (typeArgument && TypeDef.Primitive.VOID.equals(primitiveType)) {
                return ClassTypeDef.of(Void.class);
            }
            return typeArgument ? TypeDescriptors.toBoxedIfNecessary(primitiveType) : primitiveType;
        }
        if (anInterface.isRawType()) {
            return javaClassType(anInterface);
        }
        Map<String, ClassElement> typeArguments = resolvedTypeArguments(anInterface);
        TypeDef interfaceTypeDef = javaClassType(anInterface);
        List<? extends GenericPlaceholderElement> declaredPlaceholders = anInterface.getDeclaredGenericPlaceholders();
        if (!typeArguments.isEmpty() && !rendersRaw(anInterface, typeArguments, declaredPlaceholders)) {
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
        if (signatureType.isArray() && (signatureType instanceof GenericPlaceholderElement || signatureType instanceof WildcardElement)) {
            // E[] / E... keeps its array shape around the resolved type variable
            ClassElement resolvedComponentType = resolvedType != null && resolvedType.isArray() ? resolvedType.fromArray() : resolvedType;
            return bridgeSignatureType(signatureType.fromArray(), resolvedComponentType, signatureTypeArguments).array(signatureType.getArrayDimensions());
        }
        if (signatureType instanceof WildcardElement wildcardElement) {
            // The resolved counterpart of a wildcard is usually a wildcard too: its bound, not the
            // wildcard itself, resolves the signature bound (otherwise `? super T` resolved against
            // `? super Book` would render as `? super ? super Book`).
            WildcardElement resolvedWildcard = resolvedType instanceof WildcardElement wildcard ? wildcard : null;
            if (!wildcardElement.getLowerBounds().isEmpty()) {
                ClassElement resolvedBound = resolvedWildcard == null ? resolvedType : firstOrNull(resolvedWildcard.getLowerBounds());
                return TypeDef.wildcardSupertypeOf(bridgeSignatureType(wildcardElement.getLowerBounds().getFirst(), resolvedBound, signatureTypeArguments));
            }
            if (!wildcardElement.getUpperBounds().isEmpty()) {
                ClassElement upperBound = wildcardElement.getUpperBounds().getFirst();
                if (!Object.class.getName().equals(upperBound.getName())) {
                    ClassElement resolvedBound = resolvedWildcard == null ? resolvedType : firstOrNull(resolvedWildcard.getUpperBounds());
                    return TypeDef.wildcardSubtypeOf(bridgeSignatureType(upperBound, resolvedBound, signatureTypeArguments));
                }
            }
            if (resolvedWildcard != null) {
                return sourceSignatureType(resolvedWildcard, true, signatureTypeArguments);
            }
            if (resolvedType != null && !isObjectType(resolvedType)) {
                return TypeDef.wildcardSubtypeOf(sourceSignatureType(resolvedType, true, signatureTypeArguments));
            }
            return TypeDef.wildcard();
        }
        if (signatureType instanceof GenericPlaceholderElement placeholder) {
            ClassElement resolvedTypeArgument = resolveMappedTypeArgument(signatureTypeArguments.get(placeholder.getVariableName()));
            if (resolvedTypeArgument != null && !samePlaceholderType(placeholder, resolvedTypeArgument)) {
                return sourceSignatureType(resolvedTypeArgument, false, signatureTypeArguments);
            }
            if (isMethodTypeVariable(placeholder)) {
                return TypeDef.variable(placeholder.getVariableName());
            }
            if (resolvedType != null && !isObjectType(resolvedType)) {
                return sourceSignatureType(resolvedType, false, signatureTypeArguments);
            }
            Optional<ClassElement> resolved = placeholder.getResolved();
            if (resolved.isPresent() && !samePlaceholderType(placeholder, resolved.get())) {
                return sourceSignatureType(resolved.get(), false, signatureTypeArguments);
            }
            if (placeholder.isRawType()) {
                return boundBridgeSignatureType(placeholder, null, signatureTypeArguments);
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
            if (rendersRaw(signatureType, typeArguments, declaredPlaceholders)) {
                return javaClassType(signatureType);
            }
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

    /**
     * Adds the no-argument constructor that reflection-based frameworks (JPA, JAXB, Java serialization)
     * instantiate a field-backed class with before they populate its fields. Only the constant defaults of
     * the Python constructor are applied: the Python object itself is created lazily by
     * {@code asPolyglotValue()} from the field values, as after the field-assigning constructor.
     */
    private static void addNoArgumentConstructor(ClassDef.ClassDefBuilder builder,
                                                 ParameterElement[] parameters,
                                                 List<PropertyElement> beanProperties,
                                                 Map<String, FieldDef> propertyFields) {
        PythonParameterDefaultValueProvider defaultValueProvider = new PythonParameterDefaultValueProvider();
        builder.addMethod(MethodDef.constructor()
            .addModifiers(Modifier.PUBLIC)
            .build((aThis, methodParameters) -> {
                List<StatementDef> assignments = new ArrayList<>();
                for (ParameterElement parameter : parameters) {
                    PropertyElement beanProperty = findBeanProperty(beanProperties, parameter.getName());
                    FieldDef field = propertyFields.get(parameter.getName());
                    if (beanProperty == null || field == null || !hasDefaultValue(parameter)) {
                        continue;
                    }
                    ExpressionDef defaultValue = defaultedConstructorParameterValue(parameter);
                    if (defaultValue == null) {
                        defaultValue = defaultValueProvider.defaultValueExpression(parameter, null)
                            .filter(value -> !(value instanceof ExpressionDef.Constant constant && constant.value() == null))
                            .orElse(null);
                    }
                    if (defaultValue != null) {
                        assignments.add(aThis.field(field).assign(defaultValue));
                    }
                }
                return StatementDef.multi(assignments);
            }));
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

    /**
     * The positions, among the constructor parameters, of the arguments of the constructor of an introspected Python
     * base class, which a dataclass inherits as its leading fields. {@code null} when the base has to be constructed
     * from the Python object instead: it is not introspected, or its constructor takes a parameter of another name.
     */
    private static int @Nullable [] pythonSuperConstructorParameterIndexes(ClassElement superType, ParameterElement[] parameters) {
        if (!superType.hasStereotype(Introspected.class)) {
            return null;
        }
        ParameterElement[] superParameters = superType.getPrimaryConstructor()
            .map(MethodElement::getParameters)
            .orElse(ParameterElement.ZERO_PARAMETER_ELEMENTS);
        int[] indexes = new int[superParameters.length];
        for (int i = 0; i < superParameters.length; i++) {
            indexes[i] = indexOfParameter(parameters, superParameters[i].getName());
            if (indexes[i] < 0) {
                return null;
            }
        }
        return indexes;
    }

    private static int indexOfParameter(ParameterElement[] parameters, String name) {
        for (int i = 0; i < parameters.length; i++) {
            if (parameters[i].getName().equals(name)) {
                return i;
            }
        }
        return -1;
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
        return parameter.getNativeType() instanceof ArgumentDef argumentDef
            && argumentDef.defaultValue() instanceof DefaultFactoryDef factory
            && factory.isBuiltin(factoryName);
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
            return boundBridgeSignatureType(placeholder, resolvedTypeArgument, signatureTypeArguments);
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
        return samePlaceholder(argumentPlaceholder, placeholder);
    }

    private static TypeDef sourceTypeArgument(ClassElement typeArgument, @Nullable GenericPlaceholderElement placeholder) {
        return sourceTypeArgument(typeArgument, placeholder, Map.of());
    }

    private static TypeDef sourceTypeArgument(
        ClassElement typeArgument,
        @Nullable GenericPlaceholderElement placeholder,
        Map<String, ClassElement> resolvedTypeArguments
    ) {
        if (typeArgument instanceof WildcardElement) {
            return sourceSignatureType(typeArgument, true, resolvedTypeArguments);
        }
        if (typeArgument instanceof GenericPlaceholderElement
            && (placeholder == null || !isDeclaredPlaceholderArgument(typeArgument, placeholder))) {
            return sourceSignatureType(typeArgument, true, resolvedTypeArguments);
        }
        if (placeholder != null) {
            if (isDeclaredPlaceholderArgument(typeArgument, placeholder)) {
                return boundSignatureType(placeholder, true, resolvedTypeArguments);
            }
            if (isObjectType(typeArgument)) {
                Optional<ClassElement> bound = firstNonObjectBound(placeholder);
                if (bound.isPresent() && !referencesPlaceholder(bound.get(), placeholder, new HashSet<>())) {
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

    private static @Nullable DecoratorDef findScriptDecorator(PythonScriptElement scriptElement, PythonVisitorContext context) {
        return context
            .getProcessingEnvironment()
            .environment()
            .decorators()
            .get(scriptElement.getName());
    }

    private static boolean isScriptTestMethod(MethodElement methodElement) {
        return methodElement.getName().startsWith("test");
    }

    private void visitScript(PythonScriptElement scriptElement, VisitorContext context) {
        try {
            if (classBuilders.containsKey(scriptElement.getName())) {
                return;
            }

            String typeName = scriptElement.getName();

            var builder = ClassDef.builder(scriptElement.getPackageName() + "." + scriptElement.getSimpleName())
                .addModifiers(Modifier.PUBLIC);
            ScriptDef scriptDef = scriptElement.getNativeType();
            builder.addAnnotation(AnnotationDef.builder(PYTHON_MODULE_ANNOTATION)
                .addMember("moduleName", scriptDef.name())
                .addMember("packageName", scriptElement.getPackageName())
                .build());
            builder.addAnnotation(Vetoed.class);
            copyRuntimeAnnotations(scriptElement, builder, ElementType.TYPE, context);
            builder.addSuperinterface(ClassTypeDef.of("io.micronaut.context.python.ValueCoercible"));
            boolean isJunit5TestModule = scriptElement.hasAnnotation(ANN_MICRONAUT_TEST);

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
                    ExpressionDef pythonInstance = PYTHON_CONTEXT_RUNTIME
                        .invokeStatic("findScript", POLYGLOT_VALUE,
                            List.of(
                                ExpressionDef.constant(scriptElement.getPackageName()),
                                ExpressionDef.constant(name)
                            )
                        );

                    // Scripts are singletons: the most recently constructed instance is the INSTANCE
                    // returned by getInstance() and fromPolyglotValue().
                    return StatementDef.multi(
                        aThis.field(pythonValue).assign(pythonInstance),
                        aThis.field(instanceField).assign(aThis)
                    );
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
                    .annotated(ann -> isJunit5TestModule ||
                        ann.hasStereotype(Executable.class) ||
                            ann.hasAnnotation(AnnotationUtil.PRE_DESTROY) ||
                            ann.hasAnnotation(AnnotationUtil.POST_CONSTRUCT) ||
                            ann.hasStereotype(Around.class) ||
                            ann.hasDeclaredStereotype(AnnotationUtil.SCOPE) ||
                            isDeclaredBeanMethod(ann)));

            for (MethodElement methodElement : methodsToBridge) {
                boolean isJunit5Test = methodElement.hasDeclaredAnnotation(JUNIT_TEST)
                    || (isJunit5TestModule && isScriptTestMethod(methodElement));
                addBridgeMethod(BridgeMethodSpec.of(methodElement, scriptElement).junit5Test(isJunit5Test).script(true), builder, context, addedMethodNames);
            }

            // Find injection fields (script attributes)
            List<PropertyElement> beanProperties = scriptElement.getBeanProperties();
            for (PropertyElement beanProperty : beanProperties) {
                if (beanProperty.hasStereotype(AnnotationUtil.INJECT)) {
                    // scripts rely on polyglot value; keep old behavior
                    addSetterScript(beanProperty, builder, pythonValue);
                }

                if (beanProperty.hasStereotype(Bean.class) || beanProperty.hasStereotype(AnnotationUtil.INJECT)) {
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
        if (t instanceof WildcardElement wildcard) {
            return erasedType(wildcardErasure(wildcard));
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
        if (genericType instanceof GenericPlaceholderElement) {
            // the field of a generic dataclass attribute (result: T) has the type variable as its type
            return sourceSignatureType(genericType);
        }
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

    static void addMethodTypeVariables(MethodElement methodElement, MethodDef.MethodDefBuilder methodBuilder) {
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
        methodTypeVariables(methodElement, resolvedTypeArguments, inferredMethodBounds).forEach(methodBuilder::addTypeVariable);
    }

    private static List<TypeDef.TypeVariable> methodTypeVariables(
        MethodElement methodElement,
        Map<String, ClassElement> resolvedTypeArguments,
        Map<String, ClassElement> inferredMethodBounds
    ) {
        List<TypeDef.TypeVariable> typeVariables = new ArrayList<>();
        for (GenericPlaceholderElement placeholder : methodElement.getDeclaredTypeVariables()) {
            List<TypeDef> bounds = placeholder.getBounds().stream()
                .filter(bound -> !Object.class.getName().equals(bound.getName()))
                .map(bound -> typeVariableReferencesAsClassNames(sourceSignatureType(bound, true, resolvedTypeArguments)))
                .toList();
            ClassElement inferredBound = inferredMethodBounds.get(placeholder.getVariableName());
            if (bounds.isEmpty() && inferredBound != null && !isObjectType(inferredBound)) {
                bounds = List.of(sourceSignatureType(inferredBound, true, resolvedTypeArguments));
            }
            typeVariables.add(TypeDef.variable(placeholder.getVariableName(), bounds));
        }
        return typeVariables;
    }

    /**
     * The source generator renders the bounds of a method type variable without the method in
     * scope, so a bound that references another variable of the same method (a variable
     * {@code L} bounded by a list of the variable {@code A}) would print as that variable's
     * bound. A plain class type named like the variable prints as the variable.
     */
    private static TypeDef typeVariableReferencesAsClassNames(TypeDef typeDef) {
        return switch (typeDef) {
            case TypeDef.TypeVariable typeVariable -> ClassTypeDef.of(typeVariable.name());
            case ClassTypeDef.Parameterized parameterized -> TypeDef.parameterized(
                parameterized.rawType(),
                parameterized.typeArguments().stream().map(PythonStubGenerator::typeVariableReferencesAsClassNames).toList()
            );
            case TypeDef.Wildcard wildcard -> new TypeDef.Wildcard(
                wildcard.upperBounds().stream().map(PythonStubGenerator::typeVariableReferencesAsClassNames).toList(),
                wildcard.lowerBounds().stream().map(PythonStubGenerator::typeVariableReferencesAsClassNames).toList()
            );
            case TypeDef.Array array -> TypeDef.array(typeVariableReferencesAsClassNames(array.componentType()), array.dimensions());
            default -> typeDef;
        };
    }

    private static @Nullable ClassElement firstOrNull(List<? extends ClassElement> elements) {
        return elements.isEmpty() ? null : elements.getFirst();
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

    static String javaTypeName(ClassElement t) {
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

    /**
     * The erasure of a wildcard: its upper bound ({@code ? extends X} erases to {@code X},
     * {@code ?} and {@code ? super X} to {@code Object}).
     */
    private static ClassElement wildcardErasure(WildcardElement wildcard) {
        List<? extends ClassElement> upperBounds = wildcard.getUpperBounds();
        if (upperBounds.isEmpty()) {
            return ClassElement.of(Object.class);
        }
        return upperBounds.getFirst();
    }

    /**
     * Whether two placeholders are the same type variable. Placeholders cannot be compared with
     * {@code equals}: a Java placeholder is represented by its bound, so every unbounded type
     * variable equals every other one.
     */
    private static boolean samePlaceholder(GenericPlaceholderElement left, GenericPlaceholderElement right) {
        if (left == right) {
            return true;
        }
        if (!left.getVariableName().equals(right.getVariableName())) {
            return false;
        }
        Optional<Element> leftDeclaring = left.getDeclaringElement();
        Optional<Element> rightDeclaring = right.getDeclaringElement();
        if (leftDeclaring.isEmpty() || rightDeclaring.isEmpty()) {
            return left.equals(right);
        }
        return leftDeclaring.get().equals(rightDeclaring.get());
    }

    private static boolean isMethodTypeVariable(GenericPlaceholderElement placeholder) {
        return placeholder.getDeclaringElement().filter(MethodElement.class::isInstance).isPresent();
    }

    /**
     * Whether a placeholder already carries the type it resolves to in the signature it was read
     * from, so it must not be re-resolved by name against the type arguments of another declaration.
     */
    private static boolean isResolvedPlaceholder(GenericPlaceholderElement placeholder) {
        return !placeholder.isRawType() && placeholder.getResolved().filter(resolved -> !samePlaceholderType(placeholder, resolved)).isPresent();
    }

    private static boolean samePlaceholderType(GenericPlaceholderElement placeholder, ClassElement resolved) {
        return resolved instanceof GenericPlaceholderElement resolvedPlaceholder && samePlaceholder(placeholder, resolvedPlaceholder);
    }

    /**
     * Whether a type mentions the given placeholder in its type arguments or, through other
     * placeholders, in their bounds: expanding such a bound into a signature would never end
     * ({@code B extends Builder<T, B>}, {@code E extends Enum<E>}).
     */
    private static boolean referencesPlaceholder(ClassElement type, GenericPlaceholderElement placeholder, Set<String> visitedPlaceholders) {
        if (type instanceof GenericPlaceholderElement candidate) {
            if (samePlaceholder(candidate, placeholder)) {
                return true;
            }
            if (!visitedPlaceholders.add(placeholderKey(candidate))) {
                return false;
            }
            for (ClassElement bound : candidate.getBounds()) {
                if (referencesPlaceholder(bound, placeholder, visitedPlaceholders)) {
                    return true;
                }
            }
            return false;
        }
        if (type instanceof WildcardElement wildcard) {
            for (ClassElement bound : wildcard.getUpperBounds()) {
                if (referencesPlaceholder(bound, placeholder, visitedPlaceholders)) {
                    return true;
                }
            }
            for (ClassElement bound : wildcard.getLowerBounds()) {
                if (referencesPlaceholder(bound, placeholder, visitedPlaceholders)) {
                    return true;
                }
            }
            return false;
        }
        for (ClassElement typeArgument : type.getTypeArguments().values()) {
            if (referencesPlaceholder(typeArgument, placeholder, visitedPlaceholders)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether a type mentions the given raw type in its type arguments or, through placeholders,
     * in their bounds.
     */
    private static boolean referencesRawType(ClassElement type, String rawTypeName, Set<String> visitedPlaceholders) {
        if (type instanceof GenericPlaceholderElement placeholder) {
            if (!visitedPlaceholders.add(placeholderKey(placeholder))) {
                return false;
            }
            for (ClassElement bound : placeholder.getBounds()) {
                if (referencesRawType(bound, rawTypeName, visitedPlaceholders)) {
                    return true;
                }
            }
            return false;
        }
        if (type instanceof WildcardElement wildcard) {
            for (ClassElement bound : wildcard.getUpperBounds()) {
                if (referencesRawType(bound, rawTypeName, visitedPlaceholders)) {
                    return true;
                }
            }
            for (ClassElement bound : wildcard.getLowerBounds()) {
                if (referencesRawType(bound, rawTypeName, visitedPlaceholders)) {
                    return true;
                }
            }
            return false;
        }
        if (sameRawTypeName(type, rawTypeName)) {
            return true;
        }
        for (ClassElement typeArgument : type.getTypeArguments().values()) {
            if (referencesRawType(typeArgument, rawTypeName, visitedPlaceholders)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether a reference to a generic type has to stay raw: one of its type arguments is a
     * variable of the type itself (an unparameterized reference), or the {@code Object} the element
     * model substitutes for a cyclic argument, and that variable's bound refers back to the type
     * ({@code AgentBuilder<T, B extends AgentBuilder<T, ?>>}). Expanding such a bound never ends,
     * and neither its erasure nor {@code Object} is within the bound; the raw type is.
     */
    private static boolean rendersRaw(
        ClassElement type,
        Map<String, ClassElement> typeArguments,
        List<? extends GenericPlaceholderElement> declaredPlaceholders
    ) {
        String rawTypeName = type.getName();
        int index = 0;
        for (Map.Entry<String, ClassElement> entry : typeArguments.entrySet()) {
            GenericPlaceholderElement placeholder = placeholderFor(declaredPlaceholders, entry.getKey(), index++);
            if (placeholder != null) {
                ClassElement typeArgument = entry.getValue();
                if (isDeclaredPlaceholderArgument(typeArgument, placeholder) || isObjectType(typeArgument)) {
                    ClassElement bound = firstBound(placeholder);
                    if (referencesRawType(bound, rawTypeName, new HashSet<>()) || referencesPlaceholder(bound, placeholder, new HashSet<>())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Identifies a placeholder by its declaring element and variable name. A method is identified by
     * its owner and parameter types, not only its simple name, so the {@code T} of {@code <T> foo(a)}
     * and the {@code T} of {@code <T> foo(a, b)} are distinct keys.
     */
    private static String placeholderKey(GenericPlaceholderElement placeholder) {
        String declaringElement = placeholder.getDeclaringElement().map(element -> {
            if (element instanceof MethodElement methodElement) {
                return methodElement.getOwningType().getName() + "." + bridgeMethodKey(methodElement);
            }
            return element.getName();
        }).orElse("");
        return declaringElement + "#" + placeholder.getVariableName();
    }

    /**
     * The first bound of a placeholder as a source type. A bound that refers back to the
     * placeholder is rendered as its erasure.
     */
    private static TypeDef boundSignatureType(
        GenericPlaceholderElement placeholder,
        boolean typeArgument,
        Map<String, ClassElement> signatureTypeArguments
    ) {
        ClassElement bound = firstBound(placeholder);
        if (referencesPlaceholder(bound, placeholder, new HashSet<>())) {
            return javaClassType(bound);
        }
        return sourceSignatureType(bound, typeArgument, signatureTypeArguments);
    }

    /**
     * The first bound of a placeholder as a bridge signature type, see {@link #boundSignatureType}.
     */
    private static TypeDef boundBridgeSignatureType(
        GenericPlaceholderElement placeholder,
        @Nullable ClassElement resolvedType,
        Map<String, ClassElement> signatureTypeArguments
    ) {
        ClassElement bound = firstBound(placeholder);
        if (referencesPlaceholder(bound, placeholder, new HashSet<>())) {
            return javaClassType(bound);
        }
        return bridgeSignatureType(bound, resolvedType, signatureTypeArguments);
    }

    private static String pythonSimpleName(ClassElement element) {
        if (element instanceof AbstractPythonClassElement pythonClassElement) {
            return pythonClassElement.getNativeType().name().replace('$', '.');
        }
        return element.getSimpleName();
    }

    static FieldDef pythonClassReferenceField(String fieldName, ClassElement element) {
        return FieldDef.builder(fieldName)
            .ofType(PYTHON_CLASS_REFERENCE)
            .addModifiers(Modifier.STATIC, Modifier.FINAL)
            .initializer(pythonClassReferenceExpression(element))
            .build();
    }

    /**
     * The expression instantiating the {@code PythonClassReference} of a Python class.
     *
     * @param element The Python class
     * @return The instantiation expression
     */
    static ExpressionDef pythonClassReferenceExpression(ClassElement element) {
        PythonClassReferenceDef classReference = pythonClassReferenceDef(element);
        List<ExpressionDef> nestedMembers = new ArrayList<>();
        for (String nestedMemberName : classReference.nestedMemberNames()) {
            nestedMembers.add(ExpressionDef.constant(nestedMemberName));
        }
        return PYTHON_CLASS_REFERENCE.instantiate(
            ExpressionDef.constant(classReference.packageName()),
            ExpressionDef.constant(classReference.rootName()),
            TypeDef.STRING.array().instantiate(nestedMembers),
            ExpressionDef.constant(classReference.displayName()),
            ExpressionDef.constant(classReference.cacheKey())
        );
    }

    static AnnotationDef pythonClassAnnotation(ClassElement element) {
        PythonClassReferenceDef classReference = pythonClassReferenceDef(element);
        return AnnotationDef.builder(PYTHON_CLASS_ANNOTATION)
            .addMember("packageName", classReference.packageName())
            .addMember("rootName", classReference.rootName())
            .addMember("nestedMemberNames", classReference.nestedMemberNames())
            .addMember("displayName", classReference.displayName())
            .addMember("cacheKey", classReference.cacheKey())
            .build();
    }

    private static PythonClassReferenceDef pythonClassReferenceDef(ClassElement element) {
        String pythonSimpleName = pythonSimpleName(element);
        int nestedSeparator = pythonSimpleName.indexOf('.');
        String rootName = nestedSeparator < 0 ? pythonSimpleName : pythonSimpleName.substring(0, nestedSeparator);
        List<String> nestedMemberNames = new ArrayList<>();
        if (nestedSeparator >= 0) {
            nestedMemberNames.addAll(List.of(pythonSimpleName.substring(nestedSeparator + 1).split("\\.")));
        }
        String packageName = element.getPackageName();
        return new PythonClassReferenceDef(
            packageName,
            rootName,
            nestedMemberNames,
            pythonSimpleName,
            "class-instance:" + java.util.Objects.toString(packageName, "python") + "." + pythonSimpleName
        );
    }

    static void addReferencedPythonClassReferenceFields(ClassDef.ClassDefBuilder builder, ClassElement owner, List<MethodElement> methods) {
        referencedPythonClassReferenceFields(owner, methods).forEach(builder::addField);
    }

    private static void addReferencedPythonClassReferenceFields(EnumDef.EnumDefBuilder builder, ClassElement owner, List<MethodElement> methods) {
        referencedPythonClassReferenceFields(owner, methods).forEach(builder::addField);
    }

    private static List<FieldDef> referencedPythonClassReferenceFields(ClassElement owner, List<MethodElement> methods) {
        List<FieldDef> fields = new ArrayList<>();
        Set<String> added = new HashSet<>();
        for (MethodElement method : methods) {
            ClassElement declaringType = method.getDeclaringType();
            if (!(declaringType instanceof AbstractPythonClassElement)
                || owner.getName().equals(declaringType.getName())
                || !added.add(declaringType.getName())) {
                continue;
            }
            if (method.isStatic() || isAsyncPythonMethod(method) && !declaringType.isAbstract()) {
                fields.add(pythonClassReferenceField(pythonClassReferenceFieldName(declaringType), declaringType));
            }
        }
        return fields;
    }

    static ExpressionDef pythonClassReference(ClassElement owner, FieldDef field) {
        return ClassTypeDef.of(owner.getName()).getStaticField(field.getName(), PYTHON_CLASS_REFERENCE);
    }

    static ExpressionDef pythonClassReference(ClassElement owner, ClassElement element) {
        if (!(element instanceof AbstractPythonClassElement) || owner.getName().equals(element.getName())) {
            return ClassTypeDef.of(owner.getName()).getStaticField("__PYTHON_CLASS_REFERENCE", PYTHON_CLASS_REFERENCE);
        }
        return ClassTypeDef.of(owner.getName()).getStaticField(pythonClassReferenceFieldName(element), PYTHON_CLASS_REFERENCE);
    }

    private static String pythonClassReferenceFieldName(ClassElement element) {
        return "__PYTHON_CLASS_REFERENCE_" + Integer.toHexString(element.getName().hashCode()).replace('-', '_');
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
        ClassElement genericType = param.getGenericType();
        boolean mapOfPython = genericType.isAssignable(Map.class) && genericType.getTypeArguments().get("V") instanceof PythonClassElement;
        boolean listOfPython = genericType.isAssignable(List.class) && genericType.getTypeArguments().get("E") instanceof PythonClassElement;
        if (targetContext != null) {
            // Generated Python-class arguments stay as host bridges for ordinary methods, including
            // inside lists and maps, so Python consistently uses their generated Java accessors.
            // Pooled bridges instead pass raw arguments to invokePooled, which converts them after
            // borrowing the target context.
            ExpressionDef parameter;
            if (mapOfPython) {
                parameter = PYTHON_COERCION.invokeStatic(COERCE_MAP, TypeDef.of(Map.class), methodParam);
            } else if (listOfPython) {
                parameter = PYTHON_COERCION.invokeStatic(COERCE_LIST, TypeDef.of(List.class), methodParam);
            } else if (genericType instanceof PythonClassElement) {
                parameter = methodParam;
            } else {
                parameter = PYTHON_COERCION.invokeStatic(
                    "coerceToContext",
                    TypeDef.OBJECT,
                    methodParam,
                    targetContext,
                    classLiteral(param.getGenericType())
                );
            }
            parameters.add(parameter);
            return;
        }
        ExpressionDef parameter;
        if (mapOfPython) {
            parameter = PYTHON_COERCION.invokeStatic(COERCE_MAP, TypeDef.of(Map.class), methodParam);
        } else if (listOfPython) {
            parameter = PYTHON_COERCION.invokeStatic(COERCE_LIST, TypeDef.of(List.class), methodParam);
        } else {
            parameter = methodParam;
        }
        parameters.add(parameter);
    }

    private static ExpressionDef coerceTypedElementToPolyglotValue(TypedElement element, ExpressionDef expr) {
        ClassElement genericType = element.getGenericType();
        if (genericType.isAssignable(Map.class) && genericType.getTypeArguments().get("V") instanceof PythonClassElement) {
            return PYTHON_COERCION.invokeStatic(COERCE_MAP, TypeDef.of(Map.class), expr);
        } else if (genericType.isAssignable(List.class) && genericType.getTypeArguments().get("E") instanceof PythonClassElement) {
            return PYTHON_COERCION.invokeStatic(COERCE_LIST, TypeDef.of(List.class), expr);
        } else if (genericType instanceof PythonClassElement) {
            return PYTHON_COERCION.invokeStatic("coerceValue", TypeDef.OBJECT, expr);
        } else {
            return expr;
        }
    }

    private static ExpressionDef coerceTypedElementToPolyglotValue(TypedElement element,
                                                                    ExpressionDef expr,
                                                                    ExpressionDef targetContext) {
        return PYTHON_COERCION.invokeStatic("coerceToContext", TypeDef.OBJECT, expr, targetContext, classLiteral(element.getGenericType()));
    }

    /**
     * Copies the runtime annotations of a Python-defined property accessor ({@code @property} getter or
     * setter) onto the generated accessor, which is where a framework that reads properties reflectively
     * (JPA property access) looks for them. Synthetic accessors carry the field's annotations, which
     * belong on the generated field instead.
     */
    private void copyAccessorAnnotations(Optional<MethodElement> accessor, MethodDef.MethodDefBuilder builder, VisitorContext visitorContext) {
        accessor.filter(method -> !method.isSynthetic())
            .ifPresent(method -> copyRuntimeAnnotations(method, builder, ElementType.METHOD, visitorContext));
    }

    /**
     * Copies the annotations that reflection-based frameworks (JPA, JAXB, Bean Validation, JUnit, ...)
     * need to find on the generated Java declaration: every annotation of a Java annotation type with
     * {@link RetentionPolicy#RUNTIME} retention that may be placed on such a declaration. Micronaut and
     * dependency injection annotations are served through the annotation metadata of the Python element
     * and are only copied when a framework reads them reflectively ({@link #MICRONAUT_ANNOTATIONS_TO_COPY});
     * Python-defined annotations and the {@code java.lang} annotations that constrain the declaration
     * ({@code @FunctionalInterface}, {@code @SafeVarargs}) are never copied.
     *
     * @param element        The Python element
     * @param builder        The builder of the generated declaration
     * @param declaration    The kind of the generated declaration
     * @param visitorContext The visitor context
     */
    private void copyRuntimeAnnotations(Element element, AbstractElementBuilder<?> builder, ElementType declaration, VisitorContext visitorContext) {
        AnnotationMetadata annotationMetadata = element.getAnnotationMetadata();
        for (String annotationName : annotationMetadata.getDeclaredAnnotationNames()) {
            AnnotationValue<Annotation> av = annotationMetadata.getAnnotation(annotationName);
            if (!isCopiedRuntimeAnnotation(annotationName, declaration, visitorContext) || av == null) {
                continue;
            }
            try {
                builder.addAnnotation(reflectiveAnnotationDef(av, element, visitorContext));
            } catch (UnrepresentableAnnotationException e) {
                visitorContext.warn("Annotation @" + annotationName + " is not copied onto the generated Java declaration of ["
                    + element.getName() + "], reflection-based frameworks will not see it: " + e.getMessage(), element);
            }
        }
    }

    /**
     * Builds the source representation of an annotation, checking that every member value can be written as a
     * Java constant of the member type. The metadata can hold members the annotation type does not declare
     * (added by an annotation mapper) and values the source cannot express, such as an unresolved Python
     * expression for a nested annotation. Such a member is left out, with a warning, when the annotation type
     * fills it with its default: a member that does not compile would break the whole generated class. Only a
     * member without a default cannot be left out, and then the annotation as a whole is not copied.
     *
     * @param annotation The annotation
     * @param element    The annotated Python element, for the warnings
     * @param context    The visitor context
     * @return The annotation definition
     * @throws UnrepresentableAnnotationException When the annotation cannot be written at all
     */
    private static AnnotationDef reflectiveAnnotationDef(AnnotationValue<?> annotation, Element element, VisitorContext context) {
        String annotationName = annotation.getAnnotationName();
        ClassElement annotationType = context.getClassElement(annotationName)
            .orElseThrow(() -> new UnrepresentableAnnotationException("the annotation type cannot be resolved"));
        Map<String, ClassElement> memberTypes = new LinkedHashMap<>();
        for (MethodElement member : annotationType.getMethods()) {
            memberTypes.putIfAbsent(member.getName(), member.getReturnType());
        }
        Set<String> defaultedMembers = PythonAnnotationTypes.defaultedMembers(annotationType);
        AnnotationDef.AnnotationDefBuilder builder = AnnotationDef.builder(ClassTypeDef.of(annotationType));
        for (Map.Entry<CharSequence, Object> entry : annotation.getValues().entrySet()) {
            String memberName = entry.getKey().toString();
            ClassElement memberType = memberTypes.get(memberName);
            boolean copyMember = true;
            if (memberType == null) {
                // A mapper added the member: it is served by the annotation metadata, not the annotation type
                context.warn("The " + memberDescription(annotationName, memberName) + " is not declared by the annotation type"
                    + " and is not copied onto the generated Java declaration of [" + element.getName() + "]", element);
                copyMember = false;
            }
            Object value = null;
            if (copyMember) {
                try {
                    value = reflectiveMemberValue(annotationName, memberName, entry.getValue(), memberType, element, context);
                } catch (UnrepresentableAnnotationException e) {
                    if (!defaultedMembers.contains(memberName)) {
                        throw e;
                    }
                    context.warn("The " + memberDescription(annotationName, memberName) + " keeps its default on the generated Java declaration of ["
                        + element.getName() + "], reflection-based frameworks will not see its value: " + e.getMessage(), element);
                    copyMember = false;
                }
            }
            if (copyMember) {
                if (value instanceof Collection<?> collection) {
                    builder.addMember(memberName, new ArrayList<>(collection));
                } else {
                    builder.addMember(memberName, value);
                }
            }
        }
        return builder.build();
    }

    @SuppressWarnings("unchecked")
    private static Object reflectiveMemberValue(String annotationName, String memberName, Object value, ClassElement memberType, Element element, VisitorContext context) {
        if (memberType.isArray()) {
            ClassElement componentType = memberType.fromArray();
            List<Object> values = new ArrayList<>();
            for (Object elementValue : memberValues(value)) {
                values.add(reflectiveMemberValue(annotationName, memberName, elementValue, componentType, element, context));
            }
            return values;
        }
        if (value instanceof Collection<?> || (value != null && value.getClass().isArray())) {
            List<Object> values = memberValues(value);
            if (values.size() != 1) {
                throw unrepresentable(annotationName, memberName, value, memberType);
            }
            value = values.getFirst();
        }
        if (value instanceof AnnotationValue<?> nested) {
            if (!PythonAnnotationTypes.isAnnotationType(memberType)) {
                throw unrepresentable(annotationName, memberName, value, memberType);
            }
            return reflectiveAnnotationDef(nested, element, context);
        }
        String typeName = memberType.getName();
        if (String.class.getName().equals(typeName)) {
            if (value instanceof CharSequence) {
                return value.toString();
            }
            throw unrepresentable(annotationName, memberName, value, memberType);
        }
        if (memberType.isPrimitive()) {
            return primitiveMemberValue(annotationName, memberName, value, memberType);
        }
        if (Class.class.getName().equals(typeName)) {
            String className;
            if (value instanceof AnnotationClassValue<?> classValue) {
                className = classValue.getName();
            } else if (value instanceof CharSequence) {
                className = value.toString();
            } else {
                className = null;
            }
            if (className != null) {
                ClassElement type = context.getClassElement(className).orElse(null);
                if (type != null) {
                    return ClassTypeDef.of(type).getStaticField(CLASS_FIELD, TypeDef.of(Class.class));
                }
            }
            throw unrepresentable(annotationName, memberName, value, memberType);
        }
        if (memberType.isEnum()) {
            String constantName;
            if (value instanceof Enum<?> enumValue) {
                constantName = enumValue.name();
            } else if (value instanceof CharSequence) {
                constantName = value.toString();
            } else {
                constantName = null;
            }
            boolean known = constantName != null && (memberType instanceof EnumElement enumElement
                ? enumElement.values().contains(constantName)
                : javax.lang.model.SourceVersion.isIdentifier(constantName));
            if (!known) {
                throw unrepresentable(annotationName, memberName, value, memberType);
            }
            ClassTypeDef enumType = ClassTypeDef.of(memberType);
            return enumType.getStaticField(constantName, enumType);
        }
        throw unrepresentable(annotationName, memberName, value, memberType);
    }

    private static Object primitiveMemberValue(String annotationName, String memberName, Object value, ClassElement memberType) {
        String typeName = memberType.getName();
        if (value instanceof Boolean booleanValue && BOOLEAN_TYPE.equals(typeName)) {
            return booleanValue;
        }
        if ("char".equals(typeName)) {
            if (value instanceof Character character) {
                return character;
            }
            if (value instanceof CharSequence text && text.length() == 1) {
                return text.charAt(0);
            }
        } else if (value instanceof Number number) {
            switch (typeName) {
                case "int":
                    return number.intValue();
                case "long":
                    return number.longValue();
                case SHORT_TYPE:
                    return number.shortValue();
                case "byte":
                    return number.byteValue();
                case DOUBLE_TYPE:
                    return number.doubleValue();
                case FLOAT_TYPE:
                    return number.floatValue();
                default:
                    break;
            }
        }
        throw unrepresentable(annotationName, memberName, value, memberType);
    }

    private static List<Object> memberValues(Object value) {
        if (value instanceof Collection<?> collection) {
            return new ArrayList<>(collection);
        }
        if (value != null && value.getClass().isArray()) {
            int length = java.lang.reflect.Array.getLength(value);
            List<Object> values = new ArrayList<>(length);
            for (int i = 0; i < length; i++) {
                values.add(java.lang.reflect.Array.get(value, i));
            }
            return values;
        }
        List<Object> values = new ArrayList<>(1);
        values.add(value);
        return values;
    }

    private static UnrepresentableAnnotationException unrepresentable(String annotationName, String memberName, Object value, ClassElement memberType) {
        return new UnrepresentableAnnotationException("the value [" + value + "] of " + memberDescription(annotationName, memberName)
            + " cannot be written as a " + memberType.getName() + " constant");
    }

    private static String memberDescription(String annotationName, String memberName) {
        return "member '" + memberName + "' of @" + annotationName;
    }

    private boolean isCopiedRuntimeAnnotation(String annotationName, ElementType declaration, VisitorContext visitorContext) {
        return copiedRuntimeAnnotations.computeIfAbsent(
            annotationName + '#' + declaration,
            key -> isRuntimeAnnotationOf(annotationName, declaration, visitorContext)
        );
    }

    private static boolean isRuntimeAnnotationOf(String annotationName, ElementType declaration, VisitorContext visitorContext) {
        if (annotationName.startsWith(MICRONAUT_PACKAGE_PREFIX)) {
            return MICRONAUT_ANNOTATIONS_TO_COPY.contains(annotationName)
                || MICRONAUT_ANNOTATION_PACKAGES_TO_COPY.stream().anyMatch(annotationName::startsWith);
        }
        if (annotationName.startsWith(JAVA_LANG_PACKAGE_PREFIX)
            || TYPE_ANNOTATIONS_TO_SKIP_IN_SOURCE.contains(annotationName)
            || INJECTION_ANNOTATION_PACKAGE_PREFIXES.stream().anyMatch(annotationName::startsWith)) {
            return false;
        }
        ClassElement annotationType = visitorContext.getClassElement(annotationName).orElse(null);
        if (annotationType == null
            || annotationType instanceof AbstractPythonClassElement
            || !PythonAnnotationTypes.isAnnotationType(annotationType)) {
            return false;
        }
        return PythonAnnotationTypes.retentionPolicy(annotationType) == RetentionPolicy.RUNTIME
            && PythonAnnotationTypes.targetsDeclaration(annotationType, declaration);
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
        FieldDef pythonClassReference = pythonClassReferenceField("__PYTHON_CLASS_REFERENCE", classElement);
        EnumDef.EnumDefBuilder enumBuilder = EnumDef.builder(classElement.getName())
            .addModifiers(Modifier.PUBLIC)
            .addAnnotation(Vetoed.class)
            .addAnnotation(pythonClassAnnotation(classElement))
            .addSuperinterface(ClassTypeDef.of("io.micronaut.context.python.PooledValueCoercible"));
        enumBuilder.addField(pythonClassReference);
        copyRuntimeAnnotations(classElement, enumBuilder, ElementType.TYPE, context);
        List<String> enumConstants = classElement instanceof EnumElement enumElement ? enumElement.values() : List.of();
        for (String enumConstant : enumConstants) {
            enumBuilder.addEnumConstant(enumConstant);
        }
        enumBuilder.addMethod(MethodDef.builder(AS_POLYGLOT_VALUE)
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC)
            .returns(POLYGLOT_VALUE)
            .build((aThis, parameters) -> PYTHON_CONTEXT_RUNTIME.invokeStatic(
                ENUM_VALUE,
                POLYGLOT_VALUE,
                pythonClassReference(classElement, pythonClassReference),
                aThis.invoke("name", TypeDef.STRING)
            ).returning()));
        enumBuilder.addMethod(MethodDef.builder(AS_POLYGLOT_VALUE)
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC)
            .addParameter(POLYGLOT_CONTEXT)
            .returns(POLYGLOT_VALUE)
            .build((aThis, parameters) -> PYTHON_COERCION.invokeStatic(
                "coercePooledValue",
                POLYGLOT_VALUE,
                aThis,
                parameters.getFirst()
            ).returning()));
        enumBuilder.addMethod(MethodDef.builder(RECONSTRUCT_POLYGLOT_VALUE)
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC)
            .addParameter(POLYGLOT_CONTEXT)
            .returns(POLYGLOT_VALUE)
            .build((aThis, parameters) -> PYTHON_CONTEXT_RUNTIME.invokeStatic(
                ENUM_VALUE,
                POLYGLOT_VALUE,
                parameters.getFirst(),
                pythonClassReference(classElement, pythonClassReference),
                aThis.invoke("name", TypeDef.STRING)
            ).returning()));
        enumBuilder.addMethod(MethodDef.builder(FROM_POLYGLOT_VALUE)
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
            .addParameter(POLYGLOT_VALUE)
            .returns(thisType)
            .build((aThis, methodParameters) -> PYTHON_CONVERSION.invokeStatic(
                "convertValue",
                thisType,
                methodParameters.get(0),
                thisType.getStaticField(CLASS_FIELD, TypeDef.CLASS)
            ).returning()));
        Set<String> addedMethodNames = new LinkedHashSet<>();
        MethodElement jsonValueMethod = enumJsonValueMethod(classElement);
        List<MethodElement> enumMethods = classElement.getEnclosedElements(ElementQuery.ALL_METHODS.onlyInstance().onlyDeclared());
        addReferencedPythonClassReferenceFields(enumBuilder, classElement, enumMethods);
        for (MethodElement methodElement : enumMethods) {
            addBridgeMethod(BridgeMethodSpec.of(methodElement, classElement), enumBuilder, context, addedMethodNames);
        }
        if (jsonValueMethod != null && !"toString".equals(jsonValueMethod.getName()) && addedMethodNames.add("toString()")) {
            enumBuilder.addMethod(MethodDef.builder("toString")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .returns(TypeDef.STRING)
                .build((aThis, parameters) -> aThis.invoke(jsonValueMethod.getName(), TypeDef.STRING).returning()));
        } else if (jsonValueMethod == null && addedMethodNames.add("toString()")) {
            enumBuilder.addMethod(MethodDef.builder("jsonValue")
                .addAnnotation("com.fasterxml.jackson.annotation.JsonValue")
                .addModifiers(Modifier.PUBLIC)
                .returns(TypeDef.STRING)
                .build((aThis, parameters) -> aThis.invoke("name", TypeDef.STRING).returning()));
            enumBuilder.addMethod(MethodDef.builder("toString")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .returns(TypeDef.STRING)
                .build((aThis, parameters) -> aThis.invoke(AS_POLYGLOT_VALUE, POLYGLOT_VALUE)
                    .invoke(GET_MEMBER, POLYGLOT_VALUE, ExpressionDef.constant("value"))
                    .invoke("asString", TypeDef.STRING)
                    .returning()));
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
            ExpressionDef enumValue = PYTHON_CONVERSION.invokeStatic(
                "enumStringValue",
                TypeDef.STRING,
                PYTHON_CONTEXT_RUNTIME.invokeStatic(
                    ENUM_VALUE,
                    POLYGLOT_VALUE,
                    pythonClassReference(classElement, classElement),
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

    private void addBridgeMethod(BridgeMethodSpec spec, ObjectDefBuilder<?> builder, VisitorContext visitorContext, Set<String> addedMethodNames) {
        MethodElement methodElement = spec.method();
        ClassElement bridgeOwner = spec.owner();
        boolean isJunit5Test = spec.junit5Test();
        ClassElement returnTypeOverride = spec.returnTypeOverride();
        MethodElement signatureMethod = spec.signatureMethod();
        MethodElement resolvedSignatureMethod = spec.resolvedSignatureMethod();
        Map<String, ClassElement> signatureTypeArguments = spec.signatureTypeArguments();
        String pythonFunctionName = methodElement.getName();
        Map<String, ClassElement> inferredMethodBounds = inferMethodTypeBounds(signatureMethod, methodElement);
        Map<String, ClassElement> bridgeSignatureTypeArguments = withoutDeclaredMethodTypeVariables(signatureTypeArguments, signatureMethod);
        boolean genericToArray = "toArray".equals(signatureMethod.getName())
            && signatureMethod.getParameters().length == 1
            && (signatureMethod.getDeclaredTypeVariables().size() == 1 || resolvedSignatureMethod.getDeclaredTypeVariables().size() == 1);
        MethodElement sourceSignatureMethod = resolvedSignatureMethod.getDeclaredTypeVariables().size() == 1
            ? resolvedSignatureMethod
            : signatureMethod;
        List<TypeDef.TypeVariable> methodTypeVariables = methodTypeVariables(sourceSignatureMethod, bridgeSignatureTypeArguments, inferredMethodBounds);
        List<ParameterDef> parameterDefs = bridgeParameters(spec, sourceSignatureMethod, genericToArray, bridgeSignatureTypeArguments);
        // Duplicates are detected on the Java signature the stub emits, not on the Python method:
        // a Java interface may declare same-arity overloads (generate(Class<T>) and generate(T))
        // that Python, which has no overloading, implements with a single method. Each overload
        // needs its own bridge or javac rejects the stub as not implementing the interface. The
        // key is built from the emitted (resolved) parameter types rather than the declaring
        // method, so a generic interface method (handle(T) implemented for String) and a plain
        // interface method with the same erasure (handle(String)) are bridged once. The Python
        // method key is recorded too so the later declared-method pass does not bridge a method
        // again under its Python signature.
        String key = bridgeMethodKey(pythonFunctionName, parameterDefs, methodTypeVariables);
        String pythonKey = PYTHON_METHOD_KEY_PREFIX + bridgeMethodKey(methodElement);
        boolean declaredSignature = signatureMethod == methodElement;
        if (addedMethodNames.contains(key) || declaredSignature && addedMethodNames.contains(pythonKey)) {
            return;
        }

        addedMethodNames.add(key);
        addedMethodNames.add(pythonKey);

        if (isFactoryBeanMethod(bridgeOwner, methodElement.getAnnotationMetadata())) {
            if (isAsyncPythonMethod(methodElement)) {
                throw new ProcessingException(methodElement, "Factory methods declared with @Bean cannot be async.");
            }
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

            String preDestroy = methodElement.stringValue(Bean.class, MEMBER_PRE_DESTROY).orElse(null);
            if (preDestroy != null && genericReturnType instanceof AbstractPythonClassElement) {
                StubEntry stubEntry = this.classBuilders.get(genericReturnType.getName());
                if (stubEntry != null) {
                    MethodElement preDestroyMethod = stubEntry.originatingElement
                        .findMethod(preDestroy).orElse(null);
                    if (preDestroyMethod == null) {
                        throw new ProcessingException(methodElement, "Pre-destroy method referenced [" + preDestroy + "] not found in " + stubEntry.originatingElement.getName());
                    } else {
                        addBridgeMethod(BridgeMethodSpec.of(preDestroyMethod, preDestroyMethod.getOwningType()), stubEntry.builder, visitorContext, stubEntry.bridgedMethods);
                    }
                }
            }
        }

        ClassElement effectiveReturnType = effectiveBridgeReturnType(methodElement, returnTypeOverride);
        ClassElement declaredReturnType = signatureMethod == methodElement || returnTypeOverride != null
            ? null
            : resolvedSignatureMethod.getGenericReturnType();
        TypeDef methodSourceReturnType = genericToArray
            ? ClassTypeDef.of(sourceSignatureMethod.getDeclaredTypeVariables().getFirst().getVariableName()).array()
            : bridgeSourceReturnType(methodElement, signatureMethod, resolvedSignatureMethod, effectiveReturnType, returnTypeOverride, isJunit5Test, bridgeSignatureTypeArguments);
        boolean returnsMethodTypeVariable = !(methodElement instanceof PythonMethodElement) && !sourceSignatureMethod.getDeclaredTypeVariables().isEmpty();
        MethodDef.MethodDefBuilder methodBuilder = MethodDef.builder(pythonFunctionName)
            .returns(methodSourceReturnType);
        if (methodElement.isStatic()) {
            methodBuilder.addModifiers(Modifier.PUBLIC, Modifier.STATIC);
        } else {
            methodBuilder.addModifiers(Modifier.PUBLIC);
        }
        methodTypeVariables.forEach(methodBuilder::addTypeVariable);

        copyRuntimeAnnotations(methodElement, methodBuilder, ElementType.METHOD, visitorContext);
        if (isJunit5Test && !methodElement.hasDeclaredAnnotation(JUNIT_TEST)) {
            methodBuilder.addAnnotation(JUNIT_TEST);
        }
        parameterDefs.forEach(methodBuilder::addParameter);
        @NonNull ParameterElement[] parameters = methodElement.getParameters();
        int receiverOffset = parameters.length - parameterDefs.size();

        boolean spreadsVarargs = spreadsVarargs(methodElement, bridgeOwner);
        builder.addMethod(methodBuilder
            .build(((aThis, methodParameters) -> {
                List<ExpressionDef> parameterExpressions = new ArrayList<>();
                ExpressionDef invokedValue;
                boolean isAsyncMethod = isAsyncPythonMethod(methodElement);
                if (methodElement.isStatic()) {
                    List<ExpressionDef> arguments = new ArrayList<>();
                    ClassElement declaringType = methodElement.getDeclaringType();
                    arguments.add(pythonClassReference(bridgeOwner, declaringType));
                    arguments.add(ExpressionDef.constant(pythonFunctionName));
                    if (spreadsVarargs) {
                        // `*args`: the trailing Java array is spread into positional Python arguments
                        arguments.add(PYTHON_INVOCATION.invokeStatic(
                            WITH_VARARGS,
                            TypeDef.OBJECT.array(),
                            TypeDef.OBJECT.array().instantiate(methodParameters.subList(0, methodParameters.size() - 1)),
                            methodParameters.getLast()
                        ));
                    } else {
                        arguments.addAll(methodParameters);
                    }
                    invokedValue = PYTHON_CONTEXT_RUNTIME.invokeStatic(
                        "invokeStaticMethod",
                        POLYGLOT_VALUE,
                        arguments
                    );
                } else {
                    ExpressionDef targetValueExpression = aThis.invoke(AS_POLYGLOT_VALUE, POLYGLOT_VALUE);
                    ClassElement declaringType = methodElement.getDeclaringType();
                    if (isAsyncMethod && !declaringType.isAbstract()) {
                        targetValueExpression = PYTHON_CONTEXT_RUNTIME.invokeStatic(
                            "asyncInstance",
                            POLYGLOT_VALUE,
                            targetValueExpression,
                            pythonClassReference(bridgeOwner, declaringType)
                        );
                    }
                    var targetValue = targetValueExpression;
                    var targetContext = targetValue.invoke("getContext", POLYGLOT_CONTEXT);
                    if (receiverOffset == 1) {
                        parameterExpressions.add(aThis);
                    }
                    ExpressionDef variadicArguments = null;
                    for (int i = receiverOffset; i < parameters.length; i++) {
                        @NonNull ParameterElement parameter = parameters[i];
                        VariableDef.MethodParameter methodParameter = methodParameters.get(i - receiverOffset);
                        if (spreadsVarargs && i == parameters.length - 1) {
                            // `*args`: the trailing Java array is spread into positional Python arguments
                            List<ExpressionDef> variadic = new ArrayList<>(1);
                            coerceParameterToPolyglotValue(parameter, variadic, methodParameter, targetContext);
                            variadicArguments = variadic.getFirst();
                        } else {
                            coerceParameterToPolyglotValue(parameter, parameterExpressions, methodParameter, targetContext);
                        }
                    }
                    ExpressionDef pythonArguments = TypeDef.OBJECT.array().instantiate(parameterExpressions);
                    if (variadicArguments != null) {
                        pythonArguments = PYTHON_INVOCATION.invokeStatic(WITH_VARARGS, TypeDef.OBJECT.array(), pythonArguments, variadicArguments);
                    }
                    if (spec.introduced()) {
                        invokedValue = PYTHON_INVOCATION.invokeStatic(
                            "invokeIntroducedMethod",
                            POLYGLOT_VALUE,
                            targetValue,
                            ExpressionDef.constant(pythonFunctionName),
                            classLiteral(effectiveReturnType),
                            pythonArguments
                        );
                    } else {
                        invokedValue = PYTHON_INVOCATION.invokeStatic(
                            "invokePythonMethod",
                            POLYGLOT_VALUE,
                            targetValue,
                            ExpressionDef.constant(pythonFunctionName),
                            pythonArguments
                        );
                    }
                }

                if (isJunit5Test) {
                    return (StatementDef) invokedValue;
                } else {
                    if (effectiveReturnType.isVoid()) {
                        return (StatementDef) invokedValue;
                    } else if (isAsyncMethod) {
                        return invokedValue.newLocal("pythonCoroutine", pythonCoroutine ->
                            coroutineResult(pythonCoroutine, methodSourceReturnType, declaredReturnType).returning()
                        );
                    } else {
                        boolean bridgeSignature = signatureMethod != methodElement
                            || !bridgeSignatureTypeArguments.isEmpty()
                            || returnTypeOverride != null
                            || returnsMethodTypeVariable;
                        return returnConvertedValue(allClasses, effectiveReturnType, invokedValue, bridgeSignature ? methodSourceReturnType : null, declaredReturnType);
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
        if ("toArray".equals(signatureMethod.getName()) && signatureMethod.getParameters().length == 0) {
            return TypeDef.OBJECT.array();
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

    /**
     * The value a bridged {@code async def} returns: the coroutine as a {@link CompletionStage}, adapted
     * to the reactive type the Java signature declares when that is not a completion stage.
     */
    private static ExpressionDef coroutineResult(
        ExpressionDef pythonCoroutine,
        TypeDef methodSourceReturnType,
        @Nullable ClassElement declaredReturnType
    ) {
        ExpressionDef completionStage = PYTHON_ASYNCIO_RUNTIME.invokeStatic(
            "toCompletionStage",
            TypeDef.of(CompletionStage.class),
            pythonCoroutine
        ).cast(TypeDef.of(CompletionStage.class));
        if (declaredReturnType != null && isReactiveType(declaredReturnType) && !declaredReturnType.isAssignable(CompletionStage.class)) {
            return PYTHON_HTTP_CONVERSION.invokeStatic("convertReactive", TypeDef.OBJECT, completionStage, classLiteral(declaredReturnType))
                .cast(methodSourceReturnType);
        }
        return completionStage.cast(methodSourceReturnType);
    }

    private static ClassElement effectiveBridgeReturnType(MethodElement methodElement, @Nullable ClassElement returnTypeOverride) {
        ClassElement returnType = returnTypeOverride == null ? methodElement.getGenericReturnType() : returnTypeOverride;
        if (returnType instanceof GenericPlaceholderElement placeholder) {
            return resolvedOrFirstBound(placeholder);
        }
        return returnType;
    }

    static String bridgeMethodKey(MethodElement methodElement) {
        StringBuilder key = new StringBuilder(methodElement.getName()).append('(');
        for (ParameterElement parameter : methodElement.getParameters()) {
            ClassElement type = parameter.getType();
            key.append(type.getName());
            key.append("[]".repeat(type.getArrayDimensions()));
            key.append(';');
        }
        return key.append(')').toString();
    }

    /**
     * The parameters of a bridge method as the stub emits them: the Python parameters typed with the
     * (resolved) Java signature the bridge implements.
     */
    private static List<ParameterDef> bridgeParameters(
        BridgeMethodSpec spec,
        MethodElement sourceSignatureMethod,
        boolean genericToArray,
        Map<String, ClassElement> bridgeSignatureTypeArguments
    ) {
        MethodElement methodElement = spec.method();
        MethodElement signatureMethod = spec.signatureMethod();
        @NonNull ParameterElement[] parameters = methodElement.getParameters();
        @NonNull ParameterElement[] signatureParameters = signatureMethod.getParameters();
        @NonNull ParameterElement[] resolvedSignatureParameters = spec.resolvedSignatureMethod().getParameters();
        int receiverOffset = spec.script() && parameters.length > 0 && "self".equals(parameters[0].getName()) ? 1 : 0;
        List<ParameterDef> parameterDefs = new ArrayList<>(parameters.length);
        for (int i = receiverOffset; i < parameters.length; i++) {
            @NonNull ParameterElement parameter = parameters[i];
            ParameterElement signatureParameter = i < signatureParameters.length ? signatureParameters[i] : parameter;
            ParameterElement resolvedSignatureParameter = i < resolvedSignatureParameters.length ? resolvedSignatureParameters[i] : signatureParameter;
            TypeDef parameterType = genericToArray
                ? ClassTypeDef.of(sourceSignatureMethod.getDeclaredTypeVariables().getFirst().getVariableName()).array()
                : bridgeSourceParameterType(signatureMethod, signatureParameter, resolvedSignatureParameter, parameter, bridgeSignatureTypeArguments);
            parameterDefs.add(ParameterDef.builder(parameter.getName(), parameterType).build());
        }
        return parameterDefs;
    }

    /**
     * The key of a bridge method as javac sees it: the method name and the erasure of each emitted
     * parameter type, with method type variables erased to their first bound.
     */
    private static String bridgeMethodKey(String name, List<ParameterDef> parameters, List<TypeDef.TypeVariable> methodTypeVariables) {
        Map<String, TypeDef> bounds = new LinkedHashMap<>();
        for (TypeDef.TypeVariable typeVariable : methodTypeVariables) {
            bounds.put(typeVariable.name(), typeVariable.bounds().isEmpty() ? TypeDef.OBJECT : typeVariable.bounds().getFirst());
        }
        StringBuilder key = new StringBuilder(name).append('(');
        for (ParameterDef parameter : parameters) {
            key.append(erasedTypeName(parameter.getType(), bounds)).append(';');
        }
        return key.append(')').toString();
    }

    private static String erasedTypeName(TypeDef type, Map<String, TypeDef> methodTypeVariableBounds) {
        return switch (type) {
            case TypeDef.Array array -> erasedTypeName(array.componentType(), methodTypeVariableBounds) + "[]".repeat(array.dimensions());
            case TypeDef.TypeVariable typeVariable -> {
                TypeDef bound = methodTypeVariableBounds.get(typeVariable.name());
                if (bound == null) {
                    bound = typeVariable.bounds().isEmpty() ? TypeDef.OBJECT : typeVariable.bounds().getFirst();
                }
                yield bound instanceof TypeDef.TypeVariable ? Object.class.getName() : erasedTypeName(bound, methodTypeVariableBounds);
            }
            case TypeDef.Wildcard wildcard -> wildcard.upperBounds().isEmpty()
                ? Object.class.getName()
                : erasedTypeName(wildcard.upperBounds().getFirst(), methodTypeVariableBounds);
            case TypeDef.AnnotatedTypeDef annotated -> erasedTypeName(annotated.typeDef(), methodTypeVariableBounds);
            case ClassTypeDef.AnnotatedClassTypeDef annotated -> erasedTypeName(annotated.typeDef(), methodTypeVariableBounds);
            case TypeDef.Primitive primitive -> primitive.name();
            case ClassTypeDef classTypeDef -> classTypeDef.getName();
            default -> type.toString();
        };
    }

    /**
     * Whether the bridge spreads its trailing array parameter into positional Python arguments: the Python
     * implementation declares {@code *args}. When the bridged method is the Java interface method itself
     * (the Python type hints did not match its signature), the Python method is looked up by name and arity.
     */
    private static boolean spreadsVarargs(MethodElement methodElement, ClassElement owner) {
        ParameterElement[] parameters = methodElement.getParameters();
        if (parameters.length == 0 || !parameters[parameters.length - 1].getType().isArray()) {
            return false;
        }
        if (methodElement instanceof PythonMethodElement pythonMethod) {
            return pythonMethod.isVarArgs();
        }
        return owner.getEnclosedElements(ElementQuery.ALL_METHODS.onlyDeclared().onlyInstance().named(methodElement.getName()))
            .stream()
            .anyMatch(declared -> declared instanceof PythonMethodElement pythonMethod
                && pythonMethod.isVarArgs()
                && pythonMethod.getParameters().length == parameters.length);
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

    private static Optional<FieldElement> attributeField(PropertyElement beanProperty) {
        if (beanProperty instanceof PythonPropertyElement pythonProperty) {
            return pythonProperty.getAttributeField();
        }
        return beanProperty.getField();
    }

    /**
     * Whether a Java interface accessor is implemented by the getter or setter generated for an attribute of the same
     * name, so a dataclass can implement an interface such as {@code KubernetesObject} through its attributes
     * ({@code apiVersion} implements {@code getApiVersion()}). The Python class declares no method of that name, and
     * the attribute type must satisfy the interface signature for the generated accessor to override it.
     */
    private static boolean isImplementedByPropertyAccessor(MethodElement interfaceMethod, List<PropertyElement> beanProperties) {
        String methodName = interfaceMethod.getName();
        ParameterElement[] parameters = interfaceMethod.getParameters();
        for (PropertyElement beanProperty : beanProperties) {
            String propertyName = beanProperty.getName();
            if (parameters.length == 0 && !interfaceMethod.getReturnType().isVoid()) {
                boolean synthetic = beanProperty.getReadMethod().map(MethodElement::isSynthetic).orElse(true);
                boolean sameName = methodName.equals(beanGetterName(propertyName))
                    || (isBooleanProperty(beanProperty) && methodName.equals(booleanBeanGetterName(propertyName)));
                if (synthetic && sameName && !beanProperty.isWriteOnly()
                    && satisfiesReturnType(beanProperty.getGenericType(), interfaceMethod.getGenericReturnType())) {
                    return true;
                }
            } else if (parameters.length == 1 && interfaceMethod.getReturnType().isVoid()) {
                boolean synthetic = beanProperty.getWriteMethod().map(MethodElement::isSynthetic).orElse(true);
                if (synthetic && methodName.equals(beanSetterName(propertyName)) && !beanProperty.isReadOnly()
                    && parameters[0].getType().getName().equals(beanProperty.getType().getName())) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Whether a generated getter of the property type overrides the interface accessor: the type has to be
     * assignable, and where the accessor returns a parameterized type the type arguments have to match as well,
     * as javac requires (a {@code list[CustomObject]} attribute does not implement a getter returning a list of
     * {@code KubernetesObject}); a type variable or wildcard of the accessor accepts any argument.
     */
    private static boolean satisfiesReturnType(ClassElement propertyType, ClassElement returnType) {
        if (!propertyType.isAssignable(returnType)) {
            return false;
        }
        Map<String, ClassElement> expectedArguments = returnType.getTypeArguments();
        if (expectedArguments.isEmpty()) {
            return true;
        }
        Map<String, ClassElement> actualArguments = propertyType.getTypeArguments(returnType.getName());
        for (Map.Entry<String, ClassElement> expected : expectedArguments.entrySet()) {
            ClassElement expectedArgument = expected.getValue();
            if (expectedArgument instanceof GenericPlaceholderElement || expectedArgument instanceof WildcardElement) {
                continue;
            }
            ClassElement actualArgument = actualArguments.get(expected.getKey());
            if (actualArgument == null || !actualArgument.getName().equals(expectedArgument.getName())) {
                return false;
            }
        }
        return true;
    }

    private static boolean isDynamicBeanProperty(PropertyElement beanProperty) {
        return beanProperty.getReadMethod().filter(method -> !method.isSynthetic()).isPresent()
            || beanProperty.getWriteMethod().filter(method -> !method.isSynthetic()).isPresent();
    }

    /**
     * Whether the stub of a reconstructible bean (an {@code @Introspected} class whose state is fully
     * held by the generated property fields) can implement {@link Serializable}.
     *
     * <p>Java serialization writes the property fields only; the GraalPy value and the sync state are
     * transient, and {@code asPolyglotValue()} recreates the Python object from the fields on the first
     * use after deserialization. A Python superclass must be reconstructible as well, and a Java
     * superclass must itself be serializable, otherwise its state could not be written.</p>
     */
    private static boolean isSerializableStub(@Nullable ClassElement superType, boolean extendsPythonClass, boolean extendsHostClass) {
        if (superType == null || (!extendsPythonClass && !extendsHostClass)) {
            return true;
        }
        if (extendsHostClass) {
            return superType.isAssignable(Serializable.class);
        }
        return superType.hasStereotype(Introspected.class)
            && superType.getBeanProperties().stream().noneMatch(PythonStubGenerator::isDynamicBeanProperty)
            && isSerializableStub(
                superType.getSuperType().orElse(null),
                superType.getSuperType().map(AbstractPythonClassElement.class::isInstance).orElse(false),
                superType.getSuperType().map(type -> !(type instanceof AbstractPythonClassElement) && !type.isInterface() && !Object.class.getName().equals(type.getName())).orElse(false)
            );
    }

    private static boolean declaresInterface(Collection<ClassElement> interfaces, Class<?> interfaceType) {
        for (ClassElement anInterface : interfaces) {
            if (interfaceType.getName().equals(anInterface.getName())) {
                return true;
            }
        }
        return false;
    }

    private static boolean isConfigurationBuilderProperty(PropertyElement property) {
        return property.hasAnnotation(ANN_CONFIGURATION_BUILDER)
            || property.getReadMethod().map(method -> method.hasAnnotation(ANN_CONFIGURATION_BUILDER)).orElse(false)
            || property.getWriteMethod().map(method -> method.hasAnnotation(ANN_CONFIGURATION_BUILDER)).orElse(false);
    }

    /**
     * Whether the Python class declares a method with the name and arity of the interface method, whatever
     * its type hints: an unannotated {@code def greet(self, who)} overrides {@code greet(String)} too. The
     * interface signature is bridged for it and the Python method is looked up by name at run time.
     */
    private static boolean declaresOverride(ClassElement element, MethodElement interfaceMethod) {
        int arity = interfaceMethod.getParameters().length;
        return element.getEnclosedElements(ElementQuery.ALL_METHODS.onlyDeclared().onlyInstance().named(interfaceMethod.getName()))
            .stream()
            .anyMatch(declared -> declared.getParameters().length == arity
                || declared instanceof PythonMethodElement pythonMethod && pythonMethod.isVarArgs() && pythonMethod.getParameters().length <= arity);
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

    /**
     * The return type the bridge of a Python override converts the Python result to when the Python hint
     * names a different primitive or boxed type than the Java method ({@code -> int} for {@code long count()},
     * or no hint at all): the stub declares the Java type, so the conversion has to produce it.
     */
    private static @Nullable ClassElement resolveDeclaredBridgeReturnType(MethodElement declaredMethod, MethodElement interfaceMethod) {
        ClassElement interfaceReturnType = interfaceMethod.getGenericReturnType();
        if (interfaceReturnType instanceof GenericPlaceholderElement || interfaceReturnType.isVoid() || !PythonJavaTypes.isPrimitiveOrBoxedType(interfaceReturnType)) {
            return null;
        }
        ClassElement declaredReturnType = declaredMethod.getGenericReturnType();
        if (declaredReturnType.isVoid() || declaredReturnType.getName().equals(interfaceReturnType.getName())) {
            return null;
        }
        return interfaceReturnType;
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
        // Array return types (notably List.toArray() and List.toArray(T[]))
        // must retain their array shape. The interface type argument fallback
        // below is only valid for an erased scalar Object return type.
        if (genericReturnType.isArray()) {
            return null;
        }
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
            if (isMethodTypeVariable(placeholder) || isResolvedPlaceholder(placeholder)) {
                return null;
            }
            return typeArguments.get(placeholder.getVariableName());
        }
        if (genericReturnType.isVoid() || !Object.class.getName().equals(genericReturnType.getName())) {
            return null;
        }
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
            // A method type variable (<R> R findOne(...)) stays a variable, and a placeholder the
            // element model already resolved against its declaring type (the T of ArgumentBinder<T, S>
            // bound to Object through AnnotatedArgumentBinder<A, Object, S>) keeps that resolution; the
            // arguments of the implemented interface only answer for its own, unresolved variables.
            if (isMethodTypeVariable(placeholder) || isResolvedPlaceholder(placeholder)) {
                return null;
            }
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
            if (resolvedTypeArgument == null
                && typeArguments.size() == 1
                && interfaceTypeArguments.size() == 1
                && Object.class.getName().equals(typeArgument.getName())
                && !(typeArgument instanceof GenericPlaceholderElement placeholder && (isMethodTypeVariable(placeholder) || isResolvedPlaceholder(placeholder)))) {
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
        // Python has no overloading: a `def find(self, id: int)` overrides `find(Integer)` as well as
        // `find(ID)` resolved to Integer, so a primitive hint matches the boxed Java type.
        if (PythonJavaTypes.isSameOrBoxedType(parameter.getType(), interfaceParameter.getGenericType())) {
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

    private void addValueCoerciblePropertyMembers(
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
        builder.addMethod(MethodDef.builder("micronautValueCoerciblePutMember")
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC)
            .addParameter("key", TypeDef.STRING)
            .addParameter("value", POLYGLOT_VALUE)
            .returns(TypeDef.Primitive.BOOLEAN)
            .build((aThis, methodParameters) -> putMemberMatchBody(aThis, methodParameters.get(0), methodParameters.get(1), beanProperties, propertyFields)));
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
                        PYTHON_COERCION.invokeStatic(
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

    private StatementDef putMemberMatchBody(
        VariableDef.This aThis,
        VariableDef.MethodParameter key,
        VariableDef.MethodParameter value,
        List<PropertyElement> beanProperties,
        Map<String, FieldDef> propertyFields
    ) {
        List<StatementDef> statements = new ArrayList<>(beanProperties.size() + 1);
        for (PropertyElement beanProperty : beanProperties) {
            FieldDef field = propertyFields.get(beanProperty.getName());
            if (field == null) {
                continue;
            }
            statements.add(
                ExpressionDef.constant(beanProperty.getName())
                    .invoke("equals", TypeDef.Primitive.BOOLEAN, key)
                    .isTrue()
                    .doIf(StatementDef.multi(
                        aThis.field(field).assign(propertyFieldValue(beanProperty, value)),
                        ExpressionDef.trueValue().returning()
                    ))
            );
        }
        statements.add(ExpressionDef.falseValue().returning());
        return StatementDef.multi(statements);
    }

    private void addGetterPojo(PropertyElement beanProperty, ClassDef.ClassDefBuilder builder, FieldDef field, VisitorContext visitorContext) {
        TypeDef propertyType = propertySourceType(beanProperty);
        Optional<MethodElement> rm = beanProperty.getReadMethod();
        boolean isSynthetic = rm.map(MethodElement::isSynthetic).orElse(true);
        String getterName = isSynthetic ? beanGetterName(beanProperty.getName()) :
            rm.get().getName();
        addGetterPojo(beanProperty, builder, field, propertyType, getterName, visitorContext);

        String booleanGetterName = booleanBeanGetterName(beanProperty.getName());
        if (isBooleanProperty(beanProperty) && !booleanGetterName.equals(getterName)) {
            addGetterPojo(beanProperty, builder, field, propertyType, booleanGetterName, null);
        }
    }

    private void addGetterPojo(
        PropertyElement beanProperty,
        ClassDef.ClassDefBuilder builder,
        FieldDef field,
        TypeDef propertyType,
        String getterName,
        @Nullable VisitorContext visitorContext
    ) {
        MethodDef.MethodDefBuilder getterBuilder = MethodDef
            .builder(getterName)
            .addModifiers(Modifier.PUBLIC)
            .returns(propertyType);
        if (visitorContext != null) {
            copyAccessorAnnotations(beanProperty.getReadMethod(), getterBuilder, visitorContext);
        }

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
        return BOOLEAN_TYPE.equals(typeName) || Boolean.class.getName().equals(typeName);
    }

    private void addSetterPojo(PropertyElement beanProperty, ClassDef.ClassDefBuilder builder, FieldDef field, VisitorContext visitorContext) {
        TypeDef returnType = TypeDef.VOID;
        Optional<MethodElement> wm = beanProperty.getWriteMethod();
        boolean isSynthetic = wm.map(MethodElement::isSynthetic).orElse(true);
        String setterName = isSynthetic ? beanSetterName(beanProperty.getName()) :
                                            wm.get().getName();
        MethodDef.MethodDefBuilder propertySetter = MethodDef
            .builder(setterName)
            .addModifiers(Modifier.PUBLIC)
            .returns(returnType);
        copyAccessorAnnotations(wm, propertySetter, visitorContext);

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
                return uncheckedCast(PYTHON_CONVERSION.invokeStatic(
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
        addGetterDynamic(beanProperty, builder, propertyType, getterName, null);
    }

    private void addGetterDynamic(
        PropertyElement beanProperty,
        ClassDef.ClassDefBuilder builder,
        TypeDef propertyType,
        String getterName,
        @Nullable VisitorContext visitorContext
    ) {
        MethodDef.MethodDefBuilder getterBuilder = MethodDef
            .builder(getterName)
            .addModifiers(Modifier.PUBLIC)
            .returns(propertyType);
        if (visitorContext != null) {
            copyAccessorAnnotations(beanProperty.getReadMethod(), getterBuilder, visitorContext);
        }

        builder.addMethod(getterBuilder.build(((aThis, methodParameters) -> {
            var invokedValue = aThis.invoke(AS_POLYGLOT_VALUE, POLYGLOT_VALUE).invoke(
                GET_MEMBER,
                POLYGLOT_VALUE,
                ExpressionDef.constant(beanProperty.getName())
            );
            return returnConvertedValue(allClasses, beanProperty.getGenericType(), invokedValue);
        })));
    }

    private void addNamedGetterDynamic(PropertyElement beanProperty, ClassDef.ClassDefBuilder builder, boolean addBooleanAlias, VisitorContext visitorContext) {
        TypeDef propertyType = propertySourceType(beanProperty);
        String getterName = beanProperty.getReadMethod().map(MethodElement::getName).orElse(beanProperty.getName());
        addGetterDynamic(beanProperty, builder, propertyType, getterName, visitorContext);

        String booleanGetterName = booleanBeanGetterName(beanProperty.getName());
        if (addBooleanAlias && isBooleanProperty(beanProperty) && !booleanGetterName.equals(getterName)) {
            // the alias carries no annotations: one annotated getter per property (JPA property access)
            addGetterDynamic(beanProperty, builder, propertyType, booleanGetterName, null);
        }
    }

    private void addSetterDynamic(PropertyElement beanProperty, ClassDef.ClassDefBuilder builder, VisitorContext visitorContext, boolean adaptAsyncMembers) {
        TypeDef returnType = TypeDef.VOID;
        String setterName = beanSetterName(beanProperty.getName());
        MethodDef.MethodDefBuilder propertySetter = MethodDef
            .builder(setterName)
            .addModifiers(Modifier.PUBLIC)
            .returns(returnType);
        copyAccessorAnnotations(beanProperty.getWriteMethod(), propertySetter, visitorContext);

        propertySetter.addParameter(propertySourceType(beanProperty));

        builder.addMethod(propertySetter.build(((aThis, methodParameters) -> {
            var targetValue = aThis.invoke(AS_POLYGLOT_VALUE, POLYGLOT_VALUE);
            if (!adaptAsyncMembers) {
                return PYTHON_COERCION.invokeStatic(
                    "putMember",
                    TypeDef.VOID,
                    targetValue,
                    ExpressionDef.constant(beanProperty.getName()),
                    methodParameters.getFirst().cast(TypeDef.OBJECT)
                );
            }
            return StatementDef.multi(
                PYTHON_COERCION.invokeStatic(
                    "putMember",
                    TypeDef.VOID,
                    targetValue,
                    ExpressionDef.constant(beanProperty.getName()),
                    PYTHON_COERCION.invokeStatic(
                        "asyncMemberValue",
                        TypeDef.OBJECT,
                        targetValue,
                        methodParameters.getFirst().cast(TypeDef.OBJECT)
                    )
                ),
                PYTHON_CONTEXT_RUNTIME.invokeStatic(
                    "rememberAsyncMember",
                    TypeDef.VOID,
                    targetValue,
                    ExpressionDef.constant(beanProperty.getName()),
                    methodParameters.getFirst().cast(TypeDef.OBJECT)
                )
            );
        })));
    }

    private void addNamedSetterDynamic(PropertyElement beanProperty, ClassDef.ClassDefBuilder builder, VisitorContext visitorContext, boolean adaptAsyncMembers) {
        TypeDef returnType = TypeDef.VOID;
        String setterName = beanProperty.getWriteMethod().map(MethodElement::getName).orElse(beanProperty.getName());
        MethodDef.MethodDefBuilder propertySetter = MethodDef
            .builder(setterName)
            .addModifiers(Modifier.PUBLIC)
            .returns(returnType);
        copyAccessorAnnotations(beanProperty.getWriteMethod(), propertySetter, visitorContext);

        propertySetter.addParameter(propertySourceType(beanProperty));

        builder.addMethod(propertySetter.build(((aThis, methodParameters) -> {
            var targetValue = aThis.invoke(AS_POLYGLOT_VALUE, POLYGLOT_VALUE);
            if (!adaptAsyncMembers) {
                return PYTHON_COERCION.invokeStatic(
                    "putMember",
                    TypeDef.VOID,
                    targetValue,
                    ExpressionDef.constant(beanProperty.getName()),
                    methodParameters.getFirst().cast(TypeDef.OBJECT)
                );
            }
            return StatementDef.multi(
                PYTHON_COERCION.invokeStatic(
                    "putMember",
                    TypeDef.VOID,
                    targetValue,
                    ExpressionDef.constant(beanProperty.getName()),
                    PYTHON_COERCION.invokeStatic(
                        "asyncMemberValue",
                        TypeDef.OBJECT,
                        targetValue,
                        methodParameters.getFirst().cast(TypeDef.OBJECT)
                    )
                ),
                PYTHON_CONTEXT_RUNTIME.invokeStatic(
                    "rememberAsyncMember",
                    TypeDef.VOID,
                    targetValue,
                    ExpressionDef.constant(beanProperty.getName()),
                    methodParameters.getFirst().cast(TypeDef.OBJECT)
                )
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
                GET_MEMBER,
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
        return handleReturnType(allClasses, returnType, invokedValue, null);
    }

    /**
     * Converts the value a Python method returned to the method's Java return type.
     *
     * @param allClasses         The generated classes by name
     * @param returnType         The return type the Python method declares
     * @param invokedValue       The polyglot value the Python method returned
     * @param declaredReturnType The return type of the Java method the Python method implements, if
     *                           it differs from the Python declaration; a reactive Java declaration
     *                           ({@code Mono}, {@code Flux}, {@code CompletionStage}) adapts the Python
     *                           result to it
     * @return The conversion expression
     */
    static ExpressionDef handleReturnType(
        Map<String, ClassElement> allClasses,
        ClassElement returnType,
        ExpressionDef invokedValue,
        @Nullable ClassElement declaredReturnType
    ) {
        if (declaredReturnType != null && isReactiveType(declaredReturnType) && !sameErasure(declaredReturnType, returnType)) {
            if (Object.class.getName().equals(returnType.getName())) {
                // The Python method has no usable return annotation: the Java declaration decides
                return handleReturnType(allClasses, declaredReturnType, invokedValue, null);
            }
            // The bridge casts the result to the Java signature, so the erasure of the declaration suffices here
            if (returnType.isAssignable(PUBLISHER)) {
                return convertPublisher(allClasses, returnType, invokedValue, declaredReturnType, erasedType(declaredReturnType));
            }
            if (returnType.isAssignable(CompletionStage.class)) {
                return PYTHON_HTTP_CONVERSION.invokeStatic("convertReactive", TypeDef.OBJECT,
                        convertRuntimeValue(returnType, invokedValue), classLiteral(declaredReturnType))
                    .cast(erasedType(declaredReturnType));
            }
        }
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
                case JAVA_LANG_DOUBLE ->
                    convertNullableValue(invokedValue, invokedValue.invoke(AS_DOUBLE, TypeDef.Primitive.DOUBLE));
                case JAVA_LANG_FLOAT ->
                    convertNullableValue(invokedValue, invokedValue.invoke(AS_FLOAT, TypeDef.Primitive.FLOAT));
                case "java.lang.Long" ->
                    convertNullableValue(invokedValue, invokedValue.invoke("asLong", TypeDef.Primitive.LONG));
                case JAVA_LANG_SHORT ->
                    convertNullableValue(invokedValue, invokedValue.invoke(AS_SHORT, TypeDef.Primitive.SHORT));
                case "java.lang.Byte" ->
                    convertNullableValue(invokedValue, invokedValue.invoke("asByte", TypeDef.Primitive.BYTE));
                case "java.lang.Character" ->
                    convertNullableValue(invokedValue, invokedValue.invoke("asString", ClassTypeDef.STRING)
                        .invoke("charAt", TypeDef.Primitive.CHAR, ExpressionDef.constant(0)));
                case JAVA_LANG_STRING ->
                    convertNullableValue(invokedValue, invokedValue.invoke("asString", ClassTypeDef.STRING));
                case "java.lang.Object" ->
                    PYTHON_CONVERSION.invokeStatic("convertObject", ClassTypeDef.OBJECT, invokedValue);
                default -> {
                    // Check for collection types
                    if (returnType.isAssignable(List.class)) {
                        ClassElement componentType = returnType.getFirstTypeArgument().orElse(null);
                        if (componentType != null && isGeneratedWrapperType(allClasses, componentType)) {
                            yield uncheckedCast(PYTHON_CONVERSION.invokeStatic(
                                "convertList",
                                List.of(POLYGLOT_VALUE, POLYGLOT_VALUE_CONVERTER),
                                ClassTypeDef.of(List.class),
                                invokedValue,
                                generatedWrapperConverter(componentType)
                            ), returnType);
                        }
                        ExpressionDef genericType = toClassExpression(componentType);
                        yield uncheckedCast(PYTHON_CONVERSION.invokeStatic("convertList", ClassTypeDef.of(List.class),
                                invokedValue, genericType), returnType);
                    } else if (returnType.isAssignable(Map.class)) {
                        Map<String, ClassElement> typeArguments = returnType.getTypeArguments();
                        ExpressionDef keyType = toClassExpression(typeArguments.get("K"));
                        ExpressionDef valueType = toClassExpression(typeArguments.get("V"));
                        yield uncheckedCast(PYTHON_CONVERSION.invokeStatic("convertMap", ClassTypeDef.of(Map.class),
                                invokedValue, keyType, valueType), returnType);
                    } else if (returnType.isAssignable(Set.class)) {
                        ClassElement componentType = returnType.getFirstTypeArgument().orElse(null);
                        ExpressionDef genericType = toClassExpression(componentType);

                        yield uncheckedCast(PYTHON_CONVERSION.invokeStatic("convertSet", ClassTypeDef.of(Set.class),
                                invokedValue, genericType), returnType);
                    } else if (returnType.isAssignable(java.util.Optional.class)) {
                        ClassElement componentType = returnType.getFirstTypeArgument().orElse(null);
                        ExpressionDef genericType = toClassExpression(componentType);

                        yield uncheckedCast(PYTHON_CONVERSION.invokeStatic("convertOptional", ClassTypeDef.of(java.util.Optional.class),
                                invokedValue, genericType), returnType);
                    } else if (returnType.isAssignable(PUBLISHER)) {
                        yield convertPublisher(allClasses, returnType, invokedValue, returnType, sourceSignatureType(returnType));
                    } else if (returnType.isAssignable(CompletionStage.class)) {
                        // A publisher returned where a completion stage is declared (an unannotated
                        // method implementing a CompletableFuture signature returning a Mono) is adapted
                        yield PYTHON_HTTP_CONVERSION.invokeStatic("convertReactiveValue", TypeDef.OBJECT,
                                invokedValue, classLiteral(returnType))
                            .cast(sourceSignatureType(returnType));
                    } else if (returnType.isAssignable(HTTP_RESPONSE)) {
                        ClassElement bodyType = returnType.getFirstTypeArgument().orElse(null);
                        if (bodyType == null || Object.class.getName().equals(bodyType.getName())) {
                            yield PYTHON_HTTP_CONVERSION.invokeStatic("convertHttpResponse", ClassTypeDef.OBJECT,
                                    invokedValue, CLASS_OBJECT)
                                .cast(ClassTypeDef.of(returnType));
                        }
                        yield PYTHON_HTTP_CONVERSION.invokeStatic("convertHttpResponse", ClassTypeDef.OBJECT,
                                invokedValue, toClassExpression(bodyType))
                            .cast(ClassTypeDef.of(returnType));
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

    /**
     * Converts a Python-returned publisher: its items to the item type of {@code publisherType} and
     * the publisher itself to the erasure of {@code targetType} when that is a more specific reactive
     * type than {@code Publisher} (a Reactor {@code Mono}/{@code Flux}, a {@code CompletionStage}).
     *
     * @param allClasses    The generated classes by name
     * @param publisherType The publisher type the Python method declares
     * @param invokedValue  The polyglot value the Python method returned
     * @param targetType    The reactive type to adapt the publisher to
     * @param castType      The type the expression is cast to
     * @return The conversion expression
     */
    private static ExpressionDef convertPublisher(
        Map<String, ClassElement> allClasses,
        ClassElement publisherType,
        ExpressionDef invokedValue,
        ClassElement targetType,
        TypeDef castType
    ) {
        ClassElement componentType = publisherType.getFirstTypeArgument().orElse(null);
        ExpressionDef itemConversion = componentType != null && isGeneratedWrapperType(allClasses, componentType)
            ? generatedWrapperConverter(componentType)
            : toClassExpression(componentType);
        TypeDef itemConversionType = componentType != null && isGeneratedWrapperType(allClasses, componentType)
            ? POLYGLOT_VALUE_CONVERTER
            : TypeDef.CLASS;
        ExpressionDef converted;
        if (PUBLISHER.equals(targetType.getName())) {
            converted = PYTHON_HTTP_CONVERSION.invokeStatic(
                "convertPublisher",
                List.of(POLYGLOT_VALUE, itemConversionType),
                ClassTypeDef.of(PUBLISHER),
                invokedValue,
                itemConversion
            );
        } else {
            converted = PYTHON_HTTP_CONVERSION.invokeStatic(
                "convertPublisher",
                List.of(POLYGLOT_VALUE, itemConversionType, TypeDef.CLASS),
                TypeDef.OBJECT,
                invokedValue,
                itemConversion,
                classLiteral(targetType)
            );
        }
        return PYTHON_CONVERSION.invokeStatic(AS_OBJECT_METHOD, TypeDef.OBJECT, converted).cast(castType);
    }

    private static boolean isReactiveType(ClassElement type) {
        return type.isAssignable(PUBLISHER) || type.isAssignable(CompletionStage.class);
    }

    private static boolean sameErasure(ClassElement first, ClassElement second) {
        return first.getName().equals(second.getName());
    }

    static StatementDef returnConvertedValue(Map<String, ClassElement> allClasses, ClassElement returnType, ExpressionDef invokedValue) {
        return returnConvertedValue(allClasses, returnType, invokedValue, null, null);
    }

    private static StatementDef returnConvertedValue(
        Map<String, ClassElement> allClasses,
        ClassElement returnType,
        ExpressionDef invokedValue,
        @Nullable TypeDef castType,
        @Nullable ClassElement declaredReturnType
    ) {
        if (returnType.isVoid() || TypeDef.VOID.equals(castType) || TypeDef.Primitive.VOID.equals(castType)) {
            return (StatementDef) invokedValue;
        }
        return invokedValue.newLocal("pythonResult", result ->
            castReturnValue(handleReturnType(allClasses, returnType, result, declaredReturnType), castType).returning()
        );
    }

    private static ExpressionDef castReturnValue(ExpressionDef expression, @Nullable TypeDef castType) {
        if (castType == null) {
            return expression;
        }
        return PYTHON_CONVERSION.invokeStatic(AS_OBJECT_METHOD, castType, expression);
    }

    private static StatementDef fromPolyglotValueBody(ClassTypeDef thisType, VariableDef.MethodParameter value) {
        return StatementDef.multi(
            PYTHON_CONVERSION.invokeStatic("isNone", TypeDef.Primitive.BOOLEAN, value)
                .isTrue()
                .doIf(ExpressionDef.nullValue().returning()),
            thisType.instantiate(value).returning()
        );
    }

    private StatementDef initializeFromPolyglotValue(
        VariableDef.This aThis,
        ExpressionDef value,
        List<PropertyElement> beanProperties,
        Map<String, FieldDef> propertyFields,
        Map<String, FieldDef> syncSnapshotFields,
        @Nullable FieldDef pythonValueField,
        boolean extendsPythonClass
    ) {
        if (extendsPythonClass) {
            List<StatementDef> statements = new ArrayList<>();
            statements.add(aThis.superRef().invokeSuperConstructor(value));
            ExpressionDef storedValue = aThis.superRef().invoke(AS_POLYGLOT_VALUE, POLYGLOT_VALUE);
            if (pythonValueField != null) {
                statements.add(aThis.field(pythonValueField).assign(storedValue));
                storedValue = aThis.field(pythonValueField);
            }
            statements.addAll(polyglotValuePropertyAssignments(aThis, storedValue, beanProperties, propertyFields, syncSnapshotFields));
            return StatementDef.multi(statements);
        }
        return value.newLocal("pythonInstance", pythonInstance -> {
            List<StatementDef> statements = new ArrayList<>();
            if (pythonValueField != null) {
                statements.add(aThis.field(pythonValueField).assign(pythonInstance));
            }
            ExpressionDef storedValue = pythonValueField == null ? pythonInstance : aThis.field(pythonValueField);
            statements.addAll(polyglotValuePropertyAssignments(aThis, storedValue, beanProperties, propertyFields, syncSnapshotFields));
            return StatementDef.multi(statements);
        });
    }

    private StatementDef polyglotValuePropertyAssignment(VariableDef.This aThis,
                                                          ExpressionDef value,
                                                          PropertyElement beanProperty,
                                                          FieldDef field,
                                                          @Nullable FieldDef snapshot) {
        String propertyName = beanProperty.getName();
        ExpressionDef.InvokeInstanceMethod member = value.invoke(GET_MEMBER, POLYGLOT_VALUE, ExpressionDef.constant(propertyName));
        if (isCollectionLike(beanProperty.getGenericType())) {
            return aThis.field(field).assign(propertyFieldValue(beanProperty, member));
        }
        ExpressionDef.InvokeInstanceMethod has = value.invoke("hasMember", TypeDef.Primitive.BOOLEAN, ExpressionDef.constant(propertyName));
        // Read the member once into a local: the nullable conversions test and convert it separately.
        return has.isTrue().doIf(member.newLocal(MEMBER_LOCAL_PREFIX + propertyName, local -> {
            List<StatementDef> assignments = new ArrayList<>(2);
            assignments.add(aThis.field(field).assign(convertValueForType(beanProperty.getGenericType(), local)));
            if (snapshot != null) {
                assignments.add(aThis.field(snapshot).assign(aThis.field(field)));
            }
            return StatementDef.multi(assignments);
        }));
    }

    private List<StatementDef> polyglotValuePropertyAssignments(
        VariableDef.This aThis,
        ExpressionDef value,
        List<PropertyElement> beanProperties,
        Map<String, FieldDef> propertyFields,
        Map<String, FieldDef> syncSnapshotFields
    ) {
        List<StatementDef> statements = new ArrayList<>();
        for (PropertyElement beanProperty : beanProperties) {
            FieldDef field = propertyFields.get(beanProperty.getName());
            if (field != null) {
                statements.add(polyglotValuePropertyAssignment(aThis, value, beanProperty, field, syncSnapshotFields.get(beanProperty.getName())));
            }
        }
        return statements;
    }

    /**
     * The value of the generated field of a property read from the attribute of the Python object. A
     * list or dict of plain element types is viewed rather than copied: the Python collection stays the
     * attribute and the source of truth, and the Java field reads and writes through to it, so an
     * {@code append} in Python and an {@code add} in Java reach the same collection and the attribute
     * keeps its native Python type. Every other property value is converted.
     */
    private ExpressionDef propertyFieldValue(PropertyElement beanProperty, ExpressionDef member) {
        ClassElement type = beanProperty.getGenericType();
        if (isSharedCollectionProperty(beanProperty)) {
            if (type.isAssignable(List.class)) {
                return uncheckedCast(PYTHON_COERCION.invokeStatic("listView", ClassTypeDef.of(List.class), member, toClassExpression(type.getTypeArguments().get("E"))), type);
            }
            Map<String, ClassElement> typeArguments = type.getTypeArguments();
            return uncheckedCast(PYTHON_COERCION.invokeStatic("mapView", ClassTypeDef.of(Map.class), member,
                toClassExpression(typeArguments.get("K")), toClassExpression(typeArguments.get("V"))), type);
        }
        return convertValueForType(type, member);
    }

    /**
     * Writes the generated field of a property to the attribute of the Python object during a sync and
     * assigns the field the value to hold from then on. A viewed list or dict of this context is already
     * the attribute; a collection assigned from Java is copied into a native Python collection, which
     * the field views from then on.
     */
    private static StatementDef propertyWrite(VariableDef.This aThis, ExpressionDef target, PropertyElement beanProperty, FieldDef field) {
        ClassElement type = beanProperty.getGenericType();
        ExpressionDef fieldRef = aThis.field(field);
        ExpressionDef name = ExpressionDef.constant(beanProperty.getName());
        if (isSharedCollectionProperty(beanProperty)) {
            if (type.isAssignable(List.class)) {
                return aThis.field(field).assign(uncheckedCast(PYTHON_COERCION.invokeStatic("putListMember", ClassTypeDef.of(List.class),
                    target, name, fieldRef, toClassExpression(type.getTypeArguments().get("E"))), type));
            }
            Map<String, ClassElement> typeArguments = type.getTypeArguments();
            return aThis.field(field).assign(uncheckedCast(PYTHON_COERCION.invokeStatic("putMapMember", ClassTypeDef.of(Map.class),
                target, name, fieldRef, toClassExpression(typeArguments.get("K")), toClassExpression(typeArguments.get("V"))), type));
        }
        return PYTHON_COERCION.invokeStatic(
            "putMember",
            TypeDef.VOID,
            target,
            name,
            coerceTypedElementToPolyglotValue(beanProperty, fieldRef).cast(TypeDef.OBJECT)
        );
    }

    /**
     * Whether a list or dict property holds only plain values (strings, numbers, booleans, nested
     * lists and dicts of those), so the Java field can view the Python collection. A collection of
     * Python objects or of converted standard types is converted on every crossing instead.
     */
    private static boolean isSharedCollectionProperty(PropertyElement beanProperty) {
        return isSharedCollectionType(beanProperty.getGenericType());
    }

    private static boolean isSharedCollectionType(ClassElement type) {
        if (type.isAssignable(List.class)) {
            return isPlainElementType(type.getTypeArguments().get("E"));
        }
        if (type.isAssignable(Map.class)) {
            return isPlainElementType(type.getTypeArguments().get("K")) && isPlainElementType(type.getTypeArguments().get("V"));
        }
        return false;
    }

    private static boolean isPlainElementType(@Nullable ClassElement type) {
        if (type == null || type instanceof GenericPlaceholderElement || type.isArray()) {
            return false;
        }
        if (type.isPrimitive()) {
            return true;
        }
        return switch (type.getName()) {
            case JAVA_LANG_STRING, "java.lang.Boolean", "java.lang.Byte", JAVA_LANG_SHORT, "java.lang.Integer",
                 "java.lang.Long", JAVA_LANG_FLOAT, JAVA_LANG_DOUBLE, "java.lang.Character" -> true;
            default -> isSharedCollectionType(type);
        };
    }

    /**
     * One guest call that assigns every listed member, instead of one call per member.
     */
    private static StatementDef putMembers(ExpressionDef target, List<ExpressionDef> names, List<ExpressionDef> values) {
        return PYTHON_COERCION.invokeStatic(
            "putMembers",
            TypeDef.VOID,
            target,
            TypeDef.STRING.array().instantiate(names),
            TypeDef.OBJECT.array().instantiate(values)
        );
    }

    /**
     * Whether a property holds a value that cannot change in place, so an unchanged field reference
     * means the Python attribute is still current.
     */
    private static boolean isImmutablePropertyType(PropertyElement beanProperty) {
        ClassElement type = beanProperty.getGenericType();
        if (type.isArray()) {
            return false;
        }
        if (type.isPrimitive() || type.isEnum()) {
            return true;
        }
        return IMMUTABLE_PROPERTY_TYPES.contains(type.getName());
    }

    private static ExpressionDef convertNullableValue(ExpressionDef value, ExpressionDef nonNullValue) {
        return PYTHON_CONVERSION.invokeStatic("isNone", TypeDef.Primitive.BOOLEAN, value)
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
        return PYTHON_CONVERSION.invokeStatic(AS_OBJECT_METHOD, TypeDef.OBJECT, expression).cast(sourceSignatureType(targetType));
    }

    private static ExpressionDef convertRuntimeValue(ClassElement targetType, ExpressionDef value) {
        return PYTHON_CONVERSION.invokeStatic("convertValue", ClassTypeDef.OBJECT,
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

        // Add parameters. The implicit Python receiver (`cls`/`self`) is already
        // excluded from the resolved parameters, so every remaining one is a real argument.
        for (@NonNull ParameterElement parameter : creatorMethod.getParameters()) {
            var parameterType = TypeDef.of(parameter.getType());
            ParameterDef parameterDef = ParameterDef
                .builder(parameter.getName(), parameterType).build();
            factoryMethodBuilder.addParameter(parameterDef);
        }

        builder.addMethod(factoryMethodBuilder
            .build(((aThis, methodParameters) -> {
                // Call the Python static method via PYTHON_CONTEXT_RUNTIME
                List<ExpressionDef> arguments = new ArrayList<>();
                arguments.add(pythonClassReference(element, element));
                arguments.add(ExpressionDef.constant(pythonMethodName));

                // Add method parameters
                for (VariableDef.MethodParameter methodParam : methodParameters) {
                    arguments.add(methodParam);
                }

                // Call invokeStaticMethod and convert the result
                ExpressionDef pythonResult = PYTHON_CONTEXT_RUNTIME.invokeStatic(
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
            case BOOLEAN_TYPE, "java.lang.Boolean" ->
                invokedValue.invoke("asBoolean", TypeDef.Primitive.BOOLEAN);
            case DOUBLE_TYPE, JAVA_LANG_DOUBLE ->
                invokedValue.invoke(AS_DOUBLE, TypeDef.Primitive.DOUBLE);
            case FLOAT_TYPE, JAVA_LANG_FLOAT ->
                invokedValue.invoke(AS_FLOAT, TypeDef.Primitive.FLOAT);
            case "long", "java.lang.Long" ->
                invokedValue.invoke("asLong", TypeDef.Primitive.LONG);
            case SHORT_TYPE, JAVA_LANG_SHORT ->
                invokedValue.invoke(AS_SHORT, TypeDef.Primitive.SHORT);
            case "byte", "java.lang.Byte" ->
                invokedValue.invoke("asByte", TypeDef.Primitive.BYTE);
            case "char", "java.lang.Character" ->
                invokedValue.invoke("asString", ClassTypeDef.STRING)
                    .invoke("charAt", TypeDef.Primitive.CHAR, ExpressionDef.constant(0));
            default -> invokedValue.invoke("asString", ClassTypeDef.STRING);
        };
    }

    /**
     * The {@code graalpyInternalValue} field of a wrapper class; every wrapper that bridges to a
     * Python instance declares it, so a missing field is a generator bug.
     */
    private static FieldDef pythonValueField(ClassStubModel model) {
        return requireField(model.pythonValue(), "Expected graalpyInternalValue field");
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
                case BOOLEAN_TYPE -> member.invoke("asBoolean", TypeDef.Primitive.BOOLEAN);
                case DOUBLE_TYPE -> member.invoke(AS_DOUBLE, TypeDef.Primitive.DOUBLE);
                case FLOAT_TYPE -> member.invoke(AS_FLOAT, TypeDef.Primitive.FLOAT);
                case "long" -> member.invoke("asLong", TypeDef.Primitive.LONG);
                case SHORT_TYPE -> member.invoke(AS_SHORT, TypeDef.Primitive.SHORT);
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
                case JAVA_LANG_DOUBLE:
                    return convertNullableValue(member, member.invoke(AS_DOUBLE, TypeDef.Primitive.DOUBLE));
                case JAVA_LANG_FLOAT:
                    return convertNullableValue(member, member.invoke(AS_FLOAT, TypeDef.Primitive.FLOAT));
                case "java.lang.Long":
                    return convertNullableValue(member, member.invoke("asLong", TypeDef.Primitive.LONG));
                case JAVA_LANG_SHORT:
                    return convertNullableValue(member, member.invoke(AS_SHORT, TypeDef.Primitive.SHORT));
                case "java.lang.Byte":
                    return convertNullableValue(member, member.invoke("asByte", TypeDef.Primitive.BYTE));
                case "java.lang.Character":
                    return convertNullableValue(member, member.invoke("asString", ClassTypeDef.STRING).invoke("charAt", TypeDef.Primitive.CHAR, ExpressionDef.constant(0)));
                case JAVA_LANG_STRING:
                    return convertNullableValue(member, member.invoke("asString", ClassTypeDef.STRING));
                default:
                    if (type.isAssignable(List.class)) {
                        ClassElement componentType = type.getFirstTypeArgument().orElse(null);
                        if (componentType != null && isGeneratedWrapperType(allClasses, componentType)) {
                            return PYTHON_CONVERSION.invokeStatic(
                                "convertList",
                                List.of(POLYGLOT_VALUE, POLYGLOT_VALUE_CONVERTER),
                                ClassTypeDef.of(List.class),
                                member,
                                generatedWrapperConverter(componentType)
                            );
                        }
                        ExpressionDef genericType = toClassExpression(componentType);
                        return uncheckedCast(PYTHON_CONVERSION.invokeStatic("convertList", ClassTypeDef.of(List.class), member, genericType), type);
                    } else if (type.isAssignable(Map.class)) {
                        Map<String, ClassElement> typeArguments = type.getTypeArguments();
                        ExpressionDef keyType = toClassExpression(typeArguments.get("K"));
                        ExpressionDef valueType = toClassExpression(typeArguments.get("V"));
                        return uncheckedCast(PYTHON_CONVERSION.invokeStatic("convertMap", ClassTypeDef.of(Map.class), member, keyType, valueType), type);
                    } else if (type.isAssignable(Set.class)) {
                        ClassElement componentType = type.getFirstTypeArgument().orElse(null);
                        ExpressionDef genericType = toClassExpression(componentType);
                        return uncheckedCast(PYTHON_CONVERSION.invokeStatic("convertSet", ClassTypeDef.of(Set.class), member, genericType), type);
                    } else if (type.isAssignable(java.util.Optional.class)) {
                        ClassElement componentType = type.getFirstTypeArgument().orElse(null);
                        ExpressionDef genericType = toClassExpression(componentType);
                        return uncheckedCast(PYTHON_CONVERSION.invokeStatic("convertOptional", ClassTypeDef.of(java.util.Optional.class), member, genericType), type);
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

    /**
     * Whether a method carries a declared {@code @Bean} annotation or stereotype. Every such method is bridged:
     * a {@code @Factory} needs the bridge for its bean methods, including those it inherits from a base class
     * that is not itself a {@code @Factory}, as the bean definition of an inherited bean method calls the
     * bridge of the declaring stub. Visitors can also add {@code @Bean} directly to Python methods after
     * metadata parsing, and those methods need bridges so that the generated bean definitions can call them.
     * A plain bridge is harmless elsewhere.
     *
     * @param annotationMetadata The annotation metadata of the method
     * @return True if the method declares a {@code @Bean} annotation or stereotype
     */
    private static boolean isDeclaredBeanMethod(AnnotationMetadata annotationMetadata) {
        return annotationMetadata.hasDeclaredAnnotation(Bean.class)
            || annotationMetadata.hasDeclaredStereotype(Bean.class);
    }

    /**
     * Whether a method is a factory method producing a bean. Mirrors the core bean definition creators: a
     * method carrying a declared {@code @Bean} annotation or stereotype only produces a bean when the class
     * it is bridged for is a {@code @Factory}, or when a {@code @Factory} being compiled extends that class
     * and so inherits the method as one of its bean methods. Elsewhere the {@code @Bean} stereotype is
     * incidental, for example messaging listener annotations meta-annotated with {@code @MessageListener}
     * that are placed on methods of ordinary beans, and such methods are bridged as regular executable
     * methods without the factory method validation.
     *
     * @param owner              The class the method is bridged for
     * @param annotationMetadata The annotation metadata of the method
     * @return True if the method is a factory method
     */
    private boolean isFactoryBeanMethod(ClassElement owner, AnnotationMetadata annotationMetadata) {
        return isDeclaredBeanMethod(annotationMetadata)
            && (owner.hasStereotype(Factory.class) || hasFactorySubclass(owner));
    }

    /**
     * Whether a {@code @Factory} class among the compiled Python classes extends the given class. The bean
     * methods such a factory inherits are bridged once, on the stub of the declaring class, so that stub
     * has to apply the factory method validation and bridge the pre-destroy method on their behalf.
     *
     * @param owner The class declaring the method
     * @return True if a compiled {@code @Factory} extends the class
     */
    private boolean hasFactorySubclass(ClassElement owner) {
        String ownerName = owner.getName();
        for (ClassElement classElement : allClasses.values()) {
            if (!classElement.getName().equals(ownerName)
                && classElement.hasStereotype(Factory.class)
                && classElement.isAssignable(ownerName)) {
                return true;
            }
        }
        return false;
    }

    static boolean isAsyncPythonMethod(MethodElement methodElement) {
        return methodElement instanceof PythonMethodElement pythonMethodElement && pythonMethodElement.isAsync();
    }

    /**
     * Bridges a producer method of an associated bean into the stub of the Python class that declares it. The
     * child bean definition written for the method invokes it on the generated Java class, and a Python method
     * is only bridged into the stub when Micronaut needs to see it.
     *
     * @param producerMethod The producer method registered with {@code BeanElementBuilder.produceBeans(...)}
     * @param visitorContext The visitor context
     */
    public void bridgeProducerMethod(PythonMethodElement producerMethod, VisitorContext visitorContext) {
        String declaringTypeName = producerMethod.getDeclaringType().getName();
        StubEntry stubEntry = classBuilders.get(declaringTypeName);
        if (stubEntry != null) {
            addBridgeMethod(BridgeMethodSpec.of(producerMethod, stubEntry.originatingElement), stubEntry.builder, visitorContext, stubEntry.bridgedMethods);
        } else {
            // the declaring class is not compiled in this round (a base class of another module, for example):
            // its stub does not get the bridge, and the child bean definition cannot invoke the producer
            visitorContext.warn("Producer method [" + producerMethod.getName() + "] of associated bean ["
                + producerMethod.getOwningType().getName() + "] is declared by [" + declaringTypeName
                + "], which is not compiled in this round: the method is not bridged into the generated Java class", producerMethod);
        }
    }

    @Override
    public VisitorKind getVisitorKind() {
        return VisitorKind.ISOLATING;
    }

    /**
     * Thrown when an annotation value of the metadata cannot be written to the generated Java source.
     */
    private static final class UnrepresentableAnnotationException extends RuntimeException {
        UnrepresentableAnnotationException(String message) {
            super(message);
        }
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

    private record PythonClassReferenceDef(
        String packageName,
        String rootName,
        List<String> nestedMemberNames,
        String displayName,
        String cacheKey) {
    }

    /**
     * Everything {@link #visitClass} computes about a class before it emits members, shared by the
     * extracted emission steps.
     *
     * @param builder The class builder
     * @param element The class element
     * @param classElement The Python class element
     * @param context The visitor context
     * @param pythonVisitorContext The Python visitor context
     * @param typeName The generated type name
     * @param isAopProxy Whether the class is an AOP proxy target
     * @param pythonClassReference The Python class reference field
     * @param superType The super type, if any
     * @param extendsPythonClass Whether the super type is a Python class
     * @param extendsHostClass Whether the super type is a Java class
     * @param isIntrospectedBean Whether the class is introspected
     * @param isJunit5Test Whether the class is a JUnit 5 test
     * @param beanProperties The bean properties
     * @param hasDynamicBeanProperties Whether any property is a custom Python property
     * @param isReconstructibleBean Whether the bean can be rebuilt in another context
     * @param hasConfigurationBuilderProperty Whether a property is a configuration builder
     * @param propertyFields The generated property fields by name
     * @param syncSnapshotFields The sync snapshot fields by property name
     * @param pythonValue The field holding the Python value, if any
     * @param pythonValueSyncing The re-entrancy guard field for syncing, if any
     */
    private record ClassStubModel(
        ClassDef.ClassDefBuilder builder,
        ClassElement element,
        AbstractPythonClassElement classElement,
        VisitorContext context,
        PythonVisitorContext pythonVisitorContext,
        String typeName,
        boolean isAopProxy,
        FieldDef pythonClassReference,
        @Nullable ClassElement superType,
        boolean extendsPythonClass,
        boolean extendsHostClass,
        boolean isIntrospectedBean,
        boolean isJunit5Test,
        List<PropertyElement> beanProperties,
        boolean hasDynamicBeanProperties,
        boolean isReconstructibleBean,
        boolean hasConfigurationBuilderProperty,
        Map<String, FieldDef> propertyFields,
        Map<String, FieldDef> syncSnapshotFields,
        @Nullable FieldDef pythonValue,
        @Nullable FieldDef pythonValueSyncing
    ) {
    }

    /**
     * What a bridge method is generated from.
     *
     * @param method                  The Python method to bridge
     * @param owner                   The type the bridge is generated for
     * @param junit5Test              Whether the method is a JUnit 5 test
     * @param script                  Whether the method belongs to a module script
     * @param returnTypeOverride      A return type replacing the method's own, if any
     * @param signatureMethod         The method whose raw signature the bridge declares
     * @param resolvedSignatureMethod The signature method with type arguments resolved
     * @param signatureTypeArguments  Type arguments applied to the signature
     * @param introduced              Whether the method is introduced by an interface the proxy alone implements
     */
    record BridgeMethodSpec(
        MethodElement method,
        ClassElement owner,
        boolean junit5Test,
        boolean script,
        @Nullable ClassElement returnTypeOverride,
        MethodElement signatureMethod,
        MethodElement resolvedSignatureMethod,
        Map<String, ClassElement> signatureTypeArguments,
        boolean introduced
    ) {

        static BridgeMethodSpec of(MethodElement method, ClassElement owner) {
            return new BridgeMethodSpec(method, owner, false, false, null, method, method, Map.of(), false);
        }

        BridgeMethodSpec junit5Test(boolean value) {
            return new BridgeMethodSpec(method, owner, value, script, returnTypeOverride, signatureMethod, resolvedSignatureMethod, signatureTypeArguments, introduced);
        }

        BridgeMethodSpec script(boolean value) {
            return new BridgeMethodSpec(method, owner, junit5Test, value, returnTypeOverride, signatureMethod, resolvedSignatureMethod, signatureTypeArguments, introduced);
        }

        BridgeMethodSpec returnType(@Nullable ClassElement value) {
            return new BridgeMethodSpec(method, owner, junit5Test, script, value, signatureMethod, resolvedSignatureMethod, signatureTypeArguments, introduced);
        }

        BridgeMethodSpec signature(MethodElement rawSignature, MethodElement resolvedSignature, Map<String, ClassElement> typeArguments) {
            return new BridgeMethodSpec(method, owner, junit5Test, script, returnTypeOverride, rawSignature, resolvedSignature, typeArguments, introduced);
        }

        /**
         * @param value Whether the method is implemented by the introduction proxy alone, so that an instance
         *              without the Python member (one not created as the proxy) answers {@code null}
         * @return The spec
         */
        BridgeMethodSpec introduced(boolean value) {
            return new BridgeMethodSpec(method, owner, junit5Test, script, returnTypeOverride, signatureMethod, resolvedSignatureMethod, signatureTypeArguments, value);
        }
    }

    /**
     * The state fields a class stub declares.
     *
     * @param propertyFields     The generated property fields by name
     * @param syncSnapshotFields The sync snapshot fields by property name
     * @param pythonValue        The field holding the Python value, if any
     * @param pythonValueSyncing The re-entrancy guard field for syncing, if any
     */
    private record StateFields(Map<String, FieldDef> propertyFields, Map<String, FieldDef> syncSnapshotFields, @Nullable FieldDef pythonValue, @Nullable FieldDef pythonValueSyncing) {
    }

    /**
     * The methods bridged for a class stub.
     *
     * @param methodsToBridge      The bridged methods
     * @param hasAsyncBridgeMethod Whether any of them is an async Python method
     */
    private record BridgedMethods(List<MethodElement> methodsToBridge, boolean hasAsyncBridgeMethod) {
    }
}
