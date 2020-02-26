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
package io.micronaut.cli.interactive.completers

import jline.console.completer.FileNameCompleter

/**
 * JLine Completor that does file path matching like FileNameCompletor,
 * but in addition it escapes whitespace in completions with the '\'
 * character.
 *
 * @author Peter Ledbrook
 * @since 1.0
 */
class EscapingFileNameCompletor extends FileNameCompleter {
    /**
     * <p>Gets FileNameCompletor to create a list of candidates and then
     * inserts '\' before any whitespace characters in each of the candidates.
     * If a candidate ends in a whitespace character, then that is <em>not</em>
     * escaped.</p>
     */
    int complete(String buffer, int cursor, List<CharSequence> candidates) {
        int retval = super.complete(buffer, cursor, candidates)

        int count = candidates.size()
        for (int i = 0; i < count; i++) {
            candidates[i] = candidates[i].replaceAll(/(\s)(?!$)/, '\\\\$1')
        }

        return retval
    }
}
