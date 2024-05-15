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
package io.micronaut.inject.writer;

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
import io.micronaut.context.annotation.Autowired;
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
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Value;
import io.micronaut.context.env.ConfigurationPath;
import io.micronaut.core.annotation.AccessorsStyle;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationMetadataProvider;
import io.micronaut.core.annotation.AnnotationUtil;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.AnnotationValueBuilder;
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
import io.micronaut.core.reflect.ClassUtils;
import io.micronaut.core.reflect.ReflectionUtils;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.DefaultArgument;
import io.micronaut.core.type.TypeVariableResolver;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.core.util.Toggleable;
import io.micronaut.inject.AdvisedBeanType;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.BeanDefinitionReference;
import io.micronaut.inject.DisposableBeanDefinition;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.inject.ExecutableMethodsDefinition;
import io.micronaut.inject.InitializingBeanDefinition;
import io.micronaut.inject.InjectableBeanDefinition;
import io.micronaut.inject.InjectionPoint;
import io.micronaut.inject.ParametrizedInstantiatableBeanDefinition;
import io.micronaut.inject.ProxyBeanDefinition;
import io.micronaut.inject.ValidatedBeanDefinition;
import io.micronaut.inject.annotation.AbstractAnnotationMetadataBuilder;
import io.micronaut.inject.annotation.AnnotationMetadataHierarchy;
import io.micronaut.inject.annotation.AnnotationMetadataReference;
import io.micronaut.inject.annotation.AnnotationMetadataWriter;
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
import io.micronaut.inject.processing.JavaModelUtils;
import io.micronaut.inject.qualifiers.AnyQualifier;
import io.micronaut.inject.qualifiers.Qualifiers;
import io.micronaut.inject.visitor.BeanElementVisitor;
import io.micronaut.inject.visitor.BeanElementVisitorContext;
import io.micronaut.inject.visitor.VisitorContext;
import jakarta.inject.Singleton;
import java.util.function.BiConsumer;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.signature.SignatureVisitor;

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
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Predicate;
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
public class BeanDefinitionWriter extends AbstractClassFileWriter implements BeanDefinitionVisitor, BeanElement, Toggleable {
    @NextMajorVersion("Inline as true")
    public static final String OMIT_CONFPROP_INJECTION_POINTS = "micronaut.processing.omit.confprop.injectpoints";

    public static final String CLASS_SUFFIX = "$Definition";

    private static final Constructor<AbstractBeanDefinitionBeanConstructor> CONSTRUCTOR_ABSTRACT_CONSTRUCTOR_IP = ReflectionUtils.findConstructor(
                    AbstractBeanDefinitionBeanConstructor.class,
                    BeanDefinition.class)
            .orElseThrow(() -> new ClassGenerationException("Invalid version of Micronaut present on the class path"));

    private static final Method POST_CONSTRUCT_METHOD = ReflectionUtils.getRequiredInternalMethod(AbstractInitializableBeanDefinition.class, "postConstruct", BeanResolutionContext.class, BeanContext.class, Object.class);

    private static final org.objectweb.asm.commons.Method INJECT_BEAN_METHOD = org.objectweb.asm.commons.Method.getMethod(
            ReflectionUtils.getRequiredInternalMethod(InjectableBeanDefinition.class, "inject", BeanResolutionContext.class, BeanContext.class, Object.class)
    );

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

    private static final org.objectweb.asm.commons.Method CONTAINS_PROPERTIES_VALUE_METHOD = org.objectweb.asm.commons.Method.getMethod(ReflectionUtils.getRequiredInternalMethod(
            AbstractInitializableBeanDefinition.class,
            "containsPropertiesValue",
            BeanResolutionContext.class,
            BeanContext.class,
            String.class));

    private static final org.objectweb.asm.commons.Method CONTAINS_PROPERTY_VALUE_METHOD = org.objectweb.asm.commons.Method.getMethod(ReflectionUtils.getRequiredInternalMethod(
            AbstractInitializableBeanDefinition.class,
            "containsPropertyValue",
            BeanResolutionContext.class,
            BeanContext.class,
            String.class));

    private static final Type TYPE_ABSTRACT_BEAN_DEFINITION_AND_REFERENCE = Type.getType(AbstractInitializableBeanDefinitionAndReference.class);

    private static final org.objectweb.asm.commons.Method METHOD_OPTIONAL_EMPTY = org.objectweb.asm.commons.Method.getMethod(
            ReflectionUtils.getRequiredMethod(Optional.class, "empty")
    );
    private static final Type TYPE_OPTIONAL = Type.getType(Optional.class);
    private static final org.objectweb.asm.commons.Method METHOD_OPTIONAL_OF = org.objectweb.asm.commons.Method.getMethod(
            ReflectionUtils.getRequiredMethod(Optional.class, "of", Object.class)
    );
    private static final String METHOD_NAME_INSTANTIATE = "instantiate";
    private static final org.objectweb.asm.commons.Method METHOD_BEAN_CONSTRUCTOR_INSTANTIATE = org.objectweb.asm.commons.Method.getMethod(ReflectionUtils.getRequiredMethod(
            BeanConstructor.class,
        METHOD_NAME_INSTANTIATE,
            Object[].class
    ));
    private static final String METHOD_DESCRIPTOR_CONSTRUCTOR_INSTANTIATE = getMethodDescriptor(Object.class, Arrays.asList(
            BeanResolutionContext.class,
            BeanContext.class,
            List.class,
            BeanDefinition.class,
            BeanConstructor.class,
            int.class,
            Object[].class
    ));
    private static final String METHOD_DESCRIPTOR_INTERCEPTED_LIFECYCLE = getMethodDescriptor(Object.class, Arrays.asList(
            BeanResolutionContext.class,
            BeanContext.class,
            BeanDefinition.class,
            ExecutableMethod.class,
            Object.class
    ));
    private static final Method METHOD_GET_BEAN = ReflectionUtils.getRequiredInternalMethod(DefaultBeanContext.class, "getBean", BeanResolutionContext.class, Class.class, Qualifier.class);
    private static final org.objectweb.asm.commons.Method COLLECTION_TO_ARRAY = org.objectweb.asm.commons.Method.getMethod(
            ReflectionUtils.getRequiredInternalMethod(Collection.class, "toArray", Object[].class)
    );
    private static final Type TYPE_RESOLUTION_CONTEXT = Type.getType(BeanResolutionContext.class);
    private static final Type TYPE_BEAN_CONTEXT = Type.getType(BeanContext.class);
    private static final Type TYPE_BEAN_DEFINITION = Type.getType(BeanDefinition.class);
    private static final String METHOD_DESCRIPTOR_INITIALIZE = Type.getMethodDescriptor(Type.getType(Object.class), Type.getType(BeanResolutionContext.class), Type.getType(BeanContext.class), Type.getType(Object.class));

    private static final org.objectweb.asm.commons.Method PROTECTED_ABSTRACT_BEAN_DEFINITION_CONSTRUCTOR = new org.objectweb.asm.commons.Method(CONSTRUCTOR_NAME, getConstructorDescriptor(
            Class.class, // beanType
            AbstractInitializableBeanDefinition.MethodOrFieldReference.class // constructor
    ));

    private static final org.objectweb.asm.commons.Method SET_FIELD_WITH_REFLECTION_METHOD = org.objectweb.asm.commons.Method.getMethod(
            ReflectionUtils.getRequiredMethod(AbstractInitializableBeanDefinition.class, "setFieldWithReflection", BeanResolutionContext.class, BeanContext.class, int.class, Object.class, Object.class)
    );

    private static final org.objectweb.asm.commons.Method INVOKE_WITH_REFLECTION_METHOD = org.objectweb.asm.commons.Method.getMethod(
            ReflectionUtils.getRequiredMethod(AbstractInitializableBeanDefinition.class, "invokeMethodWithReflection", BeanResolutionContext.class, BeanContext.class, int.class, Object.class, Object[].class)
    );

    private static final org.objectweb.asm.commons.Method IS_METHOD_RESOLVED = org.objectweb.asm.commons.Method.getMethod(
        ReflectionUtils.getRequiredMethod(AbstractInitializableBeanDefinition.class, "isMethodResolved", int.class, Object[].class)
    );

    private static final Type TYPE_REFLECTION_UTILS = Type.getType(ReflectionUtils.class);

    private static final org.objectweb.asm.commons.Method GET_FIELD_WITH_REFLECTION_METHOD = org.objectweb.asm.commons.Method.getMethod(
            ReflectionUtils.getRequiredInternalMethod(ReflectionUtils.class, "getField", Class.class, String.class, Object.class));

    private static final org.objectweb.asm.commons.Method METHOD_INVOKE_METHOD = org.objectweb.asm.commons.Method.getMethod(
            ReflectionUtils.getRequiredInternalMethod(ReflectionUtils.class, "invokeMethod", Object.class, java.lang.reflect.Method.class, Object[].class));

    private static final org.objectweb.asm.commons.Method BEAN_DEFINITION_CLASS_CONSTRUCTOR = new org.objectweb.asm.commons.Method(CONSTRUCTOR_NAME, getConstructorDescriptor(
            Class.class, // beanType
            AbstractInitializableBeanDefinition.MethodOrFieldReference.class, // constructor
            AnnotationMetadata.class, // annotationMetadata
            AbstractInitializableBeanDefinition.MethodReference[].class, // methodInjection
            AbstractInitializableBeanDefinition.FieldReference[].class, // fieldInjection
            AbstractInitializableBeanDefinition.AnnotationReference[].class, // annotationInjection
            ExecutableMethodsDefinition.class, // executableMethodsDefinition
            Map.class, // typeArgumentsMap
            AbstractInitializableBeanDefinition.PrecalculatedInfo.class // precalculated info
    ));

    private static final Type PRECALCULATED_INFO = Type.getType(AbstractInitializableBeanDefinition.PrecalculatedInfo.class);
    private static final org.objectweb.asm.commons.Method PRECALCULATED_INFO_CONSTRUCTOR = org.objectweb.asm.commons.Method.getMethod(
            ReflectionUtils.getRequiredInternalConstructor(AbstractInitializableBeanDefinition.PrecalculatedInfo.class,
                    Optional.class, // scope
                    boolean.class, // isAbstract
                    boolean.class, // isIterable
                    boolean.class, // isSingleton
                    boolean.class, // isPrimary
                    boolean.class, // isConfigurationProperties
                    boolean.class, // isContainerType
                    boolean.class,  // requiresMethodProcessing,
                    boolean.class // hasEvaluatedExpressions
            ));

    private static final String FIELD_CONSTRUCTOR = "$CONSTRUCTOR";
    private static final String FIELD_INJECTION_METHODS = "$INJECTION_METHODS";
    private static final String FIELD_INJECTION_FIELDS = "$INJECTION_FIELDS";
    private static final String FIELD_ANNOTATION_INJECTIONS = "$ANNOTATION_INJECTIONS";
    private static final String FIELD_TYPE_ARGUMENTS = "$TYPE_ARGUMENTS";
    private static final String FIELD_INNER_CLASSES = "$INNER_CONFIGURATION_CLASSES";
    private static final String FIELD_EXPOSED_TYPES = "$EXPOSED_TYPES";

    private static final org.objectweb.asm.commons.Method METHOD_REFERENCE_CONSTRUCTOR = new org.objectweb.asm.commons.Method(CONSTRUCTOR_NAME, getConstructorDescriptor(
            Class.class, // declaringType,
            String.class, // methodName
            Argument[].class, // arguments
            AnnotationMetadata.class// annotationMetadata
    ));

    private static final org.objectweb.asm.commons.Method METHOD_REFERENCE_CONSTRUCTOR_POST_PRE = new org.objectweb.asm.commons.Method(CONSTRUCTOR_NAME, getConstructorDescriptor(
            Class.class, // declaringType,
            String.class, // methodName
            Argument[].class, // arguments
            AnnotationMetadata.class, // annotationMetadata
            boolean.class, // isPostConstructMethod
            boolean.class // isPreDestroyMethod,
    ));

    private static final org.objectweb.asm.commons.Method FIELD_REFERENCE_CONSTRUCTOR = new org.objectweb.asm.commons.Method(CONSTRUCTOR_NAME, getConstructorDescriptor(
            Class.class, // declaringType;
            Argument.class // argument;
    ));

    private static final org.objectweb.asm.commons.Method ANNOTATION_REFERENCE_CONSTRUCTOR = new org.objectweb.asm.commons.Method(CONSTRUCTOR_NAME, getConstructorDescriptor(
            Argument.class // argument;
    ));

    private static final org.objectweb.asm.commons.Method METHOD_QUALIFIER_FOR_ARGUMENT =
            org.objectweb.asm.commons.Method.getMethod(
                    ReflectionUtils.getRequiredMethod(Qualifiers.class, "forArgument", Argument.class)
            );
    private static final org.objectweb.asm.commons.Method METHOD_QUALIFIER_BY_NAME =
            org.objectweb.asm.commons.Method.getMethod(
                    ReflectionUtils.getRequiredMethod(Qualifiers.class, "byName", String.class)
            );
    private static final org.objectweb.asm.commons.Method METHOD_QUALIFIER_BY_ANNOTATION =
            org.objectweb.asm.commons.Method.getMethod(
                    ReflectionUtils.getRequiredMethod(Qualifiers.class, "byAnnotationSimple", AnnotationMetadata.class, String.class)
            );
    private static final org.objectweb.asm.commons.Method METHOD_QUALIFIER_BY_REPEATABLE_ANNOTATION =
            org.objectweb.asm.commons.Method.getMethod(
                    ReflectionUtils.getRequiredMethod(Qualifiers.class, "byRepeatableAnnotation", AnnotationMetadata.class, String.class)
            );
    private static final org.objectweb.asm.commons.Method METHOD_QUALIFIER_BY_QUALIFIERS =
            org.objectweb.asm.commons.Method.getMethod(
                    ReflectionUtils.getRequiredMethod(Qualifiers.class, "byQualifiers", Qualifier[].class)
            );
    private static final org.objectweb.asm.commons.Method METHOD_QUALIFIER_BY_INTERCEPTOR_BINDING =
            org.objectweb.asm.commons.Method.getMethod(
                    ReflectionUtils.getRequiredMethod(Qualifiers.class, "byInterceptorBinding", AnnotationMetadata.class)
            );
    private static final org.objectweb.asm.commons.Method METHOD_QUALIFIER_BY_TYPE = org.objectweb.asm.commons.Method.getMethod(
            ReflectionUtils.getRequiredMethod(Qualifiers.class, "byType", Class[].class)
    );

    private static final org.objectweb.asm.commons.Method METHOD_BEAN_RESOLUTION_CONTEXT_MARK_FACTORY = org.objectweb.asm.commons.Method.getMethod(
            ReflectionUtils.getRequiredMethod(BeanResolutionContext.class, "markDependentAsFactory")
    );
    private static final Type TYPE_QUALIFIERS = Type.getType(Qualifiers.class);
    private static final Type TYPE_QUALIFIER = Type.getType(Qualifier.class);
    private static final String MESSAGE_ONLY_SINGLE_CALL_PERMITTED = "Only a single call to visitBeanFactoryMethod(..) is permitted";

    private static final int INJECT_METHOD_BEAN_RESOLUTION_CONTEXT_PARAM = 0;
    private static final int INJECT_METHOD_BEAN_CONTEXT_PARAM = 1;
    private static final int INJECT_METHOD_BEAN_INSTANCE_PARAM = 2;

    private static final int INSTANTIATE_METHOD_BEAN_RESOLUTION_CONTEXT_PARAM = 0;
    private static final int INSTANTIATE_METHOD_BEAN_CONTEXT_PARAM = 1;

    private static final org.objectweb.asm.commons.Method METHOD_BEAN_CONTEXT_GET_CONVERSION_SERVICE = org.objectweb.asm.commons.Method.getMethod(
            ReflectionUtils.getRequiredMethod(ConversionServiceProvider.class, "getConversionService")
    );

    private static final org.objectweb.asm.commons.Method METHOD_INVOKE_INTERNAL = org.objectweb.asm.commons.Method.getMethod(
        ReflectionUtils.getRequiredInternalMethod(AbstractExecutableMethod.class, "invokeInternal", Object.class, Object[].class));

    private final ClassWriter classWriter;
    private final String beanFullClassName;
    private final String beanDefinitionName;
    private final String beanDefinitionInternalName;
    private final Type beanType;
    private final Set<Class<?>> interfaceTypes;
    private final Map<String, Integer> defaultsStorage = new HashMap<>();
    private final Map<String, GeneratorAdapter> loadTypeMethods = new LinkedHashMap<>();
    private final Map<String, ClassWriter> innerClasses = new LinkedHashMap<>(2);
    private final String packageName;
    private final String beanSimpleClassName;
    private final Type beanDefinitionType;
    private final boolean isInterface;
    private final boolean isAbstract;
    private final boolean isConfigurationProperties;
    private final Element beanProducingElement;
    private final ClassElement beanTypeElement;
    private final VisitorContext visitorContext;
    private final boolean isPrimitiveBean;
    private final List<String> beanTypeInnerClasses;
    private final EvaluatedExpressionProcessor evaluatedExpressionProcessor;
    private GeneratorAdapter buildMethodVisitor;
    private GeneratorAdapter injectMethodVisitor;
    private GeneratorAdapter checkIfShouldLoadMethodVisitor;
    private Label injectEnd = null;
    private GeneratorAdapter preDestroyMethodVisitor;
    private GeneratorAdapter postConstructMethodVisitor;
    private boolean postConstructAdded;
    private GeneratorAdapter interceptedDisposeMethod;
    private int currentFieldIndex = 0;
    private int currentMethodIndex = 0;

    private int buildInstanceLocalVarIndex = -1;
    private int injectInstanceLocalVarIndex = -1;
    private int postConstructInstanceLocalVarIndex = -1;
    private int preDestroyInstanceLocalVarIndex = -1;
    private boolean beanFinalized = false;
    private Type superType = TYPE_ABSTRACT_BEAN_DEFINITION_AND_REFERENCE;
    private boolean isParametrized = false;
    private boolean superBeanDefinition = false;
    private boolean isSuperFactory = false;
    private final AnnotationMetadata annotationMetadata;
    private ConfigBuilderState currentConfigBuilderState;
    private boolean preprocessMethods = false;
    private Map<String, Map<String, ClassElement>> typeArguments;
    private String interceptedType;

    private int innerClassIndex;

    private final List<FieldVisitData> fieldInjectionPoints = new ArrayList<>(2);
    private final List<MethodVisitData> methodInjectionPoints = new ArrayList<>(2);
    private final List<MethodVisitData> postConstructMethodVisits = new ArrayList<>(2);
    private final List<MethodVisitData> preDestroyMethodVisits = new ArrayList<>(2);
    private final List<MethodVisitData> allMethodVisits = new ArrayList<>(2);
    private final Map<Type, List<AnnotationVisitData>> annotationInjectionPoints = new LinkedHashMap<>(2);
    private final Map<String, Boolean> isLifeCycleCache = new HashMap<>(2);
    private ExecutableMethodsDefinitionWriter executableMethodsDefinitionWriter;

    private Object constructor; // MethodElement or FieldElement
    private boolean disabled = false;

    private final boolean keepConfPropInjectPoints;
    private boolean proxiedBean = false;
    private boolean isProxyTarget = false;

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
        super(originatingElements);
        this.classWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
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
        this.isPrimitiveBean = beanTypeElement.isPrimitive() && !beanTypeElement.isArray();
        this.annotationMetadata = beanProducingElement.getTargetAnnotationMetadata();
        this.beanDefinitionType = getTypeReferenceForName(this.beanDefinitionName);
        this.beanType = getTypeReference(beanTypeElement);
        this.beanDefinitionInternalName = getInternalName(this.beanDefinitionName);
        this.interfaceTypes = new TreeSet<>(Comparator.comparing(Class::getName));
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
    public final List<MethodVisitData> getPostConstructMethodVisits() {
        return Collections.unmodifiableList(postConstructMethodVisits);
    }

    /**
     * @return The underlying class writer
     */
    public ClassVisitor getClassWriter() {
        return classWriter;
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
        this.interfaceTypes.add(interfaceType);
    }

    @Override
    public void visitSuperBeanDefinition(String name) {
        this.superBeanDefinition = true;
        this.superType = getTypeReferenceForName(name);
    }

    @Override
    public void visitSuperBeanDefinitionFactory(String beanName) {
        visitSuperBeanDefinition(beanName);
        this.superBeanDefinition = false;
        this.isSuperFactory = true;
    }

    @Override
    public String getBeanTypeName() {
        return beanFullClassName;
    }

    @Override
    public Type getProvidedType() {
        return beanType;
    }

    @Override
    public void setValidated(boolean validated) {
        if (validated) {
            this.interfaceTypes.add(ValidatedBeanDefinition.class);
        } else {
            this.interfaceTypes.remove(ValidatedBeanDefinition.class);
        }
    }

    @Override
    public void setInterceptedType(String typeName) {
        if (typeName != null) {
            this.interfaceTypes.add(AdvisedBeanType.class);
        }
        this.interceptedType = typeName;
    }

    @Override
    public Optional<Type> getInterceptedType() {
        return Optional.ofNullable(interceptedType)
                .map(BeanDefinitionWriter::getTypeReferenceForName);
    }

    @Override
    public boolean isValidated() {
        return this.interfaceTypes.contains(ValidatedBeanDefinition.class);
    }

    @Override
    public String getBeanDefinitionName() {
        return beanDefinitionName;
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
            // now implement the inject method
            visitInjectMethodDefinition();
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
            // now implement the inject method
            visitInjectMethodDefinition();
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
            // now implement the inject method
            visitInjectMethodDefinition();
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
            visitBuildMethodDefinition(constructor, requiresReflection);

            // now implement the inject method
            visitInjectMethodDefinition();

            evaluatedExpressionProcessor.processEvaluatedExpressions(constructor.getAnnotationMetadata(), null);
            for (ParameterElement parameter: constructor.getParameters()) {
                evaluatedExpressionProcessor.processEvaluatedExpressions(parameter.getAnnotationMetadata(), null);
            }
        }
    }

    @Override
    public void visitDefaultConstructor(AnnotationMetadata annotationMetadata, VisitorContext visitorContext) {
        if (this.constructor == null) {
            ClassElement bean = ClassElement.of(beanType.getClassName());
            MethodElement defaultConstructor = MethodElement.of(
                    bean,
                    annotationMetadata,
                    bean,
                    bean,
                    "<init>"
            );
            constructor = defaultConstructor;

            // now prepare the implementation of the build method. See BeanFactory interface
            visitBuildMethodDefinition(defaultConstructor, false);
            // now implement the inject method
            visitInjectMethodDefinition();
        }
    }

    /**
     * Finalize the bean definition to the given output stream.
     */
    @SuppressWarnings("Duplicates")
    @Override
    public void visitBeanDefinitionEnd() {
        if (classWriter == null) {
            throw new IllegalStateException("At least one called to visitBeanDefinitionConstructor(..) is required");
        }

        if (executableMethodsDefinitionWriter != null) {
            // Make sure the methods are written and annotation defaults are contributed
            executableMethodsDefinitionWriter.visitDefinitionEnd();
        }

        processAllBeanElementVisitors();

        if (constructor instanceof MethodElement methodElement) {
            boolean isParametrized = Arrays.stream(methodElement.getParameters())
                    .map(AnnotationMetadataProvider::getAnnotationMetadata)
                    .anyMatch(this::isAnnotatedWithParameter);
            if (isParametrized) {
                this.interfaceTypes.add(ParametrizedInstantiatableBeanDefinition.class);
            }
        }

        classWriter.visit(
            V17,
            ACC_PUBLIC | ACC_SYNTHETIC,
            beanDefinitionInternalName,
            generateBeanDefSig(beanType),
            getSuperTypeInternalType(),
            interfaceTypes.stream().map(Type::getInternalName).toArray(String[]::new)
        );

        annotateAsGeneratedAndService(classWriter, BeanDefinitionReference.class.getName());

        if (buildMethodVisitor == null) {
            throw new IllegalStateException("At least one call to visitBeanDefinitionConstructor() is required");
        }

        GeneratorAdapter staticInit = visitStaticInitializer(classWriter);

        classWriter.visitField(ACC_PRIVATE | ACC_FINAL | ACC_STATIC, FIELD_CONSTRUCTOR,
                Type.getType(AbstractInitializableBeanDefinition.MethodOrFieldReference.class).getDescriptor(), null, null);

        if (!superBeanDefinition && !allMethodVisits.isEmpty()) {
            Type methodsFieldType = Type.getType(AbstractInitializableBeanDefinition.MethodReference[].class);
            classWriter.visitField(ACC_PRIVATE | ACC_FINAL | ACC_STATIC, FIELD_INJECTION_METHODS, methodsFieldType.getDescriptor(), null, null);
            pushNewArray(staticInit, AbstractInitializableBeanDefinition.MethodReference.class, allMethodVisits, methodVisitData -> {
                pushNewMethodReference(
                    staticInit,
                    JavaModelUtils.getTypeReference(methodVisitData.beanType),
                    methodVisitData.methodElement,
                    methodVisitData.getAnnotationMetadata(),
                    methodVisitData.isPostConstruct(),
                    methodVisitData.isPreDestroy()
                );
            });
            staticInit.putStatic(beanDefinitionType, FIELD_INJECTION_METHODS, methodsFieldType);
        }

        if (!fieldInjectionPoints.isEmpty()) {
            Type fieldsFieldType = Type.getType(AbstractInitializableBeanDefinition.FieldReference[].class);
            classWriter.visitField(ACC_PRIVATE | ACC_FINAL | ACC_STATIC, FIELD_INJECTION_FIELDS, fieldsFieldType.getDescriptor(), null, null);
            pushNewArray(staticInit, AbstractInitializableBeanDefinition.FieldReference.class, fieldInjectionPoints, fieldVisitData -> {
                pushNewFieldReference(
                    staticInit,
                    JavaModelUtils.getTypeReference(fieldVisitData.beanType),
                    fieldVisitData.fieldElement,
                    fieldVisitData.annotationMetadata
                );
            });
            staticInit.putStatic(beanDefinitionType, FIELD_INJECTION_FIELDS, fieldsFieldType);
        }

        if (!annotationInjectionPoints.isEmpty()) {
            Type annotationInjectionsFieldType = Type.getType(AbstractInitializableBeanDefinition.AnnotationReference[].class);
            classWriter.visitField(ACC_PRIVATE | ACC_FINAL | ACC_STATIC, FIELD_ANNOTATION_INJECTIONS,
                    annotationInjectionsFieldType.getDescriptor(), null, null);

            List<Type> injectedTypes = new ArrayList<>(annotationInjectionPoints.keySet());
            pushNewArray(staticInit, AbstractInitializableBeanDefinition.AnnotationReference.class, injectedTypes, annotationVisitData -> {
                pushNewAnnotationReference(staticInit, annotationVisitData);
            });
            staticInit.putStatic(beanDefinitionType, FIELD_ANNOTATION_INJECTIONS, annotationInjectionsFieldType);
        }

        if (!superBeanDefinition && hasTypeArguments()) {
            Type typeArgumentsFieldType = Type.getType(Map.class);
            classWriter.visitField(ACC_PRIVATE | ACC_FINAL | ACC_STATIC, FIELD_TYPE_ARGUMENTS, typeArgumentsFieldType.getDescriptor(), null, null);
            pushStringMapOf(staticInit, typeArguments, true, null, new Consumer<Map<String, ClassElement>>() {
                @Override
                public void accept(Map<String, ClassElement> stringClassElementMap) {
                    pushTypeArgumentElements(
                            annotationMetadata,
                            beanDefinitionType,
                            classWriter,
                            staticInit,
                            beanDefinitionName,
                            stringClassElementMap,
                            defaultsStorage,
                            loadTypeMethods
                    );
                }
            });
            staticInit.putStatic(beanDefinitionType, FIELD_TYPE_ARGUMENTS, typeArgumentsFieldType);
        }

        // first build the constructor
        visitBeanDefinitionConstructorInternal(
                staticInit,
                constructor
        );

        addInnerConfigurationMethod(staticInit);
        addGetExposedTypes(staticInit);

        staticInit.returnValue();
        staticInit.visitMaxs(DEFAULT_MAX_STACK, defaultsStorage.size() + 3);
        staticInit.visitEnd();

        if (buildMethodVisitor != null) {
            if (isPrimitiveBean) {
                pushBoxPrimitiveIfNecessary(beanType, buildMethodVisitor);
            }
            buildMethodVisitor.returnValue();
            buildMethodVisitor.visitMaxs(DEFAULT_MAX_STACK, 10);
        }
        if (injectMethodVisitor != null) {
            if (injectEnd != null) {
                injectMethodVisitor.visitLabel(injectEnd);
            }
            invokeSuperInjectMethod(injectMethodVisitor, INJECT_BEAN_METHOD);
            if (isPrimitiveBean) {
                pushBoxPrimitiveIfNecessary(beanType, injectMethodVisitor);
            }
            injectMethodVisitor.returnValue();
            injectMethodVisitor.visitMaxs(DEFAULT_MAX_STACK, 10);
        }
        if (postConstructMethodVisitor != null) {
            postConstructMethodVisitor.loadLocal(postConstructInstanceLocalVarIndex);
            postConstructMethodVisitor.returnValue();
            postConstructMethodVisitor.visitMaxs(DEFAULT_MAX_STACK, 10);
        }
        if (preDestroyMethodVisitor != null) {
            preDestroyMethodVisitor.loadLocal(preDestroyInstanceLocalVarIndex);
            preDestroyMethodVisitor.returnValue();
            preDestroyMethodVisitor.visitMaxs(DEFAULT_MAX_STACK, 10);
        }
        if (interceptedDisposeMethod != null) {
            interceptedDisposeMethod.visitMaxs(1, 1);
            interceptedDisposeMethod.visitEnd();
        }
        if (checkIfShouldLoadMethodVisitor != null) {
            buildCheckIfShouldLoadMethod(checkIfShouldLoadMethodVisitor, annotationInjectionPoints);
            checkIfShouldLoadMethodVisitor.visitMaxs(DEFAULT_MAX_STACK, 10);
        }
        addGetOrder();

        getInterceptedType().ifPresent(t -> implementInterceptedTypeMethod(t, this.classWriter));

        GeneratorAdapter loadMethod = startPublicMethodZeroArgs(classWriter, BeanDefinition.class, "load");
        pushNewInstance(loadMethod, beanDefinitionType);
        loadMethod.returnValue();
        loadMethod.endMethod();

        if (annotationMetadata.hasDeclaredAnnotation(Context.class)) {
            GeneratorAdapter isContextScopeMethod = startPublicMethodZeroArgs(classWriter, boolean.class, "isContextScope");
            isContextScopeMethod.push(true);
            isContextScopeMethod.returnValue();
            isContextScopeMethod.endMethod();
        }

        if (proxiedBean || superType != TYPE_ABSTRACT_BEAN_DEFINITION_AND_REFERENCE) {
            GeneratorAdapter isProxiedBeanMethod = startPublicMethodZeroArgs(classWriter, boolean.class, "isProxiedBean");
            isProxiedBeanMethod.push(proxiedBean);
            isProxiedBeanMethod.returnValue();
            isProxiedBeanMethod.endMethod();
        }

        if (isProxyTarget || superType != TYPE_ABSTRACT_BEAN_DEFINITION_AND_REFERENCE) {
            GeneratorAdapter isProxiedBeanMethod = startPublicMethodZeroArgs(classWriter, boolean.class, "isProxyTarget");
            isProxiedBeanMethod.push(isProxyTarget);
            isProxiedBeanMethod.returnValue();
            isProxiedBeanMethod.endMethod();
        }

        if (!annotationMetadata.hasStereotype(Requires.class)) {
            GeneratorAdapter isEnabledBeanMethod = startPublicMethod(classWriter, "isEnabled", boolean.class, BeanContext.class);
            isEnabledBeanMethod.push(true);
            isEnabledBeanMethod.returnValue();
            isEnabledBeanMethod.endMethod();

            GeneratorAdapter isEnabledBeanMethod2 = startPublicMethod(classWriter, "isEnabled", boolean.class, BeanContext.class, BeanResolutionContext.class);
            isEnabledBeanMethod2.push(true);
            isEnabledBeanMethod2.returnValue();
            isEnabledBeanMethod2.endMethod();
        }

        for (GeneratorAdapter method : loadTypeMethods.values()) {
            method.visitMaxs(3, 1);
            method.visitEnd();
        }
        classWriter.visitEnd();
        this.beanFinalized = true;
    }

    private String getSuperTypeInternalType() {
        return getSuperType().getInternalName();
    }

    private Type getSuperType() {
        return isSuperFactory ? TYPE_ABSTRACT_BEAN_DEFINITION_AND_REFERENCE : superType;
    }

    private void buildCheckIfShouldLoadMethod(GeneratorAdapter adapter, Map<Type, List<AnnotationVisitData>> beanPropertyVisitData) {
        List<Type> injectedTypes = new ArrayList<>(beanPropertyVisitData.keySet());

        for (int currentTypeIndex = 0; currentTypeIndex < injectedTypes.size(); currentTypeIndex++) {
            Type injectedType = injectedTypes.get(currentTypeIndex);
            List<AnnotationVisitData> annotationVisitData = beanPropertyVisitData.get(injectedType);
            boolean multiplePropertiesFromType = annotationVisitData.size() > 1;

            Integer injectedBeanIndex = null;
            for (int i = 0; i < annotationVisitData.size(); i++) {
                int currentPropertyIndex = i;
                boolean isLastProperty = currentTypeIndex == (injectedTypes.size() - 1) && currentPropertyIndex == (annotationVisitData.size() - 1);

                AnnotationVisitData visitData = annotationVisitData.get(currentPropertyIndex);
                MethodElement propertyGetter = visitData.memberPropertyGetter;
                // load 'this'
                adapter.loadThis();
                // push injected bean property name
                adapter.push(visitData.memberPropertyName);

                if (injectedBeanIndex != null) {
                    adapter.loadLocal(injectedBeanIndex);
                } else {
                    // load 'this'
                    adapter.loadThis();
                    // 1st argument load BeanResolutionContext
                    adapter.loadArg(0);
                    // 2nd argument load BeanContext
                    adapter.loadArg(1);
                    // 3rd argument the injected bean index
                    adapter.push(currentTypeIndex);
                    // push qualifier
                    pushQualifier(adapter, visitData.memberBeanType, () -> resolveAnnotationArgument(adapter, currentPropertyIndex));
                    // invoke getBeanForAnnotation
                    pushInvokeMethodOnSuperClass(adapter, GET_BEAN_FOR_ANNOTATION);
                    // cast the return value to the correct type
                    pushCastToType(adapter, visitData.memberBeanType);

                    // if multiple properties from same bean should be checked, saving bean to local variable
                    if (multiplePropertiesFromType) {
                        injectedBeanIndex = adapter.newLocal(injectedType);
                        adapter.storeLocal(injectedBeanIndex);
                        adapter.loadLocal(injectedBeanIndex);
                    }
                }

                org.objectweb.asm.commons.Method propertyGetterMethod = org.objectweb.asm.commons.Method.getMethod(propertyGetter.getDescription(false));
                // getter might be an interface method
                if (visitData.memberBeanType.getType().isInterface()) {
                    adapter.invokeInterface(injectedType, propertyGetterMethod);
                } else {
                    adapter.invokeVirtual(injectedType, propertyGetterMethod);
                }
                pushBoxPrimitiveIfNecessary(propertyGetterMethod.getReturnType(), adapter);
                // pushing required arguments and invoking checkBeanPropertyValue
                adapter.push(visitData.requiredValue);
                adapter.push(visitData.notEqualsValue);
                pushInvokeMethodOnSuperClass(adapter, CHECK_INJECTED_BEAN_PROPERTY_VALUE);

                if (isLastProperty) {
                    adapter.returnValue();
                }
            }
        }
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

    private void addInnerConfigurationMethod(GeneratorAdapter staticInit) {
        if (isConfigurationProperties) {


            if (beanTypeInnerClasses.size() > 0) {
                classWriter.visitField(ACC_PRIVATE | ACC_FINAL | ACC_STATIC, FIELD_INNER_CLASSES, Type.getType(Set.class).getDescriptor(), null, null);
                pushStoreClassesAsSet(staticInit, beanTypeInnerClasses.toArray(EMPTY_STRING_ARRAY));
                staticInit.putStatic(beanDefinitionType, FIELD_INNER_CLASSES, Type.getType(Set.class));

                GeneratorAdapter isInnerConfigurationMethod = startProtectedMethod(classWriter, "isInnerConfiguration", boolean.class.getName(), Class.class.getName());
                isInnerConfigurationMethod.getStatic(beanDefinitionType, FIELD_INNER_CLASSES, Type.getType(Set.class));
                isInnerConfigurationMethod.loadArg(0);
                isInnerConfigurationMethod.invokeInterface(Type.getType(Collection.class), org.objectweb.asm.commons.Method.getMethod(
                        ReflectionUtils.getRequiredMethod(Collection.class, "contains", Object.class)
                ));
                isInnerConfigurationMethod.returnValue();
                isInnerConfigurationMethod.visitMaxs(1, 1);
                isInnerConfigurationMethod.visitEnd();
            }
        }
    }

    private void addGetExposedTypes(GeneratorAdapter staticInit) {
        if (annotationMetadata.hasDeclaredAnnotation(Bean.class.getName())) {
            final String[] exposedTypes = annotationMetadata.stringValues(Bean.class.getName(), "typed");
            if (exposedTypes.length > 0) {
                classWriter.visitField(ACC_PRIVATE | ACC_FINAL | ACC_STATIC, FIELD_EXPOSED_TYPES, Type.getType(Set.class).getDescriptor(), null, null);
                pushStoreClassesAsSet(staticInit, exposedTypes);
                staticInit.putStatic(beanDefinitionType, FIELD_EXPOSED_TYPES, Type.getType(Set.class));

                GeneratorAdapter getExposedTypesMethod = startPublicMethod(classWriter, "getExposedTypes", Set.class.getName());
                getExposedTypesMethod.getStatic(beanDefinitionType, FIELD_EXPOSED_TYPES, Type.getType(Set.class));
                getExposedTypesMethod.returnValue();
                getExposedTypesMethod.visitMaxs(1, 1);
                getExposedTypesMethod.visitEnd();
            }
        }
    }

    private void addGetOrder() {
        int order = OrderUtil.getOrder(annotationMetadata);
        if (order != 0) {
            GeneratorAdapter getOrderMethod = startPublicMethod(classWriter, "getOrder", int.class.getName());
            getOrderMethod.push(order);
            getOrderMethod.returnValue();
            getOrderMethod.endMethod();
        }
    }

    private void pushStoreClassesAsSet(GeneratorAdapter writer, String[] classes) {
        if (classes.length > 1) {
            writer.newInstance(Type.getType(HashSet.class));
            writer.dup();
            pushArrayOfClasses(writer, classes);
            writer.invokeStatic(Type.getType(Arrays.class), org.objectweb.asm.commons.Method.getMethod(
                    ReflectionUtils.getRequiredMethod(Arrays.class, "asList", Object[].class)
            ));
            writer.invokeConstructor(Type.getType(HashSet.class), org.objectweb.asm.commons.Method.getMethod(
                    ReflectionUtils.getRequiredInternalConstructor(HashSet.class, Collection.class)
            ));
        } else {
            pushClass(writer, classes[0]);
            writer.invokeStatic(Type.getType(Collections.class), org.objectweb.asm.commons.Method.getMethod(
                    ReflectionUtils.getRequiredMethod(Collections.class, "singleton", Object.class)
            ));
        }
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
            return scope.equals(Singleton.class.getName()) || scope.equals(AnnotationUtil.SINGLETON);
        } else {
            final AnnotationMetadata annotationMetadata;
            if (beanProducingElement instanceof ClassElement) {
                annotationMetadata = getAnnotationMetadata();
            } else {
                annotationMetadata = beanProducingElement.getDeclaredMetadata();
            }

            return annotationMetadata.stringValue(DefaultScope.class)
                    .map(t -> t.equals(Singleton.class.getName()) || t.equals(AnnotationUtil.SINGLETON))
                    .orElse(false);
        }
    }

    private void lookupReferenceAnnotationMetadata(GeneratorAdapter annotationMetadataMethod) {
        annotationMetadataMethod.loadThis();
        annotationMetadataMethod.getStatic(beanDefinitionType, AbstractAnnotationMetadataWriter.FIELD_ANNOTATION_METADATA, Type.getType(AnnotationMetadata.class));
        annotationMetadataMethod.returnValue();
        annotationMetadataMethod.visitMaxs(1, 1);
        annotationMetadataMethod.visitEnd();
    }

    /**
     * @return The bytes of the class
     */
    public byte[] toByteArray() {
        if (!beanFinalized) {
            throw new IllegalStateException("Bean definition not finalized. Call visitBeanDefinitionEnd() first.");
        }
        return classWriter.toByteArray();
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
        try (OutputStream out = visitor.visitClass(getBeanDefinitionName(), getOriginatingElements())) {
            if (!innerClasses.isEmpty()) {
                for (Map.Entry<String, ClassWriter> entry : innerClasses.entrySet()) {
                    try (OutputStream constructorOut = visitor.visitClass(entry.getKey(), getOriginatingElements())) {
                        constructorOut.write(entry.getValue().toByteArray());
                    }
                }
            }
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
            out.write(toByteArray());
        }
    }

    @Override
    public void visitSetterValue(
            TypedElement declaringType,
            MethodElement methodElement,
            AnnotationMetadata annotationMetadata,
            boolean requiresReflection,
            boolean isOptional) {

        if (!requiresReflection) {

            ParameterElement parameter = methodElement.getParameters()[0];

            Label falseCondition = isOptional ? pushPropertyContainsCheck(
                    injectMethodVisitor,
                    parameter.getType(),
                    parameter.getName(),
                    annotationMetadata
            ) : null;

            ClassElement genericType = parameter.getGenericType();
            if (isConfigurationProperties && isValueType(annotationMetadata)) {

                injectMethodVisitor.loadLocal(injectInstanceLocalVarIndex, beanType);

                Optional<String> property = annotationMetadata.stringValue(Property.class, "name");
                Optional<String> valueValue = annotationMetadata.stringValue(Value.class);
                if (isInnerType(genericType)) {
                    boolean isArray = genericType.isArray();
                    boolean isCollection = genericType.isAssignable(Collection.class);
                    if (isCollection || isArray) {
                        ClassElement typeArgument = genericType.isArray() ? genericType.fromArray() : genericType.getFirstTypeArgument().orElse(null);
                        if (typeArgument != null && !typeArgument.isPrimitive()) {
                            pushInvokeGetBeansOfTypeForSetter(injectMethodVisitor, methodElement.getName(), parameter, annotationMetadata);
                        } else {
                            pushInvokeGetBeanForSetter(injectMethodVisitor, methodElement.getName(), parameter, annotationMetadata);
                        }
                    } else {
                        pushInvokeGetBeanForSetter(injectMethodVisitor, methodElement.getName(), parameter, annotationMetadata);
                    }
                } else if (property.isPresent()) {
                    pushInvokeGetPropertyValueForSetter(injectMethodVisitor, methodElement.getName(), parameter, property.get(), annotationMetadata);
                } else if (valueValue.isPresent()) {
                    pushInvokeGetPropertyPlaceholderValueForSetter(injectMethodVisitor, methodElement.getName(), parameter, valueValue.get(), annotationMetadata);
                } else {
                    throw new IllegalStateException();
                }

                Type declaringTypeRef = JavaModelUtils.getTypeReference(declaringType);
                String methodDescriptor = getMethodDescriptor(methodElement.getReturnType(), Arrays.asList(methodElement.getParameters()));
                boolean isDeclaringTypeInterface = declaringType.getType().isInterface();
                injectMethodVisitor.visitMethodInsn(isDeclaringTypeInterface ? INVOKEINTERFACE : INVOKEVIRTUAL,
                        declaringTypeRef.getInternalName(), methodElement.getName(),
                        methodDescriptor, isDeclaringTypeInterface);

                if (!methodElement.getReturnType().isVoid()) {
                    injectMethodVisitor.pop();
                }

                if (keepConfPropInjectPoints) {
                    final MethodVisitData methodVisitData = new MethodVisitData(
                            declaringType,
                            methodElement,
                            false,
                            annotationMetadata);
                    methodInjectionPoints.add(methodVisitData);
                    allMethodVisits.add(methodVisitData);
                    currentMethodIndex++;
                }
            } else {
                final MethodVisitData methodVisitData = new MethodVisitData(
                        declaringType,
                        methodElement,
                        false,
                        annotationMetadata);
                visitMethodInjectionPointInternal(methodVisitData, injectMethodVisitor, injectInstanceLocalVarIndex);
                methodInjectionPoints.add(methodVisitData);
                allMethodVisits.add(methodVisitData);
                currentMethodIndex++;
            }

            if (falseCondition != null) {
                injectMethodVisitor.visitLabel(falseCondition);
            }
        } else {
            final MethodVisitData methodVisitData = new MethodVisitData(
                    declaringType,
                    methodElement,
                    false,
                    annotationMetadata);
            methodInjectionPoints.add(methodVisitData);
            allMethodVisits.add(methodVisitData);
            currentMethodIndex++;
        }
    }

    @Override
    public void visitPostConstructMethod(TypedElement declaringType,
                                         MethodElement methodElement,
                                         boolean requiresReflection, VisitorContext visitorContext) {

        visitPostConstructMethodDefinition(false);
        // for "super bean definitions" we just delegate to super
        if (!superBeanDefinition || isInterceptedLifeCycleByType(this.annotationMetadata, "POST_CONSTRUCT")) {
            MethodVisitData methodVisitData = new MethodVisitData(declaringType, methodElement, requiresReflection, methodElement.getAnnotationMetadata(), true, false);
            postConstructMethodVisits.add(methodVisitData);
            allMethodVisits.add(methodVisitData);
            visitMethodInjectionPointInternal(methodVisitData, postConstructMethodVisitor, postConstructInstanceLocalVarIndex);
            currentMethodIndex++;
        }
    }

    @Override
    public void visitPreDestroyMethod(TypedElement declaringType,
                                      MethodElement methodElement,
                                      boolean requiresReflection,
                                      VisitorContext visitorContext) {
        // for "super bean definitions" we just delegate to super
        if (!superBeanDefinition || isInterceptedLifeCycleByType(this.annotationMetadata, "PRE_DESTROY")) {
            visitPreDestroyMethodDefinition(false);

            MethodVisitData methodVisitData = new MethodVisitData(declaringType, methodElement, requiresReflection, methodElement.getAnnotationMetadata(), false, true);
            preDestroyMethodVisits.add(methodVisitData);
            allMethodVisits.add(methodVisitData);
            visitMethodInjectionPointInternal(methodVisitData, preDestroyMethodVisitor, preDestroyInstanceLocalVarIndex);
            currentMethodIndex++;
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
        visitMethodInjectionPointInternal(methodVisitData, injectMethodVisitor, injectInstanceLocalVarIndex);
        currentMethodIndex++;
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
                visitorContext,
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
        return this.annotationMetadata;
    }

    @Override
    public void visitConfigBuilderField(
            ClassElement type,
            String field,
            AnnotationMetadata annotationMetadata,
            ConfigurationMetadataBuilder metadataBuilder,
            boolean isInterface) {
        String factoryMethod = annotationMetadata
                .getValue(
                        ConfigurationBuilder.class,
                        "factoryMethod",
                        String.class)
                .orElse(null);

        if (StringUtils.isNotEmpty(factoryMethod)) {
            Type builderType = JavaModelUtils.getTypeReference(type);

            injectMethodVisitor.loadLocal(injectInstanceLocalVarIndex, beanType);
            injectMethodVisitor.invokeStatic(
                    builderType,
                    org.objectweb.asm.commons.Method.getMethod(
                            builderType.getClassName() + " " + factoryMethod + "()"
                    )
            );

            injectMethodVisitor.putField(beanType, field, builderType);
        }

        this.currentConfigBuilderState = new ConfigBuilderState(
                type,
                field,
                false,
                annotationMetadata,
                isInterface);
    }

    @Override
    public void visitConfigBuilderMethod(
            ClassElement type,
            String methodName,
            AnnotationMetadata annotationMetadata,
            ConfigurationMetadataBuilder metadataBuilder,
            boolean isInterface) {

        String factoryMethod = annotationMetadata
                .getValue(
                        ConfigurationBuilder.class,
                        "factoryMethod",
                        String.class)
                .orElse(null);

        if (StringUtils.isNotEmpty(factoryMethod)) {
            Type builderType = JavaModelUtils.getTypeReference(type);

            injectMethodVisitor.loadLocal(injectInstanceLocalVarIndex, beanType);
            injectMethodVisitor.invokeStatic(
                    builderType,
                    org.objectweb.asm.commons.Method.getMethod(
                            builderType.getClassName() + " " + factoryMethod + "()"
                    )
            );

            String propertyName = NameUtils.getPropertyNameForGetter(methodName);
            String setterName = NameUtils.setterNameFor(propertyName);

            injectMethodVisitor.invokeVirtual(beanType, org.objectweb.asm.commons.Method.getMethod(
                    "void " + setterName + "(" + builderType.getClassName() + ")"
            ));
        }

        this.currentConfigBuilderState = new ConfigBuilderState(type, methodName, true, annotationMetadata, isInterface);
    }

    @Override
    public void visitConfigBuilderDurationMethod(
            String propertyName,
            ClassElement returnType,
            String methodName,
            String path) {
        visitConfigBuilderMethodInternal(
                propertyName,
                returnType,
                methodName,
                ClassElement.of(Duration.class),
                Collections.emptyMap(),
                true,
                path
        );
    }

    @Override
    public void visitConfigBuilderMethod(
            String propertyName,
            ClassElement returnType,
            String methodName,
            ClassElement paramType,
            Map<String, ClassElement> generics,
            String path) {

        visitConfigBuilderMethodInternal(
                propertyName,
                returnType,
                methodName,
                paramType,
                generics,
                false,
                path
        );
    }

    @Override
    public void visitConfigBuilderEnd() {
        currentConfigBuilderState = null;
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

        visitFieldInjectionPointInternal(
                declaringType,
                fieldElement,
                fieldElement.getAnnotationMetadata(),
                requiresReflection
        );
    }

    private void visitFieldInjectionPointInternal(
            TypedElement declaringType,
            FieldElement fieldElement,
            AnnotationMetadata annotationMetadata,
            boolean requiresReflection) {

        boolean isRequired = fieldElement
            .booleanValue(AnnotationUtil.INJECT, Autowired.MEMBER_REQUIRED)
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
        visitFieldInjectionPointInternal(
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
                            .named(name -> annotationMemberProperty.equals(NameUtils.getPropertyNameForGetter(name, readPrefixes)))
                            .filter((e) -> !e.hasParameters())
            ).orElse(null);
        }

        if (memberPropertyGetter == null) {
            visitorContext.fail("Bean property [" + annotationMemberProperty + "] is not available on bean ["
                    + annotationMemberBeanType.getName() + "]", annotationMemberBeanType);
        } else {
            Type injectedType = JavaModelUtils.getTypeReference(annotationMemberClassElement);
            annotationInjectionPoints.computeIfAbsent(injectedType, type -> new ArrayList<>(2))
                    .add(new AnnotationVisitData(annotationMemberBeanType, annotationMemberProperty, memberPropertyGetter, requiredValue, notEqualsValue));
        }
    }

    @Override
    public void visitFieldValue(TypedElement declaringType,
                                FieldElement fieldElement,
                                boolean requiresReflection,
                                boolean isOptional) {
        AnnotationMetadata annotationMetadata = fieldElement.getAnnotationMetadata();
        Label falseCondition = isOptional ? pushPropertyContainsCheck(injectMethodVisitor, fieldElement.getType(), fieldElement.getName(), annotationMetadata) : null;

        if (isInnerType(fieldElement.getGenericType())) {
            visitFieldInjectionPointInternal(declaringType, fieldElement, annotationMetadata, requiresReflection);
        } else if (!isConfigurationProperties || requiresReflection) {
            boolean isRequired = fieldElement
                .booleanValue(AnnotationUtil.INJECT, Autowired.MEMBER_REQUIRED)
                .orElse(true);
            visitFieldInjectionPointInternal(
                    declaringType,
                    fieldElement,
                    annotationMetadata,
                    requiresReflection,
                    GET_VALUE_FOR_FIELD,
                    isOptional,
                    false,
                    isRequired
            );
        } else {
            injectMethodVisitor.loadLocal(injectInstanceLocalVarIndex, beanType);

            Optional<String> property = annotationMetadata.stringValue(Property.class, "name");
            if (property.isPresent()) {
                pushInvokeGetPropertyValueForField(injectMethodVisitor, fieldElement, annotationMetadata, property.get());
            } else {
                Optional<String> valueValue = annotationMetadata.stringValue(Value.class);
                if (valueValue.isPresent()) {
                    pushInvokeGetPropertyPlaceholderValueForField(injectMethodVisitor, fieldElement, annotationMetadata, valueValue.get());
                }
            }
            putField(injectMethodVisitor, fieldElement, requiresReflection, declaringType);

            if (keepConfPropInjectPoints) {
                fieldInjectionPoints.add(new FieldVisitData(declaringType, fieldElement, annotationMetadata, requiresReflection));
                currentFieldIndex++;
            }

        }

        if (falseCondition != null) {
            injectMethodVisitor.visitLabel(falseCondition);
        }
    }

    private void pushInvokeGetPropertyValueForField(GeneratorAdapter injectMethodVisitor, FieldElement fieldElement, AnnotationMetadata annotationMetadata, String value) {
        // load 'this'
        injectMethodVisitor.loadThis();
        // 1st argument load BeanResolutionContext
        injectMethodVisitor.loadArg(0);
        // 2nd argument load BeanContext
        injectMethodVisitor.loadArg(1);
        // 3rd argument the method index

        annotationMetadata = MutableAnnotationMetadata.of(annotationMetadata);
        removeAnnotations(annotationMetadata, PropertySource.class.getName(), Property.class.getName());

        if (keepConfPropInjectPoints) {
            resolveFieldArgument(injectMethodVisitor, currentFieldIndex);
        } else {
            pushCreateArgument(
                    this.annotationMetadata,
                    beanFullClassName,
                    beanDefinitionType,
                    classWriter,
                    injectMethodVisitor,
                    fieldElement.getName(),
                    fieldElement.getGenericType(),
                    annotationMetadata,
                    fieldElement.getGenericType().getTypeArguments(),
                    new HashMap<>(),
                    loadTypeMethods
            );
        }
        // 4th property value
        injectMethodVisitor.push(value);
        // 5th cli property name
        injectMethodVisitor.push(getCliPrefix(fieldElement.getName()));

        pushInvokeMethodOnSuperClass(injectMethodVisitor, GET_PROPERTY_VALUE_FOR_FIELD);
        // cast the return value to the correct type
        pushCastToType(injectMethodVisitor, fieldElement.getType());
    }

    private void pushInvokeGetPropertyPlaceholderValueForField(GeneratorAdapter injectMethodVisitor, FieldElement fieldElement, AnnotationMetadata annotationMetadata, String value) {
        // load 'this'
        injectMethodVisitor.loadThis();
        // 1st argument load BeanResolutionContext
        injectMethodVisitor.loadArg(0);
        // 2nd argument load BeanContext
        injectMethodVisitor.loadArg(1);
        // 3rd argument the method index

        annotationMetadata = MutableAnnotationMetadata.of(annotationMetadata);
        removeAnnotations(annotationMetadata, PropertySource.class.getName(), Property.class.getName());

        if (keepConfPropInjectPoints) {
            resolveFieldArgument(injectMethodVisitor, currentFieldIndex);
        } else {
            pushCreateArgument(
                    this.annotationMetadata,
                    beanFullClassName,
                    beanDefinitionType,
                    classWriter,
                    injectMethodVisitor,
                    fieldElement.getName(),
                    fieldElement.getGenericType(),
                    annotationMetadata,
                    fieldElement.getGenericType().getTypeArguments(),
                    new HashMap<>(),
                    loadTypeMethods
            );
        }
        // 4th property value
        injectMethodVisitor.push(value);

        pushInvokeMethodOnSuperClass(injectMethodVisitor, GET_PROPERTY_PLACEHOLDER_VALUE_FOR_FIELD);
        // cast the return value to the correct type
        pushCastToType(injectMethodVisitor, fieldElement.getType());
    }

    private void visitConfigBuilderMethodInternal(
            String propertyName,
            ClassElement returnType,
            String methodName,
            ClassElement paramType,
            Map<String, ClassElement> generics,
            boolean isDurationWithTimeUnit,
            String propertyPath) {

        if (currentConfigBuilderState != null) {
            Type builderType = currentConfigBuilderState.getType();
            String builderName = currentConfigBuilderState.getName();
            boolean isResolveBuilderViaMethodCall = currentConfigBuilderState.isMethod();
            GeneratorAdapter injectMethodVisitor = this.injectMethodVisitor;

            boolean zeroArgs = paramType == null;

            // Optional optional = AbstractBeanDefinition.getValueForPath(...)
            int optionalLocalIndex = pushGetValueForPathCall(injectMethodVisitor, paramType, propertyName, propertyPath, zeroArgs, generics);

            Label ifEnd = new Label();
            // if(optional.isPresent())
            injectMethodVisitor.invokeVirtual(Type.getType(Optional.class), org.objectweb.asm.commons.Method.getMethod(
                    ReflectionUtils.getRequiredMethod(Optional.class, "isPresent")
            ));
            injectMethodVisitor.push(false);
            injectMethodVisitor.ifCmp(Type.BOOLEAN_TYPE, GeneratorAdapter.EQ, ifEnd);
            if (zeroArgs) {
                pushOptionalGet(injectMethodVisitor, optionalLocalIndex);
                pushCastToType(injectMethodVisitor, boolean.class);
                injectMethodVisitor.push(false);
                injectMethodVisitor.ifCmp(Type.BOOLEAN_TYPE, GeneratorAdapter.EQ, ifEnd);
            }

            injectMethodVisitor.visitLabel(new Label());

            String methodDescriptor;
            if (zeroArgs) {
                methodDescriptor = getMethodDescriptor(returnType, Collections.emptyList());
            } else if (isDurationWithTimeUnit) {
                methodDescriptor = getMethodDescriptor(returnType, Arrays.asList(ClassElement.of(long.class), ClassElement.of(TimeUnit.class)));
            } else {
                methodDescriptor = getMethodDescriptor(returnType, Collections.singleton(paramType));
            }

            Label tryStart = new Label();
            Label tryEnd = new Label();
            Label exceptionHandler = new Label();
            injectMethodVisitor.visitTryCatchBlock(tryStart, tryEnd, exceptionHandler, Type.getInternalName(NoSuchMethodError.class));

            injectMethodVisitor.visitLabel(tryStart);

            injectMethodVisitor.loadLocal(injectInstanceLocalVarIndex);
            if (isResolveBuilderViaMethodCall) {
                String desc = builderType.getClassName() + " " + builderName + "()";
                injectMethodVisitor.invokeVirtual(beanType, org.objectweb.asm.commons.Method.getMethod(desc));
            } else {
                injectMethodVisitor.getField(beanType, builderName, builderType);
            }

            if (!zeroArgs) {
                pushOptionalGet(injectMethodVisitor, optionalLocalIndex);
                pushCastToType(injectMethodVisitor, paramType);
            }

            boolean anInterface = currentConfigBuilderState.isInterface();

            if (isDurationWithTimeUnit) {
                injectMethodVisitor.invokeVirtual(Type.getType(Duration.class), org.objectweb.asm.commons.Method.getMethod(
                        ReflectionUtils.getRequiredMethod(Duration.class, "toMillis")
                ));
                Type tu = Type.getType(TimeUnit.class);
                injectMethodVisitor.getStatic(tu, "MILLISECONDS", tu);
            }

            if (anInterface) {
                injectMethodVisitor.invokeInterface(builderType,
                        new org.objectweb.asm.commons.Method(methodName, methodDescriptor));
            } else {
                injectMethodVisitor.invokeVirtual(builderType,
                        new org.objectweb.asm.commons.Method(methodName, methodDescriptor));
            }

            if (!returnType.isVoid()) {
                injectMethodVisitor.pop();
            }
            injectMethodVisitor.visitJumpInsn(GOTO, tryEnd);
            injectMethodVisitor.visitLabel(exceptionHandler);
            injectMethodVisitor.pop();
            injectMethodVisitor.visitLabel(tryEnd);
            injectMethodVisitor.visitLabel(ifEnd);
        }
    }

    private void pushOptionalGet(GeneratorAdapter injectMethodVisitor, int optionalLocalIndex) {
        injectMethodVisitor.loadLocal(optionalLocalIndex);
        // get the value: optional.get()
        injectMethodVisitor.invokeVirtual(Type.getType(Optional.class), org.objectweb.asm.commons.Method.getMethod(
                ReflectionUtils.getRequiredMethod(Optional.class, "get")
        ));
    }

    private int pushGetValueForPathCall(GeneratorAdapter injectMethodVisitor, ClassElement propertyType, String propertyName, String propertyPath, boolean zeroArgs, Map<String, ClassElement> generics) {
        injectMethodVisitor.loadThis();
        injectMethodVisitor.loadArg(0); // the resolution context
        injectMethodVisitor.loadArg(1); // the bean context
        if (zeroArgs) {
            // if the parameter type is null this is a zero args method that expects a boolean flag
            buildArgument(
                    injectMethodVisitor,
                    propertyName,
                    Type.getType(Boolean.class)
            );
        } else {
            buildArgumentWithGenerics(
                    annotationMetadata,
                    beanDefinitionType,
                    classWriter,
                    injectMethodVisitor,
                    propertyName,
                    JavaModelUtils.getTypeReference(propertyType),
                    propertyType,
                    generics,
                    new HashSet<>(),
                    new HashMap<>(),
                    loadTypeMethods
            );
        }

        injectMethodVisitor.push(propertyPath);
        // Optional optional = AbstractBeanDefinition.getValueForPath(...)
        injectMethodVisitor.invokeVirtual(beanDefinitionType, org.objectweb.asm.commons.Method.getMethod(GET_VALUE_FOR_PATH));
        int optionalInstanceIndex = injectMethodVisitor.newLocal(Type.getType(Optional.class));
        injectMethodVisitor.storeLocal(optionalInstanceIndex);
        injectMethodVisitor.loadLocal(optionalInstanceIndex);
        return optionalInstanceIndex;
    }

    private boolean pushValueBypassingBeanContext(GeneratorAdapter writer, ClassElement type) {
        // Used in instantiate and inject methods
        if (type.isAssignable(BeanResolutionContext.class)) {
            writer.loadArg(INSTANTIATE_METHOD_BEAN_RESOLUTION_CONTEXT_PARAM);
            return true;
        }
        if (type.isAssignable(BeanContext.class)) {
            writer.loadArg(INSTANTIATE_METHOD_BEAN_CONTEXT_PARAM);
            return true;
        }
        if (visitorContext.getClassElement(ConversionService.class).orElseThrow().equals(type)) {
            // We only want to assign to exact `ConversionService` classes not to classes extending `ConversionService`
            writer.loadArg(INSTANTIATE_METHOD_BEAN_CONTEXT_PARAM);
            writer.invokeInterface(TYPE_BEAN_CONTEXT, METHOD_BEAN_CONTEXT_GET_CONVERSION_SERVICE);
            return true;
        }
        if (type.isAssignable(ConfigurationPath.class)) {
            writer.loadArg(INSTANTIATE_METHOD_BEAN_RESOLUTION_CONTEXT_PARAM);
            writer.invokeInterface(Type.getType(BeanResolutionContext.class), org.objectweb.asm.commons.Method.getMethod(
                    ReflectionUtils.getRequiredInternalMethod(BeanResolutionContext.class, "getConfigurationPath")
            ));
            return true;
        }
        return false;
    }

    private void visitFieldInjectionPointInternal(
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

        GeneratorAdapter injectMethodVisitor = this.injectMethodVisitor;

        if (isRequired) {
            injectMethodVisitor.loadLocal(injectInstanceLocalVarIndex, beanType);
        }

        if (!pushValueBypassingBeanContext(injectMethodVisitor, fieldElement.getGenericField())) {
            // first get the value of the field by calling AbstractBeanDefinition.getBeanForField(..)
            // load 'this'
            injectMethodVisitor.loadThis();
            // 1st argument load BeanResolutionContext
            injectMethodVisitor.loadArg(INJECT_METHOD_BEAN_RESOLUTION_CONTEXT_PARAM);
            // 2nd argument load BeanContext
            injectMethodVisitor.loadArg(INJECT_METHOD_BEAN_CONTEXT_PARAM);
            // 3rd argument the field index
            injectMethodVisitor.push(currentFieldIndex);
            if (requiresGenericType) {
                resolveFieldArgumentGenericType(injectMethodVisitor, fieldElement.getGenericType(), currentFieldIndex);
            }
            // push qualifier
            pushQualifier(injectMethodVisitor, fieldElement, () -> resolveFieldArgument(injectMethodVisitor, currentFieldIndex));
            // invoke getBeanForField
            pushInvokeMethodOnSuperClass(injectMethodVisitor, methodToInvoke);
            if (isArray && requiresGenericType) {
                convertToArray(fieldElement.getType().fromArray(), injectMethodVisitor);
            }
            // cast the return value to the correct type
            pushCastToType(injectMethodVisitor, fieldElement.getType());
        }

        Label falseCondition = null;
        Type fieldType = JavaModelUtils.getTypeReference(fieldElement.getType());
        if (!isRequired) {
            int i = injectMethodVisitor.newLocal(fieldType);
            injectMethodVisitor.storeLocal(i, fieldType);
            injectMethodVisitor.loadLocal(i, fieldType);
            falseCondition = injectMethodVisitor.newLabel();
            injectMethodVisitor.ifNull(falseCondition);
            injectMethodVisitor.loadLocal(injectInstanceLocalVarIndex, beanType);
            injectMethodVisitor.loadLocal(i, fieldType);
        }
        putField(
            injectMethodVisitor,
            fieldElement,
            requiresReflection,
            declaringType
        );
        if (falseCondition != null) {
            injectMethodVisitor.visitLabel(falseCondition);
        }
        currentFieldIndex++;
        fieldInjectionPoints.add(new FieldVisitData(declaringType, fieldElement, annotationMetadata, requiresReflection));
    }

    private void putField(GeneratorAdapter injectMethodVisitor, FieldElement fieldElement, boolean requiresReflection, TypedElement declaringType) {
        Type declaringTypeRef = JavaModelUtils.getTypeReference(declaringType);
        Type fieldType = JavaModelUtils.getTypeReference(fieldElement.getType());
        if (!requiresReflection) {
            injectMethodVisitor.putField(declaringTypeRef, fieldElement.getName(), fieldType);
        } else {
            pushBoxPrimitiveIfNecessary(fieldType, injectMethodVisitor);
            int storedIndex = injectMethodVisitor.newLocal(Type.getType(Object.class));
            injectMethodVisitor.storeLocal(storedIndex);
            injectMethodVisitor.loadThis();
            injectMethodVisitor.loadArg(0);
            injectMethodVisitor.loadArg(1);
            injectMethodVisitor.push(currentFieldIndex);
            injectMethodVisitor.loadLocal(injectInstanceLocalVarIndex);
            injectMethodVisitor.loadLocal(storedIndex);
            injectMethodVisitor.invokeVirtual(superType, SET_FIELD_WITH_REFLECTION_METHOD);
            injectMethodVisitor.pop();
        }
    }

    private Label pushPropertyContainsCheck(GeneratorAdapter injectMethodVisitor, ClassElement propertyType, String propertyName, AnnotationMetadata annotationMetadata) {
        String propertyValue = annotationMetadata.stringValue(Property.class, "name").orElse(propertyName);

        Label trueCondition = new Label();
        Label falseCondition = new Label();
        injectMethodVisitor.loadThis();
        // 1st argument load BeanResolutionContext
        injectMethodVisitor.loadArg(0);
        // 2nd argument load BeanContext
        injectMethodVisitor.loadArg(1);
        // 3rd argument push property name
        injectMethodVisitor.push(propertyValue);
        if (isMultiValueProperty(propertyType)) {
            injectMethodVisitor.invokeVirtual(beanDefinitionType, CONTAINS_PROPERTIES_VALUE_METHOD);
        } else {
            injectMethodVisitor.invokeVirtual(beanDefinitionType, CONTAINS_PROPERTY_VALUE_METHOD);
        }
        injectMethodVisitor.push(false);

        String cliProperty = getCliPrefix(propertyName);
        if (cliProperty != null) {
            injectMethodVisitor.ifCmp(Type.BOOLEAN_TYPE, GeneratorAdapter.NE, trueCondition);

            injectMethodVisitor.loadThis();
            // 1st argument load BeanResolutionContext
            injectMethodVisitor.loadArg(0);
            // 2nd argument load BeanContext
            injectMethodVisitor.loadArg(1);
            // 3rd argument push property name
            injectMethodVisitor.push(cliProperty);
            injectMethodVisitor.invokeVirtual(beanDefinitionType, CONTAINS_PROPERTY_VALUE_METHOD);
            injectMethodVisitor.push(false);
        }

        injectMethodVisitor.ifCmp(Type.BOOLEAN_TYPE, GeneratorAdapter.EQ, falseCondition);

        injectMethodVisitor.visitLabel(trueCondition);

        return falseCondition;
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

    private void pushQualifier(GeneratorAdapter generatorAdapter, Element element, Runnable resolveArgument) {
        final List<String> qualifierNames = element.getAnnotationNamesByStereotype(AnnotationUtil.QUALIFIER);
        if (!qualifierNames.isEmpty()) {
            if (qualifierNames.size() == 1) {
                // simple qualifier
                final String annotationName = qualifierNames.iterator().next();
                pushQualifierForAnnotation(generatorAdapter, element, annotationName, resolveArgument);
            } else {
                // composite qualifier
                pushNewArray(generatorAdapter, Qualifier.class, qualifierNames,
                    name -> pushQualifierForAnnotation(generatorAdapter, element, name, resolveArgument));
                generatorAdapter.invokeStatic(TYPE_QUALIFIERS, METHOD_QUALIFIER_BY_QUALIFIERS);
            }
        } else if (element.hasAnnotation(AnnotationUtil.ANN_INTERCEPTOR_BINDING_QUALIFIER)) {
            resolveArgument.run();
            retrieveAnnotationMetadataFromProvider(generatorAdapter);
            generatorAdapter.invokeStatic(TYPE_QUALIFIERS, METHOD_QUALIFIER_BY_INTERCEPTOR_BINDING);
        } else {
            String[] byType = element.hasDeclaredAnnotation(io.micronaut.context.annotation.Type.NAME) ? element.stringValues(io.micronaut.context.annotation.Type.NAME) : null;
            if (byType != null && byType.length > 0) {
                pushArrayOfClasses(generatorAdapter, byType);
                generatorAdapter.invokeStatic(TYPE_QUALIFIERS, METHOD_QUALIFIER_BY_TYPE);
            } else {
                generatorAdapter.push((String) null);
            }
        }
    }

    private void retrieveAnnotationMetadataFromProvider(GeneratorAdapter generatorAdapter) {
        generatorAdapter.invokeInterface(Type.getType(AnnotationMetadataProvider.class), org.objectweb.asm.commons.Method.getMethod(
                ReflectionUtils.getRequiredMethod(AnnotationMetadataProvider.class, "getAnnotationMetadata")
        ));
    }

    private void pushQualifierForAnnotation(GeneratorAdapter generatorAdapter,
                                            Element element,
                                            String annotationName,
                                            Runnable resolveArgument) {
        if (annotationName.equals(Primary.NAME)) {
            // primary is the same as no qualifier
            generatorAdapter.visitInsn(ACONST_NULL);
        } else if (annotationName.equals(AnnotationUtil.NAMED)) {
            final String n = element.stringValue(AnnotationUtil.NAMED)
                    .orElse(element.getName());
            if (!n.contains("$")) {
                generatorAdapter.push(n);
                generatorAdapter.invokeStatic(TYPE_QUALIFIERS, METHOD_QUALIFIER_BY_NAME);
            } else {
                // need to resolve the name at runtime
                doResolveArgument(generatorAdapter, resolveArgument);
            }
        } else if (annotationName.equals(Any.NAME)) {
            final Type t = Type.getType(AnyQualifier.class);
            generatorAdapter.getStatic(
                    t,
                    "INSTANCE",
                    t
            );
        } else {
            final String repeatableContainerName = element.findRepeatableAnnotation(annotationName).orElse(null);
            resolveArgument.run();
            retrieveAnnotationMetadataFromProvider(generatorAdapter);
            if (repeatableContainerName != null) {
                generatorAdapter.push(repeatableContainerName);
                generatorAdapter.invokeStatic(TYPE_QUALIFIERS, METHOD_QUALIFIER_BY_REPEATABLE_ANNOTATION);
            } else {
                generatorAdapter.push(annotationName);
                generatorAdapter.invokeStatic(TYPE_QUALIFIERS, METHOD_QUALIFIER_BY_ANNOTATION);
            }
        }
    }

    private void doResolveArgument(GeneratorAdapter generatorAdapter, Runnable resolveArgument) {
        resolveArgument.run();
        generatorAdapter.invokeStatic(TYPE_QUALIFIERS, METHOD_QUALIFIER_FOR_ARGUMENT);
    }

    private void pushArrayOfClasses(GeneratorAdapter writer, String[] byType) {
        pushNewArray(writer, Class.class, byType, type -> pushClass(writer, type));
    }

    private void pushClass(GeneratorAdapter writer, String className) {
        writer.push(Type.getObjectType(className.replace('.', '/')));
    }

    private void convertToArray(ClassElement arrayType, GeneratorAdapter injectMethodVisitor) {
        injectMethodVisitor.push(0);
        injectMethodVisitor.newArray(JavaModelUtils.getTypeReference(arrayType));
        injectMethodVisitor.invokeInterface(Type.getType(Collection.class), COLLECTION_TO_ARRAY);
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

    private void visitMethodInjectionPointInternal(MethodVisitData methodVisitData,
                                                   GeneratorAdapter injectMethodVisitor,
                                                   int injectInstanceIndex) {


        MethodElement methodElement = methodVisitData.getMethodElement();
        @NonNull ParameterElement[] methodParameters = methodElement.getParameters();
        final List<ParameterElement> argumentTypes = Arrays.asList(methodParameters);
        applyDefaultNamedToParameters(argumentTypes);
        final TypedElement declaringType = methodVisitData.beanType;
        final String methodName = methodElement.getName();
        final boolean requiresReflection = methodVisitData.requiresReflection;
        final ClassElement returnType = methodElement.getReturnType();
        boolean hasArguments = methodElement.hasParameters();
        int argCount = hasArguments ? argumentTypes.size() : 0;
        Type declaringTypeRef = JavaModelUtils.getTypeReference(declaringType);
        boolean hasInjectScope = false;
        for (ParameterElement value : argumentTypes) {
            evaluatedExpressionProcessor.processEvaluatedExpressions(value.getAnnotationMetadata(), null);
            if (value.hasDeclaredAnnotation(InjectScope.class)) {
                hasInjectScope = true;
            }
        }

        boolean isRequiredInjection = InjectionPoint.isInjectionRequired(methodElement);
        if (!isRequiredInjection && hasArguments) {
            // store parameter values in local object[]
            final int parametersIndex = createParameterArray(argumentTypes, injectMethodVisitor, (index, parameter) ->
                pushMethodParameterValue(injectMethodVisitor, index, parameter)
            );

            // invoke isMethodResolved with method paremeters
            injectMethodVisitor.loadThis();
            injectMethodVisitor.push(currentMethodIndex);
            injectMethodVisitor.loadLocal(parametersIndex, Type.getType(Object[].class));
            injectMethodVisitor.invokeVirtual(superType, IS_METHOD_RESOLVED);
            injectMethodVisitor.push(false);

            // check method resolved
            Label falseCondition = injectMethodVisitor.newLabel();
            injectMethodVisitor.ifCmp(Type.BOOLEAN_TYPE, GeneratorAdapter.EQ, falseCondition);
            String methodDescriptor = getMethodDescriptor(returnType, argumentTypes);
            injectMethodVisitor.loadLocal(injectInstanceIndex, beanType);

            // load parameters from Object[]
            for (int i = 0; i < methodParameters.length; i++) {
                ParameterElement methodParameter = methodParameters[i];
                injectMethodVisitor.loadLocal(parametersIndex);
                injectMethodVisitor.push(i);
                Type t = getTypeReference(methodParameter.getType());
                injectMethodVisitor.arrayLoad(t);
                pushCastToType(injectMethodVisitor, t);
            }
            // invoke the bean method
            invokeBeanMethodDirectly(injectMethodVisitor, declaringTypeRef, methodName, methodDescriptor, returnType);
            injectMethodVisitor.visitLabel(falseCondition);
        } else if (!requiresReflection) {
            // if the method doesn't require reflection then invoke it directly

            // invoke the method on this injected instance
            injectMethodVisitor.loadLocal(injectInstanceIndex, beanType);

            String methodDescriptor;
            if (hasArguments) {
                methodDescriptor = getMethodDescriptor(returnType, argumentTypes);
                Iterator<ParameterElement> argIterator = argumentTypes.iterator();
                for (int i = 0; i < argCount; i++) {
                    ParameterElement entry = argIterator.next();
                    pushMethodParameterValue(injectMethodVisitor, i, entry);
                }
            } else {
                methodDescriptor = getMethodDescriptor(returnType, Collections.emptyList());
            }
            invokeBeanMethodDirectly(injectMethodVisitor, declaringTypeRef, methodName, methodDescriptor, returnType);
        } else {
            injectMethodVisitor.loadThis();
            injectMethodVisitor.loadArg(INJECT_METHOD_BEAN_RESOLUTION_CONTEXT_PARAM);
            injectMethodVisitor.loadArg(INJECT_METHOD_BEAN_CONTEXT_PARAM);
            injectMethodVisitor.push(currentMethodIndex);
            injectMethodVisitor.loadLocal(injectInstanceLocalVarIndex, beanType);
            newArrayOfMethodParameters(injectMethodVisitor, argumentTypes);
            injectMethodVisitor.invokeVirtual(superType, INVOKE_WITH_REFLECTION_METHOD);
        }

        destroyInjectScopeBeansIfNecessary(injectMethodVisitor, hasInjectScope);
    }

    private void invokeBeanMethodDirectly(GeneratorAdapter injectMethodVisitor, Type declaringTypeRef, String methodName, String methodDescriptor, ClassElement returnType) {
        injectMethodVisitor.visitMethodInsn(isInterface ? INVOKEINTERFACE : INVOKEVIRTUAL,
                declaringTypeRef.getInternalName(), methodName,
            methodDescriptor, isInterface);
        if (isConfigurationProperties && !returnType.isVoid()) {
            injectMethodVisitor.pop();
        }
    }

    private void newArrayOfMethodParameters(GeneratorAdapter injectMethodVisitor, List<ParameterElement> argumentTypes) {
        pushNewArrayIndexed(injectMethodVisitor, Object.class, argumentTypes, (index, entry) -> {
            pushMethodParameterValue(injectMethodVisitor, index, entry);
            pushBoxPrimitiveIfNecessary(entry.getType(), injectMethodVisitor);
        });
    }

    private void destroyInjectScopeBeansIfNecessary(GeneratorAdapter injectMethodVisitor, boolean hasInjectScope) {
        if (hasInjectScope) {
            injectMethodVisitor.loadArg(0);
            injectMethodVisitor.invokeInterface(
                    Type.getType(BeanResolutionContext.class),
                    org.objectweb.asm.commons.Method.getMethod(
                            ReflectionUtils.getRequiredInternalMethod(BeanResolutionContext.class, "destroyInjectScopedBeans")
                    )
            );
        }
    }

    private void pushMethodParameterValue(GeneratorAdapter injectMethodVisitor, int i, ParameterElement entry) {
        AnnotationMetadata argMetadata = entry.getAnnotationMetadata();
        if (!pushValueBypassingBeanContext(injectMethodVisitor, entry.getGenericType())) {
            boolean requiresGenericType = false;
            final ClassElement genericType = entry.getGenericType();
            Method methodToInvoke;
            boolean isCollection = genericType.isAssignable(Collection.class);
            boolean isMap = isInjectableMap(genericType);
            boolean isArray = genericType.isArray();

            if (isValueType(argMetadata) && !isInnerType(entry.getGenericType())) {
                Optional<String> property = argMetadata.stringValue(Property.class, "name");
                if (property.isPresent()) {
                    pushInvokeGetPropertyValueForMethod(injectMethodVisitor, i, entry, property.get());
                } else {
                    if (entry.getAnnotationMetadata().getValue(Value.class, EvaluatedExpressionReference.class).isPresent()) {
                        pushInvokeGetEvaluatedExpressionValueForMethodArgument(injectMethodVisitor, i, entry);
                    } else {
                        Optional<String> valueValue = entry.getAnnotationMetadata().stringValue(Value.class);
                        valueValue.ifPresent(s -> pushInvokeGetPropertyPlaceholderValueForMethod(injectMethodVisitor, i, entry, s));
                    }
                }
                return;
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

            // first get the value of the field by calling AbstractBeanDefinition.getBeanForMethod(..)
            // load 'this'
            injectMethodVisitor.loadThis();
            // 1st argument load BeanResolutionContext
            injectMethodVisitor.loadArg(0);
            // 2nd argument load BeanContext
            injectMethodVisitor.loadArg(1);
            // 3rd argument the method index
            injectMethodVisitor.push(currentMethodIndex);
            // 4th argument the argument index
            injectMethodVisitor.push(i);
            // invoke getBeanForField
            if (requiresGenericType) {
                resolveMethodArgumentGenericType(injectMethodVisitor, genericType, currentMethodIndex, i);
            }
            pushQualifier(injectMethodVisitor, entry, () -> resolveMethodArgument(injectMethodVisitor, currentMethodIndex, i));

            pushInvokeMethodOnSuperClass(injectMethodVisitor, methodToInvoke);
            if (isArray && requiresGenericType) {
                convertToArray(genericType.fromArray(), injectMethodVisitor);
            }
            // cast the return value to the correct type
            pushCastToType(injectMethodVisitor, entry);
        }
    }

    private void pushInvokeGetPropertyValueForMethod(GeneratorAdapter injectMethodVisitor, int i, ParameterElement entry, String value) {
        // load 'this'
        injectMethodVisitor.loadThis();
        // 1st argument load BeanResolutionContext
        injectMethodVisitor.loadArg(0);
        // 2nd argument load BeanContext
        injectMethodVisitor.loadArg(1);
        // 3rd argument the method index
        injectMethodVisitor.push(currentMethodIndex);
        // 4th argument the argument index
        injectMethodVisitor.push(i);
        // 5th property value
        injectMethodVisitor.push(value);
        // 6 cli property name
        injectMethodVisitor.push(getCliPrefix(entry.getName()));

        pushInvokeMethodOnSuperClass(injectMethodVisitor, GET_PROPERTY_VALUE_FOR_METHOD_ARGUMENT);
        // cast the return value to the correct type
        pushCastToType(injectMethodVisitor, entry);
    }

    private void pushInvokeGetEvaluatedExpressionValueForMethodArgument(GeneratorAdapter injectMethodVisitor, int i, ParameterElement entry) {
        // load 'this'
        injectMethodVisitor.loadThis();
        // 1st argument the method index
        injectMethodVisitor.push(currentMethodIndex);
        // 2nd argument the argument index
        injectMethodVisitor.push(i);

        pushInvokeMethodOnSuperClass(injectMethodVisitor, GET_EVALUATED_EXPRESSION_VALUE_FOR_METHOD_ARGUMENT);
        // cast the return value to the correct type
        pushCastToType(injectMethodVisitor, entry);
    }

    private void pushInvokeGetPropertyPlaceholderValueForMethod(GeneratorAdapter injectMethodVisitor, int i, ParameterElement entry, String value) {
        // load 'this'
        injectMethodVisitor.loadThis();
        // 1st argument load BeanResolutionContext
        injectMethodVisitor.loadArg(0);
        // 2nd argument load BeanContext
        injectMethodVisitor.loadArg(1);
        // 3rd argument the method index
        injectMethodVisitor.push(currentMethodIndex);
        // 4th argument the argument index
        injectMethodVisitor.push(i);
        // 5th property value
        injectMethodVisitor.push(value);

        pushInvokeMethodOnSuperClass(injectMethodVisitor, GET_PROPERTY_PLACEHOLDER_VALUE_FOR_METHOD_ARGUMENT);
        // cast the return value to the correct type
        pushCastToType(injectMethodVisitor, entry);
    }

    private void pushInvokeGetPropertyValueForSetter(GeneratorAdapter injectMethodVisitor, String setterName, ParameterElement entry, String value, AnnotationMetadata annotationMetadata) {
        // load 'this'
        injectMethodVisitor.loadThis();
        // 1st argument load BeanResolutionContext
        injectMethodVisitor.loadArg(0);
        // 2nd argument load BeanContext
        injectMethodVisitor.loadArg(1);
        // 3rd argument the method name
        injectMethodVisitor.push(setterName);

        annotationMetadata = MutableAnnotationMetadata.of(annotationMetadata);
        removeAnnotations(annotationMetadata, PropertySource.class.getName(), Property.class.getName());

        // 4th argument the argument
        if (keepConfPropInjectPoints) {
            resolveMethodArgument(injectMethodVisitor, currentMethodIndex, 0);
        } else {
            pushCreateArgument(
                    this.annotationMetadata,
                    beanFullClassName,
                    beanDefinitionType,
                    classWriter,
                    injectMethodVisitor,
                    entry.getName(),
                    entry.getGenericType(),
                    annotationMetadata,
                    entry.getGenericType().getTypeArguments(),
                    new HashMap<>(),
                    loadTypeMethods
            );
        }
        // 5th property value
        injectMethodVisitor.push(value);
        // 6 cli property name
        injectMethodVisitor.push(getCliPrefix(entry.getName()));

        pushInvokeMethodOnSuperClass(injectMethodVisitor, GET_PROPERTY_VALUE_FOR_SETTER);
        // cast the return value to the correct type
        pushCastToType(injectMethodVisitor, entry);
    }

    private void pushInvokeGetBeanForSetter(GeneratorAdapter injectMethodVisitor, String setterName, ParameterElement entry, AnnotationMetadata annotationMetadata) {
        // load 'this'
        injectMethodVisitor.loadThis();
        // 1st argument load BeanResolutionContext
        injectMethodVisitor.loadArg(0);
        // 2nd argument load BeanContext
        injectMethodVisitor.loadArg(1);
        // 3rd argument the method name
        injectMethodVisitor.push(setterName);

        annotationMetadata = MutableAnnotationMetadata.of(annotationMetadata);
        removeAnnotations(annotationMetadata, PropertySource.class.getName(), Property.class.getName());

        // 4th argument the argument
        if (keepConfPropInjectPoints) {
            resolveMethodArgument(injectMethodVisitor, currentMethodIndex, 0);
        } else {
            pushCreateArgument(
                    this.annotationMetadata,
                    beanFullClassName,
                    beanDefinitionType,
                    classWriter,
                    injectMethodVisitor,
                    entry.getName(),
                    entry.getGenericType(),
                    annotationMetadata,
                    entry.getGenericType().getTypeArguments(),
                    new HashMap<>(),
                    loadTypeMethods
            );
        }

        // push qualifier
        pushQualifier(injectMethodVisitor, entry.getGenericType(), injectMethodVisitor::dup);

        pushInvokeMethodOnSuperClass(injectMethodVisitor, GET_BEAN_FOR_SETTER);
        // cast the return value to the correct type
        pushCastToType(injectMethodVisitor, entry);
    }

    private void pushInvokeGetBeansOfTypeForSetter(GeneratorAdapter injectMethodVisitor, String setterName, ParameterElement entry, AnnotationMetadata annotationMetadata) {
        // load 'this'
        injectMethodVisitor.loadThis();
        // 1st argument load BeanResolutionContext
        injectMethodVisitor.loadArg(0);
        // 2nd argument load BeanContext
        injectMethodVisitor.loadArg(1);
        // 3rd argument the method name
        injectMethodVisitor.push(setterName);

        annotationMetadata = MutableAnnotationMetadata.of(annotationMetadata);
        removeAnnotations(annotationMetadata, PropertySource.class.getName(), Property.class.getName());

        // 4th argument the argument
        ClassElement genericType = entry.getGenericType();

        if (keepConfPropInjectPoints) {
            resolveMethodArgument(injectMethodVisitor, currentMethodIndex, 0);
        } else {
            pushCreateArgument(
                    this.annotationMetadata,
                    beanFullClassName,
                    beanDefinitionType,
                    classWriter,
                    injectMethodVisitor,
                    entry.getName(),
                    genericType,
                    annotationMetadata,
                    genericType.getTypeArguments(),
                    new HashMap<>(),
                    loadTypeMethods
            );
        }

        int thisArgument = injectMethodVisitor.newLocal(Type.getType(Argument.class));
        injectMethodVisitor.storeLocal(thisArgument);
        injectMethodVisitor.loadLocal(thisArgument);

        if (!resolveArgumentGenericType(injectMethodVisitor, genericType)) {
            injectMethodVisitor.loadLocal(thisArgument);
            resolveFirstTypeArgument(injectMethodVisitor);
            resolveInnerTypeArgumentIfNeeded(injectMethodVisitor, genericType);
        } else {
            injectMethodVisitor.push((String) null);
        }

        // push qualifier
        pushQualifier(injectMethodVisitor, genericType, () -> injectMethodVisitor.loadLocal(thisArgument));

        pushInvokeMethodOnSuperClass(injectMethodVisitor, GET_BEANS_OF_TYPE_FOR_SETTER);
        // cast the return value to the correct type
        pushCastToType(injectMethodVisitor, entry);
    }

    private void pushInvokeGetPropertyPlaceholderValueForSetter(GeneratorAdapter injectMethodVisitor, String setterName, ParameterElement entry, String value, AnnotationMetadata annotationMetadata) {
        // load 'this'
        injectMethodVisitor.loadThis();
        // 1st argument load BeanResolutionContext
        injectMethodVisitor.loadArg(0);
        // 2nd argument load BeanContext
        injectMethodVisitor.loadArg(1);
        // 3rd argument the method name
        injectMethodVisitor.push(setterName);
        // 4th argument the argument

        annotationMetadata = MutableAnnotationMetadata.of(annotationMetadata);
        removeAnnotations(annotationMetadata, PropertySource.class.getName(), Property.class.getName());

        if (keepConfPropInjectPoints) {
            resolveMethodArgument(injectMethodVisitor, currentMethodIndex, 0);
        } else {
            pushCreateArgument(
                    this.annotationMetadata,
                    beanFullClassName,
                    beanDefinitionType,
                    classWriter,
                    injectMethodVisitor,
                    entry.getName(),
                    entry.getGenericType(),
                    annotationMetadata,
                    entry.getGenericType().getTypeArguments(),
                    new HashMap<>(),
                    loadTypeMethods
            );
        }

        // 5th property value
        injectMethodVisitor.push(value);
        // 6 cli property name
        injectMethodVisitor.push(getCliPrefix(entry.getName()));

        pushInvokeMethodOnSuperClass(injectMethodVisitor, GET_PROPERTY_PLACEHOLDER_VALUE_FOR_SETTER);
        // cast the return value to the correct type
        pushCastToType(injectMethodVisitor, entry);
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

    private void pushInvokeMethodOnSuperClass(MethodVisitor constructorVisitor, Method methodToInvoke) {
        constructorVisitor.visitMethodInsn(INVOKESPECIAL,
            getSuperTypeInternalType(),
            methodToInvoke.getName(),
            Type.getMethodDescriptor(methodToInvoke),
            false);
    }

    private void pushInvokeMethodOnSuperClass(MethodVisitor constructorVisitor, org.objectweb.asm.commons.Method methodToInvoke) {
        constructorVisitor.visitMethodInsn(INVOKESPECIAL,
            getSuperTypeInternalType(),
            methodToInvoke.getName(),
            methodToInvoke.getDescriptor(),
            false);
    }

    private void visitCheckIfShouldLoadMethodDefinition() {
        String desc = getMethodDescriptor("void", BeanResolutionContext.class.getName(), BeanContext.class.getName());
        this.checkIfShouldLoadMethodVisitor = new GeneratorAdapter(classWriter.visitMethod(
                ACC_PROTECTED,
                "checkIfShouldLoad",
                desc,
                null,
                null), ACC_PROTECTED, "checkIfShouldLoad", desc);
    }

    @SuppressWarnings("MagicNumber")
    private void visitInjectMethodDefinition() {
        if (!isPrimitiveBean && !superBeanDefinition && injectMethodVisitor == null) {
            injectMethodVisitor = new GeneratorAdapter(classWriter.visitMethod(
                    ACC_PUBLIC,
                    INJECT_BEAN_METHOD.getName(),
                    INJECT_BEAN_METHOD.getDescriptor(),
                    null,
                    null), ACC_PUBLIC, INJECT_BEAN_METHOD.getName(), INJECT_BEAN_METHOD.getDescriptor());

            GeneratorAdapter injectMethodVisitor = this.injectMethodVisitor;
            if (isConfigurationProperties) {
                injectMethodVisitor.loadThis();
                injectMethodVisitor.loadArg(0); // the resolution context
                injectMethodVisitor.loadArg(1); // the bean context
                // invoke AbstractBeanDefinition.containsProperties(..)
                injectMethodVisitor.invokeVirtual(beanDefinitionType, org.objectweb.asm.commons.Method.getMethod(CONTAINS_PROPERTIES_METHOD));
                injectMethodVisitor.push(false);
                injectEnd = new Label();
                injectMethodVisitor.ifCmp(Type.BOOLEAN_TYPE, GeneratorAdapter.EQ, injectEnd);
                // add the true condition
                injectMethodVisitor.visitLabel(new Label());
            }
            // The object being injected is argument 3 of the inject method
            injectMethodVisitor.loadArg(2);
            // store it in a local variable
            pushCastToType(injectMethodVisitor, beanType);
            injectInstanceLocalVarIndex = injectMethodVisitor.newLocal(beanType);
            injectMethodVisitor.storeLocal(injectInstanceLocalVarIndex);
        }
    }

    @SuppressWarnings("MagicNumber")
    private void visitPostConstructMethodDefinition(boolean intercepted) {
        if (!postConstructAdded) {
            // override the post construct method
            final String lifeCycleMethodName = "initialize";

            //  for "super bean definition" we only add code to trigger "initialize"
            if (!superBeanDefinition || intercepted) {
                interfaceTypes.add(InitializingBeanDefinition.class);

                GeneratorAdapter postConstructMethodVisitor = newLifeCycleMethod(lifeCycleMethodName);
                this.postConstructMethodVisitor = postConstructMethodVisitor;
                // The object being injected is argument 3 of the inject method
                postConstructMethodVisitor.loadArg(2);
                // store it in a local variable
                pushCastToType(postConstructMethodVisitor, beanType);
                postConstructInstanceLocalVarIndex = postConstructMethodVisitor.newLocal(beanType);
                postConstructMethodVisitor.storeLocal(postConstructInstanceLocalVarIndex);
                invokeSuperInjectMethod(postConstructMethodVisitor, POST_CONSTRUCT_METHOD);
            }

            if (intercepted) {
                // store executable method in local variable
                writeInterceptedLifecycleMethod(
                        lifeCycleMethodName,
                        lifeCycleMethodName,
                        buildMethodVisitor,
                        buildInstanceLocalVarIndex
                );
            } else {
                pushBeanDefinitionMethodInvocation(buildMethodVisitor, lifeCycleMethodName);
            }
            pushCastToType(buildMethodVisitor, beanType);
            buildMethodVisitor.loadLocal(buildInstanceLocalVarIndex);
            postConstructAdded = true;
        }
    }

    private void writeInterceptedLifecycleMethod(
            String lifeCycleMethodName,
            String dispatchMethodName,
            GeneratorAdapter targetMethodVisitor,
            int instanceLocalIndex) {
        // if there is method interception in place we need to construct an inner executable method class that invokes the "initialize"
        // method and apply interception
        final InnerClassDef postConstructInnerMethod = newInnerClass(AbstractExecutableMethod.class);
        // needs fields to propagate the correct arguments to the initialize method
        final ClassWriter postConstructInnerWriter = postConstructInnerMethod.innerClassWriter;
        final Type postConstructInnerClassType = postConstructInnerMethod.innerClassType;
        final String fieldBeanDef = "$beanDef";
        final String fieldResContext = "$resolutionContext";
        final String fieldBeanContext = "$beanContext";
        final String fieldBean = "$bean";
        newFinalField(postConstructInnerWriter, beanDefinitionType, fieldBeanDef);
        newFinalField(postConstructInnerWriter, TYPE_RESOLUTION_CONTEXT, fieldResContext);
        newFinalField(postConstructInnerWriter, TYPE_BEAN_CONTEXT, fieldBeanContext);
        newFinalField(postConstructInnerWriter, beanType, fieldBean);
        // constructor will be AbstractExecutableMethod(BeanDefinition, BeanResolutionContext, BeanContext, T beanType)
        final String constructorDescriptor = getConstructorDescriptor(new Type[]{
                beanDefinitionType,
                TYPE_RESOLUTION_CONTEXT,
                TYPE_BEAN_CONTEXT,
                beanType
        });
        GeneratorAdapter protectedConstructor = new GeneratorAdapter(
                postConstructInnerWriter.visitMethod(
                        ACC_PROTECTED, CONSTRUCTOR_NAME,
                        constructorDescriptor,
                        null,
                        null
                ),
                ACC_PROTECTED,
                CONSTRUCTOR_NAME,
                constructorDescriptor
        );
        // set field $beanDef
        protectedConstructor.loadThis();
        protectedConstructor.loadArg(0);
        protectedConstructor.putField(postConstructInnerClassType, fieldBeanDef, beanDefinitionType);
        // set field $resolutionContext
        protectedConstructor.loadThis();
        protectedConstructor.loadArg(1);
        protectedConstructor.putField(postConstructInnerClassType, fieldResContext, TYPE_RESOLUTION_CONTEXT);
        // set field $beanContext
        protectedConstructor.loadThis();
        protectedConstructor.loadArg(2);
        protectedConstructor.putField(postConstructInnerClassType, fieldBeanContext, TYPE_BEAN_CONTEXT);
        // set field $bean
        protectedConstructor.loadThis();
        protectedConstructor.loadArg(3);
        protectedConstructor.putField(postConstructInnerClassType, fieldBean, beanType);

        protectedConstructor.loadThis();
        protectedConstructor.push(beanType);
        protectedConstructor.push(lifeCycleMethodName);
        invokeConstructor(
                protectedConstructor,
                AbstractExecutableMethod.class,
                Class.class,
                String.class
        );
        protectedConstructor.returnValue();
        protectedConstructor.visitMaxs(1, 1);
        protectedConstructor.visitEnd();

        // annotation metadata should reference to the metadata for bean definition
        final GeneratorAdapter getAnnotationMetadata = startPublicFinalMethodZeroArgs(postConstructInnerWriter, AnnotationMetadata.class, "getAnnotationMetadata");
        lookupReferenceAnnotationMetadata(getAnnotationMetadata);

        // now define the invokerInternal method
        final GeneratorAdapter invokeMethod = startPublicMethod(postConstructInnerWriter, METHOD_INVOKE_INTERNAL);
        invokeMethod.loadThis();
        // load the bean definition field
        invokeMethod.getField(postConstructInnerClassType, fieldBeanDef, beanDefinitionType);
        // load the arguments to the initialize method
        // 1st argument the resolution context
        invokeMethod.loadThis();
        invokeMethod.getField(postConstructInnerClassType, fieldResContext, TYPE_RESOLUTION_CONTEXT);
        // 2nd argument the bean context
        invokeMethod.loadThis();
        invokeMethod.getField(postConstructInnerClassType, fieldBeanContext, TYPE_BEAN_CONTEXT);
        // 3rd argument the bean
        invokeMethod.loadThis();
        invokeMethod.getField(postConstructInnerClassType, fieldBean, beanType);
        // now invoke initialize
        invokeMethod.visitMethodInsn(INVOKEVIRTUAL,
                beanDefinitionInternalName,
                lifeCycleMethodName,
                METHOD_DESCRIPTOR_INITIALIZE,
                false);
        invokeMethod.returnValue();
        invokeMethod.visitMaxs(1, 1);
        invokeMethod.visitEnd();

        // now instantiate the inner class
        targetMethodVisitor.visitTypeInsn(NEW, postConstructInnerMethod.constructorInternalName);
        targetMethodVisitor.visitInsn(DUP);
        // constructor signature is AbstractExecutableMethod(BeanDefinition, BeanResolutionContext, BeanContext, T beanType)
        // 1st argument: pass outer class instance to constructor
        targetMethodVisitor.loadThis();

        // 2nd argument: resolution context
        targetMethodVisitor.loadArg(0);

        // 3rd argument: bean context
        targetMethodVisitor.loadArg(1);

        // 4th argument: bean instance
        targetMethodVisitor.loadLocal(instanceLocalIndex);
        pushCastToType(targetMethodVisitor, beanType);
        targetMethodVisitor.visitMethodInsn(
                INVOKESPECIAL,
                postConstructInnerMethod.constructorInternalName,
                "<init>",
                constructorDescriptor,
                false
        );
        final int executableInstanceIndex = targetMethodVisitor.newLocal(Type.getType(ExecutableMethod.class));
        targetMethodVisitor.storeLocal(executableInstanceIndex);
        // now invoke MethodInterceptorChain.initialize or dispose
        // 1st argument: resolution context
        targetMethodVisitor.loadArg(0);
        // 2nd argument: bean context
        targetMethodVisitor.loadArg(1);
        // 3rd argument: this definition
        targetMethodVisitor.loadThis();
        // 4th argument: executable method instance
        targetMethodVisitor.loadLocal(executableInstanceIndex);
        // 5th argument: the bean instance
        targetMethodVisitor.loadLocal(instanceLocalIndex);
        pushCastToType(targetMethodVisitor, beanType);
        targetMethodVisitor.visitMethodInsn(
                INVOKESTATIC,
                "io/micronaut/aop/chain/MethodInterceptorChain",
                dispatchMethodName,
                METHOD_DESCRIPTOR_INTERCEPTED_LIFECYCLE,
                false
        );
        targetMethodVisitor.loadLocal(instanceLocalIndex);
    }

    @SuppressWarnings("MagicNumber")
    private void visitPreDestroyMethodDefinition(boolean intercepted) {
        if (preDestroyMethodVisitor == null) {
            interfaceTypes.add(DisposableBeanDefinition.class);

            // override the dispose method
            GeneratorAdapter preDestroyMethodVisitor;
            if (intercepted) {
                preDestroyMethodVisitor = newLifeCycleMethod("doDispose");
                final GeneratorAdapter disposeMethod = newLifeCycleMethod("dispose");
                disposeMethod.loadArg(2);
                int instanceLocalIndex = disposeMethod.newLocal(beanType);
                disposeMethod.storeLocal(instanceLocalIndex);

                writeInterceptedLifecycleMethod(
                        "doDispose",
                        "dispose",
                        disposeMethod,
                        instanceLocalIndex
                );
                disposeMethod.returnValue();


                this.interceptedDisposeMethod = disposeMethod;
            } else {
                preDestroyMethodVisitor = newLifeCycleMethod("dispose");
            }

            this.preDestroyMethodVisitor = preDestroyMethodVisitor;
            // The object being injected is argument 3 of the inject method
            preDestroyMethodVisitor.loadArg(2);
            // store it in a local variable
            pushCastToType(preDestroyMethodVisitor, beanType);
            preDestroyInstanceLocalVarIndex = preDestroyMethodVisitor.newLocal(beanType);
            preDestroyMethodVisitor.storeLocal(preDestroyInstanceLocalVarIndex);

            invokeSuperInjectMethod(preDestroyMethodVisitor, PRE_DESTROY_METHOD);
        }
    }

    private GeneratorAdapter newLifeCycleMethod(String methodName) {
        String desc = getMethodDescriptor(Object.class.getName(), BeanResolutionContext.class.getName(), BeanContext.class.getName(), Object.class.getName());
        return new GeneratorAdapter(classWriter.visitMethod(
                ACC_PUBLIC,
                methodName,
                desc,
                getMethodSignature(getTypeDescriptor(beanFullClassName), getTypeDescriptor(BeanResolutionContext.class.getName()), getTypeDescriptor(BeanContext.class.getName()), getTypeDescriptor(beanFullClassName)),
                null),
                ACC_PUBLIC,
                methodName,
                desc
        );
    }

    @SuppressWarnings("MagicNumber")
    private void invokeSuperInjectMethod(GeneratorAdapter methodVisitor, Method methodToInvoke) {
        // load this
        methodVisitor.loadThis();
        // load BeanResolutionContext arg 1
        methodVisitor.loadArg(0);
        // load BeanContext arg 2
        methodVisitor.loadArg(1);
        // load object being injected arg 3
        methodVisitor.loadArg(2);
        pushInvokeMethodOnSuperClass(methodVisitor, methodToInvoke);
    }

    private void invokeSuperInjectMethod(GeneratorAdapter methodVisitor, org.objectweb.asm.commons.Method methodToInvoke) {
        // load this
        methodVisitor.loadThis();
        // load BeanResolutionContext arg 1
        methodVisitor.loadArg(0);
        // load BeanContext arg 2
        methodVisitor.loadArg(1);
        // load object being injected arg 3
        methodVisitor.loadArg(2);
        pushInvokeMethodOnSuperClass(methodVisitor, methodToInvoke);
    }

    private void visitBuildFactoryMethodDefinition(
            ClassElement factoryClass,
            Element factoryElement, ParameterElement... parameters) {
        if (buildMethodVisitor == null) {
            evaluatedExpressionProcessor.processEvaluatedExpressions(factoryElement.getAnnotationMetadata(), null);
            for (ParameterElement parameterElement: parameters) {
                evaluatedExpressionProcessor.processEvaluatedExpressions(parameterElement.getAnnotationMetadata(), null);
            }

            List<ParameterElement> parameterList = Arrays.asList(parameters);
            boolean isParametrized = isParametrized(parameters);
            boolean isIntercepted = isConstructorIntercepted(factoryElement);
            Type factoryType = JavaModelUtils.getTypeReference(factoryClass);

            defineBuilderMethod(isParametrized);
            // load this

            GeneratorAdapter buildMethodVisitor = this.buildMethodVisitor;

            int factoryVar = -1;
            // Skip initializing a producer instance for static producers
            if (!factoryElement.isStatic()) {
                factoryVar = pushGetFactoryBean(factoryClass, factoryType, buildMethodVisitor);
            }
            String methodDescriptor = getMethodDescriptorForReturnType(beanType, parameterList);
            boolean hasInjectScope = false;
            if (isIntercepted) {
                int constructorIndex = initInterceptedConstructorWriter(
                        buildMethodVisitor,
                        parameterList,
                        new FactoryMethodDef(factoryType, factoryElement, methodDescriptor, factoryVar)
                );
                // populate an Object[] of all constructor arguments
                final int parametersIndex = createConstructorParameterArray(parameterList, buildMethodVisitor);
                invokeConstructorChain(buildMethodVisitor, constructorIndex, parametersIndex, parameterList);
            } else {
                if (factoryElement instanceof MethodElement methodElement) {
                    if (!methodElement.isReflectionRequired() && !parameterList.isEmpty()) {
                        hasInjectScope = pushConstructorArguments(buildMethodVisitor, parameters);
                    }
                    if (methodElement.isReflectionRequired()) {
                        if (methodElement.isStatic()) {
                            buildMethodVisitor.push((String) null);
                        }
                        DispatchWriter.pushTypeUtilsGetRequiredMethod(buildMethodVisitor, factoryType, methodElement);
                        buildMethodVisitor.dup();
                        buildMethodVisitor.push(true);
                        buildMethodVisitor.invokeVirtual(Type.getType(Method.class), org.objectweb.asm.commons.Method.getMethod(
                                ReflectionUtils.getRequiredMethod(Method.class, "setAccessible", boolean.class)
                        ));
                        hasInjectScope = pushParametersAsArray(buildMethodVisitor, parameters);
                        buildMethodVisitor.invokeStatic(TYPE_REFLECTION_UTILS, METHOD_INVOKE_METHOD);
//                        buildMethodVisitor.push((String) null);
                        if (methodElement.isReflectionRequired() && isPrimitiveBean) {
                            // Reflection always returns Object, convert it to appropriate primitive
                            pushCastToType(buildMethodVisitor, beanType);
                        }
                    } else if (methodElement.isStatic()) {
                        buildMethodVisitor.invokeStatic(factoryType, new org.objectweb.asm.commons.Method(factoryElement.getName(), methodDescriptor));
                    } else {
                        buildMethodVisitor.invokeVirtual(factoryType, new org.objectweb.asm.commons.Method(factoryElement.getName(), methodDescriptor));
                    }
                } else {
                    FieldElement fieldElement = (FieldElement) factoryElement;
                    if (fieldElement.isReflectionRequired()) {
                        if (!fieldElement.isStatic()) {
                            buildMethodVisitor.storeLocal(factoryVar);
                        }
                        buildMethodVisitor.push(factoryType);
                        buildMethodVisitor.push(fieldElement.getName());
                        if (fieldElement.isStatic()) {
                            buildMethodVisitor.push((String) null);
                        } else {
                            buildMethodVisitor.loadLocal(factoryVar);
                        }
                        buildMethodVisitor.invokeStatic(TYPE_REFLECTION_UTILS, GET_FIELD_WITH_REFLECTION_METHOD);
                        if (fieldElement.isReflectionRequired() && isPrimitiveBean) {
                            // Reflection always returns Object, convert it to appropriate primitive
                            pushCastToType(buildMethodVisitor, beanType);
                        }
                    } else if (fieldElement.isStatic()) {
                        buildMethodVisitor.getStatic(factoryType, factoryElement.getName(), beanType);
                    } else {
                        buildMethodVisitor.getField(factoryType, factoryElement.getName(), beanType);
                    }
                }
            }

            this.buildInstanceLocalVarIndex = buildMethodVisitor.newLocal(beanType);
            buildMethodVisitor.storeLocal(buildInstanceLocalVarIndex, beanType);
            if (!isPrimitiveBean) {
                pushBeanDefinitionMethodInvocation(buildMethodVisitor, INJECT_BEAN_METHOD.getName());
                pushCastToType(buildMethodVisitor, beanType);
                buildMethodVisitor.storeLocal(buildInstanceLocalVarIndex);
            }
            destroyInjectScopeBeansIfNecessary(buildMethodVisitor, hasInjectScope);
            buildMethodVisitor.loadLocal(buildInstanceLocalVarIndex, beanType);
            initLifeCycleMethodsIfNecessary();
        }
    }

    private int pushGetFactoryBean(ClassElement factoryClass, Type factoryType, GeneratorAdapter buildMethodVisitor) {
        invokeCheckIfShouldLoadIfNecessary(buildMethodVisitor);
        // for Factory beans first we need to look up the factory bean
        // before invoking the method to instantiate
        // the below code looks up the factory bean.

        // Load the BeanContext for the method call
        buildMethodVisitor.loadArg(1);
        pushCastToType(buildMethodVisitor, DefaultBeanContext.class);
        // load the first argument of the method (the BeanResolutionContext) to be passed to the method
        buildMethodVisitor.loadArg(0);
        // second argument is the bean type
        buildMethodVisitor.push(factoryType);
        // third argument is the qualifier for the factory if any
        pushQualifier(buildMethodVisitor, factoryClass, () -> {
            buildMethodVisitor.push(factoryType);
            buildMethodVisitor.push("factory");
            invokeInterfaceStaticMethod(buildMethodVisitor, Argument.class, METHOD_CREATE_ARGUMENT_SIMPLE);
        });
        buildMethodVisitor.invokeVirtual(
                Type.getType(DefaultBeanContext.class),
                org.objectweb.asm.commons.Method.getMethod(METHOD_GET_BEAN)
        );

        int factoryVar = buildMethodVisitor.newLocal(factoryType);
        buildMethodVisitor.storeLocal(factoryVar, factoryType);

        // BeanResolutionContext
        buildMethodVisitor.loadArg(0);
        // .markDependentAsFactory()
        buildMethodVisitor.invokeInterface(TYPE_RESOLUTION_CONTEXT, METHOD_BEAN_RESOLUTION_CONTEXT_MARK_FACTORY);

        buildMethodVisitor.loadLocal(factoryVar);
        pushCastToType(buildMethodVisitor, factoryClass);
        return factoryVar;
    }

    private void visitBuildMethodDefinition(MethodElement constructor, boolean requiresReflection) {
        if (buildMethodVisitor == null) {
            boolean isIntercepted = isConstructorIntercepted(constructor);
            final ParameterElement[] parameterArray = constructor.getParameters();
            List<ParameterElement> parameters = Arrays.asList(parameterArray);
            boolean isParametrized = isParametrized(parameterArray);
            defineBuilderMethod(isParametrized);
            // load this

            GeneratorAdapter buildMethodVisitor = this.buildMethodVisitor;
            invokeCheckIfShouldLoadIfNecessary(buildMethodVisitor);

            // if there is constructor interception present then we have to
            // build the parameters into an Object[] and build a constructor invocation
            if (isIntercepted) {
                final int constructorIndex = initInterceptedConstructorWriter(buildMethodVisitor, parameters, null);
                // populate an Object[] of all constructor arguments
                final int parametersIndex = createConstructorParameterArray(parameters, buildMethodVisitor);
                invokeConstructorChain(buildMethodVisitor, constructorIndex, parametersIndex, parameters);
            } else {
                boolean isKotlin = constructor.getClass().getSimpleName().startsWith("Kotlin");
                if (isKotlin) {
                    Map<Integer, Integer> checksLocals = new HashMap<>();
                    Map<Integer, Integer> valuesLocals = new HashMap<>();
                    WriterUtils.invokeBeanConstructor(buildMethodVisitor, constructor, requiresReflection, true, (index, parameter) -> {
                        Integer checkLocal = checksLocals.get(index);
                        if (checkLocal != null) {
                            buildMethodVisitor.loadLocal(checkLocal);
                            buildMethodVisitor.push(true);
                            Label end = new Label();
                            Label propertyMissingLabel = new Label();
                            buildMethodVisitor.ifCmp(Type.BOOLEAN_TYPE, GeneratorAdapter.NE, propertyMissingLabel);
                            pushConstructorArgument(buildMethodVisitor, parameter, index);
                            buildMethodVisitor.goTo(end);
                            buildMethodVisitor.visitLabel(propertyMissingLabel);
                            WriterUtils.pushDefaultTypeValue(buildMethodVisitor, parameter.getType());
                            buildMethodVisitor.goTo(end);
                            buildMethodVisitor.visitLabel(end);
                            return;
                        }
                        int loadedLocal = valuesLocals.computeIfAbsent(index, integer -> {
                            pushConstructorArgument(buildMethodVisitor, parameter, index);
                            int local = buildMethodVisitor.newLocal(getTypeReference(parameter));
                            buildMethodVisitor.storeLocal(local);
                            return local;
                        });
                        buildMethodVisitor.loadLocal(loadedLocal);
                    }, (index, parameterElement) -> {
                        if (parameterElement.hasAnnotation(Property.class)) {
                            int local = buildMethodVisitor.newLocal(Type.BOOLEAN_TYPE);
                            pushContainsPropertyCheck(buildMethodVisitor, parameterElement);
                            buildMethodVisitor.storeLocal(local);
                            buildMethodVisitor.loadLocal(local);
                            checksLocals.put(index, local);
                            return true;
                        }
                        return false;
                    });
                } else {
                    WriterUtils.invokeBeanConstructor(buildMethodVisitor, constructor, requiresReflection, true, (index, parameter) -> {
                        pushConstructorArgument(buildMethodVisitor, parameter, index);
                    }, null);
                }
            }

            this.buildInstanceLocalVarIndex = buildMethodVisitor.newLocal(beanType);
            buildMethodVisitor.storeLocal(buildInstanceLocalVarIndex);
            pushBeanDefinitionMethodInvocation(buildMethodVisitor, INJECT_BEAN_METHOD.getName());
            pushCastToType(buildMethodVisitor, beanType);
            buildMethodVisitor.storeLocal(buildInstanceLocalVarIndex);
            buildMethodVisitor.loadLocal(buildInstanceLocalVarIndex);
            initLifeCycleMethodsIfNecessary();
            pushBoxPrimitiveIfNecessary(beanType, buildMethodVisitor);
        }
    }

    private void pushContainsPropertyCheck(GeneratorAdapter writer, ParameterElement parameterElement) {
        String propertyValue = parameterElement.stringValue(Property.class, "name").orElseThrow();
        writer.loadThis();
        // 1st argument load BeanResolutionContext
        writer.loadArg(0);
        // 2nd argument load BeanContext
        writer.loadArg(1);
        // 3rd argument push property name
        writer.push(propertyValue);
        if (isMultiValueProperty(parameterElement.getType())) {
            writer.invokeVirtual(beanDefinitionType, CONTAINS_PROPERTIES_VALUE_METHOD);
        } else {
            writer.invokeVirtual(beanDefinitionType, CONTAINS_PROPERTY_VALUE_METHOD);
        }
    }

    private void invokeCheckIfShouldLoadIfNecessary(GeneratorAdapter buildMethodVisitor) {
        AnnotationValue<Requires> requiresAnnotation = annotationMetadata.getAnnotation(Requires.class);
        if (requiresAnnotation != null
                && requiresAnnotation.stringValue(RequiresCondition.MEMBER_BEAN).isPresent()
                && requiresAnnotation.stringValue(RequiresCondition.MEMBER_BEAN_PROPERTY).isPresent()) {
            visitCheckIfShouldLoadMethodDefinition();

            buildMethodVisitor.loadThis();
            buildMethodVisitor.loadArg(0);
            buildMethodVisitor.loadArg(1);
            buildMethodVisitor.invokeVirtual(beanDefinitionType, org.objectweb.asm.commons.Method.getMethod(
                    ReflectionUtils.getRequiredMethod(AbstractInitializableBeanDefinition.class, "checkIfShouldLoad",
                            BeanResolutionContext.class,
                            BeanContext.class)));
        }
    }

    private void initLifeCycleMethodsIfNecessary() {
        if (isInterceptedLifeCycleByType(this.annotationMetadata, "POST_CONSTRUCT")) {
            visitPostConstructMethodDefinition(true);
        }
        if (!superBeanDefinition && isInterceptedLifeCycleByType(this.annotationMetadata, "PRE_DESTROY")) {
            visitPreDestroyMethodDefinition(true);
        }
    }

    private void invokeConstructorChain(GeneratorAdapter generatorAdapter, int constructorLocalIndex, int parametersLocalIndex, List<ParameterElement> parameters) {
        // 1st argument: The resolution context
        generatorAdapter.loadArg(0);
        // 2nd argument: The bean context
        generatorAdapter.loadArg(1);
        // 3rd argument: The interceptors if present
        if (StringUtils.isNotEmpty(interceptedType)) {
            // interceptors will be last entry in parameter list for interceptors types
            generatorAdapter.loadLocal(parametersLocalIndex);
            // array index for last parameter
            generatorAdapter.push(AopProxyWriter.findInterceptorsListParameterIndex(parameters));
            generatorAdapter.arrayLoad(TYPE_OBJECT);
            pushCastToType(generatorAdapter, List.class);
        } else {
            // for non interceptor types we have to perform a lookup based on the binding
            generatorAdapter.visitInsn(ACONST_NULL);
        }
        // 4th argument: the bean definition
        generatorAdapter.loadThis();
        // 5th argument: The constructor
        generatorAdapter.loadLocal(constructorLocalIndex);
        // 6th argument:  additional proxy parameters count
        if (getInterceptedType().isPresent()) {
            generatorAdapter.push(AopProxyWriter.ADDITIONAL_PARAMETERS_COUNT);
        } else {
            generatorAdapter.push(0);
        }
        // 7th argument:  load the Object[] for the parameters
        generatorAdapter.loadLocal(parametersLocalIndex);

        generatorAdapter.visitMethodInsn(
                INVOKESTATIC,
                "io/micronaut/aop/chain/ConstructorInterceptorChain",
            METHOD_NAME_INSTANTIATE,
                METHOD_DESCRIPTOR_CONSTRUCTOR_INSTANTIATE,
                false
        );
    }

    private int initInterceptedConstructorWriter(
            GeneratorAdapter buildMethodVisitor,
            List<ParameterElement> parameters,
            @Nullable FactoryMethodDef factoryMethodDef) {
        // write the constructor that is a subclass of AbstractConstructorInjectionPoint
        InnerClassDef constructorInjectionPointInnerClass = newInnerClass(AbstractBeanDefinitionBeanConstructor.class);
        final ClassWriter interceptedConstructorWriter = constructorInjectionPointInnerClass.innerClassWriter;
        org.objectweb.asm.commons.Method constructorMethod = org.objectweb.asm.commons.Method.getMethod(CONSTRUCTOR_ABSTRACT_CONSTRUCTOR_IP);
        GeneratorAdapter protectedConstructor;

        final boolean hasFactoryMethod = factoryMethodDef != null;
        final String interceptedConstructorDescriptor;
        final Type factoryType = hasFactoryMethod ? factoryMethodDef.factoryType : null;
        final String factoryFieldName = "$factory";
        if (hasFactoryMethod) {
            // for factory methods we have to store the factory instance in a field and modify the constructor pass the factory instance
            newFinalField(interceptedConstructorWriter, factoryType, factoryFieldName);

            interceptedConstructorDescriptor = getConstructorDescriptor(new Type[]{
                    TYPE_BEAN_DEFINITION,
                    factoryType
            });
        } else {
            interceptedConstructorDescriptor = constructorMethod.getDescriptor();

        }
        protectedConstructor = new GeneratorAdapter(
                interceptedConstructorWriter.visitMethod(
                        ACC_PROTECTED, CONSTRUCTOR_NAME,
                        interceptedConstructorDescriptor,
                        null,
                        null
                ),
                ACC_PROTECTED,
                CONSTRUCTOR_NAME,
                interceptedConstructorDescriptor
        );
        if (hasFactoryMethod) {
            protectedConstructor.loadThis();
            protectedConstructor.loadArg(1);
            protectedConstructor.putField(constructorInjectionPointInnerClass.innerClassType, factoryFieldName, factoryType);
        }
        protectedConstructor.loadThis();
        protectedConstructor.loadArg(0);
        protectedConstructor.invokeConstructor(Type.getType(AbstractBeanDefinitionBeanConstructor.class), constructorMethod);
        protectedConstructor.returnValue();
        protectedConstructor.visitMaxs(1, 1);
        protectedConstructor.visitEnd();

        // now we need to implement the invoke method to execute the actual instantiation
        final GeneratorAdapter invokeMethod = startPublicMethod(interceptedConstructorWriter, METHOD_BEAN_CONSTRUCTOR_INSTANTIATE);
        if (hasFactoryMethod) {
            invokeMethod.loadThis();
            invokeMethod.getField(
                    constructorInjectionPointInnerClass.innerClassType,
                    factoryFieldName,
                    factoryType
            );
            pushCastToType(invokeMethod, factoryType);
        } else {
            invokeMethod.visitTypeInsn(NEW, beanType.getInternalName());
            invokeMethod.visitInsn(DUP);
        }
        for (int i = 0; i < parameters.size(); i++) {
            invokeMethod.loadArg(0);
            invokeMethod.push(i);
            invokeMethod.arrayLoad(TYPE_OBJECT);
            pushCastToType(invokeMethod, parameters.get(i));
        }

        if (hasFactoryMethod) {
            if (factoryMethodDef.factoryMethod instanceof MethodElement) {

                invokeMethod.visitMethodInsn(
                        INVOKEVIRTUAL,
                        factoryType.getInternalName(),
                        factoryMethodDef.factoryMethod.getName(),
                        factoryMethodDef.methodDescriptor,
                        false
                );
            } else {
                invokeMethod.getField(factoryType, factoryMethodDef.factoryMethod.getName(), beanType);
            }
        } else {
            String constructorDescriptor = getConstructorDescriptor(parameters);
            invokeMethod.visitMethodInsn(INVOKESPECIAL, beanType.getInternalName(), "<init>", constructorDescriptor, false);
        }
        invokeMethod.returnValue();
        invokeMethod.visitMaxs(1, 1);
        invokeMethod.visitEnd();

        // instantiate a new instance and return
        buildMethodVisitor.visitTypeInsn(NEW, constructorInjectionPointInnerClass.constructorInternalName);
        buildMethodVisitor.visitInsn(DUP);
        // pass outer class instance to constructor
        buildMethodVisitor.loadThis();

        if (hasFactoryMethod) {
            buildMethodVisitor.loadLocal(factoryMethodDef.factoryVar);
            pushCastToType(buildMethodVisitor, factoryType);
        }

        buildMethodVisitor.visitMethodInsn(
                INVOKESPECIAL,
                constructorInjectionPointInnerClass.constructorInternalName,
                "<init>",
                interceptedConstructorDescriptor,
                false
        );

        final int constructorIndex = buildMethodVisitor.newLocal(Type.getType(AbstractBeanDefinitionBeanConstructor.class));
        buildMethodVisitor.storeLocal(constructorIndex);
        return constructorIndex;
    }

    private void newFinalField(ClassWriter classWriter, Type fieldType, String fieldName) {
        classWriter
                .visitField(ACC_PRIVATE | ACC_FINAL,
                        fieldName,
                        fieldType.getDescriptor(),
                        null,
                        null
                );
    }

    private InnerClassDef newInnerClass(Class<?> superType) {
        ClassWriter interceptedConstructorWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        String interceptedConstructorWriterName = newInnerClassName();
        this.innerClasses.put(interceptedConstructorWriterName, interceptedConstructorWriter);
        final String constructorInternalName = getInternalName(interceptedConstructorWriterName);
        final Type interceptedConstructorType = getTypeReferenceForName(interceptedConstructorWriterName);
        interceptedConstructorWriter.visit(V17, ACC_SYNTHETIC | ACC_FINAL | ACC_PRIVATE,
                constructorInternalName,
                null,
                Type.getInternalName(superType),
                null
        );

        interceptedConstructorWriter.visitAnnotation(TYPE_GENERATED.getDescriptor(), false);
        interceptedConstructorWriter.visitOuterClass(
                beanDefinitionInternalName,
                null,
                null
        );
        classWriter.visitInnerClass(constructorInternalName, beanDefinitionInternalName, null, ACC_PRIVATE);
        return new InnerClassDef(
                interceptedConstructorWriterName,
                interceptedConstructorWriter,
                constructorInternalName,
                interceptedConstructorType
        );
    }

    @NonNull
    private String newInnerClassName() {
        return this.beanDefinitionName + "$" + ++innerClassIndex;
    }

    private int createConstructorParameterArray(List<ParameterElement> parameters, GeneratorAdapter buildMethodVisitor) {
        return createParameterArray(parameters, buildMethodVisitor, (index, parameter) ->
            pushConstructorArgument(
                buildMethodVisitor,
                parameter,
                index,
                true
            )
        );
    }

    private static int createParameterArray(List<ParameterElement> parameters, GeneratorAdapter buildMethodVisitor, BiConsumer<Integer, ParameterElement> parameterHandler) {
        pushNewArrayIndexed(buildMethodVisitor, Object.class, parameters, parameterHandler);
        int local = buildMethodVisitor.newLocal(Type.getType(Object[].class));
        buildMethodVisitor.storeLocal(local);
        return local;
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

    private boolean pushConstructorArguments(GeneratorAdapter buildMethodVisitor,
                                             ParameterElement[] parameters) {
        int size = parameters.length;
        boolean hasInjectScope = false;
        if (size > 0) {
            for (int i = 0; i < parameters.length; i++) {
                ParameterElement parameter = parameters[i];
                pushConstructorArgument(buildMethodVisitor, parameter, i);
                if (parameter.hasDeclaredAnnotation(InjectScope.class)) {
                    hasInjectScope = true;
                }
            }
        }
        return hasInjectScope;
    }

    private boolean pushParametersAsArray(GeneratorAdapter buildMethodVisitor, ParameterElement[] parameters) {
        pushNewArrayIndexed(buildMethodVisitor, Object.class, Arrays.asList(parameters), (index, parameter) -> {
            pushConstructorArgument(buildMethodVisitor, parameter, index, true);
        });
        boolean hasInjectScope = false;
        for (ParameterElement parameter : parameters) {
            if (parameter.hasDeclaredAnnotation(InjectScope.class)) {
                hasInjectScope = true;
            }
        }
        return hasInjectScope;
    }

    private void pushConstructorArgument(GeneratorAdapter buildMethodVisitor,
                                         ParameterElement parameter,
                                         int index) {
        pushConstructorArgument(buildMethodVisitor, parameter, index, false);
    }

    private void pushConstructorArgument(GeneratorAdapter buildMethodVisitor,
                                         ParameterElement parameter,
                                         int index,
                                         boolean castToObject) {
        AnnotationMetadata annotationMetadata = parameter.getAnnotationMetadata();
        if (isAnnotatedWithParameter(annotationMetadata) && isParametrized) {
            // load the args
            buildMethodVisitor.loadArg(2);
            // the argument name
            buildMethodVisitor.push(parameter.getName());
            buildMethodVisitor.invokeInterface(Type.getType(Map.class), org.objectweb.asm.commons.Method.getMethod(ReflectionUtils.getRequiredMethod(Map.class, "get", Object.class)));
            pushCastToType(buildMethodVisitor, parameter);
        } else if (!pushValueBypassingBeanContext(buildMethodVisitor, parameter.getGenericType())) {
            boolean hasGenericType = false;
            boolean isArray;
            Method methodToInvoke;
            final ClassElement genericType = parameter.getGenericType();
            if (isValueType(annotationMetadata) && !isInnerType(genericType)) {
                Optional<String> property = parameter.stringValue(Property.class, "name");
                if (property.isPresent()) {
                    pushInvokeGetPropertyValueForConstructor(buildMethodVisitor, index, parameter, property.get());
                } else {
                    if (parameter.getValue(Value.class, EvaluatedExpressionReference.class).isPresent()) {
                        pushInvokeGetEvaluatedExpressionValueForConstructorArgument(buildMethodVisitor, index, parameter);
                    } else {
                        Optional<String> valueValue = parameter.stringValue(Value.class);
                        valueValue.ifPresent(s -> pushInvokeGetPropertyPlaceholderValueForConstructor(buildMethodVisitor, index, parameter, s));
                    }
                }
                return;
            } else {
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
            }
            // Load this for method call
            buildMethodVisitor.loadThis();
            // load the first two arguments of the method (the BeanResolutionContext and the BeanContext) to be passed to the method
            buildMethodVisitor.loadArg(0);
            buildMethodVisitor.loadArg(1);
            // pass the index of the method as the third argument
            buildMethodVisitor.push(index);
            if (hasGenericType) {
                resolveConstructorArgumentGenericType(buildMethodVisitor, parameter.getGenericType(), index);
            }
            // push qualifier
            pushQualifier(buildMethodVisitor, parameter, () -> {
                resolveConstructorArgument(buildMethodVisitor, index);
            });
            // invoke method
            pushInvokeMethodOnSuperClass(buildMethodVisitor, methodToInvoke);
            if (isArray && hasGenericType) {
                convertToArray(parameter.getGenericType().fromArray(), buildMethodVisitor);
            }
            if (castToObject) {
                if (parameter.isPrimitive()) {
                    pushCastToType(buildMethodVisitor, Object.class);
                }
            } else {
                pushCastToType(buildMethodVisitor, parameter);
            }
        }
    }

    private void pushInvokeGetPropertyValueForConstructor(GeneratorAdapter injectMethodVisitor, int i, ParameterElement entry, String value) {
        // load 'this'
        injectMethodVisitor.loadThis();
        // 1st argument load BeanResolutionContext
        injectMethodVisitor.loadArg(0);
        // 2nd argument load BeanContext
        injectMethodVisitor.loadArg(1);
        // 4th argument the argument index
        injectMethodVisitor.push(i);
        // 5th property value
        injectMethodVisitor.push(value);
        // 6 cli property name
        injectMethodVisitor.push(getCliPrefix(entry.getName()));

        pushInvokeMethodOnSuperClass(injectMethodVisitor, GET_PROPERTY_VALUE_FOR_CONSTRUCTOR_ARGUMENT);
        // cast the return value to the correct type
        pushCastToType(injectMethodVisitor, entry);
    }

    private void pushInvokeGetPropertyPlaceholderValueForConstructor(GeneratorAdapter injectMethodVisitor, int i, ParameterElement entry, String value) {
        // load 'this'
        injectMethodVisitor.loadThis();
        // 1st argument load BeanResolutionContext
        injectMethodVisitor.loadArg(0);
        // 2nd argument load BeanContext
        injectMethodVisitor.loadArg(1);
        // 4th argument the argument index
        injectMethodVisitor.push(i);
        // 5th property value
        injectMethodVisitor.push(value);

        pushInvokeMethodOnSuperClass(injectMethodVisitor, GET_PROPERTY_PLACEHOLDER_VALUE_FOR_CONSTRUCTOR_ARGUMENT);
        // cast the return value to the correct type
        pushCastToType(injectMethodVisitor, entry);
    }

    private void pushInvokeGetEvaluatedExpressionValueForConstructorArgument(GeneratorAdapter injectMethodVisitor, int i, ParameterElement entry) {
        // load 'this'
        injectMethodVisitor.loadThis();
        // 2nd argument the argument index
        injectMethodVisitor.push(i);

        pushInvokeMethodOnSuperClass(injectMethodVisitor, GET_EVALUATED_EXPRESSION_VALUE_FOR_CONSTRUCTOR_ARGUMENT);
        // cast the return value to the correct type
        pushCastToType(injectMethodVisitor, entry);
    }

    private void resolveConstructorArgumentGenericType(GeneratorAdapter visitor, ClassElement type, int argumentIndex) {
        if (!resolveArgumentGenericType(visitor, type)) {
            resolveConstructorArgument(visitor, argumentIndex);
            if (type.isAssignable(Map.class)) {
                resolveSecondTypeArgument(visitor);
            } else {
                resolveFirstTypeArgument(visitor);
            }
            resolveInnerTypeArgumentIfNeeded(visitor, type);
        }
    }

    private void resolveConstructorArgument(GeneratorAdapter visitor, int argumentIndex) {
        Type constructorField = Type.getType(AbstractInitializableBeanDefinition.MethodOrFieldReference.class);
        Type methodRefType = Type.getType(AbstractInitializableBeanDefinition.MethodReference.class);
        visitor.getStatic(beanDefinitionType, FIELD_CONSTRUCTOR, constructorField);
        pushCastToType(visitor, methodRefType);
        visitor.getField(methodRefType, "arguments", Type.getType(Argument[].class));
        visitor.push(argumentIndex);
        visitor.arrayLoad(Type.getType(Argument.class));
    }

    private void resolveMethodArgumentGenericType(GeneratorAdapter visitor, ClassElement type, int methodIndex, int argumentIndex) {
        if (!resolveArgumentGenericType(visitor, type)) {
            resolveMethodArgument(visitor, methodIndex, argumentIndex);
            if (type.isAssignable(Map.class)) {
                resolveSecondTypeArgument(visitor);
            } else {
                resolveFirstTypeArgument(visitor);
            }
            resolveInnerTypeArgumentIfNeeded(visitor, type);
        }
    }

    private void resolveMethodArgument(GeneratorAdapter visitor, int methodIndex, int argumentIndex) {
        Type methodsRef = Type.getType(AbstractInitializableBeanDefinition.MethodReference[].class);
        Type methodRefType = Type.getType(AbstractInitializableBeanDefinition.MethodReference.class);
        visitor.getStatic(beanDefinitionType, FIELD_INJECTION_METHODS, methodsRef);
        visitor.push(methodIndex);
        visitor.arrayLoad(methodsRef);
        visitor.getField(methodRefType, "arguments", Type.getType(Argument[].class));
        visitor.push(argumentIndex);
        visitor.arrayLoad(Type.getType(Argument.class));
    }

    private void resolveFieldArgumentGenericType(GeneratorAdapter visitor, ClassElement type, int fieldIndex) {
        if (!resolveArgumentGenericType(visitor, type)) {
            resolveFieldArgument(visitor, fieldIndex);
            if (type.isAssignable(Map.class)) {
                resolveSecondTypeArgument(visitor);
            } else {
                resolveFirstTypeArgument(visitor);
            }
            resolveInnerTypeArgumentIfNeeded(visitor, type);
        }
    }

    private void resolveAnnotationArgument(GeneratorAdapter visitor, int index) {
        visitor.getStatic(beanDefinitionType, FIELD_ANNOTATION_INJECTIONS, Type.getType(AbstractInitializableBeanDefinition.FieldReference[].class));
        visitor.push(index);
        visitor.arrayLoad(Type.getType(AbstractInitializableBeanDefinition.AnnotationReference.class));
        visitor.getField(Type.getType(AbstractInitializableBeanDefinition.AnnotationReference.class), "argument", Type.getType(Argument.class));
    }

    private void resolveFieldArgument(GeneratorAdapter visitor, int fieldIndex) {
        visitor.getStatic(beanDefinitionType, FIELD_INJECTION_FIELDS, Type.getType(AbstractInitializableBeanDefinition.FieldReference[].class));
        visitor.push(fieldIndex);
        visitor.arrayLoad(Type.getType(AbstractInitializableBeanDefinition.FieldReference.class));
        visitor.getField(Type.getType(AbstractInitializableBeanDefinition.FieldReference.class), "argument", Type.getType(Argument.class));
    }

    private boolean resolveArgumentGenericType(GeneratorAdapter visitor, ClassElement type) {
        if (type.isArray()) {
            if (!type.getTypeArguments().isEmpty() && isInternalGenericTypeContainer(type.fromArray())) {
                // skip for arrays of BeanRegistration
                return false;
            }
            final ClassElement componentType = type.fromArray();
            if (componentType.isPrimitive()) {
                visitor.getStatic(
                        TYPE_ARGUMENT,
                        componentType.getName().toUpperCase(Locale.ENGLISH),
                        TYPE_ARGUMENT
                );
            } else {

                visitor.push(JavaModelUtils.getTypeReference(componentType));
                visitor.push((String) null);
                invokeInterfaceStaticMethod(
                        visitor,
                        Argument.class,
                        METHOD_CREATE_ARGUMENT_SIMPLE
                );
            }
            return true;
        } else if (type.getTypeArguments().isEmpty()) {
            visitor.visitInsn(ACONST_NULL);
            return true;
        }
        return false;
    }

    private void resolveInnerTypeArgumentIfNeeded(GeneratorAdapter visitor, ClassElement type) {
        if (isInternalGenericTypeContainer(type.getFirstTypeArgument().orElse(null))) {
            resolveFirstTypeArgument(visitor);
        }
    }

    private boolean isInternalGenericTypeContainer(@Nullable ClassElement type) {
        return type != null && type.isAssignable(BeanRegistration.class);
    }

    private void resolveFirstTypeArgument(GeneratorAdapter visitor) {
        visitor.invokeInterface(Type.getType(TypeVariableResolver.class),
                org.objectweb.asm.commons.Method.getMethod(ReflectionUtils.getRequiredInternalMethod(TypeVariableResolver.class, "getTypeParameters")));
        visitor.push(0);
        visitor.arrayLoad(Type.getType(Argument.class));
    }

    private void resolveSecondTypeArgument(GeneratorAdapter visitor) {
        visitor.invokeInterface(Type.getType(TypeVariableResolver.class),
                org.objectweb.asm.commons.Method.getMethod(ReflectionUtils.getRequiredInternalMethod(TypeVariableResolver.class, "getTypeParameters")));
        visitor.push(1);
        visitor.arrayLoad(Type.getType(Argument.class));
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

    private void defineBuilderMethod(boolean isParametrized) {
        if (isParametrized) {
            this.isParametrized = true;
        }

        String methodDescriptor;
        String methodSignature;

        if (isParametrized) {
            methodDescriptor = getMethodDescriptor(
                    Object.class.getName(),
                    BeanResolutionContext.class.getName(),
                    BeanContext.class.getName(),
                    Map.class.getName()
            );
            methodSignature = getMethodSignature(
                    getTypeDescriptor(beanTypeElement),
                    getTypeDescriptor(BeanResolutionContext.class.getName()),
                    getTypeDescriptor(BeanContext.class.getName()),
                    getTypeDescriptor(Map.class.getName())
            );
        } else {
            methodDescriptor = getMethodDescriptor(
                    Object.class.getName(),
                    BeanResolutionContext.class.getName(),
                    BeanContext.class.getName()
            );
            methodSignature = getMethodSignature(
                    getTypeDescriptor(beanTypeElement),
                    getTypeDescriptor(BeanResolutionContext.class.getName()),
                    getTypeDescriptor(BeanContext.class.getName())
            );
        }

        String methodName = isParametrized ? "doInstantiate" : METHOD_NAME_INSTANTIATE;
        this.buildMethodVisitor = new GeneratorAdapter(classWriter.visitMethod(
                ACC_PUBLIC,
                methodName,
                methodDescriptor,
                methodSignature,
                null), ACC_PUBLIC, methodName, methodDescriptor);
    }

    private void pushBeanDefinitionMethodInvocation(GeneratorAdapter buildMethodVisitor, String methodName) {
        buildMethodVisitor.loadThis();
        buildMethodVisitor.loadArg(0);
        buildMethodVisitor.loadArg(1);
        buildMethodVisitor.loadLocal(buildInstanceLocalVarIndex);
        pushBoxPrimitiveIfNecessary(beanType, buildMethodVisitor);
        buildMethodVisitor.visitMethodInsn(INVOKEVIRTUAL,
                superBeanDefinition ? superType.getInternalName() : beanDefinitionInternalName,
                methodName,
                METHOD_DESCRIPTOR_INITIALIZE,
                false);
    }

    private void visitBeanDefinitionConstructorInternal(GeneratorAdapter staticInit, Object constructor) {

        if (constructor instanceof MethodElement methodElement) {
            ParameterElement[] parameters = methodElement.getParameters();
            List<ParameterElement> parameterList = Arrays.asList(parameters);
            applyDefaultNamedToParameters(parameterList);

            pushNewMethodReference(staticInit, JavaModelUtils.getTypeReference(methodElement.getDeclaringType()), methodElement, methodElement.getAnnotationMetadata(), false, false);
        } else if (constructor instanceof FieldElement fieldConstructor) {
            pushNewFieldReference(staticInit, JavaModelUtils.getTypeReference(fieldConstructor.getDeclaringType()), fieldConstructor, fieldConstructor.getAnnotationMetadata());
        } else {
            throw new IllegalArgumentException("Unexpected constructor: " + constructor);
        }

        staticInit.putStatic(beanDefinitionType, FIELD_CONSTRUCTOR, Type.getType(AbstractInitializableBeanDefinition.MethodOrFieldReference.class));

        GeneratorAdapter publicConstructor = new GeneratorAdapter(
                classWriter.visitMethod(ACC_PUBLIC, CONSTRUCTOR_NAME, DESCRIPTOR_DEFAULT_CONSTRUCTOR, null, null),
                ACC_PUBLIC,
                CONSTRUCTOR_NAME,
                DESCRIPTOR_DEFAULT_CONSTRUCTOR
        );
        publicConstructor.loadThis();
        publicConstructor.push(beanType);
        publicConstructor.getStatic(beanDefinitionType, FIELD_CONSTRUCTOR, Type.getType(AbstractInitializableBeanDefinition.MethodOrFieldReference.class));
        publicConstructor.invokeConstructor(superBeanDefinition ? superType : beanDefinitionType, PROTECTED_ABSTRACT_BEAN_DEFINITION_CONSTRUCTOR);
        publicConstructor.visitInsn(RETURN);
        publicConstructor.visitMaxs(5, 1);
        publicConstructor.visitEnd();

        // Call protected super constructor if definition is extending another one

        if (!superBeanDefinition) {
            // create protected constructor for subclasses of AbstractBeanDefinition
            GeneratorAdapter protectedConstructor = new GeneratorAdapter(
                    classWriter.visitMethod(ACC_PROTECTED,
                            PROTECTED_ABSTRACT_BEAN_DEFINITION_CONSTRUCTOR.getName(),
                            PROTECTED_ABSTRACT_BEAN_DEFINITION_CONSTRUCTOR.getDescriptor(), null, null),
                    ACC_PROTECTED,
                    PROTECTED_ABSTRACT_BEAN_DEFINITION_CONSTRUCTOR.getName(),
                    PROTECTED_ABSTRACT_BEAN_DEFINITION_CONSTRUCTOR.getDescriptor()
            );

            AnnotationMetadata annotationMetadata = this.annotationMetadata != null ? this.annotationMetadata : AnnotationMetadata.EMPTY_METADATA;

            protectedConstructor.loadThis();
            // 1: beanType
            protectedConstructor.loadArg(0);
            // 2: `AbstractBeanDefinition2.MethodOrFieldReference.class` constructor
            protectedConstructor.loadArg(1);

            AbstractAnnotationMetadataWriter.writeAnnotationDefault(classWriter, staticInit, beanDefinitionType, annotationMetadata, defaultsStorage, loadTypeMethods);
            AbstractAnnotationMetadataWriter.initializeAnnotationMetadata(staticInit, classWriter, beanDefinitionType, annotationMetadata, defaultsStorage, loadTypeMethods);

            // 3: annotationMetadata
            if (annotationMetadata.isEmpty()) {
                protectedConstructor.push((String) null);
            } else if (annotationMetadata instanceof AnnotationMetadataReference reference) {
                String className = reference.getClassName();
                protectedConstructor.getStatic(getTypeReferenceForName(className), AbstractAnnotationMetadataWriter.FIELD_ANNOTATION_METADATA, Type.getType(AnnotationMetadata.class));
            } else {
                protectedConstructor.getStatic(beanDefinitionType, AbstractAnnotationMetadataWriter.FIELD_ANNOTATION_METADATA, Type.getType(AnnotationMetadata.class));
            }

            // 4: `AbstractBeanDefinition2.MethodReference[].class` methodInjection
            if (allMethodVisits.isEmpty()) {
                protectedConstructor.push((String) null);
            } else {
                protectedConstructor.getStatic(beanDefinitionType, FIELD_INJECTION_METHODS, Type.getType(AbstractInitializableBeanDefinition.MethodReference[].class));
            }
            // 5: `AbstractBeanDefinition2.FieldReference[].class` fieldInjection
            if (fieldInjectionPoints.isEmpty()) {
                protectedConstructor.push((String) null);
            } else {
                protectedConstructor.getStatic(beanDefinitionType, FIELD_INJECTION_FIELDS, Type.getType(AbstractInitializableBeanDefinition.FieldReference[].class));
            }
            // 6: `AbstractBeanDefinition2.AnnotationReference[].class` annotationInjection
            if (annotationInjectionPoints.isEmpty()) {
                protectedConstructor.push((String) null);
            } else {
                protectedConstructor.getStatic(beanDefinitionType, FIELD_ANNOTATION_INJECTIONS, Type.getType(AbstractInitializableBeanDefinition.AnnotationReference[].class));
            }
            // 7: `ExecutableMethod[]` executableMethods
            if (executableMethodsDefinitionWriter == null) {
                protectedConstructor.push((String) null);
            } else {
                String fieldName = "$EXEC";
                Type execType = executableMethodsDefinitionWriter.getClassType();
                classWriter.visitField(ACC_PRIVATE | ACC_FINAL | ACC_STATIC,
                    fieldName,
                    execType.getDescriptor(),
                    null,
                    null
                );
                staticInit.newInstance(execType);
                staticInit.dup();
                staticInit.invokeConstructor(execType, METHOD_DEFAULT_CONSTRUCTOR);
                staticInit.putStatic(beanDefinitionType, fieldName, execType);
                protectedConstructor.getStatic(beanDefinitionType, fieldName, execType);
            }
            // 8: `Map<String, Argument<?>[]>` typeArgumentsMap
            if (!hasTypeArguments()) {
                protectedConstructor.push((String) null);
            } else {
                protectedConstructor.getStatic(beanDefinitionType, FIELD_TYPE_ARGUMENTS, Type.getType(Map.class));
            }

            // 9: `PrecalculatedInfo`
            pushPrecalculatedInfo(staticInit, protectedConstructor, annotationMetadata);

            protectedConstructor.invokeConstructor(
                    getSuperType(),
                    BEAN_DEFINITION_CLASS_CONSTRUCTOR
            );

            protectedConstructor.visitInsn(RETURN);
            protectedConstructor.visitMaxs(20, 1);
            protectedConstructor.visitEnd();
        }
    }

    private void pushPrecalculatedInfo(GeneratorAdapter staticInit, GeneratorAdapter protectedConstructor, AnnotationMetadata annotationMetadata) {
        String fieldName = "$INFO";
        classWriter.visitField(ACC_PRIVATE | ACC_FINAL | ACC_STATIC,
            fieldName,
            PRECALCULATED_INFO.getDescriptor(),
            null,
            null
        );

        staticInit.newInstance(PRECALCULATED_INFO);
        staticInit.dup();

        // 1: `Optional` scope
        String scope = annotationMetadata.getAnnotationNameByStereotype(AnnotationUtil.SCOPE).orElse(null);
        if (scope != null) {
            staticInit.push(scope);
            staticInit.invokeStatic(
                    TYPE_OPTIONAL,
                    METHOD_OPTIONAL_OF
            );
        } else {
            staticInit.invokeStatic(TYPE_OPTIONAL, METHOD_OPTIONAL_EMPTY);
        }

        // 2: `boolean` isAbstract
        staticInit.push(isAbstract);
        // 3: `boolean` isIterable
        staticInit.push(isIterable(annotationMetadata));
        // 4: `boolean` isSingleton
        staticInit.push(
                isSingleton(scope)
        );
        // 5: `boolean` isPrimary
        staticInit.push(
                annotationMetadata.hasDeclaredStereotype(Primary.class)
        );
        // 6: `boolean` isConfigurationProperties
        staticInit.push(isConfigurationProperties);
        // 7: isContainerType
        staticInit.push(isContainerType());
        // 8: staticInit
        staticInit.push(preprocessMethods);

        // 9: hasEvaluatedExpressions
        staticInit.push(evaluatedExpressionProcessor.hasEvaluatedExpressions());

        staticInit.invokeConstructor(PRECALCULATED_INFO, PRECALCULATED_INFO_CONSTRUCTOR);
        staticInit.putStatic(beanDefinitionType, fieldName, PRECALCULATED_INFO);
        protectedConstructor.getStatic(beanDefinitionType, fieldName, PRECALCULATED_INFO);
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

    private void pushNewMethodReference(GeneratorAdapter staticInit,
                                        Type beanType,
                                        MethodElement methodElement,
                                        AnnotationMetadata annotationMetadata,
                                        boolean isPostConstructMethod,
                                        boolean isPreDestroyMethod) {
        annotationMetadata = annotationMetadata.getTargetAnnotationMetadata();
        staticInit.newInstance(Type.getType(AbstractInitializableBeanDefinition.MethodReference.class));
        staticInit.dup();
        // 1: declaringType
        staticInit.push(beanType);
        // 2: methodName
        staticInit.push(methodElement.getName());
        // 3: arguments
        if (!methodElement.hasParameters()) {
            staticInit.visitInsn(ACONST_NULL);
        } else {
            pushBuildArgumentsForMethod(
                    this.annotationMetadata,
                    beanFullClassName,
                    beanDefinitionType,
                    classWriter,
                    staticInit,
                    Arrays.asList(methodElement.getParameters()),
                    defaultsStorage,
                    loadTypeMethods
            );
        }
        // 4: annotationMetadata
        if (annotationMetadata instanceof AnnotationMetadataHierarchy hierarchy) {
            annotationMetadata = hierarchy.merge();
        }
        pushAnnotationMetadata(staticInit, annotationMetadata);
        if (isPreDestroyMethod || isPostConstructMethod) {
            // 5: isPostConstructMethod
            staticInit.push(isPostConstructMethod);
            // 6: isPreDestroyMethod
            staticInit.push(isPreDestroyMethod);
            staticInit.invokeConstructor(Type.getType(AbstractInitializableBeanDefinition.MethodReference.class), METHOD_REFERENCE_CONSTRUCTOR_POST_PRE);
        } else {
            staticInit.invokeConstructor(Type.getType(AbstractInitializableBeanDefinition.MethodReference.class), METHOD_REFERENCE_CONSTRUCTOR);
        }
    }

    private void pushNewFieldReference(GeneratorAdapter staticInit, Type declaringType, FieldElement fieldElement, AnnotationMetadata annotationMetadata) {
        staticInit.newInstance(Type.getType(AbstractInitializableBeanDefinition.FieldReference.class));
        staticInit.dup();
        // 1: declaringType
        staticInit.push(declaringType);
        // 2: argument
        pushCreateArgument(
                this.annotationMetadata,
                beanFullClassName,
                beanDefinitionType,
                classWriter,
                staticInit,
                fieldElement.getName(),
                fieldElement.getGenericType(),
                annotationMetadata,
                fieldElement.getGenericType().getTypeArguments(),
                defaultsStorage,
                loadTypeMethods
        );
        staticInit.invokeConstructor(Type.getType(AbstractInitializableBeanDefinition.FieldReference.class), FIELD_REFERENCE_CONSTRUCTOR);
    }

    private void pushNewAnnotationReference(GeneratorAdapter staticInit, Type referencedType) {
        staticInit.newInstance(Type.getType(AbstractInitializableBeanDefinition.AnnotationReference.class));
        staticInit.dup();

        // 1: argument
        staticInit.push(referencedType);
        invokeInterfaceStaticMethod(
                staticInit,
                Argument.class,
                org.objectweb.asm.commons.Method.getMethod(
                        ReflectionUtils.getRequiredInternalMethod(Argument.class, "of", Class.class)));

        staticInit.invokeConstructor(Type.getType(AbstractInitializableBeanDefinition.AnnotationReference.class),
                ANNOTATION_REFERENCE_CONSTRUCTOR);
    }

    private void pushAnnotationMetadata(GeneratorAdapter staticInit,
                                        AnnotationMetadata annotationMetadata) {
        annotationMetadata = annotationMetadata.getTargetAnnotationMetadata();
//
//        MutableAnnotationMetadata.contributeDefaults(
//            this.annotationMetadata,
//            annotationMetadata
//        );

        if (annotationMetadata == AnnotationMetadata.EMPTY_METADATA || annotationMetadata.isEmpty()) {
            staticInit.push((String) null);
        } else if (annotationMetadata instanceof AnnotationMetadataHierarchy annotationMetadataHierarchy) {
            AnnotationMetadataWriter.instantiateNewMetadataHierarchy(
                    beanDefinitionType,
                    classWriter,
                    staticInit,
                    annotationMetadataHierarchy,
                    defaultsStorage,
                    loadTypeMethods);
        } else if (annotationMetadata instanceof MutableAnnotationMetadata mutableAnnotationMetadata) {
            AnnotationMetadataWriter.instantiateNewMetadata(
                    beanDefinitionType,
                    classWriter,
                    staticInit,
                    mutableAnnotationMetadata,
                    defaultsStorage,
                    loadTypeMethods);
        } else {
            throw new IllegalStateException("Unknown annotation metadata: " + annotationMetadata.getClass().getName());
        }
    }

    private String generateBeanDefSig(Type typeParameter) {
        if (beanTypeElement.isPrimitive()) {
            if (beanTypeElement.isArray()) {
                typeParameter = JavaModelUtils.getTypeReference(beanTypeElement);
            } else {
                typeParameter = ClassUtils.getPrimitiveType(typeParameter.getClassName())
                        .map(ReflectionUtils::getWrapperType)
                        .map(Type::getType)
                        .orElseThrow(() -> new IllegalStateException("Not a primitive type: " + beanFullClassName));
            }
        }
        SignatureVisitor sv = new ArrayAwareSignatureWriter();
        visitSuperTypeParameters(sv, typeParameter);

        // visit BeanFactory interface
        for (Class<?> interfaceType : interfaceTypes) {
            Type param;
            if (ProxyBeanDefinition.class == interfaceType || AdvisedBeanType.class == interfaceType) {
                param = getInterceptedType().orElse(typeParameter);
            } else {
                param = typeParameter;
            }

            SignatureVisitor bfi = sv.visitInterface();
            bfi.visitClassType(Type.getInternalName(interfaceType));
            SignatureVisitor iisv = bfi.visitTypeArgument('=');
            visitTypeParameter(param, iisv);
            bfi.visitEnd();
        }
        return sv.toString();
    }

    private void visitSuperTypeParameters(SignatureVisitor sv, Type... typeParameters) {
        // visit super class
        SignatureVisitor psv = sv.visitSuperclass();
        psv.visitClassType(getSuperTypeInternalType());
        if (superType == TYPE_ABSTRACT_BEAN_DEFINITION_AND_REFERENCE || isSuperFactory) {
            for (Type typeParameter : typeParameters) {

                SignatureVisitor ppsv = psv.visitTypeArgument('=');
                visitTypeParameter(typeParameter, ppsv);
            }
        }

        psv.visitEnd();
    }

    private void visitTypeParameter(Type typeParameter, SignatureVisitor ppsv) {
        final boolean isArray = typeParameter.getSort() == Type.ARRAY;
        boolean isPrimitiveArray = false;
        if (isArray) {
            for (int i = 0; i < typeParameter.getDimensions(); i++) {
                ppsv.visitArrayType();
            }
            Type elementType = typeParameter.getElementType();
            while (elementType.getSort() == Type.ARRAY) {
                elementType = elementType.getElementType();
            }
            if (elementType.getSort() == Type.OBJECT) {
                ppsv.visitClassType(elementType.getInternalName());
            } else {
                // primitive
                ppsv.visitBaseType(elementType.getInternalName().charAt(0));
                isPrimitiveArray = true;
            }
        } else {
            ppsv.visitClassType(typeParameter.getInternalName());
        }
        if (isPrimitiveArray && ppsv instanceof ArrayAwareSignatureWriter writer) {
            writer.visitEndArray();
        } else {
            ppsv.visitEnd();
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


    private record FactoryMethodDef(Type factoryType, Element factoryMethod, String methodDescriptor, int factoryVar) {
    }

    private static class InnerClassDef {
        private final ClassWriter innerClassWriter;
        private final String constructorInternalName;
        private final Type innerClassType;

        public InnerClassDef(String interceptedConstructorWriterName, ClassWriter innerClassWriter, String constructorInternalName, Type innerClassType) {
            this.innerClassWriter = innerClassWriter;
            this.constructorInternalName = constructorInternalName;
            this.innerClassType = innerClassType;
        }
    }
}
