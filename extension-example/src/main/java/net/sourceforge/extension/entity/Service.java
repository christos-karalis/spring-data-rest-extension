package net.sourceforge.extension.entity;

import javax.persistence.*;

/**
 * Created by christos.karalis on 6/10/2017.
 */
@Entity
public class Service {

    @Id
    @Column(name = "SERVICE_ID")
    private Long id;

    @Column
    private String name;

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
}
