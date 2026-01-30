/*
 * Copyright 2017-2024 original authors
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
package io.micronaut.inject.writer;

import io.micronaut.aop.beandefinition.InterceptedDisposeBeanDefinition;
import io.micronaut.aop.beandefinition.InterceptedInitializingBeanDefinition;
import io.micronaut.aop.beandefinition.InterceptedInstantiateBeanDefinition;
import io.micronaut.aop.beandefinition.InterceptedParametrizedInstantiateBeanDefinition;
import io.micronaut.aop.beandefinition.ProxyInterceptedInstantiateBeanDefinition;
import io.micronaut.aop.beandefinition.ProxyInterceptedParametrizedInstantiateBeanDefinition;
import io.micronaut.context.AbstractInitializableBeanDefinition;
import io.micronaut.context.AbstractInitializableBeanDefinitionAndReference;
import io.micronaut.context.BeanContext;
import io.micronaut.context.BeanLocator;
import io.micronaut.context.BeanRegistration;
import io.micronaut.context.BeanResolutionContext;
import io.micronaut.context.DefaultReplacesDefinition;
import io.micronaut.context.LifeCycle;
import io.micronaut.context.Qualifier;
import io.micronaut.context.RequiresCondition;
import io.micronaut.context.annotation.Any;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.ConfigurationBuilder;
import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.ConfigurationReader;
import io.micronaut.context.annotation.Context;
import io.micronaut.context.annotation.DefaultImplementation;
import io.micronaut.context.annotation.DefaultScope;
import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.EachProperty;
import io.micronaut.context.annotation.Executable;
import io.micronaut.context.annotation.Infrastructure;
import io.micronaut.context.annotation.InjectScope;
import io.micronaut.context.annotation.Parallel;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.context.annotation.Primary;
import io.micronaut.context.annotation.Property;
import io.micronaut.context.annotation.PropertySource;
import io.micronaut.context.annotation.Replaces;
import io.micronaut.context.annotation.Requirements;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Type;
import io.micronaut.context.annotation.Value;
import io.micronaut.context.bean.definition.builder.BeanDefinitionInjectionPoint;
import io.micronaut.context.bean.definition.builder.BeanDefinitionInjectionPoint.BeanInjectionPoint;
import io.micronaut.context.bean.definition.builder.BeanDefinitionInjectionPoint.BeanRegistrationInjectionPoint;
import io.micronaut.context.bean.definition.builder.BeanDefinitionInjectionPoint.BeanRegistrationsInjectionPoint;
import io.micronaut.context.bean.definition.builder.BeanDefinitionInjectionPoint.BeansInjectionPoint;
import io.micronaut.context.bean.definition.builder.BeanDefinitionInjectionPoint.MapOfBeansInjectionPoint;
import io.micronaut.context.bean.definition.builder.BeanDefinitionInjectionPoint.OptionalBeanInjectionPoint;
import io.micronaut.context.bean.definition.builder.BeanDefinitionInjectionPoint.ParameterInjectionPoint;
import io.micronaut.context.bean.definition.builder.BeanDefinitionInjectionPoint.PropertyInjectionPoint;
import io.micronaut.context.bean.definition.builder.BeanDefinitionInjectionPoint.StreamOfBeansInjectionPoint;
import io.micronaut.context.bean.definition.builder.BeanDefinitionInjectionPoint.ValueInjectionPoint;
import io.micronaut.context.bean.definition.builder.ConstructorDefinition;
import io.micronaut.context.bean.definition.builder.FieldDefinition;
import io.micronaut.context.bean.definition.builder.MemberDefinition;
import io.micronaut.context.bean.definition.builder.MethodDefinition;
import io.micronaut.context.condition.Condition;
import io.micronaut.context.conditions.MatchesAbsenceOfBeansCondition;
import io.micronaut.context.conditions.MatchesAbsenceOfClassNamesCondition;
import io.micronaut.context.conditions.MatchesAbsenceOfClassesCondition;
import io.micronaut.context.conditions.MatchesConditionUtils;
import io.micronaut.context.conditions.MatchesConfigurationCondition;
import io.micronaut.context.conditions.MatchesCurrentNotOsCondition;
import io.micronaut.context.conditions.MatchesCurrentOsCondition;
import io.micronaut.context.conditions.MatchesCustomCondition;
import io.micronaut.context.conditions.MatchesDynamicCondition;
import io.micronaut.context.conditions.MatchesEnvironmentCondition;
import io.micronaut.context.conditions.MatchesMissingPropertyCondition;
import io.micronaut.context.conditions.MatchesNotEnvironmentCondition;
import io.micronaut.context.conditions.MatchesPresenceOfBeansCondition;
import io.micronaut.context.conditions.MatchesPresenceOfClassesCondition;
import io.micronaut.context.conditions.MatchesPresenceOfEntitiesCondition;
import io.micronaut.context.conditions.MatchesPresenceOfResourcesCondition;
import io.micronaut.context.conditions.MatchesPropertyCondition;
import io.micronaut.context.conditions.MatchesSdkCondition;
import io.micronaut.context.env.ConfigurationPath;
import io.micronaut.core.annotation.AccessorsStyle;
import io.micronaut.core.annotation.AnnotationClassValue;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationMetadataProvider;
import io.micronaut.core.annotation.AnnotationUtil;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.AnnotationValueBuilder;
import io.micronaut.core.annotation.Generated;
import io.micronaut.core.annotation.Indexed;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NextMajorVersion;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.convert.ConversionServiceProvider;
import io.micronaut.core.expressions.EvaluatedExpressionReference;
import io.micronaut.core.naming.Described;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.core.naming.Named;
import io.micronaut.core.order.OrderUtil;
import io.micronaut.core.order.Ordered;
import io.micronaut.core.reflect.ReflectionUtils;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.DefaultArgument;
import io.micronaut.core.type.TypeVariableResolver;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.core.util.Toggleable;
import io.micronaut.inject.AdvisedBeanType;
import io.micronaut.inject.BeanContextConditional;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.BeanDefinitionReference;
import io.micronaut.inject.DisposableBeanDefinition;
import io.micronaut.inject.ElementBeanDefinitionBuilder;
import io.micronaut.inject.ExecutableMethodsDefinition;
import io.micronaut.inject.InitializingBeanDefinition;
import io.micronaut.inject.InjectableBeanDefinition;
import io.micronaut.inject.InjectionPoint;
import io.micronaut.inject.InstantiatableBeanDefinition;
import io.micronaut.inject.OutputObjectDef;
import io.micronaut.inject.ParametrizedInstantiatableBeanDefinition;
import io.micronaut.inject.ProxyBeanDefinition;
import io.micronaut.inject.ReplacesDefinition;
import io.micronaut.inject.ValidatedBeanDefinition;
import io.micronaut.inject.annotation.AbstractAnnotationMetadataBuilder;
import io.micronaut.inject.annotation.AnnotationMetadataGenUtils;
import io.micronaut.inject.annotation.AnnotationMetadataHierarchy;
import io.micronaut.inject.annotation.AnnotationMetadataReference;
import io.micronaut.inject.annotation.MutableAnnotationMetadata;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.Element;
import io.micronaut.inject.ast.ElementQuery;
import io.micronaut.inject.ast.FieldElement;
import io.micronaut.inject.ast.MemberElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.ParameterElement;
import io.micronaut.inject.ast.PropertyElement;
import io.micronaut.inject.ast.TypedElement;
import io.micronaut.inject.ast.beans.BeanElement;
import io.micronaut.inject.ast.beans.BeanElementBuilder;
import io.micronaut.inject.processing.ProcessingException;
import io.micronaut.inject.qualifiers.AnyQualifier;
import io.micronaut.inject.qualifiers.Qualifiers;
import io.micronaut.inject.utils.BeanInjectionUtils;
import io.micronaut.inject.validation.RequiresValidation;
import io.micronaut.inject.visitor.BeanElementVisitor;
import io.micronaut.inject.visitor.BeanElementVisitorContext;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.sourcegen.model.AnnotationDef;
import io.micronaut.sourcegen.model.ClassDef;
import io.micronaut.sourcegen.model.ClassTypeDef;
import io.micronaut.sourcegen.model.ExpressionDef;
import io.micronaut.sourcegen.model.FieldDef;
import io.micronaut.sourcegen.model.MethodDef;
import io.micronaut.sourcegen.model.StatementDef;
import io.micronaut.sourcegen.model.TypeDef;
import io.micronaut.sourcegen.model.VariableDef;
import jakarta.inject.Singleton;
import org.jspecify.annotations.NullUnmarked;
import org.jspecify.annotations.Nullable;

import javax.lang.model.element.Modifier;
import java.io.Closeable;
import java.io.Serializable;
import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.IntStream;

import static io.micronaut.core.util.StringUtils.EMPTY_STRING_ARRAY;
import static io.micronaut.inject.visitor.BeanElementVisitor.VISITORS;

/**
 * <p>Responsible for building {@link BeanDefinition} instances at compile time. Uses ASM build the class definition.</p>
 *
 * <p>Should be used from AST frameworks to build bean definitions from source code data.</p>
 *
 * <p>For example:</p>
 *
 * <pre>
 *     {@code
 *
 *          BeanDefinitionWriter writer = new BeanDefinitionWriter("my.package", "MyClass", "jakarta.inject.Singleton", true)
 *          writer.visitBeanDefinitionConstructor()
 *          writer.visitFieldInjectionPoint("my.Qualifier", false, "my.package.MyDependency", "myfield" )
 *          writer.visitBeanDefinitionEnd()
 *          writer.writeTo(new File(..))
 *     }
 * </pre>
 *
 * @author Graeme Rocher
 * @author Denis Stepanov
 * @see BeanDefinition
 * @since 1.0
 */
@NullUnmarked
@Internal
public final class BeanDefinitionWriter implements BeanElement, Toggleable, ElementBeanDefinitionBuilder<OutputObjectDef> {

    /**
     * The suffix use for generated AOP intercepted types.
     */
    public static final String PROXY_SUFFIX = BeanDefinitionVisitor.PROXY_SUFFIX;

    @NextMajorVersion("Inline as true")
    public static final String OMIT_CONFPROP_INJECTION_POINTS = "micronaut.processing.omit.confprop.injectpoints";

    public static final String CLASS_SUFFIX = "$Definition";

    private static final String BUILDER_VARIABLE_PREFIX = "builder";
    private static final String ARGUMENT_MEMBER = "argument";

    private static final Method POST_CONSTRUCT_METHOD = ReflectionUtils.getRequiredInternalMethod(AbstractInitializableBeanDefinition.class, "postConstruct", BeanResolutionContext.class, BeanContext.class, Object.class);

    private static final Method INJECT_BEAN_METHOD =
        ReflectionUtils.getRequiredInternalMethod(InjectableBeanDefinition.class, "inject", BeanResolutionContext.class, BeanContext.class, Object.class);

    private static final Method PRE_DESTROY_METHOD = ReflectionUtils.getRequiredInternalMethod(AbstractInitializableBeanDefinition.class, "preDestroy", BeanResolutionContext.class, BeanContext.class, Object.class);

    private static final Method GET_BEAN_FOR_CONSTRUCTOR_ARGUMENT = getBeanLookupMethod("getBeanForConstructorArgument", false);

    private static final Method GET_BEAN_REGISTRATIONS_FOR_CONSTRUCTOR_ARGUMENT = getBeanLookupMethod("getBeanRegistrationsForConstructorArgument", true);

    private static final Method GET_BEAN_REGISTRATION_FOR_CONSTRUCTOR_ARGUMENT = getBeanLookupMethod("getBeanRegistrationForConstructorArgument", true);

    private static final Method GET_BEANS_OF_TYPE_FOR_CONSTRUCTOR_ARGUMENT = getBeanLookupMethod("getBeansOfTypeForConstructorArgument", true);

    private static final Method GET_STREAM_OF_TYPE_FOR_CONSTRUCTOR_ARGUMENT = getBeanLookupMethod("getStreamOfTypeForConstructorArgument", true);

    private static final Method GET_MAP_OF_TYPE_FOR_CONSTRUCTOR_ARGUMENT = getBeanLookupMethod("getMapOfTypeForConstructorArgument", true);

    private static final Method FIND_BEAN_FOR_CONSTRUCTOR_ARGUMENT = getBeanLookupMethod("findBeanForConstructorArgument", true);

    private static final Method GET_BEAN_FOR_FIELD = getBeanLookupMethod("getBeanForField", false);

    private static final Method GET_BEAN_FOR_ANNOTATION = getBeanLookupMethod("getBeanForAnnotation", false);

    private static final Method GET_BEAN_REGISTRATIONS_FOR_FIELD = getBeanLookupMethod("getBeanRegistrationsForField", true);

    private static final Method GET_BEAN_REGISTRATION_FOR_FIELD = getBeanLookupMethod("getBeanRegistrationForField", true);

    private static final Method GET_BEANS_OF_TYPE_FOR_FIELD = getBeanLookupMethod("getBeansOfTypeForField", true);

    private static final Method GET_VALUE_FOR_FIELD = getBeanLookupMethod("getValueForField", false);

    private static final Method GET_STREAM_OF_TYPE_FOR_FIELD = getBeanLookupMethod("getStreamOfTypeForField", true);

    private static final Method GET_MAP_OF_TYPE_FOR_FIELD = getBeanLookupMethod("getMapOfTypeForField", true);

    private static final Method FIND_BEAN_FOR_FIELD = getBeanLookupMethod("findBeanForField", true);

    private static final Method GET_VALUE_FOR_PATH = ReflectionUtils.getRequiredInternalMethod(AbstractInitializableBeanDefinition.class, "getValueForPath", BeanResolutionContext.class, BeanContext.class, Argument.class, String.class);

    private static final Method CONTAINS_PROPERTIES_METHOD = ReflectionUtils.getRequiredInternalMethod(AbstractInitializableBeanDefinition.class, "containsProperties", BeanResolutionContext.class, BeanContext.class);

    private static final Method GET_BEAN_FOR_METHOD_ARGUMENT = getBeanLookupMethodForArgument("getBeanForMethodArgument", false);

    private static final Method GET_BEAN_REGISTRATIONS_FOR_METHOD_ARGUMENT = getBeanLookupMethodForArgument("getBeanRegistrationsForMethodArgument", true);

    private static final Method GET_BEAN_REGISTRATION_FOR_METHOD_ARGUMENT = getBeanLookupMethodForArgument("getBeanRegistrationForMethodArgument", true);

    private static final Method GET_BEANS_OF_TYPE_FOR_METHOD_ARGUMENT = getBeanLookupMethodForArgument("getBeansOfTypeForMethodArgument", true);

    private static final Method GET_STREAM_OF_TYPE_FOR_METHOD_ARGUMENT = getBeanLookupMethodForArgument("getStreamOfTypeForMethodArgument", true);

    private static final Method GET_MAP_OF_TYPE_FOR_METHOD_ARGUMENT = getBeanLookupMethodForArgument("getMapOfTypeForMethodArgument", true);

    private static final Method FIND_BEAN_FOR_METHOD_ARGUMENT = getBeanLookupMethodForArgument("findBeanForMethodArgument", true);

    private static final Method CHECK_INJECTED_BEAN_PROPERTY_VALUE = ReflectionUtils.getRequiredInternalMethod(
        AbstractInitializableBeanDefinition.class,
        "checkInjectedBeanPropertyValue",
        String.class,
        Object.class,
        String.class,
        String.class);

    private static final Method GET_PROPERTY_VALUE_FOR_METHOD_ARGUMENT = ReflectionUtils.getRequiredInternalMethod(
        AbstractInitializableBeanDefinition.class,
        "getPropertyValueForMethodArgument",
        BeanResolutionContext.class,
        BeanContext.class,
        int.class,
        int.class,
        String.class,
        String.class);

    private static final Method GET_PROPERTY_PLACEHOLDER_VALUE_FOR_METHOD_ARGUMENT = ReflectionUtils.getRequiredInternalMethod(
        AbstractInitializableBeanDefinition.class,
        "getPropertyPlaceholderValueForMethodArgument",
        BeanResolutionContext.class,
        BeanContext.class,
        int.class,
        int.class,
        String.class);

    private static final Method GET_EVALUATED_EXPRESSION_VALUE_FOR_METHOD_ARGUMENT = ReflectionUtils.getRequiredInternalMethod(
        AbstractInitializableBeanDefinition.class,
        "getEvaluatedExpressionValueForMethodArgument",
        int.class,
        int.class);

    private static final Method GET_BEAN_FOR_SETTER = ReflectionUtils.getRequiredInternalMethod(
        AbstractInitializableBeanDefinition.class,
        "getBeanForSetter",
        BeanResolutionContext.class,
        BeanContext.class,
        String.class,
        Argument.class,
        Qualifier.class);

    private static final Method GET_BEANS_OF_TYPE_FOR_SETTER = ReflectionUtils.getRequiredInternalMethod(
        AbstractInitializableBeanDefinition.class,
        "getBeansOfTypeForSetter",
        BeanResolutionContext.class,
        BeanContext.class,
        String.class,
        Argument.class,
        Argument.class,
        Qualifier.class);

    private static final Method GET_PROPERTY_VALUE_FOR_SETTER = ReflectionUtils.getRequiredInternalMethod(
        AbstractInitializableBeanDefinition.class,
        "getPropertyValueForSetter",
        BeanResolutionContext.class,
        BeanContext.class,
        String.class,
        Argument.class,
        String.class,
        String.class);

    private static final Method GET_PROPERTY_PLACEHOLDER_VALUE_FOR_SETTER = ReflectionUtils.getRequiredInternalMethod(
        AbstractInitializableBeanDefinition.class,
        "getPropertyPlaceholderValueForSetter",
        BeanResolutionContext.class,
        BeanContext.class,
        String.class,
        Argument.class,
        String.class);

    private static final Method GET_PROPERTY_VALUE_FOR_CONSTRUCTOR_ARGUMENT = ReflectionUtils.getRequiredInternalMethod(
        AbstractInitializableBeanDefinition.class,
        "getPropertyValueForConstructorArgument",
        BeanResolutionContext.class,
        BeanContext.class,
        int.class,
        String.class,
        String.class);

    private static final Method GET_PROPERTY_PLACEHOLDER_VALUE_FOR_CONSTRUCTOR_ARGUMENT = ReflectionUtils.getRequiredInternalMethod(
        AbstractInitializableBeanDefinition.class,
        "getPropertyPlaceholderValueForConstructorArgument",
        BeanResolutionContext.class,
        BeanContext.class,
        int.class,
        String.class);

    private static final Method GET_EVALUATED_EXPRESSION_VALUE_FOR_CONSTRUCTOR_ARGUMENT = ReflectionUtils.getRequiredInternalMethod(
        AbstractInitializableBeanDefinition.class,
        "getEvaluatedExpressionValueForConstructorArgument",
        int.class);

    private static final Method GET_PROPERTY_VALUE_FOR_FIELD = ReflectionUtils.getRequiredInternalMethod(
        AbstractInitializableBeanDefinition.class,
        "getPropertyValueForField",
        BeanResolutionContext.class,
        BeanContext.class,
        Argument.class,
        String.class,
        String.class);

    private static final Method GET_PROPERTY_PLACEHOLDER_VALUE_FOR_FIELD = ReflectionUtils.getRequiredInternalMethod(
        AbstractInitializableBeanDefinition.class,
        "getPropertyPlaceholderValueForField",
        BeanResolutionContext.class,
        BeanContext.class,
        Argument.class,
        String.class);

    private static final Method CONTAINS_PROPERTIES_VALUE_METHOD = ReflectionUtils.getRequiredInternalMethod(
        AbstractInitializableBeanDefinition.class,
        "containsPropertiesValue",
        BeanResolutionContext.class,
        BeanContext.class,
        String.class);

    private static final Method CONTAINS_PROPERTY_VALUE_METHOD = ReflectionUtils.getRequiredInternalMethod(
        AbstractInitializableBeanDefinition.class,
        "containsPropertyValue",
        BeanResolutionContext.class,
        BeanContext.class,
        String.class);

    private static final ClassTypeDef TYPE_ABSTRACT_BEAN_DEFINITION_AND_REFERENCE = ClassTypeDef.of(AbstractInitializableBeanDefinitionAndReference.class);

    private static final Method METHOD_OPTIONAL_EMPTY = ReflectionUtils.getRequiredMethod(Optional.class, "empty");
    private static final ClassTypeDef TYPE_OPTIONAL = ClassTypeDef.of(Optional.class);
    private static final Method METHOD_OPTIONAL_OF = ReflectionUtils.getRequiredMethod(Optional.class, "of", Object.class);

    private static final Method BEAN_LOCATOR_METHOD_GET_BEAN = ReflectionUtils.getRequiredInternalMethod(BeanLocator.class, "getBean", Class.class, Qualifier.class);
    private static final Method COLLECTION_TO_ARRAY = ReflectionUtils.getRequiredInternalMethod(Collection.class, "toArray", Object[].class);

    private static final Method SET_FIELD_WITH_REFLECTION_METHOD =
        ReflectionUtils.getRequiredMethod(AbstractInitializableBeanDefinition.class, "setFieldWithReflection", BeanResolutionContext.class, BeanContext.class, int.class, Object.class, Object.class);

    private static final Method INVOKE_WITH_REFLECTION_METHOD =
        ReflectionUtils.getRequiredMethod(AbstractInitializableBeanDefinition.class, "invokeMethodWithReflection", BeanResolutionContext.class, BeanContext.class, int.class, Object.class, Object[].class);

    private static final Method IS_METHOD_RESOLVED =
        ReflectionUtils.getRequiredMethod(AbstractInitializableBeanDefinition.class, "isMethodResolved", int.class, Object[].class);

    private static final ClassTypeDef TYPE_REFLECTION_UTILS = ClassTypeDef.of(ReflectionUtils.class);

    private static final Method GET_FIELD_WITH_REFLECTION_METHOD =
        ReflectionUtils.getRequiredInternalMethod(ReflectionUtils.class, "getField", Class.class, String.class, Object.class);

    private static final Method METHOD_INVOKE_INACCESSIBLE_METHOD =
        ReflectionUtils.getRequiredInternalMethod(ReflectionUtils.class, "invokeInaccessibleMethod", Object.class, Method.class, Object[].class);

    private static final Method METHOD_GET_DEFAULT_IMPLEMENTATION =
        ReflectionUtils.getRequiredInternalMethod(BeanDefinition.class, "getDefaultImplementation");

    private static final Method METHOD_IS_CAN_BE_REPLACED =
        ReflectionUtils.getRequiredInternalMethod(BeanDefinition.class, "isCanBeReplaced");

    private static final Method METHOD_GET_REPLACES_DEFINITION =
        ReflectionUtils.getRequiredInternalMethod(BeanDefinition.class, "getReplacesDefinition");

    private static final Constructor<?> CONSTRUCTOR_DEFAULT_REPLACES_DEFINITION =
        ReflectionUtils.getRequiredInternalConstructor(DefaultReplacesDefinition.class, Class.class, Class.class, Qualifier.class, Class.class);

    private static final Optional<Constructor<AbstractInitializableBeanDefinitionAndReference>> BEAN_DEFINITION_CLASS_CONSTRUCTOR1 = ReflectionUtils.findConstructor(
        AbstractInitializableBeanDefinitionAndReference.class,
        Class.class, // beanType
        AbstractInitializableBeanDefinition.MethodOrFieldReference.class, // constructor
        AnnotationMetadata.class, // annotationMetadata
        AbstractInitializableBeanDefinition.MethodReference[].class, // methodInjection
        AbstractInitializableBeanDefinition.FieldReference[].class, // fieldInjection
        AbstractInitializableBeanDefinition.AnnotationReference[].class, // annotationInjection
        ExecutableMethodsDefinition.class, // executableMethodsDefinition
        Map.class, // typeArgumentsMap
        AbstractInitializableBeanDefinition.PrecalculatedInfo.class // precalculated info
    );

    private static final Optional<Constructor<AbstractInitializableBeanDefinitionAndReference>> BEAN_DEFINITION_CLASS_CONSTRUCTOR2 = ReflectionUtils.findConstructor(
        AbstractInitializableBeanDefinitionAndReference.class,
        Class.class, // beanType
        AbstractInitializableBeanDefinition.MethodOrFieldReference.class, // constructor
        AnnotationMetadata.class, // annotationMetadata
        AbstractInitializableBeanDefinition.MethodReference[].class, // methodInjection
        AbstractInitializableBeanDefinition.FieldReference[].class, // fieldInjection
        AbstractInitializableBeanDefinition.AnnotationReference[].class, // annotationInjection
        ExecutableMethodsDefinition.class, // executableMethodsDefinition
        Map.class, // typeArgumentsMap
        AbstractInitializableBeanDefinition.PrecalculatedInfo.class, // precalculated info
        Condition[].class, // pre conditions
        Condition[].class, // post conditions
        Throwable.class // failed initialization
    );

    private static final Constructor<?> PRECALCULATED_INFO_CONSTRUCTOR = ReflectionUtils.getRequiredInternalConstructor(AbstractInitializableBeanDefinition.PrecalculatedInfo.class,
        Optional.class, // scope
        boolean.class, // isAbstract
        boolean.class, // isIterable
        boolean.class, // isSingleton
        boolean.class, // isPrimary
        boolean.class, // isConfigurationProperties
        boolean.class, // isContainerType
        boolean.class,  // requiresMethodProcessing,
        boolean.class // hasEvaluatedExpressions
    );

    private static final String FIELD_CONSTRUCTOR = "$CONSTRUCTOR";
    private static final String FIELD_EXECUTABLE_METHODS = "$EXEC";
    private static final String FIELD_INJECTION_METHODS = "$INJECTION_METHODS";
    private static final String FIELD_INJECTION_FIELDS = "$INJECTION_FIELDS";
    private static final String FIELD_ANNOTATION_INJECTIONS = "$ANNOTATION_INJECTIONS";
    private static final String FIELD_TYPE_ARGUMENTS = "$TYPE_ARGUMENTS";
    private static final String FIELD_INNER_CLASSES = "$INNER_CONFIGURATION_CLASSES";
    private static final String FIELD_EXPOSED_TYPES = "$EXPOSED_TYPES";
    private static final String FIELD_REPLACES = "$REPLACES";
    private static final String FIELD_FAILED_INITIALIZATION = "$FAILURE";
    private static final String FIELD_PRECALCULATED_INFO = "$INFO";
    private static final String FIELD_PRE_START_CONDITIONS = "$PRE_CONDITIONS";
    private static final String FIELD_POST_START_CONDITIONS = "$POST_CONDITIONS";

    private static final Constructor<?> METHOD_REFERENCE_CONSTRUCTOR = ReflectionUtils.getRequiredInternalConstructor(AbstractInitializableBeanDefinition.MethodReference.class,
        Class.class, // declaringType,
        String.class, // methodName
        Argument[].class, // arguments
        AnnotationMetadata.class// annotationMetadata
    );

    private static final Constructor<?> METHOD_REFERENCE_CONSTRUCTOR_POST_PRE = ReflectionUtils.getRequiredInternalConstructor(AbstractInitializableBeanDefinition.MethodReference.class,
        Class.class, // declaringType,
        String.class, // methodName
        Argument[].class, // arguments
        AnnotationMetadata.class, // annotationMetadata
        boolean.class, // isPostConstructMethod
        boolean.class // isPreDestroyMethod,
    );

    private static final Constructor<?> FIELD_REFERENCE_CONSTRUCTOR = ReflectionUtils.getRequiredInternalConstructor(AbstractInitializableBeanDefinition.FieldReference.class, Class.class, Argument.class);

    private static final Constructor<?> ANNOTATION_REFERENCE_CONSTRUCTOR = ReflectionUtils.getRequiredInternalConstructor(AbstractInitializableBeanDefinition.AnnotationReference.class, Argument.class);

    private static final Method METHOD_QUALIFIER_FOR_ARGUMENT =
        ReflectionUtils.getRequiredMethod(Qualifiers.class, "forArgument", Argument.class);

    private static final Method METHOD_QUALIFIER_BY_NAME = ReflectionUtils.getRequiredMethod(Qualifiers.class, "byName", String.class);

    private static final Method METHOD_QUALIFIER_BY_STEREOTYPE = ReflectionUtils.getRequiredMethod(Qualifiers.class, "byStereotype", Class.class);

    private static final Method METHOD_QUALIFIER_BY_ANNOTATION =
        ReflectionUtils.getRequiredMethod(Qualifiers.class, "byAnnotationSimple", AnnotationMetadata.class, String.class);

    private static final Method METHOD_QUALIFIER_BY_REPEATABLE_ANNOTATION =
        ReflectionUtils.getRequiredMethod(Qualifiers.class, "byRepeatableAnnotation", AnnotationMetadata.class, String.class);

    private static final Method METHOD_QUALIFIER_BY_QUALIFIERS =
        ReflectionUtils.getRequiredMethod(Qualifiers.class, "byQualifiers", Qualifier[].class);

    private static final Method METHOD_QUALIFIER_BY_INTERCEPTOR_BINDING =
        ReflectionUtils.getRequiredMethod(Qualifiers.class, "byInterceptorBinding", AnnotationMetadata.class);

    private static final Method METHOD_QUALIFIER_BY_TYPE = ReflectionUtils.getRequiredMethod(Qualifiers.class, "byType", Class[].class);

    private static final Method METHOD_BEAN_RESOLUTION_CONTEXT_MARK_FACTORY = ReflectionUtils.getRequiredMethod(BeanResolutionContext.class, "markDependentAsFactory");

    private static final Method METHOD_PROXY_TARGET_TYPE = ReflectionUtils.getRequiredInternalMethod(ProxyBeanDefinition.class, "getTargetDefinitionType");

    private static final Method METHOD_PROXY_TARGET_CLASS = ReflectionUtils.getRequiredInternalMethod(ProxyBeanDefinition.class, "getTargetType");

    private static final ClassTypeDef TYPE_QUALIFIERS = ClassTypeDef.of(Qualifiers.class);
    private static final ClassTypeDef TYPE_QUALIFIER = ClassTypeDef.of(Qualifier.class);

    private static final int INSTANTIATE_METHOD_BEAN_RESOLUTION_CONTEXT_PARAM = 0;
    private static final int INSTANTIATE_METHOD_BEAN_CONTEXT_PARAM = 1;

    private static final Method METHOD_BEAN_CONTEXT_GET_CONVERSION_SERVICE = ReflectionUtils.getRequiredMethod(ConversionServiceProvider.class, "getConversionService");

    private static final Method METHOD_INITIALIZE =
        ReflectionUtils.getRequiredInternalMethod(InitializingBeanDefinition.class, "initialize", BeanResolutionContext.class, BeanContext.class, Object.class);

    private static final Method METHOD_DO_INITIALIZE =
        ReflectionUtils.getRequiredInternalMethod(InterceptedInitializingBeanDefinition.class, "doInitialize", BeanResolutionContext.class, BeanContext.class, Object.class);

    private static final Method METHOD_DEFAULT_INITIALIZE =
        ReflectionUtils.getRequiredInternalMethod(InterceptedInitializingBeanDefinition.class, "initialize", BeanResolutionContext.class, BeanContext.class, Object.class);

    private static final Method METHOD_DISPOSE =
        ReflectionUtils.getRequiredInternalMethod(DisposableBeanDefinition.class, "dispose", BeanResolutionContext.class, BeanContext.class, Object.class);

    private static final Method METHOD_DO_DISPOSE =
        ReflectionUtils.getRequiredInternalMethod(InterceptedDisposeBeanDefinition.class, "doDispose", BeanResolutionContext.class, BeanContext.class, Object.class);

    private static final Method METHOD_DEFAULT_DISPOSE =
        ReflectionUtils.getRequiredInternalMethod(InterceptedDisposeBeanDefinition.class, "dispose", BeanResolutionContext.class, BeanContext.class, Object.class);

    private static final Method DESTROY_INJECT_SCOPED_BEANS_METHOD = ReflectionUtils.getRequiredInternalMethod(BeanResolutionContext.class, "destroyInjectScopedBeans");
    private static final Method CHECK_IF_SHOULD_LOAD_METHOD = ReflectionUtils.getRequiredMethod(AbstractInitializableBeanDefinition.class,
        "checkIfShouldLoad",
        BeanResolutionContext.class,
        BeanContext.class);
    private static final Method GET_MAP_METHOD = ReflectionUtils.getRequiredMethod(Map.class, "get", Object.class);
    private static final Method LOAD_REFERENCE_METHOD = ReflectionUtils.getRequiredMethod(BeanDefinitionReference.class, "load");
    private static final Method IS_CONTEXT_SCOPE_METHOD = ReflectionUtils.getRequiredMethod(BeanDefinitionReference.class, "isContextScope");
    private static final Method IS_PROXIED_BEAN_METHOD = ReflectionUtils.getRequiredMethod(BeanDefinitionReference.class, "isProxiedBean");
    private static final Method IS_ENABLED_METHOD = ReflectionUtils.getRequiredMethod(BeanContextConditional.class, "isEnabled", BeanContext.class);
    private static final Method IS_ENABLED2_METHOD = ReflectionUtils.getRequiredMethod(BeanContextConditional.class, "isEnabled", BeanContext.class, BeanResolutionContext.class);
    private static final Method GET_INTERCEPTED_TYPE_METHOD = ReflectionUtils.getRequiredMethod(AdvisedBeanType.class, "getInterceptedType");
    private static final Method PARAMETRIZED_DO_INSTANTIATE_METHOD = ReflectionUtils.getRequiredMethod(ParametrizedInstantiatableBeanDefinition.class, "doInstantiate", BeanResolutionContext.class, BeanContext.class, Map.class);
    private static final Method INSTANTIATE_METHOD = ReflectionUtils.getRequiredMethod(InstantiatableBeanDefinition.class, "instantiate", BeanResolutionContext.class, BeanContext.class);
    private static final Method DO_INSTANTIATE_INTERCEPTED_METHOD = ReflectionUtils.getRequiredMethod(InterceptedInstantiateBeanDefinition.class, "doInstantiate", BeanResolutionContext.class, BeanContext.class, Object[].class);
    private static final Method INTERCEPTED_DEFAULT_INSTANTIATE_METHOD = ReflectionUtils.getRequiredMethod(InterceptedInstantiateBeanDefinition.class, "instantiate", BeanResolutionContext.class, BeanContext.class);
    private static final Method RESOLVE_INSTANTIATION_VALUES_METHOD = ReflectionUtils.getRequiredMethod(InterceptedInstantiateBeanDefinition.class, "resolveInstantiationValues", BeanResolutionContext.class, BeanContext.class);
    private static final Method RESOLVE_PARAMETRIZED_INSTANTIATION_VALUES_METHOD = ReflectionUtils.getRequiredMethod(InterceptedParametrizedInstantiateBeanDefinition.class, "resolveInstantiationValues", BeanResolutionContext.class, BeanContext.class, Map.class);
    private static final Method INTERCEPTED_PARAMETRIZED_DEFAULT_INSTANTIATE_METHOD = ReflectionUtils.getRequiredMethod(InterceptedParametrizedInstantiateBeanDefinition.class, "instantiate", BeanResolutionContext.class, BeanContext.class);

    private static final Method COLLECTION_UTILS_ENUM_SET_METHOD = ReflectionUtils.getRequiredMethod(CollectionUtils.class, "enumSet", Enum[].class);
    private static final Method IS_INNER_CONFIGURATION_METHOD = ReflectionUtils.getRequiredMethod(AbstractInitializableBeanDefinition.class, "isInnerConfiguration", Class.class);
    private static final Method CONTAINS_METHOD = ReflectionUtils.getRequiredMethod(Collection.class, "contains", Object.class);
    private static final Method GET_EXPOSED_TYPES_METHOD = ReflectionUtils.getRequiredMethod(AbstractInitializableBeanDefinition.class, "getExposedTypes");
    private static final Method IS_CANDIDATE_BEAN_METHOD = ReflectionUtils.getRequiredMethod(BeanDefinition.class, "isCandidateBean", Argument.class);
    private static final Method GET_ORDER_METHOD = ReflectionUtils.getRequiredMethod(Ordered.class, "getOrder");
    private static final Constructor<HashSet> HASH_SET_COLLECTION_CONSTRUCTOR = ReflectionUtils.getRequiredInternalConstructor(HashSet.class, Collection.class);
    private static final Method ARRAYS_AS_LIST_METHOD = ReflectionUtils.getRequiredMethod(Arrays.class, "asList", Object[].class);
    private static final Method COLLECTIONS_SINGLETON_METHOD = ReflectionUtils.getRequiredMethod(Collections.class, "singleton", Object.class);
    private static final Method OPTIONAL_IS_PRESENT_METHOD = ReflectionUtils.getRequiredMethod(Optional.class, "isPresent");
    private static final Method OPTIONAL_GET_METHOD = ReflectionUtils.getRequiredMethod(Optional.class, "get");
    private static final Method DURATION_TO_MILLIS_METHOD = ReflectionUtils.getRequiredMethod(Duration.class, "toMillis");
    private static final Method PROVIDER_GET_ANNOTATION_METADATA_METHOD = ReflectionUtils.getRequiredMethod(AnnotationMetadataProvider.class, "getAnnotationMetadata");
    private static final Method IS_PROXY_TARGET_METHOD = ReflectionUtils.getRequiredMethod(BeanDefinitionReference.class, "isProxyTarget");
    private static final Method GET_CONFIGURATION_PATH_METHOD = ReflectionUtils.getRequiredInternalMethod(BeanResolutionContext.class, "getConfigurationPath");
    private static final Method GET_TYPE_PARAMETERS_METHOD = ReflectionUtils.getRequiredInternalMethod(TypeVariableResolver.class, "getTypeParameters");
    private static final Method ARGUMENT_OF_METHOD = ReflectionUtils.getRequiredInternalMethod(Argument.class, "of", Class.class);
    private static final Method BD_GET_INDEXES_OF_EXECUTABLE_METHODS_FOR_PROCESSING = ReflectionUtils.getRequiredInternalMethod(AbstractInitializableBeanDefinition.class, "getIndexesOfExecutableMethodsForProcessing");
    private static final Method GET_INDEXES_METHOD = ReflectionUtils.getRequiredMethod(BeanDefinitionReference.class, "getIndexes");
    private static final Method IS_PARALLEL_METHOD = ReflectionUtils.getRequiredMethod(BeanDefinitionReference.class, "isParallel");
    private static final Method IS_ASSIGNABLE_METHOD = ReflectionUtils.getRequiredMethod(Class.class, "isAssignableFrom", Class.class);

    private static final Set<String> IGNORED_EXPOSED_INTERFACES = Set.of(
        AutoCloseable.class.getName(), LifeCycle.class.getName(), Ordered.class.getName(), Closeable.class.getName(),
        Named.class.getName(), Described.class.getName(),
        Record.class.getName(), Enum.class.getName(), Toggleable.class.getName(), Iterable.class.getName(),
        Serializable.class.getName()
    );

    private final String beanFullClassName;
    private final String beanDefinitionName;
    private final TypeDef beanTypeDef;
    private final Map<String, MethodDef> loadTypeMethods = new LinkedHashMap<>();
    private final ClassTypeDef beanDefinitionTypeDef;
    private final boolean isInterface;
    private final boolean isAbstract;
    private final boolean isConfigurationProperties;
    private final Element beanProducingElement;
    private final ClassElement beanTypeElement;
    private final VisitorContext visitorContext;
    private final List<String> beanTypeInnerClasses;
    private final EvaluatedExpressionProcessor evaluatedExpressionProcessor;

    private ClassTypeDef superType = TYPE_ABSTRACT_BEAN_DEFINITION_AND_REFERENCE;
    private boolean superBeanDefinition = false;
    private boolean isSuperFactory = false;
    private final AnnotationMetadata annotationMetadata;
    // Sometimes the original annotations are hierarchy etc and there we cannot contribute defaults easily
    private final AnnotationMetadata annotationMetadataDefaults = new MutableAnnotationMetadata();
    private Map<String, Map<String, ClassElement>> typeArguments;
    @Nullable
    private String interceptedType;
    @Nullable
    private Set<ClassElement> exposes;
    private final Map<ClassElement, List<AnnotationVisitData>> annotationInjectionPoints = new LinkedHashMap<>(2);
    private final Map<String, Boolean> isLifeCycleCache = new HashMap<>(2);
    private ExecutableMethodsDefinitionWriter executableMethodsDefinitionWriter;
    private boolean generateExecutableMethodsDefinitionWriter = true;

    private boolean disabled = false;

    private final boolean keepConfPropInjectPoints;
    private boolean proxiedBean = false;
    private boolean isProxyTarget = false;

    private String proxyBeanDefinitionName, proxyBeanTypeName;

    private final OriginatingElements originatingElements;

    private ClassDef.ClassDefBuilder classDefBuilder;

    private boolean validated;

    private final Function<String, ExpressionDef> loadClassValueExpressionFn;

    private final List<MethodDefinition<ClassElement, MethodElement>> allMethods = new ArrayList<>();
    private final List<MethodDefinition<ClassElement, MethodElement>> postConstructMethods = new ArrayList<>();
    private final List<MethodDefinition<ClassElement, MethodElement>> preDestroyMethods = new ArrayList<>();
    private final List<FieldDefinition<ClassElement, FieldElement>> allFields = new ArrayList<>();

    private final List<InjectCommand> injectCommands = new ArrayList<>();

    private final MemberDefinition<ClassElement> elementProducerDefinition; // Method, Constructor, orField
    private ConstructorDefinition<ClassElement, MethodElement> constructorDefinition;
    private MethodDefinition<ClassElement, MethodElement> factoryMethodDefinition;
    private FieldDefinition<ClassElement, FieldElement> factoryFieldDefinition;
    private CustomInitializerBuilder customInitializerBuilder;

    public BeanDefinitionWriter(ConstructorDefinition<ClassElement, MethodElement> constructorDefinition,
                                @Nullable
                                String beanDefinitionName,
                                @Nullable
                                AnnotationMetadata annotationMetadata,
                                VisitorContext visitorContext) {
        this(constructorDefinition, beanDefinitionName, annotationMetadata, OriginatingElements.of(constructorDefinition.constructorElement().getOwningType()), visitorContext);
    }

    public BeanDefinitionWriter(ConstructorDefinition<ClassElement, MethodElement> constructorDefinition,
                                VisitorContext visitorContext) {
        this(constructorDefinition, OriginatingElements.of(constructorDefinition.constructorElement().getOwningType()), visitorContext);
    }

    public BeanDefinitionWriter(ConstructorDefinition<ClassElement, MethodElement> constructorDefinition,
                                OriginatingElements originatingElements,
                                VisitorContext visitorContext) {
        this(constructorDefinition, null, null, originatingElements, visitorContext);
    }

    public BeanDefinitionWriter(ConstructorDefinition<ClassElement, MethodElement> constructorDefinition,
                                @Nullable
                                String beanDefinitionName,
                                @Nullable
                                AnnotationMetadata annotationMetadata,
                                OriginatingElements originatingElements,
                                VisitorContext visitorContext) {
        this(
            constructorDefinition,
            constructorDefinition.constructorElement().getOwningType(),
            constructorDefinition.constructorElement().getOwningType(),
            beanDefinitionName == null ? getBeanDefinitionName(
                constructorDefinition.constructorElement().getOwningType().getPackageName(),
                constructorDefinition.constructorElement().getOwningType().getSimpleName()
            ) : beanDefinitionName + CLASS_SUFFIX,
            !constructorDefinition.constructorElement().getOwningType().isInterface() && constructorDefinition.constructorElement().getOwningType().isAbstract(),
            annotationMetadata,
            originatingElements,
            visitorContext
        );
        this.constructorDefinition = constructorDefinition;

        applyConfigurationInjectionIfNecessary(constructorDefinition.annotationMetadata());
        evaluatedExpressionProcessor.processEvaluatedExpressions(constructorDefinition.constructorElement());
    }

    public BeanDefinitionWriter(FieldDefinition<ClassElement, FieldElement> factoryFieldDefinition,
                                VisitorContext visitorContext) {
        this(
            factoryFieldDefinition,
            factoryFieldDefinition.fieldElement(),
            factoryFieldDefinition.fieldElement().getGenericField(),
            getBeanDefinitionName(factoryFieldDefinition.fieldElement()),
            false,
            null,
            OriginatingElements.of(factoryFieldDefinition.fieldElement()),
            visitorContext
        );
        this.factoryFieldDefinition = factoryFieldDefinition;
        evaluatedExpressionProcessor.processEvaluatedExpressions(factoryFieldDefinition.getAnnotationMetadata(), factoryFieldDefinition.fieldElement().getOwningType());
    }

    public BeanDefinitionWriter(MethodDefinition<ClassElement, MethodElement> factoryMethodDefinition,
                                VisitorContext visitorContext,
                                int uniqueIdentifier) {
        this(
            factoryMethodDefinition,
            factoryMethodDefinition.methodElement(),
            factoryMethodDefinition.methodElement().getGenericReturnType(),
            getBeanDefinitionName(factoryMethodDefinition.methodElement(), uniqueIdentifier),
            false,
            null,
            OriginatingElements.of(factoryMethodDefinition.methodElement()),
            visitorContext
        );
        this.factoryMethodDefinition = factoryMethodDefinition;
        evaluatedExpressionProcessor.processEvaluatedExpressions(factoryMethodDefinition.methodElement());
    }

    private BeanDefinitionWriter(MemberDefinition<ClassElement> elementProducerDefinition,
                                 Element beanProducingElement,
                                 ClassElement beanType,
                                 String beanDefinitionName,
                                 boolean isAbstractBean,
                                 @Nullable
                                 AnnotationMetadata annotationMetadata,
                                 OriginatingElements originatingElements,
                                 VisitorContext visitorContext) {
        this.elementProducerDefinition = elementProducerDefinition;
        this.originatingElements = originatingElements;
        this.beanProducingElement = beanProducingElement;
        autoApplyNamedToBeanProducingElement(beanProducingElement);
        this.beanFullClassName = beanType.getName();
        this.isInterface = beanType.isInterface();
        this.isAbstract = isAbstractBean;
        this.beanTypeElement = beanType;
        this.annotationMetadata = annotationMetadata == null ? beanProducingElement.getTargetAnnotationMetadata() : annotationMetadata;
        this.beanDefinitionTypeDef = ClassTypeDef.of(beanDefinitionName);
        this.beanTypeDef = TypeDef.erasure(beanTypeElement);
        this.isConfigurationProperties = isConfigurationProperties(this.annotationMetadata);
        validateExposedTypes(this.annotationMetadata, visitorContext);
        this.beanDefinitionName = beanDefinitionName;
        this.visitorContext = visitorContext;
        this.evaluatedExpressionProcessor = new EvaluatedExpressionProcessor(visitorContext, getOriginatingElement());
        evaluatedExpressionProcessor.processEvaluatedExpressions(this.annotationMetadata,
            beanTypeElement.getName().contains(BeanDefinitionVisitor.PROXY_SUFFIX) ? null : beanTypeElement);

        beanTypeInnerClasses = beanTypeElement.getEnclosedElements(ElementQuery.of(ClassElement.class))
            .stream()
            .filter(BeanDefinitionWriter::isConfigurationProperties)
            .map(Element::getName)
            .toList();
        String prop = visitorContext.getOptions().get(OMIT_CONFPROP_INJECTION_POINTS);
        keepConfPropInjectPoints = prop == null || !prop.equals("true");

        TypeDef argumentType;
        if (beanTypeDef instanceof TypeDef.Primitive primitive) {
            argumentType = primitive.wrapperType();
        } else if (beanTypeDef instanceof TypeDef.Array array) {
            argumentType = array;
        } else {
            argumentType = ClassTypeDef.of(beanTypeElement);
        }

        classDefBuilder = ClassDef.builder(beanDefinitionName)
            .synthetic()
            .addModifiers(Modifier.PUBLIC)
            .addAnnotation(AnnotationDef.builder(Generated.class).addMember("service", BeanDefinitionReference.class.getName()).build())
            .superclass(TypeDef.parameterized(superType, argumentType));

        loadClassValueExpressionFn = AnnotationMetadataGenUtils.createLoadClassValueExpressionFn(beanDefinitionTypeDef, loadTypeMethods);

        typeArguments = beanType.getAllTypeArguments();

        visitAnnotationMetadata(this.annotationMetadata);
    }

    public MemberDefinition<ClassElement> getElementProducerDefinition() {
        return elementProducerDefinition;
    }

    public ClassElement getBeanTypeElement() {
        return beanTypeElement;
    }

    public void addTypeArguments(Map<String, Map<String, ClassElement>> typeArguments) {
        if (typeArguments == null) {
            this.typeArguments = typeArguments;
        } else {
            this.typeArguments.putAll(typeArguments);
        }
    }

    private void visitAnnotationMetadata(AnnotationMetadata annotationMetadata) {
        for (io.micronaut.core.annotation.AnnotationValue<Requires> annotation : annotationMetadata.getAnnotationValuesByType(Requires.class)) {
            annotation.stringValue(RequiresCondition.MEMBER_BEAN_PROPERTY)
                .ifPresent(beanProperty -> {
                    annotation.stringValue(RequiresCondition.MEMBER_BEAN)
                        .flatMap(className -> visitorContext.getClassElement(className, visitorContext.getElementAnnotationMetadataFactory().readOnly()))
                        .ifPresent(classElement -> {
                            String requiredValue = annotation.stringValue().orElse(null);
                            String notEqualsValue = annotation.stringValue(RequiresCondition.MEMBER_NOT_EQUALS).orElse(null);
                            visitAnnotationMemberPropertyInjectionPoint(classElement, beanProperty, requiredValue, notEqualsValue);
                        });
                });
        }
    }

    @Override
    public BeanDefinitionWriter addPostConstruct(MethodDefinition<ClassElement, MethodElement> methodDefinition) {
        allMethods.add(methodDefinition);
        postConstructMethods.add(methodDefinition);
        postProcessMethod(methodDefinition);
        return this;
    }

    @Override
    public BeanDefinitionWriter addPreDestroy(MethodDefinition<ClassElement, MethodElement> methodDefinition) {
        allMethods.add(methodDefinition);
        preDestroyMethods.add(methodDefinition);
        postProcessMethod(methodDefinition);
        return this;
    }

    @Override
    public BeanDefinitionWriter addMethodInjection(MethodDefinition<ClassElement, MethodElement> methodDefinition) {
        injectCommands.add(new InjectMethod(methodDefinition));
        if (shouldKeepInjectionPoint(methodDefinition.annotationMetadata())) {
            allMethods.add(methodDefinition);
        }
        postProcessMethod(methodDefinition);
        return this;
    }

    private void postProcessMethod(MethodDefinition<ClassElement, MethodElement> methodDefinition) {
        applyConfigurationInjectionIfNecessary(methodDefinition.annotationMetadata());
        applyDefaultNamedToParameters(List.of(methodDefinition.methodElement().getParameters()));
        evaluatedExpressionProcessor.processEvaluatedExpressions(methodDefinition.methodElement());
    }

    private void applyConfigurationInjectionIfNecessary(AnnotationMetadata annotationMetadata) {
        if (annotationMetadata.hasAnnotation(RequiresValidation.class)) {
            setValidated(true);
        }
    }

    @Override
    public BeanDefinitionWriter addFieldConfigurationBuilder(FieldElement fieldElement, AnnotationMetadata annotationMetadata, List<MethodDefinition<ClassElement, MethodElement>> builderMethods) {
        injectCommands.add(new InjectFieldConfigurationBuilder(fieldElement, annotationMetadata, builderMethods));
        return this;
    }

    @Override
    public BeanDefinitionWriter addMethodConfigurationBuilder(MethodElement methodElement, AnnotationMetadata annotationMetadata, List<MethodDefinition<ClassElement, MethodElement>> builderMethods) {
        injectCommands.add(new InjectMethodConfigurationBuilder(methodElement, annotationMetadata, builderMethods));
        return this;
    }

    private boolean shouldKeepInjectionPoint(AnnotationMetadata annotationMetadata1) {
        return keepConfPropInjectPoints || !isConfigurationProperties || !isValueType(annotationMetadata1);
    }

    @Override
    public BeanDefinitionWriter addFieldInjection(FieldDefinition<ClassElement, FieldElement> fieldDefinition) {
        injectCommands.add(new InjectField(fieldDefinition));
        if (shouldKeepInjectionPoint(fieldDefinition.annotationMetadata())) {
            allFields.add(fieldDefinition);
        }
        FieldElement fieldElement = fieldDefinition.fieldElement();
        autoApplyNamedIfPresent(fieldElement, fieldElement.getAnnotationMetadata());
        evaluatedExpressionProcessor.processEvaluatedExpressions(fieldElement.getAnnotationMetadata(), fieldElement.getOwningType());
        return this;
    }

    /**
     * Mark to generate proxy methods.
     *
     * @param proxyBeanDefinitionName The definition name
     * @param proxyBeanTypeName       The proxy bean name
     */
    public void generateProxyReference(String proxyBeanDefinitionName, String proxyBeanTypeName) {
        Objects.requireNonNull(proxyBeanDefinitionName);
        Objects.requireNonNull(proxyBeanTypeName);
        this.proxyBeanDefinitionName = proxyBeanDefinitionName;
        this.proxyBeanTypeName = proxyBeanTypeName;
    }

    @Override
    public boolean isEnabled() {
        return !disabled;
    }

    /**
     * Returns {@link ExecutableMethodsDefinitionWriter} of one exists.
     *
     * @return An instance of {@link ExecutableMethodsDefinitionWriter}
     */
    public ExecutableMethodsDefinitionWriter getExecutableMethodsWriter() {
        if (executableMethodsDefinitionWriter == null) {
            executableMethodsDefinitionWriter = createExecutableMethodsDefinitionWriter();
        }
        return executableMethodsDefinitionWriter;
    }

    public void setExecutableMethodsWriter(ExecutableMethodsDefinitionWriter executableMethodsDefinitionWriter) {
        this.executableMethodsDefinitionWriter = executableMethodsDefinitionWriter;
        dontGenerateExecutableMethodsDefinitionWriter();
    }

    public void dontGenerateExecutableMethodsDefinitionWriter() {
        // NOTE: proxied bean will reuse existing executable methods definition, the building is postponed
        generateExecutableMethodsDefinitionWriter = false;
    }

    /**
     * Returns {@link ExecutableMethodsDefinitionWriter} of one exists.
     *
     * @return An instance of {@link ExecutableMethodsDefinitionWriter} or null
     */
    @Nullable
    public ExecutableMethodsDefinitionWriter findExecutableMethodsWriter() {
        return executableMethodsDefinitionWriter;
    }

    private void autoApplyNamedToBeanProducingElement(Element beanProducingElement) {
        final AnnotationMetadata annotationMetadata = beanProducingElement.getAnnotationMetadata();
        if (!annotationMetadata.hasAnnotation(EachProperty.class) && !annotationMetadata.hasAnnotation(EachBean.class)) {
            autoApplyNamedIfPresent(beanProducingElement, annotationMetadata);
        }
    }

    private void validateExposedTypes(AnnotationMetadata annotationMetadata, VisitorContext visitorContext) {
        if (annotationMetadata instanceof AnnotationMetadataHierarchy) {
            annotationMetadata = annotationMetadata.getDeclaredMetadata();
        }
        final String[] types = annotationMetadata
            .stringValues(Bean.class, "typed");
        if (ArrayUtils.isNotEmpty(types) && !beanTypeElement.isProxy()) {
            for (String name : types) {
                final ClassElement exposedType = visitorContext.getClassElement(name).orElse(null);
                if (exposedType == null) {
                    visitorContext.fail("Bean defines an exposed type [" + name + "] that is not on the classpath", beanProducingElement);
                } else if (!beanTypeElement.isAssignable(exposedType)) {
                    visitorContext.fail("Bean defines an exposed type [" + name + "] that is not implemented by the bean type", beanProducingElement);
                }
            }
        }
    }

    private static String getBeanDefinitionName(String packageName, String className) {
        return packageName + "." + prefixClassName(className) + CLASS_SUFFIX;
    }

    private static String getBeanDefinitionName(MethodElement methodElement, int uniqueIdentifier) {
        return methodElement.getOwningType().getPackageName()
            + "." + prefixClassName(methodElement.getOwningType().getSimpleName())
            + "$" + NameUtils.capitalize(methodElement.getName()) + uniqueIdentifier + CLASS_SUFFIX;
    }

    private static String getBeanDefinitionName(FieldElement fieldElement) {
        return fieldElement.getOwningType().getPackageName()
            + "." + prefixClassName(fieldElement.getOwningType().getSimpleName())
            + "$" + NameUtils.capitalize(fieldElement.getName()) + CLASS_SUFFIX;
    }

    private static String prefixClassName(String className) {
        if (className.startsWith("$")) {
            return className;
        }
        return "$" + className;
    }

    /**
     * @return The type arguments
     */
    public ClassElement[] getTypeArguments() {
        if (hasTypeArguments()) {
            final Map<String, ClassElement> args = this.typeArguments.get(this.getBeanTypeName());
            if (CollectionUtils.isNotEmpty(args)) {
                return args.values().toArray(ClassElement.ZERO_CLASS_ELEMENTS);
            }
        }
        return ClassElement.ZERO_CLASS_ELEMENTS;
    }

    /**
     * @return The post construct method definitions scheduled for invocation
     */
    public List<MethodDefinition<ClassElement, MethodElement>> getPostConstructMethods() {
        return postConstructMethods;
    }

    /**
     * @return Is interface
     */
    public boolean isInterface() {
        return isInterface;
    }

    /**
     * @param interfaceType An interface to add
     */
    public void visitBeanDefinitionInterface(Class<? extends BeanDefinition> interfaceType) {
        this.classDefBuilder.addSuperinterface(TypeDef.of(interfaceType));
    }

    /**
     * @param name Visit the super bean definition
     */
    public void visitSuperBeanDefinition(String name) {
        this.superBeanDefinition = true;
        this.superType = ClassTypeDef.of(name);
        classDefBuilder.superclass(superType);
    }

    /**
     * @param beanName Visit a super bean factory
     */
    public void visitSuperBeanDefinitionFactory(String beanName) {
        this.superBeanDefinition = false;
        this.isSuperFactory = true;
    }

    /**
     * @return Teh bean type name
     */
    public String getBeanTypeName() {
        return beanFullClassName;
    }

    /**
     * @param validated If the bean is validated
     */
    public void setValidated(boolean validated) {
        if (validated) {
            if (!this.validated) {
                classDefBuilder.addSuperinterface(ClassTypeDef.of(ValidatedBeanDefinition.class));
                this.validated = true;
            }
        } else {
            if (this.validated) {
                throw new IllegalStateException("Bean definition " + beanTypeDef + " already marked for validation");
            }
        }
    }

    /**
     * @param typeName The intercepted type
     */
    public void setInterceptedType(String typeName) {
        if (typeName != null) {
            classDefBuilder.addSuperinterface(TypeDef.of(AdvisedBeanType.class));
        }
        this.interceptedType = typeName;
    }

    /**
     * @param exposes Teh exposed types
     */
    public void setExposes(Set<ClassElement> exposes) {
        this.exposes = exposes;
    }

    /**
     * @return Is validated
     */
    public boolean isValidated() {
        return validated;
    }

    /**
     * @return The bean definition name
     */
    public String getBeanDefinitionName() {
        return beanDefinitionName;
    }

    @Override
    public Element getOriginatingElement() {
        Element[] originatingElements = getOriginatingElements();
        if (ArrayUtils.isNotEmpty(originatingElements)) {
            return originatingElements[0];
        }
        return null;
    }

    @Override
    public List<OutputObjectDef> build() {
        processAllBeanElementVisitors();
        if (disabled) {
            return List.of();
        }
        OutputObjectDef executableMethodsClass = null;
        if (generateExecutableMethodsDefinitionWriter && executableMethodsDefinitionWriter != null) {
            executableMethodsClass = executableMethodsDefinitionWriter.build();
            // Make sure the methods are written and annotation defaults are contributed
        }

        evaluatedExpressionProcessor.registerExpressionForBuildTimeInit(classDefBuilder);

        MethodDef getOrderMethod = getGetOrder();
        if (getOrderMethod != null) {
            classDefBuilder.addMethod(getOrderMethod);
        }
        if (interceptedType != null) {
            classDefBuilder.addMethod(
                getGetInterceptedType(TypeDef.of(interceptedType))
            );
        }

        classDefBuilder.addMethod(
            MethodDef.override(
                LOAD_REFERENCE_METHOD
            ).build((aThis, methodParameters) -> aThis.type().instantiate().returning())
        );

        if (annotationMetadata.hasDeclaredAnnotation(Context.class)) {
            classDefBuilder.addMethod(
                MethodDef.override(
                    IS_CONTEXT_SCOPE_METHOD
                ).build((aThis, methodParameters) -> ExpressionDef.trueValue().returning())
            );
        }

        if (proxiedBean || superType != TYPE_ABSTRACT_BEAN_DEFINITION_AND_REFERENCE) {
            classDefBuilder.addMethod(
                MethodDef.override(
                    IS_PROXIED_BEAN_METHOD
                ).build((aThis, methodParameters) -> ExpressionDef.constant(proxiedBean).returning())
            );
        }

        if (isProxyTarget || superType != TYPE_ABSTRACT_BEAN_DEFINITION_AND_REFERENCE) {
            classDefBuilder.addMethod(
                MethodDef.override(
                    IS_PROXY_TARGET_METHOD
                ).build((aThis, methodParameters) -> ExpressionDef.constant(isProxyTarget).returning())
            );
        }

        if (!annotationMetadata.hasStereotype(Requires.class)) {
            classDefBuilder.addMethod(
                MethodDef.override(
                    IS_ENABLED_METHOD
                ).build((aThis, methodParameters) -> ExpressionDef.trueValue().returning())
            );
            classDefBuilder.addMethod(
                MethodDef.override(
                    IS_ENABLED2_METHOD
                ).build((aThis, methodParameters) -> ExpressionDef.trueValue().returning())
            );
        }

        if (proxyBeanDefinitionName != null) {
            classDefBuilder.addMethod(
                MethodDef.override(
                    METHOD_PROXY_TARGET_TYPE
                ).build((aThis, methodParameters)
                    -> ExpressionDef.constant(ClassTypeDef.of(proxyBeanDefinitionName)).returning())
            );

            classDefBuilder.addMethod(
                MethodDef.override(
                    METHOD_PROXY_TARGET_CLASS
                ).build((aThis, methodParameters)
                    -> ExpressionDef.constant(ClassTypeDef.of(proxyBeanTypeName)).returning())
            );
        }

        addInstantiateMethod();

        if (!injectCommands.isEmpty()) {
            classDefBuilder.addMethod(
                getInjectMethod()
            );
        }

        if (needsPostConstruct()) {
            classDefBuilder.addSuperinterface(TypeDef.of(InitializingBeanDefinition.class));
            if (isPostConstructIntercepted()) {
                classDefBuilder.addSuperinterface(TypeDef.of(InterceptedInitializingBeanDefinition.class));
                if (superBeanDefinition) {
                    classDefBuilder.addMethod(MethodDef.override(METHOD_INITIALIZE)
                        .build((aThis, methodParameters) -> aThis.superRef(ClassTypeDef.of(InterceptedInitializingBeanDefinition.class))
                            .invoke(METHOD_DEFAULT_INITIALIZE, methodParameters).returning()));
                }
                classDefBuilder.addMethod(
                    buildInitializeMethod(MethodDef.override(METHOD_DO_INITIALIZE))
                );
            } else if (!superBeanDefinition) {
                //  for "super bean definition" we only add code to trigger "initialize"
                classDefBuilder.addMethod(
                    buildInitializeMethod(MethodDef.override(METHOD_INITIALIZE))
                );
            }
        }

        if (needsPreDestroy()) {
            classDefBuilder.addSuperinterface(TypeDef.of(DisposableBeanDefinition.class));
            if (isPreDestroyIntercepted()) {
                classDefBuilder.addSuperinterface(TypeDef.of(InterceptedDisposeBeanDefinition.class));
                if (superBeanDefinition) {
                    classDefBuilder.addMethod(MethodDef.override(METHOD_DISPOSE)
                        .build((aThis, methodParameters) -> aThis.superRef(ClassTypeDef.of(InterceptedDisposeBeanDefinition.class))
                            .invoke(METHOD_DEFAULT_DISPOSE, methodParameters).returning()));
                }
                classDefBuilder.addMethod(
                    buildDisposeMethod(MethodDef.override(METHOD_DO_DISPOSE))
                );
            } else {
                classDefBuilder.addMethod(
                    buildDisposeMethod(MethodDef.override(METHOD_DISPOSE))
                );
            }
        }

        StaticBlock staticBlock = getStaticInitializer();

        classDefBuilder.addStaticInitializer(staticBlock.statement);

        addConstructor(staticBlock);

        boolean isParallel = annotationMetadata.hasStereotype(Parallel.class);
        // In v6 we can assume everything was recompiled with v5 so we can modify the default method to return false and only add this one on true
        classDefBuilder.addMethod(
            MethodDef.override(IS_PARALLEL_METHOD).build((aThis, methodParameters) -> ExpressionDef.constant(isParallel).returning())
        );

        AnnotationValue<DefaultImplementation> defaultImplementationAnnotationValue = annotationMetadata.getAnnotation(DefaultImplementation.class);
        if (defaultImplementationAnnotationValue != null) {
            AnnotationClassValue<?> defaultImplementationClass = defaultImplementationAnnotationValue.annotationClassValue("name").orElse(null);
            if (defaultImplementationClass != null) {
                classDefBuilder.addMethod(MethodDef.override(METHOD_GET_DEFAULT_IMPLEMENTATION)
                    .build((aThis, methodParameters) ->
                        ExpressionDef.constant(TypeDef.of(defaultImplementationClass.getName()))
                            .returning()
                            .doTry()
                            .doCatch(Throwable.class, exceptionVar -> ExpressionDef.nullValue().returning())
                    )
                );
            }
        }
        if (annotationMetadata.hasStereotype(Infrastructure.class)) {
            classDefBuilder.addMethod(
                MethodDef.override(METHOD_IS_CAN_BE_REPLACED).build((aThis, methodParameters) -> ExpressionDef.constant(false).returning())
            );
        }

        loadTypeMethods.values().forEach(classDefBuilder::addMethod);

        if (executableMethodsDefinitionWriter != null && executableMethodsDefinitionWriter.requiresMethodProcessing()) {
            int methodsCount = executableMethodsDefinitionWriter.getMethodsCount();
            List<ExpressionDef> expressions = new ArrayList<>(methodsCount);
            for (int i = 0; i < methodsCount; i++) {
                MethodElement method = executableMethodsDefinitionWriter.getMethodByIndex(i);
                if (method.booleanValue(Executable.class, Executable.MEMBER_PROCESS_ON_STARTUP).orElse(false)) {
                    expressions.add(TypeDef.Primitive.INT.constant(i));
                }
            }
            classDefBuilder.addMethod(MethodDef.override(BD_GET_INDEXES_OF_EXECUTABLE_METHODS_FOR_PROCESSING)
                .build((aThis, methodParameters) -> TypeDef.Primitive.INT.array().instantiate(expressions).returning()));
        }

        List<OutputObjectDef> classes = new ArrayList<>();
        classes.add(new OutputObjectDef(classDefBuilder.build(), BeanDefinitionReference.class, originatingElements));
        if (executableMethodsClass != null) {
            classes.add(executableMethodsClass);
        }
        classes.addAll(evaluatedExpressionProcessor.build());
        return classes;
    }

    private ExecutableMethodsDefinitionWriter createExecutableMethodsDefinitionWriter() {
        return new ExecutableMethodsDefinitionWriter(
            evaluatedExpressionProcessor,
            annotationMetadataDefaults,
            beanDefinitionName,
            getBeanDefinitionName(),
            originatingElements
        );
    }

    private MethodDef getGetInterceptedType(TypeDef interceptedType) {
        return MethodDef.override(GET_INTERCEPTED_TYPE_METHOD)
            .build((aThis, methodParameters) -> ExpressionDef.constant(interceptedType).returning());
    }

    private void addInstantiateMethod() {
        boolean isParametrized = isParametrized();

        if (isConstructorIntercepted(elementProducerDefinition.annotationMetadata())) {
            Method resolveValuesMethod;
            Method defaultInstantiateMethod;
            ClassTypeDef interceptedInterface;
            boolean isAopProxy = StringUtils.isNotEmpty(interceptedType);
            if (isParametrized) {
                resolveValuesMethod = RESOLVE_PARAMETRIZED_INSTANTIATION_VALUES_METHOD;
                if (isAopProxy) {
                    interceptedInterface = ClassTypeDef.of(ProxyInterceptedParametrizedInstantiateBeanDefinition.class);
                    defaultInstantiateMethod = INTERCEPTED_PARAMETRIZED_DEFAULT_INSTANTIATE_METHOD;
                } else {
                    interceptedInterface = ClassTypeDef.of(InterceptedParametrizedInstantiateBeanDefinition.class);
                    defaultInstantiateMethod = null;
                }
            } else {
                resolveValuesMethod = RESOLVE_INSTANTIATION_VALUES_METHOD;
                if (isAopProxy) {
                    interceptedInterface = ClassTypeDef.of(ProxyInterceptedInstantiateBeanDefinition.class);
                    defaultInstantiateMethod = INTERCEPTED_DEFAULT_INSTANTIATE_METHOD;
                } else {
                    interceptedInterface = ClassTypeDef.of(InterceptedInstantiateBeanDefinition.class);
                    defaultInstantiateMethod = null;
                }
            }
            classDefBuilder.addSuperinterface(interceptedInterface);

            // Remove after AbstractInitializableBeanDefinition#doInstantiate is removed
            classDefBuilder.addMethod(MethodDef.override(PARAMETRIZED_DO_INSTANTIATE_METHOD)
                .build((aThis, methodParameters) ->
                    aThis.superRef(interceptedInterface).invoke(PARAMETRIZED_DO_INSTANTIATE_METHOD, methodParameters).returning()));

            if (superBeanDefinition) {
                classDefBuilder.addMethod(MethodDef.override(INSTANTIATE_METHOD)
                    .build((aThis, methodParameters) ->
                        aThis.superRef(interceptedInterface).invoke(defaultInstantiateMethod, methodParameters).returning()));
            }
            classDefBuilder.addMethod(MethodDef.override(resolveValuesMethod)
                .build((aThis, methodParameters) -> {
                    List<StatementDef> statements = new ArrayList<>();
                    List<? extends ExpressionDef> values = resolveConstructorValues(aThis, methodParameters, isParametrized, statements);
                    statements.add(
                        TypeDef.OBJECT.array().instantiate(values).returning()
                    );
                    return StatementDef.multi(statements);
                }));
            classDefBuilder.addMethod(MethodDef.override(DO_INSTANTIATE_INTERCEPTED_METHOD)
                .build((aThis, methodParameters) -> {
                    ExpressionDef constructorValuesArray = methodParameters.get(2);
                    List<StatementDef> statements = new ArrayList<>(3);
                    statements.add(invokeCheckIfShouldLoadIfNecessary(aThis, methodParameters));
                    ParameterElement[] parameterElements = resolveConstructorParameters();
                    List<ExpressionDef> extractedValues = IntStream.range(0, parameterElements.length)
                        .<ExpressionDef>mapToObj(index -> constructorValuesArray.arrayElement(index).cast(TypeDef.erasure(parameterElements[index].getType())))
                        .toList();
                    ExpressionDef newInstance = buildNewInstance(aThis, methodParameters, statements, extractedValues);
                    statements.add(injectAndReturn(aThis, methodParameters, newInstance));
                    return StatementDef.multi(statements);
                }));
        } else {
            MethodDef.MethodDefBuilder buildMethodBuilder;
            if (isParametrized) {
                buildMethodBuilder = MethodDef.override(PARAMETRIZED_DO_INSTANTIATE_METHOD);
                classDefBuilder.addSuperinterface(TypeDef.of(ParametrizedInstantiatableBeanDefinition.class));
            } else {
                buildMethodBuilder = MethodDef.override(INSTANTIATE_METHOD);
            }

            classDefBuilder.addMethod(
                buildMethodBuilder.build((aThis, methodParameters) -> {
                    List<StatementDef> statements = new ArrayList<>(3);
                    statements.add(invokeCheckIfShouldLoadIfNecessary(aThis, methodParameters));
                    List<? extends ExpressionDef> values = resolveConstructorValues(aThis, methodParameters, isParametrized, statements);
                    ExpressionDef newInstance = buildNewInstance(aThis, methodParameters, statements, values);
                    statements.add(injectAndReturn(aThis, methodParameters, newInstance));
                    return StatementDef.multi(statements);
                })
            );
        }
    }

    private MethodDef getInjectMethod() {
        return MethodDef.override(INJECT_BEAN_METHOD)
            .build((aThis, methodParameters) -> {
                return methodParameters.get(2).cast(beanTypeDef).newLocal("beanInstance", instanceVar -> {
                    InjectMethodSignature injectMethodSignature = new InjectMethodSignature(aThis, methodParameters, instanceVar);
                    List<StatementDef> statements = new ArrayList<>();
                    boolean hasInjectPoint = false;
                    for (InjectCommand injectCommand : injectCommands) {
                        switch (injectCommand) {
                            case InjectField(var fieldDefinition) -> {
                                statements.add(
                                    injectFieldOptionally(injectMethodSignature, fieldDefinition)
                                );
                                hasInjectPoint |= BeanDefinitionWriter.hasInjectScope(fieldDefinition.fieldElement());
                            }
                            case InjectMethod(var methodDefinition) -> {
                                statements.add(injectStatement(injectMethodSignature, methodDefinition));
                                hasInjectPoint |= BeanDefinitionWriter.hasInjectScope(methodDefinition.methodElement().getParameters());
                            }
                            case InjectFieldConfigurationBuilder(
                                var fieldElement, var annotationMetadata, var builderMethods
                            ) ->
                                statements.add(getInjectFieldConfigurationBuilder(injectMethodSignature, fieldElement, annotationMetadata, builderMethods));
                            case InjectMethodConfigurationBuilder(
                                var methodElement, var annotationMetadata, var builderMethods
                            ) ->
                                statements.add(getInjectMethodConfigurationBuilder(injectMethodSignature, methodElement, annotationMetadata, builderMethods));
                        }

                    }

                    List<StatementDef> returnStatements = new ArrayList<>();
                    if (hasInjectPoint) {
                        returnStatements.add(destroyInjectScopeBeansIfNecessary(methodParameters));
                    }
                    returnStatements.add(instanceVar.returning());

                    statements.addAll(returnStatements);

                    if (isConfigurationProperties) {
                        return aThis.invoke(
                            CONTAINS_PROPERTIES_METHOD,

                            injectMethodSignature.beanResolutionContext,
                            injectMethodSignature.beanContext
                        ).ifTrue(
                            StatementDef.multi(statements),
                            StatementDef.multi(returnStatements)
                        );
                    }
                    return StatementDef.multi(statements);
                });
            });
    }

    private StatementDef injectFieldOptionally(InjectMethodSignature injectMethodSignature, FieldDefinition<ClassElement, FieldElement> injectedField) {
        StatementDef injectFieldStatement = injectField(injectMethodSignature, injectedField);
        if (injectedField.isOptional()) {
            FieldElement fieldElement = injectedField.fieldElement();
            String property = findProperty(injectedField.annotationMetadata()).orElseThrow(() -> new ProcessingException(fieldElement, "Optional field should have @Property defined"));
            injectFieldStatement = getPropertyContainsCheck(
                injectMethodSignature,
                fieldElement.getGenericField(),
                property,
                fieldElement.getName()
            ).ifTrue(injectFieldStatement);
        }
        return injectFieldStatement;
    }

    private StatementDef getInjectFieldConfigurationBuilder(InjectMethodSignature injectMethodSignature,
                                                            FieldElement fieldElement,
                                                            AnnotationMetadata annotationMetadata,
                                                            List<MethodDefinition<ClassElement, MethodElement>> builderMethods) {
        String factoryMethod = annotationMetadata
            .stringValue(ConfigurationBuilder.class, "factoryMethod").orElse(null);

        ClassTypeDef builderType = ClassTypeDef.of(fieldElement.getGenericType());
        if (StringUtils.isNotEmpty(factoryMethod)) {
            return builderType.invokeStatic(factoryMethod, builderType).newLocal(BUILDER_VARIABLE_PREFIX + NameUtils.capitalize(fieldElement.getName()), builderVar -> {
                List<StatementDef> statements = getBuilderMethodStatements(injectMethodSignature, builderMethods, (VariableDef.Local) builderVar);

                statements.add(injectMethodSignature.instanceVar
                    .field(fieldElement)
                    .put(builderVar));

                return StatementDef.multi(statements);
            });
        } else {
            return injectMethodSignature.instanceVar
                .field(fieldElement)
                .newLocal(BUILDER_VARIABLE_PREFIX + NameUtils.capitalize(fieldElement.getName()), builderVar -> StatementDef.multi(
                    getBuilderMethodStatements(injectMethodSignature, builderMethods, (VariableDef.Local) builderVar)
                ));
        }
    }

    private StatementDef getInjectMethodConfigurationBuilder(InjectMethodSignature injectMethodSignature,
                                                             MethodElement builderMethod,
                                                             AnnotationMetadata annotationMetadata,
                                                             List<MethodDefinition<ClassElement, MethodElement>> builderMethods) {
        String factoryMethod = annotationMetadata
            .stringValue(ConfigurationBuilder.class, "factoryMethod").orElse(null);

        ClassTypeDef builderType = ClassTypeDef.of(builderMethod.getGenericReturnType());
        String methodName = builderMethod.getName();
        if (StringUtils.isNotEmpty(factoryMethod)) {
            return builderType.invokeStatic(factoryMethod, builderType).newLocal(BUILDER_VARIABLE_PREFIX + NameUtils.capitalize(methodName), builderVar -> {
                List<StatementDef> statements =
                    getBuilderMethodStatements(injectMethodSignature, builderMethods, (VariableDef.Local) builderVar);

                String propertyName = NameUtils.getPropertyNameForGetter(methodName);
                String setterName = NameUtils.setterNameFor(propertyName);

                statements.add(injectMethodSignature.instanceVar
                    .invoke(setterName, TypeDef.VOID, builderVar));

                return StatementDef.multi(statements);
            });
        } else {
            return injectMethodSignature.instanceVar
                .invoke(methodName, builderType)
                .newLocal(BUILDER_VARIABLE_PREFIX + NameUtils.capitalize(methodName), builderVar -> StatementDef.multi(
                    getBuilderMethodStatements(injectMethodSignature, builderMethods, (VariableDef.Local) builderVar)
                ));
        }
    }

    private List<StatementDef> getBuilderMethodStatements(InjectMethodSignature injectMethodSignature,
                                                          List<MethodDefinition<ClassElement, MethodElement>> builderMethods,
                                                          VariableDef.Local builderVar) {
        List<StatementDef> statements = new ArrayList<>(builderMethods.size());
        for (MethodDefinition<ClassElement, MethodElement> builderMethod : builderMethods) {
            statements.add(
                getConfigBuilderPointStatement(injectMethodSignature, builderVar, builderMethod)
            );
        }
        return statements;
    }

    private StatementDef getConfigBuilderPointStatement(InjectMethodSignature injectMethodSignature,
                                                        VariableDef.Local builderVar,
                                                        MethodDefinition<ClassElement, MethodElement> builderPoint) {
        MethodElement methodElement = builderPoint.methodElement();
        PropertyInjectionPoint<ClassElement> booleanInjectionPoint = builderPoint.booleanInjectionPoint();
        boolean zeroArgs = booleanInjectionPoint != null;
        PropertyInjectionPoint<ClassElement> injectionPoint = booleanInjectionPoint == null ? (PropertyInjectionPoint<ClassElement>) builderPoint.injectionPoints().get(0) : booleanInjectionPoint;
        String propertyPath = injectionPoint.propertyPath();
        String propertyName = injectionPoint.propertyName();
        ClassElement propertyType = injectionPoint.type();
        Map<String, ClassElement> generics = propertyType.getTypeArguments();
        boolean isDurationWithTimeUnit = propertyType.getName().equals(Duration.class.getName());

        // Optional optional = AbstractBeanDefinition.getValueForPath(...)
        String localName = builderVar.name() + "_optional" + NameUtils.capitalize(propertyName);
        return getGetValueForPathCall(injectMethodSignature, propertyType, propertyName, propertyPath, zeroArgs, generics)
            .newLocal(localName, optionalVar -> {
                return optionalVar.invoke(OPTIONAL_IS_PRESENT_METHOD)
                    .ifTrue(
                        optionalVar.invoke(OPTIONAL_GET_METHOD).newLocal(localName + "_value", valueVar -> {
                            if (zeroArgs) {
                                return valueVar.cast(boolean.class).ifTrue(
                                    StatementDef.doTry(
                                        builderVar.invoke(methodElement)
                                    ).doCatch(NoSuchMethodError.class, ignore -> StatementDef.multi())
                                );
                            }
                            List<ExpressionDef> values = new ArrayList<>(2);
                            if (isDurationWithTimeUnit) {
                                ClassTypeDef timeInitType = ClassTypeDef.of(TimeUnit.class);
                                values.add(
                                    valueVar.cast(ClassTypeDef.of(Duration.class))
                                        .invoke(DURATION_TO_MILLIS_METHOD)
                                );
                                values.add(
                                    timeInitType.getStaticField("MILLISECONDS", timeInitType)
                                );
                            } else {
                                TypeDef paramTypeDef = TypeDef.erasure(propertyType);
                                values.add(valueVar.cast(paramTypeDef));
                            }
                            return StatementDef.doTry(
                                builderVar.invoke(methodElement, values)
                            ).doCatch(NoSuchMethodError.class, ignore -> StatementDef.multi());
                        })
                    );
            });
    }

    private StatementDef injectAndReturn(VariableDef.This aThis,
                                         List<VariableDef.MethodParameter> methodParameters,
                                         ExpressionDef beanInstance) {
        boolean needsInjectMethod = !injectCommands.isEmpty() || superBeanDefinition;
        boolean needsInjectScope = hasInjectScope();
        boolean needsPostConstruct = needsPostConstruct();
        if (!needsInjectScope && !needsInjectMethod && !needsPostConstruct) {
            return beanInstance.returning();
        }
        return beanInstance.newLocal("instance", instanceVar -> {
            List<StatementDef> statements = new ArrayList<>();
            if (needsInjectMethod) {
                statements.add(
                    aThis.invoke(INJECT_BEAN_METHOD, methodParameters.get(0), methodParameters.get(1), instanceVar)
                );
            }
            if (needsInjectScope) {
                statements.add(
                    destroyInjectScopeBeansIfNecessary(methodParameters)
                );
            }
            if (needsPostConstruct) {
                statements.add(
                    aThis.invoke(METHOD_INITIALIZE,

                        methodParameters.get(0),
                        methodParameters.get(1),
                        instanceVar
                    ).returning()
                );
            } else {
                statements.add(instanceVar.returning());
            }
            return StatementDef.multi(statements);
        });
    }

    private boolean needsPostConstruct() {
        return !postConstructMethods.isEmpty() || isPostConstructIntercepted();
    }

    private boolean needsPreDestroy() {
        return !preDestroyMethods.isEmpty() || isPreDestroyIntercepted();
    }

    private MethodDef buildDisposeMethod(MethodDef.MethodDefBuilder override) {
        return buildLifeCycleMethod(override, PRE_DESTROY_METHOD, preDestroyMethods);
    }

    private MethodDef buildInitializeMethod(MethodDef.MethodDefBuilder override) {
        return buildLifeCycleMethod(override, POST_CONSTRUCT_METHOD, postConstructMethods);
    }

    private MethodDef buildLifeCycleMethod(MethodDef.MethodDefBuilder methodDefBuilder,
                                           Method superMethod,
                                           List<MethodDefinition<ClassElement, MethodElement>> lifecycleMethods) {
        return methodDefBuilder.build((aThis, methodParameters) -> {
            return aThis.invoke(superMethod, methodParameters).cast(beanTypeDef).newLocal("beanInstance", beanInstance -> {
                List<StatementDef> statements = new ArrayList<>();
                boolean hasInjectScope = false;
                InjectMethodSignature injectMethodSignature = new InjectMethodSignature(aThis, methodParameters, beanInstance);

                for (MethodDefinition<ClassElement, MethodElement> lifecycleMethod : lifecycleMethods) {
                    statements.add(injectStatement(injectMethodSignature, lifecycleMethod));
                    if (!hasInjectScope) {
                        for (ParameterElement parameter : lifecycleMethod.methodElement().getSuspendParameters()) {
                            if (hasInjectScope(parameter)) {
                                hasInjectScope = true;
                                break;
                            }
                        }
                    }
                }
                if (hasInjectScope) {
                    statements.add(
                        destroyInjectScopeBeansIfNecessary(methodParameters)
                    );
                }
                statements.add(beanInstance.returning());
                return StatementDef.multi(statements);
            });
        });
    }

    private List<? extends ExpressionDef> resolveConstructorValues(VariableDef.This aThis,
                                                                   List<VariableDef.MethodParameter> methodParameters,
                                                                   boolean isParametrized,
                                                                   List<StatementDef> additionalStatements) {
        Supplier<VariableDef> constructorDefSupplier = new Supplier<>() {

            VariableDef constructorDef;

            @Override
            public VariableDef get() {
                if (constructorDef == null) {
                    Class<?> constructorType;
                    if (BeanDefinitionWriter.this.elementProducerDefinition instanceof MethodDefinition<?, ?> ||
                        BeanDefinitionWriter.this.elementProducerDefinition instanceof ConstructorDefinition<?, ?>) {
                        constructorType = AbstractInitializableBeanDefinition.MethodReference.class;
                    } else {
                        constructorType = AbstractInitializableBeanDefinition.FieldReference.class;
                    }
                    StatementDef.DefineAndAssign defineAndAssign = aThis.type()
                        .getStaticField(FIELD_CONSTRUCTOR, ClassTypeDef.of(AbstractInitializableBeanDefinition.MethodOrFieldReference.class))
                        .cast(constructorType)
                        .newLocal("constructorDef");
                    additionalStatements.add(defineAndAssign);
                    constructorDef = defineAndAssign.variable();
                }
                return constructorDef;
            }
        };
        return getConstructorArgumentValues(aThis, methodParameters, elementProducerDefinition.injectionPoints(), isParametrized, constructorDefSupplier);
    }

    private ParameterElement[] resolveConstructorParameters() {
        return switch (elementProducerDefinition) {
            case ConstructorDefinition<?, ?> ignored ->
                constructorDefinition.constructorElement().getParameters();
            case FieldDefinition<?, ?> ignored -> new ParameterElement[0];
            case MethodDefinition<?, ?> ignored ->
                factoryMethodDefinition.methodElement().getParameters();
        };
    }

    private ExpressionDef buildNewInstance(VariableDef.This aThis,
                                           List<VariableDef.MethodParameter> methodParameters,
                                           List<StatementDef> additionalStatements,
                                           List<? extends ExpressionDef> values) {
        if (customInitializerBuilder != null) {
            return customInitializerBuilder.build(additionalStatements, aThis, methodParameters, values);
        }
        return switch (elementProducerDefinition) {
            case ConstructorDefinition<?, ?> cd -> {
                ConstructorDefinition<ClassElement, MethodElement> constructorDefinition = (ConstructorDefinition<ClassElement, MethodElement>) cd;
                yield initializeBean(aThis, methodParameters, constructorDefinition, values, additionalStatements);
            }
            case FieldDefinition<?, ?> fd -> {
                FieldDefinition<ClassElement, FieldElement> fieldDefinition = (FieldDefinition<ClassElement, FieldElement>) fd;
                yield buildFactoryFieldCall(methodParameters, additionalStatements, fieldDefinition);
            }
            case MethodDefinition<?, ?> md -> {
                MethodDefinition<ClassElement, MethodElement> methodDefinition = (MethodDefinition<ClassElement, MethodElement>) md;
                yield buildFactoryMethodCall(methodParameters, methodDefinition, additionalStatements, values);
            }
        };
    }

    private ExpressionDef buildFactoryMethodCall(List<VariableDef.MethodParameter> methodParameters,
                                                 MethodDefinition<ClassElement, MethodElement> factorMethodDefinition,
                                                 List<StatementDef> additionalStatements,
                                                 List<? extends ExpressionDef> values) {
        MethodElement methodElement = factorMethodDefinition.methodElement();
        ExpressionDef factoryBean = getFactoryBean(methodParameters, methodElement.getDeclaringType(), methodElement.isStatic(), additionalStatements);
        return getBeanFromFactoryMethod(factorMethodDefinition, factoryBean, values);
    }

    private ExpressionDef buildFactoryFieldCall(List<VariableDef.MethodParameter> methodParameters,
                                                List<StatementDef> additionalStatements,
                                                FieldDefinition<ClassElement, FieldElement> factoryFieldDefinition) {
        FieldElement fieldElement = factoryFieldDefinition.fieldElement();
        ExpressionDef factoryBean = getFactoryBean(methodParameters, fieldElement.getDeclaringType(), fieldElement.isStatic(), additionalStatements);
        return getBeanFromFactoryField(factoryFieldDefinition, factoryBean);
    }

    private ExpressionDef getBeanFromFactoryMethod(MethodDefinition<ClassElement, MethodElement> factoryBuildMethodDefinition,
                                                   ExpressionDef factoryVar,
                                                   List<? extends ExpressionDef> values) {
        MethodElement methodElement = factoryBuildMethodDefinition.methodElement();
        ClassTypeDef factoryType = ClassTypeDef.of(methodElement.getDeclaringType());
        if (methodElement.isReflectionRequired()) {
            return TYPE_REFLECTION_UTILS.invokeStatic(
                METHOD_INVOKE_INACCESSIBLE_METHOD,

                methodElement.isStatic() ? ExpressionDef.nullValue() : factoryVar,
                DispatchWriter.getTypeUtilsGetRequiredMethod(factoryType, methodElement),
                TypeDef.OBJECT.array().instantiate(values)
            );
        }
        if (methodElement.isStatic()) {
            return factoryType.invokeStatic(methodElement, values);
        }
        return factoryVar.invoke(methodElement, values);
    }

    private ExpressionDef getBeanFromFactoryField(FieldDefinition<ClassElement, FieldElement> factoryBuildMethodDefinition,
                                                  ExpressionDef factoryVar) {
        FieldElement fieldElement = factoryBuildMethodDefinition.fieldElement();
        ClassTypeDef factoryType = ClassTypeDef.of(fieldElement.getDeclaringType());
        if (fieldElement.isReflectionRequired()) {
            return TYPE_REFLECTION_UTILS.invokeStatic(
                GET_FIELD_WITH_REFLECTION_METHOD,

                ExpressionDef.constant(factoryType),
                ExpressionDef.constant(fieldElement.getName()),
                fieldElement.isStatic() ? ExpressionDef.nullValue() : factoryVar
            );
        }
        if (fieldElement.isStatic()) {
            return factoryType.getStaticField(fieldElement.getName(), beanTypeDef);
        }
        return factoryVar.field(fieldElement.getName(), beanTypeDef);
    }

    private ExpressionDef initializeBean(VariableDef.This aThis,
                                         List<? extends ExpressionDef> methodParameters,
                                         ConstructorDefinition<ClassElement, MethodElement> constructorDefinition,
                                         List<? extends ExpressionDef> values,
                                         List<StatementDef> additionalStatements) {
        MethodElement constructor = constructorDefinition.constructorElement();
        if (interceptedType == null && MethodGenUtils.hasKotlinDefaultsParameters(List.of(constructor.getParameters()))) {
            // NOTE: Proxies will handle the default constructor call
            List<ExpressionDef> variables = new ArrayList<>(values.size());
            List<TypeDef> types = new ArrayList<>(values.size());
            int k = 0;
            for (ExpressionDef value : values) {
                TypeDef type = value.type();
                value = value.cast(TypeDef.OBJECT); // Avoid casting objects before null checks
                types.add(type);
                StatementDef.DefineAndAssign defineAndAssign = value.newLocal("p" + k++);
                additionalStatements.add(defineAndAssign);
                variables.add(defineAndAssign.variable());
            }
            values = variables;

            List<ExpressionDef> hasValuesExpressions = new ArrayList<>();
            for (int i = 0; i < values.size(); i++) {
                ExpressionDef value = values.get(i);
                BeanDefinitionInjectionPoint<ClassElement> parameter = constructorDefinition.injectionPoints().get(i);
                if (parameter.getAnnotationMetadata().hasAnnotation(Property.class)) {
                    hasValuesExpressions.add(
                        getContainsPropertyCheck(aThis, methodParameters, parameter.type(), parameter.getAnnotationMetadata())
                    );
                } else {
                    hasValuesExpressions.add(value.isNonNull());
                }
            }
            List<ExpressionDef> newValues = new ArrayList<>(values.size());
            for (int i = 0; i < values.size(); i++) {
                newValues.add(values.get(i).cast(types.get(i)));
            }
            values = newValues;
            return MethodGenUtils.invokeBeanConstructor(constructor, constructorDefinition.requiresReflection(), true, values, hasValuesExpressions, additionalStatements);
        }
        return MethodGenUtils.invokeBeanConstructor(constructor, constructorDefinition.requiresReflection(), false, values, null, additionalStatements);
    }

    private ExpressionDef getContainsPropertyCheck(VariableDef.This aThis,
                                                   List<? extends ExpressionDef> methodParameters,
                                                   ClassElement type,
                                                   AnnotationMetadata annotationMetadata) {
        String propertyName = findProperty(annotationMetadata).orElseThrow();

        return aThis.invoke(
            isMultiValueProperty(type) ? CONTAINS_PROPERTIES_VALUE_METHOD : CONTAINS_PROPERTY_VALUE_METHOD,

            methodParameters.get(0),
            methodParameters.get(1),
            ExpressionDef.constant(propertyName)
        );
    }

    private static Optional<String> findProperty(AnnotationMetadata annotationMetadata) {
        return annotationMetadata.stringValue(Property.class, "name");
    }

    private ExpressionDef getFactoryBean(List<VariableDef.MethodParameter> parameters,
                                         ClassElement factoryClass,
                                         boolean isStaticMember,
                                         List<StatementDef> additionalStatements) {
        if (isStaticMember) {
            return ExpressionDef.nullValue();
        }

        // for Factory beans first we need to look up the factory bean
        // before invoking the method to instantiate
        // the below code looks up the factory bean.

        TypeDef factoryTypeDef = TypeDef.erasure(factoryClass);

        ExpressionDef argumentExpression = ClassTypeDef.of(Argument.class).invokeStatic(ArgumentExpUtils.METHOD_CREATE_ARGUMENT_SIMPLE,
            ExpressionDef.constant(factoryTypeDef),
            ExpressionDef.constant("factory")
        );

        ExpressionDef beanResolutionContxt = parameters.getFirst();
        StatementDef.DefineAndAssign defineAndAssign = beanResolutionContxt
            .invoke(BEAN_LOCATOR_METHOD_GET_BEAN,
                // first argument is the bean type
                ExpressionDef.constant(factoryTypeDef),
                // second argument is the qualifier for the factory if any
                getQualifier(factoryClass, argumentExpression)
            ).cast(factoryTypeDef).newLocal("factoryBean");
        additionalStatements.add(defineAndAssign);
        additionalStatements.add(beanResolutionContxt.invoke(METHOD_BEAN_RESOLUTION_CONTEXT_MARK_FACTORY));
        return defineAndAssign.variable();
    }

    private StaticBlock getStaticInitializer() {
        List<StatementDef> statements = new ArrayList<>();

        FieldDef annotationMetadataField = AnnotationMetadataGenUtils.createAnnotationMetadataFieldAndInitialize(annotationMetadata, loadClassValueExpressionFn);

        classDefBuilder.addField(annotationMetadataField);

        FieldDef failedInitializationField = FieldDef.builder(FIELD_FAILED_INITIALIZATION, Throwable.class)
            .addModifiers(Modifier.PRIVATE, Modifier.FINAL, Modifier.STATIC)
            .build();

        classDefBuilder.addField(failedInitializationField);

        List<StatementDef> initStatements = new ArrayList<>();
        List<StatementDef> failStatements = new ArrayList<>();

        FieldDef constructorRefField = FieldDef.builder(FIELD_CONSTRUCTOR, AbstractInitializableBeanDefinition.MethodOrFieldReference.class)
            .addModifiers(Modifier.PRIVATE, Modifier.FINAL, Modifier.STATIC)
            .build();

        classDefBuilder.addField(constructorRefField);
        initStatements.add(beanDefinitionTypeDef.getStaticField(constructorRefField).put(getConstructorRef()));

        FieldDef injectionMethodsField = null;
        FieldDef injectionFieldsField = null;
        FieldDef annotationInjectionsFieldType = null;
        FieldDef typeArgumentsField = null;
        FieldDef executableMethodsField = null;

        boolean hasMethodInjection = !superBeanDefinition && !allMethods.isEmpty();
        if (hasMethodInjection) {

            TypeDef.Array methodReferenceArray = ClassTypeDef.of(AbstractInitializableBeanDefinition.MethodReference.class).array();
            injectionMethodsField = FieldDef.builder(FIELD_INJECTION_METHODS, methodReferenceArray)
                .addModifiers(Modifier.PRIVATE, Modifier.FINAL, Modifier.STATIC)
                .build();

            classDefBuilder.addField(injectionMethodsField);
            initStatements.add(beanDefinitionTypeDef.getStaticField(injectionMethodsField)
                .put(methodReferenceArray.instantiate(allMethods.stream()
                    .map(md -> getNewMethodReference(md.methodElement().getDeclaringType(), md.methodElement(), md.annotationMetadata(), postConstructMethods.contains(md), preDestroyMethods.contains(md)))
                    .toList())));
            failStatements.add(beanDefinitionTypeDef.getStaticField(injectionMethodsField).put(ExpressionDef.nullValue()));
        }
        boolean hasFieldInjection = !allFields.isEmpty();
        if (hasFieldInjection) {

            TypeDef.Array fieldReferenceArray = ClassTypeDef.of(AbstractInitializableBeanDefinition.FieldReference.class).array();
            injectionFieldsField = FieldDef.builder(FIELD_INJECTION_FIELDS, fieldReferenceArray)
                .addModifiers(Modifier.PRIVATE, Modifier.FINAL, Modifier.STATIC)
                .build();
            classDefBuilder.addField(injectionFieldsField);

            initStatements.add(beanDefinitionTypeDef.getStaticField(injectionFieldsField)
                .put(fieldReferenceArray.instantiate(allFields.stream()
                    .map(fd -> getNewFieldReference(fd.fieldElement().getDeclaringType(), fd.fieldElement()))
                    .toList())));
            failStatements.add(beanDefinitionTypeDef.getStaticField(injectionFieldsField).put(ExpressionDef.nullValue()));
        }

        boolean hasAnnotationInjection = !annotationInjectionPoints.isEmpty();
        if (hasAnnotationInjection) {
            TypeDef.Array annotationInjectionsFieldArray = ClassTypeDef.of(AbstractInitializableBeanDefinition.AnnotationReference.class).array();
            annotationInjectionsFieldType = FieldDef.builder(FIELD_ANNOTATION_INJECTIONS, annotationInjectionsFieldArray)
                .addModifiers(Modifier.PRIVATE, Modifier.FINAL, Modifier.STATIC)
                .build();
            classDefBuilder.addField(annotationInjectionsFieldType);

            initStatements.add(beanDefinitionTypeDef.getStaticField(annotationInjectionsFieldType)
                .put(annotationInjectionsFieldArray.instantiate(annotationInjectionPoints.keySet().stream()
                    .map(this::getNewAnnotationReference)
                    .toList())));
            failStatements.add(beanDefinitionTypeDef.getStaticField(annotationInjectionsFieldType).put(ExpressionDef.nullValue()));
        }

        boolean hasTypeArguments = !superBeanDefinition && hasTypeArguments();
        if (hasTypeArguments) {
            typeArgumentsField = FieldDef.builder(FIELD_TYPE_ARGUMENTS, Map.class)
                .addModifiers(Modifier.PRIVATE, Modifier.FINAL, Modifier.STATIC)
                .build();
            classDefBuilder.addField(typeArgumentsField);

            initStatements.add(beanDefinitionTypeDef.getStaticField(typeArgumentsField)
                .put(GenUtils.stringMapOf(
                    typeArguments, true, null, el -> ArgumentExpUtils.pushTypeArgumentElements(
                        annotationMetadataDefaults,
                        beanDefinitionTypeDef,
                        ClassElement.of(beanDefinitionName),
                        el,
                        loadClassValueExpressionFn
                    ))
                ));
            failStatements.add(beanDefinitionTypeDef.getStaticField(typeArgumentsField).put(ExpressionDef.nullValue()));
        }

        boolean hasExecutableMethods = executableMethodsDefinitionWriter != null;
        if (hasExecutableMethods) {
            ClassTypeDef execType = executableMethodsDefinitionWriter.getClassTypeDef();

            executableMethodsField = FieldDef.builder(FIELD_EXECUTABLE_METHODS, execType)
                .addModifiers(Modifier.PRIVATE, Modifier.FINAL, Modifier.STATIC)
                .build();
            classDefBuilder.addField(executableMethodsField);

            initStatements.add(beanDefinitionTypeDef.getStaticField(executableMethodsField).put(execType.instantiate()));
            failStatements.add(beanDefinitionTypeDef.getStaticField(executableMethodsField).put(ExpressionDef.nullValue()));
        }

        ClassTypeDef precalculatedInfoType = ClassTypeDef.of(AbstractInitializableBeanDefinition.PrecalculatedInfo.class);
        FieldDef precalculatedInfoField = FieldDef.builder(FIELD_PRECALCULATED_INFO, precalculatedInfoType)
            .addModifiers(Modifier.PRIVATE, Modifier.FINAL, Modifier.STATIC)
            .build();

        classDefBuilder.addField(precalculatedInfoField);
        String scope = annotationMetadata.getAnnotationNameByStereotype(AnnotationUtil.SCOPE).orElse(null);

        AnnotationMetadata declaredAnnotationMetadata;
        if (beanProducingElement instanceof MethodElement methodElement) {
            declaredAnnotationMetadata = methodElement.getMethodAnnotationMetadata();
        } else {
            declaredAnnotationMetadata = annotationMetadata;
        }

        List<AnnotationValue<Indexed>> indexes = declaredAnnotationMetadata.getAnnotationValuesByType(Indexed.class);
        if (!indexes.isEmpty()) {
            TypeDef.Array arrayOfClasses = TypeDef.Primitive.CLASS.array();
            FieldDef indexesField = FieldDef.builder("$INDEXES")
                .ofType(arrayOfClasses)
                .addModifiers(Modifier.PRIVATE, Modifier.FINAL, Modifier.STATIC)
                .build();
            initStatements.add(
                beanDefinitionTypeDef.getStaticField(indexesField).put(
                    arrayOfClasses.instantiate(
                        indexes.stream().map(av -> asClassExpression(av.stringValue().orElseThrow())).toArray(ExpressionDef[]::new)
                    )
                )
            );

            classDefBuilder.addField(indexesField);
            classDefBuilder.addMethod(
                MethodDef.override(GET_INDEXES_METHOD).build((aThis, methodParameters) -> aThis.type().getStaticField(indexesField).returning())
            );

            failStatements.add(beanDefinitionTypeDef.getStaticField(indexesField).put(arrayOfClasses.instantiate()));
        }

        statements.add(
            StatementDef.doTry(
                StatementDef.multi(
                    initStatements
                )
            ).doCatch(Throwable.class, exceptionVar -> StatementDef.multi(
                beanDefinitionTypeDef.getStaticField(failedInitializationField).put(exceptionVar),
                StatementDef.multi(failStatements)
            ))
        );

        statements.add(
            beanDefinitionTypeDef.getStaticField(precalculatedInfoField)
                .put(
                    precalculatedInfoType.instantiate(
                        PRECALCULATED_INFO_CONSTRUCTOR,

                        // 1: `Optional` scope
                        scope == null ? TYPE_OPTIONAL.invokeStatic(METHOD_OPTIONAL_EMPTY)
                            : TYPE_OPTIONAL.invokeStatic(METHOD_OPTIONAL_OF, ExpressionDef.constant(scope)),
                        // 2: `boolean` isAbstract
                        ExpressionDef.constant(isAbstract),
                        // 3: `boolean` isIterable
                        ExpressionDef.constant(isIterable(annotationMetadata)),
                        // 4: `boolean` isSingleton
                        ExpressionDef.constant(isSingleton(scope)),
                        // 5: `boolean` isPrimary
                        ExpressionDef.constant(annotationMetadata.hasDeclaredStereotype(Primary.class)),
                        // 6: `boolean` isConfigurationProperties
                        ExpressionDef.constant(isConfigurationProperties),
                        // 7: isContainerType
                        ExpressionDef.constant(isContainerType()),
                        // 8: preprocessMethods
                        hasExecutableMethods ?
                            beanDefinitionTypeDef.getStaticField(executableMethodsField)
                                .isNull().doIfElse(
                                    ExpressionDef.constant(false),
                                    beanDefinitionTypeDef.getStaticField(executableMethodsField).invoke(ExecutableMethodsDefinitionWriter.REQUIRES_METHOD_PROCESSING_METHOD)
                                )
                            : ExpressionDef.constant(false),
                        // 9: hasEvaluatedExpressions
                        ExpressionDef.constant(evaluatedExpressionProcessor.hasEvaluatedExpressions())

                    )
                )
        );

        statements.add(addInnerConfigurationMethod());
        statements.add(addGetExposedTypes());
        statements.add(addReplacesDefinition());

        FieldDef preStartConditionsField = null;
        FieldDef postStartConditionsField = null;

        List<AnnotationValue<Requires>> requirements = annotationMetadata.getAnnotationValuesByType(Requires.class);
        if (!requirements.isEmpty()) {
            TypeDef.Array conditionsArrayType = ClassTypeDef.of(Condition.class).array();
            preStartConditionsField = FieldDef.builder(FIELD_PRE_START_CONDITIONS, conditionsArrayType)
                .addModifiers(Modifier.PRIVATE, Modifier.FINAL, Modifier.STATIC)
                .build();
            postStartConditionsField = FieldDef.builder(FIELD_POST_START_CONDITIONS, conditionsArrayType)
                .addModifiers(Modifier.PRIVATE, Modifier.FINAL, Modifier.STATIC)
                .build();

            classDefBuilder.addField(preStartConditionsField);
            classDefBuilder.addField(postStartConditionsField);

            statements.add(addConditions(requirements, preStartConditionsField, postStartConditionsField));
        }

        // Defaults can be contributed by other static initializers, it should be at the end
        MutableAnnotationMetadata.contributeDefaults(annotationMetadataDefaults, annotationMetadata);
        AnnotationMetadataGenUtils.addAnnotationDefaults(statements, annotationMetadataDefaults, loadClassValueExpressionFn);

        return new StaticBlock(
            StatementDef.multi(statements),
            annotationMetadataField,
            failedInitializationField,
            constructorRefField,
            injectionMethodsField,
            injectionFieldsField,
            annotationInjectionsFieldType,
            typeArgumentsField,
            executableMethodsField,
            precalculatedInfoField,
            preStartConditionsField,
            postStartConditionsField
        );
    }

    private ExpressionDef getConstructorRef() {
        if (factoryFieldDefinition != null) {
            return getNewFieldReference(factoryFieldDefinition.fieldElement().getDeclaringType(), factoryFieldDefinition.fieldElement());
        }
        MethodElement methodElement;
        if (factoryMethodDefinition != null) {
            methodElement = factoryMethodDefinition.methodElement();
        } else if (constructorDefinition != null) {
            methodElement = constructorDefinition.constructorElement();
        } else {
            throw new IllegalArgumentException("Unexpected constructor: " + elementProducerDefinition);
        }
        ParameterElement[] parameters = methodElement.getParameters();
        List<ParameterElement> parameterList = Arrays.asList(parameters);
        applyDefaultNamedToParameters(parameterList);

        return getNewMethodReference(methodElement.getDeclaringType(), methodElement, methodElement.getAnnotationMetadata(), false, false);
    }

    private StatementDef addConditions(List<AnnotationValue<Requires>> requirements, FieldDef preStartConditionsField, FieldDef postStartConditionsField) {
        List<Condition> preConditions = new ArrayList<>();
        List<Condition> postConditions = new ArrayList<>();
        if (requirements.isEmpty()) {
            return StatementDef.multi();
        }
        List<AnnotationValue<Requires>> dynamicRequirements = new ArrayList<>();
        for (AnnotationValue<Requires> requirement : requirements) {
            if (requirement.getValues().values().stream().anyMatch(value -> value instanceof EvaluatedExpressionReference)) {
                dynamicRequirements.add(requirement);
                continue;
            }
            MatchesConditionUtils.createConditions(requirement, preConditions, postConditions);
        }
        if (!dynamicRequirements.isEmpty()) {
            MutableAnnotationMetadata annotationMetadata = new MutableAnnotationMetadata();
            for (AnnotationValue<Requires> requirement : requirements) {
                annotationMetadata.addRepeatable(Requirements.class.getName(), requirement);
            }
            postConditions.add(new MatchesDynamicCondition(annotationMetadata));
        }

        Function<Condition, ExpressionDef> writer = new Function<>() {
            @Override
            public ExpressionDef apply(Condition condition) {
                if (condition instanceof MatchesPropertyCondition matchesPropertyCondition) {
                    return newRecord(
                        matchesPropertyCondition.getClass(),
                        ExpressionDef.constant(matchesPropertyCondition.property()),
                        ExpressionDef.constant(matchesPropertyCondition.value()),
                        ExpressionDef.constant(matchesPropertyCondition.defaultValue()),
                        ExpressionDef.constant(matchesPropertyCondition.condition())
                    );
                } else if (condition instanceof MatchesAbsenceOfBeansCondition matchesAbsenceOfBeansCondition) {
                    return newRecord(
                        matchesAbsenceOfBeansCondition.getClass(),
                        getAnnotationClassValues(matchesAbsenceOfBeansCondition.missingBeans())
                    );
                } else if (condition instanceof MatchesPresenceOfBeansCondition matchesPresenceOfBeansCondition) {
                    return newRecord(
                        matchesPresenceOfBeansCondition.getClass(),
                        getAnnotationClassValues(matchesPresenceOfBeansCondition.beans())
                    );
                } else if (condition instanceof MatchesAbsenceOfClassesCondition matchesAbsenceOfClassesCondition) {
                    return newRecord(
                        matchesAbsenceOfClassesCondition.getClass(),
                        getAnnotationClassValues(matchesAbsenceOfClassesCondition.classes())
                    );
                } else if (condition instanceof MatchesPresenceOfClassesCondition matchesPresenceOfClassesCondition) {
                    return newRecord(
                        matchesPresenceOfClassesCondition.getClass(),
                        getAnnotationClassValues(matchesPresenceOfClassesCondition.classes())
                    );
                } else if (condition instanceof MatchesPresenceOfEntitiesCondition matchesPresenceOfEntitiesCondition) {
                    return newRecord(
                        matchesPresenceOfEntitiesCondition.getClass(),
                        getAnnotationClassValues(matchesPresenceOfEntitiesCondition.classes())
                    );
                } else if (condition instanceof MatchesAbsenceOfClassNamesCondition matchesAbsenceOfClassNamesCondition) {
                    return newRecord(
                        matchesAbsenceOfClassNamesCondition.getClass(),
                        ExpressionDef.constant(matchesAbsenceOfClassNamesCondition.classes())
                    );
                } else if (condition instanceof MatchesConfigurationCondition matchesConfigurationCondition) {
                    return newRecord(
                        matchesConfigurationCondition.getClass(),
                        ExpressionDef.constant(matchesConfigurationCondition.configurationName()),
                        ExpressionDef.constant(matchesConfigurationCondition.minimumVersion())
                    );
                } else if (condition instanceof MatchesCurrentNotOsCondition matchesCurrentNotOsCondition) {
                    return newRecord(
                        matchesCurrentNotOsCondition.getClass(),
                        ClassTypeDef.of(CollectionUtils.class)
                            .invokeStatic(
                                COLLECTION_UTILS_ENUM_SET_METHOD,

                                ClassTypeDef.of(Requires.Family.class).array().instantiate(
                                    matchesCurrentNotOsCondition.notOs().stream().map(ExpressionDef::constant).toList()
                                )
                            )
                    );
                } else if (condition instanceof MatchesCurrentOsCondition currentOsCondition) {
                    return newRecord(
                        currentOsCondition.getClass(),
                        ClassTypeDef.of(CollectionUtils.class)
                            .invokeStatic(
                                COLLECTION_UTILS_ENUM_SET_METHOD,

                                ClassTypeDef.of(Requires.Family.class).array().instantiate(
                                    currentOsCondition.os().stream().map(ExpressionDef::constant).toList()
                                )
                            )
                    );
                } else if (condition instanceof MatchesCustomCondition matchesCustomCondition) {
                    return newRecord(
                        matchesCustomCondition.getClass(),
                        getAnnotationClassValue(matchesCustomCondition.customConditionClass())
                    );
                } else if (condition instanceof MatchesEnvironmentCondition matchesEnvironmentCondition) {
                    return newRecord(
                        matchesEnvironmentCondition.getClass(),
                        ExpressionDef.constant(matchesEnvironmentCondition.env())
                    );
                } else if (condition instanceof MatchesMissingPropertyCondition matchesMissingPropertyCondition) {
                    return newRecord(
                        matchesMissingPropertyCondition.getClass(),
                        ExpressionDef.constant(matchesMissingPropertyCondition.property())
                    );
                } else if (condition instanceof MatchesNotEnvironmentCondition matchesNotEnvironmentCondition) {
                    return newRecord(
                        matchesNotEnvironmentCondition.getClass(),
                        ExpressionDef.constant(matchesNotEnvironmentCondition.env())
                    );
                } else if (condition instanceof MatchesPresenceOfResourcesCondition matchesPresenceOfResourcesCondition) {
                    return newRecord(
                        matchesPresenceOfResourcesCondition.getClass(),
                        ExpressionDef.constant(matchesPresenceOfResourcesCondition.resourcePaths())
                    );
                } else if (condition instanceof MatchesSdkCondition matchesSdkCondition) {
                    return newRecord(
                        matchesSdkCondition.getClass(),
                        ExpressionDef.constant(matchesSdkCondition.sdk()),
                        ExpressionDef.constant(matchesSdkCondition.version())
                    );
                } else if (condition instanceof MatchesDynamicCondition matchesDynamicCondition) {
                    return newRecord(
                        matchesDynamicCondition.getClass(),
                        getAnnotationMetadataExpression(matchesDynamicCondition.annotationMetadata())
                    );
                } else {
                    throw new IllegalStateException("Unsupported condition type: " + condition.getClass().getName());
                }
            }

            private ExpressionDef getAnnotationClassValues(AnnotationClassValue<?>[] classValues) {
                return ClassTypeDef.of(AnnotationClassValue.class)
                    .array()
                    .instantiate(Arrays.stream(classValues).map(this::getAnnotationClassValue).toList());
            }

            private ExpressionDef getAnnotationClassValue(AnnotationClassValue<?> annotationClassValue) {
                return loadClassValueExpressionFn.apply(annotationClassValue.getName());
            }

            private ExpressionDef newRecord(Class<?> classType, ExpressionDef... values) {
                return ClassTypeDef.of(classType).instantiate(classType.getConstructors()[0], values);
            }
        };
        TypeDef.Array conditionsArrayType = ClassTypeDef.of(Condition.class).array();
        return StatementDef.multi(
            beanDefinitionTypeDef.getStaticField(preStartConditionsField).put(
                conditionsArrayType.instantiate(preConditions.stream().map(writer).toList())
            ),
            beanDefinitionTypeDef.getStaticField(postStartConditionsField).put(
                conditionsArrayType.instantiate(postConditions.stream().map(writer).toList())
            )
        );
    }

    private void processAllBeanElementVisitors() {
        for (BeanElementVisitor<?> visitor : VISITORS) {
            if (visitor.isEnabled() && visitor.supports(this)) {
                try {
                    this.disabled = visitor.visitBeanElement(this, visitorContext) == null;
                    if (disabled) {
                        break;
                    }
                } catch (Exception e) {
                    visitorContext.fail(
                        "Error occurred visiting BeanElementVisitor of type [" + visitor.getClass().getName() + "]: " + e.getMessage(),
                        this
                    );
                    break;
                }
            }
        }
    }

    private StatementDef addInnerConfigurationMethod() {
        if (isConfigurationProperties && !beanTypeInnerClasses.isEmpty()) {
            FieldDef innerClassesField = FieldDef.builder(FIELD_INNER_CLASSES, Set.class)
                .addModifiers(Modifier.PRIVATE, Modifier.FINAL, Modifier.STATIC)
                .build();

            classDefBuilder.addField(innerClassesField);

            classDefBuilder.addMethod(
                MethodDef.override(IS_INNER_CONFIGURATION_METHOD)
                    .build((aThis, methodParameters) -> aThis.type().getStaticField(innerClassesField)
                        .invoke(CONTAINS_METHOD, methodParameters.get(0))
                        .returning())
            );

            return beanDefinitionTypeDef.getStaticField(innerClassesField).put(
                getClassesAsSetExpression(beanTypeInnerClasses.toArray(EMPTY_STRING_ARRAY))
            );
        }
        return StatementDef.multi();
    }

    private StatementDef addGetExposedTypes() {
        AnnotationMetadata producingAnnotationMetadata;
        if (beanProducingElement instanceof MethodElement methodElement) {
            producingAnnotationMetadata = methodElement.getMethodAnnotationMetadata();
        } else {
            producingAnnotationMetadata = annotationMetadata;
        }
        String[] exposedTypes = producingAnnotationMetadata.stringValues(Bean.class.getName(), "typed");
        Set<String> exposedTypeNames;
        if (exposedTypes.length != 0) {
            exposedTypeNames = Set.of(exposedTypes);
        } else {
            exposedTypeNames = new LinkedHashSet<>();
            if (interceptedType != null) {
                collectExposedTypes(exposedTypeNames, visitorContext.getClassElement(interceptedType).orElseThrow(() -> new IllegalStateException("Intercepted type not found: " + interceptedType)));
                exposedTypeNames.add(beanProducingElement.getName()); // Allow finding the proxy by it's name
            } else if (exposes != null) {
                exposes.forEach(name -> exposedTypeNames.add(name.getName()));
            } else if (isContainerType()) {
                if (beanTypeElement.isArray()) {
                    collectExposedTypes(exposedTypeNames, beanTypeElement.fromArray());
                } else {
                    collectExposedTypes(exposedTypeNames, beanTypeElement.getFirstTypeArgument()
                        .orElseThrow(() -> new IllegalStateException("No type argument found for array type: " + beanTypeElement.getType())));
                }
                collectExposedTypes(exposedTypeNames, beanTypeElement);
            } else {
                collectExposedTypes(exposedTypeNames, beanTypeElement);
            }
        }
        if (exposedTypeNames.isEmpty()) {
            // This should never happen
            return StatementDef.multi();
        }
        FieldDef exposedTypesField = FieldDef.builder(FIELD_EXPOSED_TYPES, TypeDef.parameterized(Set.class, TypeDef.Primitive.CLASS))
            .addModifiers(Modifier.PRIVATE, Modifier.FINAL, Modifier.STATIC)
            .build();

        List<StatementDef> statements = new ArrayList<>();

        classDefBuilder.addField(exposedTypesField);

        VariableDef.StaticField staticFieldExposes = beanDefinitionTypeDef.getStaticField(exposedTypesField);
        statements.add(StatementDef.doTry(
                staticFieldExposes.put(getClassesAsSetExpression(exposedTypeNames))
            ).doCatch(Throwable.class,
                exceptionVar -> staticFieldExposes.put(GenUtils.setOf(List.of())))
        );

        classDefBuilder.addMethod(
            MethodDef.override(GET_EXPOSED_TYPES_METHOD)
                .build((aThis, methodParameters) ->
                    aThis.type().getStaticField(exposedTypesField).returning())
        );

        if (!hasTypeArguments() && !isContainerType()) {

            classDefBuilder.addMethod(
                MethodDef.override(IS_CANDIDATE_BEAN_METHOD)
                    .build((aThis, methodParameters) -> {
                            if (exposedTypes.length != 0) { // User-defined exposed types
                                if (exposedTypeNames.size() == 1) {
                                    return methodParameters.get(0).newLocal("type", variableDef ->
                                        variableDef.isNonNull()
                                            .and(
                                                ArgumentExpUtils.getTypeExp(variableDef).equalsReferentially(
                                                    ExpressionDef.constant(TypeDef.of(exposedTypeNames.iterator().next()))
                                                )
                                            )
                                            .returning()
                                    );
                                } else {
                                    return methodParameters.get(0).newLocal("type", variableDef ->
                                        variableDef.isNonNull().and(
                                            staticFieldExposes.invoke(CONTAINS_METHOD, ArgumentExpUtils.getTypeExp(variableDef)).isTrue()
                                        ).returning()
                                    );
                                }
                            } else {
                                return ArgumentExpUtils.getTypeExp(methodParameters.get(0))
                                    .invoke(IS_ASSIGNABLE_METHOD, ExpressionDef.constant(beanTypeDef))
                                    .returning();
                            }
                        }
                    )
            );
        }
        return StatementDef.multi(statements);
    }

    private StatementDef addReplacesDefinition() {
        AnnotationMetadata producingAnnotationMetadata = annotationMetadata;
        AnnotationValue<Replaces> replacesAnnotationValue = producingAnnotationMetadata.getAnnotation(Replaces.class);
        if (replacesAnnotationValue == null) {
            classDefBuilder.addMethod(
                MethodDef.override(METHOD_GET_REPLACES_DEFINITION)
                    .build((aThis, methodParameters) ->
                        ExpressionDef.nullValue().returning())
            );
            return StatementDef.multi();
        }
        TypeDef replacesType = TypeDef.of(ReplacesDefinition.class);
        FieldDef replacesField = FieldDef.builder(FIELD_REPLACES, replacesType)
            .addModifiers(Modifier.PRIVATE, Modifier.FINAL, Modifier.STATIC)
            .build();

        List<StatementDef> statements = new ArrayList<>();

        classDefBuilder.addField(replacesField);

        AnnotationClassValue<?> replacesBean = replacesAnnotationValue.annotationClassValue(Replaces.MEMBER_BEAN).orElse(null);
        String named = replacesAnnotationValue.stringValue(Replaces.MEMBER_NAMED).orElse(null);
        AnnotationClassValue<?> qualifier = replacesAnnotationValue.annotationClassValue(Replaces.MEMBER_QUALIFIER).orElse(null);
        AnnotationClassValue<?> replacesFactory = replacesAnnotationValue.annotationClassValue(Replaces.MEMBER_FACTORY).orElse(null);

        if (named != null && qualifier != null) {
            throw new ProcessingException(beanProducingElement, "Both \"named\" and \"qualifier\" should not be present");
        }

        ExpressionDef qualifierExpression;
        if (named != null) {
            qualifierExpression = TYPE_QUALIFIERS.invokeStatic(METHOD_QUALIFIER_BY_NAME, ExpressionDef.constant(named));
        } else if (qualifier != null) {
            qualifierExpression = TYPE_QUALIFIERS.invokeStatic(METHOD_QUALIFIER_BY_STEREOTYPE, ExpressionDef.constant(TypeDef.of(qualifier.getName())));
        } else {
            qualifierExpression = ExpressionDef.nullValue();
        }

        VariableDef.StaticField staticFieldReplaces = beanDefinitionTypeDef.getStaticField(replacesField);
        statements.add(StatementDef.doTry(
                staticFieldReplaces.put(
                    ClassTypeDef.of(DefaultReplacesDefinition.class)
                        .instantiate(CONSTRUCTOR_DEFAULT_REPLACES_DEFINITION,
                            ExpressionDef.constant(beanTypeDef),
                            replacesBean == null ? ExpressionDef.nullValue() : ExpressionDef.constant(TypeDef.of(replacesBean.getName())),
                            qualifierExpression,
                            replacesFactory == null ? ExpressionDef.nullValue() : ExpressionDef.constant(TypeDef.of(replacesFactory.getName()))
                        )
                )
            ).doCatch(Throwable.class,
                exceptionVar -> staticFieldReplaces.put(ExpressionDef.nullValue()))
        );

        classDefBuilder.addMethod(
            MethodDef.override(METHOD_GET_REPLACES_DEFINITION)
                .build((aThis, methodParameters) ->
                    aThis.type().getStaticField(replacesField).returning())
        );
        return StatementDef.multi(statements);
    }

    private void collectExposedTypes(Set<String> exposedTypeNames, ClassElement element) {
        String className = getClassName(element);
        if (!exposedTypeNames.add(className) || IGNORED_EXPOSED_INTERFACES.contains(className)) {
            return;
        }
        element.getSuperType().ifPresent(superType -> collectExposedTypes(exposedTypeNames, superType));
        element.getInterfaces().forEach(iface -> collectExposedTypes(exposedTypeNames, iface));
    }

    private String getClassName(ClassElement element) {
        if (element.isArray()) {
            return getClassName(element.fromArray()) + "[]";
        }
        return element.getName();
    }

    @Nullable
    private MethodDef getGetOrder() {
        int order = OrderUtil.getOrder(annotationMetadata);
        if (order != 0) {
            return MethodDef.override(GET_ORDER_METHOD)
                .build((aThis, methodParameters) -> TypeDef.Primitive.INT.constant(order).returning());
        }
        return null;
    }

    private ExpressionDef getClassesAsSetExpression(String[] classes) {
        if (classes.length > 1) {
            return ClassTypeDef.of(HashSet.class)
                .instantiate(
                    HASH_SET_COLLECTION_CONSTRUCTOR,

                    ClassTypeDef.of(Arrays.class)
                        .invokeStatic(
                            ARRAYS_AS_LIST_METHOD,

                            getArrayOfClasses(classes)
                        )

                );
        }
        return ClassTypeDef.of(Collections.class)
            .invokeStatic(
                COLLECTIONS_SINGLETON_METHOD,

                asClassExpression(classes[0])
            );
    }

    private ExpressionDef getClassesAsSetExpression(Collection<String> classes) {
        return GenUtils.setOf(classes.stream().<ExpressionDef>map(this::asClassExpression).toList());
    }

    private boolean hasTypeArguments() {
        return typeArguments != null && !typeArguments.isEmpty() && typeArguments.entrySet().stream().anyMatch(e -> !e.getValue().isEmpty());
    }

    private boolean isSingleton(String scope) {
        if (beanProducingElement instanceof FieldElement && beanProducingElement.isFinal()) {
            // final fields can't change so effectively singleton
            return true;
        }

        if (scope != null) {
            return scope.equals(Singleton.class.getName()) || scope.equals(Context.class.getName());
        } else {
            final AnnotationMetadata annotationMetadata;
            if (beanProducingElement instanceof ClassElement) {
                annotationMetadata = getAnnotationMetadata();
            } else {
                annotationMetadata = beanProducingElement.getDeclaredMetadata();
            }

            return annotationMetadata.stringValue(DefaultScope.class)
                .map(t -> t.equals(Singleton.class.getName()) || t.equals(Context.class.getName()))
                .orElse(false);
        }
    }

    private boolean isPostConstructIntercepted() {
        return isInterceptedLifeCycleByType(this.annotationMetadata, "POST_CONSTRUCT");
    }

    private static MethodDefinition<ClassElement, MethodElement> createMethodDefinition(ClassElement beanType,
                                                                                        MethodElement methodElement,
                                                                                        AnnotationMetadata annotationMetadata,
                                                                                        boolean requiresReflection,
                                                                                        VisitorContext visitorContext) {
        return new MethodDefinition<>(
            methodElement,
            annotationMetadata,
            Arrays.stream(methodElement.getSuspendParameters()).map(p -> BeanInjectionUtils.getInjectionPoint(beanType, p.getGenericType(), p, p.getName(), visitorContext)).toList(),
            requiresReflection
        );
    }

    private boolean isPreDestroyIntercepted() {
        return isInterceptedLifeCycleByType(this.annotationMetadata, "PRE_DESTROY");
    }

    @Override
    public BeanDefinitionWriter addExecutableMethod(MethodElement methodElement, boolean requiresReflection) {
        getExecutableMethodsWriter().addExecutableMethod(methodElement.getDeclaringType(), methodElement);
        return this;
    }

    @Override
    public String toString() {
        return "BeanDefinitionWriter{" +
            "beanFullClassName='" + beanFullClassName + '\'' +
            '}';
    }

    @Override
    public AnnotationMetadata getAnnotationMetadata() {
        return annotationMetadata;
    }

    private StatementDef injectField(InjectMethodSignature injectMethodSignature,
                                     FieldDefinition<ClassElement, FieldElement> fieldDefinition) {

        boolean isRequired = fieldDefinition.annotationMetadata()
            .booleanValue(AnnotationUtil.INJECT, AnnotationUtil.MEMBER_REQUIRED)
            .orElse(true);
        FieldElement fieldElement = fieldDefinition.fieldElement();
        ClassElement genericType = fieldElement.getGenericType();
        boolean isArray = genericType.isArray();

        int fieldIndex = allFields.indexOf(fieldDefinition);

        ExpressionDef valueExpression = getValueBypassingBeanContext(fieldElement.getGenericField(), injectMethodSignature.methodParameters);
        if (valueExpression == null) {
            valueExpression = switch (fieldDefinition.injectionPoint()) {
                case BeanRegistrationInjectionPoint<ClassElement> ignore ->
                    resolveFieldValue(injectMethodSignature, fieldElement, GET_BEAN_REGISTRATION_FOR_FIELD, isArray, true, fieldIndex);

                case BeanRegistrationsInjectionPoint<ClassElement> ignore ->
                    resolveFieldValue(injectMethodSignature, fieldElement, GET_BEAN_REGISTRATIONS_FOR_FIELD, isArray, true, fieldIndex);

                case BeansInjectionPoint<ClassElement> ignore ->
                    resolveFieldValue(injectMethodSignature, fieldElement, GET_BEANS_OF_TYPE_FOR_FIELD, isArray, true, fieldIndex);

                case MapOfBeansInjectionPoint<ClassElement> ignore ->
                    resolveFieldValue(injectMethodSignature, fieldElement, GET_MAP_OF_TYPE_FOR_FIELD, isArray, true, fieldIndex);

                case OptionalBeanInjectionPoint<ClassElement> ignore ->
                    resolveFieldValue(injectMethodSignature, fieldElement, FIND_BEAN_FOR_FIELD, isArray, true, fieldIndex);

                case StreamOfBeansInjectionPoint<ClassElement> ignore ->
                    resolveFieldValue(injectMethodSignature, fieldElement, GET_STREAM_OF_TYPE_FOR_FIELD, isArray, true, fieldIndex);

                case ParameterInjectionPoint<ClassElement> ignore -> {
                    throw new IllegalArgumentException("Field injection doesn't support @Parameter");
                }
                case PropertyInjectionPoint<ClassElement> v ->
                    getInvokeGetPropertyValueForField(injectMethodSignature, fieldElement, fieldElement.getAnnotationMetadata(), v.propertyPath(), fieldIndex);

                case ValueInjectionPoint<ClassElement> v -> {
                    if (v.hasExpression()) {
                        yield resolveFieldValue(injectMethodSignature, fieldElement, GET_VALUE_FOR_FIELD, isArray, false, fieldIndex);
                    }
                    yield getInvokeGetPropertyPlaceholderValueForField(injectMethodSignature, fieldElement, v.value(), fieldIndex);
                }
                default ->
                    resolveFieldValue(injectMethodSignature, fieldElement, GET_BEAN_FOR_FIELD, isArray, false, fieldIndex);
            };
        }

        if (!isRequired) {
            return valueExpression.newLocal(fieldElement.getName() + "Value", valueVar ->
                valueVar.ifNonNull(
                    putField(fieldElement, fieldDefinition.requiresReflection(), injectMethodSignature, valueVar, fieldIndex)
                ));
        }
        return putField(fieldElement, fieldDefinition.requiresReflection(), injectMethodSignature, valueExpression, fieldIndex);
    }

    private void visitAnnotationMemberPropertyInjectionPoint(TypedElement annotationMemberBeanType,
                                                             String annotationMemberProperty,
                                                             @Nullable String requiredValue,
                                                             @Nullable String notEqualsValue) {
        ClassElement annotationMemberClassElement = annotationMemberBeanType.getType();
        MethodElement memberPropertyGetter = annotationMemberClassElement.getBeanProperties()
            .stream()
            .filter(property -> property.getSimpleName().equals(annotationMemberProperty))
            .findFirst()
            .flatMap(PropertyElement::getReadMethod)
            .orElse(null);

        if (memberPropertyGetter == null) {
            final String[] readPrefixes = annotationMemberBeanType.getAnnotationMetadata()
                .getValue(AccessorsStyle.class, "readPrefixes", String[].class)
                .orElse(new String[]{AccessorsStyle.DEFAULT_READ_PREFIX});

            memberPropertyGetter = annotationMemberClassElement.getEnclosedElement(
                ElementQuery.ALL_METHODS
                    .onlyAccessible(beanTypeElement)
                    .onlyInstance()
                    .filter(m -> annotationMemberProperty.equals(NameUtils.getPropertyNameForGetter(m.getName(), readPrefixes)) && !m.hasParameters())
            ).orElse(null);
        }

        if (memberPropertyGetter == null) {
            visitorContext.fail("Bean property [" + annotationMemberProperty + "] is not available on bean ["
                + annotationMemberBeanType.getName() + "]", annotationMemberBeanType);
        } else {
            annotationInjectionPoints.computeIfAbsent(annotationMemberClassElement, type -> new ArrayList<>(2))
                .add(new AnnotationVisitData(annotationMemberBeanType, annotationMemberProperty, memberPropertyGetter, requiredValue, notEqualsValue));
        }
    }

    private ExpressionDef getInvokeGetPropertyValueForField(InjectMethodSignature injectMethodSignature,
                                                            FieldElement fieldElement,
                                                            AnnotationMetadata annotationMetadata,
                                                            String value,
                                                            int fieldIndex) {
        annotationMetadata = MutableAnnotationMetadata.of(annotationMetadata);
        removeAnnotations(annotationMetadata, PropertySource.class.getName(), Property.class.getName());

        return injectMethodSignature.aThis
            .invoke(
                GET_PROPERTY_VALUE_FOR_FIELD,

                injectMethodSignature.beanResolutionContext,
                injectMethodSignature.beanContext,
                getFieldArgument(fieldElement, annotationMetadata, fieldIndex),
                ExpressionDef.constant(value),
                ExpressionDef.constant(getCliPrefix(fieldElement.getName()))
            ).cast(TypeDef.erasure(fieldElement.getType()));
    }

    private ExpressionDef getInvokeGetPropertyPlaceholderValueForField(InjectMethodSignature injectMethodSignature,
                                                                       FieldElement fieldElement,
                                                                       String value,
                                                                       int fieldIndex) {
        AnnotationMetadata annotationMetadata = MutableAnnotationMetadata.of(fieldElement.getAnnotationMetadata());
        removeAnnotations(annotationMetadata, PropertySource.class.getName(), Property.class.getName());

        return injectMethodSignature.aThis
            .invoke(
                GET_PROPERTY_PLACEHOLDER_VALUE_FOR_FIELD,

                injectMethodSignature.beanResolutionContext,
                injectMethodSignature.beanContext,
                getFieldArgument(fieldElement, annotationMetadata, fieldIndex),
                ExpressionDef.constant(value)
            ).cast(TypeDef.erasure(fieldElement.getType()));
    }

    private ExpressionDef getGetValueForPathCall(InjectMethodSignature injectMethodSignature,
                                                 ClassElement propertyType,
                                                 String propertyName,
                                                 String propertyPath,
                                                 boolean zeroArgs,
                                                 Map<String, ClassElement> generics) {
        return injectMethodSignature.aThis
            .invoke(
                GET_VALUE_FOR_PATH,

                injectMethodSignature.beanResolutionContext,
                injectMethodSignature.beanContext,
                zeroArgs ? ClassTypeDef.of(Argument.class).invokeStatic(
                    ArgumentExpUtils.METHOD_CREATE_ARGUMENT_SIMPLE,

                    ExpressionDef.constant(TypeDef.of(Boolean.class)),
                    ExpressionDef.constant("factory")
                ) : ArgumentExpUtils.buildArgumentWithGenerics(
                    annotationMetadata,
                    beanDefinitionTypeDef,
                    propertyName,
                    propertyType,
                    generics,
                    new HashSet<>(),
                    loadClassValueExpressionFn
                ),
                ExpressionDef.constant(propertyPath)
            );
    }

    private ExpressionDef getValueBypassingBeanContext(ClassElement type, List<VariableDef.MethodParameter> methodParameters) {
        // Used in instantiate and inject methods
        if (type.isAssignable(BeanResolutionContext.class)) {
            return methodParameters.get(INSTANTIATE_METHOD_BEAN_RESOLUTION_CONTEXT_PARAM);
        }
        if (type.isAssignable(BeanContext.class)) {
            return methodParameters.get(INSTANTIATE_METHOD_BEAN_CONTEXT_PARAM);
        }
        if (visitorContext.getClassElement(ConversionService.class).orElseThrow().equals(type)) {
            // We only want to assign to exact `ConversionService` classes not to classes extending `ConversionService`
            return methodParameters.get(INSTANTIATE_METHOD_BEAN_CONTEXT_PARAM)
                .invoke(METHOD_BEAN_CONTEXT_GET_CONVERSION_SERVICE);
        }
        if (type.isAssignable(ConfigurationPath.class)) {
            return methodParameters.get(INSTANTIATE_METHOD_BEAN_RESOLUTION_CONTEXT_PARAM)
                .invoke(GET_CONFIGURATION_PATH_METHOD);
        }
        return null;
    }

    private ExpressionDef resolveFieldValue(InjectMethodSignature injectMethodSignature, FieldElement fieldElement, Method methodToInvoke, boolean isArray, boolean requiresGenericType, int fieldIndex) {
        ExpressionDef valueExpression;
        List<ExpressionDef> valueExpressions = new ArrayList<>(
            List.of(
                injectMethodSignature.beanResolutionContext,
                injectMethodSignature.beanContext,
                ExpressionDef.constant(fieldIndex)
            )
        );
        if (requiresGenericType) {
            valueExpressions.add(
                resolveFieldArgumentGenericType(fieldElement.getGenericType(), fieldIndex)
            );
        }
        valueExpressions.add(
            getQualifier(fieldElement, resolveFieldArgument(fieldIndex))
        );
        valueExpression = injectMethodSignature.aThis
            .invoke(methodToInvoke, valueExpressions);

        if (isArray && requiresGenericType) {
            valueExpression = convertToArray(fieldElement.getType().fromArray(), valueExpression);
        }
        valueExpression = valueExpression.cast(TypeDef.erasure(fieldElement.getType()));
        return valueExpression;
    }

    private StatementDef putField(FieldElement fieldElement,
                                  boolean requiresReflection,
                                  InjectMethodSignature injectMethodSignature,
                                  ExpressionDef valueExpression,
                                  int fieldIndex) {
        VariableDef instanceVar = injectMethodSignature.instanceVar;
        if (requiresReflection) {
            return injectMethodSignature.aThis
                .invoke(
                    SET_FIELD_WITH_REFLECTION_METHOD,

                    injectMethodSignature.beanResolutionContext,
                    injectMethodSignature.beanContext,
                    ExpressionDef.constant(fieldIndex),
                    instanceVar,
                    valueExpression
                );
        }
        return instanceVar
            .cast(TypeDef.erasure(fieldElement.getDeclaringType()))
            .field(fieldElement)
            .put(valueExpression);
    }

    private ExpressionDef getPropertyContainsCheck(InjectMethodSignature injectMethodSignature,
                                                   ClassElement propertyType,
                                                   String propertyValue,
                                                   String propertyName) {

        ExpressionDef.InvokeInstanceMethod containsProperty = injectMethodSignature.aThis.invoke(
            isMultiValueProperty(propertyType) ? CONTAINS_PROPERTIES_VALUE_METHOD : CONTAINS_PROPERTY_VALUE_METHOD,

            injectMethodSignature.beanResolutionContext,
            injectMethodSignature.beanContext,
            ExpressionDef.constant(propertyValue) // property name
        );

        String cliProperty = getCliPrefix(propertyName);
        if (cliProperty == null) {
            return containsProperty.isTrue();
        }
        return containsProperty.isTrue().or(
            injectMethodSignature.aThis.invoke(
                CONTAINS_PROPERTY_VALUE_METHOD,

                injectMethodSignature.beanResolutionContext,
                injectMethodSignature.beanContext,
                ExpressionDef.constant(cliProperty) // property name
            ).isTrue()
        );
    }

    private String getCliPrefix(String propertyName) {
        if (isConfigurationProperties && this.annotationMetadata.isPresent(ConfigurationProperties.class, "cliPrefix")) {
            return this.annotationMetadata.stringValue(ConfigurationProperties.class, "cliPrefix").map(val -> val + propertyName).orElse(null);
        }
        return null;
    }

    private boolean isMultiValueProperty(ClassElement type) {
        return type.isAssignable(Map.class) || type.isAssignable(Collection.class) || isConfigurationProperties(type);
    }

    private ExpressionDef getQualifier(AnnotationMetadata element, ExpressionDef argumentExpression) {
        return getQualifier(element, () -> argumentExpression);
    }

    private ExpressionDef getQualifier(AnnotationMetadata element, Supplier<ExpressionDef> argumentExpressionSupplier) {
        final List<String> qualifierNames = element.getAnnotationNamesByStereotype(AnnotationUtil.QUALIFIER);
        if (!qualifierNames.isEmpty()) {
            if (qualifierNames.size() == 1) {
                // simple qualifier
                final String annotationName = qualifierNames.iterator().next();
                return getQualifierForAnnotation(element, annotationName, argumentExpressionSupplier.get());
            }
            // composite qualifier
            return TYPE_QUALIFIERS.invokeStatic(
                METHOD_QUALIFIER_BY_QUALIFIERS,

                TYPE_QUALIFIER.array().instantiate(
                    qualifierNames.stream().map(name -> getQualifierForAnnotation(element, name, argumentExpressionSupplier.get())).toList()
                )
            );
        }
        if (element.hasAnnotation(AnnotationUtil.ANN_INTERCEPTOR_BINDING_QUALIFIER)) {
            return TYPE_QUALIFIERS.invokeStatic(
                METHOD_QUALIFIER_BY_INTERCEPTOR_BINDING,
                getAnnotationMetadataFromProvider(argumentExpressionSupplier.get())
            );
        }
        String[] byType = element.hasDeclaredAnnotation(Type.NAME) ? element.stringValues(Type.NAME) : null;
        if (byType != null && byType.length > 0) {
            return TYPE_QUALIFIERS.invokeStatic(
                METHOD_QUALIFIER_BY_TYPE,

                TypeDef.CLASS.array().instantiate(Arrays.stream(byType).map(this::asClassExpression).toList())
            );
        }
        return ExpressionDef.nullValue();
    }

    private ExpressionDef getAnnotationMetadataFromProvider(ExpressionDef argumentExpression) {
        return argumentExpression.invoke(PROVIDER_GET_ANNOTATION_METADATA_METHOD);
    }

    private ExpressionDef getQualifierForAnnotation(AnnotationMetadata element,
                                                    String annotationName,
                                                    ExpressionDef argumentExpression) {
        if (annotationName.equals(Primary.NAME)) {
            // primary is the same as no qualifier
            return ExpressionDef.nullValue();
        }
        if (annotationName.equals(AnnotationUtil.NAMED)) {
            Optional<String> named = element.stringValue(AnnotationUtil.NAMED);
            final String n = named.orElseGet(() -> {
                if (element instanceof Named nmd) {
                    return nmd.getName();
                }
                throw new IllegalStateException("Named annotation not found on element: " + element);
            });
            if (!n.contains("$")) {
                return TYPE_QUALIFIERS.invokeStatic(METHOD_QUALIFIER_BY_NAME, ExpressionDef.constant(n));
            }
            return TYPE_QUALIFIERS.invokeStatic(METHOD_QUALIFIER_FOR_ARGUMENT, argumentExpression);
        }
        if (annotationName.equals(Any.NAME)) {
            return ClassTypeDef.of(AnyQualifier.class).getStaticField("INSTANCE", ClassTypeDef.of(AnyQualifier.class));
        }
        final String repeatableContainerName = element.findRepeatableAnnotation(annotationName).orElse(null);
        if (repeatableContainerName != null) {
            return TYPE_QUALIFIERS.invokeStatic(
                METHOD_QUALIFIER_BY_REPEATABLE_ANNOTATION,
                getAnnotationMetadataFromProvider(argumentExpression),
                ExpressionDef.constant(repeatableContainerName)
            );
        }
        return TYPE_QUALIFIERS.invokeStatic(
            METHOD_QUALIFIER_BY_ANNOTATION,
            getAnnotationMetadataFromProvider(argumentExpression),
            ExpressionDef.constant(annotationName)
        );
    }

    private ExpressionDef getArrayOfClasses(String[] byType) {
        return getArrayOfClasses(List.of(byType));
    }

    private ExpressionDef getArrayOfClasses(Collection<String> byType) {
        return TypeDef.CLASS.array().instantiate(byType.stream().map(this::asClassExpression).toList());
    }

    private ExpressionDef.Constant asClassExpression(String type) {
        return ExpressionDef.constant(TypeDef.of(type));
    }

    private ExpressionDef convertToArray(ClassElement arrayType, ExpressionDef value) {
        return value
            .cast(TypeDef.of(Collection.class))
            .invoke(COLLECTION_TO_ARRAY, ClassTypeDef.of(arrayType).array().instantiate());
    }

    private void autoApplyNamedIfPresent(Element element, AnnotationMetadata annotationMetadata) {
        if (annotationMetadata.hasAnnotation(AnnotationUtil.NAMED) || annotationMetadata.hasStereotype(AnnotationUtil.NAMED)) {
            autoApplyNamed(element);
        }
    }

    private void autoApplyNamed(Element element) {
        if (element.stringValue(AnnotationUtil.NAMED).isEmpty()) {
            element.annotate(AnnotationUtil.NAMED, (builder) -> {
                final String name;

                if (element instanceof ClassElement) {
                    name = NameUtils.decapitalize(element.getSimpleName());
                } else {
                    if (element instanceof MethodElement) {
                        final String n = element.getName();
                        if (NameUtils.isGetterName(n)) {
                            name = NameUtils.getPropertyNameForGetter(n);
                        } else {
                            name = n;
                        }
                    } else {
                        name = element.getName();
                    }
                }
                builder.value(name);
            });
        }
    }

    private StatementDef injectStatement(InjectMethodSignature injectMethodSignature,
                                         MethodDefinition<ClassElement, MethodElement> methodDefinition) {
        StatementDef injectStatement = doInjectMethod(injectMethodSignature, methodDefinition);
        if (methodDefinition.isOptional()) {
            MethodElement methodElement = methodDefinition.methodElement();
            String property = findProperty(methodDefinition.annotationMetadata()).orElseThrow(() -> new ProcessingException(methodElement, "Optional method should have @Property defined"));
            return getPropertyContainsCheck(
                injectMethodSignature,
                methodElement.getParameters()[0].getGenericType(),
                property,
                ""
            ).ifTrue(injectStatement);
        }
        return injectStatement;
    }

    private StatementDef doInjectMethod(InjectMethodSignature injectMethodSignature,
                                        MethodDefinition<ClassElement, MethodElement> methodDefinition) {
        VariableDef.This aThis = injectMethodSignature.aThis;
        List<VariableDef.MethodParameter> parameters = injectMethodSignature.methodParameters;
        int methodIndex = allMethods.indexOf(methodDefinition);

        List<StatementDef> statements = new ArrayList<>();
        List<ExpressionDef> invocationValues;
        if (methodDefinition.isSetter()) {
            invocationValues = injectSetterValues(aThis, parameters, methodDefinition, methodIndex, statements);
        } else {
            invocationValues = injectMethodValues(aThis, parameters, methodDefinition.injectionPoints(), methodIndex);
        }
        statements.add(doInvokeMethod(injectMethodSignature, methodDefinition, invocationValues, methodIndex));
        return StatementDef.multi(statements);
    }

    private StatementDef doInvokeMethod(InjectMethodSignature injectMethodSignature, MethodDefinition<ClassElement, MethodElement> methodDefinition, List<ExpressionDef> invocationValues, int methodIndex) {
        VariableDef.This aThis = injectMethodSignature.aThis;
        VariableDef instanceVar = injectMethodSignature.instanceVar;
        MethodElement methodElement = methodDefinition.methodElement();
        boolean isRequiredInjection = InjectionPoint.isInjectionRequired(methodDefinition.annotationMetadata());
        if (!isRequiredInjection && methodElement.hasParameters()) {
            // store parameter values in local object[]

            return TypeDef.OBJECT.array().instantiate(invocationValues).newLocal("values", valuesVar -> {
                // invoke isMethodResolved with method parameters
                List<? extends ExpressionDef> values = IntStream.range(0, methodDefinition.injectionPoints().size())
                    .mapToObj(index -> valuesVar.arrayElement(index).cast(TypeDef.erasure(methodDefinition.injectionPoints().get(index).type())))
                    .toList();

                return aThis.invoke(
                    IS_METHOD_RESOLVED,

                    ExpressionDef.constant(methodIndex),
                    valuesVar
                ).ifTrue(
                    instanceVar.invoke(methodElement, values)
                );
            });

        }
        if (!methodDefinition.requiresReflection()) {
            return instanceVar.invoke(methodElement, invocationValues);
        }
        return aThis.invoke(
            INVOKE_WITH_REFLECTION_METHOD,

            injectMethodSignature.beanResolutionContext,
            injectMethodSignature.beanContext,
            ExpressionDef.constant(methodIndex),
            instanceVar,
            TypeDef.OBJECT.array().instantiate(invocationValues)
        );
    }

    private StatementDef destroyInjectScopeBeansIfNecessary(List<VariableDef.MethodParameter> parameters) {
        return parameters.getFirst().invoke(DESTROY_INJECT_SCOPED_BEANS_METHOD);
    }

    private ExpressionDef.Cast injectMethodParameter(Method methodToInvoke,
                                                     boolean hasGenericType,
                                                     ClassElement resultType,
                                                     VariableDef.This aThis,
                                                     List<VariableDef.MethodParameter> methodParameters,
                                                     int methodIndex,
                                                     int parameterIndex,
                                                     AnnotationMetadata annotationMetadata) {
        boolean isArray = resultType.isArray();
        List<ExpressionDef> values = new ArrayList<>(
            List.of(
                // 1st argument load BeanResolutionContext
                methodParameters.get(0),
                // 2nd argument load BeanContext
                methodParameters.get(1),
                // 3rd argument the method index
                ExpressionDef.constant(methodIndex),
                // 4th argument the argument index
                ExpressionDef.constant(parameterIndex)
            )
        );

        // invoke getBeanForField
        if (hasGenericType) {
            values.add(
                resolveMethodArgumentGenericType(resultType, methodIndex, parameterIndex)
            );
        }
        ExpressionDef argumentExpression = resolveMethodArgument(methodIndex, parameterIndex);
        values.add(
            getQualifier(annotationMetadata, argumentExpression)
        );

        ExpressionDef result = aThis.invoke(methodToInvoke, values);

        if (isArray && hasGenericType) {
            result = convertToArray(resultType.fromArray(), result);
        }
        // cast the return value to the correct type
        return result.cast(TypeDef.erasure(resultType));
    }

    private ExpressionDef getInvokeGetPropertyValueForMethod(VariableDef.This aThis,
                                                             List<VariableDef.MethodParameter> methodParameters,
                                                             int methodIndex,
                                                             int parameterIndex,
                                                             String parameterName,
                                                             ClassElement type,
                                                             String value) {
        return aThis.invoke(
            GET_PROPERTY_VALUE_FOR_METHOD_ARGUMENT,
            // 1st argument load BeanResolutionContext
            methodParameters.get(0),
            // 2nd argument load BeanContext
            methodParameters.get(1),
            // 3rd argument the method index
            ExpressionDef.constant(methodIndex),
            // 4th argument the argument index
            ExpressionDef.constant(parameterIndex),
            // 5th property value
            ExpressionDef.constant(value),
            // 6 cli property name
            ExpressionDef.constant(getCliPrefix(parameterName))
        ).cast(TypeDef.erasure(type));
    }

    private ExpressionDef getInvokeGetEvaluatedExpressionValueForMethodArgument(VariableDef.This aThis,
                                                                                int methodIndex,
                                                                                int parameterIndex,
                                                                                ClassElement type) {
        return aThis.invoke(
            GET_EVALUATED_EXPRESSION_VALUE_FOR_METHOD_ARGUMENT,

            // 1st argument the method index
            ExpressionDef.constant(methodIndex),
            // 2nd argument the argument index
            ExpressionDef.constant(parameterIndex)
        ).cast(TypeDef.erasure(type));
    }

    private ExpressionDef getInvokeGetPropertyPlaceholderValueForMethod(VariableDef.This aThis,
                                                                        List<VariableDef.MethodParameter> methodParameters,
                                                                        int methodIndex,
                                                                        int parameterIndex,
                                                                        ClassElement type,
                                                                        String value) {
        return aThis.invoke(
            GET_PROPERTY_PLACEHOLDER_VALUE_FOR_METHOD_ARGUMENT,
            // 1st argument load BeanResolutionContext
            methodParameters.get(0),
            // 2nd argument load BeanContext
            methodParameters.get(1),
            // 3rd argument the method index
            ExpressionDef.constant(methodIndex),
            // 4th argument the argument index
            ExpressionDef.constant(parameterIndex),
            // 5th property value
            ExpressionDef.constant(value)
        ).cast(TypeDef.erasure(type));
    }

    private ExpressionDef getInvokeGetPropertyValueForSetter(VariableDef.This aThis,
                                                             List<VariableDef.MethodParameter> methodParameters,
                                                             String setterName,
                                                             ParameterElement entry,
                                                             String value,
                                                             AnnotationMetadata annotationMetadata,
                                                             int methodIndex) {
        annotationMetadata = MutableAnnotationMetadata.of(annotationMetadata);
        removeAnnotations(annotationMetadata, PropertySource.class.getName(), Property.class.getName());

        return aThis.invoke(
            GET_PROPERTY_VALUE_FOR_SETTER,

            // 1st argument load BeanResolutionContext
            methodParameters.get(0),
            // 2nd argument load BeanContext
            methodParameters.get(1),
            // 3rd argument the method name
            ExpressionDef.constant(setterName),
            // 4th argument the argument
            getMethodArgument(entry, annotationMetadata, methodIndex),
            // 5th property value
            ExpressionDef.constant(value),
            // 6 cli property name
            ExpressionDef.constant(getCliPrefix(entry.getName()))
        ).cast(TypeDef.erasure(entry.getType()));
    }

    private ExpressionDef getMethodArgument(ParameterElement entry, AnnotationMetadata annotationMetadata, int methodIndex) {
        if (methodIndex == -1) {
            return ArgumentExpUtils.pushCreateArgument(
                this.annotationMetadataDefaults,
                ClassElement.of(beanFullClassName),
                beanDefinitionTypeDef,
                entry.getName(),
                entry.getGenericType(),
                annotationMetadata,
                entry.getGenericType().getTypeArguments(),
                loadClassValueExpressionFn
            );
        }
        return resolveMethodArgument(methodIndex, 0);
    }

    private ExpressionDef getFieldArgument(FieldElement fieldElement, AnnotationMetadata annotationMetadata, int fieldIndex) {
        if (fieldIndex == -1) {
            return ArgumentExpUtils.pushCreateArgument(
                this.annotationMetadataDefaults,
                ClassElement.of(beanFullClassName),
                beanDefinitionTypeDef,
                fieldElement.getName(),
                fieldElement.getGenericType(),
                annotationMetadata,
                fieldElement.getGenericType().getTypeArguments(),
                loadClassValueExpressionFn
            );
        }
        return resolveFieldArgument(fieldIndex);
    }

    private ExpressionDef getInvokeGetBeanForSetter(VariableDef.This aThis,
                                                    List<VariableDef.MethodParameter> methodParameters,
                                                    String setterName,
                                                    ParameterElement entry,
                                                    AnnotationMetadata annotationMetadata,
                                                    int methodIndex) {

        annotationMetadata = MutableAnnotationMetadata.of(annotationMetadata);
        removeAnnotations(annotationMetadata, PropertySource.class.getName(), Property.class.getName());

        return aThis.invoke(
            GET_BEAN_FOR_SETTER,
            // 1st argument load BeanResolutionContext
            methodParameters.get(0),
            // 2nd argument load BeanContext
            methodParameters.get(1),
            // 3rd argument the method name
            ExpressionDef.constant(setterName),
            // 4th argument the argument
            getMethodArgument(entry, annotationMetadata, methodIndex),
            // push qualifier
            getQualifier(entry.getGenericType(), getMethodArgument(entry, annotationMetadata, methodIndex))
        ).cast(TypeDef.erasure(entry.getType()));
    }

    private ExpressionDef getInvokeGetBeansOfTypeForSetter(VariableDef.This aThis,
                                                           List<VariableDef.MethodParameter> methodParameters,
                                                           String setterName,
                                                           ParameterElement entry,
                                                           AnnotationMetadata annotationMetadata,
                                                           int methodIndex,
                                                           List<StatementDef> additionalStatements) {

        annotationMetadata = MutableAnnotationMetadata.of(annotationMetadata);
        removeAnnotations(annotationMetadata, PropertySource.class.getName(), Property.class.getName());

        // 4th argument the argument
        ClassElement genericType = entry.getGenericType();
        StatementDef.DefineAndAssign defineAndAssign = getMethodArgument(entry, annotationMetadata, methodIndex).newLocal(ARGUMENT_MEMBER);
        additionalStatements.add(defineAndAssign);
        VariableDef.Local argumentVar = defineAndAssign.variable();
        return aThis.invoke(
            GET_BEANS_OF_TYPE_FOR_SETTER,

            // 1st argument load BeanResolutionContext
            methodParameters.get(0),
            // 2nd argument load BeanContext
            methodParameters.get(1),
            // 3rd argument the method name
            ExpressionDef.constant(setterName),
            // 4th argument the argument
            argumentVar,
            // generic type
            resolveGenericType(argumentVar, genericType),
            // push qualifier
            getQualifier(entry.getGenericType(), argumentVar)
        ).cast(TypeDef.erasure(entry.getType()));
    }

    private ExpressionDef resolveGenericType(VariableDef argumentVar, ClassElement genericType) {
        ExpressionDef argumentExpression = resolveArgumentGenericType(genericType);
        if (argumentExpression == null) {
            argumentExpression = resolveFirstTypeArgument(argumentVar);
            return resolveInnerTypeArgumentIfNeeded(argumentExpression, genericType);
        }
        return argumentExpression;
    }

    private ExpressionDef getInvokeGetPropertyPlaceholderValueForSetter(VariableDef.This aThis,
                                                                        List<VariableDef.MethodParameter> methodParameters,
                                                                        String setterName,
                                                                        ParameterElement entry,
                                                                        String value,
                                                                        AnnotationMetadata annotationMetadata,
                                                                        int methodIndex) {
        annotationMetadata = MutableAnnotationMetadata.of(annotationMetadata);
        removeAnnotations(annotationMetadata, PropertySource.class.getName(), Property.class.getName());

        return aThis
            .invoke(
                GET_PROPERTY_PLACEHOLDER_VALUE_FOR_SETTER,

                // 1st argument load BeanResolutionContext
                methodParameters.get(0),
                // 2nd argument load BeanContext
                methodParameters.get(1),
                // 3rd argument the method name
                ExpressionDef.constant(setterName),
                // 4th argument the argument
                getMethodArgument(entry, annotationMetadata, methodIndex),
                // 5th property value
                ExpressionDef.constant(value),
                // 6 cli property name
                ExpressionDef.constant(getCliPrefix(entry.getName())
                ).cast(TypeDef.erasure(entry.getType())));
    }

    private void removeAnnotations(AnnotationMetadata annotationMetadata, String... annotationNames) {
        if (annotationMetadata instanceof MutableAnnotationMetadata mutableAnnotationMetadata) {
            for (String annotation : annotationNames) {
                mutableAnnotationMetadata.removeAnnotation(annotation);
            }
        }
    }

    private void applyDefaultNamedToParameters(List<ParameterElement> argumentTypes) {
        for (ParameterElement parameterElement : argumentTypes) {
            final AnnotationMetadata annotationMetadata = parameterElement.getAnnotationMetadata();
            autoApplyNamedIfPresent(parameterElement, annotationMetadata);
        }
    }

    public void visitBuildCustomMethodDefinition(CustomInitializerBuilder builder) {
        customInitializerBuilder = builder;
    }

    private StatementDef invokeCheckIfShouldLoadIfNecessary(VariableDef.This aThis, List<VariableDef.MethodParameter> parameters) {
        AnnotationValue<Requires> requiresAnnotation = annotationMetadata.getAnnotation(Requires.class);
        if (requiresAnnotation != null
            && requiresAnnotation.stringValue(RequiresCondition.MEMBER_BEAN).isPresent()
            && requiresAnnotation.stringValue(RequiresCondition.MEMBER_BEAN_PROPERTY).isPresent()) {


            MethodDef checkIfShouldLoad = buildCheckIfShouldLoadMethod();

            classDefBuilder.addMethod(
                checkIfShouldLoad
            );

            return aThis.invoke(checkIfShouldLoad, parameters);
        }
        return StatementDef.multi();
    }

    private MethodDef buildCheckIfShouldLoadMethod() {
        return MethodDef.override(CHECK_IF_SHOULD_LOAD_METHOD)
            .build((aThis, methodParameters) -> {
                List<ClassElement> injectedTypes = new ArrayList<>(annotationInjectionPoints.keySet());
                List<StatementDef> statements = new ArrayList<>();
                for (int index = 0; index < injectedTypes.size(); index++) {
                    ClassElement injectedType = injectedTypes.get(index);
                    List<AnnotationVisitData> annotationVisitData = annotationInjectionPoints.get(injectedType);
                    if (annotationVisitData.isEmpty()) {
                        continue;
                    }
                    AnnotationVisitData data = annotationVisitData.get(0);
                    ExpressionDef beanExpression = getBeanForAnnotation(aThis, methodParameters, index, data.memberBeanType);

                    if (annotationVisitData.size() == 1) {
                        statements.add(
                            checkInjectedBean(aThis, data, beanExpression.invoke(data.memberPropertyGetter))
                        );
                    } else {
                        statements.add(
                            beanExpression.newLocal("beanInstance" + index, beanInstanceVar -> StatementDef.multi(
                                annotationVisitData.stream().
                                    <StatementDef>map(d -> checkInjectedBean(
                                        aThis,
                                        d,
                                        beanInstanceVar.invoke(d.memberPropertyGetter)
                                    )
                                ).toList()
                            ))
                        );
                    }
                }
                return StatementDef.multi(statements);
            });
    }

    private ExpressionDef.InvokeInstanceMethod checkInjectedBean(VariableDef.This aThis, AnnotationVisitData data, ExpressionDef valueExpression) {
        return aThis
            .invoke(
                CHECK_INJECTED_BEAN_PROPERTY_VALUE,

                ExpressionDef.constant(data.memberPropertyName),
                valueExpression,
                ExpressionDef.constant(data.requiredValue),
                ExpressionDef.constant(data.notEqualsValue)
            );
    }

    private ExpressionDef.Cast getBeanForAnnotation(VariableDef.This aThis,
                                                    List<VariableDef.MethodParameter> methodParameters,
                                                    int currentTypeIndex,
                                                    TypedElement memberType) {
        return aThis.invoke(
            GET_BEAN_FOR_ANNOTATION,

            // 1st argument load BeanResolutionContext
            methodParameters.get(0),
            // 2nd argument load BeanContext
            methodParameters.get(1),
            // 3rd argument the injected bean index
            ExpressionDef.constant(currentTypeIndex),
            // push qualifier
            getQualifier(memberType, resolveAnnotationArgument(0))
        ).cast(TypeDef.erasure(memberType));
    }

    private boolean isConstructorIntercepted(AnnotationMetadata constructorAnnotationMetadata) {
        // a constructor is intercepted when this bean is an advised type but not proxied
        // and any AROUND_CONSTRUCT annotations are present
        AnnotationMetadataHierarchy annotationMetadata = new AnnotationMetadataHierarchy(this.annotationMetadata, constructorAnnotationMetadata);
        final String interceptType = "AROUND_CONSTRUCT";
        // for beans that are @Around(proxyTarget=true) only the constructor of the proxy target should be intercepted. Beans returned from factories are always proxyTarget=true

        return isInterceptedLifeCycleByType(annotationMetadata, interceptType);
    }

    private boolean isInterceptedLifeCycleByType(AnnotationMetadata annotationMetadata, String interceptType) {
        return isLifeCycleCache.computeIfAbsent(interceptType, s -> {
            if (this.beanTypeElement.isAssignable("io.micronaut.aop.Interceptor")) {
                // interceptor beans cannot have lifecycle methods intercepted
                return false;
            }
            final boolean isFactoryMethod = isSuperFactory || elementProducerDefinition instanceof MethodDefinition<?, ?>;
            final boolean isProxyTarget = annotationMetadata.booleanValue(AnnotationUtil.ANN_AROUND, "proxyTarget").orElse(false) || isFactoryMethod;
            // for beans that are @Around(proxyTarget = false) only the generated AOP impl should be intercepted
            final boolean isAopType = StringUtils.isNotEmpty(interceptedType);
            final boolean isConstructorInterceptionCandidate = (isProxyTarget && !isAopType) || (isAopType && !isProxyTarget);
            final boolean hasAroundConstruct;
            final AnnotationValue<Annotation> interceptorBindings
                = annotationMetadata.getAnnotation(AnnotationUtil.ANN_INTERCEPTOR_BINDINGS);
            List<AnnotationValue<Annotation>> interceptorBindingAnnotations;
            if (interceptorBindings != null) {
                interceptorBindingAnnotations = interceptorBindings.getAnnotations(AnnotationMetadata.VALUE_MEMBER);
                hasAroundConstruct = interceptorBindingAnnotations
                    .stream()
                    .anyMatch(av -> av.stringValue("kind").map(k -> k.equals(interceptType)).orElse(false));
            } else {
                interceptorBindingAnnotations = Collections.emptyList();
                hasAroundConstruct = false;
            }

            if (isConstructorInterceptionCandidate) {
                return hasAroundConstruct;
            } else if (hasAroundConstruct) {
                AnnotationMetadata typeMetadata = annotationMetadata;
                if (!isSuperFactory && typeMetadata instanceof AnnotationMetadataHierarchy hierarchy) {
                    typeMetadata = hierarchy.getRootMetadata();
                    final AnnotationValue<Annotation> av =
                        typeMetadata.getAnnotation(AnnotationUtil.ANN_INTERCEPTOR_BINDINGS);
                    if (av != null) {
                        interceptorBindingAnnotations = av.getAnnotations(AnnotationMetadata.VALUE_MEMBER);
                    } else {
                        interceptorBindingAnnotations = Collections.emptyList();
                    }
                }
                // if no other AOP advice is applied
                return interceptorBindingAnnotations
                    .stream()
                    .noneMatch(av -> av.stringValue("kind").map(k -> k.equals("AROUND")).orElse(false));
            } else {
                return false;
            }
        });
    }

    private List<? extends ExpressionDef> getConstructorArgumentValues(VariableDef.This aThis,
                                                                       List<VariableDef.MethodParameter> methodParameters,
                                                                       List<BeanDefinitionInjectionPoint<ClassElement>> parameters,
                                                                       boolean isParametrized,
                                                                       Supplier<VariableDef> constructorMethodVarSupplier) {
        List<ExpressionDef> values = new ArrayList<>();
        for (int i = 0; i < parameters.size(); i++) {
            BeanDefinitionInjectionPoint<ClassElement> parameter = parameters.get(i);
            values.add(
                injectConstructorParameterExpression(aThis, methodParameters, parameter, i, isParametrized, constructorMethodVarSupplier)
            );
        }
        return values;
    }

    private List<ExpressionDef> injectMethodValues(VariableDef.This aThis,
                                                   List<VariableDef.MethodParameter> methodParameters,
                                                   List<BeanDefinitionInjectionPoint<ClassElement>> parameters,
                                                   int methodIndex) {
        List<ExpressionDef> values = new ArrayList<>(parameters.size());
        for (int parameterIndex = 0; parameterIndex < parameters.size(); parameterIndex++) {
            BeanDefinitionInjectionPoint<ClassElement> parameter = parameters.get(parameterIndex);
            values.add(
                injectMethodParameterExpression(aThis, methodParameters, methodIndex, parameterIndex, parameter)
            );
        }
        return values;
    }

    private List<ExpressionDef> injectSetterValues(VariableDef.This aThis,
                                                   List<VariableDef.MethodParameter> methodParameters,
                                                   MethodDefinition<ClassElement, MethodElement> methodDefinition,
                                                   int methodIndex,
                                                   List<StatementDef> additionalStatements) {
        List<BeanDefinitionInjectionPoint<ClassElement>> injectionPoints = methodDefinition.injectionPoints();
        List<ExpressionDef> values = new ArrayList<>(injectionPoints.size());
        for (int parameterIndex = 0; parameterIndex < injectionPoints.size(); parameterIndex++) {
            BeanDefinitionInjectionPoint<ClassElement> injectionPoint = injectionPoints.get(parameterIndex);
            values.add(
                injectSetterParameterExpression(aThis, methodParameters, methodIndex, parameterIndex, methodDefinition, injectionPoint, additionalStatements)
            );
        }
        return values;
    }

    private boolean hasInjectScope() {
        if (factoryMethodDefinition != null) {
            return hasInjectScope(factoryMethodDefinition.methodElement());
        }
        if (constructorDefinition != null) {
            return hasInjectScope(constructorDefinition.constructorElement());
        }
        return false;
    }

    private static boolean hasInjectScope(ParameterElement[] parameters) {
        for (ParameterElement parameter : parameters) {
            if (hasInjectScope(parameter)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasInjectScope(AnnotationMetadata annotationMetadata) {
        return annotationMetadata.hasDeclaredAnnotation(InjectScope.class);
    }

    private ExpressionDef injectConstructorParameterExpression(VariableDef.This aThis,
                                                               List<VariableDef.MethodParameter> methodParameters,
                                                               BeanDefinitionInjectionPoint<ClassElement> parameter,
                                                               int index,
                                                               boolean isParametrized,
                                                               Supplier<VariableDef> constructorMethodVarSupplier) {
        ExpressionDef expression = getValueBypassingBeanContext(parameter.type(), methodParameters);
        if (expression != null) {
            return expression;
        }
        return switch (parameter) {
            case BeanInjectionPoint<ClassElement> v ->
                injectConstructorParameter(GET_BEAN_FOR_CONSTRUCTOR_ARGUMENT, false, v.type(), aThis, methodParameters, index, constructorMethodVarSupplier, v.annotationMetadata());
            case BeanRegistrationInjectionPoint<ClassElement> v ->
                injectConstructorParameter(GET_BEAN_REGISTRATION_FOR_CONSTRUCTOR_ARGUMENT, true, v.type(), aThis, methodParameters, index, constructorMethodVarSupplier, v.annotationMetadata());
            case BeanRegistrationsInjectionPoint<ClassElement> v ->
                injectConstructorParameter(GET_BEAN_REGISTRATIONS_FOR_CONSTRUCTOR_ARGUMENT, true, v.type(), aThis, methodParameters, index, constructorMethodVarSupplier, v.annotationMetadata());
            case BeansInjectionPoint<ClassElement> v ->
                injectConstructorParameter(GET_BEANS_OF_TYPE_FOR_CONSTRUCTOR_ARGUMENT, true, v.type(), aThis, methodParameters, index, constructorMethodVarSupplier, v.annotationMetadata());
            case MapOfBeansInjectionPoint<ClassElement> v ->
                injectConstructorParameter(GET_MAP_OF_TYPE_FOR_CONSTRUCTOR_ARGUMENT, true, v.type(), aThis, methodParameters, index, constructorMethodVarSupplier, v.annotationMetadata());
            case OptionalBeanInjectionPoint<ClassElement> v ->
                injectConstructorParameter(FIND_BEAN_FOR_CONSTRUCTOR_ARGUMENT, true, v.type(), aThis, methodParameters, index, constructorMethodVarSupplier, v.annotationMetadata());
            case StreamOfBeansInjectionPoint<ClassElement> v ->
                injectConstructorParameter(GET_STREAM_OF_TYPE_FOR_CONSTRUCTOR_ARGUMENT, true, v.type(), aThis, methodParameters, index, constructorMethodVarSupplier, v.annotationMetadata());
            case ParameterInjectionPoint<ClassElement> v -> {
                if (!isParametrized) {
                    throw new IllegalArgumentException("Cannot resolve constructor argument for parameter [" + v.name() + "] of type [" + v.type() + "] because it is not parametrized");
                }
                yield methodParameters.get(2).invoke(
                    GET_MAP_METHOD,
                    ExpressionDef.constant(v.name())
                );
            }
            case PropertyInjectionPoint<ClassElement> v ->
                getInvokeGetPropertyValueForConstructor(aThis, methodParameters, index, v.type(), v.propertyName(), v.propertyPath());
            case ValueInjectionPoint<ClassElement> v -> {
                if (v.hasExpression()) {
                    yield getInvokeGetEvaluatedExpressionValueForConstructorArgument(aThis, index, v.type());
                }
                yield getInvokeGetPropertyPlaceholderValueForConstructor(aThis, methodParameters, index, v.type(), v.value());
            }
        };
    }

    private ExpressionDef injectMethodParameterExpression(VariableDef.This aThis,
                                                          List<VariableDef.MethodParameter> methodParameters,
                                                          int methodIndex,
                                                          int parameterIndex,
                                                          BeanDefinitionInjectionPoint<ClassElement> injectionPoint) {
        ExpressionDef expressionDef = getValueBypassingBeanContext(injectionPoint.type(), methodParameters);
        if (expressionDef != null) {
            return expressionDef;
        }

        ExpressionDef expression = getValueBypassingBeanContext(injectionPoint.type(), methodParameters);
        if (expression != null) {
            return expression;
        }
        return switch (injectionPoint) {
            case BeanInjectionPoint<ClassElement> v ->
                injectMethodParameter(GET_BEAN_FOR_METHOD_ARGUMENT, false, v.type(), aThis, methodParameters, methodIndex, parameterIndex, v.annotationMetadata());

            case BeanRegistrationInjectionPoint<ClassElement> v ->
                injectMethodParameter(GET_BEAN_REGISTRATION_FOR_METHOD_ARGUMENT, true, v.type(), aThis, methodParameters, methodIndex, parameterIndex, v.annotationMetadata());
            case BeanRegistrationsInjectionPoint<ClassElement> v ->
                injectMethodParameter(GET_BEAN_REGISTRATIONS_FOR_METHOD_ARGUMENT, true, v.type(), aThis, methodParameters, methodIndex, parameterIndex, v.annotationMetadata());
            case BeansInjectionPoint<ClassElement> v ->
                injectMethodParameter(GET_BEANS_OF_TYPE_FOR_METHOD_ARGUMENT, true, v.type(), aThis, methodParameters, methodIndex, parameterIndex, v.annotationMetadata());
            case MapOfBeansInjectionPoint<ClassElement> v ->
                injectMethodParameter(GET_MAP_OF_TYPE_FOR_METHOD_ARGUMENT, true, v.type(), aThis, methodParameters, methodIndex, parameterIndex, v.annotationMetadata());
            case OptionalBeanInjectionPoint<ClassElement> v ->
                injectMethodParameter(FIND_BEAN_FOR_METHOD_ARGUMENT, true, v.type(), aThis, methodParameters, methodIndex, parameterIndex, v.annotationMetadata());
            case StreamOfBeansInjectionPoint<ClassElement> v ->
                injectMethodParameter(GET_STREAM_OF_TYPE_FOR_METHOD_ARGUMENT, true, v.type(), aThis, methodParameters, methodIndex, parameterIndex, v.annotationMetadata());
            case ParameterInjectionPoint<ClassElement> ignore ->
                throw new IllegalStateException("Methods cannot have @Parameter");
            case PropertyInjectionPoint<ClassElement> v ->
                getInvokeGetPropertyValueForMethod(aThis, methodParameters, methodIndex, parameterIndex, v.propertyName(), v.type(), v.propertyPath());
            case ValueInjectionPoint<ClassElement> v -> {
                if (v.hasExpression()) {
                    yield getInvokeGetEvaluatedExpressionValueForMethodArgument(aThis, methodIndex, parameterIndex, v.type());
                }
                yield getInvokeGetPropertyPlaceholderValueForMethod(aThis, methodParameters, methodIndex, parameterIndex, v.type(), v.value());
            }
        };
    }

    private ExpressionDef injectSetterParameterExpression(VariableDef.This aThis,
                                                          List<VariableDef.MethodParameter> methodParameters,
                                                          int methodIndex,
                                                          int parameterIndex,
                                                          MethodDefinition<ClassElement, MethodElement> methodDefinition,
                                                          BeanDefinitionInjectionPoint<ClassElement> injectionPoint,
                                                          List<StatementDef> additionalStatements) {
        ExpressionDef expressionDef = getValueBypassingBeanContext(injectionPoint.type(), methodParameters);
        if (expressionDef != null) {
            return expressionDef;
        }
        MethodElement methodElement = methodDefinition.methodElement();
        String setterName = methodElement.getName();
        final ParameterElement parameter = methodElement.getParameters()[parameterIndex];
        return switch (injectionPoint) {
            case BeanInjectionPoint<ClassElement> v ->
                getInvokeGetBeanForSetter(aThis, methodParameters, setterName, parameter, v.annotationMetadata(), methodIndex);

//            case BeanRegistrationInjectionPoint<ClassElement> v ->
//                injectMethodParameter(GET_BEAN_REGISTRATION_FOR_METHOD_ARGUMENT, true, v.type(), aThis, methodParameters, methodIndex, parameterIndex, v.annotationMetadata());
//            case BeanRegistrationsInjectionPoint<ClassElement> v ->
//                injectMethodParameter(GET_BEAN_REGISTRATIONS_FOR_METHOD_ARGUMENT, true, v.type(), aThis, methodParameters, methodIndex, parameterIndex, v.annotationMetadata());
            case BeansInjectionPoint<ClassElement> v ->
                getInvokeGetBeansOfTypeForSetter(aThis, methodParameters, setterName, parameter, v.annotationMetadata(), methodIndex, additionalStatements);
//            case MapOfBeansInjectionPoint<ClassElement> v ->
//                injectMethodParameter(GET_MAP_OF_TYPE_FOR_METHOD_ARGUMENT, true, v.type(), aThis, methodParameters, methodIndex, parameterIndex, v.annotationMetadata());
//            case OptionalBeanInjectionPoint<ClassElement> v ->
//                injectMethodParameter(FIND_BEAN_FOR_METHOD_ARGUMENT, true, v.type(), aThis, methodParameters, methodIndex, parameterIndex, v.annotationMetadata());
//            case StreamOfBeansInjectionPoint<ClassElement> v ->
//                injectMethodParameter(GET_STREAM_OF_TYPE_FOR_METHOD_ARGUMENT, true, v.type(), aThis, methodParameters, methodIndex, parameterIndex, v.annotationMetadata());
            case ParameterInjectionPoint<ClassElement> ignore ->
                throw new IllegalStateException("Methods cannot have @Parameter");
            case PropertyInjectionPoint<ClassElement> v -> getInvokeGetPropertyValueForSetter(aThis,
                methodParameters,
                methodElement.getName(),
                parameter,
                v.propertyPath(),
                methodDefinition.annotationMetadata(),
                methodIndex);
            case ValueInjectionPoint<ClassElement> v -> {
                // Looks like we don't support expressions for a setter
//                if (v.hasExpression()) {
//                    yield getInvokeGetEvaluatedExpressionValueForMethodArgument(aThis, methodIndex, parameterIndex, v.type());
//                }
                yield getInvokeGetPropertyPlaceholderValueForSetter(aThis,
                    methodParameters,
                    methodElement.getName(),
                    parameter,
                    v.value(),
                    methodDefinition.annotationMetadata(),
                    methodIndex);
            }
            default ->
                throw new IllegalStateException("Unsupported injection point for a setter: " + injectionPoint);
        };
    }

    private ExpressionDef.Cast injectConstructorParameter(Method methodToInvoke,
                                                          boolean hasGenericType,
                                                          ClassElement resultType,
                                                          VariableDef.This aThis,
                                                          List<VariableDef.MethodParameter> methodParameters,
                                                          int index,
                                                          Supplier<VariableDef> constructorMethodVarSupplier,
                                                          AnnotationMetadata am) {
        List<ExpressionDef> values = new ArrayList<>();
        // load the first two arguments of the method (the BeanResolutionContext and the BeanContext) to be passed to the method
        values.add(methodParameters.get(0));
        values.add(methodParameters.get(1));
        // pass the index of the method as the third argument
        values.add(ExpressionDef.constant(index));
        if (hasGenericType) {
            values.add(
                resolveConstructorArgumentGenericType(resultType, index, constructorMethodVarSupplier)
            );
        }
        // push qualifier
        values.add(
            getQualifier(am, () -> resolveConstructorArgument(index, constructorMethodVarSupplier.get()))
        );
        ExpressionDef result = aThis.superRef().invoke(methodToInvoke, values);
        if (resultType.isArray() && hasGenericType) {
            result = convertToArray(resultType.fromArray(), result);
        }
        return result.cast(TypeDef.erasure(resultType));
    }

    private ExpressionDef getInvokeGetPropertyValueForConstructor(VariableDef.This aThis,
                                                                  List<VariableDef.MethodParameter> methodParameters,
                                                                  int i,
                                                                  ClassElement type,
                                                                  String propertyName,
                                                                  String value) {

        return aThis.superRef().invoke(
            GET_PROPERTY_VALUE_FOR_CONSTRUCTOR_ARGUMENT,

            // 1st argument load BeanResolutionContext
            methodParameters.get(0),
            // 2nd argument load BeanContext
            methodParameters.get(1),
            // 4th argument the argument index
            ExpressionDef.constant(i),
            // 5th property value
            ExpressionDef.constant(value),
            // 6 cli property name
            ExpressionDef.constant(getCliPrefix(propertyName))

        ).cast(TypeDef.erasure(type));
    }

    private ExpressionDef getInvokeGetPropertyPlaceholderValueForConstructor(VariableDef.This aThis,
                                                                             List<VariableDef.MethodParameter> methodParameters,
                                                                             int i,
                                                                             ClassElement type,
                                                                             String value) {

        return aThis.superRef().invoke(
            GET_PROPERTY_PLACEHOLDER_VALUE_FOR_CONSTRUCTOR_ARGUMENT,

            // 1st argument load BeanResolutionContext
            methodParameters.get(0),
            // 2nd argument load BeanContext
            methodParameters.get(1),
            // 4th argument the argument index
            ExpressionDef.constant(i),
            // 5th property value
            ExpressionDef.constant(value)
        ).cast(TypeDef.erasure(type));
    }

    private ExpressionDef getInvokeGetEvaluatedExpressionValueForConstructorArgument(VariableDef.This aThis,
                                                                                     int i,
                                                                                     ClassElement type) {
        return aThis.superRef()
            .invoke(GET_EVALUATED_EXPRESSION_VALUE_FOR_CONSTRUCTOR_ARGUMENT, ExpressionDef.constant(i))
            .cast(TypeDef.erasure(type));
    }

    private ExpressionDef resolveConstructorArgumentGenericType(ClassElement type, int argumentIndex, Supplier<VariableDef> constructorMethodVarSupplier) {
        ExpressionDef expressionDef = resolveArgumentGenericType(type);
        if (expressionDef != null) {
            return expressionDef;
        }
        ExpressionDef argumentExpression = resolveConstructorArgument(argumentIndex, constructorMethodVarSupplier.get());
        if (type.isAssignable(Map.class)) {
            argumentExpression = resolveSecondTypeArgument(argumentExpression);
        } else {
            argumentExpression = resolveFirstTypeArgument(argumentExpression);
        }
        return resolveInnerTypeArgumentIfNeeded(argumentExpression, type);
    }

    private ExpressionDef resolveConstructorArgument(int argumentIndex, VariableDef constructorMethodVar) {
        return constructorMethodVar
            .field("arguments", ClassTypeDef.of(Argument.class).array())
            .arrayElement(argumentIndex);
    }

    private ExpressionDef resolveMethodArgumentGenericType(ClassElement type, int methodIndex, int argumentIndex) {
        ExpressionDef expressionDef = resolveArgumentGenericType(type);
        if (expressionDef != null) {
            return expressionDef;
        }
        expressionDef = resolveMethodArgument(methodIndex, argumentIndex);
        if (type.isAssignable(Map.class)) {
            expressionDef = resolveSecondTypeArgument(expressionDef);
        } else {
            expressionDef = resolveFirstTypeArgument(expressionDef);
        }
        return resolveInnerTypeArgumentIfNeeded(expressionDef, type);
    }

    private ExpressionDef resolveMethodArgument(int methodIndex, int argumentIndex) {
        return beanDefinitionTypeDef.
            getStaticField(FIELD_INJECTION_METHODS, ClassTypeDef.of(AbstractInitializableBeanDefinition.MethodReference.class).array())
            .arrayElement(methodIndex)
            .field("arguments", ClassTypeDef.of(Argument.class).array())
            .arrayElement(argumentIndex);
    }

    private ExpressionDef resolveFieldArgumentGenericType(ClassElement type, int fieldIndex) {
        ExpressionDef argumentExpression = resolveArgumentGenericType(type);
        if (argumentExpression != null) {
            return argumentExpression;
        }
        argumentExpression = resolveFieldArgument(fieldIndex);
        if (type.isAssignable(Map.class)) {
            argumentExpression = resolveSecondTypeArgument(argumentExpression);
        } else {
            argumentExpression = resolveFirstTypeArgument(argumentExpression);
        }
        return resolveInnerTypeArgumentIfNeeded(argumentExpression, type);
    }

    private ExpressionDef resolveAnnotationArgument(int index) {
        return beanDefinitionTypeDef.getStaticField(FIELD_ANNOTATION_INJECTIONS, TypeDef.of(AbstractInitializableBeanDefinition.AnnotationReference[].class))
            .arrayElement(index)
            .field(ARGUMENT_MEMBER, TypeDef.of(Argument.class));
    }

    private ExpressionDef resolveFieldArgument(int fieldIndex) {
        return beanDefinitionTypeDef.getStaticField(FIELD_INJECTION_FIELDS, TypeDef.of(AbstractInitializableBeanDefinition.FieldReference[].class))
            .arrayElement(fieldIndex)
            .field(ARGUMENT_MEMBER, TypeDef.of(Argument.class));
    }

    @Nullable
    private ExpressionDef resolveArgumentGenericType(ClassElement type) {
        if (type.isArray()) {
            if (!type.getTypeArguments().isEmpty() && isInternalGenericTypeContainer(type.fromArray())) {
                // skip for arrays of BeanRegistration
                return null;
            }
            final ClassElement componentType = type.fromArray();
            if (componentType.isPrimitive()) {
                return ArgumentExpUtils.TYPE_ARGUMENT.getStaticField(
                    componentType.getName().toUpperCase(Locale.ENGLISH),
                    ArgumentExpUtils.TYPE_ARGUMENT
                );
            }
            return ArgumentExpUtils.TYPE_ARGUMENT.invokeStatic(
                ArgumentExpUtils.METHOD_CREATE_ARGUMENT_SIMPLE,

                ExpressionDef.constant(TypeDef.erasure(componentType)),
                ExpressionDef.nullValue()
            );
        } else if (type.getTypeArguments().isEmpty()) {
            return ExpressionDef.nullValue();
        }
        return null;
    }

    private ExpressionDef resolveInnerTypeArgumentIfNeeded(ExpressionDef argumentExpression, ClassElement type) {
        if (isInternalGenericTypeContainer(type.getFirstTypeArgument().orElse(null))) {
            return resolveFirstTypeArgument(argumentExpression);
        }
        return argumentExpression;
    }

    private boolean isInternalGenericTypeContainer(@Nullable ClassElement type) {
        return type != null && type.isAssignable(BeanRegistration.class);
    }

    private ExpressionDef resolveFirstTypeArgument(ExpressionDef argumentExpression) {
        return argumentExpression.invoke(GET_TYPE_PARAMETERS_METHOD).arrayElement(0);
    }

    private ExpressionDef resolveSecondTypeArgument(ExpressionDef argumentExpression) {
        return argumentExpression.invoke(GET_TYPE_PARAMETERS_METHOD).arrayElement(1);
    }

    private boolean isValueType(AnnotationMetadata annotationMetadata) {
        if (annotationMetadata != null) {
            return annotationMetadata.hasDeclaredStereotype(Value.class) || annotationMetadata.hasDeclaredStereotype(Property.class);
        }
        return false;
    }

    private static boolean isAnnotatedWithParameter(AnnotationMetadata annotationMetadata) {
        if (annotationMetadata != null) {
            return annotationMetadata.hasDeclaredAnnotation(Parameter.class);
        }
        return false;
    }

    private boolean isParametrized(ParameterElement... parameters) {
        return Arrays.stream(parameters).anyMatch(p -> isAnnotatedWithParameter(p.getAnnotationMetadata()));
    }

    private boolean isParametrized() {
        if (factoryMethodDefinition != null) {
            return isParametrized(factoryMethodDefinition.methodElement().getParameters());
        }
        if (constructorDefinition != null) {
            return isParametrized(constructorDefinition.constructorElement().getParameters());
        }
        return false;
    }

    private void addConstructor(StaticBlock staticBlock) {
        if (superBeanDefinition) {
            classDefBuilder.addMethod(MethodDef.constructor()
                .addModifiers(Modifier.PUBLIC)
                .build((aThis, methodParameters)
                    -> aThis.superRef().invokeConstructor(

                    ExpressionDef.constant(beanTypeDef),
                    beanDefinitionTypeDef.getStaticField(staticBlock.constructorRefField)
                )));
        } else {
            MethodDef constructor = MethodDef.constructor()
                .addModifiers(Modifier.PROTECTED)
                .addParameters(Class.class, AbstractInitializableBeanDefinition.MethodOrFieldReference.class)
                .build((aThis, methodParameters) -> {

                    List<ExpressionDef> values = new ArrayList<>();
                    AnnotationMetadata annotationMetadata = this.annotationMetadata != null ? this.annotationMetadata : AnnotationMetadata.EMPTY_METADATA;

                    // 1: beanType
                    values.add(methodParameters.get(0));
                    // 2: `AbstractBeanDefinition2.MethodOrFieldReference.class` constructor
                    values.add(methodParameters.get(1));

                    // 3: annotationMetadata
                    if (annotationMetadata.isEmpty()) {
                        values.add(ExpressionDef.nullValue());
                    } else if (annotationMetadata instanceof AnnotationMetadataReference reference) {
                        values.add(AnnotationMetadataGenUtils.annotationMetadataReference(reference));
                    } else {
                        values.add(beanDefinitionTypeDef.getStaticField(staticBlock.annotationMetadataField));
                    }

                    // 4: `AbstractBeanDefinition2.MethodReference[].class` methodInjection
                    if (staticBlock.injectionMethodsField == null) {
                        values.add(ExpressionDef.nullValue());
                    } else {
                        values.add(beanDefinitionTypeDef.getStaticField(staticBlock.injectionMethodsField));
                    }
                    // 5: `AbstractBeanDefinition2.FieldReference[].class` fieldInjection
                    if (staticBlock.injectionFieldsField == null) {
                        values.add(ExpressionDef.nullValue());
                    } else {
                        values.add(beanDefinitionTypeDef.getStaticField(staticBlock.injectionFieldsField));
                    }
                    // 6: `AbstractBeanDefinition2.AnnotationReference[].class` annotationInjection
                    if (staticBlock.annotationInjectionsFieldType == null) {
                        values.add(ExpressionDef.nullValue());
                    } else {
                        values.add(beanDefinitionTypeDef.getStaticField(staticBlock.annotationInjectionsFieldType));
                    }
                    // 7: `ExecutableMethod[]` executableMethods
                    if (staticBlock.executableMethodsField == null) {
                        values.add(ExpressionDef.nullValue());
                    } else {
                        values.add(beanDefinitionTypeDef.getStaticField(staticBlock.executableMethodsField));
                    }
                    // 8: `Map<String, Argument<?>[]>` typeArgumentsMap
                    if (staticBlock.typeArgumentsField == null) {
                        values.add(ExpressionDef.nullValue());
                    } else {
                        values.add(beanDefinitionTypeDef.getStaticField(staticBlock.typeArgumentsField));
                    }
                    // 9: `PrecalculatedInfo`
                    values.add(beanDefinitionTypeDef.getStaticField(staticBlock.precalculatedInfoField));

                    if (BEAN_DEFINITION_CLASS_CONSTRUCTOR2.isPresent()) {
                        if (staticBlock.preStartConditionsField == null) {
                            // 10: Pre conditions
                            values.add(ClassTypeDef.of(Condition.class).array().instantiate());
                            // 11: Post conditions
                            values.add(ClassTypeDef.of(Condition.class).array().instantiate());
                        } else {
                            // 10: Pre conditions
                            values.add(beanDefinitionTypeDef.getStaticField(staticBlock.preStartConditionsField));
                            // 11: Post conditions
                            values.add(beanDefinitionTypeDef.getStaticField(staticBlock.postStartConditionsField));
                        }
                        // 12: Exception
                        values.add(beanDefinitionTypeDef.getStaticField(staticBlock.failedInitializationField));

                        return aThis.superRef(TYPE_ABSTRACT_BEAN_DEFINITION_AND_REFERENCE).invokeConstructor(BEAN_DEFINITION_CLASS_CONSTRUCTOR2.get(), values);

                    } else if (BEAN_DEFINITION_CLASS_CONSTRUCTOR1.isPresent()) {
                        return aThis.superRef(TYPE_ABSTRACT_BEAN_DEFINITION_AND_REFERENCE).invokeConstructor(BEAN_DEFINITION_CLASS_CONSTRUCTOR1.get(), values);
                    } else {
                        throw new IllegalStateException();
                    }
                });
            classDefBuilder.addMethod(constructor);
            classDefBuilder.addMethod(MethodDef.constructor()
                .addModifiers(Modifier.PUBLIC)
                .build((aThis, methodParameters)
                    -> aThis.invokeConstructor(
                    constructor,

                    ExpressionDef.constant(beanTypeDef),
                    beanDefinitionTypeDef.getStaticField(FIELD_CONSTRUCTOR, ClassTypeDef.of(AbstractInitializableBeanDefinition.MethodOrFieldReference.class))
                )));
        }
    }

    private boolean isContainerType() {
        return beanTypeElement.isArray() || DefaultArgument.CONTAINER_TYPES.stream().anyMatch(c -> c.equals(beanFullClassName));
    }

    private static boolean isConfigurationProperties(AnnotationMetadata annotationMetadata) {
        return isIterable(annotationMetadata) || annotationMetadata.hasStereotype(ConfigurationReader.class);
    }

    private static boolean isIterable(AnnotationMetadata annotationMetadata) {
        return annotationMetadata.hasDeclaredStereotype(EachProperty.class) || annotationMetadata.hasDeclaredStereotype(EachBean.class);
    }

    private ExpressionDef getNewMethodReference(TypedElement beanType,
                                                MethodElement methodElement,
                                                AnnotationMetadata annotationMetadata,
                                                boolean isPostConstructMethod,
                                                boolean isPreDestroyMethod) {
        annotationMetadata = annotationMetadata.getTargetAnnotationMetadata();
        if (annotationMetadata instanceof AnnotationMetadataHierarchy hierarchy) {
            annotationMetadata = hierarchy.merge();
        }
        List<ExpressionDef> values = new ArrayList<>(
            List.of(
                // 1: declaringType
                ExpressionDef.constant(TypeDef.erasure(beanType)),
                // 2: methodName
                ExpressionDef.constant(methodElement.getName()),
                // 3: arguments
                !methodElement.hasParameters() ? ExpressionDef.nullValue() : ArgumentExpUtils.pushBuildArgumentsForMethod(
                    this.annotationMetadataDefaults,
                    ClassElement.of(beanFullClassName),
                    beanDefinitionTypeDef,
                    Arrays.asList(methodElement.getParameters()),
                    loadClassValueExpressionFn
                ),
                // 4: annotationMetadata
                getAnnotationMetadataExpression(annotationMetadata)
            )
        );
        if (isPreDestroyMethod || isPostConstructMethod) {
            // 5: isPostConstructMethod
            values.add(ExpressionDef.constant(isPostConstructMethod));
            // 6: isPreDestroyMethod
            values.add(ExpressionDef.constant(isPreDestroyMethod));

            return ClassTypeDef.of(AbstractInitializableBeanDefinition.MethodReference.class)
                .instantiate(
                    METHOD_REFERENCE_CONSTRUCTOR_POST_PRE, values
                );
        } else {
            return ClassTypeDef.of(AbstractInitializableBeanDefinition.MethodReference.class)
                .instantiate(
                    METHOD_REFERENCE_CONSTRUCTOR, values
                );
        }
    }

    private ExpressionDef getNewFieldReference(TypedElement declaringType, FieldElement fieldElement) {
        MutableAnnotationMetadata fieldAnnotationMetadata = MutableAnnotationMetadata.of(
            new AnnotationMetadataHierarchy(
                fieldElement.getType().getTypeAnnotationMetadata(),
                fieldElement.getAnnotationMetadata()
            )
        );
        return ClassTypeDef.of(AbstractInitializableBeanDefinition.FieldReference.class)
            .instantiate(
                FIELD_REFERENCE_CONSTRUCTOR,

                // 1: declaringType
                ExpressionDef.constant(TypeDef.erasure(declaringType)),
                // 2: argument
                ArgumentExpUtils.pushCreateArgument(
                    this.annotationMetadataDefaults,
                    ClassElement.of(beanFullClassName),
                    beanDefinitionTypeDef,
                    fieldElement.getName(),
                    fieldElement.getGenericType(),
                    fieldAnnotationMetadata,
                    fieldElement.getGenericType().getTypeArguments(),
                    loadClassValueExpressionFn
                )
            );
    }

    private ExpressionDef getNewAnnotationReference(TypedElement referencedType) {
        return ClassTypeDef.of(AbstractInitializableBeanDefinition.AnnotationReference.class)
            .instantiate(
                ANNOTATION_REFERENCE_CONSTRUCTOR,

                ClassTypeDef.of(Argument.class)
                    .invokeStatic(
                        ARGUMENT_OF_METHOD,

                        ExpressionDef.constant(TypeDef.erasure(referencedType))
                    )
            );
    }

    private ExpressionDef getAnnotationMetadataExpression(AnnotationMetadata annotationMetadata) {
        annotationMetadata = annotationMetadata.getTargetAnnotationMetadata();

        if (annotationMetadata == AnnotationMetadata.EMPTY_METADATA || annotationMetadata.isEmpty()) {
            return ExpressionDef.nullValue();
        } else if (annotationMetadata instanceof AnnotationMetadataHierarchy annotationMetadataHierarchy) {
            return AnnotationMetadataGenUtils.instantiateNewMetadataHierarchy(annotationMetadataHierarchy, loadClassValueExpressionFn);
        } else if (annotationMetadata instanceof MutableAnnotationMetadata mutableAnnotationMetadata) {
            return AnnotationMetadataGenUtils.instantiateNewMetadata(mutableAnnotationMetadata, loadClassValueExpressionFn);
        } else {
            throw new IllegalStateException("Unknown annotation metadata: " + annotationMetadata.getClass().getName());
        }
    }

    private static Method getBeanLookupMethod(String methodName, boolean requiresGenericType) {
        if (requiresGenericType) {
            return ReflectionUtils.getRequiredInternalMethod(
                AbstractInitializableBeanDefinition.class,
                methodName,
                BeanResolutionContext.class,
                BeanContext.class,
                int.class,
                Argument.class,
                Qualifier.class);
        } else {
            return ReflectionUtils.getRequiredInternalMethod(
                AbstractInitializableBeanDefinition.class,
                methodName,
                BeanResolutionContext.class,
                BeanContext.class,
                int.class,
                Qualifier.class
            );
        }
    }

    private static Method getBeanLookupMethodForArgument(String methodName, boolean requiresGenericType) {
        if (requiresGenericType) {
            return ReflectionUtils.getRequiredInternalMethod(
                AbstractInitializableBeanDefinition.class,
                methodName,
                BeanResolutionContext.class,
                BeanContext.class,
                int.class,
                int.class,
                Argument.class,
                Qualifier.class);
        }
        return ReflectionUtils.getRequiredInternalMethod(
            AbstractInitializableBeanDefinition.class,
            methodName,
            BeanResolutionContext.class,
            BeanContext.class,
            int.class,
            int.class,
            Qualifier.class);
    }

    @Override
    public String getName() {
        return beanDefinitionName;
    }

    @Override
    public boolean isProtected() {
        return false;
    }

    @Override
    public boolean isPublic() {
        return true;
    }

    @Override
    public Object getNativeType() {
        return this;
    }

    @Override
    public Collection<Element> getInjectionPoints() {
        if (allFields.isEmpty() && allMethods.isEmpty()) {
            return Collections.emptyList();
        } else {
            Collection<Element> injectionPoints = new ArrayList<>();
            for (FieldDefinition<ClassElement, FieldElement> fieldInjectionPoint : allFields) {
                injectionPoints.add(fieldInjectionPoint.fieldElement());
            }
            for (MethodDefinition<ClassElement, MethodElement> methodInjectionPoint : allMethods) {
                injectionPoints.add(methodInjectionPoint.methodElement());
            }
            return Collections.unmodifiableCollection(injectionPoints);
        }
    }

    @Override
    public boolean isAbstract() {
        return this.isAbstract;
    }

    @Override
    public <T extends Annotation> Element annotate(String annotationType, Consumer<AnnotationValueBuilder<T>> consumer) {
        this.beanProducingElement.annotate(annotationType, consumer);
        return this;
    }

    @Override
    public Element removeAnnotation(String annotationType) {
        this.beanProducingElement.removeAnnotation(annotationType);
        return this;
    }

    @Override
    public <T extends Annotation> Element removeAnnotationIf(Predicate<AnnotationValue<T>> predicate) {
        this.beanProducingElement.removeAnnotationIf(predicate);
        return this;
    }

    @Override
    public Element removeStereotype(String annotationType) {
        this.beanProducingElement.removeStereotype(annotationType);
        return this;
    }

    @Override
    public ClassElement getDeclaringClass() {
        final Element beanProducingElement = this.beanProducingElement;
        return getDeclaringType(beanProducingElement);
    }

    private ClassElement getDeclaringType(Element beanProducingElement) {
        if (beanProducingElement instanceof ClassElement element) {
            return element;
        } else if (beanProducingElement instanceof MemberElement element) {
            return element.getDeclaringType();
        } else if (beanProducingElement instanceof BeanElementBuilder builder) {
            return builder.getDeclaringElement();
        } else {
            return this.beanTypeElement;
        }
    }

    @Override
    public Element getProducingElement() {
        return beanProducingElement;
    }

    @Override
    public Set<ClassElement> getBeanTypes() {
        final String[] types = this.annotationMetadata.stringValues(Bean.class, "typed");
        if (ArrayUtils.isNotEmpty(types)) {
            HashSet<ClassElement> classElements = new HashSet<>();
            for (String type : types) {
                visitorContext.getClassElement(type).ifPresent(classElements::add);
            }
            return Collections.unmodifiableSet(classElements);
        } else {
            final Optional<ClassElement> superType = beanTypeElement.getSuperType();
            final Collection<ClassElement> interfaces = beanTypeElement.getInterfaces();
            if (superType.isPresent() || !interfaces.isEmpty()) {
                Set<ClassElement> beanTypes = new HashSet<>();
                beanTypes.add(beanTypeElement);
                populateBeanTypes(new HashSet<>(), beanTypes, superType.orElse(null), interfaces);
                return Collections.unmodifiableSet(beanTypes);
            } else {
                return Collections.singleton(beanTypeElement);
            }
        }
    }

    private void populateBeanTypes(Set<String> processedTypes, Set<ClassElement> beanTypes, ClassElement superType, Collection<ClassElement> interfaces) {
        for (ClassElement anInterface : interfaces) {
            final String n = anInterface.getName();
            if (!processedTypes.contains(n)) {
                processedTypes.add(n);
                beanTypes.add(anInterface);
                populateBeanTypes(processedTypes, beanTypes, null, anInterface.getInterfaces());
            }
        }
        if (superType != null) {
            final String n = superType.getName();
            if (!processedTypes.contains(n)) {
                processedTypes.add(n);
                beanTypes.add(superType);
                final ClassElement next = superType.getSuperType().orElse(null);
                populateBeanTypes(processedTypes, beanTypes, next, superType.getInterfaces());
            }
        }
    }

    @Override
    public Optional<String> getScope() {
        return annotationMetadata.getAnnotationNameByStereotype(AnnotationUtil.SCOPE);
    }

    @Override
    public Collection<String> getQualifiers() {
        return Collections.unmodifiableList(annotationMetadata.getAnnotationNamesByStereotype(AnnotationUtil.QUALIFIER));
    }

    @Override
    public BeanElementBuilder addAssociatedBean(ClassElement type, VisitorContext visitorContext) {
        if (visitorContext instanceof BeanElementVisitorContext context) {
            final Element[] originatingElements = getOriginatingElements();
            return context
                .addAssociatedBean(originatingElements[0], type);
        }
        return BeanElement.super.addAssociatedBean(type, visitorContext);
    }

    @Override
    public Element[] getOriginatingElements() {
        return this.originatingElements.getOriginatingElements();
    }

    @Override
    public void addOriginatingElement(Element element) {
        originatingElements.addOriginatingElement(element);
    }

    /**
     * Sets whether this bean is a proxied type.
     *
     * @param proxiedBean   True if it proxied
     * @param isProxyTarget True if the proxied bean is a retained target
     */
    public void setProxiedBean(boolean proxiedBean, boolean isProxyTarget) {
        this.proxiedBean = proxiedBean;
        this.isProxyTarget = isProxyTarget;
    }

    /**
     * Finish any work writing beans.
     */
    @Internal
    public static void finish() {
        AbstractAnnotationMetadataBuilder.clearMutated();
        AbstractAnnotationMetadataBuilder.clearCaches();
        EvaluatedExpressionProcessor.reset();
    }

    @Internal
    private static final class AnnotationVisitData {
        final TypedElement memberBeanType;
        final String memberPropertyName;
        final MethodElement memberPropertyGetter;
        final String requiredValue;
        final String notEqualsValue;

        public AnnotationVisitData(TypedElement memberBeanType,
                                   String memberPropertyName,
                                   MethodElement memberPropertyGetter,
                                   @Nullable String requiredValue,
                                   @Nullable String notEqualsValue) {
            this.memberBeanType = memberBeanType;
            this.memberPropertyName = memberPropertyName;
            this.memberPropertyGetter = memberPropertyGetter;
            this.requiredValue = requiredValue;
            this.notEqualsValue = notEqualsValue;
        }
    }

    /**
     * The custom initializer builder.
     */
    public interface CustomInitializerBuilder {

        /**
         * The builder.
         *
         * @param statements The statements
         * @param self       The self
         * @param parameters The parameters
         * @param values     The constructor values
         * @return The built instance
         */
        ExpressionDef build(List<StatementDef> statements,
                            VariableDef.This self,
                            List<VariableDef.MethodParameter> parameters,
                            List<? extends ExpressionDef> values);

    }

    private record InjectMethodSignature(
        VariableDef.This aThis,
        List<VariableDef.MethodParameter> methodParameters,
        VariableDef beanResolutionContext,
        VariableDef beanContext,
        VariableDef instanceVar
    ) {
        private InjectMethodSignature(VariableDef.This aThis,
                                      List<VariableDef.MethodParameter> methodParameters,
                                      VariableDef instanceVar) {
            this(aThis, methodParameters, methodParameters.get(0), methodParameters.get(1), instanceVar);
        }
    }

    private record StaticBlock(
        StatementDef statement,
        FieldDef annotationMetadataField,
        FieldDef failedInitializationField,
        FieldDef constructorRefField,
        @Nullable
        FieldDef injectionMethodsField,
        @Nullable
        FieldDef injectionFieldsField,
        @Nullable
        FieldDef annotationInjectionsFieldType,
        @Nullable
        FieldDef typeArgumentsField,
        @Nullable
        FieldDef executableMethodsField,
        FieldDef precalculatedInfoField,
        @Nullable
        FieldDef preStartConditionsField,
        @Nullable
        FieldDef postStartConditionsField) {
    }

    sealed interface InjectCommand {
    }

    record InjectField(
        FieldDefinition<ClassElement, FieldElement> fieldDefinition) implements InjectCommand {
    }

    record InjectMethod(
        MethodDefinition<ClassElement, MethodElement> methodDefinition) implements InjectCommand {
    }

    record InjectMethodConfigurationBuilder(MethodElement methodElement,
                                            AnnotationMetadata annotationMetadata,
                                            List<MethodDefinition<ClassElement, MethodElement>> builderMethods) implements InjectCommand {
    }

    record InjectFieldConfigurationBuilder(FieldElement fieldElement,
                                           AnnotationMetadata annotationMetadata,
                                           List<MethodDefinition<ClassElement, MethodElement>> builderMethods) implements InjectCommand {
    }

}
