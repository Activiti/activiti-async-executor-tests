/* Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.activiti.test.asyncexecutor;
import java.util.Calendar;
import java.util.Date;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;

import org.activiti.engine.HistoryService;
import org.activiti.engine.ProcessEngine;
import org.activiti.engine.RepositoryService;
import org.activiti.engine.RuntimeService;
import org.activiti.engine.impl.cfg.StandaloneProcessEngineConfiguration;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mchange.v2.c3p0.ComboPooledDataSource;

/**
 * @author Joram Barrez
 */
public class AsyncExecutorQueueOverflowTest {
  
  private static final Logger logger = LoggerFactory.getLogger(AsyncExecutorQueueOverflowTest.class);
  
  protected static DataSource dataSource;
  
  @BeforeClass
  public static void setupDataSource() throws Exception {
    dataSource = createDataSource();
  }
  
  protected static ComboPooledDataSource createDataSource() throws Exception {
    Properties defaultProperties = new Properties();
    defaultProperties.load(AsyncExecutorQueueOverflowTest.class.getClassLoader().getResourceAsStream("default-db.properties"));
    String jdbcUrl = defaultProperties.getProperty("jdbc.url");
    String jdbcDriver = defaultProperties.getProperty("jdbc.driver");
    String jdbcUsername = defaultProperties.getProperty("jdbc.username");
    String jdbcPassword = defaultProperties.getProperty("jdbc.password");
    
    Properties dbProperties = new Properties();
    try {
      dbProperties.load(AsyncExecutorQueueOverflowTest.class.getClassLoader().getResourceAsStream("db.properties"));
      
      jdbcUrl = dbProperties.getProperty("jdbc.url");
      jdbcDriver = dbProperties.getProperty("jdbc.driver");
      jdbcUsername = dbProperties.getProperty("jdbc.username");
      jdbcPassword = dbProperties.getProperty("jdbc.password");
      
    } catch (Exception e) {
      logger.warn("Exception while loading db.properties. Using defaults");
    }
    

    logger.info("Using database " + jdbcUrl);
    
    // Connection settings
    ComboPooledDataSource ds = new ComboPooledDataSource();
    ds.setJdbcUrl(jdbcUrl);
    ds.setDriverClass(jdbcDriver);
    ds.setUser(jdbcUsername);
    ds.setPassword(jdbcPassword);

    // Pool config: see http://www.mchange.com/projects/c3p0/#configuration
    ds.setInitialPoolSize(50);
    ds.setMinPoolSize(10);
    ds.setMaxPoolSize(100);
    ds.setAcquireIncrement(5);
    return ds;
  }
  
  @Test
  public void testQueueOverflow() throws Exception {
    int[] nrOfProcessInstancesArray = {100, 500, 1000, 2000};
    int[] jobQueueSizeArray = {10, 100, 300};
    
    for (int nrOfProcessInstances : nrOfProcessInstancesArray) {
      for (int jobQueueSize : jobQueueSizeArray) {
        logger.info("========================================");
        logger.info("Execution test (" + nrOfProcessInstances + "," + jobQueueSize + ")");
        logger.info("========================================");
        executeActualTest(nrOfProcessInstances, jobQueueSize);
      }
    }
  }

  private void executeActualTest(int nrOfProcessInstances, int jobQueueSize) throws Exception, InterruptedException {
    
    ProcessEngine processEngine = initProcessEngineWithJobQueueSize(jobQueueSize);
    
    // Start date = Wed 20 january 2016 7:00 GMT
    Date startDate = createDate(2016, 0, 20, 7, 0, 0); 
    logger.info("Test start date = " + startDate);
    processEngine.getProcessEngineConfiguration().getClock().setCurrentTime(startDate);
    
    final RepositoryService repositoryService = processEngine.getRepositoryService();
    final RuntimeService runtimeService = processEngine.getRuntimeService();
    final HistoryService historyService = processEngine.getHistoryService();
    
    repositoryService.createDeployment().addClasspathResource("testAsyncExecutorQueueOverflow.bpmn20.xml").deploy();
    
    ExecutorService startProcessInstancesExecutorService = Executors.newFixedThreadPool(5);
    for (int i=0; i<nrOfProcessInstances; i++) {
      startProcessInstancesExecutorService.execute(new Runnable() {
        public void run() {
          runtimeService.startProcessInstanceByKey("testAsyncExecutor");
        }
      });
    }
    
    logger.info("All process instance runnables submitted");
    startProcessInstancesExecutorService.shutdown();
    startProcessInstancesExecutorService.awaitTermination(10, TimeUnit.MINUTES);
    logger.info("All process instances started");
    
    Assert.assertEquals(nrOfProcessInstances, runtimeService.createProcessInstanceQuery().count());
    
    // Move date to Weds 9:01, triggering all timers
    Date mondayMorningDate = createDate(2016, 0, 20, 9, 1, 0); 
    processEngine.getProcessEngineConfiguration().getClock().setCurrentTime(mondayMorningDate);
    logger.info("Changed the process engine clock to " + processEngine.getProcessEngineConfiguration().getClock().getCurrentTime());
    
    boolean allJobsProcessed = false;
    Date waitTimeStartDate = new Date(); 
    while (!allJobsProcessed) {
      
      long count = historyService.createHistoricActivityInstanceQuery().activityId("theServiceTask").unfinished().count();
      allJobsProcessed = count == nrOfProcessInstances; 
      
      if (!allJobsProcessed) {
        logger.info("Waiting a bit longer, not all jobs have been finished. Current count = " + count);
        Thread.sleep(1000L);
      }
      
      // To avoid looping forever
      if (new Date().getTime() - waitTimeStartDate.getTime() > (5L * 60L * 1000L)) {
        Assert.fail("Wait time for executing jobs expired");
      }
      
    }
    
    Assert.assertEquals(nrOfProcessInstances, runtimeService.createProcessInstanceQuery().count());
    Assert.assertEquals(nrOfProcessInstances, historyService.createHistoricActivityInstanceQuery().activityId("theScriptTask").finished().count());
    Assert.assertEquals(nrOfProcessInstances, historyService.createHistoricActivityInstanceQuery().activityId("theServiceTask").unfinished().count());
    
    processEngine.close();
  }

  protected ProcessEngine initProcessEngineWithJobQueueSize(int queueSize) throws Exception{
    StandaloneProcessEngineConfiguration config = new StandaloneProcessEngineConfiguration();
    
    config.setDataSource(dataSource);
    config.setDatabaseSchemaUpdate("drop-create");
    
    config.setAsyncExecutorEnabled(true);
    config.setAsyncExecutorActivate(true);
    config.setAsyncExecutorThreadPoolQueueSize(queueSize);
    config.setAsyncExecutorDefaultAsyncJobAcquireWaitTime(500);
    return config.buildProcessEngine();  
  }
  
  protected static Date createDate(int year, int month, int day, int hour, int minute, int seconds) {
    Calendar calendar = Calendar.getInstance();
    calendar.set(Calendar.YEAR, 2016);
    calendar.set(Calendar.MONTH, month);
    calendar.set(Calendar.DAY_OF_MONTH, day);
    calendar.set(Calendar.HOUR_OF_DAY, hour);
    calendar.set(Calendar.MINUTE, minute);
    calendar.set(Calendar.SECOND, seconds);
    return calendar.getTime();
  }

}
