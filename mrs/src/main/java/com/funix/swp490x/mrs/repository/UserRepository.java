package com.funix.swp490x.mrs.repository;

import com.funix.swp490x.mrs.domain.Role;
import com.funix.swp490x.mrs.domain.User;
import com.funix.swp490x.mrs.domain.UserStatus;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * Email lookup for authentication. The column collation is case-insensitive
     * (DC-06), so no normalisation is needed here.
     */
    Optional<User> findByEmail(String email);

    /**
     * P-06a Zone B/D — filter by role, status and a name/email fragment, then
     * page. ADMIN rows are never listed (the sole admin does not manage
     * themselves here). Null filters mean "all" among non-ADMIN accounts;
     * blank {@code q} is treated as null by the caller.
     */
    @Query("""
            SELECT u FROM User u
            WHERE u.role <> 'ADMIN'
              AND (:role IS NULL OR u.role = :role)
              AND (:status IS NULL OR u.status = :status)
              AND (:q IS NULL OR LOWER(u.username) LIKE LOWER(CONCAT('%', :q, '%'))
                               OR LOWER(u.email) LIKE LOWER(CONCAT('%', :q, '%')))
            """)
    Page<User> search(@Param("role") Role role,
            @Param("status") UserStatus status,
            @Param("q") String q,
            Pageable pageable);
}
