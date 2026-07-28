package com.funix.swp490x.mrs.repository;

import com.funix.swp490x.mrs.domain.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * Email lookup for authentication. The column collation is case-insensitive
     * (DC-06), so no normalisation is needed here.
     */
    Optional<User> findByEmail(String email);
}
