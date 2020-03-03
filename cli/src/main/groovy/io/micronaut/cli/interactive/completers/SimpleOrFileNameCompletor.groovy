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

import jline.console.completer.Completer
import jline.console.completer.StringsCompleter

/**
 * JLine Completor that mixes a fixed set of options with file path matches.
 * Fixed options that match will appear first, followed by file path matches.
 *
 * @author Peter Ledbrook
 * @since 1.0
 */
class SimpleOrFileNameCompletor implements Completer {
    private simpleCompletor
    private fileNameCompletor

    SimpleOrFileNameCompletor(List fixedOptions) {
        this(fixedOptions as String[])
    }

    SimpleOrFileNameCompletor(String[] fixedOptions) {
        simpleCompletor = new StringsCompleter(fixedOptions)
        fileNameCompletor = new EscapingFileNameCompletor()
    }

    int complete(String buffer, int cursor, List candidates) {
        // Try the simple completor first...
        def retval = simpleCompletor.complete(buffer, cursor, candidates)

        // ...and then the file path completor. By using the given candidate
        // list with both completors we aggregate the results automatically.
        def fileRetval = fileNameCompletor.complete(buffer, cursor, candidates)

        // If the simple completor has matched, we return its value, otherwise
        // we return whatever the file path matcher returned. This ensures that
        // both simple completor and file path completor candidates appear
        // correctly in the command prompt. If neither competors have matches,
        // we of course return -1.
        if (retval == -1) {
            retval = fileRetval
        }
        return candidates ? retval : -1
    }
}
