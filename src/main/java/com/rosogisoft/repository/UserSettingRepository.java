package com.rosogisoft.repository;


import com.rosogisoft.domain.SettingKey;
import com.rosogisoft.domain.UserSetting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserSettingRepository extends JpaRepository<UserSetting, Long> {

    @Query("SELECT s FROM UserSetting s WHERE s.owner.id = :ownerId")
    List<UserSetting> findAllByOwnerId(@Param("ownerId") Long ownerId);

    @Query("SELECT s FROM UserSetting s WHERE s.owner.id = :ownerId AND s.key = :key")
    Optional<UserSetting> findByOwnerIdAndKey(@Param("ownerId") Long ownerId,
                                              @Param("key") SettingKey key);

    @Modifying
    @Query(value = """
        INSERT INTO user_settings (owner_id, key, value, updated_at)
        VALUES (:ownerId, :key, :value, NOW())
        ON CONFLICT (owner_id, key) DO UPDATE
        SET value = :value, updated_at = NOW()
        """, nativeQuery = true)
    void upsert(@Param("ownerId") Long ownerId,
                @Param("key") String key,
                @Param("value") String value);
}
