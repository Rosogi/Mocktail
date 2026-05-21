package com.rosogisoft.service;

public record TemplateSuggestion(String expression,
                                 String scope,
                                 String key,
                                 String preview,
                                 boolean hidden,
                                 String kind,
                                 String label,
                                 String insertText,
                                 String description) {

    public static TemplateSuggestion value(String expression,
                                           String scope,
                                           String key,
                                           String preview,
                                           boolean hidden) {
        return new TemplateSuggestion(
                expression,
                scope,
                key,
                preview,
                hidden,
                "value",
                expression,
                expression,
                "");
    }

    public static TemplateSuggestion function(String expression,
                                              String label,
                                              String insertText,
                                              String description) {
        return new TemplateSuggestion(
                expression,
                "fn",
                "",
                "",
                false,
                "function",
                label,
                insertText,
                description);
    }
}
