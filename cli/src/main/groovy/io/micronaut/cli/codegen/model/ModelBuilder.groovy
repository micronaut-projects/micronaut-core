/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.cli.codegen.model

import groovy.transform.CompileStatic
import io.micronaut.cli.io.support.FileSystemResource
import io.micronaut.cli.io.support.Resource
import io.micronaut.cli.io.support.ResourceUtils
import io.micronaut.cli.util.NameUtils
import org.codehaus.groovy.runtime.MetaClassHelper

/**
 * Used to build a Model for the purposes of codegen
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@CompileStatic
trait ModelBuilder {

    String defaultPackage

    /**
     * A model for the given class name
     * @param className The class name
     *
     * @return The {@link Model} instance
     */
    Model model(Class cls) {
        return model(cls.getName())
    }
    /**
     * A model for the given class name
     * @param className The class name
     *
     * @return The {@link Model} instance
     */
    Model model(String className) {
        if (className.contains('-')) {
            className = NameUtils.getNameFromScript(className)
        }
        if (defaultPackage && !className.contains('.')) {
            return new ModelImpl("${defaultPackage}.$className")
        } else {
            return new ModelImpl(className)
        }
    }

    /**
     * A model for the given class name
     * @param className The class name
     *
     * @return The {@link Model} instance
     */
    Model model(File file) {
        model(new FileSystemResource(file))
    }

    /**
     * A model for the given class name
     * @param className The class name
     *
     * @return The {@link Model} instance
     */
    Model model(Resource resource) {
        def className = ResourceUtils.getClassName(resource)
        model(className)
    }

    @CompileStatic
    private static class ModelImpl implements Model {
        final String className
        final String fullName
        final String propertyName
        final String packageName
        final String simpleName
        final String lowerCaseName
        final String packagePath

        ModelImpl(String className) {
            this.className = MetaClassHelper.capitalize(NameUtils.getShortName(className))
            this.fullName = className
            this.propertyName = NameUtils.getPropertyName(className)
            this.packageName = NameUtils.getPackageName(className)
            this.packagePath = packageName.replace('.' as char, File.separatorChar)
            this.simpleName = this.className
            this.lowerCaseName = NameUtils.getScriptName(className)
        }


        ModelImpl(String className, String convention) {
            className = this.convention(className, convention)

            this.className = MetaClassHelper.capitalize(NameUtils.getShortName(className))
            this.fullName = className
            this.propertyName = trimConvention(NameUtils.getPropertyName(className), convention)
            this.packageName = NameUtils.getPackageName(className)
            this.packagePath = packageName.replace('.' as char, File.separatorChar)
            this.simpleName = this.className
            this.lowerCaseName = NameUtils.getScriptName(className)

        }

        @Override
        String getModelName() {
            propertyName
        }

        @Override
        String convention(String name, String conventionName) {
            if (name.endsWith(conventionName)) {
                name
            } else {
                "${name}${conventionName}"
            }
        }

        Model forConvention(String convention) {
            return new ModelImpl(fullName, convention)
        }

        @Override
        String trimConvention(String name, String conventionName) {
            if (name.endsWith(conventionName)) {
                int end = name.lastIndexOf(conventionName)
                name.substring(0, end)
            } else {
                "${name}"
            }
        }

        @Override
        Map<String, ?> asMap() {
            [className: className, fullName: fullName, propertyName: propertyName, modelName: propertyName, packageName: packageName, packagePath: packagePath, simpleName: simpleName, lowerCaseName: lowerCaseName]
        }
    }

}
