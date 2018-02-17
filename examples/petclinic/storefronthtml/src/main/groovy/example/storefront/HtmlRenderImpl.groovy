package example.storefront

import example.storefront.ui.NavBar
import example.storefront.ui.PetListViewModel
import example.storefront.ui.PetViewModel
import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import groovy.xml.MarkupBuilder
import io.reactivex.Single
import org.particleframework.context.annotation.Value

import javax.inject.Singleton

@Singleton
@CompileStatic
class HtmlRenderImpl implements HtmlRenderer {

    @Value('${petstore.html.template}')
    String filename

    @Override
    @CompileDynamic
    String renderPetCell(PetViewModel pet) {
        StringWriter writer = new StringWriter()
        MarkupBuilder html = new MarkupBuilder(writer)
        html.div(class:"col-sm") {
            a(href: petHref(pet)) {
                img(src: pet.image)
            }
        }
        writer.toString()
    }

    String hrefByNavBar(NavBar nav) {
        if ( nav == NavBar.HOME ) {
            return '/'
        }

        "/${nav.name().toLowerCase()}"
    }

    String petHref(PetViewModel pet) {
        "/pets/${pet.id}"
    }

    String titleByNavbar(NavBar nav) {
        nav.name()
    }

    List<NavBar> navBarItems() {
        [NavBar.HOME, NavBar.PETS, NavBar.VENDORS]
    }

    @CompileDynamic
    String renderNavBar(NavBar current) {
        StringWriter writer = new StringWriter()
        MarkupBuilder html = new MarkupBuilder(writer)
        html.ul(class: 'navbar-nav') {
            for ( NavBar item : navBarItems() ) {
                if ( item == current) {
                    li(class: "nav-item active") {
                        a(class: "nav-link", href: hrefByNavBar(item), titleByNavbar(item))
                    }
                } else {
                    li(class: "nav-item") {
                        a(class: "nav-link", href: hrefByNavBar(item)) {
                            mkp.yield titleByNavbar(item)
                            span(class: "sr-only") {
                                mkp.yieldUnescaped '(current)'
                            }
                        }
                    }
                }
            }
        }
        writer.toString()
    }

    @Override
    Single<String> renderContainer(NavBar item, Single<String> container) {
        String placeHolder = '{{container}}'
        String text = getClass().getResource(filename).text
        text = text.replaceAll('\\{\\{navbar}}', renderNavBar(item))
        this.container(placeHolder, text, container)
    }

    @Override
    Single<String> container(String placeHolder, String text, Single<String> container) {

        int indexOf = text.indexOf(placeHolder)
        if ( indexOf == -1 ) {
            return container
        }
        Single<String> preffix = Single.just(text.substring(0, indexOf))
        Single<String> suffix = Single.just(text.substring((indexOf + placeHolder.length()), text.length()))
        preffix.concatWith(container)
                .reduce('', { String s, String s2 -> s + s2 })
                .concatWith(suffix)
                .reduce('', { String s, String s2 -> s + s2 })
    }

    @Override
    @CompileDynamic
    String renderPet(PetViewModel petViewModel) {
        StringWriter writer = new StringWriter()
        MarkupBuilder html = new MarkupBuilder(writer)
        html.img(src: petViewModel.image, alt: '', class: 'img-thumbnail')
        html.div(class: "clearfix") {
            div(class:"jumbotron") {
                p {
                    b('Request Info about this PET')
                }
                form(method: 'POST', action:"/pet/requestInfo") {
                    div(class: "form-group") {
                        input(type: "hidden", name: "id", value: "${petViewModel.id}")
                        label(for: "inputEmail", 'Email address')
                        input(type: "email", class: "form-control", id:"inputEmail", ('aria-describedby'): 'emailHelp', placeholder: "Enter email", name: 'email')
                        small(id:"emailHelp", class: "form-text text-muted", "We'll never share your email with anyone else.")
                    }
                    button(type: "submit", class:"btn btn-primary", 'Send me info')
                }
            }
        }
        writer.toString()
    }

    @Override
    @CompileDynamic
    String renderPetGrid(PetListViewModel petListViewModel) {
        StringWriter writer = new StringWriter()
        MarkupBuilder html = new MarkupBuilder(writer)
        html.h2 {
            mkp.yield petListViewModel.type as String
        }
        int numberOfColumns = 3
        for ( int row = 0; row < (petListViewModel.petList.size() / numberOfColumns); row++) {
            int fromIndex = row * numberOfColumns
            int toIndex = Math.min(fromIndex + numberOfColumns, petListViewModel.petList.size())
            List<PetViewModel> rowPets = petListViewModel.petList.subList(fromIndex, toIndex)
            html.div(class: "row") {
                for ( PetViewModel pet : rowPets ) {
                    mkp.yieldUnescaped renderPetCell(pet)
                }
            }
        }
        writer.toString()
    }


}
