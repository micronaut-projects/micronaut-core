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
package io.micronaut.ast.transform.test;

import groovy.lang.GroovyClassLoader;
import groovy.lang.GroovyCodeSource;
import org.codehaus.groovy.ast.ASTNode;
import org.codehaus.groovy.ast.stmt.BlockStatement;
import org.codehaus.groovy.control.CompilationUnit;
import org.codehaus.groovy.control.CompilePhase;
import org.codehaus.groovy.control.CompilerConfiguration;

import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Forked from {@link org.codehaus.groovy.ast.builder.AstStringCompiler}.
 */
class MicronautAstBuilder {

    /**
     * Performs the String source to {@link java.util.List} of {@link org.codehaus.groovy.ast.ASTNode}.
     *
     * @param script
     *      a Groovy script in String form
     * @param compilePhase
     *      the int based CompilePhase to compile it to.
     * @param statementsOnly
     * @return {@link java.util.List} of {@link org.codehaus.groovy.ast.ASTNode}
     */
    public List<ASTNode> compile(String script, CompilePhase compilePhase, boolean statementsOnly) {
        final String scriptClassName = makeScriptClassName();
        GroovyCodeSource codeSource = new GroovyCodeSource(script, scriptClassName + ".groovy", "/groovy/script");
        codeSource.setCachable(false);
        CompilationUnit cu = new CompilationUnit(CompilerConfiguration.DEFAULT, codeSource.getCodeSource(),
                AccessController.doPrivileged((PrivilegedAction<GroovyClassLoader>) GroovyClassLoader::new));
        cu.addSource(codeSource.getName(), script);
        cu.compile(compilePhase.getPhaseNumber());

        // collect all the ASTNodes into the result, possibly ignoring the script body if desired
        List<ASTNode> result = cu.getAST().getModules().stream().reduce(new LinkedList<>(), (acc, node) -> {
            BlockStatement statementBlock = node.getStatementBlock();
            if (null != statementBlock) {
                acc.add(statementBlock);
            }
            acc.addAll(
                    node.getClasses().stream()
                            .filter(c -> !(statementsOnly && scriptClassName.equals(c.getName())))
                            .collect(Collectors.toList())
            );

            return acc;
        }, (o1, o2) -> o1);

        return result;
    }

    /**
     * Performs the String source to {@link java.util.List} of statement {@link ASTNode}.
     *
     * @param script a Groovy script in String form
     * @return {@link java.util.List} of statement {@link ASTNode}
     * @since 3.0.0
     */
    public List<ASTNode> compile(String script) {
        return this.compile(script, CompilePhase.CLASS_GENERATION, true);
    }

    private static String makeScriptClassName() {
        return "Script" + System.nanoTime();
    }

}
