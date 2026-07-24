package io.micronaut.http.client

import io.micronaut.context.annotation.Requires
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.annotation.Client

interface KotlinInheritedDefaultApi {
    @Get("/inherited-default")
    fun index(): String

    fun inheritedDefault(): String = index() + " from default"
}

@Client("/")
interface KotlinInheritedDefaultClient : KotlinInheritedDefaultApi

@Requires(property = "spec.name", value = "KotlinInheritedDefaultClientSpec")
@Controller
class KotlinInheritedDefaultClientController : KotlinInheritedDefaultApi {
    override fun index(): String = "success"
}
