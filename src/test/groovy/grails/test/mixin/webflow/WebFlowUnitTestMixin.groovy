/*
 * Copyright 2011 SpringSource
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
package grails.test.mixin.webflow

import grails.testing.web.controllers.ControllerUnitTest
import grails.util.GrailsMetaClassUtils
import groovy.transform.CompileStatic

import org.grails.web.util.GrailsApplicationAttributes
import org.junit.Assert
import spock.lang.Specification

/**
 * A unit test mixin for testing Web Flow interactions
 *
 * @author Graeme Rocher
 * @since 2.0
 */
class WebFlowUnitTestMixin<T> extends Specification implements ControllerUnitTest<T> {
    /*
    private static final Set<String> REQUIRED_FEATURES = (["webFlow"] as Set).asImmutable()

    public WebFlowUnitTestMixin(Set<String> features) {
        super((REQUIRED_FEATURES + features) as Set)
    }

    public WebFlowUnitTestMixin() {
        super(REQUIRED_FEATURES)
    }
    */

    @CompileStatic
    static class TestState {
        String stateTransition
        String lastEventName
        String lastTransitionName
        Boolean isOutput
        Boolean isInput
        Map conversation = [:]
        Map flow = [:]
        Map flowMap = [:]
        Map currentEvent = [:]
        Map inputParams = [:]
    }

    private TestState testState;

    TestState getTestState() {
        if(testState == null) {
            testState = new TestState()
        }
        testState
    }

    public Object mockWebFlowController(Class<?> controllerClass) {
        def mc = GrailsMetaClassUtils.getExpandoMetaClass(controllerClass)
        mc.getFlow = {-> this.testState.flow }
        mc.getConversation = {-> this.testState.conversation }
        mc.getCurrentEvent = {-> this.testState.currentEvent }
        return mockController(controllerClass)
    }

    def propertyMissing(String name) {
        def controller = webRequest.currentRequest.getAttribute(GrailsApplicationAttributes.CONTROLLER)
        def webFlowName = name
        if (controller == null) {
            Assert.fail("No controller mocked. Call mockController(Class) first")
        }
        def webFlowClosure
        try {
            webFlowClosure = controller."$webFlowName"
        } catch (MissingPropertyException mpe) {
            Assert.fail("No flow named $name found in controller $controller")
        }

        if (webFlowClosure instanceof Closure) {
            def flowMap = WebFlowUnitTestSupport.translate(webFlowClosure, {
                TestState testState = this.getTestState()
                testState.lastEventName = it.event
                testState.lastTransitionName = it.transition
                testState.isOutput = it.isOutput
                testState.isInput = it.isInput
                testState.inputParams = it.inputParams
            })
            testState.flowMap = flowMap
            webFlowClosure.delegate = this
            return flowMap
        }
        return webFlowClosure
    }

    String getStateTransition() {
        this.getTestState().stateTransition
    }

    /**
     * Registers the end transition state of a web flow if it is returned as
     * <code>return success()</code>
     */
    Object methodMissing(String name, Object args) {
        TestState testState = this.getTestState()
        if (testState.isOutput) {
            doOutput(name, args[0])
            return
        }
        if (testState.isInput) {
            args ? doInput(name, args[0]) : doInput(name)
            return
        }
        if (testState.lastEventName && testState.flowMap[testState.lastEventName].on."$name") {
            testState.stateTransition = name
            return name
        }
        if (testState.lastEventName && testState.lastTransitionName) {
            if (name == 'error') {
                testState.stateTransition = testState.lastEventName
                return name
            }
            if (name == 'success' && testState.flowMap[testState.lastEventName].on?."${testState.lastTransitionName}"?.to) {
                testState.stateTransition = testState.flowMap[testState.lastEventName].on?."${testState.lastTransitionName}"?.to
                return name
            }
        }
        throw new MissingMethodException(name, this.class, args)
    }

    def doOutput(String name, Closure valueClosure) {
        TestState testState = this.getTestState()
        if (testState.currentEvent.attributes == null) {
            testState.currentEvent.attributes = [:]
        }
        testState.currentEvent.attributes[name] = valueClosure()
    }

    def doOutput(String name, Map valueMap) {
        TestState testState = this.getTestState()
        if (testState.currentEvent.attributes == null) {
            testState.currentEvent.attributes = [:]
        }
        testState.currentEvent.attributes[name] = valueMap.value
    }

    def doOutput(String name, Object value) {
        TestState testState = this.getTestState()
        if (testState.currentEvent.attributes == null) {
            testState.currentEvent.attributes = [:]
        }
        testState.currentEvent.attributes[name] = value
    }

    def doInput(String name) {
        doInput (name, false, null)
    }

    def doInput(String name, Closure valueClosure) {
        doInput(name, false, valueClosure)
    }

    def doInput(String name, Object defaultValue) {
        doInput(name, false, defaultValue)
    }

    def doInput(String name, Map map) {
        doInput(name, map.required, map.value)
    }

    def doInput(String name, Boolean required, Closure valueClosure) {
        def value = valueClosure()
        doInput(name, required, value)
    }

    def doInput(String name, Boolean required, Object defaultValue) {
        TestState testState = this.getTestState()
        def value = testState.inputParams[name] ?: defaultValue
        if (required && !value) {
            throw new MissingPropertyException("Missing required attribute $name")
        }
        testState.flow[name] = value
    }
}