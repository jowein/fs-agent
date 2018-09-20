/**
 * Copyright (C) 2014 WhiteSource Ltd.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.whitesource.fs.configuration;

import org.apache.commons.lang.StringUtils;
import org.whitesource.agent.Constants;

import java.util.ArrayList;
import java.util.List;

import static org.whitesource.agent.ConfigPropertyKeys.*;

/**
 * Author: eugen.horovitz
 */
public class ConfigurationValidation {

    /* --- Static members --- */

    private static final int MAX_EXTRACTION_DEPTH = 7;

    public List<String> getConfigurationErrors(boolean projectPerFolder, String configProjectToken, String configProjectName, String configApiToken, String configFilePath,
                                               int archiveDepth, String[] includes, String[] projectPerFolderIncludes, String[] pythonIncludes) {
        List<String> errors = new ArrayList<>();
        String[] requirements = pythonIncludes[0].split(Constants.WHITESPACE);
        if (StringUtils.isBlank(configApiToken)) {
            String error = "Could not retrieve " + ORG_TOKEN_PROPERTY_KEY + " property from " + configFilePath;
            errors.add(error);
        }
        boolean noProjectToken = StringUtils.isBlank(configProjectToken);
        boolean noProjectName = StringUtils.isBlank(configProjectName);

        if (noProjectToken && noProjectName && !projectPerFolder) {
            String error = "Could not retrieve properties " + PROJECT_NAME_PROPERTY_KEY + " and " + PROJECT_TOKEN_PROPERTY_KEY + " from " + configFilePath;
            errors.add(error);
        } else if (!noProjectToken && !noProjectName) {
            String error = "Please choose just one of either " + PROJECT_NAME_PROPERTY_KEY + " or " + PROJECT_TOKEN_PROPERTY_KEY + " (and not both)";
            errors.add(error);
        }
        if (archiveDepth < 0 || archiveDepth > MAX_EXTRACTION_DEPTH) {
            errors.add("Error: archiveExtractionDepth value should be greater than 0 and less than " + MAX_EXTRACTION_DEPTH);
        }
        if (includes.length < 1 || StringUtils.isBlank(includes[0])) {
            errors.add("Error: includes parameter must have at list one scanning pattern");
        }
        if (projectPerFolder && projectPerFolderIncludes == null) {
            errors.add("projectPerFolderIncludes parameter is empty, specify folders to include or mark as comment to scan all folders");
        }

        if (requirements.length > 0) {
            for (String requirement : requirements) {
                if (!requirement.endsWith(Constants.TXT_EXTENSION)) {
                    String error = "Invalid file name: " + requirement + Constants.WHITESPACE + "in property" + PYTHON_REQUIREMENTS_FILE_INCLUDES + "from " + configFilePath;
                    errors.add(error);
                }
            }
        }

        return errors;
    }
}