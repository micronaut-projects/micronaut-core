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

import java.util.regex.Pattern

/**
 * JLine Completor that accepts a string if it matches a given regular
 * expression pattern.
 *
 * @author Peter Ledbrook
 * @since 1.0
 */
class RegexCompletor implements Completer {
    Pattern pattern

    RegexCompletor(String pattern) {
        this(Pattern.compile(pattern))
    }

    RegexCompletor(Pattern pattern) {
        this.pattern = pattern
    }

    /**
     * <p>Check whether the whole buffer matches the configured pattern.
     * If it does, the buffer is added to the <tt>candidates</tt> list
     * (which indicates acceptance of the buffer string) and returns 0,
     * i.e. the start of the buffer. This mimics the behaviour of SimpleCompletor.
     * </p>
     * <p>If the buffer doesn't match the configured pattern, this returns
     * -1 and the <tt>candidates</tt> list is left empty.</p>
     */
    int complete(String buffer, int cursor, List candidates) {
        if (buffer ==~ pattern) {
            candidates << buffer
            return 0
        } else {
            return -1
        }
    }
}
