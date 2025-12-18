package cn.rhymed.task.monitor.interfaces.annotation;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author rhymed.liu[rhymed.liu@anker-in.com]
 * @TaskRecoveryHandler注解契约测试
 * @since 2025-12-10 11:44
 */
class TaskRecoveryHandlerTest {

    @Test
    void annotationShouldBeRetainedAtRuntime() {
        assertTrue(TaskRecoveryHandler.class.isAnnotationPresent(java.lang.annotation.Retention.class));
        java.lang.annotation.Retention retention = TaskRecoveryHandler.class.getAnnotation(java.lang.annotation.Retention.class);
        assertEquals(java.lang.annotation.RetentionPolicy.RUNTIME, retention.value());
    }

    @Test
    void annotationShouldTargetMethods() {
        assertTrue(TaskRecoveryHandler.class.isAnnotationPresent(java.lang.annotation.Target.class));
        java.lang.annotation.Target target = TaskRecoveryHandler.class.getAnnotation(java.lang.annotation.Target.class);
        assertEquals(1, target.value().length);
        assertEquals(java.lang.annotation.ElementType.METHOD, target.value()[0]);
    }

    @Test
    void annotationShouldHaveTaskNameAttribute() throws NoSuchMethodException {
        Method taskNameMethod = TaskRecoveryHandler.class.getDeclaredMethod("taskName");
        assertNotNull(taskNameMethod);
        assertEquals(String.class, taskNameMethod.getReturnType());
    }

    @Test
    void annotationShouldHavePriorityAttributeWithDefaultValue() throws NoSuchMethodException {
        Method priorityMethod = TaskRecoveryHandler.class.getDeclaredMethod("priority");
        assertNotNull(priorityMethod);
        assertEquals(int.class, priorityMethod.getReturnType());
        assertEquals(0, priorityMethod.getDefaultValue());
    }

    @Test
    void annotationCanBeAppliedToMethod() throws NoSuchMethodException {
        Method method = TestRecoveryHandler.class.getDeclaredMethod("recoverTask", Object.class);
        TaskRecoveryHandler annotation = method.getAnnotation(TaskRecoveryHandler.class);

        assertNotNull(annotation);
        assertEquals("testTask", annotation.taskName());
        assertEquals(10, annotation.priority());
    }

    static class TestRecoveryHandler {
        @TaskRecoveryHandler(taskName = "testTask", priority = 10)
        public void recoverTask(Object taskLog) {
            // Test recovery logic
        }
    }
}
