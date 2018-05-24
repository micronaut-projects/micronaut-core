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

package io.micronaut.ast.groovy.visitor;

import io.micronaut.inject.visitor.Element;
import io.micronaut.inject.visitor.VisitorContext;
import org.codehaus.groovy.ast.ASTNode;
import org.codehaus.groovy.control.ErrorCollector;
import org.codehaus.groovy.control.Janitor;
import org.codehaus.groovy.control.SourceUnit;
import org.codehaus.groovy.control.messages.SyntaxErrorMessage;
import org.codehaus.groovy.syntax.SyntaxException;

/**
 * The visitor context when visiting Groovy code.
 *
 * @author James Kleeh
 * @author Graeme Rocher
 * @since 1.0
 */
public class GroovyVisitorContext implements VisitorContext {

    private final ErrorCollector errorCollector;
    private final SourceUnit sourceUnit;

    /**
     * @param sourceUnit The {@link SourceUnit}
     */
    public GroovyVisitorContext(SourceUnit sourceUnit) {
        this.sourceUnit = sourceUnit;
        this.errorCollector = sourceUnit.getErrorCollector();
    }

    @Override
    public void fail(String message, Element element) {
        errorCollector.addError(buildErrorMessage(message, element));
    }

    @Override
    public void warn(String message, Element element) {
        ASTNode expr = (ASTNode) element.getNativeType();
        final String sample = sourceUnit.getSample(expr.getLineNumber(), expr.getColumnNumber(), new Janitor());
        System.err.println("WARNING: " + message + "\n\n" + sample);

    }

    private SyntaxErrorMessage buildErrorMessage(String message, Element element) {
        ASTNode expr = (ASTNode) element.getNativeType();
        return new SyntaxErrorMessage(
            new SyntaxException(message + '\n', expr.getLineNumber(), expr.getColumnNumber(),
                expr.getLastLineNumber(), expr.getLastColumnNumber()), sourceUnit);
    }

}
