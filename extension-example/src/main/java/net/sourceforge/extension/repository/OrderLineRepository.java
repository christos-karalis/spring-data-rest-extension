package net.sourceforge.extension.repository;

import net.sourceforge.extension.entity.OrderLine;
import net.sourceforge.extension.entity.Service;
import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;

/**
 * Created by christos.karalis on 6/7/2017.
 */
@RepositoryRestResource(path = "orderLine")
public interface OrderLineRepository extends PagingAndSortingRepository<OrderLine, Long> {
}
