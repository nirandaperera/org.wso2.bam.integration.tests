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


import org.apache.axis2.AxisFault;
import org.apache.axis2.client.Options;
import org.apache.axis2.client.ServiceClient;
import org.apache.axis2.context.ConfigurationContext;
import org.apache.axis2.context.ConfigurationContextFactory;
import org.apache.axis2.context.ServiceContext;
import org.apache.axis2.transport.http.HTTPConstants;
import org.apache.commons.httpclient.auth.AuthenticationException;
import org.apache.commons.io.FileUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.wso2.carbon.authenticator.stub.AuthenticationAdminStub;
import org.wso2.carbon.integration.framework.ClientConnectionUtil;
import org.wso2.carbon.integration.framework.utils.CodeCoverageUtils;
import org.wso2.carbon.integration.framework.utils.FrameworkSettings;
import org.wso2.carbon.integration.framework.utils.ServerUtils;
import org.wso2.carbon.integration.framework.utils.TestUtil;
import org.wso2.carbon.registry.resource.stub.ResourceAdminServiceStub;
import org.wso2.carbon.registry.resource.stub.beans.xsd.CollectionContentBean;

import java.io.File;
import java.io.IOException;
import java.net.URL;

import static org.testng.Assert.assertTrue;

/*
* This test case validates the following use case.
* Once a toolbox is deployed, the analytic scripts are being saved in the registry.
* It may be needed to change these analytic scripts time to time and the most convenient way to achieve this is, replacing the toolbox in the deployment folder of BAM.
* This test validates if the existing scripts in the registry are being correctly replaced by the new scripts.
*/
public class ToolboxDeploymentValidationTestCase {
    private static final Log log = LogFactory.getLog(ToolboxDeploymentValidationTestCase.class);

    private static final String BAM_TBOX_DEPLOYMENT_DIR = "/repository/deployment/server/bam-toolbox/";
    private static final String REGISTRY_SERVICE = "/services/ResourceAdminService";
    private static final String SERVICE_STAT_TBOX_NAME = "Service_Statistics_Monitoring.tbox";
    private static final String HIVE_SCRIPT_LOCATION = "/_system/config/repository/hive/scripts/";
    private static final String SERVICE_STAT_TBOX = "/samples/toolboxes/Service_Statistics_Monitoring.tbox";
    private static final String HOST_NAME = "localhost";

    private static final URL SERVICE_STAT_TBOX_DUMMY = ToolboxDeploymentValidationTestCase.class.getClassLoader()
            .getResource(SERVICE_STAT_TBOX_NAME);

    private int portOffset = 3;
    private String carbonZip;
    private String carbonHome;
    private String serviceStatToolBoxDestination;
    private String initialHiveScriptName;
    private String initialScript;
    private String replacedScript;
    private ResourceAdminServiceStub registryStub;

    private String sessionCookie;

    private ServerUtils serverUtils = new ServerUtils();
//    private LoginLogoutUtil loginLogoutUtil = new LoginLogoutUtil();

//    @Test(groups = {"wso2.bam"}, timeOut = 180000)
//    public void beforeClass() {
//        log.info("Stopping any existing servers!");
//        System.out.println(FrameworkSettings.CARBON_HOME);
//        carbonHome= FrameworkSettings.CARBON_HOME;
//
//        BAMTestServerManager mgr = new BAMTestServerManager();
//
//        System.out.println("stopping server **************************");
//        try {
//            mgr.stopServer();
////            stopServer();
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
//
//        System.out.println("*****************starting server ****************");
//        try {
//            startServer();
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//        System.out.println(FrameworkSettings.CARBON_HOME);
//
//
//    }

    @BeforeClass(groups = {"wso2.bam"}, timeOut = 360000)
    public void init() throws Exception {


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

        log.info("Logging into Carbon server");

        String loggedInSessionCookie = login();// loginLogoutUtil.login();

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

        initialScript = registryStub
                .getTextContent(initialHiveScriptName).trim();

        String[] initialQueries = initialScript.split(";");
        logQueries(initialQueries);
    }

    @Test(groups = {"wso2.bam"}, dependsOnMethods = {"readingInitialHiveScripts"}, timeOut = 180000)
    public void temporarilyLogoutAndStopServer() throws Exception {
        log.info("Temporarily logging out and stopping the Carbon server");
        logoutAndStopServer();
    }

    @Test(groups = {"wso2.bam"}, dependsOnMethods = {"temporarilyLogoutAndStopServer"})
    public void replaceToolbox() throws Exception {
        log.info("Replacing toolbox from another toolbox!");
        copyFile(new File(SERVICE_STAT_TBOX_DUMMY.getFile()), new File(serviceStatToolBoxDestination));
    }

    @Test(groups = {"wso2.bam"}, dependsOnMethods = {"replaceToolbox"}, timeOut = 180000)
    public void restartServer() throws Exception {
        log.info("Restarting the Carbon server with the replaced toolbox");
        startServer();

        log.info("Logging into Carbon server");

        String loggedInSessionCookie = login();
        log.info("Logged in session cookie : " + loggedInSessionCookie);

        ConfigurationContext configContext = ConfigurationContextFactory.
                createConfigurationContextFromFileSystem(null);
        initializedRegistryStub(loggedInSessionCookie, configContext);
    }

    @Test(groups = {"wso2.bam"}, dependsOnMethods = {"restartServer"})
    public void checkingHiveScripts() throws Exception {
        boolean nameReplaced = false;
        boolean contentReplaced = false;

        log.info("Reading Hive scripts after server restart");
        CollectionContentBean collection = registryStub
                .getCollectionContent(HIVE_SCRIPT_LOCATION);

        String replacedHiveScriptName = collection.getChildPaths()[0];

        log.info("Replaced Hive script name : " + replacedHiveScriptName);

        replacedScript = registryStub
                .getTextContent(replacedHiveScriptName).trim();

        String[] replacedHiveQueries = replacedScript.split(";");
        logQueries(replacedHiveQueries);

        if (!replacedHiveScriptName.equals(initialHiveScriptName)) {
            nameReplaced = true;
            log.info("Script name replaced!");

            if (!replacedScript.equals(initialScript)) {
                contentReplaced = true;
                log.info("Script replaced!");
            }
        }

        assertTrue(nameReplaced, "Script name is the same. It may not have been replaced!");
        assertTrue(contentReplaced, "Script content is the same!");
    }


    @AfterClass(groups = {"wso2.bam"}, timeOut = 180000)
    public void logoutAndStopServer() throws Exception {
        log.info("Logging out from Carbon server");
        ClientConnectionUtil.waitForPort(Integer.parseInt(FrameworkSettings.HTTPS_PORT));
        logout();

        log.info("Stopping the server");
        stopServer();
    }


    protected void copyArtifacts(String carbonHome) throws IOException {
        // No artifacts need to be copied
    }

    private static void copyFile(File srcFile, File destFile) throws IOException {
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

        String EPR = "https://" + FrameworkSettings.HOST_NAME + ":"
                + 9446//FrameworkSettings.HTTPS_PORT
                + REGISTRY_SERVICE;
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

    public String login() throws Exception {

        ClientConnectionUtil.waitForPort(Integer.parseInt(FrameworkSettings.HTTPS_PORT) + portOffset);
        AuthenticationAdminStub authAdminStub;

        authAdminStub = getAuthAdminStub(null);

        if (log.isDebugEnabled()) {
            log.debug("UserName : " + FrameworkSettings.USER_NAME + " Password : " +
                    FrameworkSettings.PASSWORD + " HostName : " + HOST_NAME);
        }
        boolean isLoggedIn = authAdminStub.login(FrameworkSettings.USER_NAME,
                FrameworkSettings.PASSWORD, HOST_NAME);
        assert isLoggedIn : "Login failed!";
        log.debug("getting sessionCookie");
        ServiceContext serviceContext = authAdminStub.
                _getServiceClient().getLastOperationContext().getServiceContext();
        sessionCookie = (String) serviceContext.getProperty(HTTPConstants.COOKIE_STRING);
        assert sessionCookie != null : "Logged in session cookie is null";
        if (log.isDebugEnabled()) {
            log.debug("sessionCookie : " + sessionCookie);
        }
        log.info("Successfully logged in : " + sessionCookie);
        return sessionCookie;
    }

    public void logout() throws Exception {
        AuthenticationAdminStub authenticationAdminStub = getAuthAdminStub(null);

        try {
            Options options = authenticationAdminStub._getServiceClient().getOptions();
            options.setProperty(org.apache.axis2.transport.http.HTTPConstants.COOKIE_STRING,
                    sessionCookie);
            authenticationAdminStub.logout();
        } catch (Exception e) {
            String msg = "Error occurred while logging out";
            log.error(msg, e);
            throw new AuthenticationException(msg, e);
        }
    }

    private AuthenticationAdminStub getAuthAdminStub(String carbonManagementContext) throws AxisFault {
        String authenticationServiceURL;
        if (carbonManagementContext == null || carbonManagementContext.trim().equals("")) {
            authenticationServiceURL =
                    "https://localhost:"+ (Integer.parseInt(FrameworkSettings.HTTPS_PORT) + portOffset) +
                            "/services/AuthenticationAdmin";
        } else {
            authenticationServiceURL =
                    "https://localhost:" + (Integer.parseInt(FrameworkSettings.HTTPS_PORT) + portOffset) +
                            "/" + carbonManagementContext + "/services/AuthenticationAdmin";
        }

        if (log.isDebugEnabled()) {
            log.debug("AuthenticationAdminService URL = " + authenticationServiceURL);
        }
        AuthenticationAdminStub authenticationAdminStub =
                new AuthenticationAdminStub(authenticationServiceURL);
        ServiceClient client = authenticationAdminStub._getServiceClient();
        Options options = client.getOptions();
        options.setManageSession(true);
        return authenticationAdminStub;
    }



}
