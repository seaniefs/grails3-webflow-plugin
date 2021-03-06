package org.grails.webflow

import grails.util.GrailsStringUtils
import org.springframework.beans.BeansException
import org.springframework.beans.factory.BeanCreationException
import org.springframework.beans.factory.BeanFactory
import org.springframework.beans.factory.NoSuchBeanDefinitionException
import org.springframework.beans.factory.config.AutowireCapableBeanFactory
import org.springframework.beans.factory.support.DefaultListableBeanFactory
import org.springframework.context.ApplicationContext
import org.springframework.context.ApplicationEvent
import org.springframework.context.MessageSource
import org.springframework.context.MessageSourceResolvable
import org.springframework.context.NoSuchMessageException
import org.springframework.core.ResolvableType
import org.springframework.core.env.Environment
import org.springframework.core.io.AbstractResource
import org.springframework.core.io.ClassPathResource
import org.springframework.core.io.Resource
import org.springframework.mock.web.MockServletContext
import org.springframework.util.AntPathMatcher
import org.springframework.util.PathMatcher
import org.springframework.web.context.WebApplicationContext
import org.springframework.web.context.support.StandardServletEnvironment

import javax.servlet.ServletContext
import java.lang.annotation.Annotation

/**
 * Created by seaniefs on 04/04/17.
 * Re-write tests not to use :P - temporarily added so as tests pass
 */
@Deprecated
public class MockApplicationContext extends GroovyObjectSupport implements WebApplicationContext {

    Date startupDate = new Date();
    Map<String, Object> beans = new HashMap<String, Object>();
    List<Resource> resources = new ArrayList<Resource>();
    List<String> ignoredClassLocations = new ArrayList<String>();
    PathMatcher pathMatcher = new AntPathMatcher();
    ServletContext servletContext = new MockServletContext();

    public void registerMockBean(String name, Object instance) {
        beans.put(name, instance);
    }

    /**
     * Registers a mock resource. Path separator: "/"
     * @param location the location of the resource. Example: /WEB-INF/grails-app/i18n/messages.properties
     */
    public void registerMockResource(String location) {
        resources.add(new ClassPathResource(GrailsStringUtils.trimStart(location, "/")));
    }

    /**
     * Registers a mock resource. Path separator: "/"
     * @param location the location of the resource. Example: /WEB-INF/grails-app/i18n/messages.properties
     */
    public void registerMockResource(String location, String contents) {
        resources.add(new MockResource(GrailsStringUtils.trimStart(location, "/"), contents));
    }

    /**
     * Unregisters a mock resource. Path separator: "/"
     * @param location the location of the resource. Example: /WEB-INF/grails-app/i18n/messages.properties
     */
    public void unregisterMockResource(String location) {
        for (Iterator<Resource> it = resources.iterator(); it.hasNext();) {
            MockResource mockResource = (MockResource) it.next();
            if (mockResource.location.equals(location)) {
                it.remove();
            }
        }
    }

    /**
     * Registers a resource that should not be found on the classpath. Path separator: "/"
     * @param location the location of the resource. Example: /WEB-INF/grails-app/i18n/messages.properties
     */
    public void registerIgnoredClassPathLocation(String location) {
        ignoredClassLocations.add(location);
    }

    /**
     * Unregisters a resource that should not be found on the classpath. Path separator: "/"
     * @param location the location of the resource. Example: /WEB-INF/grails-app/i18n/messages.properties
     */
    public void unregisterIgnoredClassPathLocation(String location) {
        ignoredClassLocations.remove(location);
    }

    public ApplicationContext getParent() {
        throw new UnsupportedOperationException("Method not supported by implementation");
    }

    public String getId() {
        return "MockApplicationContext";
    }

    public String getApplicationName() {
        return getId();
    }

    public String getDisplayName() {
        return getId();
    }

    public long getStartupDate() {
        return startupDate.getTime();
    }

    public void publishEvent(ApplicationEvent event) {
        // do nothing
    }

    @Override
    void publishEvent(Object event) {

    }

    public boolean containsBeanDefinition(String beanName) {
        return beans.containsKey(beanName);
    }

    public int getBeanDefinitionCount() {
        return beans.size();
    }

    public String[] getBeanDefinitionNames() {
        return beans.keySet().toArray(new String[beans.keySet().size()]);
    }

    @Override
    String[] getBeanNamesForType(ResolvableType type) {
        return new String[0]
    }

    public String[] getBeanNamesForType(Class type) {
        List<String> beanNames = new ArrayList<String>();
        for (String beanName : beans.keySet()) {
            if (type.isAssignableFrom(beans.get(beanName).getClass())) {
                beanNames.add(beanName);
            }
        }
        return beanNames.toArray(new String[beanNames.size()]);
    }

    @SuppressWarnings("rawtypes")
    public String[] getBeanNamesForType(Class type, boolean includePrototypes, boolean includeFactoryBeans) {
        return getBeanNamesForType(type);
    }

    @SuppressWarnings("unchecked")
    public <T> Map<String, T> getBeansOfType(Class<T> type) throws BeansException {
        String[] beanNames = getBeanNamesForType(type);
        Map<String, T> newMap = new HashMap<String, T>();
        for (int i = 0; i < beanNames.length; i++) {
            String beanName = beanNames[i];
            newMap.put(beanName, (T)getBean(beanName));
        }
        return newMap;
    }

    public <T> Map<String, T> getBeansOfType(Class<T> type, boolean includeNonSingletons, boolean allowEagerInit) throws BeansException {
        return getBeansOfType(type);
    }

    public <A extends Annotation> A findAnnotationOnBean(String name, Class<A> annotation) {
        Object o = getBean(name);
        if (o != null) {
            return o.getClass().getAnnotation(annotation);
        }
        return null;
    }

    public Map<String, Object> getBeansWithAnnotation(Class<? extends Annotation> annotation) throws BeansException {
        Map<String, Object> submap = new HashMap<String, Object>();
        for (Object beanName : beans.keySet()) {
            Object bean = beans.get(beanName);
            if (bean != null && bean.getClass().getAnnotation(annotation) != null) {
                submap.put(beanName.toString(), bean);
            }
        }
        return submap;
    }

    /**
     * Find all names of beans whose {@code Class} has the supplied {@link Annotation}
     * type, without creating any bean instances yet.
     * @param annotationType the type of annotation to look for
     * @return the names of all matching beans
     * @since 2.4
     */
    public String[] getBeanNamesForAnnotation(Class<? extends Annotation> annotationType) {
        List<String> beanNamesList = new ArrayList<String>();
        for (Object beanName : beans.keySet()) {
            Object bean = beans.get(beanName);
            if (bean != null && bean.getClass().getAnnotation(annotationType) != null) {
                beanNamesList.add(beanName.toString());
            }
        }
        return beanNamesList.toArray(new String[beanNamesList.size()]);
    }


    public Object getBean(String name) throws BeansException {
        if (!beans.containsKey(name)) {
            throw new NoSuchBeanDefinitionException(name);
        }
        return beans.get(name);
    }

    @SuppressWarnings("unchecked")
    public <T> T getBean(String name, Class<T> requiredType) throws BeansException {
        if (!beans.containsKey(name)) {
            throw new NoSuchBeanDefinitionException(name);
        }

        if (requiredType != null && !requiredType.isAssignableFrom(beans.get(name).getClass())) {
            throw new NoSuchBeanDefinitionException(name);
        }

        return (T)beans.get(name);
    }

    public <T> T getBean(Class<T> tClass) throws BeansException {
        final Map<String, T> map = getBeansOfType(tClass);
        if (map.isEmpty()) {
            throw new NoSuchBeanDefinitionException(tClass, "No bean found for type: "  + tClass.getName());
        }
        return map.values().iterator().next();
    }

    public Object getBean(String name, Object... args) throws BeansException {
        return getBean(name);
    }

    @Override
    public Object getProperty(String name) {
        if (beans.containsKey(name)) {
            return beans.get(name);
        }

        return super.getProperty(name);
    }

    public boolean containsBean(String name) {
        return beans.containsKey(name);
    }

    public boolean isSingleton(String name) {
        throw new UnsupportedOperationException("Method not supported by implementation");
    }

    public boolean isPrototype(String s) {
        throw new UnsupportedOperationException("Method not supported by implementation");
    }

    @Override
    boolean isTypeMatch(String name, ResolvableType typeToMatch) throws NoSuchBeanDefinitionException {
        return false
    }

    @SuppressWarnings("rawtypes")
    public boolean isTypeMatch(String name, Class aClass) {
        return aClass.isInstance(getBean(name));
    }

    public Class getType(String name) throws NoSuchBeanDefinitionException {
        if (!beans.containsKey(name)) {
            throw new NoSuchBeanDefinitionException(name);
        }

        return beans.get(name).getClass();
    }

    public String[] getAliases(String name) {
        return [] as String[]
    }

    public BeanFactory getParentBeanFactory() {
        return null;
    }

    public String getMessage(String code, Object[] args, String defaultMessage, Locale locale) {
        MessageSource messageSource = (MessageSource)getBean("messageSource");
        if (messageSource == null) {
            throw new BeanCreationException("No bean [messageSource] found in MockApplicationContext");
        }
        return messageSource.getMessage(code, args, defaultMessage, locale);
    }

    public String getMessage(String code, Object[] args, Locale locale) throws NoSuchMessageException {
        MessageSource messageSource = (MessageSource)getBean("messageSource");
        if (messageSource == null) {
            throw new BeanCreationException("No bean [messageSource] found in MockApplicationContext");
        }
        return messageSource.getMessage(code, args, locale);
    }

    public String getMessage(MessageSourceResolvable resolvable, Locale locale) throws NoSuchMessageException {
        MessageSource messageSource = (MessageSource)getBean("messageSource");
        if (messageSource == null) {
            throw new BeanCreationException("No bean [messageSource] found in MockApplicationContext");
        }
        return messageSource.getMessage(resolvable, locale);
    }

    public Resource[] getResources(String locationPattern) throws IOException {
        if (locationPattern.startsWith("classpath:") || locationPattern.startsWith("file:")) {
            throw new UnsupportedOperationException("Location patterns 'classpath:' and 'file:' not supported by implementation");
        }

        locationPattern = GrailsStringUtils.trimStart(locationPattern, "/"); // starting with "**/" is OK
        List<Resource> result = new ArrayList<Resource>();
        for (Resource res : resources) {
            String path = res instanceof ClassPathResource ? ((ClassPathResource) res).getPath() : res.getDescription();
            if (pathMatcher.match(locationPattern, path)) {
                result.add(res);
            }
        }
        return result.toArray(new Resource[0]);
    }

    public Resource getResource(String location) {
        for (Resource mockResource : resources) {
            if (pathMatcher.match(mockResource.getDescription(), GrailsStringUtils.trimStart(location, "/"))) {
                return mockResource;
            }
        }
        // Check for ignored resources and return null instead of a classpath resource in that case.
        for (String resourceLocation : ignoredClassLocations) {
            if (pathMatcher.match(
                    GrailsStringUtils.trimStart(location, "/"),
                    GrailsStringUtils.trimStart(resourceLocation, "/"))) {
                return null;
            }
        }

        return new ClassPathResource(location);
    }

    public boolean containsLocalBean(String arg0) {
        throw new UnsupportedOperationException("Method not supported by implementation");
    }

    public AutowireCapableBeanFactory getAutowireCapableBeanFactory() throws IllegalStateException {
        return new DefaultListableBeanFactory();
    }
    public ClassLoader getClassLoader() {
        return getClass().getClassLoader();
    }

    public ServletContext getServletContext() {
        return servletContext;
    }

    public void setServletContext(ServletContext servletContext) {
        this.servletContext = servletContext;
    }

    public Environment getEnvironment() {
        return new StandardServletEnvironment();
    }

    public class MockResource extends AbstractResource {

        private String contents = "";
        private String location;

        public MockResource(String location) {
            this.location = location;
        }

        public MockResource(String location, String contents) {
            this(location);
            this.contents = contents;
        }

        @Override
        public boolean exists() {
            return true;
        }

        public String getDescription() {
            return location;
        }

        public InputStream getInputStream() throws IOException {
            return new ByteArrayInputStream(contents.getBytes("UTF-8"));
        }
    }

    @Override
    public <T> T getBean(Class<T> requiredType, Object... args)
            throws BeansException {
        return getBean(requiredType);
    }
}
