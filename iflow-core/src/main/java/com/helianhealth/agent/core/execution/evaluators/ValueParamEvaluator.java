package com.helianhealth.agent.core.execution.evaluators;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 按 input 键名取值。
 */
@Component(ValueParamEvaluator.NAME)
@Slf4j
public class ValueParamEvaluator implements Evaluator {

    public static final String NAME = "value-param";

    @Override
    public Object evaluate(String expression, Map<String, Object> input) {
        log.debug("ValueParam evaluator -- key: {}", expression);
        if (input == null) {
            throw new IllegalArgumentException("Input has to be a JSON object for value-param evaluator");
        }
        return input.get(expression);
    }
}
