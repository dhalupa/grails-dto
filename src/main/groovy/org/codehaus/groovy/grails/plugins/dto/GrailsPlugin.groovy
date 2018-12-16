package org.codehaus.groovy.grails.plugins.dto

import grails.plugins.*
import grails.plugins.dto.DTO
import org.dozer.spring.DozerBeanMapperFactoryBean
import org.grails.core.artefact.DomainClassArtefactHandler
import org.springframework.context.ApplicationContext

class GrailsPlugin extends Plugin {

    // the version or versions of Grails the plugin is designed for
    def grailsVersion = "3.1.15 > *"
    // resources that are excluded from plugin packaging
    def pluginExcludes = [
            "grails-app/views/error.gsp"
    ]

    // TODO Fill in these fields
    def title = "" // Headline display name of the plugin
    def author = "Your name"
    def authorEmail = ""
    def description = '''\
Brief summary/description of the plugin.
'''

    // URL to the plugin's documentation
    def documentation = "http://grails.org/plugin/"

    // Extra (optional) plugin metadata

    // License: one of 'APACHE', 'GPL2', 'GPL3'
//    def license = "APACHE"

    // Details of company behind the plugin (if there is one)
//    def organization = [ name: "My Company", url: "http://www.my-company.com/" ]

    // Any additional developers beyond the author specified above.
//    def developers = [ [ name: "Joe Bloggs", email: "joe@bloggs.net" ]]

    // Location of the plugin's issue tracker.
//    def issueManagement = [ system: "JIRA", url: "http://jira.grails.org/browse/GPMYPLUGIN" ]

    // Online location of the plugin's browseable source code.
//    def scm = [ url: "http://svn.codehaus.org/grails-plugins/" ]

    Closure doWithSpring() {
        { ->
            // Create the DTO generator bean.
            if (grailsApplication.config.getProperty('grails.generate.indent', Boolean)) {
                dtoGenerator(DefaultGrailsDtoGenerator, true, application.config.grails.generate.indent)
            } else {
                dtoGenerator(DefaultGrailsDtoGenerator)
            }

            dozerMapper(DozerBeanMapperFactoryBean) {
                if (application.config.dto.mapping.files) {
                    mappingFiles = application.config.dto.mapping.files
                }
            }
        }
    }


    void doWithDynamicMethods() {
        // Add "as DTO" and toDTO() to domain classes.
        for (dc in grailsApplication.getArtefacts(DomainClassArtefactHandler.TYPE)) {
            addDtoMethods(dc.metaClass, grailsApplication.mainContext)
        }

        // Add the toDTO(Class) method to collections.
        Collection.metaClass.toDTO = { obj ->
            // Find out what class the target collection should contain.
            def containedClass = obj instanceof Class ? obj : obj.getClass()

            // Next create a collection of the appropriate type.
            def clazz = delegate.getClass()
            if (SortedSet.isAssignableFrom(clazz)) {
                obj = new TreeSet()
            } else if (Set.isAssignableFrom(clazz)) {
                obj = new HashSet()
            } else {
                obj = new ArrayList(delegate.size())
            }

            // Finally, add the individual DTOs to the new collection.
            final mapper = grailsApplication.mainContext.getBean("dozerMapper")
            delegate.each { obj << mapper.map(it, containedClass) }
            return obj
        }
    }

    private addDtoMethods(final MetaClass mc, final ApplicationContext ctx) {
        // First add the "as DTO".
        final originalAsType = mc.getMetaMethod("asType", [Class] as Object[])
        mc.asType = { Class clazz ->
            if (DTO == clazz) {
                // Do the DTO conversion.
                return mapDomainInstance(ctx, delegate)
            } else {
                // Use the original asType implementation.
                return originalAsType.invoke(delegate, [clazz] as Object[])
            }
        }

        // Then the toDTO() method if not already defined
        if (mc.getMetaMethod('toDTO', [] as Object[]) == null) {
            mc.toDTO = { ->
                return mapDomainInstance(ctx, delegate)
            }
        }

        if (mc.getMetaMethod('toDTO', [Class] as Object[]) == null) {
            mc.toDTO = { Class clazz ->
                // Convert the domain instance to a DTO.
                def mapper = ctx.getBean("dozerMapper")
                return mapper.map(delegate, clazz)
            }
        }
    }

    /**
     * Uses the Dozer mapper to map a domain instance to its corresponding
     * DTO.
     * @param ctx The Spring application context containing the Dozer
     * mapper.
     * @param obj The domain instance to map.
     * @return The DTO corresponding to the given domain instance.
     */
    private mapDomainInstance(ctx, obj) {
        // Get the appropriate DTO class for this domain instance.
        def dtoClassName = obj.getClass().name + "DTO"
        def dtoClass = obj.getClass().classLoader.loadClass(dtoClassName)

        // Now convert the domain instance to a DTO.
        def mapper = ctx.getBean("dozerMapper")
        return mapper.map(obj, dtoClass)
    }

}
