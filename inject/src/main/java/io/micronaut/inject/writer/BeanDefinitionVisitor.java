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

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.ast.Element;
import io.micronaut.inject.configuration.ConfigurationMetadataBuilder;
import org.objectweb.asm.Type;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.io.File;
import java.io.IOException;
import java.util.Map;

/**
 * Interface for {@link BeanDefinitionVisitor} implementations such as {@link BeanDefinitionWriter}.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public interface BeanDefinitionVisitor {

    /**
     * The suffix use for generated AOP intercepted types.
     */
    String PROXY_SUFFIX = "$Intercepted";

    /**
     * @return The element where the bean definition originated from.
     */
    @Nullable Element getOriginatingElement();

    /**
     * Visits a no arguments constructor. Either this method or
     * {@link #visitBeanDefinitionConstructor(AnnotationMetadata, boolean, Map, Map, Map)} should be called at least
     * once.
     *
     * @param annotationMetadata The annotation metadata for the constructor.
     * @param requiresReflection Whether invoking the constructor requires reflection.
     */
    void visitBeanDefinitionConstructor(
            AnnotationMetadata annotationMetadata,
            boolean requiresReflection
    );

    /**
     * Visits the constructor used to create the bean definition.
     *
     * @param annotationMetadata         The annotation metadata for the constructor
     * @param requiresReflection         Whether invoking the constructor requires reflection
     * @param argumentTypes              The argument type names for each parameter
     * @param argumentAnnotationMetadata The argument annotation metadata
     * @param genericTypes               The generic types for each parameter
     */
    void visitBeanDefinitionConstructor(AnnotationMetadata annotationMetadata,
                                        boolean requiresReflection,

                                        Map<String, Object> argumentTypes,
                                        Map<String, AnnotationMetadata> argumentAnnotationMetadata,
                                        Map<String, Map<String, Object>> genericTypes);

    /**
     * @return The name of the bean definition reference class.
     */
    @NonNull
    String getBeanDefinitionReferenceClassName();

    /**
     * @return Whether the provided type an interface
     */
    boolean isInterface();

    /**
     * @return Is the bean singleton
     */
    boolean isSingleton();

    /**
     * Visit a marker interface on the generated bean definition.
     *
     * @param interfaceType The interface type
     */
    void visitBeanDefinitionInterface(Class<? extends BeanDefinition> interfaceType);

    /**
     * Alter the super class of this bean definition. The passed class should be a subclass of
     * {@link io.micronaut.context.AbstractBeanDefinition}.
     *
     * @param name The super type
     */
    void visitSuperBeanDefinition(String name);

    /**
     * Alter the super class of this bean definition to use another factory bean.
     *
     * @param beanName The bean name
     */
    void visitSuperBeanDefinitionFactory(String beanName);

    /**
     * @return The full class name of the bean
     */
    String getBeanTypeName();

    /**
     * The provided type of the bean. Usually this is the same as {@link #getBeanTypeName()}, except in the case of
     * factory beans which produce a different type.
     *
     * @return The provided type
     */
    Type getProvidedType();

    /**
     * Make the bean definition as validated by javax.validation.
     *
     * @param validated Whether the bean definition is validated
     */
    void setValidated(boolean validated);

    /**
     * @return Return whether the bean definition is validated.
     */
    boolean isValidated();

    /**
     * @return The name of the bean definition class
     */
    String getBeanDefinitionName();


    /**
     * Finalize the bean definition to the given output stream.
     */
    void visitBeanDefinitionEnd();

    /**
     * Write the state of the writer to the given compilation directory.
     *
     * @param compilationDir The compilation directory
     * @throws IOException If an I/O error occurs
     */
    void writeTo(File compilationDir) throws IOException;

    /**
     * Write the class to output via a visitor that manages output destination.
     *
     * @param visitor the writer output visitor
     * @throws IOException If an error occurs
     */
    void accept(ClassWriterOutputVisitor visitor) throws IOException;

    /**
     * Visits an injection point for a field and setter pairing.
     *
     * @param declaringType      The declaring type
     * @param returnType         The return type
     * @param annotationMetadata The annotation metadata
     * @param requiresReflection Whether the setter requires reflection
     * @param fieldType          The field type
     * @param fieldName          The field name
     * @param setterName         The setter name
     * @param genericTypes       The generic types
     * @param isOptional         Whether the setter is optional
     */
    void visitSetterValue(Object declaringType,
                          Object returnType,
                          AnnotationMetadata annotationMetadata,
                          boolean requiresReflection,
                          Object fieldType,
                          String fieldName,
                          String setterName,
                          Map<String, Object> genericTypes,
                          boolean isOptional);


    /**
     * Visits an injection point for a setter.
     *
     * @param declaringType          The declaring type
     * @param returnType             The return type
     * @param methodMetadata         The annotation metadata
     * @param requiresReflection     Whether the setter requires reflection
     * @param valueType              The field type
     * @param setterName             The setter name
     * @param genericTypes           The generic types
     * @param setterArgumentMetadata The setter argument metadata
     * @param isOptional             Whether the setter is optional
     */
    void visitSetterValue(Object declaringType,
                          Object returnType,
                          AnnotationMetadata methodMetadata,
                          boolean requiresReflection,
                          Object valueType,
                          String setterName,
                          Map<String, Object> genericTypes,
                          AnnotationMetadata setterArgumentMetadata,
                          boolean isOptional);

    /**
     * Visits a method injection point.
     *
     * @param declaringType              The declaring type of the method. Either a Class or a string representing
     *                                   the name of the type
     * @param requiresReflection         Whether the method requires reflection
     * @param returnType                 The return type of the method. Either a Class or a string representing the
     *                                   name of the type
     * @param methodName                 The method name
     * @param argumentTypes              The argument types. Note: an ordered map should be used such as LinkedHashMap.
     *                                   Can be null or empty.
     * @param argumentAnnotationMetadata The argument annotation metadata
     * @param genericTypes               The generic types of each argument. Can be null.
     * @param annotationMetadata         The annotation metadata
     */
    void visitPostConstructMethod(Object declaringType,
                                  boolean requiresReflection,
                                  Object returnType,
                                  String methodName,
                                  Map<String, Object> argumentTypes,
                                  Map<String, AnnotationMetadata> argumentAnnotationMetadata,
                                  Map<String, Map<String, Object>> genericTypes,
                                  AnnotationMetadata annotationMetadata);

    /**
     * Visits a method injection point.
     *
     * @param declaringType              The declaring type of the method. Either a Class or a string representing the
     *                                   name of the type
     * @param requiresReflection         Whether the method requires reflection
     * @param returnType                 The return type of the method. Either a Class or a string representing the name
     *                                   of the type
     * @param methodName                 The method name
     * @param argumentTypes              The argument types. Note: an ordered map should be used such as LinkedHashMap.
     *                                   Can be null or empty.
     * @param argumentAnnotationMetadata The argument annotation metadata
     * @param genericTypes               The generic types of each argument. Can be null.
     * @param annotationMetadata         The annotation metadata
     */
    void visitPreDestroyMethod(Object declaringType,
                               boolean requiresReflection,
                               Object returnType,
                               String methodName,
                               Map<String, Object> argumentTypes,
                               Map<String, AnnotationMetadata> argumentAnnotationMetadata,
                               Map<String, Map<String, Object>> genericTypes,
                               AnnotationMetadata annotationMetadata);

    /**
     * Visits a method injection point.
     *
     * @param declaringType              The declaring type of the method. Either a Class or a string representing the
     *                                   name of the type
     * @param requiresReflection         Whether the method requires reflection
     * @param returnType                 The return type of the method. Either a Class or a string representing the name
     *                                   of the type
     * @param methodName                 The method name
     * @param argumentTypes              The argument types. Note: an ordered map should be used such as LinkedHashMap.
     *                                   Can be null or empty.
     * @param argumentAnnotationMetadata The argument annotation metadata
     * @param genericTypes               The generic types of each argument. Can be null.
     * @param annotationMetadata         The annotation metadata
     */
    void visitMethodInjectionPoint(Object declaringType,
                                   boolean requiresReflection,
                                   Object returnType,
                                   String methodName,
                                   Map<String, Object> argumentTypes,
                                   Map<String, AnnotationMetadata> argumentAnnotationMetadata,
                                   Map<String, Map<String, Object>> genericTypes,
                                   AnnotationMetadata annotationMetadata);

    /**
     * Visit a method that is to be made executable allow invocation of said method without reflection.
     *
     * @param declaringType              The declaring type of the method. Either a Class or a string representing the
     *                                   name of the type
     * @param returnType                 The return type of the method. Either a Class or a string representing the name
     *                                   of the type
     * @param genericReturnType          The generic return type
     * @param returnTypeGenericTypes     The return type for generic types
     * @param methodName                 The method name
     * @param argumentTypes              The argument types. Note: an ordered map should be used such as LinkedHashMap.
     *                                   Can be null or empty.
     * @param genericArgumentTypes       The generic argument types. Note: an ordered map should be used such as LinkedHashMap.
     *                                   Can be null or empty.
     * @param argumentAnnotationMetadata The argument annotation metadata
     * @param genericTypes               The generic types of each argument. Can be null.
     * @param annotationMetadata         The annotation metadata for the method
     * @param isInterface                If the method belongs to an interface
     * @param isDefault                  If the method is a default method
     * @return The {@link ExecutableMethodWriter}.
     */
    ExecutableMethodWriter visitExecutableMethod(Object declaringType,
                                                 Object returnType,
                                                 Object genericReturnType,
                                                 Map<String, Object> returnTypeGenericTypes,
                                                 String methodName,
                                                 Map<String, Object> argumentTypes,
                                                 Map<String, Object> genericArgumentTypes,
                                                 Map<String, AnnotationMetadata> argumentAnnotationMetadata,
                                                 Map<String, Map<String, Object>> genericTypes,
                                                 @Nullable AnnotationMetadata annotationMetadata,
                                                 boolean isInterface,
                                                 boolean isDefault);

    /**
     * Visits a field injection point.
     *
     * @param declaringType      The declaring type. Either a Class or a string representing the name of the type
     * @param fieldType          The type of the field
     * @param fieldName          The name of the field
     * @param requiresReflection Whether accessing the field requires reflection
     * @param annotationMetadata The annotation metadata for the field
     * @param typeArguments      The generic type arguments
     */
    void visitFieldInjectionPoint(Object declaringType,
                                  Object fieldType,
                                  String fieldName,
                                  boolean requiresReflection,
                                  @Nullable AnnotationMetadata annotationMetadata,
                                  @Nullable Map<String, Object> typeArguments);

    /**
     * Visits a field injection point.
     *
     * @param declaringType      The declaring type. Either a Class or a string representing the name of the type
     * @param fieldType          The type of the field
     * @param fieldName          The name of the field
     * @param requiresReflection Whether accessing the field requires reflection
     * @param annotationMetadata The annotation metadata for the field
     * @param typeArguments      The generic type arguments
     * @param isOptional         Is the value optional
     */
    void visitFieldValue(Object declaringType,
                         Object fieldType,
                         String fieldName,
                         boolean requiresReflection,
                         @Nullable AnnotationMetadata annotationMetadata,
                         @Nullable Map<String, Object> typeArguments,
                         boolean isOptional);

    /**
     * @return The package name of the bean
     */
    String getPackageName();

    /**
     * @return The short name of the bean
     */
    String getBeanSimpleName();

    /**
     * @return The annotation metadata
     */
    AnnotationMetadata getAnnotationMetadata();

    /**
     * Begin defining a configuration builder.
     *
     * @param type               The type of the builder
     * @param field              The name of the field that represents the builder
     * @param annotationMetadata The annotation metadata associated with the field
     * @param metadataBuilder    The {@link ConfigurationMetadataBuilder}
     * @param isInterface        Whether the builder type is an interface or not
     * @see io.micronaut.context.annotation.ConfigurationBuilder
     */
    void visitConfigBuilderField(
            Object type,
            String field,
            AnnotationMetadata annotationMetadata,
            ConfigurationMetadataBuilder metadataBuilder,
            boolean isInterface);

    /**
     * Begin defining a configuration builder.
     *
     * @param type               The type of the builder
     * @param methodName         The name of the method that returns the builder
     * @param annotationMetadata The annotation metadata associated with the field
     * @param metadataBuilder    The {@link ConfigurationMetadataBuilder}
     * @param isInterface        Whether the builder type is an interface or not
     * @see io.micronaut.context.annotation.ConfigurationBuilder
     */
    void visitConfigBuilderMethod(
            Object type,
            String methodName,
            AnnotationMetadata annotationMetadata,
            ConfigurationMetadataBuilder metadataBuilder,
            boolean isInterface);

    /**
     * Visit a configuration builder method.
     *
     * @param prefix              The prefix used for the method
     * @param returnType          The return type
     * @param methodName          The method name
     * @param paramType           The method type
     * @param generics            The generic types of the method
     * @param path                The property path
     * @see io.micronaut.context.annotation.ConfigurationBuilder
     */
    void visitConfigBuilderMethod(
            String prefix,
            Object returnType,
            String methodName,
            Object paramType,
            Map<String, Object> generics,
            String path);

    /**
     * Visit a configuration builder method that accepts a long and a TimeUnit.
     *
     * @param prefix              The prefix used for the method
     * @param returnType          The return type
     * @param methodName          The method name
     * @param path                The property path
     * @see io.micronaut.context.annotation.ConfigurationBuilder
     */
    void visitConfigBuilderDurationMethod(
            String prefix,
            Object returnType,
            String methodName,
            String path);

    /**
     * Finalize a configuration builder field.
     *
     * @see io.micronaut.context.annotation.ConfigurationBuilder
     */
    void visitConfigBuilderEnd();

    /**
     * By default, when the {@link io.micronaut.context.BeanContext} is started, the
     * {@link BeanDefinition#getExecutableMethods()} are not processed by registered
     * {@link io.micronaut.context.processor.ExecutableMethodProcessor} instances unless this method returns true.
     *
     * @return Whether the bean definition requires method processing
     * @see io.micronaut.context.annotation.Executable#processOnStartup()
     */
    default boolean requiresMethodProcessing() {
        return false;
    }

    /**
     * Sets whether the {@link BeanDefinition#requiresMethodProcessing()} returns true.
     *
     * @param shouldPreProcess True if they should be pre-processed
     */
    void setRequiresMethodProcessing(boolean shouldPreProcess);

    /**
     * Visits the type arguments for the bean.
     *
     * @param typeArguments The type arguments
     */
    void visitTypeArguments(Map<String, Map<String, Object>> typeArguments);
}
