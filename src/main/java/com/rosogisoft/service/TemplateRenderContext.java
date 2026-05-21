package com.rosogisoft.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.XMLConstants;
import javax.xml.namespace.NamespaceContext;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class TemplateRenderContext {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String SOAP11_NS = "http://schemas.xmlsoap.org/soap/envelope/";
    private static final String SOAP12_NS = "http://www.w3.org/2003/05/soap-envelope";

    private final Long ownerId;
    private final String method;
    private final String path;
    private final String queryString;
    private final Map<String, String> headers;
    private final String requestBody;
    private final EnvironmentContext environmentContext;
    private final TemplatePhase phase;
    private final Map<String, Object> functionCache;

    private Map<String, String> queryParams;
    private JsonNode bodyJson;
    private Document bodyXml;
    private Map<String, String> xmlNamespaces;

    public TemplateRenderContext(Long ownerId,
                                 String method,
                                 String path,
                                 String queryString,
                                 Map<String, String> headers,
                                 String requestBody,
                                 EnvironmentContext environmentContext,
                                 TemplatePhase phase) {
        this(ownerId, method, path, queryString, headers, requestBody, environmentContext, phase,
                new ConcurrentHashMap<>());
    }

    private TemplateRenderContext(Long ownerId,
                                  String method,
                                  String path,
                                  String queryString,
                                  Map<String, String> headers,
                                  String requestBody,
                                  EnvironmentContext environmentContext,
                                  TemplatePhase phase,
                                  Map<String, Object> functionCache) {
        this.ownerId = ownerId;
        this.method = method;
        this.path = path;
        this.queryString = queryString;
        this.headers = headers != null ? headers : Map.of();
        this.requestBody = requestBody;
        this.environmentContext = environmentContext != null ? environmentContext : EnvironmentContext.empty();
        this.phase = phase != null ? phase : TemplatePhase.RESPONSE;
        this.functionCache = functionCache;
    }

    public TemplateRenderContext withPhase(TemplatePhase nextPhase) {
        return new TemplateRenderContext(ownerId, method, path, queryString, headers, requestBody,
                environmentContext, nextPhase, functionCache);
    }

    public Long ownerId() {
        return ownerId;
    }

    public String method() {
        return method;
    }

    public String path() {
        return path;
    }

    public String queryString() {
        return queryString;
    }

    public Map<String, String> headers() {
        return headers;
    }

    public String requestBody() {
        return requestBody;
    }

    public EnvironmentContext environmentContext() {
        return environmentContext;
    }

    public TemplatePhase phase() {
        return phase;
    }

    public Map<String, Object> functionCache() {
        return functionCache;
    }

    public Map<String, String> queryParams() {
        if (queryParams == null) {
            queryParams = parseQueryString(queryString);
        }
        return queryParams;
    }

    public JsonNode bodyJson() {
        if (bodyJson == null) {
            bodyJson = parseJson(requestBody);
        }
        return bodyJson;
    }

    public Document bodyXml() {
        if (bodyXml == null) {
            bodyXml = parseXml(requestBody);
        }
        return bodyXml;
    }

    public Map<String, String> xmlNamespaces() {
        if (xmlNamespaces == null) {
            Document xml = bodyXml();
            xmlNamespaces = xml != null ? collectNamespaces(xml) : Map.of();
        }
        return xmlNamespaces;
    }

    private Map<String, String> parseQueryString(String query) {
        if (query == null || query.isBlank()) {
            return Map.of();
        }
        Map<String, String> map = new LinkedHashMap<>();
        for (String pair : query.split("&")) {
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

    private JsonNode parseJson(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readTree(body);
        } catch (Exception e) {
            log.debug("Request body is not valid JSON - skipping JSON path resolution");
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
            namespaces.putIfAbsent(prefix != null && !prefix.isBlank() ? prefix : "d", namespaceUri);
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

    public record StaticNamespaceContext(Map<String, String> namespaces) implements NamespaceContext {
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
