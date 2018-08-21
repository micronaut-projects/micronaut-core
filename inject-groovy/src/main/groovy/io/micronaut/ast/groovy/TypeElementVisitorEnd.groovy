package io.micronaut.ast.groovy

import groovy.transform.CompileStatic
import io.micronaut.ast.groovy.utils.AstMessageUtils
import io.micronaut.ast.groovy.visitor.LoadedVisitor
import org.codehaus.groovy.ast.ASTNode
import org.codehaus.groovy.control.CompilePhase
import org.codehaus.groovy.control.SourceUnit
import org.codehaus.groovy.transform.ASTTransformation
import org.codehaus.groovy.transform.GroovyASTTransformation

@CompileStatic
@GroovyASTTransformation(phase = CompilePhase.OUTPUT)
class TypeElementVisitorEnd implements ASTTransformation {

    @Override
    void visit(ASTNode[] nodes, SourceUnit source) {
        Map<String, LoadedVisitor> loadedVisitors = TypeElementVisitorTransform.loadedVisitors

        if (loadedVisitors != null) {
            for(loadedVisitor in loadedVisitors.values()) {
                try {
                    loadedVisitor.finish()
                } catch (Throwable e) {
                    AstMessageUtils.error(
                            source,
                            source.getAST(),
                            "Error finalizing type visitor [$loadedVisitor.visitor]: $e.message")
                }
            }
        }

        TypeElementVisitorTransform.loadedVisitors = null
    }
}
