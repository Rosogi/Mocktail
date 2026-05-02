package com.rosogisoft.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "user_password_credentials")
@Getter
@Setter
@NoArgsConstructor
public class UserPasswordCredential {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false, unique = true)
    private String login;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "must_change_password", nullable = false)
    private boolean mustChangePassword = true;

    @Column(name = "password_changed_at")
    private Instant passwordChangedAt;

    public UserPasswordCredential(User user, String login, String passwordHash) {
        this.user = user;
        this.login = login;
        this.passwordHash = passwordHash;
    }
}
