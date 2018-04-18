package io.micronaut.ast.groovy.visitor;

import io.micronaut.inject.visitor.Element;
import io.micronaut.inject.visitor.VisitorContext;
import org.codehaus.groovy.ast.ASTNode;
import org.codehaus.groovy.control.ErrorCollector;
import org.codehaus.groovy.control.SourceUnit;
import org.codehaus.groovy.control.messages.SyntaxErrorMessage;
import org.codehaus.groovy.syntax.SyntaxException;

public class GroovyVisitorContext implements VisitorContext {

    private final ErrorCollector errorCollector;
    private final SourceUnit sourceUnit;

    public GroovyVisitorContext(SourceUnit sourceUnit) {
        this.sourceUnit = sourceUnit;
        this.errorCollector = sourceUnit.getErrorCollector();
    }

    private SyntaxErrorMessage buildMessage(String message, Element element) {
        ASTNode expr = (ASTNode) element.getNativeType();
        return new SyntaxErrorMessage(
                new SyntaxException(message + '\n', expr.getLineNumber(), expr.getColumnNumber(),
                        expr.getLastLineNumber(), expr.getLastColumnNumber()), sourceUnit);
    }

    @Override
    public void fail(String message, Element element) {
        errorCollector.addError(buildMessage(message, element));
    }

}
