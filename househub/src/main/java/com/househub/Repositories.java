package com.househub;

import org.springframework.data.jpa.repository.JpaRepository;

interface MemberRepository extends JpaRepository<Member, Long> {
    boolean existsByNameIgnoreCase(String name);
}

interface ExpenseRepository extends JpaRepository<Expense, Long> {}
