/*
 * Copyright 2018 original authors
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
package io.micronaut.ast.groovy.config

import groovy.transform.CompileStatic
import io.micronaut.context.annotation.ConfigurationReader
import io.micronaut.inject.configuration.ConfigurationMetadataBuilder
import org.codehaus.groovy.ast.ClassHelper
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.InnerClassNode
import io.micronaut.ast.groovy.utils.AstAnnotationUtils
import io.micronaut.ast.groovy.utils.AstGenericUtils
import io.micronaut.context.annotation.ConfigurationReader
import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.inject.configuration.ConfigurationMetadataBuilder



/**
 * Implementation of ConfigurationMetadataBuilder for Groovy
 *
 * @author graemerocher
 * @since 1.0
 */
@CompileStatic
class GroovyConfigurationMetadataBuilder extends ConfigurationMetadataBuilder<ClassNode> {
    private final Map<ClassNode, String> typePaths = new HashMap<>()

    @Override
    protected String buildPropertyPath(ClassNode declaringType, String propertyName) {
        String value = buildTypePath(declaringType)
        return value + '.' + propertyName
    }

    @Override
    protected String buildTypePath(ClassNode declaringType) {
        return typePaths.computeIfAbsent(declaringType, { s ->
            AnnotationMetadata annotationMetadata = AstAnnotationUtils.getAnnotationMetadata(declaringType)
            StringBuilder path = new StringBuilder(annotationMetadata.getValue(ConfigurationReader.class, String.class).orElseThrow({
                ->
                new IllegalStateException("@ConfigurationProperties found with no value for type: " + declaringType.name)
            }))

            prependSuperclasses(declaringType, path)
            while( declaringType != null && declaringType instanceof  InnerClassNode ) {
                // we have an inner class, so prepend inner class
                declaringType = ((InnerClassNode)declaringType).getOuterClass()
                if(declaringType != null) {

                        Optional<String> parentConfig = AstAnnotationUtils.getAnnotationMetadata(declaringType).getValue(ConfigurationReader.class, String.class)
                        if(parentConfig.isPresent()) {
                            path.insert(0, parentConfig.get() + '.')
                            prependSuperclasses(declaringType, path)
                        }
                        else {
                            break
                        }

                }

            }
            return path.toString()
        })
    }

    private void prependSuperclasses(ClassNode declaringType, StringBuilder path) {
        ClassNode superclass = declaringType.getSuperClass()
        while(superclass != ClassHelper.OBJECT_TYPE) {
            Optional<String> parentConfig = AstAnnotationUtils.getAnnotationMetadata(superclass).getValue(ConfigurationReader.class, String.class)
            if(parentConfig.isPresent()) {
                path.insert(0, parentConfig.get() + '.')
                superclass = declaringType.getSuperClass()
            }
            else {
                break
            }
        }
    }
    @Override
    protected String getTypeString(ClassNode type) {
        return AstGenericUtils.resolveTypeReference(type)
    }
}
