package example.storefront

import groovy.transform.CompileStatic
import io.reactivex.Single

@CompileStatic
interface PetGridHtmlRenderer {
    Single<String> renderPetGrid()
}
