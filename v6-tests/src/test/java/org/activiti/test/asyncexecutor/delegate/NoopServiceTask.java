package org.activiti.test.asyncexecutor.delegate;

import java.util.concurrent.atomic.AtomicInteger;

import org.activiti.engine.delegate.DelegateExecution;
import org.activiti.engine.impl.delegate.ActivityBehavior;
import org.activiti.engine.impl.persistence.entity.ExecutionEntity;

public class NoopServiceTask implements ActivityBehavior {
	
	private static AtomicInteger atomicInteger = new AtomicInteger(0);

	@Override
	public void execute(DelegateExecution execution) {
		System.out.println("Executing service task + " 
		    + execution.getCurrentFlowElement().getName()  + ", counter = " + atomicInteger.incrementAndGet());
	}

}
