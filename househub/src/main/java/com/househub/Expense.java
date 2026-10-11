package com.househub;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

@Entity
public class Expense {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false)
    public String title;

    @Column(nullable = false, precision = 12, scale = 2)
    public BigDecimal amount;

    @Column(nullable = false)
    public LocalDate date;

    @ManyToOne(optional = false)
    public Member paidBy;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "expense_participants")
    public Set<Member> participants = new LinkedHashSet<>();

    /** Stored file name on disk (random), and the original name shown to the user. */
    public String receiptFile;
    public String receiptName;

    public BigDecimal getShare() {
        return amount.divide(BigDecimal.valueOf(participants.size()), 2, RoundingMode.HALF_UP);
    }

    public String getParticipantNames() {
        return participants.stream().map(m -> m.name).sorted().collect(Collectors.joining(", "));
    }
}
