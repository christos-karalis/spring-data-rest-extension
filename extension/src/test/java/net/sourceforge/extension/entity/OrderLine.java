package net.sourceforge.extension.entity;

import javax.persistence.*;

/**
 * Created by christos.karalis on 6/4/2017.
 */
@Entity
@Table(name = "ORDER_LINE")
public class OrderLine {

    @Id
    @Column
    private Long id;
    @ManyToOne
    @JoinColumn(name = "SERVICE_ID")
    private Service service;
    @ManyToOne
    @JoinColumn(name = "APPLICATION_ID")
    private Application application;
    @Column
    private Integer quantity;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Service getService() {
        return service;
    }

    public void setService(Service service) {
        this.service = service;
    }

    public Application getApplication() {
        return application;
    }

    public void setApplication(Application application) {
        this.application = application;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }
}
