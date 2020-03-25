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
package io.micronaut.cli.profile

import picocli.CommandLine.ParseResult

/**
 * Context for the execution of {@link io.micronaut.cli.profile.Command} instances within a {@link io.micronaut.cli.profile.Profile}
 *
 * @author Lari Hotari
 * @author Graeme Rocher
 * @since 1.0
 */
interface ExecutionContext extends ProjectContext {

    /**
     * @return The parsed command line arguments as an instance of {@link picocli.CommandLine.ParseResult}
     */
    ParseResult getParseResult()

    /**
     * Allows cancelling of the running command
     */
    void cancel()

    /**
     * Attaches a listener for cancellation events
     *
     * @param listener The {@link CommandCancellationListener}
     */
    void addCancelledListener(CommandCancellationListener listener)
}
