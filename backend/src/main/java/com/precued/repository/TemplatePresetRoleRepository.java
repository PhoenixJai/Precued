package com.precued.repository;

import com.precued.entity.TemplatePresetRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TemplatePresetRoleRepository
        extends JpaRepository<TemplatePresetRole, TemplatePresetRole.Id> {
    List<TemplatePresetRole> findByPresetId(UUID presetId);
}
