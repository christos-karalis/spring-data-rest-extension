package net.sourceforge.extension.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.querydsl.core.types.EntityPath
import com.querydsl.core.types.dsl.BooleanExpression
import com.querydsl.jpa.JPAQueryBase
import com.querydsl.jpa.impl.JPAQuery
import net.sourceforge.extension.Association
import net.sourceforge.extension.DefaultPathGenerator
import net.sourceforge.extension.PredicateUtils
import net.sourceforge.extension.QueryLanguagePathGenerator
import net.sourceforge.extension.service.AdvancedSearchService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.repository.CrudRepository
import org.springframework.data.repository.support.Repositories
import org.springframework.data.repository.support.RepositoryInvokerFactory
import org.springframework.data.rest.core.config.RepositoryRestConfiguration
import org.springframework.data.rest.core.event.AfterCreateEvent
import org.springframework.data.rest.core.event.AfterDeleteEvent
import org.springframework.data.rest.core.event.AfterSaveEvent
import org.springframework.data.rest.core.event.BeforeCreateEvent
import org.springframework.data.rest.core.event.BeforeDeleteEvent
import org.springframework.data.rest.core.event.BeforeSaveEvent
import org.springframework.data.rest.webmvc.ControllerUtils
import org.springframework.data.rest.webmvc.PersistentEntityResource
import org.springframework.data.rest.webmvc.PersistentEntityResourceAssembler
import org.springframework.data.rest.webmvc.RepositoryRestController
import org.springframework.data.rest.webmvc.RootResourceInformation
import org.springframework.data.rest.webmvc.support.BackendId
import org.springframework.format.support.DefaultFormattingConversionService
import org.springframework.hateoas.ResourceSupport
import org.springframework.hateoas.UriTemplate
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.util.Assert
import org.springframework.web.HttpRequestMethodNotSupportedException
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.ResponseBody
import org.springframework.web.multipart.support.DefaultMultipartHttpServletRequest

import javax.persistence.EntityManager
import javax.persistence.OneToMany
import javax.servlet.http.HttpServletRequest
import java.beans.BeanInfo
import java.beans.Introspector
import java.beans.PropertyDescriptor
import java.lang.reflect.AnnotatedElement
import java.lang.reflect.Field
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.lang.reflect.ParameterizedType

/**
 * Created by User on 6/11/2017.
 */
@RepositoryRestController
class AdvancedPostController {
    //    private static final Logger LOGGER = Logger.getLogger(AdvancedPostController.class);

    private static final String HTTP = "http";
    private static final String BASE_MAPPING = "/{repository}";

    @Autowired
    private Repositories repositories;

    @Autowired
    private ApplicationEventPublisher publisher;

    @Autowired
    private RepositoryRestConfiguration config;

    @Autowired
    private AdvancedSearchService advancedSearchService;

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

    /**
     * It is an extension of the provided Spring Data Rest {@link org.springframework.data.rest.webmvc.RepositoryEntityController}
     * POST method that it is possible to save in one transaction {@link javax.persistence.OneToMany} additionally its associated
     * entities on that the entity. It is based on the stored info of {@link Association}
     * on the application context
     *
     * @param resourceInformation
     * @param payload should be on the following form e.g.
     *{ person : { name : "George" }, addresses : { street: "street 124" }}* @param repository
     * @param assembler
     * @return
     * @throws org.springframework.web.HttpRequestMethodNotSupportedException
     * @throws java.lang.reflect.InvocationTargetException
     * @throws IllegalAccessException
     */
    @ResponseBody
    @RequestMapping(value = "/{repository}/advanced", method = RequestMethod.POST, consumes = [MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_JSON_UTF8_VALUE])
    public ResponseEntity<ResourceSupport> postCollectionResource(RootResourceInformation resourceInformation,
                                                                  @RequestBody Map<String, Object> payload,
                                                                  @PathVariable("repository") String repository,
                                                                  PersistentEntityResourceAssembler assembler)
            throws HttpRequestMethodNotSupportedException, InvocationTargetException, IllegalAccessException {
        Assert.notNull(payload.get(repository), "empty.save");
        Class<?> domainType = resourceInformation.getDomainType();

        Object obj = objectMapper.convertValue(payload.get(repository), domainType);
        return advancedSearchService.internalPostController(resourceInformation, payload, repository, assembler, domainType, obj);
    }

    /**
     * It is an extension of the provided Spring Data Rest {@link org.springframework.data.rest.webmvc.RepositoryEntityController}
     * POST method that it is possible to save in one transaction {@link javax.persistence.OneToMany} additionally its associated
     * entities on that the entity. It is based on the stored info of {@link Association}
     * on the application context
     *
     * @param resourceInformation
     * @param repository
     * @param assembler
     * @return
     * @throws HttpRequestMethodNotSupportedException
     * @throws InvocationTargetException
     * @throws IllegalAccessException
     */
    @ResponseBody
    @RequestMapping(value = "/{repository}/advanced", method = RequestMethod.POST, consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ResourceSupport> postMultipartCollectionResource(RootResourceInformation resourceInformation,
                                                                           HttpServletRequest request,
                                                                           @PathVariable("repository") String repository,
                                                                           PersistentEntityResourceAssembler assembler)
            throws HttpRequestMethodNotSupportedException, InvocationTargetException, IllegalAccessException, IOException, ClassNotFoundException, ReflectiveOperationException {
        Map<String, Object> payload = objectMapper.readValue(((DefaultMultipartHttpServletRequest) request).getFile("payload").getInputStream(), Map.class);
        Assert.notNull(payload.get(repository), "empty.save");
        Class<?> domainType = resourceInformation.getDomainType();

        Object entity = objectMapper.convertValue(payload.get(repository), domainType);

        ((DefaultMultipartHttpServletRequest) request).getFileMap().findAll {!key.equals("payload")}
                .each { key, value ->
            Object fileEntity = multipartFileProcessor.persistMultipartFile(value);
            entity.hasProperty(key).setProperty(entity, fileEntity)
            if (!(fileEntity instanceof byte[])) {
                em.persist(fileEntity);
            }
        }
        return advancedSearchService.internalPostController(resourceInformation, payload, repository, assembler, domainType, entity);
    }

    /**
     * It is an extension of the provided Spring Data Rest {@link org.springframework.data.rest.webmvc.RepositoryEntityController}
     * patch method that it is possible to save in one transaction {@link javax.persistence.OneToMany} associated entities
     * on that the entity. It fixes an issue identified on the original version that if an entity is secured on the repository
     * level and it updates the entity even it is not accessible
     * @param resourceInformation
     * @param payload should be on the following form e.g.
     *{ person : { name : "George" }, addresses : ["address" : "http://server/context/rest/address/id" ] }* @param repository
     * @param assembler
     * @param id the identifier of the entity persisted on db
     * @return
     * @throws HttpRequestMethodNotSupportedException
     * @throws InvocationTargetException
     * @throws IllegalAccessException
     * @throws NoSuchFieldException
     * @throws NoSuchMethodException
     * @throws InstantiationException
     * @throws ClassNotFoundException
     */
    @ResponseBody
    @RequestMapping(value = "/{repository}/advanced/{id}", method = RequestMethod.PATCH)
    public ResponseEntity<ResourceSupport> patchCollectionResource(RootResourceInformation resourceInformation,
                                                                   @RequestBody Map<String, Object> payload,
                                                                   @PathVariable("repository") String repository,
                                                                   PersistentEntityResourceAssembler assembler,
                                                                   @BackendId Serializable id)
            throws ReflectiveOperationException, URISyntaxException {
        Class domainPredicateClass = pathGenerator.getPredicateClass(resourceInformation.getDomainType())
        EntityPath entityPath = (EntityPath) domainPredicateClass.newInstance("entity")

        Object info = advancedSearchService.updateObject(resourceInformation, payload, repository, id, domainPredicateClass, entityPath)

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

        HttpHeaders headers = new HttpHeaders();
        addLocationHeader(headers, assembler, info);

        PersistentEntityResource resource = null;
        return ControllerUtils.toResponseEntity(HttpStatus.CREATED, headers, resource);
    }

    private void removeUnselectedEntities(List<Object> newObjects, Association association, List<Object> existingObjects) {
        existingObjects.findAll { existing -> newObjects.find { object -> !checkIfEquals(association, existing, objectMapper.convertValue(object, association.getDomainType())) } }
            .each {
                existing ->
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

    private String translateHttp(String property) {
        (property.contains(HTTP)
                && property.lastIndexOf('/') >= 0) ? property.substring(property.lastIndexOf('/') + 1) : property
    }


}


