/**
 * JBoss, Home of Professional Open Source.
 * Copyright 2014 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.pnc.core.builder;

import org.jboss.logging.Logger;
import org.jboss.pnc.model.Artifact;
import org.jboss.pnc.model.BuildRecord;
import org.jboss.pnc.model.BuildStatus;
import org.jboss.pnc.spi.BuildResult;
import org.jboss.pnc.spi.builddriver.BuildDriverResult;
import org.jboss.pnc.spi.datastore.Datastore;
import org.jboss.pnc.spi.datastore.DatastoreException;
import org.jboss.pnc.spi.repositorymanager.RepositoryManagerResult;

import javax.inject.Inject;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

/**
 * Created by <a href="mailto:matejonnet@gmail.com">Matej Lazar</a> on 2014-12-15.
 */
public class DatastoreAdapter {

    private Datastore datastore;

    private static final Logger log = Logger.getLogger(DatastoreAdapter.class);

    @Inject
    public DatastoreAdapter(Datastore datastore) {
        this.datastore = datastore;
    }

    public void storeResult(BuildTask buildTask, BuildResult buildResult) throws DatastoreException {
        try {
            BuildDriverResult buildDriverResult = buildResult.getBuildDriverResult();
            RepositoryManagerResult repositoryManagerResult = buildResult.getRepositoryManagerResult();

            BuildRecord buildRecord = new BuildRecord();
            // Build driver results
            buildRecord.setBuildLog(buildDriverResult.getBuildLog());
            buildRecord.setStatus(buildDriverResult.getBuildDriverStatus().toBuildStatus());
            // Repository manager results, it's null in case of failed build
            if (repositoryManagerResult != null) {
                linkArtifactsWithBuildRecord(repositoryManagerResult.getBuiltArtifacts(), buildRecord);
                buildRecord.setBuiltArtifacts(repositoryManagerResult.getBuiltArtifacts());
                linkArtifactsWithBuildRecord(repositoryManagerResult.getDependencies(), buildRecord);
                buildRecord.setDependencies(repositoryManagerResult.getDependencies());
            }
            setAuditDataToBuildRecord(buildRecord, buildTask);

            log.debugf("Storing results of %s to datastore.", buildTask.getBuildConfiguration().getName());
            datastore.storeCompletedBuild(buildRecord);
        } catch (Exception e) {
            throw new DatastoreException("Error storing the result to datastore.", e);
        }
    }

    public void storeResult(BuildTask buildTask, Throwable e) throws DatastoreException {
        BuildRecord buildRecord = new BuildRecord();
        StringWriter stackTraceWriter = new StringWriter();
        PrintWriter stackTracePrinter = new PrintWriter(stackTraceWriter);
        e.printStackTrace(stackTracePrinter);
        buildRecord.setStatus(BuildStatus.SYSTEM_ERROR); 
        
        String errorMessage = "Last build status: " + buildTask.getStatus().toString() + "\n";
        errorMessage += "Caught exception: " + stackTraceWriter.toString();
        buildRecord.setBuildLog(errorMessage);
        setAuditDataToBuildRecord(buildRecord, buildTask);

        log.debugf("Storing ERROR result of %s to datastore. Error: %s", buildTask.getBuildConfiguration().getName() + "\n\n\n Exception: " + errorMessage, e);
        datastore.storeCompletedBuild(buildRecord);
    }

    private void setAuditDataToBuildRecord(BuildRecord buildRecord, BuildTask buildTask) {
        buildRecord.setBuildConfigurationAudited(buildTask.getBuildConfiguration().getBuildConfigurationAudited());
        buildRecord.setLatestBuildConfiguration(buildTask.getBuildConfiguration());
        buildRecord.setUser(buildTask.getUser());
        buildRecord.setStartTime(Timestamp.from(Instant.ofEpochMilli(buildTask.getStartTime())));
        buildRecord.setEndTime(Timestamp.from(Instant.now()));
    }

    private void linkArtifactsWithBuildRecord(List<Artifact> artifacts, BuildRecord buildRecord) {
        artifacts.forEach(a -> a.setBuildRecord(buildRecord));
    }

    public boolean isBuildConfigurationBuilt() {
        return false; // TODO
    }
}
