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

import io.micronaut.aop.chain.ConstructorInterceptorChain;
import io.micronaut.aop.chain.MethodInterceptorChain;
import io.micronaut.aop.writer.AopProxyWriter;
import io.micronaut.context.AbstractBeanDefinitionBeanConstructor;
import io.micronaut.context.AbstractExecutableMethod;
import io.micronaut.context.AbstractInitializableBeanDefinition;
import io.micronaut.context.AbstractInitializableBeanDefinitionAndReference;
import io.micronaut.context.BeanContext;
import io.micronaut.context.BeanRegistration;
import io.micronaut.context.BeanResolutionContext;
import io.micronaut.context.DefaultBeanContext;
import io.micronaut.context.Qualifier;
import io.micronaut.context.RequiresCondition;
import io.micronaut.context.annotation.Any;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.ConfigurationBuilder;
import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.ConfigurationReader;
import io.micronaut.context.annotation.Context;
import io.micronaut.context.annotation.DefaultScope;
import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.EachProperty;
import io.micronaut.context.annotation.InjectScope;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.context.annotation.Primary;
import io.micronaut.context.annotation.Property;
import io.micronaut.context.annotation.PropertySource;
import io.micronaut.context.annotation.Requirements;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Value;
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
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NextMajorVersion;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.beans.BeanConstructor;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.convert.ConversionServiceProvider;
import io.micronaut.core.expressions.EvaluatedExpressionReference;
import io.micronaut.core.naming.NameUtils;
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
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.inject.ExecutableMethodsDefinition;
import io.micronaut.inject.InitializingBeanDefinition;
import io.micronaut.inject.InjectableBeanDefinition;
import io.micronaut.inject.InjectionPoint;
import io.micronaut.inject.InstantiatableBeanDefinition;
import io.micronaut.inject.ParametrizedInstantiatableBeanDefinition;
import io.micronaut.inject.ProxyBeanDefinition;
import io.micronaut.inject.ValidatedBeanDefinition;
import io.micronaut.inject.annotation.AbstractAnnotationMetadataBuilder;
import io.micronaut.inject.annotation.AnnotationMetadataGenUtils;
import io.micronaut.inject.annotation.AnnotationMetadataHierarchy;
import io.micronaut.inject.annotation.AnnotationMetadataReference;
import io.micronaut.inject.annotation.MutableAnnotationMetadata;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.ConstructorElement;
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
import io.micronaut.inject.configuration.ConfigurationMetadataBuilder;
import io.micronaut.inject.qualifiers.AnyQualifier;
import io.micronaut.inject.qualifiers.Qualifiers;
import io.micronaut.inject.visitor.BeanElementVisitor;
import io.micronaut.inject.visitor.BeanElementVisitorContext;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.sourcegen.bytecode.ByteCodeWriter;
import io.micronaut.sourcegen.model.AnnotationDef;
import io.micronaut.sourcegen.model.ClassDef;
import io.micronaut.sourcegen.model.ClassTypeDef;
import io.micronaut.sourcegen.model.ExpressionDef;
import io.micronaut.sourcegen.model.FieldDef;
import io.micronaut.sourcegen.model.MethodDef;
import io.micronaut.sourcegen.model.ObjectDef;
import io.micronaut.sourcegen.model.StatementDef;
import io.micronaut.sourcegen.model.TypeDef;
import io.micronaut.sourcegen.model.VariableDef;
import jakarta.inject.Singleton;
import org.objectweb.asm.Type;

import javax.lang.model.element.Modifier;
import java.io.IOException;
import java.io.OutputStream;
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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import java.util.stream.Stream;

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
@Internal
public final class BeanDefinitionWriter implements ClassOutputWriter, BeanDefinitionVisitor, BeanElement, Toggleable {
    @NextMajorVersion("Inline as true")
    public static final String OMIT_CONFPROP_INJECTION_POINTS = "micronaut.processing.omit.confprop.injectpoints";

    public static final String CLASS_SUFFIX = "$Definition";

    private static final Constructor<AbstractBeanDefinitionBeanConstructor> CONSTRUCTOR_ABSTRACT_CONSTRUCTOR_IP = ReflectionUtils.findConstructor(
            AbstractBeanDefinitionBeanConstructor.class,
            BeanDefinition.class)
        .orElseThrow(() -> new ClassGenerationException("Invalid version of Micronaut present on the class path"));

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

    private static final String METHOD_NAME_INSTANTIATE = "instantiate";
    private static final Method METHOD_BEAN_CONSTRUCTOR_INSTANTIATE = ReflectionUtils.getRequiredMethod(
        BeanConstructor.class,
        METHOD_NAME_INSTANTIATE,
        Object[].class
    );

    private static final Method METHOD_DESCRIPTOR_CONSTRUCTOR_INSTANTIATE = ReflectionUtils.getRequiredMethod(ConstructorInterceptorChain.class, METHOD_NAME_INSTANTIATE,
        BeanResolutionContext.class,
        BeanContext.class,
        List.class,
        BeanDefinition.class,
        BeanConstructor.class,
        int.class,
        Object[].class
    );

    private static final Method METHOD_GET_BEAN = ReflectionUtils.getRequiredInternalMethod(DefaultBeanContext.class, "getBean", BeanResolutionContext.class, Class.class, Qualifier.class);
    private static final Method COLLECTION_TO_ARRAY = ReflectionUtils.getRequiredInternalMethod(Collection.class, "toArray", Object[].class);

    private static final Method DISPOSE_INTERCEPTOR_METHOD =
        ReflectionUtils.getRequiredInternalMethod(MethodInterceptorChain.class, "dispose",
            BeanResolutionContext.class,
            BeanContext.class,
            BeanDefinition.class,
            ExecutableMethod.class,
            Object.class);

    private static final Method INITIALIZE_INTERCEPTOR_METHOD =
        ReflectionUtils.getRequiredInternalMethod(MethodInterceptorChain.class, "initialize",
            BeanResolutionContext.class,
            BeanContext.class,
            BeanDefinition.class,
            ExecutableMethod.class,
            Object.class);

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
    private static final String MESSAGE_ONLY_SINGLE_CALL_PERMITTED = "Only a single call to visitBeanFactoryMethod(..) is permitted";

    private static final int INJECT_METHOD_BEAN_RESOLUTION_CONTEXT_PARAM = 0;
    private static final int INJECT_METHOD_BEAN_CONTEXT_PARAM = 1;

    private static final int INSTANTIATE_METHOD_BEAN_RESOLUTION_CONTEXT_PARAM = 0;
    private static final int INSTANTIATE_METHOD_BEAN_CONTEXT_PARAM = 1;

    private static final Method METHOD_BEAN_CONTEXT_GET_CONVERSION_SERVICE = ReflectionUtils.getRequiredMethod(ConversionServiceProvider.class, "getConversionService");

    private static final Method METHOD_INVOKE_INTERNAL =
        ReflectionUtils.getRequiredInternalMethod(AbstractExecutableMethod.class, "invokeInternal", Object.class, Object[].class);

    private static final Method METHOD_INITIALIZE =
        ReflectionUtils.getRequiredInternalMethod(InitializingBeanDefinition.class, "initialize", BeanResolutionContext.class, BeanContext.class, Object.class);

    private static final Method METHOD_DISPOSE =
        ReflectionUtils.getRequiredInternalMethod(DisposableBeanDefinition.class, "dispose", BeanResolutionContext.class, BeanContext.class, Object.class);

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
    private static final Method DO_INSTANTIATE_METHOD = ReflectionUtils.getRequiredMethod(AbstractInitializableBeanDefinition.class, "doInstantiate", BeanResolutionContext.class, BeanContext.class, Map.class);
    private static final Method INSTANTIATE_METHOD = ReflectionUtils.getRequiredMethod(InstantiatableBeanDefinition.class, "instantiate", BeanResolutionContext.class, BeanContext.class);
    private static final Method COLLECTION_UTILS_ENUM_SET_METHOD = ReflectionUtils.getRequiredMethod(CollectionUtils.class, "enumSet", Enum[].class);
    private static final Method IS_INNER_CONFIGURATION_METHOD = ReflectionUtils.getRequiredMethod(AbstractInitializableBeanDefinition.class, "isInnerConfiguration", Class.class);
    private static final Method CONTAINS_METHOD = ReflectionUtils.getRequiredMethod(Collection.class, "contains", Object.class);
    private static final Method GET_EXPOSED_TYPES_METHOD = ReflectionUtils.getRequiredMethod(AbstractInitializableBeanDefinition.class, "getExposedTypes");
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
    private static final Constructor<AbstractExecutableMethod> ABSTRACT_EXECUTABLE_METHOD_CONSTRUCTOR = ReflectionUtils.getRequiredInternalConstructor(AbstractExecutableMethod.class, Class.class, String.class);
    private static final Method GET_TYPE_PARAMETERS_METHOD = ReflectionUtils.getRequiredInternalMethod(TypeVariableResolver.class, "getTypeParameters");
    private static final Method ARGUMENT_OF_METHOD = ReflectionUtils.getRequiredInternalMethod(Argument.class, "of", Class.class);

    private final String beanFullClassName;
    private final String beanDefinitionName;
    private final TypeDef beanTypeDef;
    private final Map<String, MethodDef> loadTypeMethods = new LinkedHashMap<>();
    private final String packageName;
    private final String beanSimpleClassName;
    private final ClassTypeDef beanDefinitionTypeDef;
    private final boolean isInterface;
    private final boolean isAbstract;
    private final boolean isConfigurationProperties;
    private final Element beanProducingElement;
    private final ClassElement beanTypeElement;
    private final VisitorContext visitorContext;
    private final List<String> beanTypeInnerClasses;
    private final EvaluatedExpressionProcessor evaluatedExpressionProcessor;

    private boolean beanFinalized = false;
    private ClassTypeDef superType = TYPE_ABSTRACT_BEAN_DEFINITION_AND_REFERENCE;
    private boolean superBeanDefinition = false;
    private boolean isSuperFactory = false;
    private final AnnotationMetadata annotationMetadata;
    private boolean preprocessMethods = false;
    private Map<String, Map<String, ClassElement>> typeArguments;
    @Nullable
    private String interceptedType;

    private final List<FieldVisitData> fieldInjectionPoints = new ArrayList<>(2);
    private final List<MethodVisitData> methodInjectionPoints = new ArrayList<>(2);
    private final List<MethodVisitData> postConstructMethodVisits = new ArrayList<>(2);
    private final List<MethodVisitData> preDestroyMethodVisits = new ArrayList<>(2);
    private final List<MethodVisitData> allMethodVisits = new ArrayList<>(2);
    private final Map<ClassElement, List<AnnotationVisitData>> annotationInjectionPoints = new LinkedHashMap<>(2);
    private final Map<String, Boolean> isLifeCycleCache = new HashMap<>(2);
    private ExecutableMethodsDefinitionWriter executableMethodsDefinitionWriter;

    private Object constructor; // MethodElement or FieldElement
    private boolean disabled = false;

    private final boolean keepConfPropInjectPoints;
    private boolean proxiedBean = false;
    private boolean isProxyTarget = false;

    private String proxyBeanDefinitionName, proxyBeanTypeName;

    private final OriginatingElements originatingElements;

    private final ClassDef.ClassDefBuilder classDefBuilder;

    private BuildMethodDefinition buildMethodDefinition;
    private final List<InjectMethodCommand> injectCommands = new ArrayList<>();
    private ConfigBuilderInjectCommand configBuilderInjectCommand;
    private boolean validated;

    /**
     * Creates a bean definition writer.
     *
     * @param classElement   The class element
     * @param visitorContext The visitor context
     */
    public BeanDefinitionWriter(ClassElement classElement,
                                VisitorContext visitorContext) {
        this(classElement, OriginatingElements.of(classElement), visitorContext, null);
    }

    /**
     * Creates a bean definition writer.
     *
     * @param classElement        The class element
     * @param originatingElements The originating elements
     * @param visitorContext      The visitor context
     */
    public BeanDefinitionWriter(ClassElement classElement,
                                OriginatingElements originatingElements,
                                VisitorContext visitorContext) {
        this(classElement, originatingElements, visitorContext, null);
    }

    /**
     * Creates a bean definition writer.
     *
     * @param beanProducingElement The bean producing element
     * @param originatingElements  The originating elements
     * @param visitorContext       The visitor context
     * @param uniqueIdentifier     An optional unique identifier to include in the bean name
     */
    public BeanDefinitionWriter(Element beanProducingElement,
                                OriginatingElements originatingElements,
                                VisitorContext visitorContext,
                                @Nullable Integer uniqueIdentifier) {
        this.originatingElements = originatingElements;
        this.beanProducingElement = beanProducingElement;
        if (beanProducingElement instanceof ClassElement classElement) {
            autoApplyNamedToBeanProducingElement(classElement);
            if (classElement.isPrimitive()) {
                throw new IllegalArgumentException("Primitive beans can only be created from factories");
            }
            this.beanTypeElement = classElement;
            this.packageName = classElement.getPackageName();
            this.isInterface = classElement.isInterface();
            this.isAbstract = classElement.isAbstract();
            this.beanFullClassName = classElement.getName();
            this.beanSimpleClassName = classElement.getSimpleName();
            this.beanDefinitionName = getBeanDefinitionName(packageName, beanSimpleClassName);
        } else if (beanProducingElement instanceof MethodElement factoryMethodElement) {
            autoApplyNamedToBeanProducingElement(beanProducingElement);
            final ClassElement producedElement = factoryMethodElement.getGenericReturnType();
            this.beanTypeElement = producedElement;
            this.packageName = producedElement.getPackageName();
            this.isInterface = producedElement.isInterface();
            this.isAbstract = false;
            this.beanFullClassName = producedElement.getName();
            this.beanSimpleClassName = producedElement.getSimpleName();
            String upperCaseMethodName = NameUtils.capitalize(factoryMethodElement.getName());
            if (uniqueIdentifier == null) {
                throw new IllegalArgumentException("Factory methods require passing a unique identifier");
            }
            final ClassElement declaringType = factoryMethodElement.getOwningType();
            this.beanDefinitionName = declaringType.getPackageName() + "." + prefixClassName(declaringType.getSimpleName()) + "$" + upperCaseMethodName + uniqueIdentifier + CLASS_SUFFIX;
        } else if (beanProducingElement instanceof PropertyElement factoryPropertyElement) {
            autoApplyNamedToBeanProducingElement(beanProducingElement);
            final ClassElement producedElement = factoryPropertyElement.getGenericType();
            this.beanTypeElement = producedElement;
            this.packageName = producedElement.getPackageName();
            this.isInterface = producedElement.isInterface();
            this.isAbstract = beanProducingElement.isAbstract();
            this.beanFullClassName = producedElement.getName();
            this.beanSimpleClassName = producedElement.getSimpleName();
            String upperCaseMethodName = NameUtils.capitalize(factoryPropertyElement.getName());
            if (uniqueIdentifier == null) {
                throw new IllegalArgumentException("Factory methods require passing a unique identifier");
            }
            final ClassElement declaringType = factoryPropertyElement.getOwningType();
            this.beanDefinitionName = declaringType.getPackageName() + "." + prefixClassName(declaringType.getSimpleName()) + "$" + upperCaseMethodName + uniqueIdentifier + CLASS_SUFFIX;
        } else if (beanProducingElement instanceof FieldElement factoryMethodElement) {
            autoApplyNamedToBeanProducingElement(beanProducingElement);
            final ClassElement producedElement = factoryMethodElement.getGenericField();
            this.beanTypeElement = producedElement;
            this.packageName = producedElement.getPackageName();
            this.isInterface = producedElement.isInterface();
            this.isAbstract = false;
            this.beanFullClassName = producedElement.getName();
            this.beanSimpleClassName = producedElement.getSimpleName();
            String fieldName = NameUtils.capitalize(factoryMethodElement.getName());
            if (uniqueIdentifier == null) {
                throw new IllegalArgumentException("Factory fields require passing a unique identifier");
            }
            final ClassElement declaringType = factoryMethodElement.getOwningType();
            this.beanDefinitionName = declaringType.getPackageName() + "." + prefixClassName(declaringType.getSimpleName()) + "$" + fieldName + uniqueIdentifier + CLASS_SUFFIX;
        } else if (beanProducingElement instanceof BeanElementBuilder beanElementBuilder) {
            this.beanTypeElement = beanElementBuilder.getBeanType();
            this.packageName = this.beanTypeElement.getPackageName();
            this.isInterface = this.beanTypeElement.isInterface();
            this.isAbstract = beanElementBuilder.getProducingElement() instanceof ClassElement && this.beanTypeElement.isAbstract();
            this.beanFullClassName = this.beanTypeElement.getName();
            this.beanSimpleClassName = this.beanTypeElement.getSimpleName();
            if (uniqueIdentifier == null) {
                throw new IllegalArgumentException("Beans produced by addAssociatedBean(..) require passing a unique identifier");
            }
            final Element originatingElement = beanElementBuilder.getOriginatingElement();
            if (originatingElement instanceof ClassElement originatingClass) {
                this.beanDefinitionName = getAssociatedBeanName(uniqueIdentifier, originatingClass);
            } else if (originatingElement instanceof MethodElement methodElement) {
                ClassElement originatingClass = methodElement.getDeclaringType();
                this.beanDefinitionName = getAssociatedBeanName(uniqueIdentifier, originatingClass);
            } else {
                throw new IllegalArgumentException("Unsupported originating element");
            }
        } else {
            throw new IllegalArgumentException("Unsupported element type: " + beanProducingElement.getClass().getName());
        }
        this.annotationMetadata = beanProducingElement.getTargetAnnotationMetadata();
        this.beanDefinitionTypeDef = ClassTypeDef.of(beanDefinitionName);
        this.beanTypeDef = TypeDef.erasure(beanTypeElement);
        this.isConfigurationProperties = isConfigurationProperties(annotationMetadata);
        validateExposedTypes(annotationMetadata, visitorContext);
        this.visitorContext = visitorContext;
        this.evaluatedExpressionProcessor = new EvaluatedExpressionProcessor(visitorContext, getOriginatingElement());
        evaluatedExpressionProcessor.processEvaluatedExpressions(this.annotationMetadata,
            beanTypeElement.getName().contains(BeanDefinitionVisitor.PROXY_SUFFIX) ? null : beanTypeElement);

        beanTypeInnerClasses = beanTypeElement.getEnclosedElements(ElementQuery.of(ClassElement.class))
            .stream()
            .filter(this::isConfigurationProperties)
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
            .addModifiers(Modifier.PUBLIC)
            .addAnnotation(AnnotationDef.builder(Generated.class).addMember("service", BeanDefinitionReference.class.getName()).build())
            .superclass(TypeDef.parameterized(superType, argumentType));
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
    @Nullable
    public ExecutableMethodsDefinitionWriter getExecutableMethodsWriter() {
        return executableMethodsDefinitionWriter;
    }

    @NonNull
    private String getAssociatedBeanName(@NonNull Integer uniqueIdentifier, ClassElement originatingClass) {
        return originatingClass.getPackageName() + "." + prefixClassName(originatingClass.getSimpleName()) + prefixClassName(beanSimpleClassName) + uniqueIdentifier + CLASS_SUFFIX;
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

    @NonNull
    private static String getBeanDefinitionName(String packageName, String className) {
        return packageName + "." + prefixClassName(className) + CLASS_SUFFIX;
    }

    private static String prefixClassName(String className) {
        if (className.startsWith("$")) {
            return className;
        }
        return "$" + className;
    }

    @NonNull
    @Override
    public ClassElement[] getTypeArguments() {
        if (hasTypeArguments()) {
            final Map<String, ClassElement> args = this.typeArguments.get(this.getBeanTypeName());
            if (CollectionUtils.isNotEmpty(args)) {
                return args.values().toArray(ClassElement.ZERO_CLASS_ELEMENTS);
            }
        }
        return BeanDefinitionVisitor.super.getTypeArguments();
    }

    @Override
    @NonNull
    public Map<String, ClassElement> getTypeArgumentMap() {
        if (hasTypeArguments()) {
            Map<String, ClassElement> args = this.typeArguments.get(this.getBeanTypeName());
            if (CollectionUtils.isNotEmpty(args)) {
                return Collections.unmodifiableMap(args);
            }
        }
        return Collections.emptyMap();
    }


    /**
     * @return The name of the bean definition reference class.
     */
    @Override
    @NonNull
    public String getBeanDefinitionReferenceClassName() {
        throw new IllegalStateException("Not supported!");
    }

    /**
     * @return The data for any post construct methods that were visited
     */
    public List<MethodVisitData> getPostConstructMethodVisits() {
        return Collections.unmodifiableList(postConstructMethodVisits);
    }

    @Override
    public boolean isInterface() {
        return isInterface;
    }

    @Override
    public boolean isSingleton() {
        return annotationMetadata.hasDeclaredStereotype(AnnotationUtil.SINGLETON);
    }

    @Override
    public void visitBeanDefinitionInterface(Class<? extends BeanDefinition> interfaceType) {
        this.classDefBuilder.addSuperinterface(TypeDef.of(interfaceType));
    }

    @Override
    public void visitSuperBeanDefinition(String name) {
        this.superBeanDefinition = true;
        this.superType = ClassTypeDef.of(name);
        classDefBuilder.superclass(superType);
    }

    @Override
    public void visitSuperBeanDefinitionFactory(String beanName) {
        this.superBeanDefinition = false;
        this.isSuperFactory = true;
    }

    @Override
    public String getBeanTypeName() {
        return beanFullClassName;
    }

    @Override
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

    @Override
    public void setInterceptedType(String typeName) {
        if (typeName != null) {
            classDefBuilder.addSuperinterface(TypeDef.of(AdvisedBeanType.class));
        }
        this.interceptedType = typeName;
    }

    @Override
    public Optional<Type> getInterceptedType() {
        throw new IllegalStateException("Not supported!");
    }

    @Override
    public boolean isValidated() {
        return validated;
    }

    @Override
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

    /**
     * <p>In the case where the produced class is produced by a factory method annotated with
     * {@link Bean} this method should be called.</p>
     *
     * @param factoryClass  The factory class
     * @param factoryMethod The factory method
     */
    @Override
    public void visitBeanFactoryMethod(ClassElement factoryClass,
                                       MethodElement factoryMethod) {
        if (constructor != null) {
            throw new IllegalStateException(MESSAGE_ONLY_SINGLE_CALL_PERMITTED);
        } else {
            constructor = factoryMethod;
            // now prepare the implementation of the build method. See BeanFactory interface
            visitBuildFactoryMethodDefinition(factoryClass, factoryMethod, factoryMethod.getParameters());
        }
    }

    /**
     * <p>In the case where the produced class is produced by a factory method annotated with
     * {@link Bean} this method should be called.</p>
     *
     * @param factoryClass  The factory class
     * @param factoryMethod The factory method
     * @param parameters    The parameters
     */
    @Override
    public void visitBeanFactoryMethod(ClassElement factoryClass,
                                       MethodElement factoryMethod,
                                       ParameterElement[] parameters) {
        if (constructor != null) {
            throw new IllegalStateException(MESSAGE_ONLY_SINGLE_CALL_PERMITTED);
        } else {
            constructor = factoryMethod;
            // now prepare the implementation of the build method. See BeanFactory interface
            visitBuildFactoryMethodDefinition(factoryClass, factoryMethod, parameters);
        }
    }

    /**
     * <p>In the case where the produced class is produced by a factory field annotated with
     * {@link Bean} this method should be called.</p>
     *
     * @param factoryClass The factory class
     * @param factoryField The factory field
     */
    @Override
    public void visitBeanFactoryField(ClassElement factoryClass, FieldElement factoryField) {
        if (constructor != null) {
            throw new IllegalStateException(MESSAGE_ONLY_SINGLE_CALL_PERMITTED);
        } else {
            constructor = factoryField;

            autoApplyNamedIfPresent(factoryField, factoryField.getAnnotationMetadata());
            // now prepare the implementation of the build method. See BeanFactory interface
            visitBuildFactoryMethodDefinition(factoryClass, factoryField);
        }
    }

    /**
     * Visits the constructor used to create the bean definition.
     *
     * @param constructor        The constructor
     * @param requiresReflection Whether invoking the constructor requires reflection
     * @param visitorContext     The visitor context
     */
    @Override
    public void visitBeanDefinitionConstructor(MethodElement constructor,
                                               boolean requiresReflection,
                                               VisitorContext visitorContext) {
        if (this.constructor == null) {
            this.constructor = constructor;

            // now prepare the implementation of the build method. See BeanFactory interface
            visitBuildConstructorDefinition(constructor, requiresReflection);

            evaluatedExpressionProcessor.processEvaluatedExpressions(constructor.getAnnotationMetadata(), null);
            for (ParameterElement parameter : constructor.getParameters()) {
                evaluatedExpressionProcessor.processEvaluatedExpressions(parameter.getAnnotationMetadata(), null);
            }
        }
    }

    @Override
    public void visitDefaultConstructor(AnnotationMetadata annotationMetadata, VisitorContext visitorContext) {
        if (this.constructor == null) {
            ClassElement bean = ClassElement.of(((ClassTypeDef) beanTypeDef).getName());
            MethodElement defaultConstructor = MethodElement.of(
                bean,
                annotationMetadata,
                bean,
                bean,
                "<init>"
            );
            constructor = defaultConstructor;

            // now prepare the implementation of the build method. See BeanFactory interface
            visitBuildConstructorDefinition(defaultConstructor, false);
        }
    }

    /**
     * Finalize the bean definition to the given output stream.
     */
    @SuppressWarnings("Duplicates")
    @Override
    public void visitBeanDefinitionEnd() {
        if (executableMethodsDefinitionWriter != null) {
            // Make sure the methods are written and annotation defaults are contributed
            executableMethodsDefinitionWriter.visitDefinitionEnd();
        }

        processAllBeanElementVisitors();

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

        classDefBuilder.addMethod(
            getBuildMethod(buildMethodDefinition)
        );
        if (!injectCommands.isEmpty()) {
            classDefBuilder.addMethod(
                getInjectMethod(injectCommands)
            );
        }

        if (buildMethodDefinition.postConstruct != null) {
            //  for "super bean definition" we only add code to trigger "initialize"
            if (!superBeanDefinition || buildMethodDefinition.postConstruct.intercepted) {
                classDefBuilder.addSuperinterface(TypeDef.of(InitializingBeanDefinition.class));

                // Create a new method that will be invoked by the intercepted chain
                MethodDef targetInitializeMethod = buildInitializeMethod(buildMethodDefinition.postConstruct, MethodDef.builder("initialize$intercepted")
                    .addModifiers(Modifier.PUBLIC)
                    .addParameters(BeanResolutionContext.class, BeanContext.class, Object.class)
                    .returns(Object.class));

                classDefBuilder.addMethod(
                    targetInitializeMethod
                );

                // Original initialize method is invoking the interceptor chain
                classDefBuilder.addMethod(
                    MethodDef.override(METHOD_INITIALIZE).build((aThis, methodParameters) -> {
                        ClassTypeDef executableMethodInterceptor = createExecutableMethodInterceptor(targetInitializeMethod, "InitializeInterceptor");
                        return interceptAndReturn(aThis, methodParameters, executableMethodInterceptor, INITIALIZE_INTERCEPTOR_METHOD);
                    })
                );
            }
        }

        if (buildMethodDefinition.preDestroy != null) {
            classDefBuilder.addSuperinterface(TypeDef.of(DisposableBeanDefinition.class));

            if (buildMethodDefinition.preDestroy.intercepted) {
                // Create a new method that will be invoked by the intercepted chain
                MethodDef targetDisposeMethod = buildDisposeMethod(buildMethodDefinition.preDestroy, MethodDef.builder("dispose$intercepted")
                    .addModifiers(Modifier.PUBLIC)
                    .addParameters(BeanResolutionContext.class, BeanContext.class, Object.class)
                    .returns(Object.class));

                classDefBuilder.addMethod(
                    targetDisposeMethod
                );

                // Original dispose method is invoking the interceptor chain
                classDefBuilder.addMethod(
                    MethodDef.override(METHOD_DISPOSE).build((aThis, methodParameters) -> {
                        ClassTypeDef executableMethodInterceptor = createExecutableMethodInterceptor(targetDisposeMethod, "DisposeInterceptor");
                        return interceptAndReturn(aThis, methodParameters, executableMethodInterceptor, DISPOSE_INTERCEPTOR_METHOD);
                    })
                );

            } else {
                classDefBuilder.addMethod(
                    buildDisposeMethod(buildMethodDefinition.preDestroy, MethodDef.override(METHOD_DISPOSE))
                );
            }
        }

        StaticBlock staticBlock = getStaticInitializer();

        classDefBuilder.addStaticInitializer(staticBlock.statement);

        addConstructor(staticBlock);

        loadTypeMethods.values().forEach(classDefBuilder::addMethod);

        this.beanFinalized = true;
    }

    private MethodDef getGetInterceptedType(TypeDef interceptedType) {
        return MethodDef.override(GET_INTERCEPTED_TYPE_METHOD)
            .build((aThis, methodParameters) -> ExpressionDef.constant(interceptedType).returning());
    }

    private MethodDef getBuildMethod(BuildMethodDefinition buildMethodDefinition) {
        boolean isParametrized = isParametrized(buildMethodDefinition.getParameters());

        MethodDef.MethodDefBuilder buildMethodBuilder;
        if (isParametrized) {
            buildMethodBuilder = MethodDef.override(DO_INSTANTIATE_METHOD);
            classDefBuilder.addSuperinterface(TypeDef.of(ParametrizedInstantiatableBeanDefinition.class));
        } else {
            buildMethodBuilder = MethodDef.override(INSTANTIATE_METHOD);
        }

        return buildMethodBuilder.build((aThis, methodParameters) -> StatementDef.multi(
            invokeCheckIfShouldLoadIfNecessary(aThis, methodParameters),
            buildInstance(
                aThis,
                methodParameters,
                buildMethodDefinition,
                instance -> onBeanInstance(aThis, methodParameters, buildMethodDefinition, instance),
                isParametrized
            )
        ));
    }

    private MethodDef getInjectMethod(List<InjectMethodCommand> injectCommands) {
        return MethodDef.override(INJECT_BEAN_METHOD)
            .build((aThis, methodParameters) -> {
                return methodParameters.get(2).cast(beanTypeDef).newLocal("beanInstance", instanceVar -> {
                    InjectMethodSignature injectMethodSignature = new InjectMethodSignature(aThis, methodParameters, instanceVar);
                    List<StatementDef> statements = new ArrayList<>();
                    boolean hasInjectPoint = false;
                    for (InjectMethodCommand injectCommand : injectCommands) {
                        statements.add(getInjectStatement(injectCommand, injectMethodSignature));
                        hasInjectPoint |= injectCommand.hasInjectScope();
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

    private StatementDef getInjectStatement(InjectMethodCommand injectionPoint, InjectMethodSignature injectMethodSignature) {
        if (injectionPoint instanceof SetterInjectionInjectCommand setterInjectionInjectCommand) {
            return setSetterValue(
                injectMethodSignature,
                setterInjectionInjectCommand.declaringType,
                setterInjectionInjectCommand.methodElement,
                setterInjectionInjectCommand.annotationMetadata,
                setterInjectionInjectCommand.requiresReflection,
                setterInjectionInjectCommand.isOptional
            );
        }
        if (injectionPoint instanceof InjectFieldInjectCommand injectFieldInjectCommand) {
            return injectField(
                injectMethodSignature,
                injectFieldInjectCommand.declaringType,
                injectFieldInjectCommand.fieldElement,
                injectFieldInjectCommand.fieldElement.getAnnotationMetadata(),
                injectFieldInjectCommand.requiresReflection
            );
        }
        if (injectionPoint instanceof InjectMethodInjectCommand injectMethodInjectCommand) {
            return injectMethod(
                injectMethodInjectCommand.methodElement,
                injectMethodInjectCommand.requiresReflection,
                injectMethodSignature.aThis,
                injectMethodSignature.methodParameters,
                injectMethodSignature.instanceVar,
                injectMethodInjectCommand.methodIndex
            );
        }
        if (injectionPoint instanceof InjectFieldValueInjectCommand injectFieldValueInjectCommand) {
            return setFieldValue(
                injectMethodSignature,
                injectFieldValueInjectCommand.declaringType,
                injectFieldValueInjectCommand.fieldElement,
                injectFieldValueInjectCommand.requiresReflection,
                injectFieldValueInjectCommand.isOptional
            );
        }
        if (injectionPoint instanceof ConfigMethodBuilderInjectPointCommand configBuilderMethodInjectPoint) {
            String factoryMethod = configBuilderMethodInjectPoint.configBuilderState.getAnnotationMetadata()
                .stringValue(ConfigurationBuilder.class, "factoryMethod").orElse(null);

            ClassTypeDef builderType = ClassTypeDef.of(configBuilderMethodInjectPoint.type);
            if (StringUtils.isNotEmpty(factoryMethod)) {
                return builderType.invokeStatic(factoryMethod, builderType).newLocal("builder" + NameUtils.capitalize(configBuilderMethodInjectPoint.methodName), builderVar -> {
                    List<StatementDef> statements =
                        getBuilderMethodStatements(injectMethodSignature, configBuilderMethodInjectPoint.builderPoints, builderVar);

                    String propertyName = NameUtils.getPropertyNameForGetter(configBuilderMethodInjectPoint.methodName);
                    String setterName = NameUtils.setterNameFor(propertyName);

                    statements.add(injectMethodSignature.instanceVar
                        .invoke(setterName, TypeDef.VOID, builderVar));

                    return StatementDef.multi(statements);
                });
            } else {
                return injectMethodSignature.instanceVar
                    .invoke(configBuilderMethodInjectPoint.methodName, builderType)
                    .newLocal("builder" + NameUtils.capitalize(configBuilderMethodInjectPoint.methodName), builderVar -> StatementDef.multi(
                        getBuilderMethodStatements(injectMethodSignature, configBuilderMethodInjectPoint.builderPoints, builderVar)
                    ));
            }
        }
        if (injectionPoint instanceof ConfigFieldBuilderInjectCommand configBuilderFieldInjectPoint) {
            String factoryMethod = configBuilderFieldInjectPoint.configBuilderState.getAnnotationMetadata()
                .stringValue(ConfigurationBuilder.class, "factoryMethod").orElse(null);
            ClassTypeDef builderType = ClassTypeDef.of(configBuilderFieldInjectPoint.type);
            if (StringUtils.isNotEmpty(factoryMethod)) {
                return builderType.invokeStatic(factoryMethod, builderType).newLocal("builder" + NameUtils.capitalize(configBuilderFieldInjectPoint.field), builderVar -> {
                    List<StatementDef> statements = getBuilderMethodStatements(injectMethodSignature, configBuilderFieldInjectPoint.builderPoints, builderVar);

                    statements.add(injectMethodSignature.instanceVar
                        .field(configBuilderFieldInjectPoint.field, builderType)
                        .put(builderVar));

                    return StatementDef.multi(statements);
                });
            } else {
                return injectMethodSignature.instanceVar
                    .field(configBuilderFieldInjectPoint.field, builderType)
                    .newLocal("builder" + NameUtils.capitalize(configBuilderFieldInjectPoint.field), builderVar -> StatementDef.multi(
                        getBuilderMethodStatements(injectMethodSignature, configBuilderFieldInjectPoint.builderPoints, builderVar)
                    ));
            }
        }
        throw new IllegalStateException();
    }

    private List<StatementDef> getBuilderMethodStatements(InjectMethodSignature injectMethodSignature, List<ConfigBuilderPointInjectCommand> points, VariableDef builderVar) {
        List<StatementDef> statements = new ArrayList<>();
        for (ConfigBuilderPointInjectCommand builderPoint : points) {
            statements.add(
                getConfigBuilderPointStatement(injectMethodSignature, builderVar, builderPoint)
            );
        }
        return statements;
    }

    private StatementDef getConfigBuilderPointStatement(InjectMethodSignature injectMethodSignature,
                                                        VariableDef builderVar,
                                                        ConfigBuilderPointInjectCommand builderPoint) {
        if (builderPoint instanceof ConfigBuilderMethodInjectCommand configBuilderMethodInjectPoint) {
            return visitConfigBuilderMethodInternal(
                injectMethodSignature,
                configBuilderMethodInjectPoint.propertyName,
                configBuilderMethodInjectPoint.returnType,
                configBuilderMethodInjectPoint.methodName,
                configBuilderMethodInjectPoint.paramType,
                configBuilderMethodInjectPoint.generics,
                false,
                configBuilderMethodInjectPoint.path,
                builderVar
            );
        }
        if (builderPoint instanceof ConfigBuilderMethodDurationInjectCommand configBuilderMethodDurationInjectPoint) {
            return visitConfigBuilderMethodInternal(
                injectMethodSignature,
                configBuilderMethodDurationInjectPoint.propertyName,
                configBuilderMethodDurationInjectPoint.returnType,
                configBuilderMethodDurationInjectPoint.methodName,
                ClassElement.of(Duration.class),
                Map.of(),
                true,
                configBuilderMethodDurationInjectPoint.path,
                builderVar
            );
        }
        throw new IllegalStateException();
    }

    private StatementDef setFieldValue(InjectMethodSignature injectMethodSignature,
                                       TypedElement declaringType,
                                       FieldElement fieldElement,
                                       boolean requiresReflection,
                                       boolean isOptional) {
        AnnotationMetadata annotationMetadata = fieldElement.getAnnotationMetadata();
        StatementDef setFieldValueStatement = setFieldValue(injectMethodSignature, fieldElement, isOptional, declaringType, requiresReflection, annotationMetadata);

        if (isOptional) {
            return getPropertyContainsCheck(
                injectMethodSignature,
                fieldElement.getType(),
                fieldElement.getName(),
                annotationMetadata
            ).ifTrue(setFieldValueStatement);
        }
        return setFieldValueStatement;
    }

    private StatementDef setFieldValue(InjectMethodSignature injectMethodSignature,
                                       FieldElement fieldElement,
                                       boolean isOptional,
                                       TypedElement declaringType,
                                       boolean requiresReflection,
                                       AnnotationMetadata annotationMetadata) {
        if (isInnerType(fieldElement.getGenericType())) {
            return injectField(injectMethodSignature, declaringType, fieldElement, annotationMetadata, requiresReflection);
        }
        if (!isConfigurationProperties || requiresReflection) {
            boolean isRequired = fieldElement
                .booleanValue(AnnotationUtil.INJECT, AnnotationUtil.MEMBER_REQUIRED)
                .orElse(true);
            return visitFieldInjectionPointInternal(
                injectMethodSignature,
                declaringType,
                fieldElement,
                annotationMetadata,
                requiresReflection,
                GET_VALUE_FOR_FIELD,
                isOptional,
                false,
                isRequired
            );
        }
        fieldInjectionPoints.add(new FieldVisitData(declaringType, fieldElement, annotationMetadata, false));
        int fieldIndex = fieldInjectionPoints.size() - 1;
        ExpressionDef value;
        Optional<String> property = annotationMetadata.stringValue(Property.class, "name");
        if (property.isPresent()) {
            value = getInvokeGetPropertyValueForField(injectMethodSignature, fieldElement, annotationMetadata, property.get(), fieldIndex);
        } else {
            Optional<String> valueValue = annotationMetadata.stringValue(Value.class);
            if (valueValue.isPresent()) {
                value = getInvokeGetPropertyPlaceholderValueForField(injectMethodSignature, fieldElement, annotationMetadata, valueValue.get(), fieldIndex);
            } else {
                // ???
                value = ExpressionDef.nullValue();
            }
        }
        return injectMethodSignature.instanceVar.field(fieldElement).put(value);
    }

    private StatementDef onBeanInstance(VariableDef.This aThis,
                                        List<VariableDef.MethodParameter> methodParameters,
                                        BuildMethodDefinition buildMethodDefinition,
                                        ExpressionDef beanInstance) {
        boolean needsInjectMethod = !injectCommands.isEmpty() || superBeanDefinition;
        boolean needsInjectScope = hasInjectScope(buildMethodDefinition.getParameters());
        boolean needsPostConstruct = buildMethodDefinition.postConstruct != null;
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

    private MethodDef buildDisposeMethod(BuildMethodLifecycleDefinition def, MethodDef.MethodDefBuilder override) {
        return buildLifeCycleMethod(override, PRE_DESTROY_METHOD, def);
    }

    private MethodDef buildInitializeMethod(BuildMethodLifecycleDefinition def, MethodDef.MethodDefBuilder override) {
        return buildLifeCycleMethod(override, POST_CONSTRUCT_METHOD, def);
    }

    private MethodDef buildLifeCycleMethod(MethodDef.MethodDefBuilder methodDefBuilder,
                                           Method superMethod,
                                           BuildMethodLifecycleDefinition lifeCycleDefinition) {
        return methodDefBuilder.build((aThis, methodParameters) -> {
            return aThis.invoke(superMethod, methodParameters).cast(beanTypeDef).newLocal("beanInstance", beanInstance -> {
                List<StatementDef> statements = new ArrayList<>();
                boolean hasInjectScope = false;
                for (InjectMethodBuildCommand injectionPoint : lifeCycleDefinition.injectionPoints) {
                    statements.add(injectMethod(injectionPoint.methodElement, injectionPoint.requiresReflection, aThis, methodParameters, beanInstance, injectionPoint.methodIndex));
                    if (!hasInjectScope) {
                        for (ParameterElement parameter : injectionPoint.methodElement.getSuspendParameters()) {
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

    private StatementDef buildInstance(VariableDef.This aThis,
                                       List<VariableDef.MethodParameter> methodParameters,
                                       BuildMethodDefinition buildMethodDefinition,
                                       Function<ExpressionDef, StatementDef> onBeanInstance,
                                       boolean isParametrized) {
        StatementDef.DefineAndAssign[] constructorDef= new StatementDef.DefineAndAssign[] { null };
        Supplier<VariableDef> constructorDefSupplier = new Supplier<VariableDef>() {

            @Override
            public VariableDef get() {
                if (constructorDef[0] == null) {
                    Class<?> constructorType;
                    if (constructor instanceof MethodElement) {
                        constructorType = AbstractInitializableBeanDefinition.MethodReference.class;
                    } else {
                        constructorType = AbstractInitializableBeanDefinition.FieldReference.class;
                    }
                    constructorDef[0] = aThis.type()
                        .getStaticField(FIELD_CONSTRUCTOR, ClassTypeDef.of(AbstractInitializableBeanDefinition.MethodOrFieldReference.class))
                        .cast(constructorType)
                        .newLocal("constructorDef");
                }
                return constructorDef[0].variable();
            }
        };
        if (buildMethodDefinition instanceof FactoryBuildMethodDefinition factoryBuildMethodDefinition) {
            if (factoryBuildMethodDefinition.parameters.length > 0) {
                List<? extends ExpressionDef> values = getConstructorArgumentValues(aThis, methodParameters,
                    List.of(buildMethodDefinition.getParameters()), isParametrized, constructorDefSupplier);
                StatementDef statement = buildFactoryGet(aThis, methodParameters, onBeanInstance, factoryBuildMethodDefinition, values);
                if (constructorDef[0] != null) {
                    return StatementDef.multi(
                        constructorDef[0],
                        statement
                    );
                }
                return statement;
            }
            return buildFactoryGet(aThis, methodParameters, onBeanInstance, factoryBuildMethodDefinition, List.of());
        }
        if (buildMethodDefinition instanceof ConstructorBuildMethodDefinition constructorBuildMethodDefinition) {
            if (constructorBuildMethodDefinition.constructor.hasParameters()) {
                List<? extends ExpressionDef> values = getConstructorArgumentValues(aThis, methodParameters,
                    List.of(buildMethodDefinition.getParameters()), isParametrized, constructorDefSupplier);
                StatementDef statement = buildConstructorInstantiate(aThis, methodParameters, onBeanInstance, constructorBuildMethodDefinition, values);
                if (constructorDef[0] != null) {
                    return StatementDef.multi(
                        constructorDef[0],
                        statement
                    );
                }
                return statement;
            }
            return buildConstructorInstantiate(aThis, methodParameters, onBeanInstance, constructorBuildMethodDefinition, List.of());
        }
        throw new IllegalStateException("Unknown build method definition: " + buildMethodDefinition);
    }

    private StatementDef buildConstructorInstantiate(VariableDef.This aThis,
                                                     List<VariableDef.MethodParameter> methodParameters,
                                                     Function<ExpressionDef, StatementDef> onBeanInstance,
                                                     ConstructorBuildMethodDefinition constructorBuildMethodDefinition,
                                                     List<? extends ExpressionDef> values) {
        List<ParameterElement> parameters = List.of(constructorBuildMethodDefinition.constructor.getSuspendParameters());
        if (isConstructorIntercepted(constructorBuildMethodDefinition.constructor)) {
            ClassTypeDef factoryInterceptor = createConstructorInterceptor(constructorBuildMethodDefinition);
            return onBeanInstance.apply(
                invokeConstructorChain(
                    aThis,
                    methodParameters,
                    factoryInterceptor.instantiate(CONSTRUCTOR_ABSTRACT_CONSTRUCTOR_IP, aThis),
                    TypeDef.OBJECT.array().instantiate(values),
                    parameters)
            );
        }
        return onBeanInstance.apply(
            initializeBean(aThis, methodParameters, constructorBuildMethodDefinition, values)
        );
    }

    private StatementDef buildFactoryGet(VariableDef.This aThis,
                                         List<VariableDef.MethodParameter> methodParameters,
                                         Function<ExpressionDef, StatementDef> onBeanInstance,
                                         FactoryBuildMethodDefinition factoryBuildMethodDefinition, List<? extends ExpressionDef> values) {
        return withGetFactoryBean(methodParameters, factoryBuildMethodDefinition, factoryVar -> {
            List<ParameterElement> parameters = List.of(factoryBuildMethodDefinition.parameters);
            if (isConstructorIntercepted(factoryBuildMethodDefinition.factoryElement)) {
                ClassTypeDef factoryInterceptor = createFactoryInterceptor(factoryBuildMethodDefinition);
                return onBeanInstance.apply(
                    invokeConstructorChain(
                        aThis,
                        methodParameters,
                        factoryInterceptor.instantiate(
                            List.of(
                                ClassTypeDef.of(BeanDefinition.class),
                                factoryVar.type()
                            ), aThis, factoryVar),
                        TypeDef.OBJECT.array().instantiate(values),
                        parameters)
                );
            }
            return onBeanInstance.apply(
                getBeanFromFactory(factoryBuildMethodDefinition, factoryVar, values)
            );
        });
    }

    private ExpressionDef getBeanFromFactory(FactoryBuildMethodDefinition factoryBuildMethodDefinition,
                                             ExpressionDef factoryVar,
                                             List<? extends ExpressionDef> values) {
        ClassTypeDef factoryType = ClassTypeDef.of(factoryBuildMethodDefinition.factoryClass);
        Element factoryElement = factoryBuildMethodDefinition.factoryElement;
        if (factoryElement instanceof MethodElement methodElement) {
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
        FieldElement fieldElement = (FieldElement) factoryElement;
        if (fieldElement.isReflectionRequired()) {
            return TYPE_REFLECTION_UTILS.invokeStatic(
                GET_FIELD_WITH_REFLECTION_METHOD,

                ExpressionDef.constant(factoryType),
                ExpressionDef.constant(fieldElement.getName()),
                fieldElement.isStatic() ? ExpressionDef.nullValue() : factoryVar
            );
        }
        if (fieldElement.isStatic()) {
            return factoryType.getStaticField(factoryElement.getName(), beanTypeDef);
        }
        return factoryVar.field(factoryElement.getName(), beanTypeDef);
    }

    private ExpressionDef initializeBean(VariableDef.This aThis,
                                         List<VariableDef.MethodParameter> methodParameters,
                                         ConstructorBuildMethodDefinition constructorBuildMethodDefinition,
                                         List<? extends ExpressionDef> values) {
        MethodElement constructor = constructorBuildMethodDefinition.constructor;
        List<ExpressionDef> hasValuesExpressions;
        if (values == null) {
            hasValuesExpressions = null;
        } else {
            hasValuesExpressions = new ArrayList<>();
            ParameterElement[] parameters = constructorBuildMethodDefinition.getParameters();
            for (int i = 0; i < values.size(); i++) {
                ExpressionDef value = values.get(i);
                ParameterElement parameter = parameters[i];
                if (parameter.hasAnnotation(Property.class)) {
                    hasValuesExpressions.add(
                        getContainsPropertyCheck(aThis, methodParameters, parameter)
                    );
                } else {
                    hasValuesExpressions.add(value.isNonNull());
                }
            }

        }
        return MethodGenUtils.invokeBeanConstructor(constructor, constructorBuildMethodDefinition.requiresReflection, true, values, hasValuesExpressions);
    }

    private ExpressionDef getContainsPropertyCheck(VariableDef.This aThis,
                                                   List<VariableDef.MethodParameter> methodParameters,
                                                   ParameterElement parameterElement) {
        String propertyName = parameterElement.stringValue(Property.class, "name").orElseThrow();

        return aThis.invoke(
            isMultiValueProperty(parameterElement.getType()) ? CONTAINS_PROPERTIES_VALUE_METHOD : CONTAINS_PROPERTY_VALUE_METHOD,

            methodParameters.get(0),
            methodParameters.get(1),
            ExpressionDef.constant(propertyName)
        );
    }

    private StatementDef withGetFactoryBean(List<VariableDef.MethodParameter> parameters,
                                            FactoryBuildMethodDefinition factoryBuildMethodDefinition,
                                            Function<ExpressionDef, StatementDef> fn) {
        if (factoryBuildMethodDefinition.factoryElement.isStatic()) {
            return fn.apply(ExpressionDef.nullValue());
        }

        // for Factory beans first we need to look up the factory bean
        // before invoking the method to instantiate
        // the below code looks up the factory bean.

        TypeDef factoryTypeDef = TypeDef.erasure(factoryBuildMethodDefinition.factoryClass);

        ExpressionDef argumentExpression = ClassTypeDef.of(Argument.class).invokeStatic(ArgumentExpUtils.METHOD_CREATE_ARGUMENT_SIMPLE,
            ExpressionDef.constant(factoryTypeDef),
            ExpressionDef.constant("factory")
        );

        return StatementDef.multi(
            parameters.get(1).cast(DefaultBeanContext.class)
                .invoke(METHOD_GET_BEAN,
                    // load the first argument of the method (the BeanResolutionContext) to be passed to the method
                    parameters.get(0),
                    // second argument is the bean type
                    ExpressionDef.constant(factoryTypeDef),
                    // third argument is the qualifier for the factory if any
                    getQualifier(factoryBuildMethodDefinition.factoryClass, argumentExpression)
                ).cast(factoryTypeDef).newLocal("factoryBean", factoryBeanVar -> StatementDef.multi(
                    parameters.get(0).invoke(METHOD_BEAN_RESOLUTION_CONTEXT_MARK_FACTORY),
                    fn.apply(factoryBeanVar)
                ))
        );
    }

    private ClassTypeDef createConstructorInterceptor(ConstructorBuildMethodDefinition constructorBuildMethodDefinition) {
        String interceptedConstructorWriterName = "ConstructorInterceptor";
        ClassDef.ClassDefBuilder innerClassBuilder = ClassDef.builder(interceptedConstructorWriterName)
            .addModifiers(Modifier.FINAL)
            .superclass(ClassTypeDef.of(AbstractBeanDefinitionBeanConstructor.class))
            .addAnnotation(Generated.class);

        innerClassBuilder.addMethod(
            MethodDef.constructor()
                .addModifiers(Modifier.PUBLIC)
                .addParameters(CONSTRUCTOR_ABSTRACT_CONSTRUCTOR_IP.getParameterTypes())
                .build((aThis, methodParameters)
                    -> aThis.superRef().invokeConstructor(CONSTRUCTOR_ABSTRACT_CONSTRUCTOR_IP, methodParameters.get(0)))
        );

        innerClassBuilder.addMethod(
            MethodDef.override(METHOD_BEAN_CONSTRUCTOR_INSTANTIATE)
                .build((aThis, methodParameters) -> {
                    ParameterElement[] parameters = constructorBuildMethodDefinition.constructor.getSuspendParameters();
                    List<ExpressionDef> values = IntStream.range(0, parameters.length)
                        .<ExpressionDef>mapToObj(index -> methodParameters.get(0).arrayElement(index).cast(TypeDef.erasure(parameters[index].getType())))
                        .toList();
                    return MethodGenUtils.invokeBeanConstructor(constructorBuildMethodDefinition.constructor, true, values)
                        .returning();
                })
        );

        classDefBuilder.addInnerType(innerClassBuilder.build());

        return ClassTypeDef.of(beanDefinitionName + "$" + interceptedConstructorWriterName);
    }

    private ClassTypeDef createFactoryInterceptor(FactoryBuildMethodDefinition factoryBuildMethodDefinition) {
        String interceptedConstructorWriterName = "ConstructorInterceptor";

        ClassDef.ClassDefBuilder innerClassBuilder = ClassDef.builder(interceptedConstructorWriterName)
            .addModifiers(Modifier.FINAL)
            .superclass(ClassTypeDef.of(AbstractBeanDefinitionBeanConstructor.class))
            .addAnnotation(Generated.class);


        // for factory methods we have to store the factory instance in a field and modify the constructor pass the factory instance
        ClassTypeDef factoryType = ClassTypeDef.of(factoryBuildMethodDefinition.factoryClass);

        FieldDef factoryField = FieldDef.builder("$factory", factoryType)
            .addModifiers(Modifier.PRIVATE, Modifier.FINAL)
            .build();

        innerClassBuilder.addField(factoryField);

        innerClassBuilder.addMethod(
            MethodDef.constructor()
                .addModifiers(Modifier.PROTECTED)
                .addParameters(CONSTRUCTOR_ABSTRACT_CONSTRUCTOR_IP.getParameterTypes())
                .addParameters(factoryType)
                .build((aThis, methodParameters)
                    -> StatementDef.multi(
                    aThis.superRef().invokeConstructor(CONSTRUCTOR_ABSTRACT_CONSTRUCTOR_IP, methodParameters.get(0)),
                    aThis.field(factoryField).put(methodParameters.get(1))
                ))
        );

        // now we need to implement the invoke method to execute the actual instantiation

        innerClassBuilder.addMethod(
            MethodDef.override(METHOD_BEAN_CONSTRUCTOR_INSTANTIATE)
                .build((aThis, methodParameters) -> {
                    List<ExpressionDef> values = IntStream.range(0, factoryBuildMethodDefinition.parameters.length)
                        .<ExpressionDef>mapToObj(index -> methodParameters.get(0)
                            .arrayElement(index)
                            .cast(TypeDef.erasure(factoryBuildMethodDefinition.parameters[index].getType())))
                        .toList();
                    return getBeanFromFactory(factoryBuildMethodDefinition, aThis.field(factoryField), values).returning();
                })
        );

        classDefBuilder.addInnerType(innerClassBuilder.build());

        return ClassTypeDef.of(beanDefinitionName + "$" + interceptedConstructorWriterName);
    }

    private StaticBlock getStaticInitializer() {
        List<StatementDef> statements = new ArrayList<>();

        FieldDef annotationMetadataField = AnnotationMetadataGenUtils.createAnnotationMetadataField(beanDefinitionTypeDef, annotationMetadata, loadTypeMethods);

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

        boolean hasMethodInjection = !superBeanDefinition && !allMethodVisits.isEmpty();
        if (hasMethodInjection) {

            TypeDef.Array methodReferenceArray = ClassTypeDef.of(AbstractInitializableBeanDefinition.MethodReference.class).array();
            injectionMethodsField = FieldDef.builder(FIELD_INJECTION_METHODS, methodReferenceArray)
                .addModifiers(Modifier.PRIVATE, Modifier.FINAL, Modifier.STATIC)
                .build();

            classDefBuilder.addField(injectionMethodsField);
            initStatements.add(beanDefinitionTypeDef.getStaticField(injectionMethodsField)
                .put(methodReferenceArray.instantiate(allMethodVisits.stream()
                    .map(md -> getNewMethodReference(md.beanType, md.methodElement, md.annotationMetadata, md.postConstruct, md.preDestroy))
                    .toList())));
            failStatements.add(beanDefinitionTypeDef.getStaticField(injectionMethodsField).put(ExpressionDef.nullValue()));
        }
        boolean hasFieldInjection = !fieldInjectionPoints.isEmpty();
        if (hasFieldInjection) {

            TypeDef.Array fieldReferenceArray = ClassTypeDef.of(AbstractInitializableBeanDefinition.FieldReference.class).array();
            injectionFieldsField = FieldDef.builder(FIELD_INJECTION_FIELDS, fieldReferenceArray)
                .addModifiers(Modifier.PRIVATE, Modifier.FINAL, Modifier.STATIC)
                .build();
            classDefBuilder.addField(injectionFieldsField);

            initStatements.add(beanDefinitionTypeDef.getStaticField(injectionFieldsField)
                .put(fieldReferenceArray.instantiate(fieldInjectionPoints.stream()
                    .map(fd -> getNewFieldReference(fd.beanType, fd.fieldElement, fd.annotationMetadata))
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
                .put(ExpressionsUtils.stringMapOf(
                    typeArguments, true, null, el -> ArgumentExpUtils.pushTypeArgumentElements(
                        annotationMetadata,
                        beanDefinitionTypeDef,
                        ClassElement.of(beanDefinitionName),
                        el,
                        loadTypeMethods
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
                        ExpressionDef.constant(preprocessMethods),
                        // 9: hasEvaluatedExpressions
                        ExpressionDef.constant(evaluatedExpressionProcessor.hasEvaluatedExpressions())

                    )
                )
        );

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

        statements.add(addInnerConfigurationMethod());
        statements.add(addGetExposedTypes());

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
        AnnotationMetadataGenUtils.writeAnnotationDefault(statements, beanDefinitionTypeDef, annotationMetadata, loadTypeMethods);

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
        if (constructor instanceof MethodElement methodElement) {
            ParameterElement[] parameters = methodElement.getParameters();
            List<ParameterElement> parameterList = Arrays.asList(parameters);
            applyDefaultNamedToParameters(parameterList);

            return getNewMethodReference(methodElement.getDeclaringType(), methodElement, methodElement.getAnnotationMetadata(), false, false);
        } else if (constructor instanceof FieldElement fieldConstructor) {
            return getNewFieldReference(fieldConstructor.getDeclaringType(), fieldConstructor, fieldConstructor.getAnnotationMetadata());
        } else {
            throw new IllegalArgumentException("Unexpected constructor: " + constructor);
        }
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
                return AnnotationMetadataGenUtils.invokeLoadClassValueMethod(beanDefinitionTypeDef, loadTypeMethods, annotationClassValue);
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
        if (annotationMetadata.hasDeclaredAnnotation(Bean.class.getName())) {
            final String[] exposedTypes = annotationMetadata.stringValues(Bean.class.getName(), "typed");
            if (exposedTypes.length > 0) {
                FieldDef exposedTypesField = FieldDef.builder(FIELD_EXPOSED_TYPES, Set.class)
                    .addModifiers(Modifier.PRIVATE, Modifier.FINAL, Modifier.STATIC)
                    .build();

                classDefBuilder.addField(exposedTypesField);

                classDefBuilder.addMethod(
                    MethodDef.override(GET_EXPOSED_TYPES_METHOD)
                        .build((aThis, methodParameters) -> aThis.type().getStaticField(exposedTypesField).returning())
                );

                return beanDefinitionTypeDef.getStaticField(exposedTypesField).put(getClassesAsSetExpression(exposedTypes));
            }
        }
        return StatementDef.multi();
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

    private boolean hasTypeArguments() {
        return typeArguments != null && !typeArguments.isEmpty() && typeArguments.entrySet().stream().anyMatch(e -> !e.getValue().isEmpty());
    }

    private boolean isSingleton(String scope) {
        if (beanProducingElement instanceof FieldElement && beanProducingElement.isFinal()) {
            // final fields can't change so effectively singleton
            return true;
        }

        if (scope != null) {
            return scope.equals(Singleton.class.getName());
        } else {
            final AnnotationMetadata annotationMetadata;
            if (beanProducingElement instanceof ClassElement) {
                annotationMetadata = getAnnotationMetadata();
            } else {
                annotationMetadata = beanProducingElement.getDeclaredMetadata();
            }

            return annotationMetadata.stringValue(DefaultScope.class)
                .map(t -> t.equals(Singleton.class.getName()))
                .orElse(false);
        }
    }

    /**
     * @return The bytes of the class
     */
    public byte[] toByteArray() {
        if (!beanFinalized) {
            throw new IllegalStateException("Bean definition not finalized. Call visitBeanDefinitionEnd() first.");
        }
        return new ByteCodeWriter().write(classDefBuilder.build());
    }

    @Override
    public void accept(ClassWriterOutputVisitor visitor) throws IOException {
        if (disabled) {
            return;
        }
        visitor.visitServiceDescriptor(
            BeanDefinitionReference.class,
            beanDefinitionName,
            getOriginatingElement()
        );
        write(visitor, classDefBuilder.build());
        try {
            if (executableMethodsDefinitionWriter != null) {
                executableMethodsDefinitionWriter.accept(visitor);
            }
        } catch (RuntimeException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IOException exception) {
                throw exception;
            } else {
                throw e;
            }
        }
        evaluatedExpressionProcessor.writeEvaluatedExpressions(visitor);
    }

    private void write(ClassWriterOutputVisitor visitor, ObjectDef objectDef) throws IOException {
        try (OutputStream out = visitor.visitClass(objectDef.getName(), getOriginatingElements())) {
            out.write(new ByteCodeWriter().write(objectDef));
        }
        for (ObjectDef innerType : objectDef.getInnerTypes()) {
            write(visitor, innerType);
        }
    }

    @Override
    public void visitSetterValue(
        TypedElement declaringType,
        MethodElement methodElement,
        AnnotationMetadata annotationMetadata,
        boolean requiresReflection,
        boolean isOptional) {

        injectCommands.add(new SetterInjectionInjectCommand(declaringType, methodElement, annotationMetadata, requiresReflection, isOptional));
    }

    private StatementDef setSetterValue(InjectMethodSignature injectMethodSignature,
                                        TypedElement declaringType,
                                        MethodElement methodElement,
                                        AnnotationMetadata annotationMetadata,
                                        boolean requiresReflection,
                                        boolean isOptional) {

        if (!requiresReflection) {

            ParameterElement parameter = methodElement.getParameters()[0];

            StatementDef setValueStatement = setSetterValue(injectMethodSignature, declaringType, methodElement, annotationMetadata, parameter);
            if (isOptional) {
                return getPropertyContainsCheck(
                    injectMethodSignature,
                    parameter.getType(),
                    parameter.getName(),
                    annotationMetadata
                ).ifTrue(setValueStatement);
            }
            return setValueStatement;
        }
        final MethodVisitData methodVisitData = new MethodVisitData(
            declaringType,
            methodElement,
            false,
            annotationMetadata);
        methodInjectionPoints.add(methodVisitData);
        allMethodVisits.add(methodVisitData);
        return StatementDef.multi();
    }

    private StatementDef setSetterValue(InjectMethodSignature injectMethodSignature,
                                        TypedElement declaringType,
                                        MethodElement methodElement,
                                        AnnotationMetadata annotationMetadata,
                                        ParameterElement parameter) {
        ClassElement genericType = parameter.getGenericType();
        if (isConfigurationProperties && isValueType(annotationMetadata)) {

            int methodIndex = -1;
            if (keepConfPropInjectPoints) {
                final MethodVisitData methodVisitData = new MethodVisitData(
                    declaringType,
                    methodElement,
                    false,
                    annotationMetadata);
                methodInjectionPoints.add(methodVisitData);
                allMethodVisits.add(methodVisitData);
                methodIndex = allMethodVisits.size() - 1;
            }

            Function<ExpressionDef, StatementDef> onValue = value -> injectMethodSignature
                .instanceVar.invoke(methodElement, value);

            Optional<String> valueValue = annotationMetadata.stringValue(Value.class);
            if (isInnerType(genericType)) {
                boolean isArray = genericType.isArray();
                boolean isCollection = genericType.isAssignable(Collection.class);
                if (isCollection || isArray) {
                    ClassElement typeArgument = genericType.isArray() ? genericType.fromArray() : genericType.getFirstTypeArgument().orElse(null);
                    if (typeArgument != null && !typeArgument.isPrimitive()) {
                        return getInvokeGetBeansOfTypeForSetter(injectMethodSignature, methodElement.getName(), parameter, annotationMetadata, onValue, methodIndex);
                    }
                    return onValue.apply(
                        getInvokeGetBeanForSetter(injectMethodSignature, methodElement.getName(), parameter, annotationMetadata, methodIndex)
                    );
                }
                return onValue.apply(
                    getInvokeGetBeanForSetter(injectMethodSignature, methodElement.getName(), parameter, annotationMetadata, methodIndex)
                );
            }
            Optional<String> property = annotationMetadata.stringValue(Property.class, "name");
            if (property.isPresent()) {
                return onValue.apply(
                    getInvokeGetPropertyValueForSetter(injectMethodSignature, methodElement.getName(), parameter, property.get(), annotationMetadata, methodIndex)
                );
            }
            if (valueValue.isPresent()) {
                return onValue.apply(
                    getInvokeGetPropertyPlaceholderValueForSetter(injectMethodSignature, methodElement.getName(), parameter, valueValue.get(), annotationMetadata, methodIndex)
                );
            }
            throw new IllegalStateException();
        } else {
            final MethodVisitData methodVisitData = new MethodVisitData(
                declaringType,
                methodElement,
                false,
                annotationMetadata);
            methodInjectionPoints.add(methodVisitData);
            allMethodVisits.add(methodVisitData);
            return injectMethod(
                methodElement,
                false,
                injectMethodSignature.aThis,
                injectMethodSignature.methodParameters,
                injectMethodSignature.instanceVar,
                allMethodVisits.size() - 1
            );
        }
    }

    @Override
    public void visitPostConstructMethod(TypedElement declaringType,
                                         MethodElement methodElement,
                                         boolean requiresReflection,
                                         VisitorContext visitorContext) {
        buildMethodDefinition.postConstruct(false);
        // for "super bean definitions" we just delegate to super
        if (!superBeanDefinition || isInterceptedLifeCycleByType(this.annotationMetadata, "POST_CONSTRUCT")) {
            MethodVisitData methodVisitData = new MethodVisitData(declaringType, methodElement, requiresReflection, methodElement.getAnnotationMetadata(), true, false);
            postConstructMethodVisits.add(methodVisitData);
            allMethodVisits.add(methodVisitData);
            buildMethodDefinition.postConstruct.injectionPoints.add(new
                    InjectMethodBuildCommand(
                    declaringType,
                    methodElement,
                    requiresReflection,
                    allMethodVisits.size() - 1
                )
            );
        }
    }

    @Override
    public void visitPreDestroyMethod(TypedElement declaringType,
                                      MethodElement methodElement,
                                      boolean requiresReflection,
                                      VisitorContext visitorContext) {
        // for "super bean definitions" we just delegate to super
        if (!superBeanDefinition || isInterceptedLifeCycleByType(this.annotationMetadata, "PRE_DESTROY")) {
            buildMethodDefinition.preDestroy(false);

            MethodVisitData methodVisitData = new MethodVisitData(declaringType, methodElement, requiresReflection, methodElement.getAnnotationMetadata(), false, true);
            preDestroyMethodVisits.add(methodVisitData);
            allMethodVisits.add(methodVisitData);
            buildMethodDefinition.preDestroy.injectionPoints.add(new InjectMethodBuildCommand(
                declaringType,
                methodElement,
                requiresReflection,
                allMethodVisits.size() - 1
            ));
        }
    }

    @Override
    public void visitMethodInjectionPoint(TypedElement declaringType,
                                          MethodElement methodElement,
                                          boolean requiresReflection,
                                          VisitorContext visitorContext) {
        MethodVisitData methodVisitData = new MethodVisitData(declaringType, methodElement, requiresReflection, methodElement.getAnnotationMetadata());
        evaluatedExpressionProcessor.processEvaluatedExpressions(methodElement.getAnnotationMetadata(), this.beanTypeElement);
        methodInjectionPoints.add(methodVisitData);
        allMethodVisits.add(methodVisitData);
        injectCommands.add(new InjectMethodInjectCommand(
            declaringType,
            methodElement,
            requiresReflection,
            visitorContext,
            allMethodVisits.size() - 1)
        );
    }

    @Override
    public int visitExecutableMethod(TypedElement declaringBean,
                                     MethodElement methodElement, VisitorContext visitorContext) {
        return visitExecutableMethod(
            declaringBean,
            methodElement,
            null,
            null
        );
    }

    /**
     * Visit a method that is to be made executable allow invocation of said method without reflection.
     *
     * @param declaringType                    The declaring type of the method. Either a Class or a string representing the
     *                                         name of the type
     * @param methodElement                    The method element
     * @param interceptedProxyClassName        The intercepted proxy class name
     * @param interceptedProxyBridgeMethodName The intercepted proxy bridge method name
     * @return The index of a new method.
     */
    public int visitExecutableMethod(TypedElement declaringType,
                                     MethodElement methodElement,
                                     String interceptedProxyClassName,
                                     String interceptedProxyBridgeMethodName) {

        if (executableMethodsDefinitionWriter == null) {
            executableMethodsDefinitionWriter = new ExecutableMethodsDefinitionWriter(
                evaluatedExpressionProcessor,
                annotationMetadata,
                beanDefinitionName,
                getBeanDefinitionName(),
                originatingElements
            );
        }
        return executableMethodsDefinitionWriter.visitExecutableMethod(declaringType, methodElement, interceptedProxyClassName, interceptedProxyBridgeMethodName);
    }

    @Override
    public String toString() {
        return "BeanDefinitionWriter{" +
            "beanFullClassName='" + beanFullClassName + '\'' +
            '}';
    }

    @Override
    public String getPackageName() {
        return packageName;
    }

    @Override
    public String getBeanSimpleName() {
        return beanSimpleClassName;
    }

    @Override
    public AnnotationMetadata getAnnotationMetadata() {
        return annotationMetadata;
    }

    @Override
    public void visitConfigBuilderField(
        ClassElement type,
        String field,
        AnnotationMetadata annotationMetadata,
        ConfigurationMetadataBuilder metadataBuilder,
        boolean isInterface) {

        ConfigBuilderState state = new ConfigBuilderState(type, field, false, annotationMetadata, isInterface);
        configBuilderInjectCommand = new ConfigFieldBuilderInjectCommand(type, field, annotationMetadata, metadataBuilder, isInterface, state, new ArrayList<>());
        injectCommands.add(configBuilderInjectCommand);
    }

    @Override
    public void visitConfigBuilderMethod(
        ClassElement type,
        String methodName,
        AnnotationMetadata annotationMetadata,
        ConfigurationMetadataBuilder metadataBuilder,
        boolean isInterface) {

        ConfigBuilderState state = new ConfigBuilderState(type, methodName, true, annotationMetadata, isInterface);
        configBuilderInjectCommand = new ConfigMethodBuilderInjectPointCommand(type, methodName, annotationMetadata, metadataBuilder, isInterface, state, new ArrayList<>());
        injectCommands.add(configBuilderInjectCommand);
    }

    @Override
    public void visitConfigBuilderDurationMethod(
        String propertyName,
        ClassElement returnType,
        String methodName,
        String path) {
        configBuilderInjectCommand.builderPoints().add(new ConfigBuilderMethodDurationInjectCommand(propertyName, returnType, methodName, path));
    }

    @Override
    public void visitConfigBuilderMethod(
        String propertyName,
        ClassElement returnType,
        String methodName,
        ClassElement paramType,
        Map<String, ClassElement> generics,
        String path) {
        configBuilderInjectCommand.builderPoints().add(new ConfigBuilderMethodInjectCommand(propertyName, returnType, methodName, paramType, generics, path));
    }

    @Override
    public void visitConfigBuilderEnd() {
        configBuilderInjectCommand = null;
    }

    @Override
    public void setRequiresMethodProcessing(boolean shouldPreProcess) {
        this.preprocessMethods = shouldPreProcess;
    }

    @Override
    public void visitTypeArguments(Map<String, Map<String, ClassElement>> typeArguments) {
        this.typeArguments = typeArguments;
    }

    @Override
    public boolean requiresMethodProcessing() {
        return this.preprocessMethods;
    }

    @Override
    public void visitFieldInjectionPoint(
        TypedElement declaringType,
        FieldElement fieldElement,
        boolean requiresReflection,
        VisitorContext visitorContext) {
        injectCommands.add(new InjectFieldInjectCommand(declaringType, fieldElement, requiresReflection));
    }

    private StatementDef injectField(InjectMethodSignature injectMethodSignature,
                                     TypedElement declaringType,
                                     FieldElement fieldElement,
                                     AnnotationMetadata annotationMetadata,
                                     boolean requiresReflection) {

        boolean isRequired = fieldElement
            .booleanValue(AnnotationUtil.INJECT, AnnotationUtil.MEMBER_REQUIRED)
            .orElse(true);
        boolean requiresGenericType = false;
        Method methodToInvoke;
        final ClassElement genericType = fieldElement.getGenericType();
        boolean isArray = genericType.isArray();
        boolean isCollection = genericType.isAssignable(Collection.class);
        boolean isMap = isInjectableMap(genericType);
        if (isMap) {
            requiresGenericType = true;
            methodToInvoke = GET_MAP_OF_TYPE_FOR_FIELD;
        } else if (isCollection || isArray) {
            requiresGenericType = true;
            ClassElement typeArgument = genericType.isArray() ? genericType.fromArray() : genericType.getFirstTypeArgument().orElse(null);
            if (typeArgument != null && !typeArgument.isPrimitive()) {
                if (typeArgument.isAssignable(BeanRegistration.class)) {
                    methodToInvoke = GET_BEAN_REGISTRATIONS_FOR_FIELD;
                } else {
                    methodToInvoke = GET_BEANS_OF_TYPE_FOR_FIELD;
                }
            } else {
                requiresGenericType = false;
                methodToInvoke = GET_BEAN_FOR_FIELD;
            }
        } else if (genericType.isAssignable(Stream.class)) {
            requiresGenericType = true;
            methodToInvoke = GET_STREAM_OF_TYPE_FOR_FIELD;
        } else if (genericType.isAssignable(Optional.class)) {
            requiresGenericType = true;
            methodToInvoke = FIND_BEAN_FOR_FIELD;
        } else if (genericType.isAssignable(BeanRegistration.class)) {
            requiresGenericType = true;
            methodToInvoke = GET_BEAN_REGISTRATION_FOR_FIELD;
        } else {
            methodToInvoke = GET_BEAN_FOR_FIELD;
        }
        return visitFieldInjectionPointInternal(
            injectMethodSignature,
            declaringType,
            fieldElement,
            annotationMetadata,
            requiresReflection,
            methodToInvoke,
            isArray,
            requiresGenericType,
            isRequired
        );
    }

    private static boolean isInjectableMap(ClassElement genericType) {
        boolean typeMatches = Stream.of(Map.class, HashMap.class, LinkedHashMap.class, TreeMap.class)
            .anyMatch(t -> genericType.getName().equals(t.getName()));
        if (typeMatches) {

            Map<String, ClassElement> typeArgs = genericType.getTypeArguments();
            if (typeArgs.size() == 2) {
                ClassElement k = typeArgs.get("K");
                return k != null && k.isAssignable(CharSequence.class);
            }
        }
        return false;
    }

    private boolean isInnerType(ClassElement genericType) {
        String type;
        if (genericType.isContainerType()) {
            type = genericType.getFirstTypeArgument().map(Element::getName).orElse("");
        } else if (genericType.isArray()) {
            type = genericType.fromArray().getName();
        } else {
            type = genericType.getName();
        }
        return beanTypeInnerClasses.contains(type);
    }

    @Override
    public void visitAnnotationMemberPropertyInjectionPoint(TypedElement annotationMemberBeanType,
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

    @Override
    public void visitFieldValue(TypedElement declaringType,
                                FieldElement fieldElement,
                                boolean requiresReflection,
                                boolean isOptional) {
        injectCommands.add(new InjectFieldValueInjectCommand(declaringType, fieldElement, requiresReflection, isOptional));
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
                                                                       AnnotationMetadata annotationMetadata,
                                                                       String value,
                                                                       int fieldIndex) {
        annotationMetadata = MutableAnnotationMetadata.of(annotationMetadata);
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

    private StatementDef visitConfigBuilderMethodInternal(
        InjectMethodSignature injectMethodSignature,
        String propertyName,
        ClassElement returnType,
        String methodName,
        ClassElement paramType,
        Map<String, ClassElement> generics,
        boolean isDurationWithTimeUnit,
        String propertyPath,
        VariableDef builderVar) {

        boolean zeroArgs = paramType == null;

        // Optional optional = AbstractBeanDefinition.getValueForPath(...)
        return getGetValueForPathCall(injectMethodSignature, paramType, propertyName, propertyPath, zeroArgs, generics)
            .newLocal("optional" + NameUtils.capitalize(propertyPath.replace('.', '_')), optionalVar -> {
                return optionalVar.invoke(OPTIONAL_IS_PRESENT_METHOD)
                    .ifTrue(
                        optionalVar.invoke(OPTIONAL_GET_METHOD).newLocal("value", valueVar -> {
                            if (zeroArgs) {
                                return valueVar.cast(boolean.class).ifTrue(
                                    StatementDef.doTry(
                                        builderVar.invoke(methodName, TypeDef.erasure(returnType))
                                    ).doCatch(NoSuchMethodError.class, exceptionVar -> StatementDef.multi())
                                );
                            }
                            List<ExpressionDef> values = new ArrayList<>();
                            List<TypeDef> parameterTypes = new ArrayList<>();
                            if (isDurationWithTimeUnit) {
                                parameterTypes.add(TypeDef.Primitive.LONG);
                                ClassTypeDef timeInitType = ClassTypeDef.of(TimeUnit.class);
                                parameterTypes.add(timeInitType);
                                values.add(
                                    valueVar.cast(ClassTypeDef.of(Duration.class))
                                        .invoke(DURATION_TO_MILLIS_METHOD)
                                );
                                values.add(
                                    timeInitType.getStaticField("MILLISECONDS", timeInitType)
                                );
                            } else {
                                TypeDef paramTypeDef = TypeDef.erasure(paramType);
                                parameterTypes.add(paramTypeDef);
                                values.add(valueVar.cast(paramTypeDef));
                            }
                            return StatementDef.doTry(
                                builderVar.invoke(methodName, parameterTypes, TypeDef.erasure(returnType), values)
                            ).doCatch(NoSuchMethodError.class, exceptionVar -> StatementDef.multi());
                        })
                    );
            });
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
                    loadTypeMethods
                ),
                ExpressionDef.constant(propertyPath)
            );
    }

    @Internal
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

    private StatementDef visitFieldInjectionPointInternal(InjectMethodSignature injectMethodSignature,
                                                          TypedElement declaringType,
                                                          FieldElement fieldElement,
                                                          AnnotationMetadata annotationMetadata,
                                                          boolean requiresReflection,
                                                          Method methodToInvoke,
                                                          boolean isArray,
                                                          boolean requiresGenericType,
                                                          boolean isRequired) {
        evaluatedExpressionProcessor.processEvaluatedExpressions(annotationMetadata, null);

        autoApplyNamedIfPresent(fieldElement, annotationMetadata);

        fieldInjectionPoints.add(new FieldVisitData(declaringType, fieldElement, annotationMetadata, requiresReflection));

        int fieldIndex = fieldInjectionPoints.size() - 1;

        ExpressionDef valueExpression = getValueBypassingBeanContext(fieldElement.getGenericField(), injectMethodSignature.methodParameters);
        if (valueExpression == null) {
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
        }

        if (!isRequired) {
            return valueExpression.newLocal("value", valueVar ->
                valueVar.ifNonNull(
                    putField(fieldElement, requiresReflection, injectMethodSignature, valueVar, fieldIndex)
                ));
        }
        return putField(fieldElement, requiresReflection, injectMethodSignature, valueExpression, fieldIndex);
    }

    private StatementDef putField(FieldElement fieldElement,
                                  boolean requiresReflection,
                                  InjectMethodSignature injectMethodSignature,
                                  ExpressionDef valueExpression,
                                  int fieldIndex) {
        if (requiresReflection) {
            return injectMethodSignature.aThis
                .invoke(
                    SET_FIELD_WITH_REFLECTION_METHOD,

                    injectMethodSignature.beanResolutionContext,
                    injectMethodSignature.beanContext,
                    ExpressionDef.constant(fieldIndex),
                    injectMethodSignature.instanceVar,
                    valueExpression
                );
        }
        return injectMethodSignature.instanceVar.field(fieldElement).put(valueExpression);
    }

    private ExpressionDef getPropertyContainsCheck(InjectMethodSignature injectMethodSignature,
                                                   ClassElement propertyType,
                                                   String propertyName,
                                                   AnnotationMetadata annotationMetadata) {
        String propertyValue = annotationMetadata.stringValue(Property.class, "name").orElse(propertyName);

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

    private ExpressionDef getQualifier(Element element, ExpressionDef argumentExpression) {
        return getQualifier(element, () -> argumentExpression);
    }

    private ExpressionDef getQualifier(Element element, Supplier<ExpressionDef> argumentExpressionSupplier) {
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
        String[] byType = element.hasDeclaredAnnotation(io.micronaut.context.annotation.Type.NAME) ? element.stringValues(io.micronaut.context.annotation.Type.NAME) : null;
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

    private ExpressionDef getQualifierForAnnotation(Element element,
                                                    String annotationName,
                                                    ExpressionDef argumentExpression) {
        if (annotationName.equals(Primary.NAME)) {
            // primary is the same as no qualifier
            return ExpressionDef.nullValue();
        }
        if (annotationName.equals(AnnotationUtil.NAMED)) {
            final String n = element.stringValue(AnnotationUtil.NAMED).orElse(element.getName());
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
        return TypeDef.CLASS.array().instantiate(Arrays.stream(byType).map(this::asClassExpression).toList());
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

    private StatementDef injectMethod(MethodElement methodElement,
                                      boolean requiresReflection,
                                      VariableDef.This aThis,
                                      List<VariableDef.MethodParameter> parameters,
                                      VariableDef instanceVar,
                                      int methodIndex) {


        final List<ParameterElement> argumentTypes = Arrays.asList(methodElement.getParameters());
        applyDefaultNamedToParameters(argumentTypes);
        for (ParameterElement value : argumentTypes) {
            evaluatedExpressionProcessor.processEvaluatedExpressions(value.getAnnotationMetadata(), null);
        }

        return injectStatement(aThis, parameters, methodElement, requiresReflection, instanceVar, methodIndex);
    }

    private StatementDef injectStatement(VariableDef.This aThis,
                                         List<VariableDef.MethodParameter> parameters,
                                         MethodElement methodElement,
                                         boolean requiresReflection,
                                         VariableDef instanceVar,
                                         int methodIndex) {
        final List<ParameterElement> argumentTypes = Arrays.asList(methodElement.getParameters());
        boolean isRequiredInjection = InjectionPoint.isInjectionRequired(methodElement);
        List<ExpressionDef> invocationValues = IntStream.range(0, argumentTypes.size())
            .mapToObj(index -> getBeanForMethodParameter(aThis, parameters, index, argumentTypes.get(index), methodIndex))
            .toList();
        if (!isRequiredInjection && methodElement.hasParameters()) {
            // store parameter values in local object[]

            return TypeDef.OBJECT.array().instantiate(invocationValues).newLocal("values", valuesVar -> {
                // invoke isMethodResolved with method parameters
                List<? extends ExpressionDef> values = IntStream.range(0, argumentTypes.size())
                    .mapToObj(index -> valuesVar.arrayElement(index).cast(TypeDef.erasure(argumentTypes.get(index).getType())))
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
        if (!requiresReflection) {
            return instanceVar.invoke(methodElement, invocationValues);
        }
        return aThis.invoke(
            INVOKE_WITH_REFLECTION_METHOD,

            parameters.get(INJECT_METHOD_BEAN_RESOLUTION_CONTEXT_PARAM),
            parameters.get(INJECT_METHOD_BEAN_CONTEXT_PARAM),
            ExpressionDef.constant(methodIndex),
            instanceVar,
            TypeDef.OBJECT.array().instantiate(invocationValues)
        );
    }

    private StatementDef destroyInjectScopeBeansIfNecessary(List<VariableDef.MethodParameter> parameters) {
        return parameters.get(0).invoke(DESTROY_INJECT_SCOPED_BEANS_METHOD);
    }

    private ExpressionDef getBeanForMethodParameter(VariableDef.This aThis,
                                                    List<VariableDef.MethodParameter> methodParameters,
                                                    int i,
                                                    ParameterElement entry,
                                                    int methodIndex) {
        AnnotationMetadata argMetadata = entry.getAnnotationMetadata();
        ExpressionDef expressionDef = getValueBypassingBeanContext(entry.getGenericType(), methodParameters);
        if (expressionDef != null) {
            return expressionDef;
        }
        boolean requiresGenericType = false;
        final ClassElement genericType = entry.getGenericType();
        Method methodToInvoke;
        boolean isCollection = genericType.isAssignable(Collection.class);
        boolean isMap = isInjectableMap(genericType);
        boolean isArray = genericType.isArray();

        if (isValueType(argMetadata) && !isInnerType(entry.getGenericType())) {
            Optional<String> property = argMetadata.stringValue(Property.class, "name");
            if (property.isPresent()) {
                return getInvokeGetPropertyValueForMethod(aThis, methodParameters, i, entry, property.get(), methodIndex);
            } else {
                if (entry.getAnnotationMetadata().getValue(Value.class, EvaluatedExpressionReference.class).isPresent()) {
                    return getInvokeGetEvaluatedExpressionValueForMethodArgument(aThis, i, entry, methodIndex);
                } else {
                    Optional<String> valueValue = entry.getAnnotationMetadata().stringValue(Value.class);
                    if (valueValue.isPresent()) {
                        return getInvokeGetPropertyPlaceholderValueForMethod(aThis, methodParameters, i, entry, valueValue.get(), methodIndex);
                    }
                }
                return ExpressionDef.nullValue();
            }
        } else if (isCollection || isArray) {
            requiresGenericType = true;
            ClassElement typeArgument = genericType.isArray() ? genericType.fromArray() : genericType.getFirstTypeArgument().orElse(null);
            if (typeArgument != null && !typeArgument.isPrimitive()) {
                if (typeArgument.isAssignable(BeanRegistration.class)) {
                    methodToInvoke = GET_BEAN_REGISTRATIONS_FOR_METHOD_ARGUMENT;
                } else {
                    methodToInvoke = GET_BEANS_OF_TYPE_FOR_METHOD_ARGUMENT;
                }
            } else {
                methodToInvoke = GET_BEAN_FOR_METHOD_ARGUMENT;
                requiresGenericType = false;
            }
        } else if (isMap) {
            requiresGenericType = true;
            methodToInvoke = GET_MAP_OF_TYPE_FOR_METHOD_ARGUMENT;
        } else if (genericType.isAssignable(Stream.class)) {
            requiresGenericType = true;
            methodToInvoke = GET_STREAM_OF_TYPE_FOR_METHOD_ARGUMENT;
        } else if (genericType.isAssignable(Optional.class)) {
            requiresGenericType = true;
            methodToInvoke = FIND_BEAN_FOR_METHOD_ARGUMENT;
        } else if (genericType.isAssignable(BeanRegistration.class)) {
            requiresGenericType = true;
            methodToInvoke = GET_BEAN_REGISTRATION_FOR_METHOD_ARGUMENT;
        } else {
            methodToInvoke = GET_BEAN_FOR_METHOD_ARGUMENT;
        }

        List<ExpressionDef> values = new ArrayList<>(
            List.of(
                // 1st argument load BeanResolutionContext
                methodParameters.get(0),
                // 2nd argument load BeanContext
                methodParameters.get(1),
                // 3rd argument the method index
                ExpressionDef.constant(methodIndex),
                // 4th argument the argument index
                ExpressionDef.constant(i)
            )
        );

        // invoke getBeanForField
        if (requiresGenericType) {
            values.add(
                resolveMethodArgumentGenericType(genericType, methodIndex, i)
            );
        }
        ExpressionDef argumentExpression = resolveMethodArgument(methodIndex, i);
        values.add(
            getQualifier(entry, argumentExpression)
        );

        ExpressionDef result = aThis.invoke(methodToInvoke, values);

        if (isArray && requiresGenericType) {
            result = convertToArray(genericType.fromArray(), result);
        }
        // cast the return value to the correct type
        return result.cast(TypeDef.erasure(entry.getType()));
    }

    private ExpressionDef getInvokeGetPropertyValueForMethod(VariableDef.This aThis,
                                                             List<VariableDef.MethodParameter> methodParameters,
                                                             int i,
                                                             ParameterElement entry,
                                                             String value,
                                                             int methodIndex) {
        return aThis.invoke(
            GET_PROPERTY_VALUE_FOR_METHOD_ARGUMENT,
            // 1st argument load BeanResolutionContext
            methodParameters.get(0),
            // 2nd argument load BeanContext
            methodParameters.get(1),
            // 3rd argument the method index
            ExpressionDef.constant(methodIndex),
            // 4th argument the argument index
            ExpressionDef.constant(i),
            // 5th property value
            ExpressionDef.constant(value),
            // 6 cli property name
            ExpressionDef.constant(getCliPrefix(entry.getName()))
        ).cast(TypeDef.erasure(entry.getType()));
    }

    private ExpressionDef getInvokeGetEvaluatedExpressionValueForMethodArgument(VariableDef.This aThis,
                                                                                int i,
                                                                                ParameterElement entry,
                                                                                int methodIndex) {
        return aThis.invoke(
            GET_EVALUATED_EXPRESSION_VALUE_FOR_METHOD_ARGUMENT,

            // 1st argument the method index
            ExpressionDef.constant(methodIndex),
            // 2nd argument the argument index
            ExpressionDef.constant(i)
        ).cast(TypeDef.erasure(entry.getType()));
    }

    private ExpressionDef getInvokeGetPropertyPlaceholderValueForMethod(VariableDef.This aThis,
                                                                        List<VariableDef.MethodParameter> methodParameters,
                                                                        int i,
                                                                        ParameterElement entry,
                                                                        String value,
                                                                        int methodIndex) {
        return aThis.invoke(
            GET_PROPERTY_PLACEHOLDER_VALUE_FOR_METHOD_ARGUMENT,
            // 1st argument load BeanResolutionContext
            methodParameters.get(0),
            // 2nd argument load BeanContext
            methodParameters.get(1),
            // 3rd argument the method index
            ExpressionDef.constant(methodIndex),
            // 4th argument the argument index
            ExpressionDef.constant(i),
            // 5th property value
            ExpressionDef.constant(value)
        ).cast(TypeDef.erasure(entry.getType()));
    }

    private ExpressionDef getInvokeGetPropertyValueForSetter(InjectMethodSignature injectMethodSignature,
                                                             String setterName,
                                                             ParameterElement entry,
                                                             String value,
                                                             AnnotationMetadata annotationMetadata,
                                                             int methodIndex) {
        annotationMetadata = MutableAnnotationMetadata.of(annotationMetadata);
        removeAnnotations(annotationMetadata, PropertySource.class.getName(), Property.class.getName());

        return injectMethodSignature.aThis.invoke(
            GET_PROPERTY_VALUE_FOR_SETTER,

            // 1st argument load BeanResolutionContext
            injectMethodSignature.beanResolutionContext,
            // 2nd argument load BeanContext
            injectMethodSignature.beanContext,
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
        return keepConfPropInjectPoints ? resolveMethodArgument(methodIndex, 0) : ArgumentExpUtils.pushCreateArgument(
            this.annotationMetadata,
            ClassElement.of(beanFullClassName),
            beanDefinitionTypeDef,
            entry.getName(),
            entry.getGenericType(),
            annotationMetadata,
            entry.getGenericType().getTypeArguments(),
            loadTypeMethods
        );
    }

    private ExpressionDef getFieldArgument(FieldElement fieldElement, AnnotationMetadata annotationMetadata, int fieldIndex) {
        if (!keepConfPropInjectPoints) {
            return ArgumentExpUtils.pushCreateArgument(
                this.annotationMetadata,
                ClassElement.of(beanFullClassName),
                beanDefinitionTypeDef,
                fieldElement.getName(),
                fieldElement.getGenericType(),
                annotationMetadata,
                fieldElement.getGenericType().getTypeArguments(),
                loadTypeMethods
            );
        }
        return resolveFieldArgument(fieldIndex);
    }

    private ExpressionDef getInvokeGetBeanForSetter(InjectMethodSignature injectMethodSignature,
                                                    String setterName,
                                                    ParameterElement entry,
                                                    AnnotationMetadata annotationMetadata,
                                                    int methodIndex) {

        annotationMetadata = MutableAnnotationMetadata.of(annotationMetadata);
        removeAnnotations(annotationMetadata, PropertySource.class.getName(), Property.class.getName());

        return injectMethodSignature.aThis.invoke(
            GET_BEAN_FOR_SETTER,

            // 1st argument load BeanResolutionContext
            injectMethodSignature.beanResolutionContext,
            // 2nd argument load BeanContext
            injectMethodSignature.beanContext,
            // 3rd argument the method name
            ExpressionDef.constant(setterName),
            // 4th argument the argument
            getMethodArgument(entry, annotationMetadata, methodIndex),
            // push qualifier
            getQualifier(entry.getGenericType(), getMethodArgument(entry, annotationMetadata, methodIndex))
        ).cast(TypeDef.erasure(entry.getType()));
    }

    private StatementDef getInvokeGetBeansOfTypeForSetter(InjectMethodSignature injectMethodSignature,
                                                          String setterName,
                                                          ParameterElement entry,
                                                          AnnotationMetadata annotationMetadata,
                                                          Function<ExpressionDef, StatementDef> onValue,
                                                          int methodIndex) {

        annotationMetadata = MutableAnnotationMetadata.of(annotationMetadata);
        removeAnnotations(annotationMetadata, PropertySource.class.getName(), Property.class.getName());

        // 4th argument the argument
        ClassElement genericType = entry.getGenericType();

        return getMethodArgument(entry, annotationMetadata, methodIndex).newLocal("argument", argumentVar -> {
            ExpressionDef value = injectMethodSignature.aThis.invoke(
                GET_BEANS_OF_TYPE_FOR_SETTER,

                // 1st argument load BeanResolutionContext
                injectMethodSignature.beanResolutionContext,
                // 2nd argument load BeanContext
                injectMethodSignature.beanContext,
                // 3rd argument the method name
                ExpressionDef.constant(setterName),
                // 4th argument the argument
                argumentVar,
                // generic type
                resolveGenericType(argumentVar, genericType),
                // push qualifier
                getQualifier(entry.getGenericType(), argumentVar)
            ).cast(TypeDef.erasure(entry.getType()));
            return onValue.apply(value);
        });
    }

    private ExpressionDef resolveGenericType(VariableDef argumentVar, ClassElement genericType) {
        ExpressionDef argumentExpression = resolveArgumentGenericType(genericType);
        if (argumentExpression == null) {
            argumentExpression = resolveFirstTypeArgument(argumentVar);
            return resolveInnerTypeArgumentIfNeeded(argumentExpression, genericType);
        }
        return argumentExpression;
    }

    private ExpressionDef getInvokeGetPropertyPlaceholderValueForSetter(InjectMethodSignature injectMethodSignature,
                                                                        String setterName,
                                                                        ParameterElement entry,
                                                                        String value,
                                                                        AnnotationMetadata annotationMetadata,
                                                                        int methodIndex) {
        annotationMetadata = MutableAnnotationMetadata.of(annotationMetadata);
        removeAnnotations(annotationMetadata, PropertySource.class.getName(), Property.class.getName());

        return injectMethodSignature.aThis
            .invoke(
                GET_PROPERTY_PLACEHOLDER_VALUE_FOR_SETTER,

                // 1st argument load BeanResolutionContext
                injectMethodSignature.beanResolutionContext,
                // 2nd argument load BeanContext
                injectMethodSignature.beanContext,
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

    @SuppressWarnings("MagicNumber")
    private ClassTypeDef createExecutableMethodInterceptor(MethodDef interceptMethod, String name) {
        // if there is method interception in place we need to construct an inner executable method class that invokes the "initialize"
        // method and apply interception
        String interceptedConstructorWriterName = name;

        ClassDef.ClassDefBuilder innerClassBuilder = ClassDef.builder(interceptedConstructorWriterName)
            .addModifiers(Modifier.FINAL)
            .superclass(ClassTypeDef.of(AbstractExecutableMethod.class))
            .addAnnotation(Generated.class);

        FieldDef fieldBeanDef = FieldDef.builder("$beanDef", beanDefinitionTypeDef)
            .addModifiers(Modifier.PRIVATE, Modifier.FINAL)
            .build();

        FieldDef fieldResContext = FieldDef.builder("$resolutionContext", BeanResolutionContext.class)
            .addModifiers(Modifier.PRIVATE, Modifier.FINAL)
            .build();

        FieldDef fieldBeanContext = FieldDef.builder("$beanContext", BeanContext.class)
            .addModifiers(Modifier.PRIVATE, Modifier.FINAL)
            .build();

        FieldDef fieldBean = FieldDef.builder("$bean", beanTypeDef)
            .addModifiers(Modifier.PRIVATE, Modifier.FINAL)
            .build();

        innerClassBuilder.addField(fieldBeanDef);
        innerClassBuilder.addField(fieldResContext);
        innerClassBuilder.addField(fieldBeanContext);
        innerClassBuilder.addField(fieldBean);

        // constructor will be AbstractExecutableMethod(BeanDefinition, BeanResolutionContext, BeanContext, T beanType)

        innerClassBuilder.addMethod(
            MethodDef.constructor()
                .addParameters(beanDefinitionTypeDef)
                .addParameters(BeanResolutionContext.class, BeanContext.class)
                .addParameters(beanTypeDef)
                .build((aThis, methodParameters) -> StatementDef.multi(
                    aThis.superRef().invokeConstructor(
                        ABSTRACT_EXECUTABLE_METHOD_CONSTRUCTOR,

                        ExpressionDef.constant(beanTypeDef),
                        ExpressionDef.constant(interceptMethod.getName())
                    ),
                    aThis.field(fieldBeanDef).put(methodParameters.get(0)),
                    aThis.field(fieldResContext).put(methodParameters.get(1)),
                    aThis.field(fieldBeanContext).put(methodParameters.get(2)),
                    aThis.field(fieldBean).put(methodParameters.get(3))
                ))
        );

        innerClassBuilder.addMethod(
            MethodDef.override(PROVIDER_GET_ANNOTATION_METADATA_METHOD)
                .build((aThis, methodParameters) ->
                    beanDefinitionTypeDef.getStaticField(AnnotationMetadataGenUtils.FIELD_ANNOTATION_METADATA)
                        .returning())
        );

        innerClassBuilder.addMethod(
            MethodDef.override(METHOD_INVOKE_INTERNAL)
                .build((aThis, methodParameters) ->
                    aThis.field(fieldBeanDef).invoke(interceptMethod,

                        aThis.field(fieldResContext),
                        aThis.field(fieldBeanContext),
                        aThis.field(fieldBean)
                    ).returning())
        );

        classDefBuilder.addInnerType(innerClassBuilder.build());

        return ClassTypeDef.of(beanDefinitionName + "$" + interceptedConstructorWriterName);
    }

    private StatementDef interceptAndReturn(VariableDef.This aThis,
                                            List<VariableDef.MethodParameter> methodParameters,
                                            ClassTypeDef innerTypeDef,
                                            Method interceptorMethod) {
        ExpressionDef localInstance = methodParameters.get(2).cast(beanTypeDef);

        // now instantiate the inner class
        ExpressionDef executableMethodInstance = innerTypeDef.instantiate(
            // 1st argument: pass outer class instance to constructor
            aThis,
            // 2nd argument: resolution context
            methodParameters.get(0),
            // 3rd argument: bean context
            methodParameters.get(1),
            // 4th argument: bean instance
            localInstance
        );
        // now invoke MethodInterceptorChain.initialize or dispose
        return ClassTypeDef.of(MethodInterceptorChain.class)
            .invokeStatic(
                interceptorMethod,

                List.of(
                    // 1st argument: resolution context
                    methodParameters.get(0),
                    // 2nd argument: bean context
                    methodParameters.get(1),
                    // 3rd argument: this definition
                    aThis,
                    // 4th argument: executable method instance
                    executableMethodInstance,
                    // 5th argument: the bean instance
                    localInstance
                )
            ).cast(beanTypeDef).returning();
    }

    private void visitBuildFactoryMethodDefinition(ClassElement factoryClass, Element factoryElement, ParameterElement... parameters) {
        if (buildMethodDefinition == null) {
            buildMethodDefinition = new FactoryBuildMethodDefinition(factoryClass, factoryElement, parameters);
            onBuild(factoryElement, parameters);
        }
    }

    private void visitBuildConstructorDefinition(MethodElement constructor, boolean requiresReflection) {
        if (buildMethodDefinition == null) {
            buildMethodDefinition = new ConstructorBuildMethodDefinition(constructor, requiresReflection);
            onBuild(constructor, constructor.getParameters());
        }
    }

    private void onBuild(Element factoryElement, ParameterElement[] parameters) {
        evaluatedExpressionProcessor.processEvaluatedExpressions(factoryElement.getAnnotationMetadata(), null);
        for (ParameterElement parameterElement : parameters) {
            evaluatedExpressionProcessor.processEvaluatedExpressions(parameterElement.getAnnotationMetadata(), null);
        }
        if (isInterceptedLifeCycleByType(this.annotationMetadata, "POST_CONSTRUCT")) {
            buildMethodDefinition.postConstruct(true);
        }
        if (!superBeanDefinition && isInterceptedLifeCycleByType(this.annotationMetadata, "PRE_DESTROY")) {
            buildMethodDefinition.preDestroy(true);
        }
    }

    @Nullable
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

    private ExpressionDef invokeConstructorChain(VariableDef.This aThis,
                                                 List<VariableDef.MethodParameter> methodParameters,
                                                 ExpressionDef beanConstructor,
                                                 ExpressionDef constructorValue,
                                                 List<ParameterElement> parameters) {
        return ClassTypeDef.of(ConstructorInterceptorChain.class)
            .invokeStatic(
                METHOD_DESCRIPTOR_CONSTRUCTOR_INSTANTIATE,
                // 1st argument: The resolution context
                methodParameters.get(0),
                // 2nd argument: The bean context
                methodParameters.get(1),
                // 3rd argument: The interceptors if present
                StringUtils.isNotEmpty(interceptedType) ?
                    constructorValue.arrayElement(AopProxyWriter.findInterceptorsListParameterIndex(parameters)).cast(List.class)
                    : ExpressionDef.nullValue(),
                // 4th argument: the bean definition
                aThis,
                // 5th argument: The constructor
                beanConstructor,
                // 6th argument:  additional proxy parameters count
                interceptedType != null ? ExpressionDef.constant(AopProxyWriter.ADDITIONAL_PARAMETERS_COUNT) : ExpressionDef.constant(0),
                // 7th argument:  load the Object[] for the parameters
                constructorValue
            );
    }

    private boolean isConstructorIntercepted(Element constructor) {
        // a constructor is intercepted when this bean is an advised type but not proxied
        // and any AROUND_CONSTRUCT annotations are present
        AnnotationMetadataHierarchy annotationMetadata = new AnnotationMetadataHierarchy(this.annotationMetadata, constructor.getAnnotationMetadata());
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
            final Element originatingElement = getOriginatingElements()[0];
            final boolean isFactoryMethod = (originatingElement instanceof MethodElement && !(originatingElement instanceof ConstructorElement));
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
                                                                       List<ParameterElement> parameters,
                                                                       boolean isParametrized,
                                                                       Supplier<VariableDef> constructorMethodVarSupplier) {
        List<ExpressionDef> values = new ArrayList<>();
        for (int i = 0; i < parameters.size(); i++) {
            ParameterElement parameter = parameters.get(i);
            values.add(
                getConstructorArgument(aThis, methodParameters, parameter, i, isParametrized, constructorMethodVarSupplier)
            );
        }
        return values;
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

    private ExpressionDef getConstructorArgument(VariableDef.This aThis,
                                                 List<VariableDef.MethodParameter> methodParameters,
                                                 ParameterElement parameter,
                                                 int index,
                                                 boolean isParametrized,
                                                 Supplier<VariableDef> constructorMethodVarSupplier) {
        AnnotationMetadata annotationMetadata = parameter.getAnnotationMetadata();
        if (isAnnotatedWithParameter(annotationMetadata) && isParametrized) {
            // load the args
            return methodParameters.get(2)
                .invoke(
                    GET_MAP_METHOD,
                    ExpressionDef.constant(parameter.getName())
                );
        }
        ExpressionDef expression = getValueBypassingBeanContext(parameter.getGenericType(), methodParameters);
        if (expression != null) {
            return expression;
        }

        boolean hasGenericType = false;
        boolean isArray;
        Method methodToInvoke;
        final ClassElement genericType = parameter.getGenericType();
        if (isValueType(annotationMetadata) && !isInnerType(genericType)) {
            Optional<String> property = parameter.stringValue(Property.class, "name");
            if (property.isPresent()) {
                return getInvokeGetPropertyValueForConstructor(aThis, methodParameters, index, parameter, property.get());
            }
            if (parameter.getValue(Value.class, EvaluatedExpressionReference.class).isPresent()) {
                return getInvokeGetEvaluatedExpressionValueForConstructorArgument(aThis, index, parameter);
            }
            Optional<String> valueValue = parameter.stringValue(Value.class);
            if (valueValue.isPresent()) {
                return getInvokeGetPropertyPlaceholderValueForConstructor(aThis, methodParameters, index, parameter, valueValue.get());
            }
            return ExpressionDef.nullValue();
        }
        isArray = genericType.isArray();
        if (genericType.isAssignable(Collection.class) || isArray) {
            hasGenericType = true;
            ClassElement typeArgument = genericType.isArray() ? genericType.fromArray() : genericType.getFirstTypeArgument().orElse(null);
            if (typeArgument != null && !typeArgument.isPrimitive()) {
                if (typeArgument.isAssignable(BeanRegistration.class)) {
                    methodToInvoke = GET_BEAN_REGISTRATIONS_FOR_CONSTRUCTOR_ARGUMENT;
                } else {
                    methodToInvoke = GET_BEANS_OF_TYPE_FOR_CONSTRUCTOR_ARGUMENT;
                }
            } else {
                methodToInvoke = GET_BEAN_FOR_CONSTRUCTOR_ARGUMENT;
                hasGenericType = false;
            }
        } else if (isInjectableMap(genericType)) {
            hasGenericType = true;
            methodToInvoke = GET_MAP_OF_TYPE_FOR_CONSTRUCTOR_ARGUMENT;
        } else if (genericType.isAssignable(Stream.class)) {
            hasGenericType = true;
            methodToInvoke = GET_STREAM_OF_TYPE_FOR_CONSTRUCTOR_ARGUMENT;
        } else if (genericType.isAssignable(Optional.class)) {
            hasGenericType = true;
            methodToInvoke = FIND_BEAN_FOR_CONSTRUCTOR_ARGUMENT;
        } else if (genericType.isAssignable(BeanRegistration.class)) {
            hasGenericType = true;
            methodToInvoke = GET_BEAN_REGISTRATION_FOR_CONSTRUCTOR_ARGUMENT;
        } else {
            methodToInvoke = GET_BEAN_FOR_CONSTRUCTOR_ARGUMENT;
        }
        List<ExpressionDef> values = new ArrayList<>();
        // load the first two arguments of the method (the BeanResolutionContext and the BeanContext) to be passed to the method
        values.add(methodParameters.get(0));
        values.add(methodParameters.get(1));
        // pass the index of the method as the third argument
        values.add(ExpressionDef.constant(index));
        if (hasGenericType) {
            values.add(
                resolveConstructorArgumentGenericType(parameter.getGenericType(), index, constructorMethodVarSupplier)
            );
        }
        // push qualifier
        values.add(
            getQualifier(parameter, () -> resolveConstructorArgument(index, constructorMethodVarSupplier.get()))
        );
        ExpressionDef result = aThis.superRef().invoke(methodToInvoke, values);
        if (isArray && hasGenericType) {
            result = convertToArray(parameter.getGenericType().fromArray(), result);
        }
        return result.cast(TypeDef.erasure(parameter.getType()));
    }

    private ExpressionDef getInvokeGetPropertyValueForConstructor(VariableDef.This aThis,
                                                                  List<VariableDef.MethodParameter> methodParameters,
                                                                  int i, ParameterElement entry, String value) {

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
            ExpressionDef.constant(getCliPrefix(entry.getName()))

        ).cast(TypeDef.erasure(entry.getType()));
    }

    private ExpressionDef getInvokeGetPropertyPlaceholderValueForConstructor(VariableDef.This aThis,
                                                                             List<VariableDef.MethodParameter> methodParameters,
                                                                             int i, ParameterElement entry, String value) {

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
        ).cast(TypeDef.erasure(entry.getType()));
    }

    private ExpressionDef getInvokeGetEvaluatedExpressionValueForConstructorArgument(VariableDef.This aThis,
                                                                                     int i, ParameterElement entry) {
        return aThis.superRef()
            .invoke(GET_EVALUATED_EXPRESSION_VALUE_FOR_CONSTRUCTOR_ARGUMENT, ExpressionDef.constant(i))
            .cast(TypeDef.erasure(entry.getType()));
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
            .field("argument", TypeDef.of(Argument.class));
    }

    private ExpressionDef resolveFieldArgument(int fieldIndex) {
        return beanDefinitionTypeDef.getStaticField(FIELD_INJECTION_FIELDS, TypeDef.of(AbstractInitializableBeanDefinition.FieldReference[].class))
            .arrayElement(fieldIndex)
            .field("argument", TypeDef.of(Argument.class));
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

    private boolean isAnnotatedWithParameter(AnnotationMetadata annotationMetadata) {
        if (annotationMetadata != null) {
            return annotationMetadata.hasDeclaredAnnotation(Parameter.class);
        }
        return false;
    }

    private boolean isParametrized(ParameterElement... parameters) {
        return Arrays.stream(parameters).anyMatch(p -> isAnnotatedWithParameter(p.getAnnotationMetadata()));
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

    private boolean isConfigurationProperties(AnnotationMetadata annotationMetadata) {
        return isIterable(annotationMetadata) || annotationMetadata.hasStereotype(ConfigurationReader.class);
    }

    private boolean isIterable(AnnotationMetadata annotationMetadata) {
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
                    this.annotationMetadata,
                    ClassElement.of(beanFullClassName),
                    beanDefinitionTypeDef,
                    Arrays.asList(methodElement.getParameters()),
                    loadTypeMethods
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

    private ExpressionDef getNewFieldReference(TypedElement declaringType, FieldElement fieldElement, AnnotationMetadata annotationMetadata) {
        return ClassTypeDef.of(AbstractInitializableBeanDefinition.FieldReference.class)
            .instantiate(
                FIELD_REFERENCE_CONSTRUCTOR,

                // 1: declaringType
                ExpressionDef.constant(TypeDef.erasure(declaringType)),
                // 2: argument
                ArgumentExpUtils.pushCreateArgument(
                    this.annotationMetadata,
                    ClassElement.of(beanFullClassName),
                    beanDefinitionTypeDef,
                    fieldElement.getName(),
                    fieldElement.getGenericType(),
                    annotationMetadata,
                    fieldElement.getGenericType().getTypeArguments(),
                    loadTypeMethods
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
//
//        MutableAnnotationMetadata.contributeDefaults(
//            this.annotationMetadata,
//            annotationMetadata
//        );

        if (annotationMetadata == AnnotationMetadata.EMPTY_METADATA || annotationMetadata.isEmpty()) {
            return ExpressionDef.nullValue();
        } else if (annotationMetadata instanceof AnnotationMetadataHierarchy annotationMetadataHierarchy) {
            return AnnotationMetadataGenUtils.instantiateNewMetadataHierarchy(
                beanDefinitionTypeDef,
                annotationMetadataHierarchy,
                loadTypeMethods);
        } else if (annotationMetadata instanceof MutableAnnotationMetadata mutableAnnotationMetadata) {
            return AnnotationMetadataGenUtils.instantiateNewMetadata(
                beanDefinitionTypeDef,
                mutableAnnotationMetadata,
                loadTypeMethods);
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
        if (fieldInjectionPoints.isEmpty() && methodInjectionPoints.isEmpty()) {
            return Collections.emptyList();
        } else {
            Collection<Element> injectionPoints = new ArrayList<>();
            for (FieldVisitData fieldInjectionPoint : fieldInjectionPoints) {
                injectionPoints.add(fieldInjectionPoint.fieldElement);
            }
            for (MethodVisitData methodInjectionPoint : methodInjectionPoints) {
                injectionPoints.add(methodInjectionPoint.methodElement);
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

    @Override
    public boolean isProxyTarget() {
        return isProxyTarget;
    }

    @Override
    public boolean isProxiedBean() {
        return proxiedBean;
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

    @Internal
    private static final class FieldVisitData {
        final TypedElement beanType;
        final FieldElement fieldElement;
        final AnnotationMetadata annotationMetadata;
        final boolean requiresReflection;

        FieldVisitData(
            TypedElement beanType,
            FieldElement fieldElement,
            AnnotationMetadata annotationMetadata,
            boolean requiresReflection) {
            this.beanType = beanType;
            this.fieldElement = fieldElement;
            this.annotationMetadata = annotationMetadata;
            this.requiresReflection = requiresReflection;
        }

    }

    /**
     * Data used when visiting method.
     */
    @Internal
    public static final class MethodVisitData {
        private final TypedElement beanType;
        private final boolean requiresReflection;
        private final MethodElement methodElement;
        private final AnnotationMetadata annotationMetadata;
        private final boolean postConstruct;
        private final boolean preDestroy;

        /**
         * Default constructor.
         *
         * @param beanType           The declaring type
         * @param methodElement      The method element
         * @param requiresReflection Whether reflection is required
         * @param annotationMetadata The annotation metadata
         */
        MethodVisitData(
            TypedElement beanType,
            MethodElement methodElement,
            boolean requiresReflection,
            AnnotationMetadata annotationMetadata) {
            this.beanType = beanType;
            this.requiresReflection = requiresReflection;
            this.methodElement = methodElement;
            this.annotationMetadata = annotationMetadata;
            this.postConstruct = false;
            this.preDestroy = false;
        }

        MethodVisitData(
            TypedElement beanType,
            MethodElement methodElement,
            boolean requiresReflection,
            AnnotationMetadata annotationMetadata,
            boolean postConstruct,
            boolean preDestroy) {
            this.beanType = beanType;
            this.requiresReflection = requiresReflection;
            this.methodElement = methodElement;
            this.annotationMetadata = annotationMetadata;
            this.postConstruct = postConstruct;
            this.preDestroy = preDestroy;
        }

        /**
         * @return The method element
         */
        public MethodElement getMethodElement() {
            return methodElement;
        }

        /**
         * @return The annotationMetadata
         */
        public AnnotationMetadata getAnnotationMetadata() {
            return annotationMetadata;
        }

        /**
         * @return The declaring type object.
         */
        public TypedElement getBeanType() {
            return beanType;
        }

        /**
         * @return is reflection required
         */
        public boolean isRequiresReflection() {
            return requiresReflection;
        }

        public boolean isPostConstruct() {
            return postConstruct;
        }

        public boolean isPreDestroy() {
            return preDestroy;
        }
    }

    private static final class FactoryBuildMethodDefinition extends BuildMethodDefinition {
        private final ClassElement factoryClass;
        private final Element factoryElement;
        private final ParameterElement[] parameters;

        private FactoryBuildMethodDefinition(ClassElement factoryClass, Element factoryElement, ParameterElement[] parameters) {
            this.factoryClass = factoryClass;
            this.factoryElement = factoryElement;
            this.parameters = parameters;
        }

        @Override
        public ParameterElement[] getParameters() {
            return parameters;
        }
    }

    private static final class ConstructorBuildMethodDefinition extends BuildMethodDefinition {
        private final MethodElement constructor;
        private final boolean requiresReflection;

        private ConstructorBuildMethodDefinition(MethodElement constructor, boolean requiresReflection) {
            this.constructor = constructor;
            this.requiresReflection = requiresReflection;
        }

        @Override
        ParameterElement[] getParameters() {
            return constructor.getParameters();
        }
    }

    private abstract static class BuildMethodDefinition {

        private BuildMethodLifecycleDefinition postConstruct;
        private BuildMethodLifecycleDefinition preDestroy;

        abstract ParameterElement[] getParameters();

        void postConstruct(boolean intercepted) {
            if (postConstruct == null) {
                postConstruct = new BuildMethodLifecycleDefinition(intercepted);
            }
        }

        void preDestroy(boolean intercepted) {
            if (preDestroy == null) {
                preDestroy = new BuildMethodLifecycleDefinition(intercepted);
            }
        }
    }

    private static final class BuildMethodLifecycleDefinition {
        private final boolean intercepted;
        private final List<InjectMethodBuildCommand> injectionPoints = new ArrayList<>();

        private BuildMethodLifecycleDefinition(boolean intercepted) {
            this.intercepted = intercepted;
        }
    }

    private record SetterInjectionInjectCommand(TypedElement declaringType,
                                                MethodElement methodElement,
                                                AnnotationMetadata annotationMetadata,
                                                boolean requiresReflection,
                                                boolean isOptional) implements InjectMethodCommand {

        @Override
        public boolean hasInjectScope() {
            return BeanDefinitionWriter.hasInjectScope(methodElement.getParameters());
        }

    }

    private record InjectMethodInjectCommand(TypedElement declaringType,
                                             MethodElement methodElement,
                                             boolean requiresReflection,
                                             VisitorContext visitorContext,
                                             int methodIndex) implements InjectMethodCommand {

        @Override
        public boolean hasInjectScope() {
            return BeanDefinitionWriter.hasInjectScope(methodElement.getParameters());
        }

    }

    private record ConfigFieldBuilderInjectCommand(ClassElement type,
                                                   String field,
                                                   AnnotationMetadata annotationMetadata,
                                                   ConfigurationMetadataBuilder metadataBuilder,
                                                   boolean isInterface,
                                                   ConfigBuilderState configBuilderState,
                                                   List<ConfigBuilderPointInjectCommand> builderPoints) implements ConfigBuilderInjectCommand {

        @Override
        public boolean hasInjectScope() {
            return false;
        }

    }

    private record ConfigMethodBuilderInjectPointCommand(ClassElement type,
                                                         String methodName,
                                                         AnnotationMetadata annotationMetadata,
                                                         ConfigurationMetadataBuilder metadataBuilder,
                                                         boolean isInterface,
                                                         ConfigBuilderState configBuilderState,
                                                         List<ConfigBuilderPointInjectCommand> builderPoints) implements ConfigBuilderInjectCommand {

        @Override
        public boolean hasInjectScope() {
            return false;
        }
    }

    private record ConfigBuilderMethodDurationInjectCommand(String propertyName,
                                                            ClassElement returnType,
                                                            String methodName,
                                                            String path) implements ConfigBuilderPointInjectCommand {

    }

    private record ConfigBuilderMethodInjectCommand(String propertyName,
                                                    ClassElement returnType,
                                                    String methodName,
                                                    ClassElement paramType,
                                                    Map<String, ClassElement> generics,
                                                    String path) implements ConfigBuilderPointInjectCommand {

    }

    private interface ConfigBuilderInjectCommand extends InjectMethodCommand {
        List<ConfigBuilderPointInjectCommand> builderPoints();
    }

    private interface ConfigBuilderPointInjectCommand {
    }

    private record InjectFieldInjectCommand(TypedElement declaringType,
                                            FieldElement fieldElement,
                                            boolean requiresReflection) implements InjectMethodCommand {

        @Override
        public boolean hasInjectScope() {
            return BeanDefinitionWriter.hasInjectScope(fieldElement);
        }
    }

    private record InjectFieldValueInjectCommand(TypedElement declaringType,
                                                 FieldElement fieldElement,
                                                 boolean requiresReflection,
                                                 boolean isOptional) implements InjectMethodCommand {

        @Override
        public boolean hasInjectScope() {
            return BeanDefinitionWriter.hasInjectScope(fieldElement);
        }
    }

    private interface InjectMethodCommand {

        boolean hasInjectScope();

    }

    private record InjectMethodBuildCommand(TypedElement declaringType, MethodElement methodElement,
                                            boolean requiresReflection, int methodIndex) {
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

    private record StaticBlock(@NonNull
                               StatementDef statement,
                               @NonNull
                               FieldDef annotationMetadataField,
                               @NonNull
                               FieldDef failedInitializationField,
                               @NonNull
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
                               @NonNull
                               FieldDef precalculatedInfoField,
                               @Nullable
                               FieldDef preStartConditionsField,
                               @Nullable
                               FieldDef postStartConditionsField) {
    }

}
