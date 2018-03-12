package example.pets;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class FriendlyUrlTest {

    @Test
    public void testSanitizeWithDashes() {
        expect:
        assertEquals("harry-potter", new FriendlyUrl().sanitizeWithDashes("Harry Potter"));
        assertEquals("harry", new FriendlyUrl().sanitizeWithDashes("Harry "));
    }

}
