package net.sourceforge.extension;

/**
 * Created by christos.karalis on 6/4/2017.
 */
public class DefaultPathGenerator implements QueryLanguagePathGenerator{

    public Class getPredicateClass(Class clazz) throws ClassNotFoundException {
        String name = clazz.getName();
        return Class.forName(new StringBuilder(name).insert(name.lastIndexOf('.')+1, 'Q').toString());
    }

}
