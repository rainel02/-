package com.pindou.app.model;

import java.util.ArrayList;
import java.util.List;

public class TodoProjectRow {
    private Long projectId;
    private String projectName;
    private String projectImage;
    private String projectUrl;
    private Integer quantityPlan;
    private List<ColorRequirement> colors = new ArrayList<>();

    public TodoProjectRow() {}

    public TodoProjectRow(Long projectId, String projectName, String projectImage, String projectUrl, Integer quantityPlan, List<ColorRequirement> colors) {
        this.projectId = projectId;
        this.projectName = projectName;
        this.projectImage = projectImage;
        this.projectUrl = projectUrl;
        this.quantityPlan = quantityPlan;
        this.colors = colors;
    }

    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }
    public String getProjectName() { return projectName; }
    public void setProjectName(String projectName) { this.projectName = projectName; }
    public String getProjectImage() { return projectImage; }
    public void setProjectImage(String projectImage) { this.projectImage = projectImage; }
    public String getProjectUrl() { return projectUrl; }
    public void setProjectUrl(String projectUrl) { this.projectUrl = projectUrl; }
    public Integer getQuantityPlan() { return quantityPlan; }
    public void setQuantityPlan(Integer quantityPlan) { this.quantityPlan = quantityPlan; }
    public List<ColorRequirement> getColors() { return colors; }
    public void setColors(List<ColorRequirement> colors) { this.colors = colors == null ? new ArrayList<>() : colors; }
}
