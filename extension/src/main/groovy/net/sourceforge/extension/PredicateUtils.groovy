package net.sourceforge.extension

import org.apache.commons.lang3.BooleanUtils
import org.apache.commons.lang3.StringUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import com.querydsl.core.types.CollectionExpression
import com.querydsl.core.types.EntityPath
import com.querydsl.core.types.Path
import com.querydsl.core.types.dsl.BooleanExpression
import com.querydsl.core.types.dsl.BooleanPath
import com.querydsl.core.types.dsl.CollectionPathBase
import com.querydsl.core.types.dsl.DatePath
import com.querydsl.core.types.dsl.DateTimePath
import com.querydsl.core.types.dsl.EntityPathBase
import com.querydsl.core.types.dsl.NumberPath
import com.querydsl.core.types.dsl.PathBuilder
import com.querydsl.core.types.dsl.SimpleExpression
import com.querydsl.core.types.dsl.StringPath
import com.querydsl.core.types.dsl.TemporalExpression
import com.querydsl.jpa.JPAQueryBase
import com.querydsl.jpa.impl.JPAQuery

import org.springframework.data.jpa.repository.support.Querydsl
import org.springframework.data.mapping.PersistentEntity
import org.springframework.data.mapping.PersistentProperty
import org.springframework.data.repository.support.Repositories

import javax.persistence.EntityManager
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type

public class Constants {
    public static final String CURRENT_DATE_ACTIVE = "current_date_between";
    public static final String DATE_FROM = "date_from";
    public static final String DATE_TO = "date_to";
    public static final String RANGE_TO = "to";
    public static final String RANGE_FROM = "from";
    public static final String OR = "or";
    public static final String AND = "and";
    public static final String ISNULL = "isNull";
    public static final String ISNOTNULL = "isNotNull";
}


public class PredicateUtils {

    private static final Logger LOGGER = LoggerFactory.getLogger(PredicateUtils.class);

    private Repositories repositories;
    private EntityManager em;
    private QueryLanguagePathGenerator pathGenerator = new DefaultPathGenerator();
    private EntityPath entityPath;
    private Class idType;

    PredicateUtils(Repositories repositories, EntityManager em, Class domainType, QueryLanguagePathGenerator pathGenerator) {
        this.pathGenerator = pathGenerator
        this.repositories = repositories
        this.em = em
        (idType, entityPath) = initEntity(domainType)
    }

    PredicateUtils(Repositories repositories, EntityManager em, Class domainType) {
        this.repositories = repositories
        this.em = em
        (idType, entityPath) = initEntity(domainType)
    }

    void setPathGenerator(QueryLanguagePathGenerator pathGenerator) {
        this.pathGenerator = pathGenerator
    }

    Querydsl generateQuerydsl() {
        return new Querydsl(em, new PathBuilder<>(entityPath.getType(), entityPath.getMetadata()))
    }

    JPAQueryBase generateQuery(AdvancedSearch advancedSearch) {
        return internalGenerateQuery(idType, advancedSearch, entityPath)
    }

    private List initEntity(Class domainType) {
        PersistentEntity entity = repositories.getPersistentEntity(domainType)
        idType = ((PersistentProperty) entity.getIdProperty()).getType()

        String name = entity.getType().getSimpleName();
        def clazz = pathGenerator.getPredicateClass(domainType)
        entityPath = (EntityPath) clazz.newInstance("" + Character.toUpperCase(name.charAt(0)) + name.subSequence(1, name.length()));
        [idType, entityPath]
    }

    private JPAQueryBase internalGenerateQuery(Class<?> idActualType, AdvancedSearch advancedSearch, EntityPath entityPath) {
        List<BooleanExpression> expressions = new ArrayList<>()
        Map<Path, Path> joins = new LinkedHashMap<>()
        parseMapForJoins(entityPath, advancedSearch.getOperands(), joins, expressions)
        JPAQueryBase query = new JPAQuery(em).from(entityPath)
        joins.findAll { key, value ->
            if (value instanceof CollectionExpression) {
                query = query.leftJoin((CollectionExpression) value, key);
            } else if (value instanceof  EntityPath) {
                query = query.leftJoin((EntityPath) value, key);
            }
        }

        if (!expressions.isEmpty()) {
            BooleanExpression expression = expressions.get(0);
            if (advancedSearch.getOperator().equals(AdvancedSearch.Operator.AND)) {
                expressions.each { expression = expression.and(it) }
            } else {
                expressions.each { expression = expression.or(it) }
            }
            if (idActualType in Base64Convertable) {
                query.where(expression);
            } else {
                query.distinct().where(expression);
            }
        }
        return (joins.size()>0&&!(idActualType in Base64Convertable)) ? (JPAQueryBase) query.distinct().where() : query
    }

    private void addEntityJoinAndParse(EntityPath entityPath, Map<Path, Path> joins, List<BooleanExpression> expressions, Map nestedMap, MetaProperty property) {
        Class subEntityPathClass = property.getType()
        EntityPath subEntityPath = (EntityPath) subEntityPathClass.newInstance(property.getName())
        joins[subEntityPath] = (Path) property.getProperty(entityPath)
        parseMapForJoins(subEntityPath, nestedMap, joins, expressions)
    }

    private void addCollectionJoinAndParse(EntityPath entityPath, Map<Path, Path> joins, List<BooleanExpression> expressions, Map nestedMap, MetaProperty property) {
        //TODO a dirty way to get the Type of sub entityPath
        Class subEntityPathClass = property.getProperty(entityPath).any().getClass()
        EntityPath subEntityPath = (EntityPath) subEntityPathClass.newInstance(property.getName())
        if (property.getType() in CollectionPathBase) {
            joins[subEntityPath] = (Path) property.getProperty(entityPath)
        }
        parseMapForJoins(subEntityPath, nestedMap, joins, expressions);
    }


    private void parseMapForJoins(EntityPath entityPath, Map<String, Object> searchMap, Map<Path, Path> joins, List<BooleanExpression> expressions) {
        searchMap.each { key, value ->
            def property = entityPath.hasProperty(key)
            if (property) {
                BooleanExpression expression
                if (value instanceof String && StringUtils.isNotBlank(value)) {
                    ((property.getType() in StringPath
                            && (expression = ((StringPath) property.getProperty(entityPath)).trim().containsIgnoreCase(value.trim()))) ||
                    (property.getType() in BooleanPath
                            && (expression = generateExpression(property.getProperty(entityPath) as BooleanPath, value))) ||
                    (property.getType() in NumberPath
                            && (expression = generateExpression(property.getProperty(entityPath) as NumberPath, value))) ||
                    (property.getType() in DatePath
                            && (expression = generateExpression(property.getProperty(entityPath) as DatePath, value))) ||
                    (property.getType().getGenericSuperclass() in ParameterizedType
                            && property.getType() in EntityPathBase
                            && (expression=matchToEntityId(entityPath, property, value))))
                    if (expression) {
                        expressions << expression
                    }
                } else if (value in Number && property.getType() in NumberPath)  {
                    (expression = generateExpression((NumberPath) property.getProperty(entityPath), (Number) value)) && expressions << expression
                } else if (value in Number && property.getType().getGenericSuperclass() instanceof ParameterizedType &&
                        property.getType() in EntityPathBase)  {
                    (expression = matchToEntityId(entityPath, property, ((Number) value).toString())) && expressions << expression
                } else if (value instanceof Map) {
                    if (property.getProperty(entityPath) in CollectionPathBase) {
                        addCollectionJoinAndParse(entityPath, joins, expressions, value, property)
                    } else if (property.getType() in EntityPathBase) {
                        addEntityJoinAndParse(entityPath, joins, expressions, value, property)
                    } else if (property.getProperty(entityPath) instanceof NumberPath) {
                        (expression=generateExpression((NumberPath) property.getProperty(entityPath), value)) && expressions << expression
                    } else if (property.getProperty(entityPath) instanceof DatePath) {
                        (expression = generateExpression((DatePath) property.getProperty(entityPath), value)) && expressions << expression
                    } else if (property.getProperty(entityPath) instanceof DateTimePath) {
                        (expression = generateExpression((DateTimePath) property.getProperty(entityPath), value)) && expressions << expression
                    }
                } else if (value instanceof Collection) {
                    if (property.getType().getGenericSuperclass() instanceof ParameterizedType &&
                            property.getType() in EntityPathBase) {
                        if ((expression = matchToEntityId(entityPath, property, value))!=null) {
                            expressions << expression
                        }
                    } else if (property.getProperty(entityPath) instanceof SimpleExpression) {
                        boolean addedNull = false;
                        def matchedValues = value.findAll{ it -> if (value) { value } else { addedNull = true; false} }
                        expressions.add(((SimpleExpression) property.getProperty(entityPath)).in(matchedValues).or(addedNull?((SimpleExpression) property.getProperty(entityPath)).isNull():null))
                    }
                } else if (value instanceof Boolean) {
                    property.getType() in BooleanPath && expressions << ((BooleanPath) property.getProperty(entityPath)).eq((Boolean) value)
                }
            } else if (key in [Constants.CURRENT_DATE_ACTIVE] && value instanceof Map) {
                TemporalExpression from = getDatePath(entityPath, (String) ((Map) value).get(Constants.DATE_FROM));
                TemporalExpression to = getDatePath(entityPath, (String) ((Map) value).get(Constants.DATE_TO));
                Date current = new Date();
                if (from!=null && to!=null) {
                    expressions.add(from.before(current).and(to.after(current).or(to.isNull())))
                }
            } else if (key in [Constants.OR, Constants.AND] && value instanceof Map) {
                List<BooleanExpression> orExpressions = new ArrayList<BooleanExpression>();
                parseMapForJoins(entityPath, (Map<String, Object>) value, joins, orExpressions);
                if (!orExpressions.isEmpty()) {
                    BooleanExpression expression = orExpressions.get(0)
                    for (int i = 1; i < orExpressions.size(); i++) {
                        expression = Constants.OR.equals(key)?
                                    expression.or(orExpressions.get(i)):expression.and(orExpressions.get(i))
                    }
                    expressions << expression
                }
            } else if (key in [Constants.OR, Constants.AND] && value instanceof List) {
                BooleanExpression totalExpressions = null
                value.each { Map<String, Object> it ->
                    List<BooleanExpression> orExpressions = new ArrayList<>();
                    parseMapForJoins(entityPath, it, joins, orExpressions);
                    if (!orExpressions.isEmpty()) {
                        int initial = 0;
                        if (totalExpressions==null) {
                            totalExpressions = orExpressions.get(initial++);
                        }
                        for (int i = initial; i < orExpressions.size(); i++) {
                            totalExpressions = Constants.OR.equals(key)?
                                    totalExpressions.or(orExpressions.get(i)):
                                    totalExpressions.and(orExpressions.get(i))
                        }
                    }
                }
                expressions << totalExpressions
            } else if ( key in [Constants.ISNULL, Constants.ISNOTNULL]
                    && value instanceof String) {
                def valueProperty = entityPath.hasProperty(value)
                if (valueProperty) {
                    def nullableField = valueProperty.getProperty(entityPath);
                    if (key in [Constants.ISNULL]) {
                        expressions << ((SimpleExpression) nullableField).isNull()
                    } else {
                        expressions << (((SimpleExpression) nullableField)).isNotNull()
                    }
                }
            }

        }

    }

    private static Date parseDate(Date value) { value }
    private static Date parseDate(Long value) { new Date((Long) value) }
    private static Date parseDate(String value) { new Date(Long.parseLong((String) value)) }
    private static Date parseDate(Object value) {
        if (value in String) parseDate(((String) value))
        else if (value in Long) parseDate(((Long) value))
        else if (value in Date) parseDate(((Date) value))
    }

    private BigDecimal parseNumber(String o) { return new BigDecimal(o) }
    private BigDecimal parseNumber(Double o) { return new BigDecimal((Double) o) }
    private BigDecimal parseNumber(Integer o) { return new BigDecimal((Integer) o) }
    private BigDecimal parseNumber(Object o) { return null }

    /**
     * Translates to a {@link BooleanExpression} of the {@link com.querydsl.core.types.EntityPath} on the provided {@link java.lang.reflect.Field}
     * by matching to the {@param value}
     * @param entityPath the {@link com.querydsl.core.types.EntityPath} for which {@link BooleanExpression} will be generated
     * @param field the {@link java.lang.reflect.Field} that we should match
     * @param value the value which will be matched
     * @return the requested {@link BooleanExpression}
     * @throws IllegalAccessException
     * @throws NoSuchFieldException
     */
    public BooleanExpression matchToEntityId(EntityPath entityPath, MetaProperty property, String value) {
        if (((ParameterizedType) property.getType().getGenericSuperclass()).getActualTypeArguments().length > 0) {
            Type clazz = ((ParameterizedType) property.getType().getGenericSuperclass()).getActualTypeArguments()[0]
            String propertyId = repositories.getPersistentEntity((Class) clazz).getIdProperty().getName()
            Object propertyIdField = property.getProperty(entityPath)[propertyId]
            String id  = checkIfUrlAndParse(value)
            SimpleExpression object = propertyIdField instanceof StringPath?(StringPath) propertyIdField :
                    (propertyIdField instanceof NumberPath ? (NumberPath) propertyIdField :null)
            if (Constants.ISNULL.equals(id) || Constants.ISNOTNULL.equals(id)) {
                return Constants.ISNULL.equals(id)?object.isNull():object.isNotNull()
            }
            Object objectId = propertyIdField instanceof StringPath? id  :
                    (propertyIdField instanceof NumberPath ? getValueOf((NumberPath) propertyIdField, id) :null)
            return object!=null?object.eq(objectId):null;
        }
        return null;
    }

    private String checkIfUrlAndParse(String value) {
        (value.contains("http")
                && value.lastIndexOf('/') >= 0) ? value.substring(value.lastIndexOf('/') + 1) : value
    }

    /**
     * Translates to a {@link BooleanExpression} of the {@link EntityPath} on the provided {@link MetaProperty}
     * by matching to any of the {@param value}
     * @param entityPath the {@link EntityPath} for which {@link BooleanExpression} will be generated
     * @param property the {@link MetaProperty} that we should match
     * @param values the values which will try to match
     * @return the requested {@link BooleanExpression}
     * @throws IllegalAccessException
     * @throws NoSuchFieldException
     */
    public BooleanExpression matchToEntityId(EntityPath entityPath, MetaProperty property, Collection values) {
        if (((ParameterizedType) property.getType().getGenericSuperclass()).getActualTypeArguments().length > 0) {
            Type clazz = ((ParameterizedType) property.getType().getGenericSuperclass()).getActualTypeArguments()[0];
            String propertyId = repositories.getPersistentEntity((Class) clazz).getIdProperty().getName();
            Object propertyIdField = property.getProperty(entityPath)[propertyId]
            SimpleExpression object = propertyIdField instanceof StringPath?
                    (StringPath) propertyIdField:
                        propertyIdField instanceof NumberPath?
                                (NumberPath) propertyIdField:null
            boolean addedNull = false
            boolean addedNotNull = false
            Collection<Object> matchedIds = new ArrayList<Object>()
            values.each {
                if (!(it in [Constants.ISNULL, Constants.ISNOTNULL])) {
                    String id  = checkIfUrlAndParse(it.toString())
                    matchedIds.add(propertyIdField in StringPath ? id :
                            (propertyIdField in NumberPath ?
                                    getValueOf((NumberPath) propertyIdField, id) : null))
                } else {
                    Constants.ISNULL.equals(it)&&(addedNull=true) ||
                            Constants.ISNOTNULL.equals(it)&&(addedNotNull=true)
                }
            }
            if (object!=null) {
                if (addedNull) {
                    return object.isNull().or(object.in(matchedIds))
                } else if (addedNotNull) {
                    return object.isNotNull()
                } else {
                    return  object.in(matchedIds);
                }
            }
        }
        return null;
    }

    private Object getValueOf(NumberPath propertyIdField, String id) {
        propertyIdField.getType().getMethod("valueOf", String.class).invoke(null, id)
    }

    private TemporalExpression getDatePath(EntityPath entityPath, String path) throws IllegalAccessException {
        def property = entityPath.hasProperty(path)
        if (property) {
            Object value = property.getProperty(entityPath);
            if (value instanceof TemporalExpression) {
                return (TemporalExpression) value;
            } else {
                return null;
            }
        }
    }

    private BooleanExpression generateEqualsExpression(NumberPath matchedPath, String val) {
        if (matchedPath.getType().equals(BigDecimal.class)) {
            return matchedPath.eq(new BigDecimal(val))
        } else if (matchedPath.getType().equals(Long.class)) {
            return matchedPath.eq(Long.parseLong(val))
        } else {
            return matchedPath.eq(Integer.parseInt(val))
        }
    }

    private BooleanExpression generateEqualsExpression(NumberPath matchedPath, Number val) {
        if (matchedPath.getType().equals(BigDecimal.class)) {
            return matchedPath.eq(new BigDecimal(val.longValue()));
        } else if (matchedPath.getType().equals(Long.class)) {
            return matchedPath.eq(val);
        } else {
            return matchedPath.eq(val.intValue());
        }
    }

    /**
     * It will generate a {@link BooleanExpression} of the {@link TemporalExpression} matchedPath depending on the provided
     * matcher that can be a Map with keys {@value #RANGE_FORM} and {@value #RANGE_TO}. An expression between the
     * provided values of aforementioned keys
     * @param matchedPath the {@link TemporalExpression} for which {@link BooleanExpression} will be created
     * @param matcher the map with values to be a long as te one expected by {@link Date}.getTime()
     * @return a {@link BooleanExpression} if it finds the expected contents on the {@param matcher}
     * @throws IllegalAccessException
     */
    private BooleanExpression generateExpression(final TemporalExpression matchedPath, Object matcher) throws IllegalAccessException {
        if (matcher instanceof Map) {
            Date from = parseDate(((Map) matcher).get(Constants.RANGE_FROM))
            Date to = parseDate(((Map) matcher).get(Constants.RANGE_TO))
            if (from!=null || to!=null) {
                return matchedPath.between(from, to);
            }
        } else if  (matcher instanceof String) {

        }
        return null;
    }

    /**
     * It will generate a {@link BooleanExpression} of the {@link NumberPath} matchedPath depending on the provided
     * matcher that can be either Map with keys {@value #RANGE_FORM} and {@value #RANGE_TO} or a {@link String}. In the
     * first case create an expression between the provided values of aforementioned keys or an exact match exception
     * @param matchedPath the {@link NumberPath} for which {@link BooleanExpression} will be created
     * @param matcher the map or the simple string expected
     * @return a {@link BooleanExpression} if it finds the expected contents on the {@param matcher}
     * @throws IllegalAccessException
     */
    private BooleanExpression generateExpression(final NumberPath matchedPath, Object matcher) throws IllegalAccessException {
        if (matcher instanceof Map) {
            Number from = parseNumber(((Map) matcher).get(Constants.RANGE_FROM))
            Number to = parseNumber(((Map) matcher).get(Constants.RANGE_TO))
            if (from!=null || to!=null) {
                return matchedPath.between(from, to)
            }
        } else if  (matcher instanceof String) {
            return generateEqualsExpression(matchedPath, (String) matcher);
        } else if  (matcher instanceof Number) {
            return generateEqualsExpression(matchedPath, (Number) matcher);
        }
        return null;
    }

    private BooleanExpression generateExpression(final BooleanPath matchedPath, Object matcher) throws IllegalAccessException {
        if (matcher instanceof String) {
            Boolean b = BooleanUtils.toBooleanObject(matcher, "true", "false", Constants.ISNULL)
            if (b!=null) {
                return matchedPath.eq(b)
            } else {
                return matchedPath.isNull()
            }
        }
        return null;
    }

}