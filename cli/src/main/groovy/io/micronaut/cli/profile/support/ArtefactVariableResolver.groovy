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
package io.micronaut.cli.profile.support

import groovy.transform.CompileStatic
import io.micronaut.cli.profile.ExecutionContext
import io.micronaut.cli.profile.commands.templates.SimpleTemplate
import io.micronaut.cli.util.NameUtils

/**
 * @author Graeme Rocher
 * @since 1.0
 */
@CompileStatic
class ArtefactVariableResolver {
    /**
     * The artifact name and package
     */
    String artifactPackage, artifactName
    /**
     * The suffix used as a convention for the file
     */
    String convention
    Map<String, String> variables = [:]

    ArtefactVariableResolver(String artifactName, String artifactPackage = null) {
        this(artifactName, null, artifactPackage)
    }

    ArtefactVariableResolver(String artifactName, String convention, String artifactPackage) {
        this.artifactPackage = artifactPackage
        this.artifactName = artifactName
        this.convention = convention
        createVariables()
    }

    Map createVariables() {
        if (artifactPackage) {
            variables['artifact.package.name'] = artifactPackage
            variables['artifact.package.path'] = artifactPackage?.replace('.', '/')
            variables['artifact.package'] = "package $artifactPackage\n".toString()
        }
        if (convention && artifactName.endsWith(convention)) {
            artifactName = artifactName.substring(0, artifactName.length() - convention.length())
        }
        variables['artifact.name'] = artifactName
        variables['artifact.propertyName'] = NameUtils.getPropertyName(artifactName)
        return variables
    }

    File resolveFile(String pathToResolve, ExecutionContext context) {
        String destinationName = new SimpleTemplate(pathToResolve).render(variables)
        File destination = new File(context.baseDir, destinationName).absoluteFile

        if (!destination.getParentFile().exists()) {
            destination.getParentFile().mkdirs()
        }
        return destination
    }
}
