package com.example.interviewprep.config;

import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.ws.config.annotation.EnableWs;
import org.springframework.ws.config.annotation.WsConfigurerAdapter;
import org.springframework.ws.transport.http.MessageDispatcherServlet;
import org.springframework.ws.wsdl.wsdl11.DefaultWsdl11Definition;
import org.springframework.xml.xsd.SimpleXsdSchema;
import org.springframework.xml.xsd.XsdSchema;

/**
 * SOAP Web Services configuration for legacy system integration.
 * 
 * Provides SOAP endpoints for external systems that require XML-based communication.
 * Demonstrates protocol mediation between SOAP and REST architectures.
 * 
 * Key patterns implemented:
 * - Contract-first web service development
 * - WSDL generation from XSD schemas
 * - Message transformation between protocols
 * - Legacy system adapter patterns
 */
//@EnableWs
//@Configuration
public class WebServiceConfig /* extends WsConfigurerAdapter */ {

    /**
     * Message dispatcher servlet for handling SOAP requests.
     */
    @Bean
    public ServletRegistrationBean<MessageDispatcherServlet> messageDispatcherServlet(ApplicationContext applicationContext) {
        MessageDispatcherServlet servlet = new MessageDispatcherServlet();
        servlet.setApplicationContext(applicationContext);
        servlet.setTransformWsdlLocations(true);
        return new ServletRegistrationBean<>(servlet, "/ws/*");
    }

    /**
     * WSDL definition for NAV case management service.
     * Provides SOAP interface for external case management operations.
     */
    @Bean(name = "caseManagement")
    public DefaultWsdl11Definition defaultWsdl11Definition(XsdSchema caseManagementSchema) {
        DefaultWsdl11Definition wsdl11Definition = new DefaultWsdl11Definition();
        wsdl11Definition.setPortTypeName("CaseManagementPort");
        wsdl11Definition.setLocationUri("/ws");
        wsdl11Definition.setTargetNamespace("http://nav.no/integration/casemanagement");
        wsdl11Definition.setSchema(caseManagementSchema);
        return wsdl11Definition;
    }

    /**
     * XSD schema for case management operations.
     */
    @Bean
    public XsdSchema caseManagementSchema() {
        return new SimpleXsdSchema(new ClassPathResource("schemas/casemanagement.xsd"));
    }

    /**
     * WSDL definition for user management service.
     * Provides SOAP interface for user data operations.
     */
    @Bean(name = "userManagement")
    public DefaultWsdl11Definition userManagementWsdl11Definition(XsdSchema userManagementSchema) {
        DefaultWsdl11Definition wsdl11Definition = new DefaultWsdl11Definition();
        wsdl11Definition.setPortTypeName("UserManagementPort");
        wsdl11Definition.setLocationUri("/ws");
        wsdl11Definition.setTargetNamespace("http://nav.no/integration/usermanagement");
        wsdl11Definition.setSchema(userManagementSchema);
        return wsdl11Definition;
    }

    /**
     * XSD schema for user management operations.
     */
    @Bean
    public XsdSchema userManagementSchema() {
        return new SimpleXsdSchema(new ClassPathResource("schemas/usermanagement.xsd"));
    }

    /**
     * WSDL definition for external system notifications.
     * Provides callback interface for external system integrations.
     */
    @Bean(name = "notifications")
    public DefaultWsdl11Definition notificationWsdl11Definition(XsdSchema notificationSchema) {
        DefaultWsdl11Definition wsdl11Definition = new DefaultWsdl11Definition();
        wsdl11Definition.setPortTypeName("NotificationPort");
        wsdl11Definition.setLocationUri("/ws");
        wsdl11Definition.setTargetNamespace("http://nav.no/integration/notifications");
        wsdl11Definition.setSchema(notificationSchema);
        return wsdl11Definition;
    }

    /**
     * XSD schema for notification operations.
     */
    @Bean
    public XsdSchema notificationSchema() {
        return new SimpleXsdSchema(new ClassPathResource("schemas/notifications.xsd"));
    }
}