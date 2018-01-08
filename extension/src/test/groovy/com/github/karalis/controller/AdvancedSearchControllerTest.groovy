package com.github.karalis.controller

import com.querydsl.jpa.JPAQueryBase
import groovy.json.JsonSlurper
import groovy.transform.PackageScope
import net.sourceforge.extension.AdvancedSearch
import net.sourceforge.extension.PredicateUtils
import net.sourceforge.extension.controller.Config
import net.sourceforge.extension.entity.Address
import net.sourceforge.extension.entity.Application
import net.sourceforge.extension.entity.Service
import org.junit.Assert
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.repository.support.Repositories
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.jdbc.Sql
import org.springframework.test.context.jdbc.SqlGroup
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner

import javax.persistence.EntityManager
import java.lang.reflect.InvocationTargetException
import java.text.ParseException
import java.text.SimpleDateFormat

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = Config.class)
class AdvancedSearchControllerTest {

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private Repositories repositories;

    def slurper;

    @Before
    void init() {
        slurper = new JsonSlurper()
    }

    @Test
    @SqlGroup([
        @Sql(scripts = "classpath:addresses.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD),
        @Sql(scripts = "classpath:drop.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
    ])
    @Ignore
    public void testOneToOneJoin() throws NoSuchMethodException, NoSuchFieldException, InstantiationException, IllegalAccessException, InvocationTargetException {
        PredicateUtils predicateUtils = new PredicateUtils(repositories, entityManager, Address.class);
        AdvancedSearch advancedSearch = new AdvancedSearch();
        List fetch = checkAlacantProvinceMatches(predicateUtils, advancedSearch);
        Assert.assertEquals(fetch.size(), 3);
        advancedSearch = new AdvancedSearch();
        fetch = checkAlicanteNameMatches(predicateUtils, advancedSearch);
        Assert.assertEquals(fetch.size(), 2);
        advancedSearch = new AdvancedSearch();
        fetch = checkByCityIdMatches(predicateUtils, advancedSearch);
        Assert.assertEquals(fetch.size(), 2);
        advancedSearch = new AdvancedSearch();
        fetch = checkByMultipleCityIdMatches(predicateUtils, advancedSearch);
        Assert.assertEquals(fetch.size(), 3);
        advancedSearch = new AdvancedSearch();
        fetch = checkByMultipleCityIdLongMatches(predicateUtils, advancedSearch);
        Assert.assertEquals(fetch.size(), 3);
        advancedSearch = new AdvancedSearch();
        fetch = checkByMultipleCityIdNullMatches(predicateUtils, advancedSearch);
        Assert.assertEquals(fetch.size(), 3);
        advancedSearch = new AdvancedSearch();
        fetch = checkByMultipleCityIdNotNullMatches(predicateUtils, advancedSearch);
        Assert.assertEquals(fetch.size(), 4);

        advancedSearch = new AdvancedSearch();
        fetch = checkByMultipleCityNullMatches(predicateUtils, advancedSearch);
        Assert.assertEquals(fetch.size(), 1);

        advancedSearch = new AdvancedSearch();
        fetch = checkZeroMatches(predicateUtils, advancedSearch);
        Assert.assertEquals(fetch.size(), 0);
        advancedSearch = new AdvancedSearch();
        fetch = checkAnyMatches(predicateUtils, advancedSearch);
        Assert.assertEquals(fetch.size(), 4);
    }

    @Test
    @SqlGroup([
        @Sql(scripts = "classpath:addresses.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD),
        @Sql(scripts = "classpath:drop.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
    ])
    public void testNull() throws NoSuchMethodException, NoSuchFieldException, InstantiationException, IllegalAccessException, InvocationTargetException {
        PredicateUtils predicateUtils = new PredicateUtils(repositories, entityManager, Address.class);
        AdvancedSearch advancedSearch = new AdvancedSearch();
        List fetch = checkByMultipleCityNullMatches(predicateUtils, advancedSearch);
        Assert.assertEquals(fetch.size(), 1);
    }

    @Test
    @SqlGroup([
            @Sql(scripts = "classpath:addresses.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD),
            @Sql(scripts = "classpath:drop.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
    ])
    void testCollection() throws NoSuchMethodException, NoSuchFieldException, InstantiationException, IllegalAccessException, InvocationTargetException {
        PredicateUtils predicateUtils = new PredicateUtils(repositories, entityManager, Address.class)
        AdvancedSearch advancedSearch = new AdvancedSearch(
                operands: slurper.parseText('{"city": { "name" : ["ALICANTE","ELCHE"] }}')
        )
        JPAQueryBase jpaQueryBase = predicateUtils.generateQuery(advancedSearch);
        List fetch = jpaQueryBase.fetch();
        Assert.assertEquals(fetch.size(), 3);
    }

    @Test
    @SqlGroup([
        @Sql(scripts = "classpath:addresses.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD),
        @Sql(scripts = "classpath:drop.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
    ])
    public void testActive() throws NoSuchMethodException, NoSuchFieldException, InstantiationException, IllegalAccessException, InvocationTargetException {
        PredicateUtils predicateUtils = new PredicateUtils(repositories, entityManager, Service.class);
        AdvancedSearch advancedSearch = new AdvancedSearch(
                operands: slurper.parseText(
                        '''{"current_date_between" : { 
                                "date_from" : "activeFrom",
                                "date_to" : "activeTo"
                        } }'''
                )
        )
        JPAQueryBase jpaQueryBase = predicateUtils.generateQuery(advancedSearch);
        List fetch = jpaQueryBase.fetch();
        Assert.assertEquals(fetch.size(), 1);

    }

    @Test
    @SqlGroup([
        @Sql(scripts = "classpath:addresses.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD),
        @Sql(scripts = "classpath:drop.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
    ])
    public void testDeepNested() {
        PredicateUtils predicateUtils = new PredicateUtils(repositories, entityManager, Application.class);
        AdvancedSearch advancedSearch = new AdvancedSearch(
                operands: slurper.parseText('{ "address" : { "city": { "name" : "ALICANTE" }}}')
        )
        JPAQueryBase jpaQueryBase = predicateUtils.generateQuery(advancedSearch);
        List fetch = jpaQueryBase.fetch();
        Assert.assertEquals(fetch.size(), 1);

    }


    @Test
    @SqlGroup([
        @Sql(scripts = "classpath:addresses.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD),
        @Sql(scripts = "classpath:drop.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
    ])
    public void testBoolean() throws NoSuchMethodException, NoSuchFieldException, InstantiationException, IllegalAccessException, InvocationTargetException, ParseException {
        PredicateUtils predicateUtils = new PredicateUtils(repositories, entityManager, Application.class);
        AdvancedSearch advancedSearch = new AdvancedSearch(operands: slurper.parseText('{"address" : {"verified":true}}'))
        JPAQueryBase jpaQueryBase = predicateUtils.generateQuery(advancedSearch);
        List fetch = jpaQueryBase.fetch();
        Assert.assertEquals(fetch.size(), 1);

        advancedSearch = new AdvancedSearch(
                operands: slurper.parseText('{"address" : {"verified":"true"}}')
        )
        jpaQueryBase = predicateUtils.generateQuery(advancedSearch);
        fetch = jpaQueryBase.fetch();
        Assert.assertEquals(fetch.size(), 1);

        advancedSearch = new AdvancedSearch(
                operands: slurper.parseText('{"address" : {"verified":"isNull"}}')
        )
        jpaQueryBase = predicateUtils.generateQuery(advancedSearch);
        fetch = jpaQueryBase.fetch();
        Assert.assertEquals(fetch.size(), 1);

        advancedSearch = new AdvancedSearch(
                operands: slurper.parseText('{"address" : {"verified":false}}')
        )
        jpaQueryBase = predicateUtils.generateQuery(advancedSearch);
        fetch = jpaQueryBase.fetch();
        Assert.assertEquals(fetch.size(), 0);

        advancedSearch = new AdvancedSearch(
                operands: slurper.parseText('{"address" : {"verified":"false"}}')
        )
        jpaQueryBase = predicateUtils.generateQuery(advancedSearch);
        fetch = jpaQueryBase.fetch();
        Assert.assertEquals(fetch.size(), 0);

        advancedSearch = new AdvancedSearch(
                operands: slurper.parseText('{"address" : {"verified": null }}')
        )
        jpaQueryBase = predicateUtils.generateQuery(advancedSearch);
        fetch = jpaQueryBase.fetch();
        Assert.assertEquals(fetch.size(), 2);

        advancedSearch = new AdvancedSearch(
                operands: slurper.parseText('{"address" : {"verified": "" }}')
        )
        jpaQueryBase = predicateUtils.generateQuery(advancedSearch);
        fetch = jpaQueryBase.fetch();
        Assert.assertEquals(fetch.size(), 2);
    }

    @Test
    @SqlGroup([
        @Sql(scripts = "classpath:addresses.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD),
        @Sql(scripts = "classpath:drop.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
    ])
    public void testOrInMap() throws NoSuchMethodException, NoSuchFieldException, InstantiationException, IllegalAccessException, InvocationTargetException, ParseException {
        PredicateUtils predicateUtils = new PredicateUtils(repositories, entityManager, Address.class);
        AdvancedSearch advancedSearch = new AdvancedSearch(operands: slurper.parseText(
                '{"or" : [{"city":"1"}, {"city":"4"}]}'
        ));
        JPAQueryBase jpaQueryBase = predicateUtils.generateQuery(advancedSearch);
        List fetch = jpaQueryBase.fetch();
        Assert.assertEquals(fetch.size(), 3);

        advancedSearch = new AdvancedSearch(operands: slurper.parseText(
                '{"or" : [{"city":"1", "street" : "CARRER RUPERTO CHAPI"}]}'
        ));
        jpaQueryBase = predicateUtils.generateQuery(advancedSearch);
        fetch = jpaQueryBase.fetch();
        Assert.assertEquals(fetch.size(), 3);

        advancedSearch = new AdvancedSearch(operands: slurper.parseText(
                '{"or" : [{"city":"1", "street" : "CARRER RUPERTO CHAPI "}]}'
        ));
        jpaQueryBase = predicateUtils.generateQuery(advancedSearch);
        fetch = jpaQueryBase.fetch();
        Assert.assertEquals(fetch.size(), 3);

        advancedSearch = new AdvancedSearch(operands: slurper.parseText(
                '{"or" : {"city":"isNull"}}'
        ));
        jpaQueryBase = predicateUtils.generateQuery(advancedSearch);
        fetch = jpaQueryBase.fetch();
        Assert.assertEquals(fetch.size(), 1);


        advancedSearch = new AdvancedSearch(operands: slurper.parseText(
                '{"city":"isNull"}'
        ));
        jpaQueryBase = predicateUtils.generateQuery(advancedSearch);
        fetch = jpaQueryBase.fetch();
        Assert.assertEquals(fetch.size(), 1);
    }

    @Test
    @SqlGroup([
        @Sql(scripts = "classpath:addresses.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD),
        @Sql(scripts = "classpath:drop.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
    ])
    public void testDateMatch() throws NoSuchMethodException, NoSuchFieldException, InstantiationException, IllegalAccessException, InvocationTargetException, ParseException {
        PredicateUtils predicateUtils = new PredicateUtils(repositories, entityManager, Application.class);
        AdvancedSearch advancedSearch = new AdvancedSearch();

        HashMap<Object, Object> value = new HashMap<>();
        value.put("from", new Date().getTime());
        advancedSearch.getOperands().put("submissionDate", value);
        JPAQueryBase jpaQueryBase = predicateUtils.generateQuery(advancedSearch);
        List fetch = jpaQueryBase.fetch();
        Assert.assertEquals(fetch.size(), 0);

        value = new HashMap<>();
        value.put("to", new Date().getTime());
        advancedSearch.getOperands().put("submissionDate", value);
        jpaQueryBase = predicateUtils.generateQuery(advancedSearch);
        fetch = jpaQueryBase.fetch();
        Assert.assertEquals(fetch.size(), 2);

        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("dd-MM-yyyy");

        value = new HashMap<>();
        value.put("from", simpleDateFormat.parse("01-05-2016").getTime());
        value.put("to", simpleDateFormat.parse("31-05-2016").getTime());
        advancedSearch.getOperands().put("submissionDate", value);
        jpaQueryBase = predicateUtils.generateQuery(advancedSearch);
        fetch = jpaQueryBase.fetch();
        Assert.assertEquals(fetch.size(), 1);

        value = new HashMap<>();
        value.put("from", simpleDateFormat.parse("06-05-2016").getTime());
        value.put("to", simpleDateFormat.parse("31-05-2016").getTime());
        advancedSearch.getOperands().put("submissionDate", value);
        jpaQueryBase = predicateUtils.generateQuery(advancedSearch);
        fetch = jpaQueryBase.fetch();
        Assert.assertEquals(fetch.size(), 0);

        value = new HashMap<>();
        value.put("from", simpleDateFormat.parse("01-05-2016"));
        value.put("to", simpleDateFormat.parse("31-05-2016"));
        advancedSearch.getOperands().put("submissionDate", value);
        jpaQueryBase = predicateUtils.generateQuery(advancedSearch);
        fetch = jpaQueryBase.fetch();
        Assert.assertEquals(fetch.size(), 1);

        value = new HashMap<>();
        value.put("from", simpleDateFormat.parse("06-05-2016"));
        value.put("to", simpleDateFormat.parse("31-05-2016"));
        advancedSearch.getOperands().put("submissionDate", value);
        jpaQueryBase = predicateUtils.generateQuery(advancedSearch);
        fetch = jpaQueryBase.fetch();
        Assert.assertEquals(fetch.size(), 0);

        value = new HashMap<>();
        advancedSearch.getOperands().put("submissionDate", value);
        jpaQueryBase = predicateUtils.generateQuery(advancedSearch);
        fetch = jpaQueryBase.fetch();
        Assert.assertEquals(fetch.size(), 2);

    }

    @Test
    @SqlGroup([
        @Sql(scripts = "classpath:addresses.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD),
        @Sql(scripts = "classpath:drop.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
    ])
    public void testJoinCollection() throws NoSuchMethodException, NoSuchFieldException, InstantiationException, IllegalAccessException, InvocationTargetException, ParseException {
        PredicateUtils predicateUtils = new PredicateUtils(repositories, entityManager, Application.class);
        AdvancedSearch advancedSearch = new AdvancedSearch(operands: slurper.parseText('{"orderLines" : { "service" : { "name" : "whatever" }}}'));

        JPAQueryBase jpaQueryBase = predicateUtils.generateQuery(advancedSearch);
        List fetch = jpaQueryBase.fetch();
        Assert.assertEquals(fetch.size(), 0);
    }

    @Test
    @SqlGroup([
        @Sql(scripts = "classpath:addresses.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD),
        @Sql(scripts = "classpath:drop.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
    ])
    public void testBigInteger() throws NoSuchMethodException, NoSuchFieldException, InstantiationException, IllegalAccessException, InvocationTargetException, ParseException {
        PredicateUtils predicateUtils = new PredicateUtils(repositories, entityManager, Application.class);

        check(predicateUtils, "quantityBigInteger", 12);
        check(predicateUtils, "quantityBigInteger", "12");
        check(predicateUtils, "quantityBigInteger", 12.2);
        check(predicateUtils, "quantityBigInteger", "12.2");
        check(predicateUtils, "quantityBigInteger", 12l);
        check(predicateUtils, "quantity", 12);
        check(predicateUtils, "quantity", "12");
        check(predicateUtils, "quantity", 12.2);
        check(predicateUtils, "quantity", "12.2");
        check(predicateUtils, "quantity", 12l);

        AdvancedSearch advancedSearch = new AdvancedSearch();

        advancedSearch.getOperands().put("quantityBigInteger", "10");
        JPAQueryBase jpaQueryBase = predicateUtils.generateQuery(advancedSearch);
        List fetch = jpaQueryBase.fetch();
        Assert.assertEquals(fetch.size(), 1);

        advancedSearch = new AdvancedSearch();
        advancedSearch.getOperands().put("quantity", "10");
        jpaQueryBase = predicateUtils.generateQuery(advancedSearch);
        fetch = jpaQueryBase.fetch();
        Assert.assertEquals(fetch.size(), 1);

    }

    private void check(PredicateUtils predicateUtils, String property, Object checkedValue) {
        AdvancedSearch advancedSearch = new AdvancedSearch( operands: slurper.parseText('{ "'+property+'" : { "from" : '+checkedValue+' } }'))
        JPAQueryBase jpaQueryBase = predicateUtils.generateQuery(advancedSearch)
        List fetch = jpaQueryBase.fetch()
        Assert.assertEquals(fetch.size(), 1)

        advancedSearch = new AdvancedSearch( operands: slurper.parseText('{ "'+property+'" : { "to" : '+checkedValue+' } }'.toString()))
        jpaQueryBase = predicateUtils.generateQuery(advancedSearch)
        fetch = jpaQueryBase.fetch()
        Assert.assertEquals(fetch.size(), 1)
    }

    @Test
    @SqlGroup([
        @Sql(scripts = "classpath:addresses.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD),
        @Sql(scripts = "classpath:drop.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
    ])
    public void testIgnoreNull() throws NoSuchMethodException, NoSuchFieldException, InstantiationException, IllegalAccessException, InvocationTargetException, ParseException {
        PredicateUtils predicateUtils = new PredicateUtils(repositories, entityManager, Application.class);
        AdvancedSearch advancedSearch = new AdvancedSearch();
        advancedSearch.getOperands().put("address", null);
        JPAQueryBase jpaQueryBase = predicateUtils.generateQuery(advancedSearch);
        List fetch = jpaQueryBase.fetch();


        advancedSearch = new AdvancedSearch();
        advancedSearch.getOperands().put("quantity", null);
        jpaQueryBase = predicateUtils.generateQuery(advancedSearch);
        fetch = jpaQueryBase.fetch();


    }

    @Test
    @SqlGroup([
        @Sql(scripts = "classpath:addresses.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD),
        @Sql(scripts = "classpath:drop.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
    ])
    public void testNumberMatch() throws NoSuchMethodException, NoSuchFieldException, InstantiationException, IllegalAccessException, InvocationTargetException, ParseException {
        PredicateUtils predicateUtils = new PredicateUtils(repositories, entityManager, Application.class);
        AdvancedSearch advancedSearch = new AdvancedSearch();

        HashMap<Object, Object> value = new HashMap<>();
        value.put("from", 12);
        advancedSearch.getOperands().put("quantity", value);
        JPAQueryBase jpaQueryBase = predicateUtils.generateQuery(advancedSearch);
        List fetch = jpaQueryBase.fetch();
        Assert.assertEquals(fetch.size(), 1);

        value = new HashMap<>();
        value.put("to", 12);
        advancedSearch.getOperands().put("quantity", value);
        jpaQueryBase = predicateUtils.generateQuery(advancedSearch);
        fetch = jpaQueryBase.fetch();
        Assert.assertEquals(fetch.size(), 1);

        advancedSearch.getOperands().put("quantity", "10");
        jpaQueryBase = predicateUtils.generateQuery(advancedSearch);
        fetch = jpaQueryBase.fetch();
        Assert.assertEquals(fetch.size(), 1);

        advancedSearch.getOperands().put("quantity", 14);
        jpaQueryBase = predicateUtils.generateQuery(advancedSearch);
        fetch = jpaQueryBase.fetch();
        Assert.assertEquals(fetch.size(), 1);
    }


    private List checkAlacantProvinceMatches(PredicateUtils predicateUtils, AdvancedSearch advancedSearch) {
        HashMap<Object, Object> value = new HashMap<>();
        value.put("province", "ALACANT");
        advancedSearch.getOperands().put("city", value);
        JPAQueryBase jpaQueryBase = predicateUtils.generateQuery(advancedSearch);
        return jpaQueryBase.fetch();
    }

    private List checkZeroMatches(PredicateUtils predicateUtils, AdvancedSearch advancedSearch) {
        HashMap<Object, Object> value = new HashMap<>();
        value.put("province", "ALACANT");
        value.put("name", "MADRID");
        advancedSearch.getOperands().put("city", value);
        JPAQueryBase jpaQueryBase = predicateUtils.generateQuery(advancedSearch);
        return jpaQueryBase.fetch();
    }

    private List checkAnyMatches(PredicateUtils predicateUtils, AdvancedSearch advancedSearch) {
        advancedSearch.setOperator(AdvancedSearch.Operator.OR);
        HashMap<Object, Object> value = new HashMap<>();
        value.put("province", "ALACANT");
        value.put("name", "MADRID");
        advancedSearch.getOperands().put("city", value);
        JPAQueryBase jpaQueryBase = predicateUtils.generateQuery(advancedSearch);
        return jpaQueryBase.fetch();
    }

    private List checkAlicanteNameMatches(PredicateUtils predicateUtils, AdvancedSearch advancedSearch) {
        HashMap<Object, Object> value = new HashMap<>();
        value.put("name", "ALICANTE");
        advancedSearch.getOperands().put("city", value);
        JPAQueryBase jpaQueryBase = predicateUtils.generateQuery(advancedSearch);
        return jpaQueryBase.fetch();
    }

    private List checkByCityIdMatches(PredicateUtils predicateUtils, AdvancedSearch advancedSearch) {
        advancedSearch.getOperands().put("city", "1");
        JPAQueryBase jpaQueryBase = predicateUtils.generateQuery(advancedSearch);
        return jpaQueryBase.fetch();
    }


    private List checkByMultipleCityIdMatches(PredicateUtils predicateUtils, AdvancedSearch advancedSearch) {
        advancedSearch.getOperands().put("city", Arrays.asList(["1", "4"]));
        JPAQueryBase jpaQueryBase = predicateUtils.generateQuery(advancedSearch);
        return jpaQueryBase.fetch();
    }

    private List checkByMultipleCityIdLongMatches(PredicateUtils predicateUtils, AdvancedSearch advancedSearch) {
        advancedSearch.getOperands().put("city", Arrays.asList([1l, 4l]));
        JPAQueryBase jpaQueryBase = predicateUtils.generateQuery(advancedSearch);
        return jpaQueryBase.fetch();
    }

    private List checkByMultipleCityIdNullMatches(PredicateUtils predicateUtils, AdvancedSearch advancedSearch) {
        advancedSearch.getOperands().put("city", Arrays.asList(["1", "isNull"]));
        JPAQueryBase jpaQueryBase = predicateUtils.generateQuery(advancedSearch);
        return jpaQueryBase.fetch();
    }

    private List checkByMultipleCityIdNotNullMatches(PredicateUtils predicateUtils, AdvancedSearch advancedSearch) {
        advancedSearch.getOperands().put("city", Arrays.asList(["1", "isNotNull"]));
        JPAQueryBase jpaQueryBase = predicateUtils.generateQuery(advancedSearch);
        return jpaQueryBase.fetch();
    }

    private List checkByMultipleCityNullMatches(PredicateUtils predicateUtils, AdvancedSearch advancedSearch) {
        advancedSearch.getOperands().put("isNull", "city");
        JPAQueryBase jpaQueryBase = predicateUtils.generateQuery(advancedSearch);
        return jpaQueryBase.fetch();
    }
}
