package com.precued.repository;

import com.precued.entity.TemplateStage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TemplateStageRepository extends JpaRepository<TemplateStage, UUID> {
    List<TemplateStage> findByTemplateIdOrderBySortOrder(String templateId);
}
