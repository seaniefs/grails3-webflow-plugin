package org.grails.webflow.ajax;

/**
 * Created by seaniefs on 31/08/17.
 */

import org.grails.web.servlet.mvc.GrailsWebRequest;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.util.Assert;
import org.springframework.webflow.context.ExternalContext;
import org.springframework.webflow.context.ExternalContextHolder;
import org.springframework.webflow.core.collection.AttributeMap;
import org.springframework.webflow.core.collection.MutableAttributeMap;
import org.springframework.webflow.definition.FlowDefinition;
import org.springframework.webflow.definition.StateDefinition;
import org.springframework.webflow.definition.TransitionDefinition;
import org.springframework.webflow.execution.*;
import org.springframework.webflow.execution.factory.FlowExecutionListenerLoader;
import org.springframework.webflow.execution.factory.StaticFlowExecutionListenerLoader;
import org.springframework.webflow.execution.repository.FlowExecutionRepository;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.*;

/**
 * Spring {@link BeanPostProcessor} to attempt to introduce some fixes to the
 * Spring Web Flow implementation so as AJAX works, because currently it's broken.
 * @author seaniefs
 *
 */
public class AjaxWebFlowFixBeanPostProcessor implements BeanPostProcessor {

    public static final class StaticFlowExecutionListenerLoader implements FlowExecutionListenerLoader {

        /**
         * The listener array to return when {@link #getListeners(FlowDefinition)} is invoked.
         */
        private final FlowExecutionListener[] listeners;

        /**
         * Creates a new flow execution listener loader that returns the provided listener on each invocation.
         * @param listener the listener
         */
        public StaticFlowExecutionListenerLoader(FlowExecutionListener listener) {
            this(new FlowExecutionListener[] { listener });
        }

        /**
         * Creates a new flow execution listener loader that returns the provided listener on each invocation.
         * @param firstListener the first listener
         * @param secondListener the second listener
         */
        public StaticFlowExecutionListenerLoader(FlowExecutionListener firstListener, FlowExecutionListener secondListener) {
            this(new FlowExecutionListener[] { firstListener, secondListener });
        }

        /**
         * Creates a new flow execution listener loader that returns the provided listener array on each invocation. Clients
         * should not attempt to modify the passed in array as no deep copy is made.
         * @param listeners the listener array.
         */
        public StaticFlowExecutionListenerLoader(FlowExecutionListener... listeners) {
            Assert.notNull(listeners, "The flow execution listener array is required");
            this.listeners = listeners;
        }

        /**
         * Creates a new flow execution listener loader that returns an empty listener array on each invocation.
         */
        private StaticFlowExecutionListenerLoader() {
            this(new FlowExecutionListener[0]);
        }

        public FlowExecutionListener[] getListeners(FlowDefinition flowDefinition) {
            return listeners;
        }
    }

    public static class AjaxFixFlowExecutionListener implements FlowExecutionListener {

        static FlowExecutionRepository repository;
        static FlowExecutionKeyFactory factory;
        private boolean enabled;

        public AjaxFixFlowExecutionListener(boolean enabled) {
            this.enabled = enabled;
        }

        @Override
        public void requestSubmitted(RequestContext context) {
        }

        @Override
        public void requestProcessed(RequestContext context) {
            // TODO Auto-generated method stub

        }

        @Override
        public void sessionCreating(RequestContext context,
                                    FlowDefinition definition) {
            // TODO Auto-generated method stub

        }

        @Override
        public void sessionStarting(RequestContext context,
                                    FlowSession session, MutableAttributeMap input) {
            // TODO Auto-generated method stub

        }

        @Override
        public void sessionStarted(RequestContext context, FlowSession session) {
            // TODO Auto-generated method stub

        }

        @Override
        public void eventSignaled(RequestContext context, Event event) {
            // TODO Auto-generated method stub

        }

        @Override
        public void transitionExecuting(RequestContext context, TransitionDefinition transition) {
            if(!enabled) {
                return;
            }
            if ( isAjaxRequest()
                    && ExecutionParameterAccessor.getFlowExecutionKey() == null ) {
                // Ensure there is a flow execution key...
                FlowExecution execution = repository.getFlowExecution(context.getFlowExecutionContext().getKey());
                FlowExecutionKey key = factory.getKey(execution);
                ExecutionParameterAccessor.setExecutionParameter(key);

                // Urgh - Set up execution and flowExecutionKey - if this is not performed,
                // taglibs won't render g:submitButton and g:form correctly. :P
                //
                // TODO: Find a nicer way - but how?! - NOTE: flowExecutionKey is used by
                //		 taglibs...!
                GrailsWebRequest req = (GrailsWebRequest)org.springframework.web.context.request.RequestContextHolder.currentRequestAttributes();
                req.getParams().setProperty("execution", "" + key);
                req.getRequest().setAttribute("flowExecutionKey", "" + key);
                //
            }
        }

        @Override
        public void stateEntering(RequestContext context, StateDefinition state)
                throws EnterStateVetoException {
            // TODO Auto-generated method stub

        }

        @Override
        public void stateEntered(RequestContext context,
                                 StateDefinition previousState, StateDefinition state) {
            // TODO Auto-generated method stub

        }

        @Override
        public void viewRendering(RequestContext context, View view, StateDefinition viewState) {
        }

        @Override
        public void viewRendered(RequestContext context, View view,
                                 StateDefinition viewState) {
            // TODO Auto-generated method stub

        }

        @Override
        public void paused(RequestContext context) {
            // TODO Auto-generated method stub

        }

        @Override
        public void resuming(RequestContext context) {
            // TODO Auto-generated method stub

        }

        @Override
        public void sessionEnding(RequestContext context, FlowSession session,
                                  String outcome, MutableAttributeMap output) {
            // TODO Auto-generated method stub

        }

        @Override
        public void sessionEnded(RequestContext context, FlowSession session,
                                 String outcome, AttributeMap output) {
            // TODO Auto-generated method stub

        }

        @Override
        public void exceptionThrown(RequestContext context,
                                    FlowExecutionException exception) {
            // TODO Auto-generated method stub

        }

    }

    public class InvocationHandlerImpl implements InvocationHandler {

        private Object proxiedObject;

        public InvocationHandlerImpl(Object proxiedObject) {
            super();
            this.proxiedObject = proxiedObject;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {

            Object  result = null;
            boolean handled = false;

            // If this is parsing a key, then clear the execution parameter...
            String methodName = method.getName();
            int argCount = method.getParameterTypes().length;

            Class<?> invokedClass = method.getDeclaringClass();

            try
            {

                // parseFlowExecutionKey?  Ensure thread has no cached FlowExecutionKey
                if( invokedClass.equals(FlowExecutionRepository.class)
                        && "parseFlowExecutionKey".equals(methodName)
                        && argCount == 1 ) {
                    handled = true;
                    ExecutionParameterAccessor.clearExecutionParameter();
                    result = method.invoke(proxiedObject, args);
                }
                // getKey?  If AJAX request...
                else if( invokedClass.equals(FlowExecutionKeyFactory.class)
                        && "getKey".equals(methodName)
                        && argCount == 1
                        && isAjaxRequest()) {
                    handled = true;

                    // Use cached key, else invoke method and cache key...
                    if (ExecutionParameterAccessor.getFlowExecutionKey() != null) {
                        result = ExecutionParameterAccessor.getFlowExecutionKey();
                    }
                    else {
                        result = method.invoke(proxiedObject, args);
                        if(result instanceof FlowExecutionKey) {
                            ExecutionParameterAccessor.setExecutionParameter((FlowExecutionKey)result);
                            return result;
                        }
                    }
                }

                if( ! handled ) {
                    result = method.invoke(proxiedObject, args);
                }

            }
            catch(InvocationTargetException eITE) {
                // Error? Re-throw as correct exception!
                throw eITE.getCause();
            }

            return result;
        }



    }

    private FlowExecutionRepository repository;
    private FlowExecutionKeyFactory factory;

    /**
     * Process all beans before initialization so as we can proxy the {@link FlowExecutionRepository},
     * {@link FlowExecutionKeyFactory}.  Also, need to introduce a {@link FlowExecutionListenerLoader} so
     * as we can detect rendering of an AJAX view and ensure a key has been generated for it.
     */
    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName)
            throws BeansException {

        if ( bean instanceof FlowExecutionRepository || bean instanceof FlowExecutionKeyFactory ) {

            if(bean instanceof FlowExecutionRepository) {
                AjaxFixFlowExecutionListener.repository = (FlowExecutionRepository)bean;
            }

            if(bean instanceof FlowExecutionKeyFactory) {
                AjaxFixFlowExecutionListener.factory = (FlowExecutionKeyFactory)bean;
            }

            return createProxy(bean);

        }

        return bean;

    }

    @SuppressWarnings("unchecked")
    private <T> T createProxy(Object bean) {
        Class<?>[] allInterfaces = ReflectionUtil.getInterfaces(bean);
        return (T) Proxy.newProxyInstance(bean.getClass().getClassLoader(), allInterfaces, new InvocationHandlerImpl(bean));
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName)
            throws BeansException {
        return bean;
    }

    static boolean isAjaxRequest() {
        boolean isAjax = false;

        ExternalContext ctx = ExternalContextHolder.getExternalContext();

        if (ctx != null) {
            isAjax = ctx.isAjaxRequest();
        }

        return isAjax;
    }

}

class ReflectionUtil {

    private static final WeakHashMap<Class<?>, Class<?>[]> INTERFACE_CACHE = new WeakHashMap<Class<?>, Class<?>[]>();

    public static Class<?>[] getInterfaces(Object bean) {

        Class<?>[] allInterfaces = null;

        synchronized(INTERFACE_CACHE) {
            allInterfaces = INTERFACE_CACHE.get(bean.getClass());
        }

        if(allInterfaces == null) {

            Set<Class<?>> processedItems = new HashSet<Class<?>>();
            Set<Class<?>> foundInterfaces = new HashSet<Class<?>>();
            getInterfaces(processedItems, foundInterfaces, bean.getClass());
            allInterfaces = foundInterfaces.toArray(new Class<?>[0]);

            synchronized(INTERFACE_CACHE) {
                INTERFACE_CACHE.put(bean.getClass(), allInterfaces);
            }

        }

        return allInterfaces;
    }


    private static void getInterfaces(Collection<Class<?>> processedItems, Collection<Class<?>> foundInterfaces, Class<? extends Object> clazz) {
        if( clazz != null
                && ! processedItems.contains( clazz ) ) {
            processedItems.add( clazz );
            for(Class<?> interfaceClazz : clazz.getInterfaces()) {
                foundInterfaces.add(interfaceClazz);
                getInterfaces(processedItems, foundInterfaces, interfaceClazz);
            }
            getInterfaces(processedItems, foundInterfaces, clazz.getSuperclass());
        }
    }

}
