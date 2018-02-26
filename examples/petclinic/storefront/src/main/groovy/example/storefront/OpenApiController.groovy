package example.storefront

import org.particleframework.http.annotation.Controller
import org.particleframework.http.annotation.Get
import org.particleframework.openapi.OpenApiDocument
import org.particleframework.openapi.OpenApiInfo
import org.particleframework.openapi.OpenApiPath
import org.particleframework.openapi.OpenApiSecurityScheme
import org.particleframework.openapi.OpenApiServer

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
