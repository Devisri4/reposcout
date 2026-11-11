package com.devisri.reposcout.store;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SavedMatchRepository extends JpaRepository<SavedMatch, String> {
}
