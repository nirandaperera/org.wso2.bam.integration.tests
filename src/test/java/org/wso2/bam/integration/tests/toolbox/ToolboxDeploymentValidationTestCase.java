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

package org.wso2.bam.integration.tests.toolbox;

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

/**
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
    private static final String SERVICE_STAT_TBOX_PATH = "/samples/toolboxes/Service_Statistics_Monitoring.tbox";
    private static final String HOST_NAME = "localhost";
    private static final String AUTHENTICATION_SERVICE = "/services/AuthenticationAdmin";

    private int portOffset = 3;
    private String carbonZip;
    private String carbonHome;
    private String serviceStatToolBoxDestination;
    private String initialHiveScriptName;
    private String initialScript;
    private ResourceAdminServiceStub registryStub;
    private String sessionCookie;

    private ServerUtils serverUtils = new ServerUtils();
    private static final URL SERVICE_STAT_TBOX_DUMMY = ToolboxDeploymentValidationTestCase.class.getClassLoader()
            .getResource(SERVICE_STAT_TBOX_NAME);

    @BeforeClass(groups = {"wso2.bam"}, timeOut = 180000)
    public void init() throws Exception {
        log.info("Starting the Offline Toolbox Deployment Validation TestCase ...");
        carbonHome = unpackCarbonZip();

        //Copy the toolbox to the BAM
        String serviceStatToolBoxSource = carbonHome + SERVICE_STAT_TBOX_PATH;
        serviceStatToolBoxDestination = carbonHome + BAM_TBOX_DEPLOYMENT_DIR + SERVICE_STAT_TBOX_NAME;
        FileUtils.copyFile(new File(serviceStatToolBoxSource), new File(serviceStatToolBoxDestination));

        startServer();
        String loggedInSessionCookie = login();
        ConfigurationContext configContext = ConfigurationContextFactory.
                createConfigurationContextFromFileSystem(null);
        initializedRegistryStub(loggedInSessionCookie, configContext);
    }

    @Test(groups = {"wso2.bam"})
    public void readingInitialHiveScripts() throws Exception {
        CollectionContentBean collection = registryStub
                .getCollectionContent(HIVE_SCRIPT_LOCATION);
        initialHiveScriptName = collection.getChildPaths()[0];
        initialScript = registryStub
                .getTextContent(initialHiveScriptName).trim();
    }

    @Test(groups = {"wso2.bam"}, dependsOnMethods = {"readingInitialHiveScripts"}, timeOut = 180000)
    public void temporarilyLogoutAndStopServer() throws Exception {
        ClientConnectionUtil.waitForPort(Integer.parseInt(FrameworkSettings.HTTPS_PORT));
        logout();
        stopServer();

        //Copying the new toolbox
        File destFile = new File(serviceStatToolBoxDestination);
        if (destFile.exists()) {
            destFile.delete();
        }
        FileUtils.copyFile(new File(SERVICE_STAT_TBOX_DUMMY.getFile()), destFile);
    }

    @Test(groups = {"wso2.bam"}, dependsOnMethods = {"temporarilyLogoutAndStopServer"}, timeOut = 180000)
    public void restartServer() throws Exception {
        startServer();
        String loggedInSessionCookie = login();
        ConfigurationContext configContext = ConfigurationContextFactory.
                createConfigurationContextFromFileSystem(null);
        initializedRegistryStub(loggedInSessionCookie, configContext);
    }

    @Test(groups = {"wso2.bam"}, dependsOnMethods = {"restartServer"})
    public void checkingHiveScripts() throws Exception {
        boolean nameReplaced = false;
        boolean contentReplaced = false;

        //Reading Hive scripts after server restart
        CollectionContentBean collection = registryStub
                .getCollectionContent(HIVE_SCRIPT_LOCATION);
        String replacedHiveScriptName = collection.getChildPaths()[0];
        String replacedScript = registryStub.getTextContent(replacedHiveScriptName).trim();
        if (!replacedHiveScriptName.equals(initialHiveScriptName)) {
            nameReplaced = true;
            if (!replacedScript.equals(initialScript)) {
                contentReplaced = true;
            }
        }

        assertTrue(nameReplaced, "Script name is the same. It may not have been replaced!");
        assertTrue(contentReplaced, "Script content is the same!");
    }

    @AfterClass(groups = {"wso2.bam"}, timeOut = 180000)
    public void logoutAndStopServer() throws Exception {
        ClientConnectionUtil.waitForPort(Integer.parseInt(FrameworkSettings.HTTPS_PORT));
        logout();
        stopServer();
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
        int httpsPort = 9446;
        String EPR = "https://" + FrameworkSettings.HOST_NAME + ":"
                + httpsPort
                + REGISTRY_SERVICE;
        registryStub = new ResourceAdminServiceStub(configContext, EPR);
        ServiceClient client = registryStub._getServiceClient();
        Options option = client.getOptions();
        option.setTimeOutInMilliSeconds(10 * 60000);
        option.setManageSession(true);
        option.setProperty(org.apache.axis2.transport.http.HTTPConstants.COOKIE_STRING,
                loggedInSessionCookie);
    }

    public String login() throws Exception {
        ClientConnectionUtil.waitForPort(Integer.parseInt(FrameworkSettings.HTTPS_PORT) + portOffset);
        AuthenticationAdminStub authAdminStub;

        authAdminStub = getAuthAdminStub(null);

        boolean isLoggedIn = authAdminStub.login(FrameworkSettings.USER_NAME,
                FrameworkSettings.PASSWORD, HOST_NAME);
        assertTrue(isLoggedIn, "Login failed!");
        ServiceContext serviceContext = authAdminStub.
                _getServiceClient().getLastOperationContext().getServiceContext();
        sessionCookie = (String) serviceContext.getProperty(HTTPConstants.COOKIE_STRING);
        assertTrue(sessionCookie != null, "Logged in session cookie is null");
        return sessionCookie;
    }

    public void logout() throws Exception {
        AuthenticationAdminStub authenticationAdminStub = getAuthAdminStub(null);
        Options options = authenticationAdminStub._getServiceClient().getOptions();
        options.setProperty(org.apache.axis2.transport.http.HTTPConstants.COOKIE_STRING,
                sessionCookie);
        authenticationAdminStub.logout();
    }

    private AuthenticationAdminStub getAuthAdminStub(String carbonManagementContext) throws AxisFault {
        String authenticationServiceURL;
        if (carbonManagementContext == null || carbonManagementContext.trim().isEmpty()) {
            authenticationServiceURL =
                    "https://" + HOST_NAME + ":" + (Integer.parseInt(FrameworkSettings.HTTPS_PORT) + portOffset) +
                            AUTHENTICATION_SERVICE;
        } else {
            authenticationServiceURL =
                    "https://" + HOST_NAME + ":" + (Integer.parseInt(FrameworkSettings.HTTPS_PORT) + portOffset) +
                            "/" + carbonManagementContext + AUTHENTICATION_SERVICE;
        }
        AuthenticationAdminStub authenticationAdminStub =
                new AuthenticationAdminStub(authenticationServiceURL);
        ServiceClient client = authenticationAdminStub._getServiceClient();
        Options options = client.getOptions();
        options.setManageSession(true);
        return authenticationAdminStub;
    }
}
