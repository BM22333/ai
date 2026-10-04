package me.beiming.aichat.toolCalling;

import me.beiming.aichat.annotation.ToolDef;
import me.beiming.aichat.annotation.ToolParamDef;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 内置工具集：计算器 + 日期时间查询。
 */
@Component
public class BuiltinTools {

    @ToolDef(
        name = "calculator",
        description = "执行数学表达式计算。支持 +, -, *, /, 括号, sqrt(), pow(), sin(), cos(), log(), abs()。返回数值结果字符串。"
    )
    public String calculate(
            @ToolParamDef(name = "expression", type = "string",
                    description = "数学表达式，如 '2 + 3 * 4' 或 'sqrt(pow(3,2) + pow(4,2))'")
            String expression) {
        try {
            double result = evaluate(expression);
            // 整数不显示小数点
            if (result == Math.floor(result) && !Double.isInfinite(result)) {
                return String.valueOf((long) result);
            }
            return String.valueOf(result);
        } catch (Exception e) {
            return "计算错误: " + e.getMessage() + "。请检查表达式格式。";
        }
    }

    @ToolDef(
        name = "date_time",
        description = "获取当前日期和时间，或进行日期计算。返回格式化后的日期时间字符串。"
    )
    public String dateTime(
            @ToolParamDef(name = "operation", type = "string",
                    description = "操作类型",
                    enumValues = {"now", "today", "tomorrow", "yesterday"})
            String operation) {
        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        return switch (operation) {
            case "now", "today" -> "当前时间: " + now.format(fmt);
            case "tomorrow" -> "明天: " + now.plusDays(1).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            case "yesterday" -> "昨天: " + now.minusDays(1).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            default -> "当前时间: " + now.format(fmt);
        };
    }

    /**
     * 简易数学表达式求值器。
     * 支持：+, -, *, /, (, ), sqrt, pow, sin, cos, log, abs
     */
    private double evaluate(String expression) {
        expression = expression.replaceAll("\\s+", "");
        return parseExpression(expression);
    }

    private double parseExpression(String expr) {
        return parseAddSub(expr);
    }

    private double parseAddSub(String expr) {
        double result = parseMulDiv(expr);
        int i = 0;
        while (i < expr.length()) {
            char c = expr.charAt(i);
            if (c == '+' || c == '-') {
                int j = findMatchingEnd(expr, i + 1);
                double right = parseMulDiv(expr.substring(i + 1, j));
                if (c == '+') result += right;
                else result -= right;
                i = j;
            } else if (c == '(') {
                i = skipBrackets(expr, i);
            } else if (Character.isLetter(c)) {
                i = skipFunction(expr, i);
            } else {
                i++;
            }
        }
        return result;
    }

    private double parseMulDiv(String expr) {
        double result = parseUnary(expr);
        int i = 0;
        while (i < expr.length()) {
            char c = expr.charAt(i);
            if (c == '*' || c == '/') {
                int j = findMatchingEnd(expr, i + 1);
                double right = parseUnary(expr.substring(i + 1, j));
                if (c == '*') result *= right;
                else {
                    if (right == 0) throw new ArithmeticException("除数不能为零");
                    result /= right;
                }
                i = j;
            } else if (c == '(') {
                i = skipBrackets(expr, i);
            } else if (Character.isLetter(c)) {
                i = skipFunction(expr, i);
            } else {
                i++;
            }
        }
        return result;
    }

    private double parseUnary(String expr) {
        if (expr.isEmpty()) return 0;
        char first = expr.charAt(0);
        if (first == '-') {
            return -parseUnary(expr.substring(1));
        }
        if (first == '(') {
            int end = skipBrackets(expr, 0);
            return parseExpression(expr.substring(1, end - 1));
        }
        if (Character.isLetter(first)) {
            return parseFunction(expr);
        }
        // 数字
        return parseNumber(expr);
    }

    private double parseFunction(String expr) {
        int parenStart = expr.indexOf('(');
        if (parenStart < 0) {
            return switch (expr.toLowerCase()) {
                case "pi" -> Math.PI;
                case "e" -> Math.E;
                default -> throw new IllegalArgumentException("未知常量: " + expr);
            };
        }
        String funcName = expr.substring(0, parenStart).toLowerCase();
        int parenEnd = findMatchingParen(expr, parenStart);
        double arg = parseExpression(expr.substring(parenStart + 1, parenEnd));
        return switch (funcName) {
            case "sqrt" -> Math.sqrt(arg);
            case "pow" -> {
                int comma = findCommaAtTopLevel(expr.substring(parenStart + 1, parenEnd));
                double arg2 = parseExpression(expr.substring(parenStart + 1 + comma + 1, parenEnd));
                yield Math.pow(arg, arg2);
            }
            case "sin" -> Math.sin(Math.toRadians(arg));
            case "cos" -> Math.cos(Math.toRadians(arg));
            case "log" -> Math.log10(arg);
            case "ln" -> Math.log(arg);
            case "abs" -> Math.abs(arg);
            default -> throw new IllegalArgumentException("未知函数: " + funcName);
        };
    }

    private double parseNumber(String expr) {
        int i = 0;
        if (i < expr.length() && expr.charAt(i) == '-') i++;
        while (i < expr.length() && (Character.isDigit(expr.charAt(i)) || expr.charAt(i) == '.')) {
            i++;
        }
        return Double.parseDouble(expr.substring(0, i));
    }

    private int findMatchingEnd(String expr, int start) {
        if (start >= expr.length()) return expr.length();
        char c = expr.charAt(start);
        if (c == '(') return skipBrackets(expr, start);
        if (Character.isLetter(c)) return skipFunction(expr, start);
        if (c == '-' && start == 0) {
            // 负号后面的数字或表达式
            int j = start + 1;
            if (j < expr.length()) {
                if (expr.charAt(j) == '(') return skipBrackets(expr, j);
                if (Character.isLetter(expr.charAt(j))) return skipFunction(expr, j);
                return parseNumberEnd(expr, j);
            }
        }
        // 数字
        if (Character.isDigit(c) || c == '.') return parseNumberEnd(expr, start);
        return start + 1;
    }

    private int parseNumberEnd(String expr, int start) {
        int i = start;
        while (i < expr.length() && (Character.isDigit(expr.charAt(i)) || expr.charAt(i) == '.')) {
            i++;
        }
        return i;
    }

    private int skipBrackets(String expr, int start) {
        return findMatchingParen(expr, start) + 1;
    }

    private int findMatchingParen(String expr, int openPos) {
        int depth = 1;
        int i = openPos + 1;
        while (i < expr.length() && depth > 0) {
            char c = expr.charAt(i);
            if (c == '(') depth++;
            else if (c == ')') depth--;
            i++;
        }
        return i - 1;
    }

    private int skipFunction(String expr, int start) {
        int parenStart = expr.indexOf('(', start);
        if (parenStart < 0) {
            // 常量如 pi, e
            int i = start;
            while (i < expr.length() && Character.isLetter(expr.charAt(i))) i++;
            return i;
        }
        return findMatchingParen(expr, parenStart) + 1;
    }

    private int findCommaAtTopLevel(String expr) {
        int depth = 0;
        for (int i = 0; i < expr.length(); i++) {
            char c = expr.charAt(i);
            if (c == '(') depth++;
            else if (c == ')') depth--;
            else if (c == ',' && depth == 0) return i;
        }
        return -1;
    }
}
