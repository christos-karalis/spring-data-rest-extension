package net.sourceforge.extension.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.querydsl.core.types.EntityPath;
import com.querydsl.core.types.Path
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.SimpleExpression
import com.querydsl.jpa.JPAQueryBase
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.querydsl.jpa.impl.JPAUpdateClause;
import net.sourceforge.extension.Association
import net.sourceforge.extension.DefaultPathGenerator
import net.sourceforge.extension.PredicateUtils
import net.sourceforge.extension.QueryLanguagePathGenerator;
import net.sourceforge.extension.controller.AdvancedPostController
import net.sourceforge.extension.controller.DefaultMultipartFileProcessor
import net.sourceforge.extension.controller.MultipartFileProcessor;
import org.codehaus.groovy.runtime.metaclass.MetaClassRegistryImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.support.Repositories;
import org.springframework.data.repository.support.RepositoryInvokerFactory;
import org.springframework.data.rest.core.config.RepositoryRestConfiguration;
import org.springframework.data.rest.core.event.AfterCreateEvent
import org.springframework.data.rest.core.event.AfterDeleteEvent
import org.springframework.data.rest.core.event.AfterSaveEvent;
import org.springframework.data.rest.core.event.BeforeCreateEvent
import org.springframework.data.rest.core.event.BeforeDeleteEvent
import org.springframework.data.rest.core.event.BeforeSaveEvent;
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
import org.springframework.transaction.annotation.Transactional
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;

import javax.persistence.EntityManager
import javax.persistence.OneToMany
import java.beans.BeanInfo
import java.beans.Introspector
import java.beans.PropertyDescriptor
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.lang.reflect.ParameterizedType;

@Service
public class UpdateService {


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

    private static QueryLanguagePathGenerator pathGenerator = new DefaultPathGenerator();

    private static MultipartFileProcessor multipartFileProcessor = new DefaultMultipartFileProcessor();

    private Map<String, Association> associationMap = new HashMap<>();

    public static Association getAssociation(Class inspectedClass, String inspectedField) {
        Field field = inspectedClass.getDeclaredField(inspectedField);
        def mappedBy = getMappedBy(field)
        if (mappedBy) {
            Class domainType = (Class) ((ParameterizedType) field.getGenericType()).getActualTypeArguments()[0];
            Class predicateClass = pathGenerator.getPredicateClass(domainType);
            def matchingField = predicateClass.getDeclaredFields().find {
                (it.getType() in EntityPath && !it.getName().equals(mappedBy) && !Modifier.isStatic(it.getModifiers()))
            }
            return new Association(domainType, mappedBy, matchingField.getName());
        }

        BeanInfo beanInfo = Introspector.getBeanInfo(inspectedClass);
        PropertyDescriptor[] props = beanInfo.getPropertyDescriptors();

        def found = props.find { prop -> inspectedField.equals(prop.getName()) }
        if (found) {
            Method readMethod = found.getReadMethod()
            mappedBy = getMappedBy(readMethod)
            if (mappedBy) {
                Class domainType = (Class) ((ParameterizedType) readMethod.getGenericReturnType()).getActualTypeArguments()[0]
                def matchingField = pathGenerator.getPredicateClass(domainType).getDeclaredFields().find {
                    it.getType() in EntityPath && !it.getName().equals(mappedBy) && !Modifier.isStatic(it.getModifiers())
                }
                return new Association(domainType, mappedBy, matchingField.getName())
            }
        }

        return null;
    }

    public static String getMappedBy(AnnotatedElement annotatedElement) {
        return annotatedElement.isAnnotationPresent(OneToMany.class)?
                annotatedElement.getAnnotation(OneToMany.class).mappedBy() : null;
    }


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

        if (payload.get(repository)) {
            payload.get(repository).each { key, value ->
                Class type = resourceInformation.getDomainType().getMetaClass().getMetaProperty(key).getType()
                Path property = entityPath.hasProperty(key).getProperty(entityPath)
                Object val = (property instanceof EntityPath && value instanceof String)?
                        conversionService.convert(((String) value).startsWith("http")?new URI((String) value):value, type):value
                update.set(property, val)

            }
            long executed = update.execute()
        }
        return resourceInformation.getInvoker().invokeFindOne(id)
    }

    @Transactional
    public void internalPatchCollection(RootResourceInformation resourceInformation, Map<String, Object> payload, String repository, EntityPath entityPath, id, info) {
        PredicateUtils predicateUtils = new PredicateUtils(repositories, em, resourceInformation.getDomainType())
        payload.findAll { !it.key.equals(repository) }.each { key, value ->
            Association association = associationMap.get(key)
            if (!association) {
                    association = getAssociation(resourceInformation.getDomainType(), key);
                associationMap.put(key, association);
            }
            Assert.notNull(association, "Not supported association");
            if (value in ArrayList) {
                Class predicateClass = pathGenerator.getPredicateClass(association.getDomainType());
                Assert.notNull(predicateClass, "Not supported search for this entity");
                String name = association.getDomainType().getSimpleName();

                EntityPath entityPathAssociation = (EntityPath) predicateClass
                        .newInstance(name.substring(0, 1).toLowerCase() + name.substring(1));
                Assert.isInstanceOf(EntityPath.class, entityPath, "Please check that corresponds to entity");

                BooleanExpression expression = predicateUtils.matchToEntityId(entityPathAssociation,
                        entityPathAssociation.hasProperty(association.getParentEntityProperty()), id.toString());
                if (association.getBackwardAssociation() != null) {
                    expression = expression.or(predicateUtils.matchToEntityId(entityPathAssociation,
                            entityPathAssociation.hasProperty(association.getBackwardAssociation()
                                    .getParentEntityProperty()), id.toString()));
                }
                JPAQueryBase query = (JPAQueryBase) new JPAQuery(em).from(entityPathAssociation).where(expression);
                List<Object> existingObjects = query.fetch();

                addSelectedEntities(info, value, association, existingObjects);
                removeUnselectedEntities(value, association, existingObjects);
            }
        }
    }

    private void removeUnselectedEntities(List<Object> newObjects, Association association, List<Object> existingObjects) {
        existingObjects.findAll { existing ->
            def find = newObjects.find {
                object -> checkIfEquals(association, existing, objectMapper.convertValue(object, association.getDomainType()))
            }
            if (!find) {
                if (association.isManyToMany()) {
                    CrudRepository crudRepository = (CrudRepository) repositories.getRepositoryFor((Class<Object>) association.getDomainType());
                    publisher.publishEvent(new BeforeDeleteEvent(existing))
                    crudRepository.delete(existing)
                    publisher.publishEvent(new AfterDeleteEvent(existing))
                } else {
                    existing.hasProperty(association.getParentEntityProperty()).setProperty(existing, null)
                    publisher.publishEvent(new BeforeSaveEvent(existing))
                    invokerFactory.getInvokerFor((Class<Object>) association.getDomainType()).invokeSave(existing)
                    publisher.publishEvent(new AfterSaveEvent(existing))
                }
            }

        }

    }

    private void addSelectedEntities(Object info, List<Object> newObjects, Association association, List<Object> existingObjects) {
        newObjects.each { object ->
            def newEntity = objectMapper.convertValue(object, association.getDomainType())
            def existing = existingObjects.find{ y -> checkIfEquals(association, y, newEntity) }
            if (existing) {
                ((Map<String, Object>) object).each { key, value ->
                    Object property = newEntity.hasProperty(key).getProperty(newEntity);
                    if (!association.isManyToMany() && property instanceof String) {
                        property = translateHttp(property)
                    }
                    existing.hasProperty(key).setProperty(existing, property)
                }
                publisher.publishEvent(new BeforeSaveEvent(existing));
                invokerFactory.getInvokerFor(association.getDomainType()).invokeSave(existing);
                publisher.publishEvent(new AfterSaveEvent(existing));
            } else {
                if (association.isManyToMany()) {
                    newEntity.hasProperty(association.getParentEntityProperty()).setProperty(newEntity, info);
                    publisher.publishEvent(new BeforeCreateEvent(newEntity))
                } else {
                    //If it is not many to one is an already created object
                    Object property = newEntity.hasProperty(association.getMatchingEntity()).getProperty(newEntity)
                    if (!association.isManyToMany()) {
                        newEntity = conversionService.convert((property instanceof String && ((String) property).startsWith(HTTP))
                                ? new URI((String) property) : property, (Class<Object>) association.getDomainType())
                    }
                    newEntity.hasProperty(association.getParentEntityProperty()).setProperty(newEntity, info)
                    //before save event should be called instead
                    publisher.publishEvent(new BeforeSaveEvent(newEntity))
                }
                invokerFactory.getInvokerFor((Class<Object>) association.getDomainType()).invokeSave(newEntity)
                publisher.publishEvent(association.isManyToMany()?
                        new AfterCreateEvent(newEntity):new AfterSaveEvent(newEntity))
            }
        }
    }

    private boolean checkIfEquals(Association association, Object existing, Object newEntity) throws IllegalAccessException, InvocationTargetException, NoSuchMethodException {
        Object property = newEntity.hasProperty(association.getMatchingEntity()).getProperty(newEntity)
        if (!association.isManyToMany() && property instanceof String) {
            property = translateHttp(property)
        }
        return existing.hasProperty(association.getMatchingEntity()).getProperty(existing)
                .equals(property);
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
                association = getAssociation(domainType, key);
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

    private String translateHttp(String property) {
        (property.contains(HTTP)
                && property.lastIndexOf('/') >= 0) ? property.substring(property.lastIndexOf('/') + 1) : property
    }

}