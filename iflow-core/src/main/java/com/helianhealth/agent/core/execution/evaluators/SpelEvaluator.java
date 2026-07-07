package com.helianhealth.agent.core.execution.evaluators;

import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * SpEL 求值器：表达式内使用 {@code #root} 访问任务 inputData。
 */
@Component(SpelEvaluator.NAME)
public class SpelEvaluator implements Evaluator {

    public static final String NAME = "spel";

    private static final SpelExpressionParser PARSER = new SpelExpressionParser();

    @Override
    public Object evaluate(String expression, Map<String, Object> input) {
        StandardEvaluationContext context = new StandardEvaluationContext();
        context.setVariable("root", input);
        return PARSER.parseExpression(expression).getValue(context);
    }
}
