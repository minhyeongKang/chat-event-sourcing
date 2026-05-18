package com.chat.chat_event_sourcing.domain.user.repository;

import com.chat.chat_event_sourcing.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {
    boolean existsByUsername(String username);
}