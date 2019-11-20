/*
 * Copyright 2017-2019 original authors
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
import io.micronaut.ast.groovy.utils.AstAnnotationUtils
import io.micronaut.ast.groovy.utils.AstGenericUtils
import io.micronaut.context.annotation.ConfigurationReader
import io.micronaut.context.annotation.EachProperty
import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.core.util.StringUtils
import io.micronaut.inject.configuration.ConfigurationMetadataBuilder
import org.codehaus.groovy.ast.ClassHelper
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.InnerClassNode
import org.codehaus.groovy.control.CompilationUnit
import org.codehaus.groovy.control.SourceUnit

import java.util.function.Function

/**
 * Implementation of ConfigurationMetadataBuilder for Groovy
 *
 * @author graemerocher
 * @since 1.0
 */
@CompileStatic
class GroovyConfigurationMetadataBuilder extends ConfigurationMetadataBuilder<ClassNode> {

    final SourceUnit sourceUnit
    final CompilationUnit compilationUnit

    GroovyConfigurationMetadataBuilder(SourceUnit sourceUnit, CompilationUnit compilationUnit) {
        this.compilationUnit = compilationUnit
        this.sourceUnit = sourceUnit
    }

    @Override
    protected String buildPropertyPath(ClassNode owningType, ClassNode declaringType, String propertyName) {
        String value = buildTypePath(owningType, declaringType)
        return value + '.' + propertyName
    }

    @Override
    protected String buildTypePath(ClassNode owningType, ClassNode declaringType) {
        StringBuilder path = new StringBuilder(calculateInitialPath(owningType, declaringType))

        prependSuperclasses(declaringType, path)
        while (declaringType != null && declaringType instanceof InnerClassNode) {
            // we have an inner class, so prepend inner class
            declaringType = ((InnerClassNode) declaringType).getOuterClass()
            if (declaringType != null) {

                AnnotationMetadata parentMetadata = AstAnnotationUtils.getAnnotationMetadata(sourceUnit, compilationUnit, declaringType)
                Optional<String> parentConfig = parentMetadata.getValue(ConfigurationReader.class, String.class)
                if (parentConfig.isPresent()) {
                    String parentPath = parentConfig.get()
                    if(parentMetadata.hasDeclaredAnnotation(EachProperty)) {
                        path.insert(0, parentPath + ".*.")
                    }
                    else {

                        path.insert(0, parentPath + '.')
                    }
                    prependSuperclasses(declaringType, path)
                } else {
                    break
                }

            }

        }
        return path.toString()
    }

    private String calculateInitialPath(ClassNode owningType, ClassNode declaringType) {
        AnnotationMetadata annotationMetadata = AstAnnotationUtils.getAnnotationMetadata(sourceUnit, compilationUnit, declaringType)
        return annotationMetadata.stringValue(ConfigurationReader.class)
                .map(pathEvaluationFunction(annotationMetadata)).orElseGet( {->
            AnnotationMetadata ownerMetadata = AstAnnotationUtils.getAnnotationMetadata(sourceUnit, compilationUnit, owningType)
            return ownerMetadata.stringValue(ConfigurationReader.class)
                                .map(pathEvaluationFunction(ownerMetadata)).orElseThrow({ ->
                new IllegalStateException("Non @ConfigurationProperties type visited")
            })
        })
    }

    private Function<String, String> pathEvaluationFunction(AnnotationMetadata annotationMetadata) {
        return { String path ->
            if (annotationMetadata.hasDeclaredAnnotation(EachProperty.class)) {
                return path + ".*"
            }
            String prefix = annotationMetadata.getValue(ConfigurationReader.class, "prefix", String.class)
                    .orElse(null)
            if (StringUtils.isNotEmpty(prefix)) {
                if (StringUtils.isEmpty(path)) {
                    return prefix
                } else {
                    return prefix + "." + path
                }
            } else {
                return path
            }
        } as Function<String, String>
    }

    @Override
    protected String getTypeString(ClassNode type) {
        return AstGenericUtils.resolveTypeReference(type)
    }

    private void prependSuperclasses(ClassNode declaringType, StringBuilder path) {
        ClassNode superclass = declaringType.getSuperClass()
        while (superclass != ClassHelper.OBJECT_TYPE) {
            Optional<String> parentConfig = AstAnnotationUtils.getAnnotationMetadata(sourceUnit, compilationUnit, superclass).stringValue(ConfigurationReader.class)
            if (parentConfig.isPresent()) {
                path.insert(0, parentConfig.get() + '.')
                superclass = superclass.getSuperClass()
            } else {
                break
            }
        }
    }
}
