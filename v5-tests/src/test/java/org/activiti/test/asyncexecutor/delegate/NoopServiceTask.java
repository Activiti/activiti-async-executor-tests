package org.activiti.test.asyncexecutor.delegate;

import java.util.concurrent.atomic.AtomicInteger;

import org.activiti.engine.impl.persistence.entity.ExecutionEntity;
import org.activiti.engine.impl.pvm.delegate.ActivityBehavior;
import org.activiti.engine.impl.pvm.delegate.ActivityExecution;

public class NoopServiceTask implements ActivityBehavior {
	
	private static AtomicInteger atomicInteger = new AtomicInteger(0);

	@Override
	public void execute(ActivityExecution execution) throws Exception {
		System.out.println("Executing service task + " 
		    + ((ExecutionEntity) execution).getActivity().getProperty("name")  + ", counter = " + atomicInteger.incrementAndGet());
	}

}
