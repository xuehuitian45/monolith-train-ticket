package edu.fudanselab.trainticket.repository;

import edu.fudanselab.trainticket.entity.AuthUser;
import org.springframework.data.repository.CrudRepository;

import java.util.List;
import java.util.Optional;

/**
 * @author fdse
 */
public interface AuthUserRepository extends CrudRepository<AuthUser, String> {

    /**
     * find by username
     *
     * @param username username
     * @return Optional<User>
     */
    Optional<AuthUser> findByUsername(String username);
    
    /**
     * find first by username (handles duplicate usernames)
     *
     * @param username username
     * @return Optional<User>
     */
    Optional<AuthUser> findFirstByUsername(String username);
    
    /**
     * find all by username (for handling duplicates)
     *
     * @param username username
     * @return List of users with the same username
     */
    List<AuthUser> findAllByUsername(String username);

    /**
     * delete by user id
     *
     * @param userId user id
     * @return null
     */
    void deleteByUserId(String userId);
}
