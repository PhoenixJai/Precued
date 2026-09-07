package com.precued.repository;

import com.precued.entity.TemplateRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TemplateRoleRepository extends JpaRepository<TemplateRole, UUID> {
    List<TemplateRole> findByTemplateIdOrderBySortOrder(String templateId);
}
