package net.sourceforge.extension.repository;

import net.sourceforge.extension.entity.BasketItem;
import net.sourceforge.extension.entity.BasketItemId;
import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;

/**
 * Created by christos.karalis on 6/7/2017.
 */
@RepositoryRestResource(path = "basketItem")
public interface BasketItemRepository extends PagingAndSortingRepository<BasketItem, BasketItemId> {
}
