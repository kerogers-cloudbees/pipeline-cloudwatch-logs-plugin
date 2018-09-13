/*
 * The MIT License
 *
 * Copyright 2018 CloudBees, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package io.jenkins.plugins.pipeline_log_fluentd_cloudwatch;

import java.io.IOException;

import javax.annotation.Nonnull;

import org.apache.commons.lang.StringUtils;
import org.jenkinsci.Symbol;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.interceptor.RequirePOST;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.services.logs.AWSLogs;
import com.amazonaws.services.logs.AWSLogsClientBuilder;
import com.amazonaws.services.logs.model.FilterLogEventsRequest;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.Util;
import hudson.model.Failure;
import hudson.util.FormValidation;
import io.jenkins.plugins.aws.global_configuration.AbstractAwsGlobalConfiguration;
import io.jenkins.plugins.aws.global_configuration.CredentialsAwsGlobalConfiguration;
import jenkins.model.Jenkins;

/**
 * Store the AWS configuration to save it on a separate file
 */
@Symbol("cloudWatchLogs")
@Extension
public class CloudWatchAwsGlobalConfiguration extends AbstractAwsGlobalConfiguration {

    /**
     * Name of the CloudWatch log group.
     */
    private String logGroupName;

    public CloudWatchAwsGlobalConfiguration() {
        load();
    }

    /**
     * Testing only
     */
    CloudWatchAwsGlobalConfiguration(boolean test) {
    }

    public String getLogGroupName() {
        return logGroupName;
    }

    @DataBoundSetter
    public void setLogGroupName(String logGroupName) {
        this.logGroupName = logGroupName;
        checkValue(doCheckLogGroupName(logGroupName));
        save();
    }

    private void checkValue(@NonNull FormValidation formValidation) {
        if (formValidation.kind == FormValidation.Kind.ERROR) {
            throw new Failure(formValidation.getMessage());
        }
    }

    @Nonnull
    @Override
    public String getDisplayName() {
        return "Amazon CloudWatch Logs settings";
    }

    public AWSLogsClientBuilder getAWSLogsClientBuilder() throws IOException {
        return getAWSLogsClientBuilder(CredentialsAwsGlobalConfiguration.get().getRegion(),
                CredentialsAwsGlobalConfiguration.get().getCredentialsId());
    }

    /**
     *
     * @return an AWSLogsClientBuilder using the passed region
     * @throws IOException
     */
    @Restricted(NoExternalUse.class)
    static AWSLogsClientBuilder getAWSLogsClientBuilder(String region, String credentialsId) throws IOException {
        AWSLogsClientBuilder builder = AWSLogsClientBuilder.standard();
        if (region != null) {
            builder = builder.withRegion(region);
        }
        if (credentialsId != null) {
            AWSStaticCredentialsProvider credentialsProvider = new AWSStaticCredentialsProvider(
                    CredentialsAwsGlobalConfiguration.get().sessionCredentials(builder, region, credentialsId));
            return builder.withCredentials(credentialsProvider);
        } else {
            return builder.withCredentials(new DefaultAWSCredentialsProviderChain());
        }
    }

    public FormValidation doCheckLogGroupName(@QueryParameter String logGroupName) {
        FormValidation ret = FormValidation.ok();
        if (StringUtils.isBlank(logGroupName)) {
            ret = FormValidation.warning("The log group name cannot be empty");
        }
        return ret;
    }

    @RequirePOST
    public FormValidation doValidate(@QueryParameter String logGroupName, @QueryParameter String region,
            @QueryParameter String credentialsId) throws IOException {
        Jenkins.get().checkPermission(Jenkins.ADMINISTER);
        return validate(logGroupName, Util.fixEmptyAndTrim(region), Util.fixEmptyAndTrim(credentialsId));
    }

    @Restricted(NoExternalUse.class)
    FormValidation validate(String logGroupName, String region, String credentialsId) throws IOException {
        FormValidation ret = FormValidation.ok("success");
        AWSLogs client;
        try {
            AWSLogsClientBuilder builder = getAWSLogsClientBuilder(region, credentialsId);
            client = builder.build();
        } catch (Throwable t) {
            String msg = processExceptionMessage(t);
            ret = FormValidation.error("Unable to validate credentials: " + StringUtils.abbreviate(msg, 200));
            return ret;
        }

        try {
            filter(client, logGroupName);
        } catch (Throwable t) {
            String msg = processExceptionMessage(t);
            ret = FormValidation.error(StringUtils.abbreviate(msg, 200));
        }
        return ret;
    }

    @Restricted(NoExternalUse.class)
    protected void filter(AWSLogs client, String logGroupName) {
        FilterLogEventsRequest request = new FilterLogEventsRequest();
        request.setLogGroupName(logGroupName);
        // TODO this returns a ton of data, when all we care about is that the request does not fail; filter it down to just a few results
        client.filterLogEvents(request);
    }

}
