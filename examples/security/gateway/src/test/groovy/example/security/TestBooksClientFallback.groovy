package example.security

import example.security.books.api.Book
import example.security.gateway.BooksClient
import io.micronaut.http.annotation.Header
import io.micronaut.retry.annotation.Fallback
import javax.inject.Singleton

@Fallback
@Singleton
class TestBooksClientFallback implements BooksClient {

    public final static List<Book> GRAILS_BOOKS = Arrays.asList(
            new Book("grails-3-step-by-step", "Grails 3 - Step by Step","Cristian Olaru","https://grailsthreebook.com/","Learn how a complete greenfield application can be implemented quickly and efficiently with Grails 3 using profiles and plugins. Use the sample application that accompanies the book as an example.","grails_3_step_by_step.png"),
            new Book("practical-grails-3", "Practical Grails 3","Eric Helgeson","https://www.grails3book.com/","Learn the fundamental concepts behind building Grails applications with the first book dedicated to Grails 3. Real, up-to-date code examples are provided, so you can easily follow along.","pratical-grails-3-book-cover.png"),
            new Book("falando-de-grails", "Falando de Grails","Henrique Lobo Weissmann","http://www.casadocodigo.com.br/products/livro-grails","This is the best reference on Grails 2.5 and 3.0 written in Portuguese. It&#39;s a great guide to the framework, dealing with details that many users tend to ignore.","grails_weissmann.png"),
            new Book("grails-goodness-notebook", "Grails Goodness Notebook","Hubert A. Klein Ikkink","https://leanpub.com/grails-goodness-notebook","Experience the Grails framework through code snippets. Discover (hidden) Grails features through code examples and short articles. The articles and code will get you started quickly and provide deeper insight into Grails.","grailsgood.png"),
            new Book("the-definitive-guide-to-grails-2","The Definitive Guide to Grails 2","Jeff Scott Brown and Graeme Rocher","http://www.apress.com/9781430243779","As the title states, this is the definitive reference on the Grails framework, authored by core members of the development team.","grocher_jbrown_cover.jpg"),
            new Book("grails-in-action", "Grails in Action","Glen Smith and Peter Ledbrook","http://www.manning.com/gsmith2/","The second edition of Grails in Action is a comprehensive introduction to Grails 2 focused on helping you become super-productive fast.","gsmith2_cover150.jpg"),
            new Book("grails-2-a-quick-start-guide","Grails 2: A Quick-Start Guide","Dave Klein and Ben Klein","http://www.amazon.com/gp/product/1937785777?tag=misa09-20","This revised and updated edition shows you how to use Grails by iteratively building a unique, working application.","bklein_cover.jpg"),
            new Book("programming-grails","Programming Grails","Burt Beckwith","http://shop.oreilly.com/product/0636920024750.do","Dig deeper into Grails architecture and discover how this application framework works its magic.","bbeckwith_cover.gif")
    );

    public final static List<Book> GROOVY_BOOKS = Arrays.asList(
            new Book("making-java-groovy", "Making Java Groovy", "Ken Kousen", "http://www.manning.com/kousen/", "Make Java development easier by adding Groovy. Each chapter focuses on a task Java developers do, like building, testing, or working with databases or restful web services, and shows ways Groovy can make those tasks easier.", "Kousen-MJG.png"),
            new Book("groovy-in-action-2nd-edition", "Groovy in Action, 2nd Edition", "Dierk König, Guillaume Laforge, Paul King, Cédric Champeau, Hamlet D\"Arcy, Erik Pragt, and Jon Skeet", "http://www.manning.com/koenig2/", "This is the undisputed, definitive reference on the Groovy language, authored by core members of the development team.", "regina.png"),
            new Book("groovy-for-domain-specific-languages", "Groovy for Domain-Specific Languages", "Fergal Dearle", "http://www.packtpub.com/groovy-for-domain-specific-languages-dsl/book", "Learn how Groovy can help Java developers easily build domain-specific languages into their applications.", "gdsl.jpg"),
            new Book("groovy-2-cookbook", "Groovy 2 Cookbook", "Andrey Adamovitch, Luciano Fiandeso", "http://www.packtpub.com/groovy-2-cookbook/book", "This book contains more than 90 recipes that use the powerful features of Groovy 2 to develop solutions to everyday programming challenges.", "g2cook.jpg"),
            new Book("programming-groovy-2", "Programming Groovy 2", "Venkat Subramaniam", "http://pragprog.com/book/vslg2/programming-groovy-2", "This book helps experienced Java developers learn to use Groovy 2, from the basics of the language to its latest advances.", "vslg2.jpg")
    );

    @Override
    List<Book> grails(@Header String authorization) {
        GRAILS_BOOKS
    }

    @Override
    List<Book> groovy(@Header String authorization) {
        GROOVY_BOOKS
    }
}
