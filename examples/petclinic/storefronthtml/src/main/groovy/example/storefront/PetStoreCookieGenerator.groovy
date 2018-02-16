package example.storefront

import org.particleframework.context.annotation.Value
import org.particleframework.http.cookie.Cookie
import org.particleframework.http.cookie.CookieFactory

import javax.inject.Singleton

@Singleton
class PetStoreCookieGenerator {

    @Value('${petstore.cookie.name}')
    String cookieName

    Cookie generate() {
        CookieFactory.INSTANCE.create(cookieName, UUID.randomUUID().toString())
    }
}
