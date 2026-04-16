package com.newplanner.repository;

import com.newplanner.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends JpaRepository<User, String> {
    // PK is firebaseUid, so findById(firebaseUid) works natively
}
