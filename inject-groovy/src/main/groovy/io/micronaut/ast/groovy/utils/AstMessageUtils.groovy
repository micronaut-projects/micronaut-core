/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.ast.groovy.utils

import groovy.transform.CompileStatic
import org.codehaus.groovy.ast.ASTNode
import org.codehaus.groovy.control.Janitor
import org.codehaus.groovy.control.SourceUnit
import org.codehaus.groovy.control.messages.SimpleMessage
import org.codehaus.groovy.control.messages.SyntaxErrorMessage
import org.codehaus.groovy.syntax.SyntaxException

/**
 * Utility methods for producing error messages in AST nodes
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@CompileStatic
class AstMessageUtils {

    /**
     * Output a warning
     *
     * @param sourceUnit The source unit
     * @param node The AST node
     * @param message The message
     */
    static void warning(final SourceUnit sourceUnit, final ASTNode node, final String message) {
        final String sample = sourceUnit.getSample(node.getLineNumber(), node.getColumnNumber(), new Janitor())
        System.err.println("""WARNING: $message

$sample""")
    }

    static void error(SourceUnit sourceUnit, ASTNode expr, String errorMessage) {
        if (expr == null) {
            error(sourceUnit, errorMessage)
        } else {
            sourceUnit.getErrorCollector().addErrorAndContinue(new SyntaxErrorMessage(
                    new SyntaxException(errorMessage + '\n', expr.getLineNumber(), expr.getColumnNumber(),
                            expr.getLastLineNumber(), expr.getLastColumnNumber()),
                    sourceUnit)
            )
        }
    }

    static void error(SourceUnit sourceUnit, String errorMessage) {
        sourceUnit.getErrorCollector().addErrorAndContinue(new SimpleMessage(errorMessage, sourceUnit))
    }
}
