package com.pl.gdl.common.model;

import com.pl.gdl.common.enums.TaskStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class TaskResultTest {

    @Test
    public void testDefaultStatusIsPending() {
        TaskResult tr = new TaskResult();
        assertThat(tr.getStatus()).isEqualTo(TaskStatus.PENDING);
    }

    @Test
    public void testStatusEnumAccessors() {
        TaskResult tr = new TaskResult();
        tr.setStatus(TaskStatus.RUNNING);
        assertThat(tr.getStatus()).isEqualTo(TaskStatus.RUNNING);
        tr.setStatus(TaskStatus.FINISHED);
        assertThat(tr.getStatus()).isEqualTo(TaskStatus.FINISHED);
    }

    @Test
    public void testConstructorWithStatus() {
        TaskResult tr = new TaskResult("task-1", TaskStatus.FINISHED);
        assertThat(tr.getTaskId()).isEqualTo("task-1");
        assertThat(tr.getStatus()).isEqualTo(TaskStatus.FINISHED);
    }

    @Test
    public void testDefaultFields() {
        TaskResult tr = new TaskResult();
        assertThat(tr.getMessage()).isEqualTo("Success");
        assertThat(tr.getResult()).isNotNull().isEmpty();
    }
}
