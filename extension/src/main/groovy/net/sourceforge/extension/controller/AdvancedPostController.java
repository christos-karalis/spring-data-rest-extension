package net.sourceforge.extension.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.querydsl.core.types.EntityPath;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.JPAQueryBase;
import com.querydsl.jpa.impl.JPAQuery;
import groovy.lang.GroovySystem;
import net.sourceforge.extension.Association;
import net.sourceforge.extension.DefaultPathGenerator;
import net.sourceforge.extension.PredicateUtils;
import net.sourceforge.extension.QueryLanguagePathGenerator;
import net.sourceforge.extension.service.AdvancedSearchService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.support.Repositories;
import org.springframework.data.repository.support.RepositoryInvokerFactory;
import org.springframework.data.rest.core.config.RepositoryRestConfiguration;
import org.springframework.data.rest.core.event.AfterCreateEvent;
import org.springframework.data.rest.core.event.AfterSaveEvent;
import org.springframework.data.rest.core.event.BeforeCreateEvent;
import org.springframework.data.rest.core.event.BeforeSaveEvent;
import org.springframework.data.rest.webmvc.*;
import org.springframework.data.rest.webmvc.support.BackendId;
import org.springframework.hateoas.ResourceSupport;
import org.springframework.hateoas.UriTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.Assert;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.support.DefaultMultipartHttpServletRequest;

import javax.persistence.EntityManager;
import javax.persistence.OneToMany;
import javax.servlet.http.HttpServletRequest;
import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.io.IOException;
import java.io.Serializable;
import java.lang.reflect.*;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by christos.karalis on 6/5/2017.
 */
@RepositoryRestController
public class AdvancedPostController {

//    private static final Logger LOGGER = Logger.getLogger(AdvancedPostController.class);

    private static final String BASE_MAPPING = "/{repository}";
    private static final String HTTP = "http";
    private static final String ACCEPT_HEADER = "Accept";

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

    private static QueryLanguagePathGenerator pathGenerator = new DefaultPathGenerator();

    private static MultipartFileProcessor multipartFileProcessor = new DefaultMultipartFileProcessor();

    private Map<String, Association> associationMap = new HashMap<>();

    public static Association getAssociation(Class inspectedClass, String inspectedField) {
        try {
            Field field = inspectedClass.getDeclaredField(inspectedField);
            if (getMappedBy(field) != null) {
                Class domainType = (Class) ((ParameterizedType) field.getGenericType()).getActualTypeArguments()[0];
                Class predicateClass = pathGenerator.getPredicateClass(domainType);
                String givenField = null;
                for (Field field1 : predicateClass.getDeclaredFields()) {
                    if (EntityPath.class.isAssignableFrom(field1.getType()) && !field1.getName().equals(getMappedBy(field))) {
                        givenField = field1.getName();
                    }
                }
                return new Association(domainType, getMappedBy(field), givenField);
            }

            BeanInfo beanInfo = Introspector.getBeanInfo(inspectedClass);
            PropertyDescriptor[] props = beanInfo.getPropertyDescriptors();

            for (PropertyDescriptor prop : props) {
                Method readMethod = prop.getReadMethod();
                if (inspectedField.equals(prop.getName())) {
                    if (getMappedBy(readMethod) != null) {
                        Class domainType = (Class) ((ParameterizedType) readMethod.getGenericReturnType()).getActualTypeArguments()[0];
                        Class predicateClass = pathGenerator.getPredicateClass(domainType);
                        String givenField = null;
                        for (Field field1 : predicateClass.getDeclaredFields()) {
                            if (EntityPath.class.isAssignableFrom(field1.getType()) && !field1.getName().equals(getMappedBy(readMethod))) {
                                givenField = field1.getName();
                            }
                        }
                        return new Association(domainType,
                                getMappedBy(readMethod), givenField);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }

    public static String getMappedBy(AnnotatedElement annotatedElement) {
        if (annotatedElement.isAnnotationPresent(OneToMany.class)) {
            return annotatedElement.getAnnotation(OneToMany.class).mappedBy();
        }
        return null;
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
     * @param payload             should be on the following form e.g.
     *                            { person : { name : "George" }, addresses : { street: "street 124" } }
     * @param repository
     * @param assembler
     * @return
     * @throws HttpRequestMethodNotSupportedException
     * @throws InvocationTargetException
     * @throws IllegalAccessException
     */
    @ResponseBody
    @RequestMapping(value = BASE_MAPPING + "/advanced", method = RequestMethod.POST, consumes = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_JSON_UTF8_VALUE})
    public ResponseEntity<ResourceSupport> postCollectionResource(RootResourceInformation resourceInformation,
                                                                  HttpServletRequest request,
                                                                  @RequestBody Map<String, Object> payload,
                                                                  @PathVariable("repository") String repository,
                                                                  PersistentEntityResourceAssembler assembler,
                                                                  @RequestHeader(value = ACCEPT_HEADER, required = false) String acceptHeader)
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
    @RequestMapping(value = BASE_MAPPING + "/advanced", method = RequestMethod.POST, consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ResourceSupport> postMultipartCollectionResource(RootResourceInformation resourceInformation,
                                                                           HttpServletRequest request,
                                                                           @PathVariable("repository") String repository,
                                                                           PersistentEntityResourceAssembler assembler,
                                                                           @RequestHeader(value = ACCEPT_HEADER, required = false) String acceptHeader)
            throws HttpRequestMethodNotSupportedException, InvocationTargetException, IllegalAccessException, IOException, ClassNotFoundException, ReflectiveOperationException {
        Map<String, Object> payload = objectMapper.readValue(((DefaultMultipartHttpServletRequest) request).getFile("payload").getInputStream(), Map.class);
        Assert.notNull(payload.get(repository), "empty.save");
        Class<?> domainType = resourceInformation.getDomainType();

        Object entity = objectMapper.convertValue(payload.get(repository), domainType);

        for (Map.Entry<String, MultipartFile> entry : ((DefaultMultipartHttpServletRequest) request).getFileMap().entrySet()) {
            if (!entry.getKey().equals("payload")) {
                Object fileEntity = multipartFileProcessor.persistMultipartFile(entry.getValue());
                GroovySystem.getMetaClassRegistry().getMetaClass(domainType)
                        .setProperty(entity, entry.getKey(), fileEntity);
                if (!(fileEntity instanceof byte[]))  {
                    em.persist(fileEntity);
                }
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
     *                { person : { name : "George" }, addresses : ["address" : "http://server/context/rest/address/id" ] }
     * @param repository
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
    @RequestMapping(value = BASE_MAPPING + "/advanced/{id}", method = RequestMethod.PATCH)
    public ResponseEntity<ResourceSupport> patchCollectionResource(RootResourceInformation resourceInformation,
                                                                   @RequestBody Map<String, Object> payload,
                                                                   @PathVariable("repository") String repository,
                                                                   PersistentEntityResourceAssembler assembler,
                                                                   @BackendId Serializable id)
            throws ReflectiveOperationException, URISyntaxException {
        Class domainPredicateClass = pathGenerator.getPredicateClass(resourceInformation.getDomainType());
        EntityPath entityPath = (EntityPath) domainPredicateClass.getConstructor(String.class).newInstance("entity");

        Object info = advancedSearchService.updateObject(resourceInformation, payload, repository, id, domainPredicateClass, entityPath);

        PredicateUtils predicateUtils = new PredicateUtils(repositories, em, resourceInformation.getDomainType());
        for (Map.Entry<String, Object> entry : payload.entrySet()) {
            if (!entry.getKey().equals(repository)) {
                Association association = associationMap.get(entry.getKey());
                if (association==null) {
                    association = getAssociation(resourceInformation.getDomainType(), entry.getKey());
                    associationMap.put(entry.getKey(), association);
                }
                Assert.notNull(association, "Not supported association");
                if (entry.getValue() instanceof ArrayList) {
                    Class predicateClass = pathGenerator.getPredicateClass(association.getDomainType());
                    Assert.notNull(predicateClass, "Not supported search for this entity");
                    String name = association.getDomainType().getSimpleName();

                    EntityPath entityPathAssociation = (EntityPath) predicateClass.getConstructor(String.class)
                            .newInstance(name.substring(0, 1).toLowerCase() + name.substring(1));
                    Assert.isInstanceOf(EntityPath.class, entityPath, "Please check that corresponds to entity");


                    BooleanExpression expression = predicateUtils.matchToEntityId(entityPathAssociation,
                            GroovySystem.getMetaClassRegistry().getMetaClass(predicateClass)
                                    .getMetaProperty(association.getParentEntityProperty()), id.toString());
                    if (association.getBackwardAssociation()!=null) {
                        expression = expression.or(predicateUtils.matchToEntityId(entityPathAssociation,
                                GroovySystem.getMetaClassRegistry().getMetaClass(predicateClass)
                                        .getMetaProperty(association.getBackwardAssociation()
                                                .getParentEntityProperty())
                                , id.toString()));
                    }
                    JPAQueryBase query = (JPAQueryBase) new JPAQuery(em).from(entityPathAssociation).where(expression);
                    List<Object> existingObjects = query.fetch();

                    addSelectedEntities(info, entry, association, existingObjects);
                    removeUnselectedEntities(entry, association, existingObjects);
                }
            }
        }

        HttpHeaders headers = new HttpHeaders();
        addLocationHeader(headers, assembler, info);

        PersistentEntityResource resource = null;
        return ControllerUtils.toResponseEntity(HttpStatus.CREATED, headers, resource);
    }

    private void removeUnselectedEntities(Map.Entry<String, Object> entry, Association association, List<Object> existingObjects) throws IllegalAccessException, InvocationTargetException, NoSuchMethodException {
        for (Object existing : existingObjects) {
            boolean found = false;
            for (Object object : (ArrayList) entry.getValue()) {
                Object newEntity = objectMapper.convertValue(object, association.getDomainType());
                found = found?found:checkIfEquals(association, existing, newEntity);
            }
            if (!found) {
                if (association.isManyToMany()) {
                    CrudRepository crudRepository = (CrudRepository) repositories.getRepositoryFor((Class<Object>) association.getDomainType());
                    Object property = GroovySystem.getMetaClassRegistry().getMetaClass(existing.getClass())
                        .getProperty(existing, association.getMatchingEntity());
                    crudRepository.delete(existing);
                } else {
                    GroovySystem.getMetaClassRegistry().getMetaClass(existing.getClass())
                            .setProperty(existing, association.getParentEntityProperty(), null);
                    publisher.publishEvent(new BeforeSaveEvent(existing));
                    invokerFactory.getInvokerFor((Class<Object>) association.getDomainType()).invokeSave(existing);
                    publisher.publishEvent(new AfterSaveEvent(existing));
                }
            }
        }
    }

    private void addSelectedEntities(Object info, Map.Entry<String, Object> entry, Association association, List<Object> existingObjects) throws IllegalAccessException, InvocationTargetException, NoSuchMethodException {
        for (Object object : (ArrayList) entry.getValue()) {
            Object newEntity = objectMapper.convertValue(object, association.getDomainType());
            boolean found = false;
            for (Object existing : existingObjects) {
                boolean it = checkIfEquals(association, existing, newEntity);
                if (it) {
                    for (Map.Entry<String, Object> propertyEntry : ((Map<String, Object>) object).entrySet() ) {
                        Object property = GroovySystem.getMetaClassRegistry().getMetaClass(newEntity.getClass())
                                .getProperty(newEntity, propertyEntry.getKey());
                        if (!association.isManyToMany() && property instanceof String) {
                            property = ( ((String) property).contains(HTTP)
                                    && ((String) property).lastIndexOf('/') >= 0)?((String) property).substring(((String) property).lastIndexOf('/') + 1):((String) property);
                        }
                        GroovySystem.getMetaClassRegistry().getMetaClass(existing.getClass())
                                .setProperty(existing, propertyEntry.getKey(), property);
                    }
                    publisher.publishEvent(new BeforeSaveEvent(existing));
                    invokerFactory.getInvokerFor(association.getDomainType()).invokeSave(existing);
                    publisher.publishEvent(new AfterSaveEvent(existing));
                }
                found = found?found:it;
            }
            if (!found) {
                if (association.isManyToMany()) {
                    GroovySystem.getMetaClassRegistry().getMetaClass(newEntity.getClass())
                            .setProperty(newEntity, association.getParentEntityProperty(), info);
                } else {
                    //If it is not many to one is an already created object
                    Object property = GroovySystem.getMetaClassRegistry().getMetaClass(newEntity.getClass())
                            .getProperty(newEntity, association.getMatchingEntity());
                    if (!association.isManyToMany() && property instanceof String) {
                        property = ( ((String) property).contains(HTTP)
                                && ((String) property).lastIndexOf('/') >= 0)?((String) property).substring(((String) property).lastIndexOf('/') + 1):((String) property);

                    }
                    newEntity = invokerFactory.getInvokerFor((Class<Object>) association.getDomainType()).invokeFindOne((String) property);
                    GroovySystem.getMetaClassRegistry().getMetaClass(newEntity.getClass())
                            .setProperty(newEntity, association.getParentEntityProperty(), info);
                }
                if (association.isManyToMany()) {
                    publisher.publishEvent(new BeforeCreateEvent(newEntity));
                } else {
                    //before save event should be called instead
                    publisher.publishEvent(new BeforeSaveEvent(newEntity));
                }
                invokerFactory.getInvokerFor((Class<Object>) association.getDomainType()).invokeSave(newEntity);
                if (association.isManyToMany()) {
                    publisher.publishEvent(new AfterCreateEvent(newEntity));
                } else {
                    publisher.publishEvent(new AfterSaveEvent(newEntity));
                }

            }
        }
    }

    private boolean checkIfEquals(Association association, Object existing, Object newEntity) throws IllegalAccessException, InvocationTargetException, NoSuchMethodException {
        Object property = GroovySystem.getMetaClassRegistry().getMetaClass(newEntity.getClass())
                .getProperty(newEntity, association.getMatchingEntity());
        if (!association.isManyToMany() && property instanceof String) {
            property = ( ((String) property).contains(HTTP)
                    && ((String) property).lastIndexOf('/') >= 0)?((String) property).substring(((String) property).lastIndexOf('/') + 1):((String) property);

        }
        return GroovySystem.getMetaClassRegistry().getMetaClass(existing.getClass())
                .getProperty(existing, association.getMatchingEntity())
                .equals(property);
    }


}
