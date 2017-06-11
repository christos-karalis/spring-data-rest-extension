package net.sourceforge.extension.entity;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;

/**
 * Created by christos.karalis on 6/7/2017.
 */
@Entity
public class Applicant {

    @Id
    @Column(name="APPLICANT_ID")
    private Long id;

    @Column
    private String name;

    @Column
    private String surname;

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

    public String getSurname() {
        return surname;
    }

    public void setSurname(String surname) {
        this.surname = surname;
    }
}
