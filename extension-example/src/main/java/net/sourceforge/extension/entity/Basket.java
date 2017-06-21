package net.sourceforge.extension.entity;

import javax.persistence.*;
import java.util.List;

/**
 * Created by christos.karalis on 6/21/2017.
 */
@Entity
public class Basket {

    private Long id;

    private List<BasketItem> items;

    @Id
    @Column
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    @OneToMany(mappedBy = "basket")
    public List<BasketItem> getItems() {
        return items;
    }

    public void setItems(List<BasketItem> items) {
        this.items = items;
    }
}
