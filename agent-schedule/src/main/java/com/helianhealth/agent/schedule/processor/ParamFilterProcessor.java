package com.helianhealth.agent.schedule.processor;

import com.helianhealth.agent.common.utils.SpelUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Map;

@Component
@Slf4j
public class ParamFilterProcessor {

    public Map<String, Object> paramFilterPostProcess(String paramFilterExpr, Map<String, Object> businessData) {
        if (StringUtils.isEmpty(paramFilterExpr)) {
            return businessData;
        }
        return SpelUtils.businessDataFilter(paramFilterExpr, businessData);
    }
}
