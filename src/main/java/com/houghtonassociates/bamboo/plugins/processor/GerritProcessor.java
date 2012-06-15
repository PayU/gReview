/**
 * Copyright 2012 Houghton Associates
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
package com.houghtonassociates.bamboo.plugins.processor;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.log4j.Logger;

import com.atlassian.bamboo.build.CustomBuildProcessor;
import com.atlassian.bamboo.build.logger.BuildLogger;
import com.atlassian.bamboo.builder.BuildState;
import com.atlassian.bamboo.plan.Plan;
import com.atlassian.bamboo.plan.PlanKeys;
import com.atlassian.bamboo.repository.RepositoryDefinition;
import com.atlassian.bamboo.utils.error.ErrorCollection;
import com.atlassian.bamboo.utils.i18n.I18nBeanFactory;
import com.atlassian.bamboo.utils.i18n.TextProviderAdapter;
import com.atlassian.bamboo.v2.build.BaseConfigurableBuildPlugin;
import com.atlassian.bamboo.v2.build.BuildContext;
import com.atlassian.bamboo.v2.build.CurrentBuildResult;
import com.atlassian.bamboo.ww2.actions.build.admin.create.BuildConfiguration;
import com.atlassian.spring.container.LazyComponentReference;
import com.houghtonassociates.bamboo.plugins.GerritRepositoryAdapter;
import com.houghtonassociates.bamboo.plugins.dao.GerritChangeVO;
import com.houghtonassociates.bamboo.plugins.dao.GerritService;
import com.opensymphony.xwork.TextProvider;

/**
 * @author Jason Huntley
 * 
 */
public class GerritProcessor extends BaseConfigurableBuildPlugin implements
    CustomBuildProcessor {

    private final Logger logger = Logger.getLogger(GerritProcessor.class);
    private TextProvider textProvider = null;
    private static final LazyComponentReference<I18nBeanFactory> i18nBeanFactoryReference =
        new LazyComponentReference<I18nBeanFactory>("i18nBeanFactory");
    private Map<String, String> customConfiguration = null;
    private static final String GERRIT_RUN = "custom.gerrit.run";

    @Override
    public void init(BuildContext buildContext) {
        // TODO Auto-generated method stub
        super.init(buildContext);

        I18nBeanFactory i18nBeanFactory = i18nBeanFactoryReference.get();

        this.textProvider =
            new TextProviderAdapter(i18nBeanFactory.getI18nBean(Locale
                .getDefault()));

        this.customConfiguration =
            buildContext.getBuildDefinition().getCustomConfiguration();
    }

    @Override
    public void prepareConfigObject(BuildConfiguration buildConfiguration) {
        // TODO Auto-generated method stub
        super.prepareConfigObject(buildConfiguration);
    }

    @Override
    public ErrorCollection validate(BuildConfiguration buildConfiguration) {
        // TODO Auto-generated method stub
        return super.validate(buildConfiguration);
    }

    @Override
    protected void
                    populateContextForView(Map<String, Object> context,
                                           Plan plan) {
        // TODO Auto-generated method stub
        super.populateContextForView(context, plan);
    }

    @Override
    protected void
                    populateContextForEdit(Map<String, Object> context,
                                           BuildConfiguration buildConfiguration,
                                           Plan plan) {
        super.populateContextForEdit(context, buildConfiguration, plan);

    }

    private String buildStatusString(CurrentBuildResult results) {
        List<String> errors = results.getBuildErrors();

        if (!results.getBuildState().equals(BuildState.SUCCESS)) {
            return textProvider.getText(
                "processor.gerrit.messages.build.custom",
                Arrays.asList(errors.get(2)));
        }

        return textProvider.getText("processor.gerrit.messages.build.sucess");
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.atlassian.bamboo.v2.build.task.BuildTask#call()
     */
    @Override
    public BuildContext call() throws InterruptedException, Exception {
        final String buildPlanKey = buildContext.getPlanKey();
        final CurrentBuildResult results = buildContext.getBuildResult();
        final Boolean runVerification =
            Boolean.parseBoolean(customConfiguration.get(GERRIT_RUN));

        if (runVerification) {
            final List<RepositoryDefinition> repositories =
                buildContext.getRepositoryDefinitions();

            for (RepositoryDefinition rd : repositories) {
                if (rd.getRepository() instanceof GerritRepositoryAdapter) {
                    final GerritRepositoryAdapter gra =
                        (GerritRepositoryAdapter) rd.getRepository();
                    final BuildLogger buildLogger =
                        gra.getBuildLoggerManager().getBuildLogger(
                            PlanKeys.getPlanKey(buildPlanKey));
                    final String revision =
                        buildContext.getBuildChanges().getVcsRevisionKey(
                            rd.getId());
                    final GerritService service =
                        new GerritService(gra.getHostname(),
                            gra.getGerritAuthentication());
                    final GerritChangeVO change =
                        service.getChangeByRevision(revision);

                    if ((results.getBuildReturnCode() == 0)
                        && results.getBuildState().equals(BuildState.SUCCESS)) {
                        if (service.verifyChange(true, change.getNumber(),
                            change.getCurrentPatchSet().getNumber(),
                            buildStatusString(results)))
                            buildLogger
                                .addBuildLogEntry(textProvider
                                    .getText("processor.gerrit.messages.build.verified.pos"));
                        else {
                            buildLogger
                                .addBuildLogEntry(textProvider
                                    .getText(
                                        "processor.gerrit.messages.build.verified.failed",
                                        Arrays.asList(change.getId())));
                            logger
                                .error(textProvider
                                    .getText(
                                        "processor.gerrit.messages.build.verified.failed",
                                        Arrays.asList(change.getId())));
                        }
                    } else if (service.verifyChange(false, change.getNumber(),
                        change.getCurrentPatchSet().getNumber(),
                        buildStatusString(results))) {
                        buildLogger
                            .addBuildLogEntry(textProvider
                                .getText("processor.gerrit.messages.build.verified.pos"));
                    } else {
                        buildLogger.addBuildLogEntry(textProvider.getText(
                            "processor.gerrit.messages.build.verified.failed",
                            Arrays.asList(change.getId())));
                        logger.error(textProvider.getText(
                            "processor.gerrit.messages.build.verified.failed",
                            Arrays.asList(change.getId())));
                    }
                }
            }
        }

        return buildContext;
    }

}