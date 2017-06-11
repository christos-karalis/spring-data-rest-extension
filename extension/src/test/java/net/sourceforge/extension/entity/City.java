package net.sourceforge.extension.entity;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;

/**
 * Created by christos.karalis on 5/30/2017.
 */
@Entity
public class City {

    @Id
    @Column(name = "CITY_ID")
    private Long id;

    @Column
    private String name;

    @Column
    private String province;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getProvince() {
        return province;
    }

    public void setProvince(String province) {
        this.province = province;
    }
}
