package com.helianhealth.agent.core.execution.script;

import java.util.regex.Pattern;

/**
 * GraalJS 脚本表达式规范化（兼容 {@code function scriptFun(){...}} 写法）。
 */
public final class GraalScriptUtils {

    private static final Pattern SCRIPT_FUN_DEF = Pattern.compile("function\\s+scriptFun\\b");
    private static final Pattern SCRIPT_FUN_INVOCATION =
            Pattern.compile(";\\s*scriptFun\\s*\\(\\s*\\)\\s*;?\\s*$");

    private GraalScriptUtils() {}

    /**
     * 若脚本定义了 {@code scriptFun} 但未调用，自动追加 {@code scriptFun();}。
     */
    public static String normalizeScript(String expression) {
        if (expression == null || expression.isBlank()) {
            return expression;
        }
        String trimmed = expression.trim();
        if (SCRIPT_FUN_DEF.matcher(trimmed).find() && !SCRIPT_FUN_INVOCATION.matcher(trimmed).find()) {
            if (!trimmed.endsWith(";")) {
                trimmed += ";";
            }
            return trimmed + " scriptFun();";
        }
        return expression;
    }
}
