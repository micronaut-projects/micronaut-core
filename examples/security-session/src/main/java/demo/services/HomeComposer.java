package demo.services;

import javax.annotation.Nullable;
import javax.inject.Singleton;
import java.security.Principal;
import java.util.Optional;

@Singleton
public class HomeComposer {
    protected final Html5Composer html5Composer;

    public HomeComposer(Html5Composer html5Composer) {
        this.html5Composer = html5Composer;
    }

    public String compose(@Nullable Principal principal) {
        StringBuilder sb = new StringBuilder();
        sb.append("<h1>");
        if (principal!=null) {
            sb.append("username:");
            sb.append(principal.getName());
        } else {
            sb.append("You are not logged in");
        }
        sb.append("</h1>");
        if (principal==null) {
            sb.append("<p><a href=\"/login/auth\">Login</a></p>");
        }
        if (principal!=null) {
            sb.append("<form action=\"logout\" method=\"POST\">");
            sb.append("<input type=\"submit\" value=\"Logout\"/>");
            sb.append("</form>");
        }
        return html5Composer.compose(sb.toString());
    }
}
