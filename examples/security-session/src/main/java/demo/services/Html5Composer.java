package demo.services;

import javax.inject.Singleton;

@Singleton
public class Html5Composer {

    public String compose(String str) {
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html>");
        sb.append("<html>");
        sb.append("<head>");
        sb.append("</head>");
        sb.append("<body>");
        sb.append(str);
        sb.append("</body>");
        sb.append("</html>");
        return sb.toString();
    }
}
