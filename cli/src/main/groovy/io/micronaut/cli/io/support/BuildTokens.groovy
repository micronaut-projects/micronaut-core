package io.micronaut.cli.io.support

class BuildTokens {
    final String sourceLanguage, testFramework
    BuildTokens(String sourceLanguage, String testFramework) {
        this.sourceLanguage = sourceLanguage
        this.testFramework = testFramework
    }
}
