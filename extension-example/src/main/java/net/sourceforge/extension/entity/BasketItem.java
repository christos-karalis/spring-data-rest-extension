package net.sourceforge.extension.entity;

import javax.persistence.*;

/**
 * Created by christos.karalis on 6/21/2017.
 */
@Entity
@Table(name = "BASKET_ITEM")
public class BasketItem {

    private BasketItemId id;

    private Item item;

    private Basket basket;

    private Integer quantity;

    public BasketItem() {
        this.id = new BasketItemId();
    }

    @EmbeddedId
    @AttributeOverrides({
            @AttributeOverride(name = "itemId", column = @Column(name = "ITEM_ID")),
            @AttributeOverride(name = "basketId", column = @Column(name = "BASKET_ID"))
    })
    public BasketItemId getId() {
        return id;
    }


    public void setId(BasketItemId id) {
        this.id = id;
    }


    @ManyToOne
    @JoinColumn(name = "ITEM_ID", insertable = false, updatable = false)
    public Item getItem() {
        return item;
    }

    public void setItem(Item item) {
        if (item!=null) {
            id.setItemId(item.getId());
        } else {
            id.setItemId(null);
        }
        this.item = item;
    }

    @ManyToOne
    @JoinColumn(name = "BASKET_ID", insertable = false, updatable = false)
    public Basket getBasket() {
        return basket;
    }

    public void setBasket(Basket basket) {
        if (basket!=null) {
            id.setBasketId(basket.getId());
        } else {
            id.setBasketId(null);
        }
        this.basket = basket;
    }

    @Column
    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }
}
