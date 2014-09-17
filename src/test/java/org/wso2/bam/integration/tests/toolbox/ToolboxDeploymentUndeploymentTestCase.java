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
//import org.wso2.bam.integration.tests.BAMTestServerManager;
import org.wso2.carbon.bam.toolbox.deployer.stub.BAMToolboxDepolyerServiceStub;
import org.wso2.carbon.integration.framework.LoginLogoutUtil;
import org.wso2.carbon.integration.framework.ClientConnectionUtil;
//import org.wso2.carbon.integration.framework.utils.FrameworkSettings;
//import org.apache.axis2.context.ConfigurationContext;
//import org.apache.axis2.context.ConfigurationContextFactory;

//import org.wso2.carbon.automation.core.utils.fileutils.FileManager;

import org.wso2.carbon.integration.framework.utils.CodeCoverageUtils;
import org.wso2.carbon.integration.framework.utils.ServerUtils;
import org.wso2.carbon.integration.framework.utils.TestUtil;
import org.wso2.carbon.integration.framework.utils.FrameworkSettings;
import org.wso2.carbon.automation.api.clients.registry.ResourceAdminServiceClient;
import org.wso2.carbon.registry.resource.stub.ResourceAdminServiceStub;

import java.io.File;
import java.io.IOException;


import static org.testng.Assert.assertTrue;


public class ToolboxDeploymentUndeploymentTestCase {
    private static final Log log = LogFactory.getLog(ToolboxDeploymentUndeploymentTestCase.class);

    private LoginLogoutUtil util = new LoginLogoutUtil();

    private static final String HIVE_SCRIPT_LOCATION = "/_system/config/repository/hive/scripts/";

    private static final String SERVER_URL = "https://localhost:9443/";
    private BAMToolboxDepolyerServiceStub toolboxStub;
    private static String TOOLBOX_URL = "http://dist.wso2.org/downloads/business-activity-monitor/" +
            "tool-boxes/KPI_Phone_Retail_Store.tbox";

    private String carbonHome = "";
    private String serviceStatToolBoxSource = "";
    private String serviceStatToolBoxDestination = "";
    private String webAppStatToolBoxSource = "";
    private String webAppStatToolBoxDestination = "";
    private static final String BAM_TBOX_DEPLOYMENT_DIR = "/repository/deployment/server/bam-toolbox/";
    private static final String TBOX_SAMPLES_DIR = "/samples/toolboxes/";
    private static final String REGISTRY_SERVICE = "/services/ResourceAdminService";

    private String carbonZip;
    private int portOffset;

    private ServerUtils serverUtils = new ServerUtils();
    private ResourceAdminServiceStub registryStub;


    private static final int RETRY_COUNT = 30;


    @BeforeSuite(timeOut = 180000)
    public void setupServerAndToolBoxes() throws Exception {
        log.info("************** unpack BAM ************");
        unpackBAM();

        log.info("************** copy toolbox to BAM ************");
        log.info("carbon home: " + carbonHome);
        serviceStatToolBoxSource = carbonHome + TBOX_SAMPLES_DIR + "Service_Statistics_Monitoring.tbox";
        log.info("serviceStatToolBox source: " + serviceStatToolBoxSource);
        serviceStatToolBoxDestination = carbonHome + BAM_TBOX_DEPLOYMENT_DIR;
        log.info("serviceStatToolBox dest dir: " + serviceStatToolBoxDestination);

        try {
            copyFileToDir(new File(serviceStatToolBoxSource), new File(serviceStatToolBoxDestination));
        } catch (IOException e) {
            e.printStackTrace();
        }

        log.info("*************** start server ************");
        startServer();

    }


    @BeforeClass(groups = {"wso2.bam"})
    public void init() throws Exception {
        log.info("************** before class ************");


        String loggedInSessionCookie = util.login();

        ConfigurationContext configContext = ConfigurationContextFactory.
                createConfigurationContextFromFileSystem(null);
        initializedRegistryStub(loggedInSessionCookie, configContext);


    }


    @Test(groups = {"wso2.bam"})
    public void readingHiveScrpits() throws Exception {
        log.info("************** test class ************");

//        Thread.sleep(10000000);

//        String EPR = "https://" + FrameworkSettings.HOST_NAME +
//                ":" + FrameworkSettings.HTTPS_PORT + REGISTRY_SERVICE;
//
//        ResourceAdminServiceClient resourceAdminServiceClient = new ResourceAdminServiceClient(EPR, "admin", "admin");

//        String serviceStatHiveScrpit = resourceAdminServiceClient
//                .getTextContent(HIVE_SCRIPT_LOCATION + "service_stats.hiveql");

        String serviceStatHiveScrpit = registryStub
                .getTextContent(HIVE_SCRIPT_LOCATION + "service_stats.hiveql");

        System.out.println("------ HIVE SCRIPT -----");
        System.out.println(serviceStatHiveScrpit);


    }

//    @Test(groups = {"wso2.bam"}, dependsOnMethods = "urlToolBoxDeployment")
//    public void undeployURlToolBox() throws Exception {
//        String toolBoxname = deployedToolBox.replaceAll(".tbox", "");
//               toolboxStub.undeployToolBox(new String[]{toolBoxname});
//
//               boolean unInstalled = false;
//
//               log.info("Un installing toolbox...");
//
//               int noOfTry = 1;
//
//               while (!unInstalled && noOfTry <= RETRY_COUNT) {
//                   Thread.sleep(1000);
//
//                   BAMToolboxDepolyerServiceStub.ToolBoxStatusDTO statusDTO = toolboxStub.getDeployedToolBoxes("1", "");
//                   String[] deployedTools = statusDTO.getDeployedTools();
//                   String[] undeployingTools = statusDTO.getToBeUndeployedTools();
//                   boolean isUninstalled = true;
//
//                   if (null != undeployingTools) {
//                       for (String aTool : undeployingTools) {
//                           if (aTool.equalsIgnoreCase(toolBoxname)) {
//                               isUninstalled = false;
//                               break;
//                           }
//                       }
//                   }
//
//                   if (null != deployedTools && isUninstalled) {
//                       for (String aTool : deployedTools) {
//                           if (aTool.equalsIgnoreCase(toolBoxname)) {
//                               isUninstalled = false;
//                               break;
//                           }
//                       }
//                   }
//                   unInstalled = isUninstalled;
//                   noOfTry++;
//               }
//
//        assertTrue(unInstalled, "Un installing url toolbox" + deployedToolBox + " is not successful");
//    }


    @AfterClass(groups = {"wso2.bam"})
    public void logout() throws Exception {
        log.info("************** after class ************");
        ClientConnectionUtil.waitForPort(9443);
        util.logout();
    }

    @AfterSuite(timeOut = 180000)
    public void stopBAMServer() throws Exception {
        log.info("************** stop server ************");
        stopServer();
    }


    protected void copyArtifacts(String carbonHome) throws IOException {
        // No artifacts need to be copied
    }

    private static void copyFileToDir(File source, File dest) throws IOException {
        File destinationFile = new File(dest.getPath() + "/" + source.getName());
        System.out.println(destinationFile);
        if (destinationFile.exists()) {
            destinationFile.delete();
        }
        FileUtils.copyFileToDirectory(source, dest);
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

    private void unpackBAM() throws Exception {
        try {
            carbonHome = unpackCarbonZip();
        } catch (IOException e) {
            e.printStackTrace();
        }

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

}
