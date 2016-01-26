package org.activiti.test.asyncexecutor.delegate;

import java.util.concurrent.atomic.AtomicInteger;

import org.activiti.engine.impl.pvm.delegate.ActivityBehavior;
import org.activiti.engine.impl.pvm.delegate.ActivityExecution;

public class NoopServiceTask implements ActivityBehavior {
	
	private AtomicInteger atomicInteger = new AtomicInteger(0);
	
	public void execute(ActivityExecution execution) throws Exception {
		System.out.println("Executing service task, counter = " + atomicInteger.incrementAndGet());
	}

}
