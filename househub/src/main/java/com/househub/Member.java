package com.househub;

import jakarta.persistence.*;

@Entity
@Table(name = "house_member")
public class Member {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false, unique = true)
    public String name;

    protected Member() {}
    public Member(String name) { this.name = name; }
}
