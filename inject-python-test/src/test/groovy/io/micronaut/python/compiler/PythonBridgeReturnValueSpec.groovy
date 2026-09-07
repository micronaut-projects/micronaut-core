package io.micronaut.python.compiler

import spock.lang.Specification

class PythonBridgeReturnValueSpec extends Specification {

    void "bridge methods invoke python method once before converting nullable return values"() {
        given:
        def pythonCode = '''
from jakarta.inject import Singleton
from micronaut.context.annotation import Executable

@Singleton
class StreamLikeService:
    @Executable
    def read(self) -> str:
        return "body"
'''
        def tempDir = File.createTempDir("pyronaut-bridge-return", "")
        def compiler = PyronautCompiler.builder()
            .pythonCode(pythonCode)
            .targetDir(tempDir)
            .build()

        when:
        compiler.compile()

        then:
        def generated = new File(tempDir, "python/StreamLikeService.java")
        generated.exists()
        def javaCode = generated.text
        javaCode.count("PythonInvocation.invokePythonMethod") == 1
        javaCode.contains("Value pythonResult = PythonInvocation.invokePythonMethod")
        javaCode.contains("return PythonConversion.isNone(pythonResult) ? null : pythonResult.asString();")

        cleanup:
        tempDir.deleteDir()
    }
}
