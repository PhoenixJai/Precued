package com.precued.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/** Reusable config-time Session Flow stage owned by a Template. */
@Entity
@Table(name = "template_stage")
@Getter
@Setter
public class TemplateStage {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "template_id", nullable = false)
    private Template template;

    @Column(name = "stage_key", nullable = false)
    private String stageKey;

    @Column(nullable = false)
    private String name;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    /** Null means untimed. Database constraint requires a positive value when present. */
    @Column(name = "duration_seconds")
    private Integer durationSeconds;
}
