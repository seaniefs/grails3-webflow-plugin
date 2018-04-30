package webflow

import grails.core.GrailsControllerClass
import grails.plugins.*
import grails.web.UrlConverter
import grails.web.mapping.UrlMappingsHolder
import org.grails.core.util.ClassPropertyFetcher
import org.grails.webflow.FlowAwareDefaultRequestStateLookupStrategy
import org.grails.webflow.ajax.AjaxWebFlowFixBeanPostProcessor
import org.grails.webflow.context.servlet.GrailsFlowUrlHandler
import org.grails.webflow.engine.builder.FlowBuilder
import org.grails.webflow.execution.GrailsFlowExecutorImpl
import org.grails.webflow.mvc.servlet.GrailsFlowHandlerAdapter
import org.grails.webflow.mvc.servlet.GrailsFlowHandlerMapping
import org.grails.webflow.scope.ScopeRegistrar
import org.springframework.beans.factory.FactoryBean
import org.springframework.binding.convert.service.DefaultConversionService
import org.springframework.context.ApplicationContext
import org.springframework.expression.spel.standard.SpelExpressionParser
import org.springframework.webflow.conversation.ConversationManager
import org.springframework.webflow.conversation.impl.SessionBindingConversationManager
import org.springframework.webflow.core.collection.LocalAttributeMap
import org.springframework.webflow.core.collection.MutableAttributeMap
import org.springframework.webflow.definition.registry.FlowDefinitionLocator
import org.springframework.webflow.definition.registry.FlowDefinitionRegistry
import org.springframework.webflow.definition.registry.FlowDefinitionRegistryImpl
import org.springframework.webflow.engine.RequestControlContext
import org.springframework.webflow.engine.builder.DefaultFlowHolder
import org.springframework.webflow.engine.builder.FlowAssembler
import org.springframework.webflow.engine.builder.support.FlowBuilderServices
import org.springframework.webflow.engine.impl.FlowExecutionImplFactory
import org.springframework.webflow.execution.FlowExecutionFactory
import org.springframework.webflow.execution.FlowExecutionKey
import org.springframework.webflow.execution.repository.BadlyFormattedFlowExecutionKeyException
import org.springframework.webflow.execution.repository.FlowExecutionRepositoryException
import org.springframework.webflow.execution.repository.impl.DefaultFlowExecutionRepository
import org.springframework.webflow.execution.repository.snapshot.FlowExecutionSnapshotFactory
import org.springframework.webflow.execution.repository.snapshot.SerializedFlowExecutionSnapshotFactory
import org.springframework.webflow.expression.spel.WebFlowSpringELExpressionParser
import org.springframework.webflow.mvc.builder.MvcViewFactoryCreator

import java.beans.PropertyDescriptor
import java.lang.reflect.Method
import java.lang.reflect.Modifier

class WebflowGrailsPlugin extends Plugin {

    def name = "webflow"
    def version = "3.2.0-SNAPSHOT"

    // the version or versions of Grails the plugin is designed for
    def grailsVersion = "3.2.0 > *"
    // resources that are excluded from plugin packaging
    def pluginExcludes = [
        "grails-app/views/**"
    ]

    // TODO Fill in these fields
    def title = "webflow" // Headline display name of the plugin
    def author = "Your name"
    def authorEmail = ""
    def description = '''\
Brief summary/description of the plugin.
'''
    def profiles = ['web']

    // URL to the plugin's documentation
    def documentation = "http://grails-plugins.github.io/grails-webflow-plugin/"

    // Extra (optional) plugin metadata

    // License: one of 'APACHE', 'GPL2', 'GPL3'
    def license = "APACHE"

    // Details of company behind the plugin (if there is one)
//    def organization = [ name: "My Company", url: "http://www.my-company.com/" ]

    // Any additional developers beyond the author specified above.
//    def developers = [ [ name: "Joe Bloggs", email: "joe@bloggs.net" ]]

    // Location of the plugin's issue tracker.
//    def issueManagement = [ system: "JIRA", url: "http://jira.grails.org/browse/GPMYPLUGIN" ]

    // Online location of the plugin's browseable source code.
//    def scm = [ url: "http://svn.codehaus.org/grails-plugins/" ]

    /**
     * Sub classes should override to provide implementations
     *
     * @return A closure that defines beans to be executed by Spring
     */
    @Override
    Closure doWithSpring() {
        { ->

            // Fix AJAX bugs Grails
            boolean enableAjaxFix = config?.grails?.plugin?.springwebflow?.enableAjaxFix == true
            if(enableAjaxFix) {
                ajaxWebFlowFixBeanPostProcessor(AjaxWebFlowFixBeanPostProcessor)
            }
            ajaxFixFlowExecutionListener(AjaxWebFlowFixBeanPostProcessor.AjaxFixFlowExecutionListener, enableAjaxFix)

            // TODO: Remove - HACK :P - issue is that GrailsControllerClass assumes that ...Flow Closures are actions,
            ///      and therefore uses the name of the closure as the default action for the controller in links; this is
            //       a temporary hack to workaround this - but not sure how to solve this permanently. :(
            requestStateLookupStrategy(FlowAwareDefaultRequestStateLookupStrategy)
            //

            // Keep this private else it causes issues....
            DefaultConversionService conversionServiceRef = new DefaultConversionService()
            //

            viewFactoryCreator(MvcViewFactoryCreator) {
                viewResolvers = ref('jspViewResolver')
            }

            flowHandlerMapping(GrailsFlowHandlerMapping, ref("grailsUrlMappingsHolder")) {
                // Run slightly higher precedence than the default UrlHandlerMapping - is this correct?
                order = -6
            }
            //conversationService(WebflowDefaultConversionService)
            sep(SpelExpressionParser)
            webFlowExpressionParser(WebFlowSpringELExpressionParser, sep, conversionServiceRef)

            flowBuilderServices(FlowBuilderServices) {
                conversionService = conversionServiceRef
                expressionParser = webFlowExpressionParser
                viewFactoryCreator = viewFactoryCreator
            }

            flowRegistry(FlowDefinitionRegistryImpl)

            flowScopeRegistrar(ScopeRegistrar)

            // TODO: Was springConfig.containsBean("sessionFactory") but this seems to cause issues under 3.2.x, so
            //       temporarily changed - need to check this actually works as currently untested with databases
            boolean configureHibernateListener = true
            if (configureHibernateListener) {
                try {
                    webFlowHibernateConversationListener(org.grails.webflow.persistence.SessionAwareHibernateFlowExecutionListener, ref("sessionFactory"), ref("transactionManager"))
                    webFlowExecutionListenerLoader(AjaxWebFlowFixBeanPostProcessor.StaticFlowExecutionListenerLoader, ref("ajaxFixFlowExecutionListener"), ref("webFlowHibernateConversationListener"))
                }
                catch (MissingPropertyException mpe) {
                    // no session factory, this is ok
                    log.info "Webflow loading without Hibernate integration. SessionFactory not found."
                    configureHibernateListener = false
                }
            }

            flowExecutionFactory(FlowExecutionImplFactory) {
                executionAttributes = new LocalAttributeMap(alwaysRedirectOnPause: true)
                if (configureHibernateListener) {
                    executionListenerLoader = ref("webFlowExecutionListenerLoader")
                }
            }

            conversationManager(SessionBindingConversationManager)

            // Allow the snapshot factory class to be changed according to configuration.
            def snapshotFactoryClazz = config?.grails?.plugin?.springwebflow?.flowExecutionSnapshotFactoryClazz
            def snapshotFactoryClazzAttrs = config?.grails?.plugin?.springwebflow?.flowExecutionSnapshotFactoryClazzAttrs ?: [:]
            def maxSnapshotsValue = config?.grails?.plugin?.springwebflow?.maxSnapshots ?: 30
            if (!(snapshotFactoryClazz instanceof Class)) {
                snapshotFactoryClazz = SerializedFlowExecutionSnapshotFactory
            }
            //
            flowExecutionSnapshotFactory(FlowExecutionSnapshotFactoryFactory) {
                instanceClazz = snapshotFactoryClazz
                flowExecutionFactory = ref("flowExecutionFactory")
                flowDefinitionLocator = ref("flowRegistry")
                otherAttrs = snapshotFactoryClazzAttrs
            }
            flowExecutionRepository(CustomFlowExecutionRepository, conversationManager, flowExecutionSnapshotFactory) {
                maxSnapshots = maxSnapshotsValue
            }
            flowExecutor(GrailsFlowExecutorImpl, flowRegistry, flowExecutionFactory, flowExecutionRepository)

            mainFlowController(GrailsFlowHandlerAdapter) {
                flowExecutor = flowExecutor
                flowUrlHandler = { GrailsFlowUrlHandler uh -> }
            }
        }
    }

    /**
     * Invokes once the {@link org.springframework.context.ApplicationContext} has been refreshed and after {#doWithDynamicMethods()} is invoked. Subclasses should override
     */
    @Override
    void doWithApplicationContext() {
        def appCtx = getApplicationContext()
        FlowExecutionFactory flowExecutionFactory = appCtx.getBean("flowExecutionFactory")
        flowExecutionFactory.executionKeyFactory = appCtx.getBean("flowExecutionRepository")
    }

    /**
     * Invoked in a phase where plugins can add dynamic methods. Subclasses should override
     */
    @Override
    void doWithDynamicMethods() {
        def appCtx = getApplicationContext()
        def application = getGrailsApplication()
        UrlMappingsHolder grailsUrlMappingsHolder = appCtx.getBean(UrlMappingsHolder)

        // Manually wire this... :P
        FlowAwareDefaultRequestStateLookupStrategy lookupStrategy = appCtx.getBean(FlowAwareDefaultRequestStateLookupStrategy)
        lookupStrategy.grailsApplication = application
        GrailsFlowHandlerMapping grailsFlowHandlerMapping = appCtx.getBean(GrailsFlowHandlerMapping)
        grailsFlowHandlerMapping.grailsApplication = application
        UrlConverter urlConverter = appCtx.getBean(UrlConverter)

        // Find instances of ...Flow closures in the controller
        for (GrailsControllerClass c in application.controllerClasses) {
            registerFlowsForController(appCtx, c, grailsUrlMappingsHolder)
        }

        RequestControlContext.metaClass.getFlow = { -> delegate.flowScope }

        RequestControlContext.metaClass.getConversation = { -> delegate.conversationScope }

        RequestControlContext.metaClass.getFlash = { -> delegate.flashScope }

        MutableAttributeMap.metaClass.getProperty = { String name ->
            def mp = delegate.class.metaClass.getMetaProperty(name)
            def result = null
            if (mp) result = mp.getProperty(delegate)
            else {
                result = delegate.get(name)
            }
            result
        }
        MutableAttributeMap.metaClass.setProperty = { String name, value ->
            def mp = delegate.class.metaClass.getMetaProperty(name)
            if (mp) mp.setProperty(delegate, value)
            else {
                delegate.put(name, value)
            }
        }
        MutableAttributeMap.metaClass.clear = {-> delegate.asMap().clear() }
        MutableAttributeMap.metaClass.getAt = { String key -> delegate.get(key) }
        MutableAttributeMap.metaClass.putAt = { String key, value -> delegate.put(key,value) }
    }

    /**
     * Invoked when a object this plugin is watching changes
     *
     * @param event The event
     */
    void onChange(Map<String, Object> event) {
        def application = getGrailsApplication()
        ApplicationContext appCtx = event.ctx
        FlowDefinitionRegistry flowRegistry = appCtx.flowRegistry
        GrailsControllerClass controller = application.getControllerClass(event.source.name)
        if (!controller) {
            return
        }

        def controllerClass = controller.clazz
        def registry = GroovySystem.metaClassRegistry
        def currentMetaClass = registry.getMetaClass(controllerClass)

        try {
            // we remove the current meta class because webflow needs an unmodified (via meta programming) controller
            // in order to configure itself correctly
            registry.removeMetaClass controllerClass
            controller.getReference().getWrappedInstance().metaClass = registry.getMetaClass(controllerClass)
            registerFlowsForController(appCtx, controllerClass)
        }
        finally {
            registry.setMetaClass controllerClass, currentMetaClass
            controller.getReference().getWrappedInstance().metaClass = currentMetaClass
        }
    }

    void onConfigChange(Map<String, Object> event) {
        // TODO Implement code that is executed when the project configuration changes.
        // The event is the same as for 'onChange'.
    }

    void onShutdown(Map<String, Object> event) {
        // TODO Implement code that is executed when the application shuts down (optional)
    }


    private static void registerFlowsForController(appCtx, GrailsControllerClass c, UrlMappingsHolder grailsUrlMappingsHolder) {
        GrailsFlowHandlerMapping grailsFlowHandlerMapping = appCtx.getBean(GrailsFlowHandlerMapping)
        UrlConverter urlConverter = appCtx.getBean(UrlConverter)

        // Clear any old mappings...
        grailsFlowHandlerMapping.clearFlows(c)

        Map<String, Closure> flows = [:]
        final String FLOW_SUFFIX = "Flow"
        ClassPropertyFetcher classPropertyFetcher = ClassPropertyFetcher.forClass(c.clazz)
        for (PropertyDescriptor propertyDescriptor : classPropertyFetcher.getPropertyDescriptors()) {
            Method readMethod = propertyDescriptor.getReadMethod();
            if (readMethod != null && !Modifier.isStatic(readMethod.getModifiers())) {
                final Class<?> propertyType = propertyDescriptor.getPropertyType();
                if ((propertyType == Object.class || propertyType == Closure.class) && propertyDescriptor.getName().endsWith(FLOW_SUFFIX)) {
                    String closureName = propertyDescriptor.getName();
                    // Ensure that we get the bean reference, so as services and other beans are autowired correctly...
                    def controllerBean = appCtx.getBean(c.clazz)
                    def flowClosure = controllerBean."${closureName}"
                    flows.put(closureName, flowClosure)
                }
            }
        }
        // Register the flows...
        for (flow in flows) {
            String flowName = flow.key.substring(0, flow.key.length() - FLOW_SUFFIX.length());
            def flowId = ("${c.logicalPropertyName}/" + flowName).toString()
            def builder = new FlowBuilder(flowId, flow.value, appCtx.flowBuilderServices, appCtx.flowRegistry)
            builder.viewPath = "/"
            builder.applicationContext = appCtx
            def assembler = new FlowAssembler(builder, builder.getFlowBuilderContext())
            appCtx.flowRegistry.registerFlowDefinition new DefaultFlowHolder(assembler)
            grailsFlowHandlerMapping.registerFlow(c, flowName)
        }
    }

}
class FlowExecutionSnapshotFactoryFactory implements FactoryBean {
    private Class instanceClazz;
    private FlowExecutionFactory flowExecutionFactory;
    private FlowDefinitionLocator flowDefinitionLocator;
    private Map otherAttrs;

    FlowExecutionSnapshotFactoryFactory() {
    }

    Class getInstanceClazz() {
        return instanceClazz
    }

    void setInstanceClazz(Class instanceClazz) {
        this.instanceClazz = instanceClazz
    }

    FlowExecutionFactory getFlowExecutionFactory() {
        return flowExecutionFactory
    }

    void setFlowExecutionFactory(FlowExecutionFactory flowExecutionFactory) {
        this.flowExecutionFactory = flowExecutionFactory
    }

    FlowDefinitionLocator getFlowDefinitionLocator() {
        return flowDefinitionLocator
    }

    void setFlowDefinitionLocator(FlowDefinitionLocator flowDefinitionLocator) {
        this.flowDefinitionLocator = flowDefinitionLocator
    }

    Map getOtherAttrs() {
        return otherAttrs
    }

    void setOtherAttrs(Map otherAttrs) {
        this.otherAttrs = otherAttrs
    }

    @Override
    Object getObject() throws Exception {
        FlowExecutionSnapshotFactory flowExecutionSnapshotFactory = instanceClazz.newInstance(flowExecutionFactory, flowDefinitionLocator)
        otherAttrs?.each {k,v ->
            flowExecutionSnapshotFactory."$k" = v
        }
        return flowExecutionSnapshotFactory
    }

    @Override
    boolean isSingleton() {
        return true
    }

    @Override
    Class<?> getObjectType() {
        return instanceClazz
    }
}

class CustomFlowExecutionRepository extends DefaultFlowExecutionRepository {
    CustomFlowExecutionRepository(ConversationManager conversationManager, FlowExecutionSnapshotFactory snapshotFactory) {
        super(conversationManager, snapshotFactory)
    }

    @Override
    FlowExecutionKey parseFlowExecutionKey(String encodedKey) throws FlowExecutionRepositoryException {
        try {
            return super.parseFlowExecutionKey(encodedKey)
        }
        catch(BadlyFormattedFlowExecutionKeyException eEKE) {
            if(eEKE?.getCause() instanceof NumberFormatException) {
                // Ok, throw away the NumberFormatException, otherwise it's difficult for Grails
                // to select the correct Exception handler...
                throw new BadlyFormattedFlowExecutionKeyException(eEKE.getFormat(), eEKE.getInvalidKey());
            }
            else {
                throw eEKE
            }
        }
    }
}
