package io.micronaut.reflection;

/**
 * An implementation of a generic super class: {@code keep(Book)} overrides the {@code keep(Object)} the super
 * class declares once erased.
 */
public class ExecBookStore extends ExecGenericStore<Book> {

    @Override
    @Tag("book-store")
    public void keep(@Tag("book-store-param") Book item) {
    }
}
