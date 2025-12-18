package cn.rhymed.execution.monitor.interfaces.annotation;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author rhymed.liu[rhymed.liu@anker-in.com]
 * @ExecutionRecoveryHandler注解契约测试
 * @since 2025-12-10 11:44
 */
class ExecutionRecoveryHandlerTest {

    @Test
    void annotationShouldBeRetainedAtRuntime() {
        assertTrue(ExecutionRecoveryHandler.class.isAnnotationPresent(java.lang.annotation.Retention.class));
        java.lang.annotation.Retention retention = ExecutionRecoveryHandler.class.getAnnotation(java.lang.annotation.Retention.class);
        assertEquals(java.lang.annotation.RetentionPolicy.RUNTIME, retention.value());
    }

    @Test
    void annotationShouldTargetMethods() {
        assertTrue(ExecutionRecoveryHandler.class.isAnnotationPresent(java.lang.annotation.Target.class));
        java.lang.annotation.Target target = ExecutionRecoveryHandler.class.getAnnotation(java.lang.annotation.Target.class);
        assertEquals(1, target.value().length);
        assertEquals(java.lang.annotation.ElementType.METHOD, target.value()[0]);
    }

    @Test
    void annotationShouldHaveExecutionNameAttribute() throws NoSuchMethodException {
        Method executionNameMethod = ExecutionRecoveryHandler.class.getDeclaredMethod("executionName");
        assertNotNull(executionNameMethod);
        assertEquals(String.class, executionNameMethod.getReturnType());
    }

    @Test
    void annotationShouldHavePriorityAttributeWithDefaultValue() throws NoSuchMethodException {
        Method priorityMethod = ExecutionRecoveryHandler.class.getDeclaredMethod("priority");
        assertNotNull(priorityMethod);
        assertEquals(int.class, priorityMethod.getReturnType());
        assertEquals(0, priorityMethod.getDefaultValue());
    }

    @Test
    void annotationCanBeAppliedToMethod() throws NoSuchMethodException {
        Method method = TestRecoveryHandler.class.getDeclaredMethod("recoverExecution", Object.class);
        ExecutionRecoveryHandler annotation = method.getAnnotation(ExecutionRecoveryHandler.class);

        assertNotNull(annotation);
        assertEquals("testExecution", annotation.executionName());
        assertEquals(10, annotation.priority());
    }

    static class TestRecoveryHandler {
        @ExecutionRecoveryHandler(executionName = "testExecution", priority = 10)
        public void recoverExecution(Object executionLog) {
            // Test recovery logic
        }
    }
}
