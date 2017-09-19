package org.grails.webflow

import org.grails.web.servlet.mvc.DefaultRequestStateLookupStrategy
import org.grails.web.servlet.mvc.GrailsWebRequest

/**
 * Created by seaniefs on 04/04/17 - hack for issue with GrailsControllerClass and default actions in url generation
 */
class FlowAwareDefaultRequestStateLookupStrategy extends DefaultRequestStateLookupStrategy {

    public FlowAwareDefaultRequestStateLookupStrategy() {
        // default
    }

    public FlowAwareDefaultRequestStateLookupStrategy(GrailsWebRequest webRequest) {
        super(webRequest);
    }


    @Override
    String getActionName() {
        return stripFlow(super.getActionName())
    }

    @Override
    String getActionName(String controllerName) {
        return stripFlow(super.getActionName(controllerName))
    }

    private String stripFlow(String input) {
        if(input?.endsWith("Flow")) {
            return input.substring(0, input.length() - "Flow".length())
        }
        return input
    }

}
