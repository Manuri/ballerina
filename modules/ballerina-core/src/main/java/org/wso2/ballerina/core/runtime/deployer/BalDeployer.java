/*
 * Copyright (c) 2016, WSO2 Inc. (http://wso2.com) All Rights Reserved.
 * <p>
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.ballerina.core.runtime.deployer;

import org.antlr.v4.runtime.ANTLRInputStream;
import org.antlr.v4.runtime.CommonTokenStream;
import org.osgi.framework.BundleContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wso2.ballerina.core.exception.BallerinaException;
import org.wso2.ballerina.core.linker.BLangLinker;
import org.wso2.ballerina.core.model.Application;
import org.wso2.ballerina.core.model.BallerinaFile;
import org.wso2.ballerina.core.model.BallerinaFunction;
import org.wso2.ballerina.core.model.Package;
import org.wso2.ballerina.core.model.builder.BLangModelBuilder;
import org.wso2.ballerina.core.parser.BallerinaLexer;
import org.wso2.ballerina.core.parser.BallerinaParser;
import org.wso2.ballerina.core.parser.BallerinaParserErrorStrategy;
import org.wso2.ballerina.core.parser.antlr4.BLangAntlr4Listener;
import org.wso2.ballerina.core.runtime.BalProgramExecutor;
import org.wso2.ballerina.core.runtime.Constants;
import org.wso2.ballerina.core.runtime.internal.GlobalScopeHolder;
import org.wso2.ballerina.core.runtime.internal.RuntimeUtils;
import org.wso2.ballerina.core.runtime.internal.ServiceContextHolder;
import org.wso2.ballerina.core.runtime.registry.ApplicationRegistry;
import org.wso2.ballerina.core.semantics.SemanticAnalyzer;
import org.wso2.carbon.deployment.engine.Artifact;
import org.wso2.carbon.deployment.engine.ArtifactType;
import org.wso2.carbon.deployment.engine.Deployer;
import org.wso2.carbon.deployment.engine.exception.CarbonDeploymentException;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;

import static org.wso2.ballerina.core.runtime.Constants.SYSTEM_PROP_BAL_FILE;

/**
 * {@code BalDeployer} is responsible for all ballerina file deployment tasks
 *
 * @since 1.0.0
 */
@Component(
        name = "org.wso2.ballerina.core.runtime.deployer.BalDeployer",
        immediate = true,
        service = Deployer.class)
public class BalDeployer implements Deployer {

    private static final Logger log = LoggerFactory.getLogger(BalDeployer.class);

    private static final String BAL_FILES_DIRECTORY = "ballerina-files";
    private static final String FILE_EXTENSION = ".bal";
    private ArtifactType artifactType = new ArtifactType<>("bal");
    private URL directoryLocation;

    @Activate
    protected void activate(BundleContext bundleContext) {
        String balFile = System.getProperty(SYSTEM_PROP_BAL_FILE);
        if (balFile != null) {
            ServiceContextHolder.getInstance().setRuntimeMode(Constants.RuntimeMode.RUN_FILE);
        }
    }

    @Override
    public void init() {
        try {
            directoryLocation = new URL("file:" + BAL_FILES_DIRECTORY);
        } catch (MalformedURLException e) {
            log.error("Error while initializing directoryLocation" + directoryLocation.getPath(), e);
        }
    }

    @Override
    public Object deploy(Artifact artifact) throws CarbonDeploymentException {
        // Deploy only when server mode
        if (ServiceContextHolder.getInstance().getRuntimeMode() == Constants.RuntimeMode.SERVER) {
            deployBalFile(artifact.getFile());
        }
        return artifact.getFile().getName();
    }

    @Override
    public void undeploy(Object key) throws CarbonDeploymentException {
        if (ServiceContextHolder.getInstance().getRuntimeMode() == Constants.RuntimeMode.SERVER) {
            undeployBalFile((String) key);
        }
    }

    @Override
    public Object update(Artifact artifact) throws CarbonDeploymentException {
        log.info("Updating " + artifact.getName() + "...");
        undeployBalFile(artifact.getName());
        deployBalFile(artifact.getFile());
        return artifact.getName();
    }

    @Override
    public URL getLocation() {
        return directoryLocation;
    }

    @Override
    public ArtifactType getArtifactType() {
        return artifactType;
    }

    public static void deployBalFile(File file) {
        InputStream inputStream = null;
        boolean successful = true;
        try {
            inputStream = new FileInputStream(file);

            if (file.getName().endsWith(FILE_EXTENSION)) {
                ANTLRInputStream antlrInputStream = new ANTLRInputStream(inputStream);
                
                // Setting the name of the source file being parsed, to the ANTLR input stream.
                // This is required by the parser-error strategy.
                antlrInputStream.name = file.getName();
                
                BallerinaLexer ballerinaLexer = new BallerinaLexer(antlrInputStream);
                CommonTokenStream ballerinaToken = new CommonTokenStream(ballerinaLexer);

                BallerinaParser ballerinaParser = new BallerinaParser(ballerinaToken);
                ballerinaParser.setErrorHandler(new BallerinaParserErrorStrategy());

                BLangModelBuilder bLangModelBuilder = new BLangModelBuilder();
                BLangAntlr4Listener ballerinaBaseListener = new BLangAntlr4Listener(bLangModelBuilder);
                ballerinaParser.addParseListener(ballerinaBaseListener);
                ballerinaParser.compilationUnit();
                BallerinaFile balFile = bLangModelBuilder.build();

                SemanticAnalyzer semanticAnalyzer = new SemanticAnalyzer(balFile);
                balFile.accept(semanticAnalyzer);

                // Link
                BLangLinker bLangLinker = new BLangLinker(balFile);
                bLangLinker.link(GlobalScopeHolder.getInstance().getScope());

                if (Constants.RuntimeMode.RUN_FILE == ServiceContextHolder.getInstance().getRuntimeMode()) {
                    BallerinaFunction function =
                            (BallerinaFunction) balFile.getFunctions().get(Constants.MAIN_FUNCTION_NAME);
                    if (function != null) {
                        if (balFile.getServices().size() == 0) {
                            if (log.isDebugEnabled()) {
                                log.debug("No Service is found in Bal file. Only Main function will be executed," +
                                        " then exit.");
                            }
                            // Run main function
                            try {
                                BalProgramExecutor.execute(function);
                            } finally {
                                RuntimeUtils.shutdownRuntime();
                            }
                            return;
                        } else {
                            if (log.isDebugEnabled()) {
                                log.debug("Bal File contains Services and Main Functions.");
                            }
                            // Run main function
                            BalProgramExecutor.execute(function);
                        }
                    } else {
                        if (balFile.getServices().size() == 0) {
                            log.warn("Unable to find Main function or Any Ballerina Service. System Exits. Bye.");
                            RuntimeUtils.shutdownRuntime();
                            return;
                        }
                    }
                }

                // Get the existing application associated with this ballerina config
                Application app = ApplicationRegistry.getInstance().getApplication(file.getName());
                if (app == null) {
                    // Create a new application with ballerina file name, if there is no app currently exists.
                    app = new Application(file.getName());
                    ApplicationRegistry.getInstance().registerApplication(app);
                }
                
                Package aPackage = app.getPackage(file.getName());
                if (aPackage == null) {
                    // check if package name is null
                    if (balFile.getPackageName() != null) {
                        aPackage = new Package(balFile.getPackageName());
                    } else {
                        aPackage = new Package("default");
                    }
                    app.addPackage(aPackage);
                }
                aPackage.addFiles(balFile);
                ApplicationRegistry.getInstance().updatePackage(aPackage);

                log.info("Deployed ballerina file : " + file.getName());
            }
        } catch (IOException e) {
            log.error("Error while creating Ballerina object model from file : " + file.getName(), e.getMessage());
            successful = false;
        } catch (BallerinaException e) {
            log.error("Failed to deploy " + file.getName() + ": " + e.getMessage(), e.getMessage());
            successful = false;
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException ignore) {
                }
            }
            if (!successful && Constants.RuntimeMode.RUN_FILE == ServiceContextHolder.getInstance().getRuntimeMode()) {
                log.error("Execution is not successful. System exits. Bye.");
                RuntimeUtils.shutdownRuntime();
            }
        }
    }

    /**
     * Undeploy a service registered through a ballerina file.
     * 
     * @param fileName  Name of the ballerina file
     */
    private void undeployBalFile(String fileName) {
        Application app = ApplicationRegistry.getInstance().getApplication(fileName);
        if (app == null) {
            log.warn("Could not find service to undeploy: " + fileName + ".");
            return;
        }
        ApplicationRegistry.getInstance().unregisterApplication(app);
        log.info("Undeployed ballerina file : " + fileName);
    }

}
