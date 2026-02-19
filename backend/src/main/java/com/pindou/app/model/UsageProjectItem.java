package com.pindou.app.model;

public class UsageProjectItem {
    private Long projectId;
    private String projectName;
    private String projectImage;
    private String projectUrl;
    private Integer used;

    public UsageProjectItem() {}

    public UsageProjectItem(Long projectId, String projectName, String projectImage, String projectUrl, Integer used) {
        this.projectId = projectId;
        this.projectName = projectName;
        this.projectImage = projectImage;
        this.projectUrl = projectUrl;
        this.used = used;
    }

    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }
    public String getProjectName() { return projectName; }
    public void setProjectName(String projectName) { this.projectName = projectName; }
    public String getProjectImage() { return projectImage; }
    public void setProjectImage(String projectImage) { this.projectImage = projectImage; }
    public String getProjectUrl() { return projectUrl; }
    public void setProjectUrl(String projectUrl) { this.projectUrl = projectUrl; }
    public Integer getUsed() { return used; }
    public void setUsed(Integer used) { this.used = used; }
}
