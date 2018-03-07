package io.micronaut.context.env.groovy

import groovy.transform.CompileStatic
import org.codehaus.groovy.ast.*
import org.codehaus.groovy.ast.expr.*
import org.codehaus.groovy.control.CompilePhase
import org.codehaus.groovy.control.CompilerConfiguration
import org.codehaus.groovy.control.SourceUnit
import org.codehaus.groovy.control.customizers.ASTTransformationCustomizer
import org.codehaus.groovy.transform.ASTTransformation
import org.codehaus.groovy.transform.GroovyASTTransformation

import static org.codehaus.groovy.ast.tools.GeneralUtils.*

/**
 * Evaluates type safe configurations converting property paths to calls to setProperty(..) and returning a map of all assigned configuration values
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