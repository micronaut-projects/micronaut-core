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
import io.micronaut.core.util.Toggleable;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.ast.*;
import io.micronaut.inject.configuration.ConfigurationMetadataBuilder;
import io.micronaut.inject.visitor.VisitorContext;
import org.objectweb.asm.Type;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;

/**
 * Interface for {@link BeanDefinitionVisitor} implementations such as {@link BeanDefinitionWriter}.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public interface BeanDefinitionVisitor extends OriginatingElements, Toggleable {

    /**
     * The suffix use for generated AOP intercepted types.
     */
    String PROXY_SUFFIX = "$Intercepted";

    /**
     * @return The element where the bean definition originated from.
     */
    @Nullable
    Element getOriginatingElement();


    /**
     * <p>In the case where the produced class is produced by a factory method annotated with
     * {@link io.micronaut.context.annotation.Bean} this method should be called.</p>
     *
     * @param factoryClass  The factory class
     * @param factoryMethod The factory method
     */
    void visitBeanFactoryMethod(ClassElement factoryClass,
                                MethodElement factoryMethod);

    /**
     * <p>In the case where the produced class is produced by a factory method annotated with
     * {@link io.micronaut.context.annotation.Bean} this method should be called.</p>
     *
     * @param factoryClass  The factory class
     * @param factoryMethod The factory method
     * @param parameters    The parameters
     */
    void visitBeanFactoryMethod(ClassElement factoryClass,
                                MethodElement factoryMethod,
                                ParameterElement[] parameters);

    /**
     * <p>In the case where the produced class is produced by a factory field annotated with
     * {@link io.micronaut.context.annotation.Bean} this method should be called.</p>
     *
     * @param factoryClass The factory class
     * @param factoryField The factory field
     */
    void visitBeanFactoryField(ClassElement factoryClass, FieldElement factoryField);

    /**
     * Visits the constructor used to create the bean definition.
     *
     * @param constructor        The method element that represents the constructor
     * @param requiresReflection Whether invoking the constructor requires reflection
     * @param visitorContext     The visitor context
     */
    void visitBeanDefinitionConstructor(MethodElement constructor,
                                        boolean requiresReflection,
                                        VisitorContext visitorContext);

    /**
     * Visits the constructor used to create the bean definition in the case where no constructor is present.
     * This method should only be called in the class defines no constructor.
     *
     * @param annotationMetadata The annotation metadata for the constructor
     * @param visitorContext     The visitor context
     */
    void visitDefaultConstructor(
            AnnotationMetadata annotationMetadata,
            VisitorContext visitorContext
    );

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
     * Sets the name of the intercepted type.
     *
     * @param typeName The type name
     */
    void setInterceptedType(String typeName);

    /**
     * @return The intercepted type
     */
    Optional<Type> getInterceptedType();

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
     * Visits an injection point for a setter.
     *
     * @param declaringType      The declaring type
     * @param methodElement      The method element
     * @param annotationMetadata The annotationMetadata
     * @param requiresReflection Whether the setter requires reflection
     * @param isOptional         Whether the setter is optional
     */
    void visitSetterValue(TypedElement declaringType,
                          MethodElement methodElement,
                          AnnotationMetadata annotationMetadata,
                          boolean requiresReflection,
                          boolean isOptional);

    /**
     * Visits a method injection point.
     *  @param declaringType      The declaring type of the method. Either a Class or a string representing
     *                           the name of the type
     * @param methodElement      The method element
     * @param requiresReflection Whether the method requires reflection
     * @param visitorContext     The visitor context
     */
    void visitPostConstructMethod(TypedElement declaringType,
                                  MethodElement methodElement,
                                  boolean requiresReflection,
                                  VisitorContext visitorContext);

    /**
     * Visits a method injection point.
     *
     * @param beanType           The bean type of the method
     * @param methodElement      The method element
     * @param requiresReflection Whether the method requires reflection
     * @param visitorContext     The visitor context
     */
    void visitPreDestroyMethod(TypedElement beanType,
                               MethodElement methodElement,
                               boolean requiresReflection,
                               VisitorContext visitorContext);

    /**
     * Visits a method injection point.
     *
     * @param beanType           The bean type of the method
     * @param methodElement      The method element
     * @param requiresReflection Whether the method requires reflection
     * @param visitorContext     The visitor context
     */
    void visitMethodInjectionPoint(TypedElement beanType,
                                   MethodElement methodElement,
                                   boolean requiresReflection,
                                   VisitorContext visitorContext);

    /**
     * Visit a method that is to be made executable allow invocation of said method without reflection.
     *
     * @param declaringBean  The declaring bean of the method. Note this may differ from {@link MethodElement#getDeclaringType()} in the case of the method coming from a super class or interface.
     * @param methodElement  The method element
     * @param visitorContext The visitor context
     * @return The index of a new method
     */
    int visitExecutableMethod(TypedElement declaringBean,
                                                 MethodElement methodElement,
                                                 VisitorContext visitorContext);

    /**
     * Visits a field injection point.
     *
     * @param declaringType      The declaring type. Either a Class or a string representing the name of the type
     * @param fieldElement       The field element
     * @param requiresReflection Whether accessing the field requires reflection
     */
    void visitFieldInjectionPoint(TypedElement declaringType,
                                  FieldElement fieldElement,
                                  boolean requiresReflection);

    /**
     * Visits an annotation injection point.
     *
     * @param annotationMemberBeanType     The type of the injected bean
     * @param annotationMemberProperty       Required property of the injected bean
     * @param requiredValue      Required value of the bean property for the bean to be loaded
     * @param notEqualsValue      The bean property value which should not be equal to present value for the bean to
     *                           be loaded
     */
    void visitAnnotationMemberPropertyInjectionPoint(TypedElement annotationMemberBeanType,
                                                     String annotationMemberProperty,
                                                     @Nullable String requiredValue,
                                                     @Nullable String notEqualsValue);

    /**
     * Visits a field injection point.
     *
     * @param declaringType      The declaring type. Either a Class or a string representing the name of the type
     * @param fieldElement       The field element
     * @param isOptional         Is the value optional
     * @param requiresReflection Whether accessing the field requires reflection
     */
    void visitFieldValue(TypedElement declaringType,
                         FieldElement fieldElement,
                         boolean isOptional, boolean requiresReflection);

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
            ClassElement type,
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
            ClassElement type,
            String methodName,
            AnnotationMetadata annotationMetadata,
            ConfigurationMetadataBuilder metadataBuilder,
            boolean isInterface);

    /**
     * Visit a configuration builder method.
     *
     * @param propertyName The property name
     * @param returnType   The return type
     * @param methodName   The method name
     * @param paramType    The method type
     * @param generics     The generic types of the method
     * @param path         The property path
     * @see io.micronaut.context.annotation.ConfigurationBuilder
     */
    void visitConfigBuilderMethod(String propertyName,
                                  ClassElement returnType,
                                  String methodName,
                                  @Nullable ClassElement paramType,
                                  Map<String, ClassElement> generics,
                                  String path);

    /**
     * Visit a configuration builder method that accepts a long and a TimeUnit.
     *
     * @param propertyName The property name
     * @param returnType   The return type
     * @param methodName   The method name
     * @param path         The property path
     * @see io.micronaut.context.annotation.ConfigurationBuilder
     */
    void visitConfigBuilderDurationMethod(String propertyName,
                                          ClassElement returnType,
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
    void visitTypeArguments(Map<String, Map<String, ClassElement>> typeArguments);

    /**
     * @return The generic type arguments for the bean type.
     * @since 3.0.0
     */
    default @NonNull ClassElement[] getTypeArguments() {
        return new ClassElement[0];
    }
}
