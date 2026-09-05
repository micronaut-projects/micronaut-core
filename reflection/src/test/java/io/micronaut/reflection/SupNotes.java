package io.micronaut.reflection;

/**
 * A type with one property of accessors and one field of its own: the processor describes the first, and a
 * reflective introspection asked for field access describes both.
 */
public class SupNotes {

    private String title = "title";

    private String note = "note"; // NOSONAR - unread on purpose, the specs assert the introspection leaves it out

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }
}
