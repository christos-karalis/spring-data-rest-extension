package net.sourceforge.extension.entity;

import javax.persistence.*;

/**
 * Created by christos.karalis on 6/11/2017.
 */
@Entity
public class OrderLine {

    @Id
    @Column
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    @Column
    private Integer quantity;

    @ManyToOne
    @JoinColumn(name = "SERVICE_ID")
    private Service service;

    @ManyToOne
    @JoinColumn(name = "APPLICATION_ID")
    private Application application;


    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
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
}
