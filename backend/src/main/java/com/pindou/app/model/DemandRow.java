package com.pindou.app.model;

import java.util.ArrayList;
import java.util.List;

public class DemandRow {
    private String code;
    private Integer remain;
    private Integer need;
    private List<UsageProjectItem> projects = new ArrayList<>();

    public DemandRow() {}

    public DemandRow(String code, Integer remain, Integer need, List<UsageProjectItem> projects) {
        this.code = code;
        this.remain = remain;
        this.need = need;
        this.projects = projects;
    }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public Integer getRemain() { return remain; }
    public void setRemain(Integer remain) { this.remain = remain; }
    public Integer getNeed() { return need; }
    public void setNeed(Integer need) { this.need = need; }
    public List<UsageProjectItem> getProjects() { return projects; }
    public void setProjects(List<UsageProjectItem> projects) { this.projects = projects; }
}
