package com.precued.repository;

import com.precued.entity.TemplateStageRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TemplateStageRoleRepository
        extends JpaRepository<TemplateStageRole, TemplateStageRole.Id> {
    List<TemplateStageRole> findByTemplateStageId(UUID templateStageId);
}
