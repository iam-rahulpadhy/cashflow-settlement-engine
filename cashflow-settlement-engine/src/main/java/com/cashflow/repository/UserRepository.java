package com.cashflow.repository;

import com.cashflow.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link User} persistence.
 *
 * <p>Spring generates the implementation at boot time; we only declare
 * the query contracts here. Keeping it thin is intentional -- we do not
 * want business logic leaking into the repository layer.</p>
 */
@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    /**
     * Looks up a user by their unique username handle.
     *
     * <p>Used by the TestRunner and validation logic to resolve a
     * human-readable name to a UUID without an extra DB call.</p>
     *
     * @param username the unique handle to search for
     * @return an Optional containing the User if found, empty otherwise
     */
    Optional<User> findByUsername(String username);
}
