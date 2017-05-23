/* Copyright 2004-2005 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.grails.webflow.mvc.servlet

import grails.core.GrailsApplication
import grails.web.mapping.UrlMapping
import grails.web.mapping.UrlMappingInfo
import grails.web.mapping.UrlMappingsHolder
import org.grails.web.servlet.mvc.GrailsWebRequest
import org.springframework.beans.factory.InitializingBean

import javax.servlet.http.HttpServletRequest;

import grails.core.GrailsControllerClass;
import org.grails.web.servlet.DefaultGrailsApplicationAttributes;
import org.grails.web.mapping.mvc.UrlMappingsHandlerMapping;
import org.springframework.web.servlet.HandlerExecutionChain;
import org.springframework.webflow.mvc.servlet.AbstractFlowHandler

import java.util.concurrent.ConcurrentHashMap

/**
 * A HandlerMapping implementation that maps Grails controller classes onto flows.
 *
 * @author Graeme Rocher
 * @since 1.2
 */

public class GrailsFlowHandlerMapping extends UrlMappingsHandlerMapping implements InitializingBean {
    private GrailsApplication grailsApplication;
    private static final String FLOW_SUFFIX = "Flow";
    private Map<String,Set<String>> flowNamesByController = new ConcurrentHashMap<>();
    private Map<String,GrailsControllerClass> controllerClassMap = new ConcurrentHashMap<>();

    GrailsFlowHandlerMapping(UrlMappingsHolder urlMappingsHolder) {
        super(urlMappingsHolder)
    }

    protected Object getHandlerForControllerClass(GrailsControllerClass controllerClass, HttpServletRequest request) {
        String actionName = (String) request.getAttribute(DefaultGrailsApplicationAttributes.ACTION_NAME_ATTRIBUTE);
        if(controllerClass != null && actionName == null) {
            if(controllerClass.defaultAction != null && controllerClass.defaultAction.endsWith(FLOW_SUFFIX)) {
                actionName = controllerClass.defaultAction.substring(0, controllerClass.defaultAction.length() - FLOW_SUFFIX.length())
                request.setAttribute(DefaultGrailsApplicationAttributes.ACTION_NAME_ATTRIBUTE, actionName)
            }
        }
        if (controllerClass != null && actionName != null) {
            if (isFlowAction(controllerClass, actionName)) {
                final String flowid = controllerClass.getLogicalPropertyName() + "/" + actionName;
                return new AbstractFlowHandler() {
                    @Override
                    public String getFlowId() {
                        return flowid;
                    }
                };
            }
        }
        return null;
    }

    protected boolean isFlowAction(GrailsControllerClass controllerClass, String actionName) {
        Set<String> flowNames = flowNamesByController.get(controllerClass?.logicalPropertyName)
        return flowNames?.contains(actionName)
    }

    @Override
    protected Object getHandlerInternal(HttpServletRequest request) throws Exception {
        String uri = urlHelper.getPathWithinApplication(request)
        UrlMappingInfo[] urlMappingInfos = urlMappingsHolder.matchAll(uri, request.getMethod(), UrlMapping.ANY_VERSION)
        GrailsControllerClass matchedController
        for(UrlMappingInfo info in urlMappingInfos) {

            if(info && info.controllerName != null) {
                // Check if the action equals the controller we want...
                Set<String> flowActions = flowNamesByController.get(info.controllerName)

                if(flowActions?.size() > 0) {
                    matchedController = controllerClassMap.get(info.controllerName)
                    String appliedAction
                    // Specific action?
                    if(info.actionName != null) {
                        if(flowActions.contains(info.actionName)) {
                            appliedAction = info.actionName
                        }
                    }
                    // Default action is flow?
                    else if(matchedController?.defaultAction?.endsWith(FLOW_SUFFIX)) {
                        appliedAction = matchedController.defaultAction[0..-(1+FLOW_SUFFIX.length())]
                    }

                    if(appliedAction != null) {
                        request.setAttribute(MATCHED_REQUEST, info)
                        request.setAttribute(DefaultGrailsApplicationAttributes.GRAILS_CONTROLLER_CLASS, matchedController)
                        request.setAttribute(DefaultGrailsApplicationAttributes.CONTROLLER_NAME_ATTRIBUTE, info.controllerName)
                        request.setAttribute(DefaultGrailsApplicationAttributes.ACTION_NAME_ATTRIBUTE, appliedAction)

                        GrailsWebRequest grailsWebRequest = GrailsWebRequest.lookup(request);
                        if (grailsWebRequest != null) {
                            grailsWebRequest.resetParams()
                            info.configure(grailsWebRequest)
                        }
                        break;
                    }
                }
            }
        }
        return getHandlerForControllerClass(matchedController, request)

    }

    @Override
    protected HandlerExecutionChain getHandlerExecutionChain(Object handler, HttpServletRequest request) {
        HandlerExecutionChain chain = (handler instanceof HandlerExecutionChain ?
                (HandlerExecutionChain) handler : new HandlerExecutionChain(handler));



        return chain;
    }

    @Override
    public Object invokeMethod(String string, Object object) {
        return object;
    }

    public void registerFlow(GrailsControllerClass controllerClass, String flowName) {
        controllerClassMap.put(controllerClass.logicalPropertyName, controllerClass)
        synchronized(this) {
            Set<String> flowNames = flowNamesByController.get(controllerClass.logicalPropertyName)
            if (flowNames == null) {
                flowNames = Collections.synchronizedSet(new HashSet<String>())
                flowNamesByController.put(controllerClass.logicalPropertyName, flowNames)
            }
            flowNames.add(flowName)
        }
    }

    void clearFlows(GrailsControllerClass controllerClass) {
        synchronized (this) {
            controllerClassMap.remove(controllerClass)
            flowNamesByController.remove(controllerClass.logicalPropertyName)
        }
    }

    public void afterPropertiesSet() {
    }

    GrailsApplication getGrailsApplication() {
        return grailsApplication
    }

    void setGrailsApplication(GrailsApplication grailsApplication) {
        this.grailsApplication = grailsApplication
    }

}
