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

package io.micronaut.cli.profile.commands

import groovy.transform.CompileDynamic
import io.micronaut.cli.console.logging.MicronautConsole
import io.micronaut.cli.profile.AbstractStep
import io.micronaut.cli.profile.CommandDescription
import io.micronaut.cli.profile.MultiStepCommand
import io.micronaut.cli.profile.Profile
import io.micronaut.cli.profile.Step
import io.micronaut.cli.profile.steps.StepRegistry
import jline.console.completer.Completer

/**
 * Simple implementation of the {@link MultiStepCommand} abstract class that parses commands defined in YAML or JSON
 *
 * @author Lari Hotari
 * @author Graeme Rocher
 * @since 1.0
 */
class DefaultMultiStepCommand extends MultiStepCommand {
    private Map<String, Object> data
    private List<AbstractStep> steps

    final CommandDescription description

    DefaultMultiStepCommand(String name, Profile profile, Map<String, Object> data) {
        super(name, profile)
        this.data = data

        def description = data?.description
        if (description instanceof List) {
            List descList = (List) description
            if (descList) {

                this.description = new CommandDescription(name: name, description: descList.get(0).toString(), usage: data?.usage)

                if (descList.size() > 1) {
                    for (arg in descList[1..-1]) {
                        if (arg instanceof Map) {
                            Map map = (Map) arg
                            if (map.containsKey('usage')) {
                                this.description.usage = map.get('usage')?.toString()
                            } else if (map.containsKey('completer')) {
                                def completerClass = map.get('completer')
                                if (completerClass) {
                                    try {
                                        this.description.completer = (Completer) Thread.currentThread().contextClassLoader.loadClass(completerClass.toString()).newInstance()
                                    } catch (e) {
                                        // ignore
                                    }
                                }
                            } else {
                                handleArgumentOrFlag(map, 'argument')
                                handleArgumentOrFlag(map, 'flag')
                            }
                        }
                    }
                }
            }
        } else {
            this.description = new CommandDescription(name: name, description: description.toString(), usage: data?.usage)
        }
    }

    @CompileDynamic
    boolean handleArgumentOrFlag(Map map, String name) {
        try {
            if (map.containsKey(name)) {
                def argName = map.remove(name)
                map.put('name', argName)
                this.description."$name"(map)
                return true
            }
        } catch (Throwable e) {
            MicronautConsole.getInstance().error("Invalid $name found in [$profile.name] profile ${map}: ${e.message}", e)
        }
        return false
    }

    List<Step> getSteps() {
        if (steps == null) {
            steps = []
            data.steps?.each {
                Map<String, Object> stepParameters = it.collectEntries { k, v -> [k as String, v] }
                AbstractStep step = createStep(stepParameters)
                if (step != null) {
                    steps.add(step)
                }
            }
        }
        steps
    }

    protected AbstractStep createStep(Map stepParameters) {
        StepRegistry.getStep(stepParameters.command?.toString(), this, stepParameters)
    }

}
