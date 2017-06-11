package net.sourceforge.extension.entity;

import javax.persistence.*;

/**
 * Created by christos.karalis on 5/27/2017.
 */
@Entity
public class Address {

    @Id
    @Column(name = "ADDRESS_ID")
    Long id;
    @ManyToOne
    @JoinColumn(name = "CITY_ID")
    City city;
    @Column
    String street;
    @Column
    String number;

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
}
