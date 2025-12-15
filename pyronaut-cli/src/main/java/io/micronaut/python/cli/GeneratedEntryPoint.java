/*
 * Copyright 2017-2021 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.python.cli;

import javax.tools.SimpleJavaFileObject;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;

class GeneratedEntryPoint extends SimpleJavaFileObject {

    private final Path sourceDir;

    public GeneratedEntryPoint(Path sourceDir) {
        this(sourceDir.toUri(), Kind.SOURCE);
    }

    protected GeneratedEntryPoint(URI uri, Kind kind) {
        super(uri, kind);
        this.sourceDir = Path.of(uri);
    }

    @Override
    public CharSequence getCharContent(boolean ignoreEncodingErrors) throws IOException {
        return generateMainClassSource();
    }

    private String generateMainClassSource() {
        var sb = new StringBuilder();
        sb.append("package pyronaut_application;\n\n");
        sb.append("import io.micronaut.runtime.Micronaut;\n");
        sb.append("import io.micronaut.python.processing.annotation.PythonApplication;\n\n");
        sb.append("@PythonApplication(\n");

        sb.append("    src = \"").append(sourceDir.toAbsolutePath()).append("\"");

        sb.append("\n)\n");
        sb.append("class PyronautMain {\n");
        sb.append("    public static void main(String[] args) {\n");
        sb.append("        Micronaut.run(args);\n");
        sb.append("    }\n");
        sb.append("}\n");
        return sb.toString();
    }
}
