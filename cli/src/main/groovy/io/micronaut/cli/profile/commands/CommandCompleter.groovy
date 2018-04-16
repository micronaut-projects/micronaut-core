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

import jline.console.completer.Completer
import io.micronaut.cli.profile.Command

/**
 * A completer for commands
 *
 * @author Graeme Rocher
 * @since 3.1
 */
class CommandCompleter implements Completer {

    Collection<Command> commands

    CommandCompleter(Collection<Command> commands) {
        this.commands = commands
    }

    @Override
    int complete(String buffer, int cursor, List<CharSequence> candidates) {
        def cmd = commands.find() {
            def trimmed = buffer.trim()
            if(trimmed.split(/\s/).size() > 1) {
                return trimmed.startsWith( it.name )
            }
            else {
                return trimmed == it.name
            }
        }
        if(cmd instanceof Completer) {
            return ((Completer)cmd).complete(buffer, cursor, candidates)
        }
        return cursor
    }
}
