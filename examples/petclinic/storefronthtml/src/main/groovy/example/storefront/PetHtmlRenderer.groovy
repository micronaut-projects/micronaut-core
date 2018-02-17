package example.storefront

import groovy.transform.CompileStatic
import io.reactivex.Single

@CompileStatic
interface PetHtmlRenderer {
    Single<String> renderPet(Long id)
}
