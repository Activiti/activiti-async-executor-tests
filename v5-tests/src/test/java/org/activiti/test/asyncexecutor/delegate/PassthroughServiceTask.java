package org.activiti.test.asyncexecutor.delegate;

import java.util.concurrent.atomic.AtomicInteger;

import org.activiti.engine.delegate.DelegateExecution;
import org.activiti.engine.delegate.JavaDelegate;
import org.activiti.engine.impl.persistence.entity.ExecutionEntity;

public class PassthroughServiceTask implements JavaDelegate {
	
	private static AtomicInteger atomicInteger = new AtomicInteger(0);
	
	public void execute(DelegateExecution execution) {
	  System.out.println("Executing service task + "
	      + ((ExecutionEntity) execution).getActivity().getProperty("name") + ", counter = " + atomicInteger.incrementAndGet());
	}

}
