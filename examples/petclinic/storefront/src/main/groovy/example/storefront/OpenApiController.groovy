package example.storefront

import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.openapi.OpenApiDocument
import io.micronaut.openapi.OpenApiInfo
import io.micronaut.openapi.OpenApiPath
import io.micronaut.openapi.OpenApiSecurityScheme
import io.micronaut.openapi.OpenApiServer

@Controller('/swagger')
class OpenApiController {

    @Get('/')
    Map index() {
        OpenApiSecurityScheme securityScheme = OpenApiSecurityScheme.builder()
                .path('api_key')
                .type('apiKey')
                .name('api_key')
                .inField('header')
                .build()
        OpenApiInfo info = OpenApiInfo.builder()
                .version('1.0.0')
                .title('Micronaut Petstore')
                .build()
        OpenApiDocument.builder()
                .swagger('2.0')
                .info(info)
                .paths([MailController, StoreController].collect { Class clazz -> OpenApiPath.of(clazz) }.flatten() as List<OpenApiPath>)
                .securityDefinitions([securityScheme])
                .build()
                .toMap()
    }
}
