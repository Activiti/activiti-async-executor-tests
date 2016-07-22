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
package org.activiti6.test.asyncexecutor;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Calendar;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;

import org.activiti.engine.HistoryService;
import org.activiti.engine.ProcessEngine;
import org.activiti.engine.ProcessEngineLifecycleListener;
import org.activiti.engine.RepositoryService;
import org.activiti.engine.RuntimeService;
import org.activiti.engine.impl.cfg.StandaloneProcessEngineConfiguration;
import org.activiti.engine.impl.db.Entity;
import org.activiti.engine.impl.db.EntityDependencyOrder;
import org.activiti.engine.impl.interceptor.Command;
import org.activiti.engine.impl.interceptor.CommandContext;
import org.activiti.engine.impl.persistence.entity.HistoricScopeInstanceEntityImpl;
import org.activiti.engine.impl.persistence.entity.PropertyEntityImpl;
import org.activiti.engine.impl.persistence.entity.TableDataManagerImpl;
import org.activiti.test.asyncexecutor.DataSourceBuilder;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Joram Barrez
 */
public class AsyncExecutorTest {
  
  private static final Logger logger = LoggerFactory.getLogger(AsyncExecutorTest.class);
  
  protected static DataSourceBuilder dataSourceBuilder;
  protected static DataSource dataSource;
  
  @BeforeClass
  public static void setupDataSource() throws Exception {
    dataSourceBuilder = new DataSourceBuilder();
    dataSource = dataSourceBuilder.createDataSource();
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

  private void executeActualTest(final int nrOfProcessInstances, final int jobQueueSize) throws Exception, InterruptedException {
    
    ProcessEngine processEngine = initProcessEngineWithJobQueueSize(jobQueueSize);
    
    // Start date = Wed 20 january 2016 7:00 GMT
    Date startDate = createDate(2016, 0, 20, 7, 0, 0); 
    logger.info("Test start date = " + startDate);
    processEngine.getProcessEngineConfiguration().getClock().setCurrentTime(startDate);
    
    final RepositoryService repositoryService = processEngine.getRepositoryService();
    final RuntimeService runtimeService = processEngine.getRuntimeService();
    final HistoryService historyService = processEngine.getHistoryService();
    
    repositoryService.createDeployment().addClasspathResource("testAsyncExecutorQueueOverflow.bpmn20.xml").deploy();
    startProcessInstances(nrOfProcessInstances, runtimeService, "testAsyncExecutor");
    
    Assert.assertEquals(nrOfProcessInstances, runtimeService.createProcessInstanceQuery().count());
    
    // Move date to Weds 9:01, triggering all timers
    Date mondayMorningDate = createDate(2016, 0, 20, 9, 1, 0); 
    processEngine.getProcessEngineConfiguration().getClock().setCurrentTime(mondayMorningDate);
    logger.info("Changed the process engine clock to " + processEngine.getProcessEngineConfiguration().getClock().getCurrentTime());
    
    waitForCondition(processEngine, new WaitCondition() {
      public boolean evaluate(ProcessEngine processEngine) {
        long count = historyService.createHistoricActivityInstanceQuery().activityId("theServiceTask").unfinished().count();
        return count == nrOfProcessInstances; 
      }
    });
    
    Assert.assertEquals(nrOfProcessInstances, runtimeService.createProcessInstanceQuery().count());
    Assert.assertEquals(nrOfProcessInstances, historyService.createHistoricActivityInstanceQuery().activityId("theScriptTask").finished().count());
    Assert.assertEquals(nrOfProcessInstances, historyService.createHistoricActivityInstanceQuery().activityId("theServiceTask").unfinished().count());
    
    processEngine.close();
  }

  @Test
  public void testAsyncExecutionWithParallelExclusiveJobs() throws Exception {
    ProcessEngine processEngine = initProcessEngineWithJobQueueSize(100);
    
    final RepositoryService repositoryService = processEngine.getRepositoryService();
    final RuntimeService runtimeService = processEngine.getRuntimeService();
    final HistoryService historyService = processEngine.getHistoryService();
    
    repositoryService.createDeployment().addClasspathResource("testAsyncExecutorParallelExclusiveServiceTasks.bpmn20.xml").deploy();
    
    final int nrOfProcessInstances = 300;
    startProcessInstances(nrOfProcessInstances, runtimeService, "parallelExclusiveServiceTasks");
    Assert.assertEquals(nrOfProcessInstances, runtimeService.createProcessInstanceQuery().count());
    
    waitForCondition(processEngine, new WaitCondition() {
      public boolean evaluate(ProcessEngine processEngine) {
        long count = processEngine.getHistoryService().createHistoricTaskInstanceQuery().taskName("taskBeforeEnd").count();
        System.out.println("Count is " + count);
        return count == nrOfProcessInstances;
      }
    });
    
    for (int i=1; i<=9; i++) {
      Assert.assertEquals(nrOfProcessInstances, historyService.createHistoricActivityInstanceQuery()
          .activityName("ServiceTask" + i).finished().count());
    }
    
    processEngine.close();
  }
  
  /*
   * This test uses many exclusive service tasks that take 0.5 second each.
   * This leads to a lot of failed lockings of the process instance.
   */
  @Test
  public void testAsyncExecutionWithParallelExclusiveSlowJobs() throws Exception {
    ProcessEngine processEngine = initProcessEngineWithJobQueueSize(100);
    
    final RepositoryService repositoryService = processEngine.getRepositoryService();
    final RuntimeService runtimeService = processEngine.getRuntimeService();
    final HistoryService historyService = processEngine.getHistoryService();
    
    repositoryService.createDeployment().addClasspathResource("testAsyncExecutorParallelExclusiveSlowServiceTasks.bpmn20.xml").deploy();
    
    final int nrOfProcessInstances = 100;
    startProcessInstances(nrOfProcessInstances, runtimeService, "parallelExclusiveServiceTasks");
    Assert.assertEquals(nrOfProcessInstances, runtimeService.createProcessInstanceQuery().count());
    
    waitForCondition(processEngine, new WaitCondition() {
      public boolean evaluate(ProcessEngine processEngine) {
        long count = processEngine.getHistoryService().createHistoricTaskInstanceQuery().taskName("taskBeforeEnd").count();
        System.out.println("Count is " + count);
        return count == nrOfProcessInstances;
      }
    });
    
    for (int i=1; i<=9; i++) {
      Assert.assertEquals(nrOfProcessInstances, historyService.createHistoricActivityInstanceQuery()
          .activityName("ServiceTask" + i).finished().count());
    }
    
    processEngine.close();
  }
  
  /*
   * If more tests are added, would probably make sense to move these up to a superclass 
   */
  
  protected ProcessEngine initProcessEngineWithJobQueueSize(int queueSize) throws Exception{
    try {
      return createProcessEngine(queueSize, "true");
    } catch (Exception e) {
      // oracle throwing a SqlException ... and the engine catching a RuntimeException...
      // Solving it by recreating the process engine without the drop
      return createProcessEngine(queueSize, "true");
    }
  }

  private ProcessEngine createProcessEngine(int queueSize, String dbSchemaSetting) {
    StandaloneProcessEngineConfiguration config = new StandaloneProcessEngineConfiguration();
    
    config.setDataSource(dataSource);
    config.setDatabaseSchemaUpdate(dbSchemaSetting);
    
    config.setAsyncExecutorActivate(true);
    config.setAsyncExecutorThreadPoolQueueSize(queueSize);
    config.setAsyncExecutorDefaultAsyncJobAcquireWaitTime(100);
    config.setAsyncExecutorDefaultTimerJobAcquireWaitTime(100);
    
    String jdbcSchema = dataSourceBuilder.getJdbcSchema();
    if (jdbcSchema != null && !"".equals(jdbcSchema)) {
      config.setDatabaseSchema(jdbcSchema);
    }
    
    // Since there are different tables in v5 vs v6 (for example the job tables), we can't use
    // 'drop-create' here as this won't work when going from v5->v6.
    // Hence, why we're doing a manual delete instead
    config.setProcessEngineLifecycleListener(new ProcessEngineLifecycleListener() {
      
      @Override
      public void onProcessEngineBuilt(ProcessEngine processEngine) {
        processEngine.getManagementService().executeCommand(new Command<Void>() {
          public Void execute(CommandContext commandContext) {
            for (Class<? extends Entity> entity : EntityDependencyOrder.DELETE_ORDER) {
              if (!entity.equals(HistoricScopeInstanceEntityImpl.class) && !entity.equals(PropertyEntityImpl.class)) {
                Statement statement = null;
                try {
                  statement = commandContext.getDbSqlSession().getSqlSession().getConnection().createStatement();
                  String table = entityClassToTable(entity);
                  if (table != null) {
                    System.out.println("Delete from " + table);
                    statement.executeUpdate("delete from " + table);
                  }
                } catch (SQLException e) {
                  e.printStackTrace();
                } finally {
                  if (statement != null) {
                    try {
                      statement.close();
                    } catch (SQLException e) {
                      e.printStackTrace();
                    }
                  }
                }
              }
            }
            return null;
          }
        });
      }
      
      @Override
      public void onProcessEngineClosed(ProcessEngine processEngine) {
        
      }
      
    });
    
    return config.buildProcessEngine();
  }
  
  private void startProcessInstances(int nrOfProcessInstances,
      final RuntimeService runtimeService, final String processDefinitionKey) throws InterruptedException {
    ExecutorService startProcessInstancesExecutorService = Executors.newFixedThreadPool(5);
    for (int i=0; i<nrOfProcessInstances; i++) {
      startProcessInstancesExecutorService.execute(new Runnable() {
        public void run() {
          runtimeService.startProcessInstanceByKey(processDefinitionKey);
        }
      });
    }
    
    logger.info("All process instance runnables submitted");
    startProcessInstancesExecutorService.shutdown();
    startProcessInstancesExecutorService.awaitTermination(10, TimeUnit.MINUTES);
    logger.info("All process instances started");
  }
  
  protected Date createDate(int year, int month, int day, int hour, int minute, int seconds) {
    Calendar calendar = Calendar.getInstance();
    calendar.set(Calendar.YEAR, 2016);
    calendar.set(Calendar.MONTH, month);
    calendar.set(Calendar.DAY_OF_MONTH, day);
    calendar.set(Calendar.HOUR_OF_DAY, hour);
    calendar.set(Calendar.MINUTE, minute);
    calendar.set(Calendar.SECOND, seconds);
    return calendar.getTime();
  }
  
  protected void waitForCondition(ProcessEngine processEngine, WaitCondition waitCondition) throws InterruptedException {
    boolean allJobsProcessed = false;
    Date waitTimeStartDate = new Date(); 
    while (!allJobsProcessed) {
      
      allJobsProcessed = waitCondition.evaluate(processEngine);
      
      if (!allJobsProcessed) {
        logger.info("Waiting a bit longer, not all jobs have been finished.");
        Thread.sleep(1000L);
      }
      
      // To avoid looping forever
      if (new Date().getTime() - waitTimeStartDate.getTime() > (5L * 60L * 1000L)) {
        Assert.fail("Wait time for executing jobs expired");
      }
      
    }
  }
  
  public static interface WaitCondition {
    boolean evaluate(ProcessEngine processEngine);
  }
  
  protected static String entityClassToTable(Class<? extends Entity> entityClass) {
    Map<Class<? extends Entity>, String> entityToTableNameMap = TableDataManagerImpl.entityToTableNameMap;
    if (entityToTableNameMap.containsKey(entityClass)) {
      return entityToTableNameMap.get(entityClass);
    }
    
    Class[] interfaces = entityClass.getInterfaces();
    for (Class interfaceClass : interfaces) {
      if (entityToTableNameMap.containsKey(interfaceClass)) {
        return entityToTableNameMap.get(interfaceClass);
      }
    }
    return null;
  }

}
