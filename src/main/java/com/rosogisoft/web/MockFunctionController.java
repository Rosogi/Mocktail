package com.rosogisoft.web;

import com.rosogisoft.domain.User;
import com.rosogisoft.service.I18nService;
import com.rosogisoft.service.MockFunctionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/functions")
@RequiredArgsConstructor
public class MockFunctionController {

    private final CurrentUserHelper currentUserHelper;
    private final MockFunctionService functionService;
    private final I18nService i18n;

    @GetMapping
    public String list(Model model) {
        User user = currentUserHelper.currentUser();
        addListModel(model, user);
        return "functions/index";
    }

    @GetMapping("/new")
    public String createForm(Model model) {
        User user = currentUserHelper.currentUser();
        addFormModel(model, user, defaultInput(), null, false, null);
        return "functions/form";
    }

    @PostMapping
    public String create(@ModelAttribute FunctionForm form, Model model, RedirectAttributes ra) {
        User user = currentUserHelper.currentUser();
        try {
            functionService.create(toInput(form), user);
            ra.addFlashAttribute("successMessage", i18n.t("flash.functionCreated"));
            return "redirect:/functions";
        } catch (RuntimeException e) {
            model.addAttribute("errorMessage", e.getMessage());
            addFormModel(model, user, form, null, false, null);
            return "functions/form";
        }
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model, RedirectAttributes ra) {
        User user = currentUserHelper.currentUser();
        return functionService.findUserFunction(id, user).map(function -> {
            addFormModel(model, user, toForm(function), id, true, null);
            return "functions/form";
        }).orElseGet(() -> {
            ra.addFlashAttribute("errorMessage", i18n.t("flash.functionNotFound"));
            return "redirect:/functions";
        });
    }

    @PostMapping("/{id}/edit")
    public String update(@PathVariable Long id,
                         @ModelAttribute FunctionForm form,
                         Model model,
                         RedirectAttributes ra) {
        User user = currentUserHelper.currentUser();
        try {
            boolean updated = functionService.update(id, toInput(form), user).isPresent();
            ra.addFlashAttribute(updated ? "successMessage" : "errorMessage",
                    updated ? i18n.t("flash.functionUpdated") : i18n.t("flash.functionNotFound"));
            return "redirect:/functions";
        } catch (RuntimeException e) {
            model.addAttribute("errorMessage", e.getMessage());
            addFormModel(model, user, form, id, true, null);
            return "functions/form";
        }
    }

    @PostMapping("/test")
    public String test(@ModelAttribute FunctionForm form,
                       @RequestParam(required = false) List<String> testArgTypes,
                       @RequestParam(required = false) List<String> testArgValues,
                       Model model) {
        User user = currentUserHelper.currentUser();
        FunctionForm hydrated = withTestArgValues(form, testArgValues);
        MockFunctionService.TestResult result = functionService.test(toInput(hydrated), parseArgs(testArgTypes, testArgValues));
        addFormModel(model, user, hydrated, hydrated.id(), hydrated.id() != null, result);
        return "functions/form";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes ra) {
        User user = currentUserHelper.currentUser();
        try {
            boolean deleted = functionService.delete(id, user);
            ra.addFlashAttribute(deleted ? "successMessage" : "errorMessage",
                    deleted ? i18n.t("flash.functionDeleted") : i18n.t("flash.functionNotFound"));
        } catch (RuntimeException e) {
            ra.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/functions";
    }

    @PostMapping("/{id}/share")
    public String share(@PathVariable Long id, RedirectAttributes ra) {
        User user = currentUserHelper.currentUser();
        boolean ok = functionService.share(id, user);
        ra.addFlashAttribute(ok ? "successMessage" : "errorMessage",
                ok ? i18n.t("flash.functionShared") : i18n.t("flash.functionNotFound"));
        return "redirect:/functions";
    }

    @PostMapping("/{id}/unshare")
    public String unshare(@PathVariable Long id, RedirectAttributes ra) {
        User user = currentUserHelper.currentUser();
        boolean ok = functionService.unshare(id, user);
        ra.addFlashAttribute(ok ? "successMessage" : "errorMessage",
                ok ? i18n.t("flash.functionUnshared") : i18n.t("flash.functionNotFound"));
        return "redirect:/functions";
    }

    private void addListModel(Model model,
                              User user) {
        model.addAttribute("user", user);
        model.addAttribute("standardFunctions", functionService.standardFunctions());
        model.addAttribute("userFunctions", functionService.userFunctions(user));
    }

    private void addFormModel(Model model,
                              User user,
                              FunctionForm form,
                              Long editingId,
                              boolean editing,
                              MockFunctionService.TestResult testResult) {
        model.addAttribute("user", user);
        model.addAttribute("form", form);
        model.addAttribute("editingId", editingId);
        model.addAttribute("editing", editing);
        model.addAttribute("testResult", testResult);
        model.addAttribute("testParameters", testParameters(form.signatureLabel(), form.sourceCode(), form.testArgValues()));
    }

    private FunctionForm defaultInput() {
        return new FunctionForm(null, "", "", "", "", List.of(), true, false);
    }

    private MockFunctionService.FunctionInput toInput(FunctionForm form) {
        String signature = clean(form.signatureLabel());
        return new MockFunctionService.FunctionInput(
                functionName(signature, form.name()),
                form.description(),
                signature,
                returnType(signature),
                fullSource(form.sourceCode(), signature),
                form.enabled());
    }

    private FunctionForm toForm(MockFunctionService.FunctionView function) {
        return new FunctionForm(
                function.id(),
                function.name(),
                function.description(),
                function.signatureLabel(),
                functionBody(function.sourceCode()),
                List.of(),
                function.enabled(),
                function.readOnly());
    }

    private FunctionForm withTestArgValues(FunctionForm form, List<String> values) {
        return new FunctionForm(
                form.id(),
                form.name(),
                form.description(),
                form.signatureLabel(),
                form.sourceCode(),
                values != null ? values : List.of(),
                form.enabled(),
                form.readOnly());
    }

    private List<Object> parseArgs(List<String> types, List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        List<Object> result = new ArrayList<>();
        for (int i = 0; i < values.size(); i++) {
            String type = i < safeList(types).size() ? safeList(types).get(i) : "string";
            result.add(convertArg(type, values.get(i)));
        }
        return result;
    }

    private Object convertArg(String type, String value) {
        String raw = value != null ? value.trim() : "";
        if (raw.isBlank()) {
            return null;
        }
        String normalizedType = cleanType(type);
        if (isNumberType(normalizedType)) {
            try {
                return raw.contains(".") ? Double.parseDouble(raw) : Long.parseLong(raw);
            } catch (NumberFormatException e) {
                return 0L;
            }
        }
        if (isBooleanType(normalizedType)) {
            return "true".equalsIgnoreCase(raw) || "1".equals(raw) || "yes".equalsIgnoreCase(raw);
        }
        return value;
    }

    private List<TestParameter> testParameters(String signature, String sourceCode, List<String> values) {
        List<SignatureParameter> parameters = signatureParameters(signature);
        if (parameters.isEmpty()) {
            parameters = sourceParameters(sourceCode);
        }
        List<TestParameter> result = new ArrayList<>();
        List<String> safeValues = safeList(values);
        for (int i = 0; i < parameters.size(); i++) {
            SignatureParameter parameter = parameters.get(i);
            String type = cleanType(parameter.type());
            String value = i < safeValues.size() ? safeValues.get(i) : "";
            result.add(new TestParameter(i, parameter.name(), type, value,
                    isNumberType(type), isBooleanType(type)));
        }
        return result;
    }

    private List<SignatureParameter> signatureParameters(String signature) {
        String value = signature != null ? signature.trim() : "";
        int open = value.indexOf('(');
        int close = value.indexOf(')', open + 1);
        if (open < 0 || close < open) {
            return List.of();
        }
        return parseParameterList(value.substring(open + 1, close), true);
    }

    private List<SignatureParameter> sourceParameters(String sourceCode) {
        String source = sourceCode != null ? sourceCode : "";
        for (String line : source.replace("\r\n", "\n").replace('\r', '\n').split("\n")) {
            String trimmed = line.trim();
            if (!trimmed.startsWith("def ") || !trimmed.endsWith(":")) {
                continue;
            }
            int open = trimmed.indexOf('(');
            int close = trimmed.indexOf(')', open + 1);
            if (open >= 0 && close > open) {
                return parseParameterList(trimmed.substring(open + 1, close), false);
            }
        }
        return List.of();
    }

    private List<SignatureParameter> parseParameterList(String raw, boolean typed) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        List<SignatureParameter> result = new ArrayList<>();
        for (String part : raw.split(",")) {
            String item = part.trim();
            if (item.isBlank()) {
                continue;
            }
            int eq = item.indexOf('=');
            if (eq >= 0) {
                item = item.substring(0, eq).trim();
            }
            String name = item;
            String type = "string";
            if (typed) {
                String normalized = item.replace(":", " ").trim().replaceAll("\\s+", " ");
                String[] tokens = normalized.split(" ");
                if (tokens.length >= 2) {
                    name = tokens[0];
                    type = tokens[1];
                }
            }
            name = name.replaceAll("[^A-Za-z0-9_]", "");
            if (!name.isBlank()) {
                result.add(new SignatureParameter(name, type));
            }
        }
        return result;
    }

    private String functionName(String signature, String fallback) {
        String value = clean(signature);
        int open = value.indexOf('(');
        if (open <= 0) {
            String cleanFallback = clean(fallback);
            if (!cleanFallback.isBlank()) {
                return cleanFallback;
            }
            throw new IllegalArgumentException("Function signature must look like fn.functionName(arg string) string.");
        }
        String name = value.substring(0, open).trim();
        if (name.startsWith("fn.")) {
            name = name.substring(3);
        }
        if (name.isBlank()) {
            throw new IllegalArgumentException("Function signature must include a function name.");
        }
        return name;
    }

    private String fullSource(String sourceCode, String signature) {
        String body = functionBody(sourceCode);
        if (body.isBlank()) {
            return "";
        }
        String parameterList = signatureParameters(signature).stream()
                .map(SignatureParameter::name)
                .collect(Collectors.joining(", "));
        return "def main(" + parameterList + "):\n" + indentBody(body);
    }

    private String functionBody(String sourceCode) {
        String source = sourceCode != null ? sourceCode.stripTrailing() : "";
        if (!isFullFunctionSource(source)) {
            return source;
        }
        String[] lines = source.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
        if (lines.length <= 1) {
            return "";
        }
        int commonIndent = Integer.MAX_VALUE;
        for (int i = 1; i < lines.length; i++) {
            if (lines[i].isBlank()) {
                continue;
            }
            commonIndent = Math.min(commonIndent, leadingSpaces(lines[i]));
        }
        int trim = commonIndent == Integer.MAX_VALUE ? 0 : Math.min(commonIndent, 4);
        List<String> bodyLines = new ArrayList<>();
        for (int i = 1; i < lines.length; i++) {
            String line = lines[i];
            bodyLines.add(line.length() >= trim ? line.substring(trim) : line.stripLeading());
        }
        return String.join("\n", bodyLines).stripTrailing();
    }

    private boolean isFullFunctionSource(String source) {
        return clean(source).startsWith("def ");
    }

    private String indentBody(String body) {
        return body.replace("\r\n", "\n")
                .replace('\r', '\n')
                .lines()
                .map(line -> line.isBlank() ? "" : "    " + line)
                .collect(Collectors.joining("\n"));
    }

    private int leadingSpaces(String line) {
        int count = 0;
        while (count < line.length() && line.charAt(count) == ' ') {
            count++;
        }
        return count;
    }

    private String returnType(String signature) {
        String value = signature != null ? signature.trim() : "";
        int close = value.lastIndexOf(')');
        if (close < 0 || close + 1 >= value.length()) {
            return "string";
        }
        String type = value.substring(close + 1).trim();
        if (type.startsWith("->")) {
            type = type.substring(2).trim();
        }
        return cleanType(type.isBlank() ? "string" : type);
    }

    private String cleanType(String type) {
        String value = type != null ? type.trim().toLowerCase(Locale.ROOT) : "string";
        if (value.endsWith("?")) {
            value = value.substring(0, value.length() - 1);
        }
        return value.isBlank() ? "string" : value;
    }

    private boolean isNumberType(String type) {
        String value = cleanType(type);
        return value.equals("number") || value.equals("int") || value.equals("integer") ||
                value.equals("long") || value.equals("float") || value.equals("double");
    }

    private boolean isBooleanType(String type) {
        String value = cleanType(type);
        return value.equals("bool") || value.equals("boolean");
    }

    private List<String> safeList(List<String> values) {
        return values != null ? values : List.of();
    }

    private String clean(String value) {
        return value != null ? value.trim() : "";
    }

    public record FunctionForm(Long id,
                               String name,
                               String description,
                               String signatureLabel,
                               String sourceCode,
                               List<String> testArgValues,
                               boolean enabled,
                               boolean readOnly) {
    }

    public record TestParameter(int index,
                                String name,
                                String type,
                                String value,
                                boolean numberType,
                                boolean booleanType) {
    }

    private record SignatureParameter(String name, String type) {
    }
}
