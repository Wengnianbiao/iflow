package com.helianhealth.iflow.tasks.mapping;

import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;
import com.jayway.jsonpath.PathNotFoundException;

import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 读取原始 payload 或命名循环变量；不隐式切换上下文。 */
final class ParamSourceReader {
    private static final Pattern ROOT = Pattern.compile("^([A-Za-z_$][A-Za-z0-9_$]*)(.*)$");
    private static final Pattern SUFFIX = Pattern.compile(
            "(?:\\.[^.\\[\\]\\s]+|\\[\\d+\\]|\\['(?:[^'\\\\]|\\\\.)*'\\]|\\[\"(?:[^\"\\\\]|\\\\.)*\"\\])*");
    private static final Configuration JSON_PATH = Configuration.builder()
            .options(Option.DEFAULT_PATH_LEAF_TO_NULL).build();

    private ParamSourceReader() {}

    static void validate(String path, Set<String> aliases) {
        Matcher matcher = parse(path);
        String root = matcher.group(1);
        if (!"$".equals(root) && !aliases.contains(root)) {
            throw new IllegalArgumentException("未知上下文变量: " + root + "，payload 路径必须以 $ 开头");
        }
        JsonPath compiled = JsonPath.compile("$" + matcher.group(2));
        if (!compiled.isDefinite()) {
            throw new IllegalArgumentException("取值路径仅支持字段和数组下标，复杂选择请使用表达式: " + path);
        }
    }

    static Object read(Object payload, Map<String, Object> aliases, String path) {
        Matcher matcher = parse(path);
        Object source = "$".equals(matcher.group(1)) ? payload : aliases.get(matcher.group(1));
        String suffix = matcher.group(2);
        if (source == null || suffix.isEmpty()) {
            return source;
        }
        try {
            return JsonPath.using(JSON_PATH).parse(source).read("$" + suffix);
        } catch (PathNotFoundException e) {
            return null;
        }
    }

    private static Matcher parse(String path) {
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("sourceParamKey 不能为空");
        }
        Matcher matcher = ROOT.matcher(path);
        if (!matcher.matches() || !SUFFIX.matcher(matcher.group(2)).matches()) {
            throw new IllegalArgumentException("无效的取值路径: " + path);
        }
        return matcher;
    }
}
