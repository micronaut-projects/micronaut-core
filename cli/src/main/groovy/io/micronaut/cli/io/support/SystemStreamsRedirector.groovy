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
package io.micronaut.cli.io.support

import groovy.transform.CompileStatic
import groovy.transform.ToString

/**
 * @author James Kleeh
 * @since 1.0
 */
@ToString
@CompileStatic
class SystemStreamsRedirector {
    private static final SystemStreamsRedirector original = current()
    final InputStream input
    final PrintStream out
    final PrintStream err

    private SystemStreamsRedirector(InputStream input, PrintStream out, PrintStream err) {
        this.input = input
        this.out = out
        this.err = err
    }

    static SystemStreamsRedirector create(InputStream input, PrintStream out, PrintStream err) {
        new SystemStreamsRedirector(input, out, err)
    }

    static SystemStreamsRedirector current() {
        create(System.in, System.out, System.err)
    }

    static SystemStreamsRedirector original() {
        original
    }

    static <T> T withOriginalIO(Closure<T> closure) {
        original.withRedirectedIO(closure)
    }

    public <T> T withRedirectedIO(Closure<T> closure) {
        SystemStreamsRedirector previous = redirect()
        try {
            return closure.call()
        } finally {
            previous.redirect()
        }
    }

    SystemStreamsRedirector redirect() {
        InputStream prevInput = null
        PrintStream prevOut = null
        PrintStream prevErr = null
        if (input != null) {
            prevInput = System.in
            if (!(prevInput.is(input))) {
                System.setIn(input)
            }
        }
        if (out != null) {
            prevOut = System.out
            if (!(prevOut.is(out))) {
                System.setOut(out)
            }
        }
        if (err != null) {
            prevErr = System.err
            if (!(prevErr.is(err))) {
                System.setErr(err)
            }
        }
        new SystemStreamsRedirector(prevInput, prevOut, prevErr)
    }
}
