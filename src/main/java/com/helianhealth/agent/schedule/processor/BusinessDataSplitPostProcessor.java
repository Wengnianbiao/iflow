package com.helianhealth.agent.schedule.processor;

import com.helianhealth.agent.exception.WorkflowBusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class BusinessDataSplitPostProcessor {

    public List<Map<String, Object>> splitPostProcessor(String splitRule, Map<String, Object> businessData) {
        // 获取待分割字段业务数据
        Object splitBusinessData = businessData.get(splitRule);
        if (splitBusinessData == null) {
            log.error("分割业务数据异常,【{}】参数不存在", splitRule);
            throw WorkflowBusinessException.BUSINESS_DATA_SPLIT_ERROR.toException("参数不存在");
        }
        if (!(splitBusinessData instanceof List)) {
            log.error("分割业务数据异常,【{}】拆分规则字段必须为数组", splitRule);
            throw WorkflowBusinessException.BUSINESS_DATA_SPLIT_ERROR.toException("拆分规则字段必须为数组");
        }

        List<?> splitDataList = (List<?>) splitBusinessData;
        List<Map<String, Object>> result = new ArrayList<>();

        // 拆分业务数据字段
        for (Object item : splitDataList) {
            Map<String, Object> newData = new HashMap<>(businessData);
            newData.remove(splitRule);
            newData.put(splitRule, item);
            result.add(newData);
        }

        return result;
    }
}
