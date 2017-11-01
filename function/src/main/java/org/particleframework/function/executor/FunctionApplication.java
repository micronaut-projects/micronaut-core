/*
 * Copyright 2017 original authors
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
package org.particleframework.function.executor;

import org.particleframework.core.cli.CommandLine;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

/**
 * Allows executing functions from the CLI
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class FunctionApplication extends StreamFunctionExecutor {

    public static void main(String...args) {
        CommandLine commandLine = CommandLine.build()
                .addOption("d", "For passing the data")
                .addOption("x", "For passing the data")
                .parse(args);


        Object value = commandLine.optionValue("d");
        if(value != null) {
            ByteArrayInputStream input = new ByteArrayInputStream(value.toString().getBytes(StandardCharsets.UTF_8));
            try {
                new FunctionApplication().execute(input, System.out);
            } catch (Exception e) {
                System.err.println("Error executing function. Use -x for more information: " + e.getMessage());
                if(commandLine.hasOption("x")) {
                    e.printStackTrace(System.err);
                }
                System.exit(1);
            }
        }
        else {
            System.err.println("No data specified. Use -d to specify the data");
            System.exit(1);
        }
    }
}
