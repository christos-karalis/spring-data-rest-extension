package net.sourceforge.extension.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.querydsl.core.types.EntityPath;
import com.querydsl.core.types.dsl.PathBuilder;
import com.querydsl.core.types.dsl.PathBuilderFactory;
import com.querydsl.jpa.JPAQueryBase;
import com.querydsl.jpa.JPQLQuery;
import net.sourceforge.extension.AdvancedSearch;
import net.sourceforge.extension.PredicateUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.jpa.repository.support.Querydsl;
import org.springframework.data.repository.support.Repositories;
import org.springframework.data.rest.webmvc.PersistentEntityResourceAssembler;
import org.springframework.data.rest.webmvc.RepositoryRestController;
import org.springframework.data.rest.webmvc.RootResourceInformation;
import org.springframework.data.rest.webmvc.support.DefaultedPageable;
import org.springframework.data.web.PagedResourcesAssembler;
import org.springframework.hateoas.Resources;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import javax.persistence.EntityManager;
import javax.xml.bind.JAXBException;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Created by christos.karalis on 6/4/2017.
 */
@RepositoryRestController
public class AdvancedSearchController {

    private static final String BASE_MAPPING = "/{repository}";

    @Autowired
    private Repositories repositories;

    @Autowired
    private PagedResourcesAssembler<Object> pagedResourcesAssembler;

    @Autowired
    private EntityManager em;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * It manages advanced queries by providing an {@link AdvancedSearch}
     * that could accept nested objects that are joined to the main table that is retrieved by {repository}.
     * It supports Strings that are checked if they are contained on the record (not exact match)
     *
     * @param resourceInformation is resolved by spring argument resolver and its information is retrieved by {repository}
     * @param advancedSearch      the object that is matches against the database
     * @param pageable            information that holds page, size that are retrieved as parameters
     * @param assembler           is resolved by spring argument resolver and its information is retrieved by {repository}
     * @return
     * @throws ClassNotFoundException
     * @throws NoSuchMethodException
     * @throws IllegalAccessException
     * @throws InvocationTargetException
     * @throws InstantiationException
     * @throws NoSuchFieldException
     */
    @RequestMapping(value = BASE_MAPPING + "/search/advanced", method = RequestMethod.POST)
    public ResponseEntity<Resources<?>> advancedSearch(
            final RootResourceInformation resourceInformation,
            @RequestBody AdvancedSearch advancedSearch,
            DefaultedPageable pageable,
            PersistentEntityResourceAssembler assembler) {

        Assert.notNull(advancedSearch.getOperator(), "Please check your operators that have valid values OR/AND");
        Assert.notNull(advancedSearch.getOperands(), "You will need one operand at least to be included");

        PredicateUtils predicateUtils = new PredicateUtils(repositories, em, resourceInformation.getDomainType());
        JPAQueryBase query = predicateUtils.generateQuery(advancedSearch);
        Querydsl querydsl = predicateUtils.generateQuerydsl();

        JPQLQuery countQuery = query.clone();
        JPQLQuery paginatedQuery = querydsl.applyPagination(pageable.getPageable(), query);

        Long total = countQuery.fetchCount();
        List<Object> content = total > pageable.getPageable().getOffset() ? paginatedQuery.fetch() : Collections.emptyList();

        Page<Object> page = new PageImpl<>(content, pageable.getPageable(), total);

        Resources<?> pagedResources = pagedResourcesAssembler.toResource(page, assembler);
        return new ResponseEntity<Resources<?>>(pagedResources, HttpStatus.OK);
    }

}
