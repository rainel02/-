package com.pindou.app.model;

import java.util.List;

public class BeadProjectSummary {
    private Long id;
    private String name;
    private List<String> tags;
    private BeadStatus status;
    private String sourceUrl;
    private String patternImage;
    private Integer quantityDone;
    private Integer quantityPlan;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<String> getTags() {
        return tags;
    }

    public void setTags(List<String> tags) {
        this.tags = tags;
    }

    public BeadStatus getStatus() {
        return status;
    }

    public void setStatus(BeadStatus status) {
        this.status = status;
    }

    public String getSourceUrl() {
        return sourceUrl;
    }

    public void setSourceUrl(String sourceUrl) {
        this.sourceUrl = sourceUrl;
    }

    public String getPatternImage() {
        return patternImage;
    }

    public void setPatternImage(String patternImage) {
        this.patternImage = patternImage;
    }

    public Integer getQuantityDone() {
        return quantityDone;
    }

    public void setQuantityDone(Integer quantityDone) {
        this.quantityDone = quantityDone;
    }

    public Integer getQuantityPlan() {
        return quantityPlan;
    }

    public void setQuantityPlan(Integer quantityPlan) {
        this.quantityPlan = quantityPlan;
    }
}