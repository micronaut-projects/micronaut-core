package io.micronaut.test.issue1940


import javax.inject.Singleton

@Singleton
class TestService {

    private final TestApi testApi

    TestService(TestApi testApi) {
        this.testApi = testApi
    }

    String greeting(String name) {
        return testApi.greeting(name)
    }
}