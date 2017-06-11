package net.sourceforge.extension.repository;

import net.sourceforge.extension.entity.Application;
import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;

/**
 * Created by christos.karalis on 6/7/2017.
 */
@RepositoryRestResource(path = "application")
public interface ApplicationRepository  extends PagingAndSortingRepository<Application, Long> {
}
