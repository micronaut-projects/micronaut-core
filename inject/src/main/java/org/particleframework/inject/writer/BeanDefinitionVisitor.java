/*
 * Copyright 2017 original authors
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License. 
 */
package org.particleframework.inject.writer;

import org.objectweb.asm.Type;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Interface for {@link BeanDefinitionVisitor} implementations such as {@link BeanDefinitionWriter}
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public interface BeanDefinitionVisitor {
    /**
     * Visits a no arguments constructor. Either this method or {@link #visitBeanDefinitionConstructor(Map, Map, Map)} should be called at least once
     */
    void visitBeanDefinitionConstructor();

    /**
     * @return Wetherh the provided type an interface
     */
    boolean isInterface();

    /**
     * @return Is the bean singleton
     */
    boolean isSingleton();

    /**
     * @return The scope type
     */
    Type getScope();

    /**
     * Alter the super class of this bean definition
     *
     * @param name The super type
     */
    void visitSuperType(String name);

    /**
     * Alter the super class of this bean to use another factory bean
     *
     * @param beanName The bean name
     */
    void visitSuperFactoryType(String beanName);

    /**
     * @return The full class name of the bean
     */
    String getBeanTypeName();

    /**
     * The provided type of the bean. Usually this is the same as {@link #getBeanTypeName()}, except in the case of factory beans
     * which produce a different type
     *
     * @return The provided type
     */
    Type getProvidedType();

    /**
     * Make the bean definition as validated by javax.validation
     *
     * @param validated Whether the bean definition is validated
     */
    void setValidated(boolean validated);

    /**
     * @return Return whether the bean definition is validated
     */
    boolean isValidated();

    /**
     * @return The name of the bean definition class
     */
    String getBeanDefinitionName();

    /**
     * Visits the constructor used to create the bean definition.
     *
     * @param argumentTypes  The argument type names for each parameter
     * @param qualifierTypes The qualifier type names for each parameter
     * @param genericTypes   The generic types for each parameter
     */
    void visitBeanDefinitionConstructor(Map<String, Object> argumentTypes,
                                        Map<String, Object> qualifierTypes,
                                        Map<String, Map<String, Object>> genericTypes);

    /**
     * Finalize the bean definition to the given output stream
     */
    void visitBeanDefinitionEnd();

    /**
     * Write the state of the writer to the given compilation directory
     *
     * @param compilationDir The compilation directory
     * @throws IOException If an I/O error occurs
     */
    void writeTo(File compilationDir) throws IOException;

    /**
     * Write the class to output via a visitor that manages output destination
     *
     * @param visitor the writer output visitor
     * @throws IOException If an error occurs
     */
    void accept(ClassWriterOutputVisitor visitor) throws IOException;

    /**
     * Visits an injection point for a field and setter pairing.
     *
     * @param declaringType      The declaring type
     * @param qualifierType      The qualifier type
     * @param requiresReflection Whether the setter requires reflection
     * @param fieldType          The field type
     * @param fieldName          The field name
     * @param setterName         The setter name
     * @param genericTypes       The generic types
     */
    void visitSetterInjectionPoint(Object declaringType,
                                   Object qualifierType,
                                   boolean requiresReflection,
                                   Object fieldType,
                                   String fieldName,
                                   String setterName,
                                   Map<String, Object> genericTypes);

    /**
     * Visits an injection point for a field and setter pairing.
     *
     * @param declaringType      The declaring type
     * @param qualifierType      The qualifier type
     * @param requiresReflection Whether the setter requires reflection
     * @param fieldType          The field type
     * @param fieldName          The field name
     * @param setterName         The setter name
     * @param genericTypes       The generic types
     * @param isOptional         Whether the setter is optional
     */
    void visitSetterValue(Object declaringType,
                          Object qualifierType,
                          boolean requiresReflection,
                          Object fieldType,
                          String fieldName,
                          String setterName,
                          Map<String, Object> genericTypes,
                          boolean isOptional);

    /**
     * Visits a method injection point
     *
     * @param declaringType      The declaring type of the method. Either a Class or a string representing the name of the type
     * @param requiresReflection Whether the method requires reflection
     * @param returnType         The return type of the method. Either a Class or a string representing the name of the type
     * @param methodName         The method name
     * @param argumentTypes      The argument types. Note: an ordered map should be used such as LinkedHashMap. Can be null or empty.
     * @param qualifierTypes     The qualifier types of each argument. Can be null.
     * @param genericTypes       The generic types of each argument. Can be null.
     */
    void visitPostConstructMethod(Object declaringType,
                                  boolean requiresReflection,
                                  Object returnType,
                                  String methodName,
                                  Map<String, Object> argumentTypes,
                                  Map<String, Object> qualifierTypes,
                                  Map<String, Map<String, Object>> genericTypes);

    /**
     * Visits a method injection point
     *
     * @param declaringType      The declaring type of the method. Either a Class or a string representing the name of the type
     * @param requiresReflection Whether the method requires reflection
     * @param returnType         The return type of the method. Either a Class or a string representing the name of the type
     * @param methodName         The method name
     * @param argumentTypes      The argument types. Note: an ordered map should be used such as LinkedHashMap. Can be null or empty.
     * @param qualifierTypes     The qualifier types of each argument. Can be null.
     * @param genericTypes       The generic types of each argument. Can be null.
     */
    void visitPreDestroyMethod(Object declaringType,
                               boolean requiresReflection,
                               Object returnType,
                               String methodName,
                               Map<String, Object> argumentTypes,
                               Map<String, Object> qualifierTypes,
                               Map<String, Map<String, Object>> genericTypes);

    /**
     * Visits a method injection point
     *
     * @param declaringType      The declaring type of the method. Either a Class or a string representing the name of the type
     * @param requiresReflection Whether the method requires reflection
     * @param returnType         The return type of the method. Either a Class or a string representing the name of the type
     * @param methodName         The method name
     * @param argumentTypes      The argument types. Note: an ordered map should be used such as LinkedHashMap. Can be null or empty.
     * @param qualifierTypes     The qualifier types of each argument. Can be null.
     * @param genericTypes       The generic types of each argument. Can be null.
     */
    void visitMethodInjectionPoint(Object declaringType,
                                   boolean requiresReflection,
                                   Object returnType,
                                   String methodName,
                                   Map<String, Object> argumentTypes,
                                   Map<String, Object> qualifierTypes,
                                   Map<String, Map<String, Object>> genericTypes);

    /**
     * Visit a method that is to be made executable allow invocation of said method without reflection
     *
     * @param declaringType  The declaring type of the method. Either a Class or a string representing the name of the type
     * @param returnType     The return type of the method. Either a Class or a string representing the name of the type
     * @param methodName     The method name
     * @param argumentTypes  The argument types. Note: an ordered map should be used such as LinkedHashMap. Can be null or empty.
     * @param qualifierTypes The qualifier types of each argument. Can be null.
     * @param genericTypes   The generic types of each argument. Can be null.
     *
     * @return The {@link ExecutableMethodWriter}. Calls should call {@link ExecutableMethodWriter#visitEnd()}  to finalize the method
     */
    ExecutableMethodWriter visitExecutableMethod(Object declaringType,
                               Object returnType,
                               Map<String, Object> returnTypeGenericTypes,
                               String methodName,
                               Map<String, Object> argumentTypes,
                               Map<String, Object> qualifierTypes,
                               Map<String, Map<String, Object>> genericTypes);

    /**
     * Visits a field injection point
     *
     * @param declaringType      The declaring type. Either a Class or a string representing the name of the type
     * @param qualifierType      The qualifier type. Either a Class or a string representing the name of the type
     * @param requiresReflection Whether accessing the field requires reflection
     * @param fieldType          The type of the field
     * @param fieldName          The name of the field
     */
    void visitFieldInjectionPoint(Object declaringType,
                                  Object qualifierType,
                                  boolean requiresReflection,
                                  Object fieldType,
                                  String fieldName);

    /**
     * Visits a field injection point
     *
     * @param declaringType      The declaring type. Either a Class or a string representing the name of the type
     * @param qualifierType      The qualifier type. Either a Class or a string representing the name of the type
     * @param requiresReflection Whether accessing the field requires reflection
     * @param fieldType          The type of the field
     * @param fieldName          The name of the field
     */
    void visitFieldValue(Object declaringType,
                         Object qualifierType,
                         boolean requiresReflection,
                         Object fieldType,
                         String fieldName,
                         boolean isOptional);

    /**
     * Adds a method as a source of annotations
     *
     * @param declaringType The declaring type
     * @param methodName The method name
     * @param parameters The parameter to the method
     */
    void visitMethodAnnotationSource(Object declaringType, String methodName, Map<String, Object> parameters);

    /**
     * @return The package name of the bean
     */
    String getPackageName();

    /**
     * @return The short name of the bean
     */
    String getBeanSimpleName();


}
