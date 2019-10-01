package io.micronaut.docs.server.sse;

/**
 * @author Graeme Rocher
 * @since 1.0
 */
// tag::class[]
class Headline {
    String title;
    String description;

    Headline() {}

    Headline(String title, String description) {
        this.title = title;
        this.description = description;
    }
}
// end::class[]