package net.sourceforge.extension.repository;

import net.sourceforge.extension.entity.Address;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;

/**
 * Created by christos.karalis on 5/28/2017.
 */
@RepositoryRestResource()
public interface AddressRepository extends JpaRepository<Address, Long> {
}
