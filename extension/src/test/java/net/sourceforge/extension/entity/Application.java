package net.sourceforge.extension.entity;

import javax.persistence.*;
import java.math.BigInteger;
import java.util.Date;
import java.util.List;

/**
 * Created by christos.karalis on 5/30/2017.
 */
@Entity
public class Application {

    @Id
    @Column
    private Long id;
    @OneToOne
    @JoinColumn(name = "ADDRESS_ID")
    private Address address;
    @Column(name = "SUBMISSION_DATE")
    private Date submissionDate;
    @Column
    private String name;
    @Column
    private Long quantity;
    @Column(name = "QUANTITY2")
    private BigInteger quantityBigInteger;
    @OneToMany(mappedBy = "application")
    private List<OrderLine> orderLines;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Address getAddress() {
        return address;
    }

    public void setAddress(Address address) {
        this.address = address;
    }

    public Date getSubmissionDate() {
        return submissionDate;
    }

    public void setSubmissionDate(Date submissionDate) {
        this.submissionDate = submissionDate;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Long getQuantity() {
        return quantity;
    }

    public void setQuantity(Long quantity) {
        this.quantity = quantity;
    }

    public BigInteger getQuantityBigInteger() {

        return quantityBigInteger;
    }

    public void setQuantityBigInteger(BigInteger quantityBigInteger) {
        this.quantityBigInteger = quantityBigInteger;
    }

    public List<OrderLine> getOrderLines() {
        return orderLines;
    }

    public void setOrderLines(List<OrderLine> orderLines) {
        this.orderLines = orderLines;
    }
}
