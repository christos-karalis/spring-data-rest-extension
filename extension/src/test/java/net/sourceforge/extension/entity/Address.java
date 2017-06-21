package net.sourceforge.extension.entity;

import javax.persistence.*;

/**
 * Created by christos.karalis on 5/27/2017.
 */
@Entity
public class Address {

    @Id
    @Column(name = "ADDRESS_ID")
    private Long id;
    @ManyToOne
    @JoinColumn(name = "CITY_ID")
    private City city;
    @Column
    private String street;
    @Column
    private String number;
    @Column
    private Boolean verified;


    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public City getCity() {
        return city;
    }

    public void setCity(City city) {
        this.city = city;
    }

    public String getStreet() {
        return street;
    }

    public void setStreet(String street) {
        this.street = street;
    }

    public String getNumber() {
        return number;
    }

    public void setNumber(String number) {
        this.number = number;
    }

    public Boolean getVerified() {
        return verified;
    }

    public void setVerified(Boolean verified) {
        this.verified = verified;
    }
}
