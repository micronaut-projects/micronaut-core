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
package io.micronaut.cli.console.logging;

import java.io.PrintStream;

/**
 * Used to replace default System.err with one that routes calls through MicronautConsole.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class ConsoleErrorPrintStream extends PrintStream {

    /**
     * @param out The print stream
     */
    public ConsoleErrorPrintStream(PrintStream out) {
        super(out, true);
    }

    /**
     * @return The print stream
     */
    public PrintStream getTargetOut() {
        return (PrintStream) out;
    }

    @Override
    public void print(Object o) {
        if (o != null) {
            MicronautConsole.getInstance().error(o.toString());
        }
    }

    @Override
    public void print(String s) {
        MicronautConsole.getInstance().error(s);
    }

    @Override
    public void println(String s) {
        MicronautConsole.getInstance().error(s);
    }

    @Override
    public void println(Object o) {
        if (o != null) {
            MicronautConsole.getInstance().error(o.toString());
        }
    }
}
