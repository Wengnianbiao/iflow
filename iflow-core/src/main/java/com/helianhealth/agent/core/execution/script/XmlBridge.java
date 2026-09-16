package com.helianhealth.agent.core.execution.script;

import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyArray;
import org.graalvm.polyglot.proxy.ProxyObject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * GraalJS 脚本内可用的 XML 解析/序列化桥。
 */
public class XmlBridge {

    public Object parse(String xml) {
        if (xml == null || xml.isBlank()) {
            return null;
        }
        return wrap(XmlMapParser.parse(xml));
    }

    public String stringify(String rootName, Object value) {
        return XmlMapParser.stringify(rootName, unwrap(value));
    }

    @SuppressWarnings("unchecked")
    private Object wrap(Object value) {
        if (value instanceof Map) {
            Map<String, Object> wrapped = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : ((Map<String, Object>) value).entrySet()) {
                wrapped.put(entry.getKey(), wrap(entry.getValue()));
            }
            return ProxyObject.fromMap(wrapped);
        }
        if (value instanceof List) {
            List<Object> list = (List<Object>) value;
            Object[] wrapped = new Object[list.size()];
            for (int i = 0; i < list.size(); i++) {
                wrapped[i] = wrap(list.get(i));
            }
            return ProxyArray.fromArray(wrapped);
        }
        return value;
    }

    private Object unwrap(Object value) {
        if (value instanceof Value polyglotValue) {
            return unwrapPolyglot(polyglotValue);
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                result.put(String.valueOf(entry.getKey()), unwrap(entry.getValue()));
            }
            return result;
        }
        if (value instanceof List<?> list) {
            List<Object> result = new ArrayList<>(list.size());
            for (Object item : list) {
                result.add(unwrap(item));
            }
            return result;
        }
        return value;
    }

    private Object unwrapPolyglot(Value value) {
        if (value.isNull()) {
            return null;
        }
        if (value.isString()) {
            return value.asString();
        }
        if (value.isBoolean()) {
            return value.asBoolean();
        }
        if (value.isNumber()) {
            if (value.fitsInLong()) {
                return value.asLong();
            }
            return value.asDouble();
        }
        if (value.hasArrayElements()) {
            List<Object> list = new ArrayList<>((int) value.getArraySize());
            for (long i = 0; i < value.getArraySize(); i++) {
                list.add(unwrap(value.getArrayElement((int) i)));
            }
            return list;
        }
        if (value.hasMembers()) {
            Map<String, Object> map = new LinkedHashMap<>();
            for (String key : value.getMemberKeys()) {
                map.put(key, unwrap(value.getMember(key)));
            }
            return map;
        }
        return value.toString();
    }
}
