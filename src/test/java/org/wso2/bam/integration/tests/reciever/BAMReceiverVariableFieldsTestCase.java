/*
 * Copyright (c) 2014, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.bam.integration.tests.reciever;

import org.apache.axis2.client.Options;
import org.apache.axis2.client.ServiceClient;
import org.apache.axis2.context.ConfigurationContext;
import org.apache.axis2.context.ConfigurationContextFactory;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.testng.annotations.Test;
import org.wso2.carbon.analytics.hive.stub.HiveExecutionServiceStub;
import org.wso2.carbon.databridge.agent.thrift.Agent;
import org.wso2.carbon.databridge.agent.thrift.DataPublisher;
import org.wso2.carbon.databridge.agent.thrift.conf.AgentConfiguration;
import org.wso2.carbon.databridge.agent.thrift.exception.AgentException;
import org.wso2.carbon.databridge.commons.Event;
import org.wso2.carbon.databridge.commons.exception.*;
import org.wso2.carbon.integration.framework.LoginLogoutUtil;
import org.wso2.carbon.integration.framework.utils.FrameworkSettings;

import java.net.*;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static org.testng.Assert.assertTrue;

public class BAMReceiverVariableFieldsTestCase {

    private static final Log log = LogFactory.getLog(BAMReceiverVariableFieldsTestCase.class);
    private ThreadPoolExecutor threadPoolExecutor;

    private static final int NUMBER_OF_THREADS = 10;
    private static final long KEEP_ALIVE_TIME = 10;

    private static final String STREAM_NAME = "org.wso2.carbon.bam.variable.fields.test";
    private static final String VERSION = "1.0.0";

    private LoginLogoutUtil util = new LoginLogoutUtil();
    private static final String HIVE_SERVICE = "/services/HiveExecutionService";

    private HiveExecutionServiceStub hiveStub;


    private void init() {
        threadPoolExecutor = new ThreadPoolExecutor(NUMBER_OF_THREADS, NUMBER_OF_THREADS,
                KEEP_ALIVE_TIME, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>());
    }

    @Test(groups = {"wso2.bam"})
    public void publishConcurrentEvents() throws
            Exception {
        init();

        for (int i = 0; i < NUMBER_OF_THREADS; i++) {
            BAMDataPublisher publisher = new BAMDataPublisher(i + 1, STREAM_NAME, VERSION);
            threadPoolExecutor.execute(publisher);
        }
        threadPoolExecutor.shutdown();

        //waiting till hive receiver saves all published data.
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }

        checkPublishedDataInCassandra();
    }


    private void checkPublishedDataInCassandra() throws Exception {
        initializeHiveStub();

        hiveStub.executeHiveScript(null, "CREATE EXTERNAL TABLE IF NOT EXISTS BAMVarFieldTest" +
                " (eventId STRING, message STRING, publisherId INT) " +
                "STORED BY 'org.apache.hadoop.hive.cassandra.CassandraStorageHandler' WITH SERDEPROPERTIES" +
                " ( \"cassandra.host\" = \"127.0.0.1\" , \"cassandra.port\" = \"9160\" , " +
                "\"cassandra.ks.name\" = \"EVENT_KS\" , \"cassandra.ks.username\" = \"admin\" ," +
                " \"cassandra.ks.password\" = \"admin\" , \"cassandra.cf.name\" = \"org_wso2_carbon_bam_variable_fields_test\" ," +
                " \"cassandra.columns.mapping\" = \":key,payload_message, payload_publisherId\" );");

        HiveExecutionServiceStub.QueryResult[] results = hiveStub.executeHiveScript(null, "SELECT Count(1) From BAMVarFieldTest");

        assertTrue(null != results || results.length == 0, "No results are returned to published test events");

        HiveExecutionServiceStub.QueryResultRow[] rows = results[0].getResultRows();
        assertTrue(null != rows || rows.length == 0, "No results are returned to published test events");

        String[] vals = rows[0].getColumnValues();
        assertTrue(null != vals || vals.length == 0, "No results are returned to published test events");

        int savedEvents = Integer.parseInt(vals[0]);

        int actualEventSent = NUMBER_OF_THREADS * BAMDataPublisher.NUMBER_EVENTS;
        assertTrue(savedEvents == actualEventSent, "Actual Events sent and the saved events are different");

    }

    private void initializeHiveStub() throws Exception {
        ConfigurationContext configContext = ConfigurationContextFactory.
                createConfigurationContextFromFileSystem(null);

        String loggedInSessionCookie = util.login();

        String EPR = "https://" + FrameworkSettings.HOST_NAME +
                ":" + FrameworkSettings.HTTPS_PORT + HIVE_SERVICE;
        hiveStub = new HiveExecutionServiceStub(configContext, EPR);
        ServiceClient client = hiveStub._getServiceClient();
        Options option = client.getOptions();
        option.setTimeOutInMilliSeconds(10*60000);
        option.setManageSession(true);
        option.setProperty(org.apache.axis2.transport.http.HTTPConstants.COOKIE_STRING,
                loggedInSessionCookie);
    }


    private class BAMDataPublisher implements Runnable {
        private final Log log = LogFactory.getLog(BAMDataPublisher.class);

        private int publisherId;
        private DataPublisher dataPublisher;

        private String streamName;
        private String version;

        private static final int NUMBER_EVENTS = 20;

        private BAMDataPublisher(int id, String streamName, String version) throws AgentException, MalformedURLException, AuthenticationException, SocketException, TransportException {
            this.publisherId = id;
            this.streamName = streamName;
            this.version = version;
            String host = getLocalHostAddress().getHostAddress();
            dataPublisher = new DataPublisher("tcp://" + host + ":7611", "admin", "admin");
        }

        @Override
        public void run() {
            try {
                Map<String, String> map;
                String streamId = defineEventStream();
                int iter = 0;
                while (iter < NUMBER_EVENTS/5) {
                    map = new HashMap<String, String>();
                    publishEvent(streamId, map);

                    map = new HashMap<String, String>();
                    map.put("key1", "value1");
                    publishEvent(streamId, map);

                    map = new HashMap<String, String>();
                    map.put("message", "message1");
                    publishEvent(streamId, map);

                    map = new HashMap<String, String>();
                    map.put("key1", "value1");
                    map.put("key2", "value2");
                    publishEvent(streamId, map);

                    map = new HashMap<String, String>();
                    map.put("key1", "value1");
                    map.put("key2", "value2");
                    map.put("message", "message1");
                    publishEvent(streamId, map);

                    /* Finally in Cassandra database, there should be,
                     * 1. NUMBER_EVENTS/5 * 3  ("key1", "value1") pairs,
                     * 2. NUMBER_EVENTS/5 * 2  ("key2", "value2") pairs,
                     * 3. NUMBER_EVENTS/5 * 2  ("message", "message1") pairs and
                     * 4. NUMBER_EVENTS/5 * 1  pairs without any key value pairs.
                     * */
                    iter++;
                }
                Thread.sleep(1000);
                dataPublisher.stop();
            } catch (AgentException e) {
                log.error(e.getErrorMessage(), e);
            } catch (StreamDefinitionException e) {
                log.error(e.getErrorMessage(), e);
            } catch (MalformedStreamDefinitionException e) {
                log.error(e.getErrorMessage(), e);
            } catch (DifferentStreamDefinitionAlreadyDefinedException e) {
                log.error(e.getErrorMessage(), e);
            } catch (InterruptedException e) {

            }

        }

        private String defineEventStream() throws AgentException, StreamDefinitionException,
                MalformedStreamDefinitionException, DifferentStreamDefinitionAlreadyDefinedException {
            String streamId = dataPublisher.defineStream("{" +
                    "  'name':'" + streamName + "'," +
                    "  'version':'" + version + "'," +
                    "  'nickName': 'Integration_test'," +
                    "  'description': 'Integration tests events'," +
                    "  'metaData':[" +
                    "          {'name':'clientType','type':'STRING'}" +
                    "  ]," +
                    "  'payloadData':[" +
                    "          {'name':'message','type':'STRING'}," +
                    "          {'name':'publisherId','type':'INT'}" +
                    "  ]" +
                    "}");
            return streamId;
        }

        private void publishEvent(String streamId, Map map) throws AgentException {
            Event event = new Event(streamId, System.currentTimeMillis(), new Object[]{"external"}, null,
                    new Object[]{"Integration test Message: " + publisherId, publisherId}, map);
            dataPublisher.publish(event);
        }


        private InetAddress getLocalHostAddress() throws SocketException {
            Enumeration<NetworkInterface> ifaces = NetworkInterface.getNetworkInterfaces();
            while (ifaces.hasMoreElements()) {
                NetworkInterface iface = ifaces.nextElement();
                Enumeration<InetAddress> addresses = iface.getInetAddresses();

                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    if (addr instanceof Inet4Address && !addr.isLoopbackAddress()) {
                        return addr;
                    }
                }
            }

            return null;
        }
    }
}
