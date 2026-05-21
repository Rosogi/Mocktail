package com.rosogisoft.service;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class StarlarkFunctionExecutor {

    private static final int MAX_CALL_DEPTH = 16;

    public Object execute(MockFunctionDefinition definition,
                          List<Object> args,
                          TemplateRenderContext context) {
        return execute(definition, args, context, 0);
    }

    Object execute(MockFunctionDefinition definition,
                   List<Object> args,
                   TemplateRenderContext context,
                   int depth) {
        if (definition == null || !definition.enabled()) {
            throw new StarlarkFunctionException("Function is disabled or not found.");
        }
        if (depth > MAX_CALL_DEPTH) {
            throw new StarlarkFunctionException("Function call depth limit exceeded.");
        }
        ParsedFunction parsed = parse(definition.sourceCode(), definition.name());
        Map<String, Object> variables = baseVariables(context);
        bindArguments(parsed.parameters(), args, variables);
        EvalResult result = executeBlock(parsed.lines(), 0, parsed.lines().size(), variables, context, depth);
        return result.returned() ? result.value() : "";
    }

    public ValidationResult validate(String sourceCode) {
        try {
            parse(sourceCode, "main");
            return new ValidationResult(true, "OK");
        } catch (RuntimeException e) {
            return new ValidationResult(false, e.getMessage());
        }
    }

    private Map<String, Object> baseVariables(TemplateRenderContext context) {
        Map<String, Object> result = new HashMap<>();
        result.put("runtime", new MockFunctionRuntime(context));
        result.put("request", Map.of(
                "method", nullToEmpty(context.method()),
                "path", nullToEmpty(context.path()),
                "query", context.queryParams(),
                "headers", context.headers(),
                "body", nullToEmpty(context.requestBody())));
        result.put("env", context.environmentContext().variables());
        result.put("global", context.environmentContext().globals());
        return result;
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private void bindArguments(List<Parameter> parameters,
                               List<Object> args,
                               Map<String, Object> variables) {
        List<Object> safeArgs = args != null ? args : List.of();
        for (int i = 0; i < parameters.size(); i++) {
            Parameter parameter = parameters.get(i);
            Object value = i < safeArgs.size() ? safeArgs.get(i) : parameter.defaultValue();
            variables.put(parameter.name(), value);
        }
    }

    private ParsedFunction parse(String sourceCode, String fallbackName) {
        if (sourceCode == null || sourceCode.isBlank()) {
            throw new StarlarkFunctionException("Function source is empty.");
        }
        String[] rawLines = sourceCode.replace("\r\n", "\n").replace('\r', '\n').split("\n");
        int defIndex = -1;
        String defLine = null;
        for (int i = 0; i < rawLines.length; i++) {
            String trimmed = rawLines[i].trim();
            if (trimmed.startsWith("def ") && trimmed.endsWith(":")) {
                defIndex = i;
                defLine = trimmed;
                break;
            }
        }
        if (defIndex < 0 || defLine == null) {
            throw new StarlarkFunctionException("Function must contain def main(...):");
        }

        int open = defLine.indexOf('(');
        int close = defLine.lastIndexOf(')');
        if (open < 0 || close < open) {
            throw new StarlarkFunctionException("Function definition has invalid arguments.");
        }
        String name = defLine.substring(4, open).trim();
        if (name.isBlank()) {
            name = fallbackName;
        }
        List<Parameter> parameters = parseParameters(defLine.substring(open + 1, close));
        int baseIndent = indent(rawLines[defIndex]);
        List<Line> lines = new ArrayList<>();
        for (int i = defIndex + 1; i < rawLines.length; i++) {
            String line = rawLines[i];
            String trimmed = stripComment(line).trim();
            if (trimmed.isBlank()) {
                continue;
            }
            int lineIndent = indent(line);
            if (lineIndent <= baseIndent) {
                continue;
            }
            lines.add(new Line(lineIndent - baseIndent - 4, trimmed));
        }
        if (lines.isEmpty()) {
            throw new StarlarkFunctionException("Function body is empty.");
        }
        return new ParsedFunction(name, parameters, lines);
    }

    private List<Parameter> parseParameters(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        List<Parameter> result = new ArrayList<>();
        for (String part : splitArgs(raw)) {
            String value = part.trim();
            if (value.isBlank()) {
                continue;
            }
            int eq = topLevelEquals(value);
            if (eq >= 0) {
                String name = parameterName(value.substring(0, eq));
                Object defaultValue = new ExpressionEvaluator(Map.of(), null, 0)
                        .eval(value.substring(eq + 1).trim());
                result.add(new Parameter(name, defaultValue));
            } else {
                result.add(new Parameter(parameterName(value), null));
            }
        }
        return result;
    }

    private String parameterName(String raw) {
        String value = raw != null ? raw.trim() : "";
        if (value.contains(":")) {
            value = value.substring(0, value.indexOf(':')).trim();
        } else if (value.contains(" ")) {
            value = value.substring(0, value.indexOf(' ')).trim();
        }
        if (value.isBlank()) {
            throw new StarlarkFunctionException("Function argument has no name.");
        }
        return value;
    }

    private int topLevelEquals(String value) {
        char quote = 0;
        int depth = 0;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (quote != 0) {
                if (c == quote && value.charAt(i - 1) != '\\') {
                    quote = 0;
                }
                continue;
            }
            if (c == '\'' || c == '"') {
                quote = c;
            } else if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
            } else if (c == '=' && depth == 0) {
                return i;
            }
        }
        return -1;
    }

    private String stripComment(String line) {
        char quote = 0;
        boolean escaped = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (escaped) {
                escaped = false;
                continue;
            }
            if (c == '\\') {
                escaped = true;
                continue;
            }
            if (quote != 0) {
                if (c == quote) {
                    quote = 0;
                }
                continue;
            }
            if (c == '\'' || c == '"') {
                quote = c;
                continue;
            }
            if (c == '#') {
                return line.substring(0, i);
            }
        }
        return line;
    }

    private int indent(String line) {
        int count = 0;
        while (count < line.length() && line.charAt(count) == ' ') {
            count++;
        }
        return count;
    }

    private EvalResult executeBlock(List<Line> lines,
                                    int start,
                                    int end,
                                    Map<String, Object> variables,
                                    TemplateRenderContext context,
                                    int depth) {
        int i = start;
        while (i < end) {
            Line line = lines.get(i);
            String text = line.text();
            if (text.startsWith("return ")) {
                Object value = evaluator(variables, context, depth).eval(text.substring("return ".length()).trim());
                return new EvalResult(value, true);
            }
            if (text.startsWith("if ") && text.endsWith(":")) {
                int ifStart = i + 1;
                int ifEnd = blockEnd(lines, ifStart, end, line.indent());
                int elseIndex = elseIndex(lines, ifEnd, end, line.indent());
                int afterIf = elseIndex >= 0 ? blockEnd(lines, elseIndex + 1, end, line.indent()) : ifEnd;
                boolean condition = truthy(evaluator(variables, context, depth)
                        .eval(text.substring(3, text.length() - 1).trim()));
                if (condition) {
                    EvalResult result = executeBlock(lines, ifStart, ifEnd, variables, context, depth);
                    if (result.returned()) return result;
                } else if (elseIndex >= 0) {
                    EvalResult result = executeBlock(lines, elseIndex + 1, afterIf, variables, context, depth);
                    if (result.returned()) return result;
                }
                i = afterIf;
                continue;
            }
            if (text.startsWith("else:")) {
                i++;
                continue;
            }
            int assignment = assignmentIndex(text);
            if (assignment > 0) {
                String name = text.substring(0, assignment).trim();
                Object value = evaluator(variables, context, depth).eval(text.substring(assignment + 1).trim());
                variables.put(name, value);
                i++;
                continue;
            }
            evaluator(variables, context, depth).eval(text);
            i++;
        }
        return new EvalResult(null, false);
    }

    private int blockEnd(List<Line> lines, int start, int end, int parentIndent) {
        int i = start;
        while (i < end && lines.get(i).indent() > parentIndent) {
            i++;
        }
        return i;
    }

    private int elseIndex(List<Line> lines, int start, int end, int parentIndent) {
        if (start < end && lines.get(start).indent() == parentIndent &&
                lines.get(start).text().startsWith("else:")) {
            return start;
        }
        return -1;
    }

    private int assignmentIndex(String text) {
        char quote = 0;
        int depth = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (quote != 0) {
                if (c == quote && text.charAt(i - 1) != '\\') quote = 0;
                continue;
            }
            if (c == '\'' || c == '"') quote = c;
            else if (c == '(') depth++;
            else if (c == ')') depth--;
            else if (c == '=' && depth == 0) {
                char prev = i > 0 ? text.charAt(i - 1) : 0;
                char next = i + 1 < text.length() ? text.charAt(i + 1) : 0;
                if (prev != '=' && prev != '!' && prev != '<' && prev != '>' && next != '=') {
                    return i;
                }
            }
        }
        return -1;
    }

    private ExpressionEvaluator evaluator(Map<String, Object> variables,
                                          TemplateRenderContext context,
                                          int depth) {
        return new ExpressionEvaluator(variables, context, depth);
    }

    private boolean truthy(Object value) {
        if (value == null) return false;
        if (value instanceof Boolean bool) return bool;
        if (value instanceof Number number) return number.doubleValue() != 0d;
        if (value instanceof String text) return !text.isEmpty();
        return true;
    }

    private List<String> splitArgs(String raw) {
        List<String> result = new ArrayList<>();
        char quote = 0;
        boolean escaped = false;
        int depth = 0;
        int start = 0;
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (escaped) {
                escaped = false;
                continue;
            }
            if (c == '\\') {
                escaped = true;
                continue;
            }
            if (quote != 0) {
                if (c == quote) quote = 0;
                continue;
            }
            if (c == '\'' || c == '"') quote = c;
            else if (c == '(') depth++;
            else if (c == ')') depth--;
            else if (c == ',' && depth == 0) {
                result.add(raw.substring(start, i));
                start = i + 1;
            }
        }
        result.add(raw.substring(start));
        return result;
    }

    public record ValidationResult(boolean valid, String message) {
    }

    private record ParsedFunction(String name, List<Parameter> parameters, List<Line> lines) {
    }

    private record Parameter(String name, Object defaultValue) {
    }

    private record Line(int indent, String text) {
    }

    private record EvalResult(Object value, boolean returned) {
    }

    private class ExpressionEvaluator {
        private final Map<String, Object> variables;
        private final TemplateRenderContext context;
        private final int depth;

        private ExpressionEvaluator(Map<String, Object> variables,
                                    TemplateRenderContext context,
                                    int depth) {
            this.variables = variables;
            this.context = context;
            this.depth = depth;
        }

        private Object eval(String expression) {
            String expr = expression != null ? expression.trim() : "";
            if (expr.isEmpty()) {
                return "";
            }
            expr = unwrap(expr);
            if (expr.startsWith("not ")) {
                return !truthy(eval(expr.substring(4)));
            }
            for (String op : List.of(" or ", " and ")) {
                int idx = findTopLevel(expr, op);
                if (idx >= 0) {
                    Object left = eval(expr.substring(0, idx));
                    if (" or ".equals(op)) {
                        return truthy(left) || truthy(eval(expr.substring(idx + op.length())));
                    }
                    return truthy(left) && truthy(eval(expr.substring(idx + op.length())));
                }
            }
            for (String op : List.of("==", "!=", ">=", "<=", ">", "<")) {
                int idx = findTopLevel(expr, op);
                if (idx >= 0) {
                    return compare(eval(expr.substring(0, idx)), eval(expr.substring(idx + op.length())), op);
                }
            }
            for (String op : List.of("+", "-")) {
                int idx = findTopLevelRight(expr, op);
                if (idx > 0) {
                    return arithmetic(eval(expr.substring(0, idx)), eval(expr.substring(idx + 1)), op);
                }
            }
            for (String op : List.of("*", "/", "%")) {
                int idx = findTopLevelRight(expr, op);
                if (idx > 0) {
                    return arithmetic(eval(expr.substring(0, idx)), eval(expr.substring(idx + 1)), op);
                }
            }
            if (isCall(expr)) {
                return call(expr);
            }
            if (isQuoted(expr)) {
                return unquote(expr);
            }
            if ("None".equals(expr) || "null".equals(expr)) return null;
            if ("True".equals(expr) || "true".equals(expr)) return true;
            if ("False".equals(expr) || "false".equals(expr)) return false;
            if (expr.matches("-?\\d+")) return Long.parseLong(expr);
            if (expr.matches("-?\\d+\\.\\d+")) return Double.parseDouble(expr);
            return resolveVariable(expr);
        }

        private String unwrap(String expr) {
            while (expr.startsWith("(") && expr.endsWith(")") && balanced(expr.substring(1, expr.length() - 1))) {
                expr = expr.substring(1, expr.length() - 1).trim();
            }
            return expr;
        }

        private boolean balanced(String expr) {
            return findUnbalancedClose(expr) < 0 && depthAtEnd(expr) == 0;
        }

        private int findUnbalancedClose(String expr) {
            int depth = 0;
            char quote = 0;
            for (int i = 0; i < expr.length(); i++) {
                char c = expr.charAt(i);
                if (quote != 0) {
                    if (c == quote && expr.charAt(i - 1) != '\\') quote = 0;
                    continue;
                }
                if (c == '\'' || c == '"') quote = c;
                else if (c == '(') depth++;
                else if (c == ')' && --depth < 0) return i;
            }
            return -1;
        }

        private int depthAtEnd(String expr) {
            int depth = 0;
            char quote = 0;
            for (int i = 0; i < expr.length(); i++) {
                char c = expr.charAt(i);
                if (quote != 0) {
                    if (c == quote && expr.charAt(i - 1) != '\\') quote = 0;
                    continue;
                }
                if (c == '\'' || c == '"') quote = c;
                else if (c == '(') depth++;
                else if (c == ')') depth--;
            }
            return depth;
        }

        private int findTopLevel(String expr, String op) {
            return findTopLevel(expr, op, false);
        }

        private int findTopLevelRight(String expr, String op) {
            return findTopLevel(expr, op, true);
        }

        private int findTopLevel(String expr, String op, boolean fromRight) {
            int depth = 0;
            char quote = 0;
            if (fromRight) {
                for (int i = expr.length() - op.length(); i >= 0; i--) {
                    char c = expr.charAt(i);
                    if (c == ')' && quote == 0) depth++;
                    else if (c == '(' && quote == 0) depth--;
                    else if ((c == '\'' || c == '"') && (i == 0 || expr.charAt(i - 1) != '\\')) {
                        quote = quote == 0 ? c : (quote == c ? 0 : quote);
                    }
                    if (depth == 0 && quote == 0 && expr.startsWith(op, i)) return i;
                }
                return -1;
            }
            for (int i = 0; i <= expr.length() - op.length(); i++) {
                char c = expr.charAt(i);
                if (quote != 0) {
                    if (c == quote && (i == 0 || expr.charAt(i - 1) != '\\')) quote = 0;
                    continue;
                }
                if (c == '\'' || c == '"') quote = c;
                else if (c == '(') depth++;
                else if (c == ')') depth--;
                if (depth == 0 && expr.startsWith(op, i)) return i;
            }
            return -1;
        }

        private boolean compare(Object left, Object right, String op) {
            int comparison;
            if (left instanceof Number || right instanceof Number) {
                comparison = Double.compare(toDouble(left), toDouble(right));
            } else {
                comparison = String.valueOf(left).compareTo(String.valueOf(right));
            }
            return switch (op) {
                case "==" -> valuesEqual(left, right);
                case "!=" -> !valuesEqual(left, right);
                case ">" -> comparison > 0;
                case "<" -> comparison < 0;
                case ">=" -> comparison >= 0;
                case "<=" -> comparison <= 0;
                default -> false;
            };
        }

        private boolean valuesEqual(Object left, Object right) {
            if (left == null || right == null) {
                return left == right;
            }
            if (left instanceof Number || right instanceof Number) {
                return Double.compare(toDouble(left), toDouble(right)) == 0;
            }
            return String.valueOf(left).equals(String.valueOf(right));
        }

        private Object arithmetic(Object left, Object right, String op) {
            if ("+".equals(op) && (!(left instanceof Number) || !(right instanceof Number))) {
                return stringify(left) + stringify(right);
            }
            double l = toDouble(left);
            double r = toDouble(right);
            return switch (op) {
                case "+" -> numeric(l + r);
                case "-" -> numeric(l - r);
                case "*" -> numeric(l * r);
                case "/" -> r == 0d ? 0L : numeric(l / r);
                case "%" -> r == 0d ? 0L : numeric(l % r);
                default -> 0L;
            };
        }

        private Object numeric(double value) {
            long asLong = (long) value;
            return value == asLong ? asLong : value;
        }

        private boolean isCall(String expr) {
            int open = expr.indexOf('(');
            return open > 0 && expr.endsWith(")") && balanced(expr.substring(open + 1, expr.length() - 1));
        }

        private Object call(String expr) {
            int open = expr.indexOf('(');
            String name = expr.substring(0, open).trim();
            List<Object> args = splitArgs(expr.substring(open + 1, expr.length() - 1)).stream()
                    .filter(arg -> !arg.trim().isEmpty())
                    .map(this::eval)
                    .toList();
            int dot = lastTopLevelDot(name);
            if (dot > 0) {
                Object target = eval(name.substring(0, dot));
                String method = name.substring(dot + 1);
                return callMethod(target, method, args);
            }
            return callGlobal(name, args);
        }

        private int lastTopLevelDot(String name) {
            int depth = 0;
            for (int i = name.length() - 1; i >= 0; i--) {
                char c = name.charAt(i);
                if (c == ')') depth++;
                else if (c == '(') depth--;
                else if (c == '.' && depth == 0) return i;
            }
            return -1;
        }

        private Object callGlobal(String name, List<Object> args) {
            return switch (name) {
                case "str" -> stringify(arg(args, 0));
                case "int" -> (long) toDouble(arg(args, 0));
                case "len" -> stringify(arg(args, 0)).length();
                case "upper" -> stringify(arg(args, 0)).toUpperCase(Locale.ROOT);
                case "lower" -> stringify(arg(args, 0)).toLowerCase(Locale.ROOT);
                case "replace" -> stringify(arg(args, 0)).replace(stringify(arg(args, 1)), stringify(arg(args, 2)));
                case "startswith" -> stringify(arg(args, 0)).startsWith(stringify(arg(args, 1)));
                case "endswith" -> stringify(arg(args, 0)).endsWith(stringify(arg(args, 1)));
                case "contains" -> stringify(arg(args, 0)).contains(stringify(arg(args, 1)));
                case "substring" -> substring(arg(args, 0), arg(args, 1), arg(args, 2));
                default -> throw new StarlarkFunctionException("Unknown function: " + name);
            };
        }

        private Object callMethod(Object target, String method, List<Object> args) {
            if (target instanceof MockFunctionRuntime runtime) {
                return switch (method) {
                    case "uuid" -> runtime.uuid(arg(args, 0));
                    case "random_int" -> runtime.random_int(arg(args, 0), arg(args, 1));
                    case "random_digits" -> runtime.random_digits(arg(args, 0));
                    case "random_alnum" -> runtime.random_alnum(arg(args, 0));
                    case "now_epoch_millis" -> runtime.now_epoch_millis();
                    case "sequence" -> runtime.sequence(arg(args, 0));
                    case "upper" -> runtime.upper(arg(args, 0));
                    case "lower" -> runtime.lower(arg(args, 0));
                    case "replace" -> runtime.replace(arg(args, 0), arg(args, 1), arg(args, 2));
                    case "substring" -> runtime.substring(arg(args, 0), arg(args, 1), arg(args, 2));
                    default -> throw new StarlarkFunctionException("Unknown runtime method: " + method);
                };
            }
            String text = stringify(target);
            return switch (method) {
                case "startswith" -> text.startsWith(stringify(arg(args, 0)));
                case "endswith" -> text.endsWith(stringify(arg(args, 0)));
                case "contains" -> text.contains(stringify(arg(args, 0)));
                case "replace" -> text.replace(stringify(arg(args, 0)), stringify(arg(args, 1)));
                case "upper" -> text.toUpperCase(Locale.ROOT);
                case "lower" -> text.toLowerCase(Locale.ROOT);
                case "strip", "trim" -> text.trim();
                case "substring" -> substring(text, arg(args, 0), arg(args, 1));
                default -> throw new StarlarkFunctionException("Unknown method: " + method);
            };
        }

        private Object substring(Object value, Object start, Object end) {
            String text = stringify(value);
            int from = Math.max(0, Math.min(text.length(), (int) toDouble(start)));
            int to = end == null ? text.length() : Math.max(from, Math.min(text.length(), (int) toDouble(end)));
            return text.substring(from, to);
        }

        private Object arg(List<Object> args, int index) {
            return index < args.size() ? args.get(index) : null;
        }

        private Object resolveVariable(String name) {
            if (variables.containsKey(name)) {
                return variables.get(name);
            }
            String[] parts = name.split("\\.");
            Object current = variables.get(parts[0]);
            if (current == null) {
                throw new StarlarkFunctionException(unknownVariableMessage(parts[0]));
            }
            for (int i = 1; i < parts.length; i++) {
                if (current instanceof Map<?, ?> map) {
                    current = lookupMap(map, parts[i]);
                } else {
                    throw new StarlarkFunctionException("Unknown property: " + parts[i]);
                }
            }
            return current != null ? current : "";
        }

        private Object lookupMap(Map<?, ?> map, String key) {
            if (map.containsKey(key)) {
                return map.get(key);
            }
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (String.valueOf(entry.getKey()).equalsIgnoreCase(key)) {
                    return entry.getValue();
                }
            }
            return "";
        }

        private String unknownVariableMessage(String name) {
            String suggestion = closestVariable(name);
            if (suggestion == null) {
                return "Unknown variable: " + name;
            }
            return "Unknown variable: " + name + ". Did you mean " + suggestion + "?";
        }

        private String closestVariable(String name) {
            return variables.keySet().stream()
                    .filter(candidate -> !List.of("runtime", "request", "env", "global").contains(candidate))
                    .filter(candidate -> distance(name, candidate) <= 2)
                    .min((left, right) -> Integer.compare(distance(name, left), distance(name, right)))
                    .orElse(null);
        }

        private int distance(String left, String right) {
            String a = left != null ? left : "";
            String b = right != null ? right : "";
            int[][] dp = new int[a.length() + 1][b.length() + 1];
            for (int i = 0; i <= a.length(); i++) {
                dp[i][0] = i;
            }
            for (int j = 0; j <= b.length(); j++) {
                dp[0][j] = j;
            }
            for (int i = 1; i <= a.length(); i++) {
                for (int j = 1; j <= b.length(); j++) {
                    int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                    dp[i][j] = Math.min(
                            Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                            dp[i - 1][j - 1] + cost);
                    if (i > 1 && j > 1 &&
                            a.charAt(i - 1) == b.charAt(j - 2) &&
                            a.charAt(i - 2) == b.charAt(j - 1)) {
                        dp[i][j] = Math.min(dp[i][j], dp[i - 2][j - 2] + 1);
                    }
                }
            }
            return dp[a.length()][b.length()];
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
                char c = body.charAt(i);
                if (escaped) {
                    result.append(switch (c) {
                        case 'n' -> '\n';
                        case 'r' -> '\r';
                        case 't' -> '\t';
                        default -> c;
                    });
                    escaped = false;
                    continue;
                }
                if (c == '\\') escaped = true;
                else result.append(c);
            }
            if (escaped) result.append('\\');
            return result.toString();
        }

        private double toDouble(Object value) {
            if (value instanceof Number number) {
                return number.doubleValue();
            }
            try {
                return Double.parseDouble(stringify(value));
            } catch (NumberFormatException e) {
                return 0d;
            }
        }

        private String stringify(Object value) {
            return value == null ? "" : String.valueOf(value);
        }
    }
}
