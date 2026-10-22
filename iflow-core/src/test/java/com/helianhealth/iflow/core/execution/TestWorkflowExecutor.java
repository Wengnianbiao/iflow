package com.helianhealth.iflow.core.execution;

import com.helianhealth.iflow.core.dal.ExecutionDAOFacade;
import com.helianhealth.iflow.core.execution.tasks.SystemTaskRegistry;
import com.helianhealth.iflow.core.lock.ExecutionLockService;
import com.helianhealth.iflow.core.queue.InMemoryExecutionQueue;
import com.helianhealth.iflow.core.queue.InMemoryQueueDAO;
import com.helianhealth.iflow.core.service.MetadataService;
import com.helianhealth.iflow.core.utils.IDGenerator;
import com.helianhealth.iflow.core.utils.ParametersUtils;

import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;

/** 手动组装测试运行时，可独立配置任务线程池并检查内存消息。 */
public class TestWorkflowExecutor extends WorkflowExecutorOps {
    private final Fixture fixture;

    public TestWorkflowExecutor(DeciderService decider, SystemTaskRegistry registry,
            ExecutionDAOFacade dao, MetadataService metadata, IDGenerator idGenerator,
            ParametersUtils parameters, ExecutionLockService locks) {
        this(decider, registry, dao, metadata, idGenerator, parameters, locks, new Fixture());
    }

    private TestWorkflowExecutor(DeciderService decider, SystemTaskRegistry registry,
            ExecutionDAOFacade dao, MetadataService metadata, IDGenerator idGenerator,
            ParametersUtils parameters, ExecutionLockService locks, Fixture fixture) {
        super(decider, registry, dao, metadata, idGenerator, parameters, locks,
                new InMemoryExecutionQueue(fixture.queue, action -> fixture.taskExecutor.execute(action)),
                fixture.completions);
        this.fixture = fixture;
    }

    public void useTaskExecutor(Executor executor) {
        fixture.taskExecutor = executor;
    }

    public InMemoryQueueDAO queue() {
        return fixture.queue;
    }

    private static final class Fixture {
        private final InMemoryQueueDAO queue = new InMemoryQueueDAO();
        private final WorkflowCompletionRegistry completions = new WorkflowCompletionRegistry();
        private Executor taskExecutor = ForkJoinPool.commonPool();
    }
}
