package com.precued.repository;

import com.precued.entity.Template;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TemplateRepository extends JpaRepository<Template, String> {
    List<Template> findByCreatedByIdOrderByCreatedAtDesc(UUID createdById);
}
