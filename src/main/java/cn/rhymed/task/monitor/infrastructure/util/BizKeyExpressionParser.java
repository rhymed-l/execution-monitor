package cn.rhymed.task.monitor.infrastructure.util;

import cn.hutool.core.util.StrUtil;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import java.lang.reflect.Method;

/**
 * BizKey表达式解析器
 * 用于解析@TaskMonitor注解中的SpEL表达式(如bizKey)
 *
 * @author rhymed.liu[rhymed.liu@anker-in.com]
 * @since 2025-12-10 11:44
 */
public class BizKeyExpressionParser {

    private static final ExpressionParser PARSER = new SpelExpressionParser();

    /**
     * 解析SpEL表达式
     *
     * @param expressionString SpEL表达式字符串(如 "#orderId", "#p0", "#request.orderId")
     * @param method           被拦截的方法
     * @param args             方法参数
     * @return 解析结果,如果解析失败或表达式为空则返回null
     */
    public static String parseExpression(String expressionString, Method method, Object[] args) {
        if (StrUtil.isBlank(expressionString)) {
            return null;
        }

        try {
            // 创建评估上下文
            EvaluationContext context = createEvaluationContext(method, args);

            // 解析表达式
            Expression expression = PARSER.parseExpression(expressionString);
            Object value = expression.getValue(context);

            return value != null ? value.toString() : null;
        } catch (Exception e) {
            // 解析失败,返回null
            return null;
        }
    }

    /**
     * 创建SpEL评估上下文
     */
    private static EvaluationContext createEvaluationContext(Method method, Object[] args) {
        StandardEvaluationContext context = new StandardEvaluationContext();

        // 获取参数名称
        String[] parameterNames = getParameterNames(method, args);

        // 注册参数变量
        if (args != null && args.length > 0) {
            for (int i = 0; i < args.length; i++) {
                // 支持 #p0, #p1, #p2... 形式
                context.setVariable("p" + i, args[i]);

                // 支持参数名形式(如 #orderId, #request)
                if (parameterNames != null && i < parameterNames.length) {
                    context.setVariable(parameterNames[i], args[i]);
                }
            }
        }

        return context;
    }

    /**
     * 获取方法参数名称
     * 注意:需要编译时添加-parameters参数才能获取真实参数名
     * 否则只能返回arg0, arg1...
     */
    private static String[] getParameterNames(Method method, Object[] args) {
        if (args == null || args.length == 0) {
            return new String[0];
        }

        try {
            // 尝试获取参数名称(Java 8+)
            java.lang.reflect.Parameter[] parameters = method.getParameters();
            String[] names = new String[parameters.length];
            for (int i = 0; i < parameters.length; i++) {
                names[i] = parameters[i].getName();
            }
            return names;
        } catch (Exception e) {
            // 如果获取失败,返回null
            return null;
        }
    }
}
