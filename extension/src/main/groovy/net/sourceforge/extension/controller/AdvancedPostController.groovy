package net.sourceforge.extension.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.querydsl.core.types.EntityPath
import net.sourceforge.extension.Association
import net.sourceforge.extension.DefaultPathGenerator
import net.sourceforge.extension.QueryLanguagePathGenerator
import net.sourceforge.extension.service.UpdateService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.rest.core.config.RepositoryRestConfiguration
import org.springframework.data.rest.webmvc.ControllerUtils
import org.springframework.data.rest.webmvc.PersistentEntityResource
import org.springframework.data.rest.webmvc.PersistentEntityResourceAssembler
import org.springframework.data.rest.webmvc.RepositoryRestController
import org.springframework.data.rest.webmvc.RootResourceInformation
import org.springframework.data.rest.webmvc.support.BackendId
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
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.ResponseBody
import org.springframework.web.multipart.MultipartHttpServletRequest

import javax.persistence.EntityManager
import javax.servlet.http.HttpServletRequest
import java.lang.reflect.InvocationTargetException

/**
 * Created by User on 6/11/2017.
 */
@RepositoryRestController
class AdvancedPostController {
    //    private static final Logger LOGGER = Logger.getLogger(AdvancedPostController.class);

    private static final String BASE_MAPPING = "/{repository}";

    @Autowired
    private RepositoryRestConfiguration config;

    @Autowired
    private UpdateService updateService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private EntityManager em;

    private static QueryLanguagePathGenerator pathGenerator = new DefaultPathGenerator();

    private static MultipartFileProcessor multipartFileProcessor = new DefaultMultipartFileProcessor();

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
        return updateService.internalPostController(resourceInformation, payload, repository, assembler, domainType, obj);
    }

    /**
     * It is an extension of the provided Spring Data Rest {@link org.springframework.data.rest.webmvc.RepositoryEntityController}
     * POST method that it is possible to save in one transaction {@link javax.persistence.OneToMany} additionally its associated
     * entities on that the entity. The main difference is from the standard controller the payload is expected as a multipart
     * file with name 'payload'. Extra multipart files are expected as properties of the associated entity. Its multipart
     * file name is matched to the name of the property
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
        Map<String, Object> payload = objectMapper.readValue(((MultipartHttpServletRequest) request).getFile("payload").getInputStream(), Map.class);
        Assert.notNull(payload.get(repository), "empty.save");
        Class<?> domainType = resourceInformation.getDomainType();

        Object entity = objectMapper.convertValue(payload.get(repository), domainType);

        ((MultipartHttpServletRequest) request).getFileMap().findAll { !it.key.equals("payload") }
                .each { key, value ->
            Object fileEntity = multipartFileProcessor.persistMultipartFile(value);
            entity.hasProperty(key).setProperty(entity, fileEntity)
            if (!(fileEntity instanceof byte[])) {
                em.persist(fileEntity);
            }
        }
        return updateService.internalPostController(resourceInformation, payload, repository, assembler, domainType, entity);
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

        Object info = updateService.updateObject(resourceInformation, payload, repository, id, domainPredicateClass, entityPath)

        updateService.internalPatchCollection(resourceInformation, payload, repository, entityPath, id, info)

        HttpHeaders headers = new HttpHeaders();
        addLocationHeader(headers, assembler, info);

        PersistentEntityResource resource = null;
        return ControllerUtils.toResponseEntity(HttpStatus.CREATED, headers, resource);
    }


}


