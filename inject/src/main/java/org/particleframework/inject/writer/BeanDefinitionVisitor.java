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
     * Visits the constructor used to create the bean definition.
     *
     * @param argumentTypes  The argument type names for each parameter
     * @param qualifierTypes The qualifier type names for each parameter
     * @param genericTypes   The generic types for each parameter
     */
    void visitBeanDefinitionConstructor(Map<String, Object> argumentTypes,
                                        Map<String, Object> qualifierTypes,
                                        Map<String, List<Object>> genericTypes);

    /**
     * Finalize the bean definition to the given output stream
     */
    void visitBeanDefinitionEnd();

    void writeTo(File compilationDir) throws IOException;

    /**
     * Write the class to output via a visitor that manages output destination
     *
     * @param visitor the writer output visitor
     * @throws IOException If an error occurs
     */
    void accept(ClassWriterOutputVisitor visitor) throws IOException;
}
