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
package io.micronaut.context.env.groovy

import groovy.transform.CompileStatic
import org.codehaus.groovy.ast.ASTNode
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.MethodNode
import org.codehaus.groovy.ast.Parameter
import org.codehaus.groovy.control.CompilePhase
import org.codehaus.groovy.control.CompilerConfiguration
import org.codehaus.groovy.control.SourceUnit
import org.codehaus.groovy.control.customizers.ASTTransformationCustomizer
import org.codehaus.groovy.transform.ASTTransformation
import org.codehaus.groovy.transform.GroovyASTTransformation

/**
 * Evaluates type safe configurations converting property paths to calls to setProperty(..) and returning a map of
 * all assigned configuration values
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@CompileStatic
class ConfigurationEvaluator {

    /**
     * Evaluate the configuration from the given input stream
     * @param inputStream The input stream
     * @return The configuration values
     */
    Map<String, Object> evaluate(InputStream inputStream) {
        Reader reader = new InputStreamReader(inputStream, "UTF-8")
        evaluate(reader)
    }

    /**
     * Evaluate the configuration from the given string
     *
     * @param string The string
     * @return The configuration values
     */
    Map<String, Object> evaluate(String string) {
        evaluate(new StringReader(string))
    }

    /**
     * Evaluates the configuration from the given reader
     * @param reader The reader
     * @return The configuration values
     */
    Map<String, Object> evaluate(Reader reader) {
        CompilerConfiguration configuration = new CompilerConfiguration()
        configuration.addCompilationCustomizers(
            new ASTTransformationCustomizer(CompileStatic.class),
            new ASTTransformationCustomizer(new ConfigTransform())
        )

        GroovyShell shell = new GroovyShell(configuration)
        Script script = shell.parse(reader)
        script.run()
        return script.getBinding().getVariables()
    }

    @GroovyASTTransformation(phase = CompilePhase.CANONICALIZATION)
    private static class ConfigTransform implements ASTTransformation {
        @Override
        void visit(ASTNode[] nodes, SourceUnit source) {
            List<ClassNode> classNodes = source.getAST().classes
            ClassNode scriptClassNode = classNodes.find { it.script }
            if (scriptClassNode != null) {
                MethodNode runMethod = scriptClassNode.getMethod('run', [] as Parameter[])
                if (runMethod != null) {
                    new SetPropertyTransformer(source).visitMethod(runMethod)
                }
            }
        }
    }
}
