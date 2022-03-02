package io.micronaut.core.cli

import spock.lang.Specification

class DefaultCommandLineSpec extends Specification {

    void "test = inside quotes is handled"() {
        given:
        CommandLine commandLine = CommandLine.parse("--tracing.zipkin.http.path=/20200101/observations/public-span?dataFormat=zipkin&dataFormatVersion=2&dataKey=123456789")

        expect:
        commandLine.getUndeclaredOptions().get("tracing.zipkin.http.path") == "/20200101/observations/public-span?dataFormat=zipkin&dataFormatVersion=2&dataKey=123456789"
    }
}
