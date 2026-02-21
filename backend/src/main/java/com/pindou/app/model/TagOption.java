package com.pindou.app.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "tag_option")
public class TagOption {
    @Id
    @Column(name = "tag", nullable = false, length = 120)
    private String tag;

    public TagOption() {
    }

    public TagOption(String tag) {
        this.tag = tag;
    }

    public String getTag() {
        return tag;
    }

    public void setTag(String tag) {
        this.tag = tag;
    }
}
