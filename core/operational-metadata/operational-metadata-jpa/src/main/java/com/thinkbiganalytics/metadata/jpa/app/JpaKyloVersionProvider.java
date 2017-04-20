package com.thinkbiganalytics.metadata.jpa.app;

/*-
 * #%L
 * thinkbig-operational-metadata-jpa
 * %%
 * Copyright (C) 2017 ThinkBig Analytics
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import com.thinkbiganalytics.KyloVersionUtil;
import com.thinkbiganalytics.KyloVersion;
import com.thinkbiganalytics.metadata.api.app.KyloVersionProvider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.List;
import java.util.Properties;

import javax.annotation.PostConstruct;

/**
 * Provider for accessing and updating the Kylo version
 */
public class JpaKyloVersionProvider implements KyloVersionProvider {

    private static final Logger log = LoggerFactory.getLogger(JpaKyloVersionProvider.class);


    private KyloVersionRepository kyloVersionRepository;

    private String currentVersion;

    private String buildTimestamp;


    @Autowired
    public JpaKyloVersionProvider(KyloVersionRepository kyloVersionRepository) {
        this.kyloVersionRepository = kyloVersionRepository;
    }

    @Override
    public boolean isUpToDate() {
        KyloVersion currentVersion = getCurrentVersion();
        return currentVersion != null && currentVersion.equals(getLatestVersion());
    }

    @Override
    public KyloVersion getCurrentVersion() {

        List<JpaKyloVersion> versions = kyloVersionRepository.findAll();
        if (versions != null && !versions.isEmpty()) {
            return versions.get(0);
        }
        return null;
    }

    @Override
    public KyloVersion updateToLatestVersion() {
        if (getLatestVersion() != null) {
            KyloVersion currentVersion = getLatestVersion();
            KyloVersion existingVersion = getCurrentVersion();
            
            if (existingVersion == null) {
                kyloVersionRepository.save(new JpaKyloVersion(currentVersion.getMajorVersion(), currentVersion.getMinorVersion()));
                existingVersion = currentVersion;
            } else {
                if (!existingVersion.equals(currentVersion)) {
                    kyloVersionRepository.deleteAll();
                    JpaKyloVersion update = new JpaKyloVersion(currentVersion.getMajorVersion(), currentVersion.getMinorVersion());
                    kyloVersionRepository.save(update);
                    existingVersion.update(update);
                }
            }
            return existingVersion;
        }
        return null;
    }
    
    @Override
    public KyloVersion getLatestVersion() {
        return KyloVersionUtil.getLatestVersion();
    }




    @PostConstruct
    private void init() {
        getLatestVersion();
    }



}
