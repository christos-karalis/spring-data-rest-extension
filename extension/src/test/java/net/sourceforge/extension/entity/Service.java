package net.sourceforge.extension.entity;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import java.util.Date;

/**
 * Created by christos.karalis on 6/3/2017.
 */
@Entity
@Table(name = "SERVICE")
public class Service {

    @Id
    @Column
    private Long id;

    @Column
    private String name;

    @Column(name = "ACTIVE_FROM")
    private Date activeFrom;

    @Column(name = "ACTIVE_TO")
    private Date activeTo;

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

    public Date getActiveFrom() {
        return activeFrom;
    }

    public void setActiveFrom(Date activeFrom) {
        this.activeFrom = activeFrom;
    }

    public Date getActiveTo() {
        return activeTo;
    }

    public void setActiveTo(Date activeTo) {
        this.activeTo = activeTo;
    }
}
