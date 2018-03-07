package io.micronaut.ast.groovy.utils

import groovy.transform.CompileStatic
import org.codehaus.groovy.ast.ASTNode
import org.codehaus.groovy.control.Janitor
import org.codehaus.groovy.control.SourceUnit
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
        sourceUnit.getErrorCollector().addErrorAndContinue(new SyntaxErrorMessage(
                new SyntaxException(errorMessage + '\n', expr.getLineNumber(), expr.getColumnNumber(),
                        expr.getLastLineNumber(), expr.getLastColumnNumber()),
                sourceUnit)
        )
    }

}
