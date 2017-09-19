package org.grails.webflow.ajax;

import org.springframework.webflow.execution.FlowExecutionKey;

/**
 * Created by seaniefs on 31/08/17.
 */
public class ExecutionParameterAccessor {

    private static ThreadLocal<FlowExecutionKey> EXECUTION_KEYS = new ThreadLocal<FlowExecutionKey>();

    static void setExecutionParameter(FlowExecutionKey flowExecutionKey) {
        EXECUTION_KEYS.set(flowExecutionKey);
    }

    public static void clearExecutionParameter() {
        EXECUTION_KEYS.set(null);
    }

    public static String getExecutionParameter() {
        FlowExecutionKey key = getFlowExecutionKey();
        return key == null ? null : key.toString();
    }

    public static FlowExecutionKey getFlowExecutionKey() {
        return EXECUTION_KEYS.get();
    }

}

