package org.activiti.test.asyncexecutor.delegate;



import java.util.concurrent.atomic.AtomicInteger;

import org.activiti.engine.delegate.DelegateExecution;
import org.activiti.engine.impl.delegate.ActivityBehavior;

public class NoopServiceTask implements ActivityBehavior {
	
	private AtomicInteger atomicInteger = new AtomicInteger(0);
	
	public void execute(DelegateExecution execution) {
		System.out.println("Executing service task, counter = " + atomicInteger.incrementAndGet());
	}

}
