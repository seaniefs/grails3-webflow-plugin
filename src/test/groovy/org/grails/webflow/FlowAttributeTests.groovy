package org.grails.webflow

import grails.util.MockHttpServletResponse
import org.grails.web.servlet.DefaultGrailsApplicationAttributes
import org.grails.web.servlet.mvc.GrailsWebRequest
import org.grails.webflow.support.AbstractGrailsTagAwareFlowExecutionTests
import org.junit.Assert
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockServletContext
import org.springframework.web.context.request.RequestContextHolder

/**
 * @author seaniefs
 * @since 1.0
 */
class FlowAttributeTests extends AbstractGrailsTagAwareFlowExecutionTests {

    void testApplyAttributes() {
        startFlow()
        Assert.assertFalse(getFlowDefinition().getAttributes().get("transactional"));
    }

    Closure getFlowClosure() {
        return {
            flowAttributes {
                ["transactional":false]
            }
            one {
                on("test1").to "test1"
                on("test2").to "test2"
                on("test3").to "test3"
            }
            test1 {
                redirect(controller:"test", action:"foo")
            }
            test2 {
                redirect(controller:"test", action:"foo", id: params.id)
            }
            test3 {
                redirect(action:"foo")
            }
        }
    }
}
