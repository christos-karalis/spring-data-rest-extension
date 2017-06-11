package net.sourceforge.extension;

/**
 * It is used to store association information of entities that is used on
 * {@link AdvancedPostController}
 */
public class Association {
    /**
     * The class type of the persisted associated entity
     */
    private Class domainType;
    /**
     *
     */
    private String parentEntityProperty;
    private String matchingEntity;
    private Association backwardAssociation;
    /**
     * if it is false the association is stored on opposite {@link javax.persistence.OneToMany}
     */
    private boolean manyToMany = true;

    public Association(Class domainType, String parentEntityProperty, String matchingEntity) {
        this.domainType = domainType;
        this.parentEntityProperty = parentEntityProperty;
        this.matchingEntity = matchingEntity;
    }

    public Association(Class domainType, String parentEntityProperty, String matchingEntity, boolean manyToMany) {
        this.domainType = domainType;
        this.parentEntityProperty = parentEntityProperty;
        this.matchingEntity = matchingEntity;
        this.manyToMany = manyToMany;
    }

    public Association(Class domainType, String parentEntityProperty, String matchingEntity, Association backwardAssociation) {
        this.domainType = domainType;
        this.parentEntityProperty = parentEntityProperty;
        this.matchingEntity = matchingEntity;
        this.backwardAssociation = backwardAssociation;
    }

    public Association getBackwardAssociation() {
        return backwardAssociation;
    }

    public Class getDomainType() {
        return domainType;
    }

    public String getParentEntityProperty() {
        return parentEntityProperty;
    }

    public String getMatchingEntity() {
        return matchingEntity;
    }

    @Override
    public String toString() {
        return "Association{" +
                "domainType=" + domainType +
                ", parentEntityProperty='" + parentEntityProperty + '\'' +
                ", matchingEntity='" + matchingEntity + '\'' +
                ", backwardAssociation=" + backwardAssociation +
                ", manyToMany=" + manyToMany +
                '}';
    }

    public boolean isManyToMany() {
        return manyToMany;
    }
}
