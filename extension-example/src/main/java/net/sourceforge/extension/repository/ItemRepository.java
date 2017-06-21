package net.sourceforge.extension.repository;

import net.sourceforge.extension.entity.Item;
import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;

/**
 * Created by christos.karalis on 6/7/2017.
 */
@RepositoryRestResource(path = "item")
public interface ItemRepository extends PagingAndSortingRepository<Item, Long> {
}
