package org.activiti.test.asyncexecutor.delegate;

import java.util.concurrent.atomic.AtomicInteger;

import org.activiti.engine.delegate.DelegateExecution;
import org.activiti.engine.delegate.JavaDelegate;

public class SlowPassthroughServiceTask implements JavaDelegate {
	
	private static AtomicInteger atomicInteger = new AtomicInteger(0);
	
	public void execute(DelegateExecution execution)  {
	  System.out.println("Executing service task + " 
        + execution.getCurrentFlowElement().getName()  + ", counter = " + atomicInteger.incrementAndGet());
	  try {
      Thread.sleep(500L);
    } catch (InterruptedException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
	}

}
