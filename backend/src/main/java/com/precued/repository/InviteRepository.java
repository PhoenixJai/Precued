package com.precued.repository;

import com.precued.entity.Invite;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InviteRepository extends JpaRepository<Invite, UUID> {
    @Query("""
            select i from Invite i
            join fetch i.roomRole rr
            join fetch rr.room r
            where i.token = :token
            """)
    Optional<Invite> findByToken(@Param("token") String token);

    List<Invite> findByRoomRoleId(UUID roomRoleId);

    @Query("""
            select i from Invite i
            join fetch i.roomRole rr
            join fetch rr.room r
            where r.id = :roomId
            order by i.createdAt asc
            """)
    List<Invite> findByRoomId(@Param("roomId") UUID roomId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select i from Invite i
            join fetch i.roomRole rr
            join fetch rr.room r
            where i.token = :token
            """)
    Optional<Invite> findByTokenForUpdate(@Param("token") String token);
}
