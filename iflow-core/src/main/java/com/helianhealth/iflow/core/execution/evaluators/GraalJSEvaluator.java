package com.helianhealth.iflow.core.execution.evaluators;

import com.helianhealth.iflow.core.execution.script.ScriptEvaluator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * GraalJS 求值器。
 */
@Component(GraalJSEvaluator.NAME)
public class GraalJSEvaluator implements Evaluator {

    private static final Logger log = LoggerFactory.getLogger(GraalJSEvaluator.class);

    public static final String NAME = "graaljs";

    private final ScriptEvaluator scriptEvaluator;

    public GraalJSEvaluator(ScriptEvaluator scriptEvaluator) {
        this.scriptEvaluator = scriptEvaluator;
    }

    @Override
    public Object evaluate(String expression, Map<String, Object> input) {
        log.debug("GraalJS evaluator -- expression: {}", expression);
        Object result = scriptEvaluator.eval(expression, input);
        log.debug("GraalJS evaluator -- result: {}", result);
        return result;
    }
}
