package com.helianhealth.agent.common.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.expression.BeanResolver;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SpEL表达式解析工具类
 * <p>
 * 异常处理原则：字段级解析失败静默处理，不向上抛出异常。
 * 单个字段的表达式解析错误不应阻断整个参数转换流程，
 * 解析异常信息通过日志记录
 * </p>
 */
public class SpelUtils {

    private static final SpelExpressionParser PARSER = new SpelExpressionParser();

    private static final Logger logger = LoggerFactory.getLogger(SpelUtils.class);

    private static final BeanResolver BEAN_RESOLVER =
            (context, beanName) -> SpringContextHolder.getBean(beanName);

    private SpelUtils() {
    }

    /**
     * 解析表达式并返回结果
     * @param expression SpEL表达式（如："#data.idCard == null ? #data.phone + 'hl' : #data.idCard"）
     * @param businessData 业务数据Map（键为字段名，值为字段值）
     * @return 表达式执行结果
     */
    public static Object parserPreNode(String expression, Object businessData, Object parentBusinessData, Map<String, Object> sourceBusinessData) {
        try {
            StandardEvaluationContext context = new StandardEvaluationContext();
            context.setVariable("data", businessData);
            context.setVariable("parent", parentBusinessData);
            context.setVariable("source", sourceBusinessData);

            return PARSER.parseExpression(expression).getValue(context);
        } catch (Exception e) {
            logger.error("前置处理表达式[{}]解析异常：", expression, e);
            return null;
        }
    }

    /**
     * 解析后置处理表达式并返回结果
     * @param expression SpEL表达式
     * @param businessData 当前层级数据
     * @param inputBusinessData 输入参数
     * @param responseBusinessData 输出参数
     * @return 表达式执行结果
     */
    public static Object parserPostNode(String expression, Object businessData, Map<String, Object> inputBusinessData, Map<String, Object> responseBusinessData) {
        try {
            StandardEvaluationContext context = new StandardEvaluationContext();
            context.setVariable("data", businessData);
            context.setVariable("input", inputBusinessData);
            context.setVariable("response", responseBusinessData);

            return PARSER.parseExpression(expression).getValue(context);
        } catch (Exception e) {
            logger.error("后置处理表达式[{}]解析异常：", expression, e);
            return null;
        }
    }

    /**
     * 业务数据过滤处理
     * @param expression 过滤表达式
     * @param businessData 业务数据
     * @return 过滤后的业务数据
     */
    public static Map<String, Object> businessDataFilter(String expression, Map<String, Object> businessData) {
        try {
            if (businessData == null) {
                return null;
            }
            Map<String, Object> deepCopyMap = JsonUtils.deepCopyMap(businessData);

            StandardEvaluationContext context = new StandardEvaluationContext();
            context.setVariable("data", deepCopyMap);
            PARSER.parseExpression(expression).getValue(context);

            return deepCopyMap;
        } catch (Exception e) {
            logger.error("业务数据过滤处理表达式[{}]解析异常：", expression, e);
            return null;
        }
    }

    /**
     * 解析前置处理 Bean 表达式并返回结果（支持通过 @beanName 访问任意 Spring Bean）
     * 前置处理上下文变量：#data, #parent, #source
     * @param expression SpEL表达式（如："@hemsMapper.selectPatientCodeByVisitNo(#data.visitNo)"）
     * @param businessData 当前层级业务数据
     * @param parentBusinessData 父层业务数据
     * @param sourceBusinessData 根业务数据
     * @return 表达式执行结果
     */
    public static Object parserPreNodeWithBeanAccess(String expression, Object businessData, Object parentBusinessData, Map<String, Object> sourceBusinessData) {
        try {
            StandardEvaluationContext context = new StandardEvaluationContext();
            context.setVariable("data", businessData);
            context.setVariable("parent", parentBusinessData);
            context.setVariable("source", sourceBusinessData);
            context.setBeanResolver(BEAN_RESOLVER);

            return PARSER.parseExpression(expression).getValue(context);
        } catch (Exception e) {
            logger.error("前置处理Bean表达式[{}]解析异常：", expression, e);
            return null;
        }
    }

    /**
     * 解析后置处理 Bean 表达式并返回结果（支持通过 @beanName 访问任意 Spring Bean）
     * 后置处理上下文变量：#data, #input, #response
     * @param expression SpEL表达式（如："@hemsMapper.selectPatientCodeByVisitNo(#data.visitNo)"）
     * @param businessData 当前层级数据
     * @param inputBusinessData 输入参数（原始请求数据）
     * @param responseBusinessData 输出参数（响应数据）
     * @return 表达式执行结果
     */
    public static Object parserPostNodeWithBeanAccess(String expression, Object businessData, Map<String, Object> inputBusinessData, Map<String, Object> responseBusinessData) {
        try {
            StandardEvaluationContext context = new StandardEvaluationContext();
            context.setVariable("data", businessData);
            context.setVariable("input", inputBusinessData);
            context.setVariable("response", responseBusinessData);
            context.setBeanResolver(BEAN_RESOLVER);

            return PARSER.parseExpression(expression).getValue(context);
        } catch (Exception e) {
            logger.error("后置处理Bean表达式[{}]解析异常：", expression, e);
            return null;
        }
    }

    public static boolean validateExpressionSyntax(String expression) {
        try {
            PARSER.parseExpression(expression);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 解析调度表达式并返回结果
     * @param expression 调度表达式
     * @param preFlowNodeResponse 前置工作流节点响应结果
     * @return 调度结果
     */
    @SuppressWarnings("unchecked")
    public static List<Integer> parseScheduleExpression(String expression, Map<String, Object> preFlowNodeResponse) {
        if (expression == null || expression.trim().isEmpty()) {
            return null;
        }

        try {
            StandardEvaluationContext context = new StandardEvaluationContext();
            context.setVariable("data", preFlowNodeResponse);
            Object parser = PARSER.parseExpression(expression).getValue(context);
            if (parser == null) {
                return null;
            }

            if (!(parser instanceof List)) {
                throw new IllegalArgumentException("期望List<Integer>，实际类型: " + parser.getClass().getSimpleName());
            }

            List<?> resultList = (List<?>) parser;
            for (Object item : resultList) {
                if (!(item instanceof Integer)) {
                    String itemType = item == null ? "null" : item.getClass().getSimpleName();
                    throw new IllegalArgumentException("元素类型错误，期望Integer，实际类型: " + itemType);
                }
            }

            return (List<Integer>) parser;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            logger.error("调度表达式[{}]解析异常：", expression, e);
            return null;
        }
    }

    /**
     * 从调度表达式中提取所有可能的节点ID
     * 例如: "#data[code] == '-1' ? T(java.util.Arrays).asList(39) : T(java.util.Arrays).asList(40, 41)"
     * 返回: [39, 40, 41]
     *
     * @param scheduleExpr 调度表达式
     * @return 所有可能的下游节点ID列表
     */
    public static List<Integer> extractNodeIdsFromScheduleExpr(String scheduleExpr) {
        List<Integer> nodeIds = new ArrayList<>();
        if (scheduleExpr == null || scheduleExpr.trim().isEmpty()) {
            return nodeIds;
        }

        // 匹配 asList(...) 中的数字
        // 模式: asList( 后跟着数字（可能有逗号分隔的多个数字）
        Pattern pattern = Pattern.compile("asList\\s*\\(([^)]+)\\)");
        Matcher matcher = pattern.matcher(scheduleExpr);

        while (matcher.find()) {
            String content = matcher.group(1);
            // 提取其中的数字
            Pattern numPattern = Pattern.compile("\\d+");
            Matcher numMatcher = numPattern.matcher(content);
            while (numMatcher.find()) {
                try {
                    nodeIds.add(Integer.parseInt(numMatcher.group()));
                } catch (NumberFormatException e) {
                    // 忽略解析失败的
                }
            }
        }

        return nodeIds;
    }
}
