package com.rosogisoft.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.XMLConstants;
import javax.xml.namespace.NamespaceContext;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPathFactory;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Renders a mock response body template by substituting {{expression}}
 * placeholders with values extracted from the incoming request.
 *
 * Supported expressions:
 *
 *   {{fieldName}}              - top-level field from JSON request body
 *   {{user.address.city}}      - nested JSON path (dot-separated)
 *   {{param.queryParamName}}   - URL query parameter
 *   {{header.HeaderName}}      - request header (case-insensitive)
 *   {{request.method}}         - HTTP method (GET, POST, ...)
 *   {{request.path}}           - request path (/api/users/1)
 *   {{xpath:/xml/path/text()}}  - XPath value from XML/SOAP request body
 *
 * If a placeholder cannot be resolved it is left unchanged.
 */
@Slf4j
@Service
public class MockTemplateEngine {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{([^}]+)}}");
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String XPATH_PREFIX = "xpath:";
    private static final String SOAP11_NS = "http://schemas.xmlsoap.org/soap/envelope/";
    private static final String SOAP12_NS = "http://www.w3.org/2003/05/soap-envelope";

    private final EnvironmentTemplateService environmentTemplateService;
    private final TemplateExpressionParser expressionParser;
    private final MockFunctionService functionService;

    @Autowired
    public MockTemplateEngine(EnvironmentTemplateService environmentTemplateService,
                              TemplateExpressionParser expressionParser,
                              MockFunctionService functionService) {
        this.environmentTemplateService = environmentTemplateService;
        this.expressionParser = expressionParser;
        this.functionService = functionService;
    }

    public MockTemplateEngine(EnvironmentTemplateService environmentTemplateService,
                              TemplateExpressionParser expressionParser) {
        this(environmentTemplateService, expressionParser, null);
    }

    /**
     * @param template    Raw mock responseBody (may contain {{...}} placeholders)
     * @param method      HTTP method of the incoming request
     * @param path        Request path
     * @param queryParams Raw query string (key=val&key2=val2), may be null
     * @param headers     Request headers map
     * @param requestBody Raw request body string, may be null
     * @return Rendered response body
     */
    public String render(String template,
                         String method,
                         String path,
                         String queryParams,
                         Map<String, String> headers,
                         String requestBody) {
        return render(template, method, path, queryParams, headers, requestBody, EnvironmentContext.empty());
    }

    public String render(String template,
                         String method,
                         String path,
                         String queryParams,
                         Map<String, String> headers,
                         String requestBody,
                         EnvironmentContext environmentContext) {
        TemplateRenderContext context = new TemplateRenderContext(
                null, method, path, queryParams, headers, requestBody, environmentContext, TemplatePhase.RESPONSE);
        return render(template, context);
    }

    public String render(String template,
                         String method,
                         String path,
                         String queryParams,
                         Map<String, String> headers,
                         String requestBody,
                         EnvironmentContext environmentContext,
                         TemplatePhase phase) {
        TemplateRenderContext context = new TemplateRenderContext(
                null, method, path, queryParams, headers, requestBody, environmentContext, phase);
        return render(template, context);
    }

    public String render(String template, TemplateRenderContext context) {

        if (template == null || !template.contains("{{")) {
            return template;
        }

        List<TemplateExpressionParser.TemplatePlaceholder> placeholders = expressionParser.placeholders(template);
        if (placeholders.isEmpty()) {
            return template;
        }
        StringBuilder result = new StringBuilder(template.length() + 64);
        int cursor = 0;
        for (TemplateExpressionParser.TemplatePlaceholder placeholder : placeholders) {
            result.append(template, cursor, placeholder.start());
            String replacement = resolveExpression(placeholder.expression(), context);
            result.append(replacement != null ? replacement : placeholder.placeholder());
            cursor = placeholder.end();
        }
        result.append(template, cursor, template.length());
        return result.toString();
    }

    public String renderResponse(String template, TemplateRenderContext context) {
        return render(template, context.withPhase(TemplatePhase.RESPONSE));
    }

    public String renderMatching(String template,
                                 String method,
                                 String path,
                                 String queryParams,
                                 Map<String, String> headers,
                                 String requestBody,
                                 EnvironmentContext environmentContext) {
        return render(template, method, path, queryParams, headers, requestBody,
                environmentContext, TemplatePhase.MATCHING);
    }

    private String resolveExpression(String expr, TemplateRenderContext context) {
        TemplateExpressionParser.TemplateExpression expression = expressionParser.parse(expr);
        Object resolved = resolveLookup(expression.lookup(), context);
        return resolved != null
                ? stringify(resolved)
                : expression.fallback().map(TemplateExpressionParser.TemplateLiteral::value).orElse(null);
    }

    private Object resolveLookup(String expr, TemplateRenderContext context) {
        if (expr == null || expr.isBlank()) {
            return null;
        }
        if (isFunctionCall(expr)) {
            return resolveFunction(expr, context);
        }
        if (expr.equalsIgnoreCase("request.method")) return context.method();
        if (expr.equalsIgnoreCase("request.path")) return context.path();

        String normalizedExpr = expr.toLowerCase(Locale.ROOT);

        if (normalizedExpr.startsWith("env.") || normalizedExpr.startsWith("global.")) {
            return environmentTemplateService.resolveExpression(expr, context.environmentContext(), false);
        }

        if (normalizedExpr.startsWith("param.")) {
            String key = expr.substring(6);
            return context.queryParams().get(key);
        }

        if (normalizedExpr.startsWith("header.")) {
            String headerName = expr.substring(7);
            return context.headers().entrySet().stream()
                    .filter(e -> e.getKey().equalsIgnoreCase(headerName))
                    .map(Map.Entry::getValue)
                    .findFirst()
                    .orElse(null);
        }

        if (normalizedExpr.startsWith(XPATH_PREFIX)) {
            String xpath = expr.substring(XPATH_PREFIX.length()).trim();
            return resolveXPath(context.bodyXml(), context.xmlNamespaces(), xpath);
        }

        JsonNode bodyJson = context.bodyJson();
        if (bodyJson != null) {
            return resolveJsonPath(bodyJson, expr);
        }

        return null;
    }

    private Object resolveFunction(String expr, TemplateRenderContext context) {
        if (context.phase() == TemplatePhase.MATCHING) {
            return null;
        }
        if (functionService == null) {
            return null;
        }
        FunctionCall call = parseFunctionCall(expr);
        List<Object> args = call.arguments().stream()
                .map(argument -> resolveArgument(argument, context))
                .toList();
        try {
            return functionService.execute(context.ownerId(), call.name(), args, context);
        } catch (RuntimeException e) {
            log.debug("Could not execute template function '{}'", call.name(), e);
            return null;
        }
    }

    private Object resolveArgument(String argument, TemplateRenderContext context) {
        String value = argument != null ? argument.trim() : "";
        if (value.startsWith("{{") && value.endsWith("}}")) {
            return resolveExpression(value.substring(2, value.length() - 2).trim(), context);
        }
        if (isFunctionCall(value)) {
            return resolveFunction(value, context);
        }
        if (isQuoted(value)) {
            return unquote(value);
        }
        if ("true".equalsIgnoreCase(value)) return true;
        if ("false".equalsIgnoreCase(value)) return false;
        if ("null".equalsIgnoreCase(value) || "none".equalsIgnoreCase(value)) return null;
        if (value.matches("-?\\d+")) return Long.parseLong(value);
        if (value.matches("-?\\d+\\.\\d+")) return Double.parseDouble(value);
        Object resolved = resolveLookup(value, context);
        return resolved != null ? resolved : value;
    }

    private boolean isFunctionCall(String expr) {
        String value = expr != null ? expr.trim() : "";
        return value.startsWith("fn.") && value.endsWith(")") && value.indexOf('(') > 3;
    }

    private FunctionCall parseFunctionCall(String expr) {
        String value = expr.trim();
        int open = value.indexOf('(');
        String name = value.substring(3, open).trim();
        String rawArgs = value.substring(open + 1, value.length() - 1);
        return new FunctionCall(name, splitArguments(rawArgs));
    }

    private List<String> splitArguments(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        char quote = 0;
        boolean escaped = false;
        int parenDepth = 0;
        int templateDepth = 0;
        int start = 0;
        for (int i = 0; i < raw.length(); i++) {
            char current = raw.charAt(i);
            if (escaped) {
                escaped = false;
                continue;
            }
            if (current == '\\') {
                escaped = true;
                continue;
            }
            if (quote != 0) {
                if (current == quote) quote = 0;
                continue;
            }
            if (current == '\'' || current == '"') {
                quote = current;
                continue;
            }
            char next = i + 1 < raw.length() ? raw.charAt(i + 1) : 0;
            if (current == '(') parenDepth++;
            else if (current == ')') parenDepth--;
            else if (current == '{' && next == '{') {
                templateDepth++;
                i++;
            } else if (current == '}' && next == '}') {
                templateDepth--;
                i++;
            } else if (current == ',' && parenDepth == 0 && templateDepth == 0) {
                result.add(raw.substring(start, i).trim());
                start = i + 1;
            }
        }
        result.add(raw.substring(start).trim());
        return result.stream().filter(arg -> !arg.isBlank()).toList();
    }

    private boolean isQuoted(String value) {
        if (value.length() < 2) return false;
        char first = value.charAt(0);
        char last = value.charAt(value.length() - 1);
        return (first == '\'' || first == '"') && first == last;
    }

    private String unquote(String value) {
        String body = value.substring(1, value.length() - 1);
        StringBuilder result = new StringBuilder(body.length());
        boolean escaped = false;
        for (int i = 0; i < body.length(); i++) {
            char current = body.charAt(i);
            if (escaped) {
                result.append(switch (current) {
                    case 'n' -> '\n';
                    case 'r' -> '\r';
                    case 't' -> '\t';
                    default -> current;
                });
                escaped = false;
                continue;
            }
            if (current == '\\') escaped = true;
            else result.append(current);
        }
        if (escaped) result.append('\\');
        return result.toString();
    }

    private String stringify(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String resolveXPath(Document document,
                                Map<String, String> namespaces,
                                String expression) {
        if (document == null || expression.isBlank()) {
            return null;
        }
        try {
            var xpath = XPathFactory.newInstance().newXPath();
            xpath.setNamespaceContext(new StaticNamespaceContext(namespaces));
            return xpath.evaluate(expression, document);
        } catch (Exception e) {
            log.debug("Could not resolve XPath expression '{}'", expression, e);
            return null;
        }
    }

    /**
     * Traverse a dot-separated path through a JsonNode tree.
     * e.g. "user.address.city" on {"user":{"address":{"city":"Berlin"}}}
     * returns "Berlin".
     */
    private String resolveJsonPath(JsonNode root, String dotPath) {
        String[] parts = dotPath.split("\\.");
        JsonNode current = root;
        for (String part : parts) {
            if (current == null || !current.isObject()) return null;
            current = current.get(part);
        }
        if (current == null || current.isNull()) return null;
        return current.isValueNode() ? current.asText() : current.toString();
    }

    private JsonNode parseJson(String body) {
        if (body == null || body.isBlank()) return null;
        try {
            return MAPPER.readTree(body);
        } catch (Exception e) {
            log.debug("Request body is not valid JSON - skipping JSON path resolution");
            return null;
        }
    }

    private Document parseXml(String body) {
        if (body == null || body.isBlank()) return null;
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            setFeatureIfSupported(factory, XMLConstants.FEATURE_SECURE_PROCESSING, true);
            setFeatureIfSupported(factory, "http://apache.org/xml/features/disallow-doctype-decl", true);
            setFeatureIfSupported(factory, "http://xml.org/sax/features/external-general-entities", false);
            setFeatureIfSupported(factory, "http://xml.org/sax/features/external-parameter-entities", false);
            setFeatureIfSupported(factory, "http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            setAttributeIfSupported(factory, XMLConstants.ACCESS_EXTERNAL_DTD, "");
            setAttributeIfSupported(factory, XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");

            var builder = factory.newDocumentBuilder();
            builder.setErrorHandler(new DefaultHandler());
            return builder.parse(new InputSource(new StringReader(body)));
        } catch (Exception e) {
            log.debug("Request body is not valid XML - skipping XPath resolution", e);
            return null;
        }
    }

    private void setFeatureIfSupported(DocumentBuilderFactory factory, String feature, boolean value) {
        try {
            factory.setFeature(feature, value);
        } catch (Exception e) {
            log.debug("XML parser feature '{}' is not supported", feature, e);
        }
    }

    private void setAttributeIfSupported(DocumentBuilderFactory factory, String attribute, String value) {
        try {
            factory.setAttribute(attribute, value);
        } catch (IllegalArgumentException e) {
            log.debug("XML parser attribute '{}' is not supported", attribute, e);
        }
    }

    private TemplateUsage inspectTemplate(String template) {
        boolean hasXPath = false;
        boolean hasJsonBodyPath = false;
        Matcher matcher = PLACEHOLDER.matcher(template);
        while (matcher.find()) {
            String expr = expressionParser.parse(matcher.group(1).trim()).lookup();
            String normalizedExpr = expr.toLowerCase(Locale.ROOT);
            if (normalizedExpr.startsWith(XPATH_PREFIX)) {
                hasXPath = true;
            } else if (!isRequestExpression(normalizedExpr)) {
                hasJsonBodyPath = true;
            }
        }
        return new TemplateUsage(hasXPath, hasJsonBodyPath);
    }

    private boolean isRequestExpression(String normalizedExpr) {
        return normalizedExpr.equals("request.method") ||
                normalizedExpr.equals("request.path") ||
                normalizedExpr.startsWith("param.") ||
                normalizedExpr.startsWith("header.") ||
                normalizedExpr.startsWith("env.") ||
                normalizedExpr.startsWith("global.");
    }

    private Map<String, String> collectNamespaces(Document document) {
        Map<String, String> namespaces = new LinkedHashMap<>();
        namespaces.put(XMLConstants.XML_NS_PREFIX, XMLConstants.XML_NS_URI);
        namespaces.put("soap11", SOAP11_NS);
        namespaces.put("soap12", SOAP12_NS);
        collectNamespaces(document.getDocumentElement(), namespaces);
        return namespaces;
    }

    private void collectNamespaces(Node node, Map<String, String> namespaces) {
        if (node == null || node.getNodeType() != Node.ELEMENT_NODE) {
            return;
        }

        String namespaceUri = node.getNamespaceURI();
        String prefix = node.getPrefix();
        if (namespaceUri != null && !namespaceUri.isBlank()) {
            if (prefix != null && !prefix.isBlank()) {
                namespaces.putIfAbsent(prefix, namespaceUri);
            } else {
                namespaces.putIfAbsent("d", namespaceUri);
            }
        }

        NamedNodeMap attributes = node.getAttributes();
        for (int i = 0; i < attributes.getLength(); i++) {
            Node attr = attributes.item(i);
            String name = attr.getNodeName();
            String value = attr.getNodeValue();
            if (XMLConstants.XMLNS_ATTRIBUTE.equals(name)) {
                namespaces.putIfAbsent("d", value);
            } else if (name.startsWith(XMLConstants.XMLNS_ATTRIBUTE + ":")) {
                namespaces.putIfAbsent(name.substring((XMLConstants.XMLNS_ATTRIBUTE + ":").length()), value);
            }
        }

        for (Node child = node.getFirstChild(); child != null; child = child.getNextSibling()) {
            collectNamespaces(child, namespaces);
        }
    }

    private Map<String, String> parseQueryString(String queryString) {
        if (queryString == null || queryString.isBlank()) return Map.of();
        Map<String, String> map = new LinkedHashMap<>();
        for (String pair : queryString.split("&")) {
            int idx = pair.indexOf('=');
            if (idx > 0) {
                map.put(pair.substring(0, idx), pair.substring(idx + 1));
            } else if (!pair.isBlank()) {
                map.put(pair, "");
            }
        }
        return map;
    }

    private record StaticNamespaceContext(Map<String, String> namespaces) implements NamespaceContext {

        @Override
        public String getNamespaceURI(String prefix) {
            if (prefix == null) {
                throw new IllegalArgumentException("Namespace prefix must not be null");
            }
            return namespaces.getOrDefault(prefix, XMLConstants.NULL_NS_URI);
        }

        @Override
        public String getPrefix(String namespaceURI) {
            return namespaces.entrySet().stream()
                    .filter(entry -> entry.getValue().equals(namespaceURI))
                    .map(Map.Entry::getKey)
                    .findFirst()
                    .orElse(null);
        }

        @Override
        public Iterator<String> getPrefixes(String namespaceURI) {
            ArrayList<String> prefixes = new ArrayList<>();
            namespaces.forEach((prefix, uri) -> {
                if (uri.equals(namespaceURI)) {
                    prefixes.add(prefix);
                }
            });
            return prefixes.iterator();
        }
    }

    private record TemplateUsage(boolean hasXPath, boolean hasJsonBodyPath) {
    }

    private record FunctionCall(String name, List<String> arguments) {
    }
}
