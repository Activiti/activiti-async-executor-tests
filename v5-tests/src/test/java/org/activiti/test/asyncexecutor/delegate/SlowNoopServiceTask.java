package org.activiti.test.asyncexecutor.delegate;

import java.util.concurrent.atomic.AtomicInteger;

import org.activiti.engine.delegate.DelegateExecution;
import org.activiti.engine.impl.pvm.delegate.ActivityBehavior;
import org.activiti.engine.impl.pvm.delegate.ActivityExecution;

public class SlowNoopServiceTask implements ActivityBehavior {
	
	private AtomicInteger atomicInteger = new AtomicInteger(0);

	@Override
	public void execute(ActivityExecution execution) throws Exception {
		System.out.println("Executing service task, counter = " + atomicInteger.incrementAndGet());
		try {
      Thread.sleep(1000L);
    } catch (InterruptedException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
	}

}
