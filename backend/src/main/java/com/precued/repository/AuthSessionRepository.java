package com.precued.repository;

import com.precued.entity.AuthSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface AuthSessionRepository extends JpaRepository<AuthSession, UUID> {

    /**
     * Join-fetches the User: AuthSessionInterceptor reads session.getUser()
     * immediately, outside any transaction (open-in-view is disabled) —
     * without this, that's the same LazyInitializationException class this
     * codebase has hit repeatedly for other FetchType.LAZY associations
     * read straight off a just-returned repository result.
     */
    @Query("select s from AuthSession s join fetch s.user where s.token = :token")
    Optional<AuthSession> findByToken(@Param("token") String token);
}
