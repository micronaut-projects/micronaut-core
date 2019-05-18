package io.micronaut.docs.replaces;

import io.micronaut.context.ApplicationContext;
import io.micronaut.docs.requires.Book;
import org.junit.Assert;
import org.junit.Test;

public class ReplacesTest {

    @Test
    public void testReplaces() {
        ApplicationContext applicationContext = ApplicationContext.run();

        Assert.assertTrue(
                applicationContext.getBean(BookService.class) instanceof MockBookService
        );
        Assert.assertEquals("An OK Novel", applicationContext.getBean(Book.class).getTitle());
        Assert.assertEquals("Learning 305", applicationContext.getBean(TextBook.class).getTitle());

        applicationContext.stop();
    }
}
