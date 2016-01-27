package org.activiti.test.asyncexecutor;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mchange.v2.c3p0.ComboPooledDataSource;

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

/**
 * @author Joram Barrez
 */
public class DataSourceBuilder {
  
  private static final Logger logger = LoggerFactory.getLogger(DataSourceBuilder.class);
  
  protected String jdbcSchema;
  
  public ComboPooledDataSource createDataSource() throws Exception {
    Properties defaultProperties = new Properties();
    defaultProperties.load(DataSourceBuilder.class.getClassLoader().getResourceAsStream("default-db.properties"));
    String jdbcUrl = defaultProperties.getProperty("jdbc.url");
    String jdbcDriver = defaultProperties.getProperty("jdbc.driver");
    String jdbcUsername = defaultProperties.getProperty("jdbc.username");
    String jdbcPassword = defaultProperties.getProperty("jdbc.password");
    
    // We need to set it on the engine later, hence why it's handles differently
    jdbcSchema = defaultProperties.getProperty("jdbc.schema");
    
    Properties dbProperties = new Properties();
    try {
      dbProperties.load(DataSourceBuilder.class.getClassLoader().getResourceAsStream("db.properties"));
      
      jdbcUrl = dbProperties.getProperty("jdbc.url");
      jdbcDriver = dbProperties.getProperty("jdbc.driver");
      jdbcUsername = dbProperties.getProperty("jdbc.username");
      jdbcPassword = dbProperties.getProperty("jdbc.password");
      jdbcSchema = dbProperties.getProperty("jdbc.schema");
      
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

  public String getJdbcSchema() {
    return jdbcSchema;
  }
  
}
