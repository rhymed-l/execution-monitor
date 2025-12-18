package cn.rhymed.execution.monitor.interfaces.annotation;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author rhymed.liu[rhymed.liu@anker-in.com]
 * @RecoveryHandler注解契约测试
 * @since 2025-12-10 11:44
 */
class RecoveryHandlerTest {

    @Test
    void annotationShouldBeRetainedAtRuntime() {
        assertTrue(RecoveryHandler.class.isAnnotationPresent(java.lang.annotation.Retention.class));
        java.lang.annotation.Retention retention = RecoveryHandler.class.getAnnotation(java.lang.annotation.Retention.class);
        assertEquals(java.lang.annotation.RetentionPolicy.RUNTIME, retention.value());
    }

    @Test
    void annotationShouldTargetMethods() {
        assertTrue(RecoveryHandler.class.isAnnotationPresent(java.lang.annotation.Target.class));
        java.lang.annotation.Target target = RecoveryHandler.class.getAnnotation(java.lang.annotation.Target.class);
        assertEquals(1, target.value().length);
        assertEquals(java.lang.annotation.ElementType.METHOD, target.value()[0]);
    }

    @Test
    void annotationShouldHaveNameAttribute() throws NoSuchMethodException {
        Method nameMethod = RecoveryHandler.class.getDeclaredMethod("name");
        assertNotNull(nameMethod);
        assertEquals(String.class, nameMethod.getReturnType());
    }

    @Test
    void annotationShouldHavePriorityAttributeWithDefaultValue() throws NoSuchMethodException {
        Method priorityMethod = RecoveryHandler.class.getDeclaredMethod("priority");
        assertNotNull(priorityMethod);
        assertEquals(int.class, priorityMethod.getReturnType());
        assertEquals(0, priorityMethod.getDefaultValue());
    }

    @Test
    void annotationCanBeAppliedToMethod() throws NoSuchMethodException {
        Method method = TestRecoveryHandler.class.getDeclaredMethod("recoverExecution", Object.class);
        RecoveryHandler annotation = method.getAnnotation(RecoveryHandler.class);

        assertNotNull(annotation);
        assertEquals("testExecution", annotation.name());
        assertEquals(10, annotation.priority());
    }

    static class TestRecoveryHandler {
        @RecoveryHandler(name = "testExecution", priority = 10)
        public void recoverExecution(Object executionLog) {
            // Test recovery logic
        }
    }
}
