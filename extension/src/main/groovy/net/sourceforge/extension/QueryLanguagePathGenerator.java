package net.sourceforge.extension;

/**
 * Created by christos.karalis on 6/4/2017.
 */
public interface QueryLanguagePathGenerator {
    Class getPredicateClass(Class clazz) throws ClassNotFoundException;
}
