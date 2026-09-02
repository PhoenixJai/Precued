package com.precued.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "template_preset")
@Getter
@Setter
public class TemplatePreset {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "template_id", nullable = false)
    private Template template;

    @Column(nullable = false)
    private String name; // e.g. "Judge + Jury Only"

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;
}
