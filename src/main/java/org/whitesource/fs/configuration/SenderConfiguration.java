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

import com.fasterxml.jackson.annotation.JsonProperty;

import static org.whitesource.agent.ConfigPropertyKeys.*;
import static org.whitesource.agent.client.ClientConstants.CONNECTION_TIMEOUT_KEYWORD;
import static org.whitesource.agent.client.ClientConstants.SERVICE_URL_KEYWORD;

public class SenderConfiguration {

    private final boolean checkPolicies;
    private final String serviceUrl;
    private final String proxyHost;
    private final int connectionTimeOut;
    private final int proxyPort;
    private final String proxyUser;
    private final String proxyPassword;
    private final boolean forceCheckAllDependencies;
    private final boolean forceUpdate;
    private final String updateTypeValue;
    private final boolean enableImpactAnalysis;
    private final boolean ignoreCertificateCheck;
    private final int connectionRetries;
    private final int connectionRetriesIntervals;

    public SenderConfiguration(
            @JsonProperty(CHECK_POLICIES_PROPERTY_KEY) boolean checkPolicies,
            @JsonProperty(SERVICE_URL_KEYWORD) String serviceUrl,
            @JsonProperty(CONNECTION_TIMEOUT_KEYWORD) int connectionTimeOut,

            @JsonProperty(PROXY_HOST_PROPERTY_KEY) String proxyHost,
            @JsonProperty(PROXY_PORT_PROPERTY_KEY) int proxyPort,
            @JsonProperty(PROXY_USER_PROPERTY_KEY) String proxyUser,
            @JsonProperty(PROXY_PASS_PROPERTY_KEY) String proxyPassword,

            @JsonProperty(FORCE_CHECK_ALL_DEPENDENCIES) boolean forceCheckAllDependencies,
            @JsonProperty(FORCE_UPDATE) boolean forceUpdate,
            @JsonProperty(UPDATE_TYPE) String updateTypeValue,
            @JsonProperty(ENABLE_IMPACT_ANALYSIS) boolean enableImpactAnalysis,
            @JsonProperty(IGNORE_CERTIFICATE_CHECK) boolean ignoreCertificateCheck,
            @JsonProperty(CONNECTION_RETRIES) int connectionRetries,
            @JsonProperty(CONNECTION_RETRIES_INTERVALS) int connectionRetriesIntervals){
        this.checkPolicies = checkPolicies;
        this.serviceUrl = serviceUrl;
        this.proxyHost = proxyHost;
        this.connectionTimeOut = connectionTimeOut;
        this.proxyPort = proxyPort;
        this.proxyUser = proxyUser;
        this.proxyPassword = proxyPassword;
        this.forceCheckAllDependencies = forceCheckAllDependencies;
        this.forceUpdate = forceUpdate;
        this.updateTypeValue = updateTypeValue;
        this.enableImpactAnalysis = enableImpactAnalysis;
        this.ignoreCertificateCheck = ignoreCertificateCheck;
        this.connectionRetries = connectionRetries;
        this.connectionRetriesIntervals = connectionRetriesIntervals;
    }

    @JsonProperty(SERVICE_URL_KEYWORD)
    public String getServiceUrl() {
        return serviceUrl;
    }

    @JsonProperty(UPDATE_TYPE)
    public String getUpdateTypeValue() {
        return updateTypeValue;
    }

    @JsonProperty(CHECK_POLICIES_PROPERTY_KEY)
    public boolean isCheckPolicies() {
        return checkPolicies;
    }

    @JsonProperty(PROXY_HOST_PROPERTY_KEY)
    public String getProxyHost() {
        return proxyHost;
    }

    @JsonProperty(CONNECTION_TIMEOUT_KEYWORD)
    public int getConnectionTimeOut() {
        return connectionTimeOut;
    }

    @JsonProperty(CONNECTION_RETRIES)
    public int getConnectionRetries(){
        return connectionRetries;
    }

    @JsonProperty(CONNECTION_RETRIES_INTERVALS)
    public int getConnectionRetriesIntervals(){
        return connectionRetriesIntervals;
    }

    @JsonProperty(PROXY_PORT_PROPERTY_KEY)
    public int getProxyPort() {
        return proxyPort;
    }

    @JsonProperty(PROXY_USER_PROPERTY_KEY)
    public String getProxyUser() {
        return proxyUser;
    }

    @JsonProperty(PROXY_PASS_PROPERTY_KEY)
    public String getProxyPassword() {
        return proxyPassword;
    }

    @JsonProperty(FORCE_CHECK_ALL_DEPENDENCIES)
    public boolean isForceCheckAllDependencies() {
        return forceCheckAllDependencies;
    }

    @JsonProperty(FORCE_UPDATE)
    public boolean isForceUpdate() {
        return forceUpdate;
    }

    @JsonProperty(ENABLE_IMPACT_ANALYSIS)
    public boolean isEnableImpactAnalysis() {
        return enableImpactAnalysis;
    }

    @JsonProperty(IGNORE_CERTIFICATE_CHECK)
    public boolean isIgnoreCertificateCheck() {
        return ignoreCertificateCheck;
    }

    @Override
    public String toString() {
        return  ", serviceUrl='" + serviceUrl + "'\n" +
                ", checkPolicies=" + checkPolicies + '\n' +
                ", forceCheckAllDependencies=" + forceCheckAllDependencies + '\n' +
                ", forceUpdate=" + forceUpdate + '\n' +
                ", updateTypeValue='" + updateTypeValue + "'";
//                ", enableImpactAnalysis=" + enableImpactAnalysis;
    }
}
