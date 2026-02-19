package com.pindou.app.model;

import java.util.ArrayList;
import java.util.List;

public class UsageRow {
    private String code;
    private Integer used;
    private List<UsageProjectItem> projects = new ArrayList<>();

    public UsageRow() {}

    public UsageRow(String code, Integer used, List<UsageProjectItem> projects) {
        this.code = code;
        this.used = used;
        this.projects = projects;
    }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public Integer getUsed() { return used; }
    public void setUsed(Integer used) { this.used = used; }
    public List<UsageProjectItem> getProjects() { return projects; }
    public void setProjects(List<UsageProjectItem> projects) { this.projects = projects; }
}
