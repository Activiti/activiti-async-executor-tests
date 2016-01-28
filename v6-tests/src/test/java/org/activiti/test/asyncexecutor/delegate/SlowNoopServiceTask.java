package org.activiti.test.asyncexecutor.delegate;

import java.util.concurrent.atomic.AtomicInteger;

import org.activiti.engine.delegate.DelegateExecution;
import org.activiti.engine.impl.delegate.ActivityBehavior;

public class SlowNoopServiceTask implements ActivityBehavior {
	
	private AtomicInteger atomicInteger = new AtomicInteger(0);
	
	@Override
	public void execute(DelegateExecution arg0) {
		System.out.println("Executing service task, counter = " + atomicInteger.incrementAndGet());
		try {
      Thread.sleep(1000L);
    } catch (InterruptedException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
	}

}
