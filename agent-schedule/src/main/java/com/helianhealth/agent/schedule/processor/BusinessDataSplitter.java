package com.helianhealth.agent.schedule.processor;

import com.helianhealth.agent.common.exception.WorkflowBusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class BusinessDataSplitter {

    public List<Map<String, Object>> split(String splitField, Map<String, Object> businessData) {
        // 支持点号路径，如 "order.items" 或 "data.user.addresses"
        if (splitField.contains(".")) {
            return splitByNestedPath(splitField, businessData);
        }
        
        // 兼容原有的简单字段拆分逻辑
        return splitBySimpleField(splitField, businessData);
    }

    /**
     * 简单字段拆分（原有逻辑）
     */
    private List<Map<String, Object>> splitBySimpleField(String splitField, Map<String, Object> businessData) {
        // 获取待分割字段业务数据
        Object splitBusinessData = businessData.get(splitField);
        if (splitBusinessData == null) {
            log.error("分割业务数据异常,【{}】参数不存在", splitField);
            throw WorkflowBusinessException.BUSINESS_DATA_SPLIT_ERROR.toException("参数不存在");
        }
        if (!(splitBusinessData instanceof List)) {
            log.error("分割业务数据异常,【{}】拆分规则字段必须为数组", splitField);
            throw WorkflowBusinessException.BUSINESS_DATA_SPLIT_ERROR.toException("拆分规则字段必须为数组");
        }

        List<?> splitDataList = (List<?>) splitBusinessData;
        List<Map<String, Object>> result = new ArrayList<>();

        // 拆分业务数据字段
        for (Object item : splitDataList) {
            Map<String, Object> newData = new HashMap<>(businessData);
            newData.remove(splitField);
            newData.put(splitField, item);
            result.add(newData);
        }

        return result;
    }

    /**
     * 支持嵌套路径的拆分，如 "order.items" 或 "data.user.addresses"
     * 前提：数组的上层嵌套一定是对象（Map）
     * @param splitPath 拆分路径，使用 . 分隔
     * @param businessData 业务数据
     * @return 拆分后的数据列表
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> splitByNestedPath(String splitPath, Map<String, Object> businessData) {
        String[] pathParts = splitPath.split("\\.");
        
        // 递归获取嵌套数组（数组上层一定是对象）
        Object targetArray = getNestedValue(businessData, pathParts);
        
        if (targetArray == null) {
            log.error("分割业务数据异常,路径【{}】对应的数据不存在", splitPath);
            throw WorkflowBusinessException.BUSINESS_DATA_SPLIT_ERROR.toException("路径对应的数据不存在: " + splitPath);
        }
        
        if (!(targetArray instanceof List)) {
            log.error("分割业务数据异常,路径【{}】对应的数据必须为数组", splitPath);
            throw WorkflowBusinessException.BUSINESS_DATA_SPLIT_ERROR.toException("路径对应的数据必须为数组: " + splitPath);
        }
        
        List<?> splitDataList = (List<?>) targetArray;
        List<Map<String, Object>> result = new ArrayList<>();
        
        // 拆分业务数据
        for (Object item : splitDataList) {
            // 深拷贝整个业务数据（仅拷贝 Map 和 List 结构）
            Map<String, Object> newData = deepCopyMap(businessData);
            // 将数组元素替换到对应路径（上层一定是 Map）
            setNestedValue(newData, pathParts, item);
            result.add(newData);
        }
        
        log.debug("按路径【{}】拆分数据，原数组大小: {}, 拆分后数据条数: {}", splitPath, splitDataList.size(), result.size());
        return result;
    }

    /**
     * 递归获取嵌套路径的值
     * 前提：路径中除了最后一个元素，其他都是 Map
     */
    @SuppressWarnings("unchecked")
    private Object getNestedValue(Map<String, Object> data, String[] pathParts) {
        Map<String, Object> current = data;
        
        // 遍历到倒数第二层（都是 Map）
        for (int i = 0; i < pathParts.length - 1; i++) {
            Object next = current.get(pathParts[i]);
            if (!(next instanceof Map)) {
                log.error("路径【{}】的第{}层不是对象类型", String.join(".", pathParts), i + 1);
                return null;
            }
            current = (Map<String, Object>) next;
        }
        
        // 返回最后一层的值（应该是数组）
        return current.get(pathParts[pathParts.length - 1]);
    }

    /**
     * 设置嵌套路径的值（将数组元素替换到指定位置）
     * 前提：路径中除了最后一个元素，其他都是 Map
     */
    @SuppressWarnings("unchecked")
    private void setNestedValue(Map<String, Object> data, String[] pathParts, Object value) {
        Map<String, Object> current = data;
        
        // 遍历到倒数第二层
        for (int i = 0; i < pathParts.length - 1; i++) {
            Object next = current.get(pathParts[i]);
            if (!(next instanceof Map)) {
                // 理论上不会走到这里，因为是深拷贝的
                log.warn("路径【{}】的第{}层不是对象，无法设置值", String.join(".", pathParts), i + 1);
                return;
            }
            current = (Map<String, Object>) next;
        }
        
        // 在最后一层设置值（替换数组为单个元素）
        current.put(pathParts[pathParts.length - 1], value);
    }

    /**
     * 深拷贝 Map（支持嵌套结构）
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> deepCopyMap(Map<String, Object> source) {
        Map<String, Object> copy = new HashMap<>();
        
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            Object value = entry.getValue();
            
            if (value instanceof Map) {
                // 递归拷贝嵌套 Map
                copy.put(entry.getKey(), deepCopyMap((Map<String, Object>) value));
            } else if (value instanceof List) {
                // 拷贝 List
                copy.put(entry.getKey(), deepCopyList((List<?>) value));
            } else {
                // 基本类型或其他对象直接引用（不需要深拷贝）
                copy.put(entry.getKey(), value);
            }
        }
        
        return copy;
    }

    /**
     * 深拷贝 List（支持嵌套结构）
     */
    @SuppressWarnings("unchecked")
    private List<Object> deepCopyList(List<?> source) {
        List<Object> copy = new ArrayList<>();
        
        for (Object item : source) {
            if (item instanceof Map) {
                copy.add(deepCopyMap((Map<String, Object>) item));
            } else if (item instanceof List) {
                copy.add(deepCopyList((List<?>) item));
            } else {
                // 基本类型直接引用
                copy.add(item);
            }
        }
        
        return copy;
    }
}
