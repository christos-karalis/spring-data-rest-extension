package net.sourceforge.extension.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.querydsl.core.types.EntityPath;
import com.querydsl.core.types.Path;
import com.querydsl.core.types.dsl.SimpleExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.querydsl.jpa.impl.JPAUpdateClause;
import net.sourceforge.extension.Association;
import net.sourceforge.extension.controller.AdvancedPostController;
import org.codehaus.groovy.runtime.metaclass.MetaClassRegistryImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.repository.support.Repositories;
import org.springframework.data.repository.support.RepositoryInvokerFactory;
import org.springframework.data.rest.core.config.RepositoryRestConfiguration;
import org.springframework.data.rest.core.event.AfterCreateEvent;
import org.springframework.data.rest.core.event.BeforeCreateEvent;
import org.springframework.data.rest.webmvc.ControllerUtils;
import org.springframework.data.rest.webmvc.PersistentEntityResource;
import org.springframework.data.rest.webmvc.PersistentEntityResourceAssembler;
import org.springframework.data.rest.webmvc.RootResourceInformation;
import org.springframework.data.rest.webmvc.support.BackendId;
import org.springframework.format.support.DefaultFormattingConversionService;
import org.springframework.hateoas.ResourceSupport;
import org.springframework.hateoas.UriTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;

import javax.persistence.EntityManager;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;

@Service
public class AdvancedSearchService {


    @Autowired
    private Repositories repositories;

    @Autowired
    private ApplicationEventPublisher publisher;

    @Autowired
    private RepositoryRestConfiguration config;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RepositoryInvokerFactory invokerFactory;

    @Autowired
    private EntityManager em;

    @Autowired
    private DefaultFormattingConversionService conversionService;

    private Map<String, Association> associationMap = new HashMap<>();

    /**
     * Sets the location header pointing to the resource representing the given instance. Will make sure we properly
     * expand the URI template potentially created as self link.
     *
     * @param headers must not be {@literal null}.
     * @param assembler must not be {@literal null}.
     * @param source must not be {@literal null}.
     */
    private void addLocationHeader(HttpHeaders headers, PersistentEntityResourceAssembler assembler, Object source) {
        String selfLink = assembler.getSelfLinkFor(source).getHref();
        headers.setLocation(new UriTemplate(selfLink).expand());
    }

    @Transactional
    public Object updateObject(RootResourceInformation resourceInformation, @RequestBody Map<String, Object> payload, @PathVariable("repository") String repository, @BackendId Serializable id, Class domainPredicateClass, EntityPath entityPath) throws NoSuchFieldException, IllegalAccessException, URISyntaxException {
        Field declaredFieldId = domainPredicateClass
                .getDeclaredField(resourceInformation.getPersistentEntity().getIdProperty().getName());
        JPAQueryFactory jpaQueryFactory = new JPAQueryFactory(em);
        SimpleExpression tSimpleExpression = (SimpleExpression<Path>) declaredFieldId.get(entityPath);

        JPAUpdateClause update = jpaQueryFactory.update(entityPath).where(tSimpleExpression.eq(conversionService.convert(id, resourceInformation.getPersistentEntity().getIdProperty().getType())));

        payload.get(repository).each { key, value ->
            Class type = resourceInformation.getDomainType().getMetaClass().getMetaProperty(key).getType()
            Path property = entityPath.hasProperty(key).getProperty(entityPath)
            Object val = (property instanceof EntityPath && value instanceof String)?
                    conversionService.convert(((String) value).startsWith("http")?new URI((String) value):value, type):value
            update.set(property, val)

        }
        long executed = update.execute()
        return resourceInformation.getInvoker().invokeFindOne(id)
    }


    @Transactional
    public ResponseEntity<ResourceSupport> internalPostController(RootResourceInformation resourceInformation, @RequestBody Map<String, Object> payload,
                                                           @PathVariable("repository") String repository, PersistentEntityResourceAssembler assembler,
                                                           Class<?> domainType, Object obj) throws IllegalAccessException, InvocationTargetException {

        em.persist(obj);

        payload.findAll { key,value->(!key.equals(repository) && !key.equals("files"))}
                .each { key, value ->
            Association association = associationMap.get(key);
            if (association == null) {
                association = AdvancedPostController.getAssociation(domainType, key);
                associationMap.put(key, association);
            }
            value.each {
                Object newEntity = objectMapper.convertValue(it, association.getDomainType())
                association.getDomainType().getMetaClass()
                        .setProperty(newEntity, association.getParentEntityProperty(), obj)
                publisher.publishEvent(new BeforeCreateEvent(newEntity))
                invokerFactory.getInvokerFor((Class<Object>) association.getDomainType()).invokeSave(newEntity)
                publisher.publishEvent(new AfterCreateEvent(newEntity))
            }
        }

        HttpHeaders headers = new HttpHeaders()
        addLocationHeader(headers, assembler, obj)

        PersistentEntityResource resource = false ? assembler.toFullResource(obj) : null
        return ControllerUtils.toResponseEntity(HttpStatus.CREATED, headers, resource)
    }
}