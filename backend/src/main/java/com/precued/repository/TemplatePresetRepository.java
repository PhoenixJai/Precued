package com.precued.repository;

import com.precued.entity.TemplatePreset;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TemplatePresetRepository extends JpaRepository<TemplatePreset, UUID> {
    List<TemplatePreset> findByTemplateIdOrderBySortOrder(String templateId);
}
