package com.itmo.infobezitmo.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Запрос генерируется Spring Data по имени метода и выполняется как PreparedStatement:
 * значение username передаётся параметром, а не подставляется в строку SQL.
 */
public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByUsername(String username);
}
