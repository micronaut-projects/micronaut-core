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
package io.micronaut.cli.profile

/**
 * Represents a step within a {@link Command}. Commands are made up of 1 or many steps.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
interface Step {

    /**
     * @return The name of the step
     */
    String getName()
    /**
     * @return The parameters to the step
     */
    Map<String, Object> getParameters()

    /**
     * @return The command that this step is part of
     */
    Command getCommand()

    /**
     * Handles the command logic
     *
     * @param context The {@link ExecutionContext} instead
     *
     * @return True if the command should proceed to the next step, false otherwise
     */
    boolean handle(ExecutionContext context)

}