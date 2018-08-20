package io.micronaut.ast.groovy

import groovy.transform.CompileStatic
import io.micronaut.ast.groovy.utils.AstMessageUtils
import io.micronaut.ast.groovy.visitor.GroovyVisitorContext
import io.micronaut.ast.groovy.visitor.LoadedVisitor
import io.micronaut.core.io.service.ServiceDefinition
import io.micronaut.core.io.service.SoftServiceLoader
import io.micronaut.inject.visitor.TypeElementVisitor
import org.codehaus.groovy.ast.ASTNode
import org.codehaus.groovy.ast.ModuleNode
import org.codehaus.groovy.control.CompilePhase
import org.codehaus.groovy.control.SourceUnit
import org.codehaus.groovy.transform.ASTTransformation
import org.codehaus.groovy.transform.GroovyASTTransformation

@CompileStatic
@GroovyASTTransformation(phase = CompilePhase.INITIALIZATION)
class TypeElementVisitorStart implements ASTTransformation {

    @Override
    void visit(ASTNode[] nodes, SourceUnit source) {

        Map<String, LoadedVisitor> loadedVisitors = TypeElementVisitorTransform.loadedVisitors

        if (loadedVisitors == null) {
            loadedVisitors = [:]

            ModuleNode moduleNode = source.getAST()
            SoftServiceLoader serviceLoader = SoftServiceLoader.load(TypeElementVisitor, TypeElementVisitorStart.classLoader)
            GroovyVisitorContext visitorContext = new GroovyVisitorContext(source)

            for (ServiceDefinition<TypeElementVisitor> definition: serviceLoader) {
                if (definition.isPresent()) {
                    TypeElementVisitor visitor = definition.load()
                    LoadedVisitor newLoadedVisitor = new LoadedVisitor(visitor, visitorContext)
                    loadedVisitors.put(definition.getName(), newLoadedVisitor)
                }
            }

            for(loadedVisitor in loadedVisitors.values()) {
                try {
                    loadedVisitor.start()
                } catch (Throwable e) {
                    AstMessageUtils.error(
                            source,
                            moduleNode,
                            "Error starting type visitor [$loadedVisitor.visitor]: $e.message")
                }
            }
        }

        TypeElementVisitorTransform.loadedVisitors = loadedVisitors
    }
}
