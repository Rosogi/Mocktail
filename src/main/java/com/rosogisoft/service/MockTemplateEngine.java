package com.rosogisoft.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
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
import java.util.Locale;
import java.util.Map;
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

    public MockTemplateEngine(EnvironmentTemplateService environmentTemplateService,
                              TemplateExpressionParser expressionParser) {
        this.environmentTemplateService = environmentTemplateService;
        this.expressionParser = expressionParser;
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

        if (template == null || !template.contains("{{")) {
            return template;
        }

        TemplateUsage usage = inspectTemplate(template);
        JsonNode bodyJson = usage.hasJsonBodyPath() ? parseJson(requestBody) : null;
        Document bodyXml = usage.hasXPath() ? parseXml(requestBody) : null;
        Map<String, String> xmlNamespaces = bodyXml != null ? collectNamespaces(bodyXml) : Map.of();
        Map<String, String> params = parseQueryString(queryParams);
        Map<String, String> safeHeaders = headers != null ? headers : Map.of();

        Matcher matcher = PLACEHOLDER.matcher(template);
        StringBuilder result = new StringBuilder(template.length() + 64);

        while (matcher.find()) {
            String expr = matcher.group(1).trim();
            String replacement = resolveExpression(expr, method, path, params, safeHeaders, bodyJson, bodyXml,
                    xmlNamespaces, environmentContext);
            matcher.appendReplacement(result,
                    replacement != null
                            ? Matcher.quoteReplacement(replacement)
                            : matcher.group(0));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private String resolveExpression(String expr,
                                     String method,
                                     String path,
                                     Map<String, String> params,
                                     Map<String, String> headers,
                                     JsonNode bodyJson,
                                     Document bodyXml,
                                     Map<String, String> xmlNamespaces,
                                     EnvironmentContext environmentContext) {
        TemplateExpressionParser.TemplateExpression expression = expressionParser.parse(expr);
        String resolved = resolveLookup(expression.lookup(), method, path, params, headers, bodyJson, bodyXml,
                xmlNamespaces, environmentContext);
        return resolved != null
                ? resolved
                : expression.fallback().map(TemplateExpressionParser.TemplateLiteral::value).orElse(null);
    }

    private String resolveLookup(String expr,
                                 String method,
                                 String path,
                                 Map<String, String> params,
                                 Map<String, String> headers,
                                 JsonNode bodyJson,
                                 Document bodyXml,
                                 Map<String, String> xmlNamespaces,
                                 EnvironmentContext environmentContext) {
        if (expr.equalsIgnoreCase("request.method")) return method;
        if (expr.equalsIgnoreCase("request.path")) return path;

        String normalizedExpr = expr.toLowerCase(Locale.ROOT);

        if (normalizedExpr.startsWith("env.") || normalizedExpr.startsWith("global.")) {
            return environmentTemplateService.resolveExpression(expr, environmentContext, false);
        }

        if (normalizedExpr.startsWith("param.")) {
            String key = expr.substring(6);
            return params.get(key);
        }

        if (normalizedExpr.startsWith("header.")) {
            String headerName = expr.substring(7);
            return headers.entrySet().stream()
                    .filter(e -> e.getKey().equalsIgnoreCase(headerName))
                    .map(Map.Entry::getValue)
                    .findFirst()
                    .orElse(null);
        }

        if (normalizedExpr.startsWith(XPATH_PREFIX)) {
            String xpath = expr.substring(XPATH_PREFIX.length()).trim();
            return resolveXPath(bodyXml, xmlNamespaces, xpath);
        }

        if (bodyJson != null) {
            return resolveJsonPath(bodyJson, expr);
        }

        return null;
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
}
