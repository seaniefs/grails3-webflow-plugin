/* Copyright 2004-2005 Graeme Rocher
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
package org.grails.webflow.persistence;

import org.hibernate.FlushMode;
import org.hibernate.Interceptor;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.engine.spi.SessionImplementor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.orm.hibernate4.SessionHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallbackWithoutResult;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.ReflectionUtils;
import org.springframework.webflow.core.collection.AttributeMap;
import org.springframework.webflow.core.collection.MutableAttributeMap;
import org.springframework.webflow.definition.FlowDefinition;
import org.springframework.webflow.execution.FlowSession;
import org.springframework.webflow.execution.RequestContext;
import org.springframework.webflow.persistence.HibernateFlowExecutionListener;
import org.springframework.util.ClassUtils;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Extends the HibernateFlowExecutionListener and doesn't bind a session if one is already present.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class SessionAwareHibernateFlowExecutionListener extends HibernateFlowExecutionListener {

    private static final boolean hibernate3Present = ClassUtils.isPresent("org.hibernate.connection.ConnectionProvider", HibernateFlowExecutionListener.class.getClassLoader());
    private static final boolean hibernate5Present = ClassUtils.isPresent("org.hibernate.boot.model.naming.PhysicalNamingStrategy", HibernateFlowExecutionListener.class.getClassLoader());
    private static final Method openSessionMethod =  ReflectionUtils.findMethod(SessionFactory.class, "openSession");
    private static final Method openSessionWithInterceptorMethod = ReflectionUtils.findMethod(SessionFactory.class, "openSession", Interceptor.class);
    private static final Method currentSessionMethod = ClassUtils.getMethod(SessionFactory.class, "getCurrentSession");
    private static final Method closeSessionMethod = ReflectionUtils.findMethod(Session.class, "close");

    private final Logger log = LoggerFactory.getLogger(getClass());

    private SessionFactory localSessionFactory;
	private TransactionTemplate transactionTemplate;
    private Interceptor entityInterceptor;

    /**
     * Create a new Hibernate Flow Execution Listener using giving Hibernate session factory and transaction manager.
     *
     * @param sessionFactory     the session factory to use
     * @param transactionManager the transaction manager to drive transactions
     */
    public SessionAwareHibernateFlowExecutionListener(SessionFactory sessionFactory, PlatformTransactionManager transactionManager) {
        super(sessionFactory, transactionManager);
        this.localSessionFactory = sessionFactory;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Override
    public void sessionStarting(RequestContext context, FlowSession session, MutableAttributeMap input) {
        if (!isSessionAlreadyBound()) {
            if(isCommitAndClearOnPause(context)) {
                log.debug("sessionStarting: CommitAndClearPre");
                doCommitAndClearPre(context);
            }
            else {
                log.debug("sessionStarting: Binding Hibernate session to flow");
                super.sessionStarting(context, session, input);
            }
        }
        else {
            log.debug("sessionStarting: Obtaining current Hibernate session");
            obtainCurrentSession(context);
        }
    }

    @Override
    public void sessionEnding(RequestContext context, FlowSession session, String outcome, MutableAttributeMap output) {
        final Session hibernateSession = getBoundHibernateSession(session);
        if (hibernateSession!= null && (session.isRoot() || isCommitAndClearOnPause(context))) {
            if(isCommitAndClearOnPause(context)) {
                log.debug("sessionEnding: CommitAndClearPost");
                doCommitAndClearPost(context);
            }
            else {
                log.debug("sessionEnding: Commit transaction and unbinding Hibernate session");
                super.sessionEnding(context, session, outcome, output);
            }
        }
    }

    @Override
    public void resuming(RequestContext context) {
        if (!isSessionAlreadyBound()) {
            if(isCommitAndClearOnPause(context)) {
                log.debug("resuming: CommitAndClearPre");
                doCommitAndClearPre(context);
            }
            else {
                log.debug("resuming: Resumed flow, obtaining existing Hibernate session");
                //            final FlowExecutionContext executionContext = context.getFlowExecutionContext();
                //            if (executionContext.getActiveSession().getScope().get(PERSISTENCE_CONTEXT_ATTRIBUTE) != null) {
                super.resuming(context);
                //            }
            }
        }
        else {
            obtainCurrentSession(context);
        }
    }

    private boolean isSessionAlreadyBound() {
        return TransactionSynchronizationManager.hasResource(localSessionFactory);
    }

    @Override
    public void sessionEnded(RequestContext context, FlowSession session, String outcome, AttributeMap output) {
        if (isPersistenceContext(session.getDefinition()) && (!isSessionAlreadyBound() || isCommitAndClearOnPause(context))) {
            super.sessionEnded(context, session, outcome, output);
        }
    }

    @Override
    public void paused(RequestContext context) {
        if (log.isDebugEnabled()) log.debug("paused: Disconnecting Hibernate session");
        if(isPersistenceContext(context.getActiveFlow())
           && isCommitAndClearOnPause(context)) {
            log.debug("paused: CommitAndClearPost");
            doCommitAndClearPost(context);
        }
        else {
            super.paused(context);
        }
    }

    private void doCommitAndClearPre(RequestContext context) {
        // Create a new session
        Session hibernateSession = createSession(context);
        // Set it up
        setHibernateSession(context.getFlowExecutionContext().getActiveSession(), new SessionTransientWrapper(hibernateSession));
        // Bind it
        bind(hibernateSession);
    }

    private void doCommitAndClearPost(RequestContext context) {
        // Ok, this is OSIV - so we need to commit any changes which were made...
        transactionTemplate.execute(new TransactionCallbackWithoutResult() {
            protected void doInTransactionWithoutResult(TransactionStatus status) {
                if (hibernate3Present) {
                    ReflectionUtils.invokeMethod(currentSessionMethod, localSessionFactory);
                }
                else {
                    localSessionFactory.getCurrentSession();
                }
                // nothing to do; a flush will happen on commit automatically as this is a read-write
                // transaction
            }
        });
        // Unbind the session, disconnect and then close
        Session session = getBoundHibernateSession(context.getFlowExecutionContext().getActiveSession());
        unbind(session);
        // Remove it from the flow
        context.getFlowScope().remove(PERSISTENCE_CONTEXT_ATTRIBUTE);
        // Close it
        ReflectionUtils.invokeMethod(closeSessionMethod, session);
    }

    private Session getBoundHibernateSession(FlowSession session) {
        return (Session) session.getScope().get(PERSISTENCE_CONTEXT_ATTRIBUTE);
    }

    private boolean isPersistenceContext(FlowDefinition flow) {
        return flow.getAttributes().contains(PERSISTENCE_CONTEXT_ATTRIBUTE);
    }

    private boolean isCommitAndClearOnPause(RequestContext context) {
        // Find the root flow session and check if commit and clear on pause is set - if so, all children inherit that...
        FlowSession flowSession = context.getFlowExecutionContext().getActiveSession();
        while(flowSession != null && !flowSession.isRoot()) {
            flowSession = flowSession.getParent();
        }
        return flowSession != null && flowSession.getDefinition().getAttributes().contains("commitAndClearOnPause");
    }

    private void obtainCurrentSession(RequestContext context) {
        MutableAttributeMap flowScope = context.getFlowScope();
        if (flowScope.get(PERSISTENCE_CONTEXT_ATTRIBUTE) != null) {
            return;
        }

        Session session = null;
        if(hibernate5Present) {
            org.springframework.orm.hibernate5.SessionHolder sessionHolder = (org.springframework.orm.hibernate5.SessionHolder) TransactionSynchronizationManager.getResource(localSessionFactory);
            if (sessionHolder != null) session = sessionHolder.getSession();
        }
        else if (hibernate3Present) {
            org.springframework.orm.hibernate3.SessionHolder sessionHolder = (org.springframework.orm.hibernate3.SessionHolder) TransactionSynchronizationManager.getResource(localSessionFactory);
            if (sessionHolder != null) session = sessionHolder.getSession();
        } else {
            SessionHolder sessionHolder = (SessionHolder) TransactionSynchronizationManager.getResource(localSessionFactory);  
            if (sessionHolder != null) session = sessionHolder.getSession();
       }
        flowScope.put(PERSISTENCE_CONTEXT_ATTRIBUTE, session);
    }

    @Override
    public void setEntityInterceptor(Interceptor entityInterceptor) {
        super.setEntityInterceptor(entityInterceptor);
        this.entityInterceptor = entityInterceptor;
    }

    private Session createSession(RequestContext context) {
        Session session;
        if (entityInterceptor != null) {
            if (hibernate3Present) {
                try {
                    session = (Session) openSessionWithInterceptorMethod.invoke(localSessionFactory, entityInterceptor);
                } catch (IllegalAccessException ex) {
                    throw new IllegalStateException("Unable to open Hibernate 3 session", ex);
                } catch (InvocationTargetException ex) {
                    throw new IllegalStateException("Unable to open Hibernate 3 session", ex);
                }
            } else {
                session = localSessionFactory.withOptions().interceptor(entityInterceptor).openSession();
            }
        } else {
            if (hibernate3Present) {
                try {
                    session = (Session) openSessionMethod.invoke(localSessionFactory);
                } catch (IllegalAccessException ex) {
                    throw new IllegalStateException("Unable to open Hibernate 3 session", ex);
                } catch (InvocationTargetException ex) {
                    throw new IllegalStateException("Unable to open Hibernate 3 session", ex);
                }
            }
            else {
                session = localSessionFactory.openSession();
            }
        }
        session.setFlushMode(FlushMode.MANUAL);
        return session;
    }

    private void setHibernateSession(FlowSession session, Session hibernateSession) {
        session.getScope().put(PERSISTENCE_CONTEXT_ATTRIBUTE, hibernateSession);
    }

    private void bind(Session session) {
        Object sessionHolder;
        if (hibernate3Present) {
            sessionHolder = new org.springframework.orm.hibernate3.SessionHolder(session);
        }
        else if (hibernate5Present) {
            sessionHolder = new org.springframework.orm.hibernate5.SessionHolder(session);
        }
        else {
            sessionHolder = new SessionHolder(session);
        }
        TransactionSynchronizationManager.bindResource(localSessionFactory, sessionHolder);
    }

    private void unbind(Session session) {
        if (TransactionSynchronizationManager.hasResource(localSessionFactory)) {
            TransactionSynchronizationManager.unbindResource(localSessionFactory);
        }
    }

}
