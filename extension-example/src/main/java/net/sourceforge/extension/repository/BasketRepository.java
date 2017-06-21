package net.sourceforge.extension.repository;

import net.sourceforge.extension.entity.Basket;
import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;

/**
 * Created by christos.karalis on 6/7/2017.
 */
@RepositoryRestResource(path = "basket")
public interface BasketRepository extends PagingAndSortingRepository<Basket, Long> {
}
