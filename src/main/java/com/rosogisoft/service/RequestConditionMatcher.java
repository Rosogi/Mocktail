package com.rosogisoft.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rosogisoft.domain.MockDefinition;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.XMLConstants;
import javax.xml.namespace.NamespaceContext;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPathFactory;
import java.io.StringReader;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

@Slf4j
@Service
public class RequestConditionMatcher {

    private static final String MODE_ADVANCED = "advanced";
    private static final String SOAP11_NS = "http://schemas.xmlsoap.org/soap/envelope/";
    private static final String SOAP12_NS = "http://www.w3.org/2003/05/soap-envelope";

    private final ObjectMapper mapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public boolean matches(MockDefinition mock,
                           String queryString,
                           Map<String, String> headers,
                           String body) {
        if (!MODE_ADVANCED.equalsIgnoreCase(mock.getRequestMatchMode())) {
            return bodyContainsMatches(mock.getRequestBodyContains(), body);
        }

        List<ConditionGroup> groups = parseGroups(mock.getRequestMatchGroups());
        if (groups == null) {
            return false;
        }
        if (groups.isEmpty()) {
            return true;
        }

        RequestContext context = new RequestContext(queryString, headers, body);
        Boolean result = null;
        for (ConditionGroup group : groups) {
            boolean groupResult = evaluateGroup(group, context);
            result = combine(result, groupResult, group.connector());
        }
        return Boolean.TRUE.equals(result);
    }

    private boolean bodyContainsMatches(String contains, String body) {
        if (contains == null || contains.isBlank()) {
            return true;
        }
        return body != null && body.contains(contains);
    }

    private List<ConditionGroup> parseGroups(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        try {
            return mapper.readValue(raw, new TypeReference<>() {});
        } catch (Exception e) {
            log.debug("Could not parse advanced request match groups", e);
            return null;
        }
    }

    private boolean evaluateGroup(ConditionGroup group, RequestContext context) {
        if (group.conditions() == null || group.conditions().isEmpty()) {
            return false;
        }

        Boolean result = null;
        for (Condition condition : group.conditions()) {
            if (!isComplete(condition)) {
                continue;
            }
            boolean conditionResult = evaluateCondition(condition, context);
            result = combine(result, conditionResult, condition.connector());
        }
        return Boolean.TRUE.equals(result);
    }

    private Boolean combine(Boolean current, boolean next, String connector) {
        if (current == null) {
            return next;
        }
        return "or".equalsIgnoreCase(connector) ? current || next : current && next;
    }

    private boolean isComplete(Condition condition) {
        if (condition == null) {
            return false;
        }
        String source = lower(condition.source());
        String operator = lower(condition.operator());
        if (!"raw_body".equals(source) && isBlank(condition.target())) {
            return false;
        }
        return "exists".equals(operator) || !isBlank(condition.value());
    }

    private boolean evaluateCondition(Condition condition, RequestContext context) {
        ResolvedValue actual = resolve(condition, context);
        String operator = lower(condition.operator());
        if ("exists".equals(operator)) {
            return actual.exists();
        }
        if (!actual.exists()) {
            return false;
        }

        String actualValue = actual.value() != null ? actual.value() : "";
        String expectedValue = condition.value() != null ? condition.value() : "";
        if ("raw_body".equals(lower(condition.source()))) {
            actualValue = normalizeWhitespace(actualValue, condition.whitespace());
            expectedValue = normalizeWhitespace(expectedValue, condition.whitespace());
        }

        return switch (operator) {
            case "contains" -> actualValue.contains(expectedValue);
            case "regex" -> regexMatches(actualValue, expectedValue);
            default -> actualValue.equals(expectedValue);
        };
    }

    private ResolvedValue resolve(Condition condition, RequestContext context) {
        return switch (lower(condition.source())) {
            case "json_body" -> resolveJson(context, condition.target());
            case "xml_body" -> resolveXml(context, condition);
            case "query_param" -> resolveMap(context.queryParams(), condition.target(), false);
            case "header" -> resolveMap(context.headers(), condition.target(), true);
            case "raw_body" -> new ResolvedValue(context.body(), context.body() != null);
            default -> ResolvedValue.missing();
        };
    }

    private ResolvedValue resolveJson(RequestContext context, String path) {
        JsonNode current = context.jsonBody();
        if (current == null || isBlank(path)) {
            return ResolvedValue.missing();
        }
        for (String part : path.split("\\.")) {
            if (current == null || current.isNull()) {
                return ResolvedValue.missing();
            }
            if (current.isArray() && part.matches("\\d+")) {
                current = current.get(Integer.parseInt(part));
            } else if (current.isObject()) {
                current = current.get(part);
            } else {
                return ResolvedValue.missing();
            }
        }
        if (current == null || current.isNull()) {
            return ResolvedValue.missing();
        }
        return new ResolvedValue(current.isValueNode() ? current.asText() : current.toString(), true);
    }

    private ResolvedValue resolveXml(RequestContext context, Condition condition) {
        Document document = context.xmlBody();
        if (document == null || isBlank(condition.target())) {
            return ResolvedValue.missing();
        }
        if ("xpath".equalsIgnoreCase(condition.xmlMode()) || looksLikeXPath(condition.target())) {
            try {
                var xpath = XPathFactory.newInstance().newXPath();
                xpath.setNamespaceContext(new StaticNamespaceContext(context.xmlNamespaces()));
                String value = trimToNull(xpath.evaluate(condition.target(), document));
                return new ResolvedValue(value, value != null);
            } catch (Exception e) {
                log.debug("Could not evaluate XPath condition '{}'", condition.target(), e);
                return ResolvedValue.missing();
            }
        }

        Node node = findFirstByLocalName(document.getDocumentElement(), condition.target());
        return node != null
                ? new ResolvedValue(trimToNull(node.getTextContent()), true)
                : ResolvedValue.missing();
    }

    private boolean looksLikeXPath(String target) {
        if (target == null) {
            return false;
        }
        String value = target.trim();
        return value.startsWith("/")
                || value.startsWith("(")
                || value.startsWith(".")
                || value.contains("::")
                || value.contains("[")
                || value.contains("@")
                || value.contains("local-name()")
                || value.matches("(?i)^(string|count|boolean|normalize-space|name|local-name)\\s*\\(.*");
    }

    private Node findFirstByLocalName(Node node, String localName) {
        if (node == null || isBlank(localName)) {
            return null;
        }
        String nodeLocalName = node.getLocalName() != null ? node.getLocalName() : node.getNodeName();
        if (localName.equals(nodeLocalName)) {
            return node;
        }
        NodeList children = node.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node found = findFirstByLocalName(children.item(i), localName);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private ResolvedValue resolveMap(Map<String, String> map, String key, boolean caseInsensitive) {
        if (map == null || isBlank(key)) {
            return ResolvedValue.missing();
        }
        return map.entrySet().stream()
                .filter(e -> caseInsensitive ? e.getKey().equalsIgnoreCase(key) : e.getKey().equals(key))
                .findFirst()
                .map(e -> new ResolvedValue(e.getValue(), true))
                .orElseGet(ResolvedValue::missing);
    }

    private boolean regexMatches(String actualValue, String expectedPattern) {
        try {
            return Pattern.compile(expectedPattern, Pattern.DOTALL).matcher(actualValue).find();
        } catch (PatternSyntaxException e) {
            return false;
        }
    }

    private String normalizeWhitespace(String value, String mode) {
        if (value == null) {
            return "";
        }
        return switch (lower(mode)) {
            case "collapse" -> value.trim().replaceAll("\\s+", " ");
            case "ignore" -> value.replaceAll("\\s+", "");
            default -> value;
        };
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private JsonNode parseJson(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            return mapper.readTree(body);
        } catch (Exception e) {
            return null;
        }
    }

    private Document parseXml(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
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

    private Map<String, String> parseQueryString(String queryString) {
        if (queryString == null || queryString.isBlank()) {
            return Map.of();
        }
        Map<String, String> map = new LinkedHashMap<>();
        for (String pair : queryString.split("&")) {
            if (pair.isBlank()) {
                continue;
            }
            int idx = pair.indexOf('=');
            String key = idx >= 0 ? pair.substring(0, idx) : pair;
            String value = idx >= 0 ? pair.substring(idx + 1) : "";
            map.put(urlDecode(key), urlDecode(value));
        }
        return map;
    }

    private String urlDecode(String value) {
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return value;
        }
    }

    private Map<String, String> collectNamespaces(Document document) {
        if (document == null) {
            return Map.of();
        }
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

    private String lower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record ConditionGroup(String connector, List<Condition> conditions) {}

    private record Condition(String connector,
                             String source,
                             String xmlMode,
                             String target,
                             String operator,
                             String value,
                             String whitespace) {}

    private record ResolvedValue(String value, boolean exists) {
        private static ResolvedValue missing() {
            return new ResolvedValue(null, false);
        }
    }

    private class RequestContext {
        private final String queryString;
        private final Map<String, String> headers;
        private final String body;
        private Map<String, String> queryParams;
        private JsonNode jsonBody;
        private Document xmlBody;
        private Map<String, String> xmlNamespaces;

        private RequestContext(String queryString, Map<String, String> headers, String body) {
            this.queryString = queryString;
            this.headers = headers != null ? headers : Map.of();
            this.body = body;
        }

        private Map<String, String> queryParams() {
            if (queryParams == null) {
                queryParams = parseQueryString(queryString);
            }
            return queryParams;
        }

        private Map<String, String> headers() {
            return headers;
        }

        private String body() {
            return body;
        }

        private JsonNode jsonBody() {
            if (jsonBody == null) {
                jsonBody = parseJson(body);
            }
            return jsonBody;
        }

        private Document xmlBody() {
            if (xmlBody == null) {
                xmlBody = parseXml(body);
            }
            return xmlBody;
        }

        private Map<String, String> xmlNamespaces() {
            if (xmlNamespaces == null) {
                xmlNamespaces = collectNamespaces(xmlBody());
            }
            return xmlNamespaces;
        }
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
            return namespaces.entrySet().stream()
                    .filter(entry -> entry.getValue().equals(namespaceURI))
                    .map(Map.Entry::getKey)
                    .iterator();
        }
    }
}
