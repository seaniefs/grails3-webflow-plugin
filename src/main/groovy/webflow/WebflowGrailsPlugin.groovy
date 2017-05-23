package webflow

import grails.plugins.*
import org.grails.webflow.WebFlowPluginSupport

class WebflowGrailsPlugin extends Plugin {

    def name = "webflow"
    def version = "2.2.0-SNAPSHOT"

    // the version or versions of Grails the plugin is designed for
    def grailsVersion = "3.1.4 > *"
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

    // TODO: Rework so as we are not using WebFlowPluginSupport
    /**
     * Sub classes should override to provide implementations
     *
     * @return A closure that defines beans to be executed by Spring
     */
    @Override
    Closure doWithSpring() {
        WebFlowPluginSupport.doWithSpring
    }

    /**
     * Invoked in a phase where plugins can add dynamic methods. Subclasses should override
     */
    @Override
    void doWithDynamicMethods() {
        WebFlowPluginSupport.doWithDynamicMethods.call(getApplicationContext(), getGrailsApplication())
    }

    /**
     * Invokes once the {@link org.springframework.context.ApplicationContext} has been refreshed and after {#doWithDynamicMethods()} is invoked. Subclasses should override
     */
    @Override
    void doWithApplicationContext() {
        WebFlowPluginSupport.doWithApplicationContext.call(getApplicationContext())
    }

    /**
     * Invoked when a object this plugin is watching changes
     *
     * @param event The event
     */
    void onChange(Map<String, Object> event) {
        WebFlowPluginSupport.onChange.call(event, getGrailsApplication())
    }

    void onConfigChange(Map<String, Object> event) {
        // TODO Implement code that is executed when the project configuration changes.
        // The event is the same as for 'onChange'.
    }

    void onShutdown(Map<String, Object> event) {
        // TODO Implement code that is executed when the application shuts down (optional)
    }
}
