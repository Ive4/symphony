/*
 * Copyright (c) 2012-2016, b3log.org & hacpai.com
 *
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
 */
package org.b3log.symphony.processor;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.b3log.latke.Keys;
import org.b3log.latke.model.User;
import org.b3log.latke.servlet.HTTPRequestContext;
import org.b3log.latke.servlet.HTTPRequestMethod;
import org.b3log.latke.servlet.advice.RequestProcessAdviceException;
import org.b3log.latke.servlet.annotation.After;
import org.b3log.latke.servlet.annotation.Before;
import org.b3log.latke.servlet.annotation.RequestProcessing;
import org.b3log.latke.servlet.annotation.RequestProcessor;
import org.b3log.latke.servlet.renderer.freemarker.AbstractFreeMarkerRenderer;
import org.b3log.latke.util.Requests;
import org.b3log.symphony.model.Common;
import org.b3log.symphony.model.Link;
import org.b3log.symphony.model.Option;
import org.b3log.symphony.model.Tag;
import org.b3log.symphony.processor.advice.LoginCheck;
import org.b3log.symphony.processor.advice.stopwatch.StopwatchEndAdvice;
import org.b3log.symphony.processor.advice.stopwatch.StopwatchStartAdvice;
import org.b3log.symphony.service.LinkForgeMgmtService;
import org.b3log.symphony.service.LinkForgeQueryService;
import org.b3log.symphony.service.OptionQueryService;
import org.b3log.symphony.util.Filler;
import org.json.JSONObject;

/**
 * Link forge processor.
 *
 * <ul>
 * <li>Shows link forge (/link-forge), GET</li>
 * <li>Submits a link into forge (/forge/link), POST</li>
 * </ul>
 *
 * @author <a href="http://88250.b3log.org">Liang Ding</a>
 * @version 1.0.0.2, Sep 11, 2016
 * @since 1.6.0
 */
@RequestProcessor
public class LinkForgeProcessor {

    /**
     * Forge thread.
     */
    private static final ExecutorService FORGE_EXECUTOR_SERVICE = Executors.newFixedThreadPool(1);

    /**
     * Link forget management service.
     */
    @Inject
    private LinkForgeMgmtService linkForgeMgmtService;

    /**
     * Link forge query service.
     */
    @Inject
    private LinkForgeQueryService linkForgeQueryService;

    /**
     * Option query service.
     */
    @Inject
    private OptionQueryService optionQueryService;

    /**
     * Filler.
     */
    @Inject
    private Filler filler;

    /**
     * Submits a link into forge.
     *
     * @param context the specified context
     * @throws Exception exception
     */
    @RequestProcessing(value = "/forge/link", method = HTTPRequestMethod.POST)
    @Before(adviceClass = {StopwatchStartAdvice.class, LoginCheck.class})
    @After(adviceClass = StopwatchEndAdvice.class)
    public void forgeLink(final HTTPRequestContext context) throws Exception {
        context.renderJSON(true);

        JSONObject requestJSONObject;
        try {
            requestJSONObject = Requests.parseRequestJSONObject(context.getRequest(), context.getResponse());
        } catch (final Exception e) {
            throw new RequestProcessAdviceException(new JSONObject().put(Keys.MSG, e.getMessage()));
        }

        final JSONObject user = (JSONObject) context.getRequest().getAttribute(User.USER);
        final String userId = user.optString(Keys.OBJECT_ID);

        final String url = requestJSONObject.optString(Common.URL);

        FORGE_EXECUTOR_SERVICE.submit(new Runnable() {
            @Override
            public void run() {
                linkForgeMgmtService.forge(url, userId);
            }
        });
    }

    /**
     * Shows link forge.
     *
     * @param context the specified context
     * @param request the specified request
     * @param response the specified response
     * @throws Exception exception
     */
    @RequestProcessing(value = "/forge/link", method = HTTPRequestMethod.GET)
    @Before(adviceClass = {StopwatchStartAdvice.class})
    @After(adviceClass = StopwatchEndAdvice.class)
    public void showLinkForge(final HTTPRequestContext context, final HttpServletRequest request, final HttpServletResponse response)
            throws Exception {
        final AbstractFreeMarkerRenderer renderer = new SkinRenderer();
        context.setRenderer(renderer);

        renderer.setTemplateName("link-forge.ftl");
        final Map<String, Object> dataModel = renderer.getDataModel();

        final List<JSONObject> tags = linkForgeQueryService.getForgedLinks();
        dataModel.put(Tag.TAGS, (Object) tags);

        final JSONObject statistic = optionQueryService.getStatistic();
        final int tagCnt = statistic.optInt(Option.ID_C_STATISTIC_TAG_COUNT);
        dataModel.put(Tag.TAG_T_COUNT, tagCnt);

        final int linkCnt = statistic.optInt(Option.ID_C_STATISTIC_LINK_COUNT);
        dataModel.put(Link.LINK_T_COUNT, linkCnt);

        filler.fillDomainNav(dataModel);
        filler.fillHeaderAndFooter(request, response, dataModel);
    }
}
