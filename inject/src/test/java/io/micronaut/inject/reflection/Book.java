package io.micronaut.inject.reflection;

import java.util.List;

@Tag("entity")
@Composed
public class Book extends Base {

    @Tag("title")
    @Tag(value = "name", priority = 2)
    private String title;

    private int pages;

    private final List<@Tag("elem") String> tags;

    private boolean published;

    Book() {
        this("", 0);
    }

    @Tag("ctor")
    public Book(String title, int pages) {
        this.title = title;
        this.pages = pages;
        this.tags = List.of("a", "b");
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public int getPages() {
        return pages;
    }

    public void setPages(int pages) {
        this.pages = pages;
    }

    public List<String> getTags() {
        return tags;
    }

    public boolean isPublished() {
        return published;
    }

    public void setPublished(boolean published) {
        this.published = published;
    }

    @Hidden("secret")
    @Tag(value = "disc", level = Level.HIGH, type = Number.class, nested = @Stereo(kind = "inner"))
    public double discount(@Tag("pct") double percent) {
        return pages * (1 - percent / 100);
    }

    public String describe(Level level) {
        return title + ":" + level;
    }
}
