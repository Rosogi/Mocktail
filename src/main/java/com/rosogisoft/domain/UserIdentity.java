package com.rosogisoft.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "user_identities",
        uniqueConstraints = @UniqueConstraint(columnNames = {"auth_provider_id", "external_subject"}))
@Getter
@Setter
@NoArgsConstructor
public class UserIdentity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "auth_provider_id", nullable = false)
    private AuthProvider authProvider;

    @Column(name = "external_subject", nullable = false, length = 512)
    private String externalSubject;

    @Column(name = "login_hint")
    private String loginHint;

    public UserIdentity(User user, AuthProvider authProvider, String externalSubject, String loginHint) {
        this.user = user;
        this.authProvider = authProvider;
        this.externalSubject = externalSubject;
        this.loginHint = loginHint;
    }
}
