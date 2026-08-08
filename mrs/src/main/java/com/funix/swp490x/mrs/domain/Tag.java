package com.funix.swp490x.mrs.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Objects;

/**
 * One metadata value, unique per {@code (type, name)}. Maps the {@code tag}
 * table created by Flyway V1; the schema is owned by the migration and JPA only
 * validates against it.
 */
@Entity
@Table(name = "tag")
public class Tag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TagType type;

    @Column(nullable = false, length = 100)
    private String name;

    protected Tag() {
    }

    public Tag(TagType type, String name) {
        this.type = type;
        this.name = name;
    }

    public Long getId() {
        return id;
    }

    public TagType getType() {
        return type;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    /**
     * By identity, which is what a {@code Set<Tag>} on a song needs. Falls back
     * to {@code (type, name)} while a tag is still unsaved.
     */
    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Tag tag)) {
            return false;
        }
        if (id != null && tag.id != null) {
            return id.equals(tag.id);
        }
        return type == tag.type && Objects.equals(name, tag.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, name);
    }

    @Override
    public String toString() {
        return type + ":" + name;
    }
}
