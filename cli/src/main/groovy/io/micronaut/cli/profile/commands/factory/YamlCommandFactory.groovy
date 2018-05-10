/*
 * Copyright 2017-2018 original authors
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

package io.micronaut.cli.profile.commands.factory

import groovy.json.JsonParserType
import groovy.json.JsonSlurper
import groovy.transform.CompileStatic
import io.micronaut.cli.io.support.Resource
import io.micronaut.cli.profile.Command
import io.micronaut.cli.profile.Profile
import io.micronaut.cli.profile.commands.DefaultMultiStepCommand
import org.yaml.snakeyaml.Yaml

/**
 * A {@link CommandFactory} that can discover commands defined in YAML or JSON
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@CompileStatic
class YamlCommandFactory extends ResourceResolvingCommandFactory<Map> {
    protected Yaml yamlParser = new Yaml()
    // LAX parser for JSON: http://mrhaki.blogspot.ie/2014/08/groovy-goodness-relax-groovy-will-parse.html
    protected JsonSlurper jsonSlurper = new JsonSlurper().setType(JsonParserType.LAX)

    final Collection<String> matchingFileExtensions = ["yml", "json"]
    final String fileNamePattern = /^.*\.(yml|json)$/

    @Override
    protected Map readCommandFile(Resource resource) {
        Map data
        InputStream is

        try {
            is = resource.inputStream
            if (resource.filename.endsWith('.json')) {
                data = jsonSlurper.parse(is, "UTF-8") as Map
            } else {
                data = yamlParser.loadAs(is, Map)
            }
        } finally {
            is?.close()
        }
        return data
    }

    protected Command createCommand(Profile profile, String commandName, Resource resource, Map data) {
        if (!data.profile || profile.name == data.profile?.toString()) {
            Command command = new DefaultMultiStepCommand(commandName, profile, data)
            Object minArguments = data?.minArguments
            command.minArguments = minArguments instanceof Integer ? (Integer) minArguments : 1
            return command
        }
        return null
    }

}
