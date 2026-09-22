package io.micronaut.web.router.proof;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Generates the source of a {@code CompiledRouteMatcher}: a URL parser for a set of routes, one
 * method per node of the tree of their path segments. At a node the generated code finds the next
 * segment, compares it with the literal children, then captures it for the variable child, and
 * backtracks when a branch does not end in a route; at the end of the path it answers the ordinal
 * of the route for the request's HTTP method. A route is compiled only if its template is made of
 * literal segments and whole-segment variables, which the parser matches exactly as the router
 * does; the others are left to the router.
 */
final class CompiledRouteMatcherGenerator {

    private static final Pattern VARIABLE = Pattern.compile("\\{[A-Za-z_][A-Za-z0-9_]*}");

    private final Node root = new Node();
    private final List<Node> nodes = new ArrayList<>();
    private int maxVariables;

    /**
     * Add a route.
     *
     * @param ordinal    The ordinal of its declaration
     * @param httpMethod The HTTP method
     * @param template   The URI template
     * @return Whether the parser matches the route, or leaves it to the router
     */
    boolean add(int ordinal, String httpMethod, String template) {
        String path = template.length() > 1 && template.endsWith("/") ? template.substring(0, template.length() - 1) : template;
        if (!path.startsWith("/")) {
            return false;
        }
        List<String> segments = path.equals("/") ? List.of() : List.of(path.substring(1).split("/", -1));
        for (String segment : segments) {
            boolean variable = VARIABLE.matcher(segment).matches();
            if (segment.isEmpty() || !variable && (segment.indexOf('{') >= 0 || segment.indexOf('}') >= 0)) {
                return false;
            }
        }
        Node node = root;
        int variables = 0;
        for (String segment : segments) {
            if (VARIABLE.matcher(segment).matches()) {
                if (node.variable == null) {
                    node.variable = new Node();
                }
                node = node.variable;
                variables++;
            } else {
                node = node.literals.computeIfAbsent(segment, s -> new Node());
            }
        }
        if (node.routes.putIfAbsent(httpMethod, ordinal) != null) {
            return false;
        }
        maxVariables = Math.max(maxVariables, variables);
        return true;
    }

    /**
     * @param className The simple name of the generated class
     * @return The source of the class, a static nested class of the declarations enum
     */
    String generate(String className) {
        number(root);
        StringBuilder source = new StringBuilder();
        source.append("    /**\n     * The URL parser of the declared routes, generated at compile time.\n     */\n");
        source.append("    static final class ").append(className).append(" implements io.micronaut.web.router.CompiledRouteMatcher {\n");
        source.append("        static final ").append(className).append(" INSTANCE = new ").append(className).append("();\n\n");
        source.append("        @Override\n        public int maxVariables() {\n            return ").append(maxVariables).append(";\n        }\n\n");
        source.append("        @Override\n        public int match(io.micronaut.http.HttpMethod m, String p, String[] v) {\n");
        source.append("            if (p.isEmpty() || p.equals(\"/\")) {\n");
        appendRoutes(source, root, "                ");
        source.append("                return -1;\n            }\n");
        source.append("            return n").append(root.id).append("(m, p, 0, v, 0);\n        }\n");
        for (Node node : nodes) {
            appendNode(source, node, node == root);
        }
        source.append("    }\n");
        return source.toString();
    }

    private void number(Node node) {
        node.id = nodes.size();
        nodes.add(node);
        node.literals.values().forEach(this::number);
        if (node.variable != null) {
            number(node.variable);
        }
    }

    private static void appendRoutes(StringBuilder source, Node node, String indent) {
        for (Map.Entry<String, Integer> route : node.routes.entrySet()) {
            source.append(indent).append("if (m == io.micronaut.http.HttpMethod.").append(route.getKey()).append(") {\n")
                .append(indent).append("    return ").append(route.getValue()).append(";\n")
                .append(indent).append("}\n");
        }
    }

    private static void appendNode(StringBuilder source, Node node, boolean root) {
        source.append("\n        private static int n").append(node.id).append("(io.micronaut.http.HttpMethod m, String p, int i, String[] v, int k) {\n");
        source.append("            int length = p.length();\n");
        source.append("            if (i == length) {\n");
        if (!root) {
            appendRoutes(source, node, "                ");
        }
        source.append("                return -1;\n            }\n");
        if (node.literals.isEmpty() && node.variable == null) {
            source.append("            return -1;\n        }\n");
            return;
        }
        source.append("            if (p.charAt(i) != '/') {\n                return -1;\n            }\n");
        source.append("            int s = i + 1;\n            int e = p.indexOf('/', s);\n            if (e < 0) {\n                e = length;\n            }\n");
        source.append("            int r;\n");
        for (Map.Entry<String, Node> literal : node.literals.entrySet()) {
            String value = literal.getKey();
            source.append("            if (e - s == ").append(value.length()).append(" && p.startsWith(\"").append(value).append("\", s)) {\n")
                .append("                r = n").append(literal.getValue().id).append("(m, p, e, v, k);\n")
                .append("                if (r >= 0) {\n                    return r;\n                }\n            }\n");
        }
        if (node.variable != null) {
            source.append("            if (e > s) {\n")
                .append("                v[k] = p.substring(s, e);\n")
                .append("                r = n").append(node.variable.id).append("(m, p, e, v, k + 1);\n")
                .append("                if (r >= 0) {\n                    return r;\n                }\n            }\n");
        }
        source.append("            return -1;\n        }\n");
    }

    /**
     * A node of the tree of path segments.
     */
    private static final class Node {
        final Map<String, Node> literals = new LinkedHashMap<>();
        final Map<String, Integer> routes = new LinkedHashMap<>();
        Node variable;
        int id;
    }
}
