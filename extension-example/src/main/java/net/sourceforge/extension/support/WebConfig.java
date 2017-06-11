package net.sourceforge.extension.support;

import org.springframework.beans.factory.BeanInitializationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.repository.support.Repositories;
import org.springframework.data.rest.webmvc.RepositoryRestController;
import org.springframework.data.rest.webmvc.config.RepositoryRestMvcConfiguration;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.annotation.TransactionManagementConfigurer;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.sql.DataSource;
import java.io.IOException;
import java.util.Properties;

/**
 * Created by christos.karalis on 6/7/2017.
 */
@Configuration
@Import(RepositoryRestMvcConfiguration.class)
@EnableWebMvc
@ComponentScan(basePackages = { "net.sourceforge.extension.controller", "net.sourceforge.extension.service" },
        includeFilters = @ComponentScan.Filter({RepositoryRestController.class, Service.class, Component.class, ControllerAdvice.class}), useDefaultFilters = false)
@EnableJpaRepositories(basePackages = "net.sourceforge.extension.repository")
@EnableTransactionManagement()
public class WebConfig extends RepositoryRestMvcConfiguration {

    @Autowired
    private ApplicationContext applicationContext;

    private Properties hibernateProperties() throws IOException {
        Properties properties = new Properties();
        properties.setProperty("hibernate.hbm2ddl.auto", "update");
//        properties.setProperty("hibernate.hbm2ddl.import_files", "addresses.sql");
        properties.setProperty("hibernate.show_sql", "true");
        return properties;
    }

    @Bean
    public DataSource dataSource() {
        EmbeddedDatabaseBuilder builder = new EmbeddedDatabaseBuilder();
        return builder.setType(EmbeddedDatabaseType.HSQL).build();
    }

    @Bean
    public EntityManagerFactory entityManagerFactory() {

        HibernateJpaVendorAdapter vendorAdapter = new HibernateJpaVendorAdapter();
        vendorAdapter.setShowSql(true);
        vendorAdapter.setGenerateDdl(false);

        LocalContainerEntityManagerFactoryBean factory = new LocalContainerEntityManagerFactoryBean();
        factory.setJpaVendorAdapter(vendorAdapter);
        factory.setPackagesToScan("net.sourceforge.extension.entity");
        factory.setDataSource(dataSource());

        Properties jpaProperties = null;
        try {
            jpaProperties = hibernateProperties();
        } catch (IOException e) {
            throw new BeanInitializationException("failed to read properties", e);
        }
        factory.setJpaProperties(jpaProperties);
        factory.afterPropertiesSet();

        return factory.getObject();
    }

    @Bean
    public Repositories repositories() {
        return new Repositories(applicationContext);
    }

    @Bean
    public PlatformTransactionManager transactionManager() {
        JpaTransactionManager txManager = new JpaTransactionManager();
        txManager.setEntityManagerFactory(entityManagerFactory());
        return txManager;
    }
}
