package dev.servicelog.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface AuthTokenRepository extends JpaRepository<AuthToken, Long> {

    Optional<AuthToken> findByTokenHashAndPurpose(String tokenHash, AuthToken.Purpose purpose);

    @Modifying
    @Query("delete from AuthToken t where t.user = :user and t.purpose = :purpose")
    void deleteAllFor(AppUser user, AuthToken.Purpose purpose);
}
