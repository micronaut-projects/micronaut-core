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
package io.micronaut.function.executor;

import io.micronaut.core.cli.CommandLine;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.function.BiConsumer;

/**
 * Allows executing functions from the CLI.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class FunctionApplication extends StreamFunctionExecutor {

    /**
     * The data option.
     */
    public static final String DATA_OPTION = "d";

    /**
     * The debug option.
     */
    public static final String DEBUG_OPTIONS = "x";

    /**
     * The main method which is the entry point.
     *
     * @param args The arguments
     */
    public static void main(String... args) {
        FunctionApplication functionApplication = new FunctionApplication();
        run(functionApplication, args);
    }

    /**
     * Run the given {@link StreamFunctionExecutor} for the given arguments.
     *
     * @param functionExecutor The function executor
     * @param args             The arguments
     */
    public static void run(StreamFunctionExecutor functionExecutor, String... args) {
        parseData(args, (data, isDebug) -> {
            try (InputStream input = data != null ? new ByteArrayInputStream(data.getBytes(StandardCharsets.UTF_8)) : System.in) {
                functionExecutor.execute(input, System.out);
            } catch (Exception e) {
                exitWithError(isDebug, e);
            }
        });
    }

    /**
     * Exit and print an error message if debug flag set.
     *
     * @param isDebug flag for print error
     * @param e exception passed in
     */
    static void exitWithError(Boolean isDebug, Exception e) {
        System.err.println("Error executing function (Use -x for more information): " + e.getMessage());
        if (isDebug) {
            System.err.println();
            System.err.println("Error Detail");
            System.err.println("------------");
            e.printStackTrace(System.err);
        }
        System.exit(1);
    }

    /**
     * Parse entries.
     *
     * @param args command line options
     * @param data data
     */
    static void parseData(String[] args, BiConsumer<String, Boolean> data) {
        CommandLine commandLine = parseCommandLine(args);
        Object value = commandLine.optionValue("d");
        if (value != null) {
            data.accept(value.toString(), commandLine.hasOption("x"));
        } else {
            data.accept(null, commandLine.hasOption("x"));
        }
    }

    /**
     * Exit.
     */
    static void exitWithNoData() {
        System.err.println("No data specified. Use -d to specify the data");
        System.exit(1);
    }

    /**
     * Parse command line entries.
     *
     * @param args command line options
     * @return command line built with options
     */
    static CommandLine parseCommandLine(String[] args) {
        return CommandLine.build()
            .addOption(DATA_OPTION, "For passing the data")
            .addOption(DEBUG_OPTIONS, "For outputting debug information")
            .parse(args);
    }
}
