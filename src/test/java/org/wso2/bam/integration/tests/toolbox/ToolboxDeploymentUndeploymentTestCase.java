package org.wso2.bam.integration.tests.toolbox;

/**
 * Copyright (c) 2009, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


import org.apache.axis2.client.Options;
import org.apache.axis2.client.ServiceClient;
import org.apache.axis2.context.ConfigurationContext;
import org.apache.axis2.context.ConfigurationContextFactory;
import org.apache.commons.io.FileUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.testng.annotations.*;
import org.wso2.carbon.integration.framework.ClientConnectionUtil;
import org.wso2.carbon.integration.framework.LoginLogoutUtil;
import org.wso2.carbon.integration.framework.utils.CodeCoverageUtils;
import org.wso2.carbon.integration.framework.utils.FrameworkSettings;
import org.wso2.carbon.integration.framework.utils.ServerUtils;
import org.wso2.carbon.integration.framework.utils.TestUtil;
import org.wso2.carbon.registry.resource.stub.ResourceAdminServiceStub;
import org.wso2.carbon.registry.resource.stub.beans.xsd.CollectionContentBean;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URL;

import static org.testng.Assert.assertTrue;


public class ToolboxDeploymentUndeploymentTestCase {
    private static final Log log = LogFactory.getLog(ToolboxDeploymentUndeploymentTestCase.class);


    private LoginLogoutUtil loginLogoutUtil = new LoginLogoutUtil();


    private String carbonHome = "";
    private String serviceStatToolBoxDestination = "";
    private static final String BAM_TBOX_DEPLOYMENT_DIR = "/repository/deployment/server/bam-toolbox/";
    private static final String REGISTRY_SERVICE = "/services/ResourceAdminService";
    private static final String SERVICE_STAT_TBOX_NAME = "Service_Statistics_Monitoring.tbox";
    private static final String HIVE_SCRIPT_LOCATION = "/_system/config/repository/hive/scripts/";
    private static final String SERVICE_STAT_TBOX = "/samples/toolboxes/Service_Statistics_Monitoring.tbox";
    private static final URL SERVICE_STAT_TBOX_DUMMY = ToolboxDeploymentUndeploymentTestCase.class.getClassLoader()
            .getResource(SERVICE_STAT_TBOX_NAME);

    private String carbonZip;
    private int portOffset;

    private ServerUtils serverUtils = new ServerUtils();
    private ResourceAdminServiceStub registryStub;

    private String[] initialQueries;
    private String[] replacedHiveQueries;

    private String initialHiveScriptName;


    @BeforeSuite(timeOut = 180000)
    public void setupServerAndToolBoxes() throws Exception {
        log.info("Unpacking BAM zip file");
        carbonHome = unpackCarbonZip();
        log.info("Carbon home: " + carbonHome);

        log.info("Copying Service Stat Toolbox");
        String serviceStatToolBoxSource = carbonHome + SERVICE_STAT_TBOX;
        log.info("SERVICE_STAT_TBOX_NAME source: " + serviceStatToolBoxSource);
        serviceStatToolBoxDestination = carbonHome + BAM_TBOX_DEPLOYMENT_DIR + SERVICE_STAT_TBOX_NAME;
        log.info("SERVICE_STAT_TBOX_NAME destination: " + serviceStatToolBoxDestination);
        try {
            copyFile(new File(serviceStatToolBoxSource), new File(serviceStatToolBoxDestination));
        } catch (IOException e) {
            e.printStackTrace();
        }

        log.info("Starting the Carbon server");
        startServer();

    }


    @BeforeClass(groups = {"wso2.bam"})
    public void init() throws Exception {
        log.info("Logging into Carbon server");

        String loggedInSessionCookie = loginLogoutUtil.login();
        log.info("Logged in session cookie : " + loggedInSessionCookie);

        ConfigurationContext configContext = ConfigurationContextFactory.
                createConfigurationContextFromFileSystem(null);
        initializedRegistryStub(loggedInSessionCookie, configContext);
    }


    @Test(groups = {"wso2.bam"})
    public void readingInitialHiveScripts() throws Exception {
        log.info("Reading initial Hive scripts");

        CollectionContentBean collection = registryStub
                .getCollectionContent(HIVE_SCRIPT_LOCATION);

        initialHiveScriptName = collection.getChildPaths()[0];

        log.info("Initial Hive script name : " + initialHiveScriptName);

        String hiveScript = registryStub
                .getTextContent(initialHiveScriptName).trim();

        initialQueries = hiveScript.split(";");


        logQueries(initialQueries);

    }

    @Test(groups = {"wso2.bam"}, dependsOnMethods = {"readingInitialHiveScripts"})
    public void temporarilyLogout() throws Exception {
        log.info("Temporarily logging out from Carbon server");
        logout();
    }

    @Test(groups = {"wso2.bam"}, dependsOnMethods = {"temporarilyLogout"}, timeOut = 180000)
    public void temporarilyShutdownServer() throws Exception {
        log.info("Temporarily shutting down server");
        stopServer();
    }

    @Test(groups = {"wso2.bam"}, dependsOnMethods = {"temporarilyShutdownServer"})
    public void replaceToolbox() throws Exception {
        log.info("Replacing toolbox from another toolbox!");

        copyFile(new File(SERVICE_STAT_TBOX_DUMMY.getFile()), new File(serviceStatToolBoxDestination));

    }

    @Test(groups = {"wso2.bam"}, dependsOnMethods = {"replaceToolbox"}, timeOut = 180000)
    public void restartServer() throws Exception {
        log.info("Restarting the Carbon server with the replaced toolbox");
        startServer();

        init();
    }

    @Test(groups = {"wso2.bam"}, dependsOnMethods = {"restartServer"})
    public void checkingHiveScripts() throws Exception {
        boolean nameReplaced = false;
        boolean querriesReplaced = true;

        log.info("Reading Hive scripts after server restart");
        CollectionContentBean collection = registryStub
                .getCollectionContent(HIVE_SCRIPT_LOCATION);

        String replacedHiveScriptName = collection.getChildPaths()[0];

        log.info("Replaced Hive script name : " + replacedHiveScriptName);

        String hiveScript = registryStub
                .getTextContent(replacedHiveScriptName).trim();

        replacedHiveQueries = hiveScript.split(";");
        logQueries(replacedHiveQueries);

//        if (replacedHiveScriptName != initialHiveScriptName) {
//            nameReplaced = true;
//            log.info("Script name replaced!");
//        }
//
//        if (replacedHiveQueries.length != initialQueries.length) {
//            querriesReplaced = false;
//        } else {
//            for (int i = 0; i < replacedHiveQueries.length; i++) {
//                if (replacedHiveQueries[i].equals(initialQueries[i])) {
//                    continue;
//                } else {
//                    querriesReplaced = false;
//                    break;
//                }
//            }
//        }
//
//        if (querriesReplaced) log.info("Queries have replaced!");
        assertTrue(nameReplaced, "Script has been replaced. Test successful!");
    }


    @AfterClass(groups = {"wso2.bam"})
    public void logout() throws Exception {
        log.info("Logging out from Carbon server");
        ClientConnectionUtil.waitForPort(Integer.parseInt(FrameworkSettings.HTTPS_PORT));
        loginLogoutUtil.logout();
    }

    @AfterSuite(timeOut = 180000)
    public void stopBAMServer() throws Exception {
        log.info("Stopping the server");
        stopServer();
    }


    protected void copyArtifacts(String carbonHome) throws IOException {
        // No artifacts need to be copied
    }

    private static void copyFile(File srcFile, File destFile) throws IOException {
        //File destinationFile = new File(dest.getPath() + "/" + source.getName());
        if (destFile.exists()) {
            log.info("File exists in the destination folder and it will be deleted!");
            destFile.delete();
        }
        FileUtils.copyFile(srcFile, destFile);
    }

    private String unpackCarbonZip() throws IOException {
        if (carbonZip == null) {
            carbonZip = System.getProperty("carbon.zip");
        }
        if (carbonZip == null) {
            throw new IllegalArgumentException("carbon zip file is null");
        }
        String carbonHome = serverUtils.setUpCarbonHome(carbonZip);
        TestUtil.copySecurityVerificationService(carbonHome);
        copyArtifacts(carbonHome);

        return carbonHome;
    }

    private void startServer() throws IOException {
        serverUtils.startServerUsingCarbonHome(carbonHome, portOffset);
        FrameworkSettings.init();
    }

    private void stopServer() throws Exception {
        serverUtils.shutdown(portOffset);
        CodeCoverageUtils.generateReports();
    }

    private void initializedRegistryStub(String loggedInSessionCookie,
                                         ConfigurationContext configContext) throws Exception {

        String EPR = "https://" + FrameworkSettings.HOST_NAME +
                ":" + FrameworkSettings.HTTPS_PORT + REGISTRY_SERVICE;
        registryStub = new ResourceAdminServiceStub(configContext, EPR);
        ServiceClient client = registryStub._getServiceClient();
        Options option = client.getOptions();
        option.setTimeOutInMilliSeconds(10 * 60000);
        option.setManageSession(true);
        option.setProperty(org.apache.axis2.transport.http.HTTPConstants.COOKIE_STRING,
                loggedInSessionCookie);
    }

    private void logQueries(String[] queries) {
        for (int i = 0; i < queries.length; i++) {
            log.info("query " + i + " : " + queries[i].trim());
        }
    }

}
