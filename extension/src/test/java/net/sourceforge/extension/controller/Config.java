package net.sourceforge.extension.controller;

import org.hibernate.dialect.HSQLDialect;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.repository.support.Repositories;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.sql.DataSource;
import java.io.IOException;
import java.util.Properties;

/**
 * Created by christos.karalis on 5/28/2017.
 */
@Configuration
@EnableJpaRepositories(basePackages = "net.sourceforge.extension.repository")
public class Config {

    @Autowired
    ApplicationContext applicationContext;

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
    public EntityManagerFactory entityManagerFactory() throws IOException {

        HibernateJpaVendorAdapter vendorAdapter = new HibernateJpaVendorAdapter();
        vendorAdapter.setShowSql(true);
        vendorAdapter.setGenerateDdl(false);

        LocalContainerEntityManagerFactoryBean factory = new LocalContainerEntityManagerFactoryBean();
        factory.setJpaVendorAdapter(vendorAdapter);
        factory.setPackagesToScan("net.sourceforge.extension.entity");
        factory.setDataSource(dataSource());

        Properties jpaProperties = hibernateProperties();
        factory.setJpaProperties(jpaProperties);
        factory.afterPropertiesSet();

        return factory.getObject();
    }

    @Bean
    public EntityManager entityManager() throws IOException{
        return entityManagerFactory().createEntityManager();
    }

    @Bean
    public Repositories repositories() {
        return new Repositories(applicationContext);
    }

}
