package net.sourceforge.extension.entity;

import net.sourceforge.extension.Base64Convertable;

import javax.persistence.Embeddable;

/**
 * Created by christos.karalis on 6/21/2017.
 */
@Embeddable
public class BasketItemId implements Base64Convertable {

    private Long itemId;

    private Long basketId;

    public Long getItemId() {
        return itemId;
    }

    public void setItemId(Long itemId) {
        this.itemId = itemId;
    }

    public Long getBasketId() {
        return basketId;
    }

    public void setBasketId(Long basketId) {
        this.basketId = basketId;
    }
}
