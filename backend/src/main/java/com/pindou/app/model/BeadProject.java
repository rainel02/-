package com.pindou.app.model;

import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "bead_project")
public class BeadProject {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @ElementCollection
    @CollectionTable(name = "bead_project_tag", joinColumns = @JoinColumn(name = "project_id"))
    @Column(name = "tag")
    private List<String> tags = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    private BeadStatus status = BeadStatus.TODO;

    private String sourceUrl;

    @Lob
    private String patternImage;

    @Lob
    private String workImage;

    private Integer quantityDone = 0;
    private Integer quantityPlan = 1;

    private Integer gridRows;
    private Integer gridCols;

    @Lob
    private String gridCellsJson;

    @ElementCollection
    @CollectionTable(name = "bead_project_required_color", joinColumns = @JoinColumn(name = "project_id"))
    private List<ColorRequirement> requiredColors = new ArrayList<>();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public List<String> getTags() { return tags; }
    public void setTags(List<String> tags) { this.tags = tags; }
    public BeadStatus getStatus() { return status; }
    public void setStatus(BeadStatus status) { this.status = status; }
    public String getSourceUrl() { return sourceUrl; }
    public void setSourceUrl(String sourceUrl) { this.sourceUrl = sourceUrl; }
    public String getPatternImage() { return patternImage; }
    public void setPatternImage(String patternImage) { this.patternImage = patternImage; }
    public String getWorkImage() { return workImage; }
    public void setWorkImage(String workImage) { this.workImage = workImage; }
    public Integer getQuantityDone() { return quantityDone; }
    public void setQuantityDone(Integer quantityDone) { this.quantityDone = quantityDone; }
    public Integer getQuantityPlan() { return quantityPlan; }
    public void setQuantityPlan(Integer quantityPlan) { this.quantityPlan = quantityPlan; }
    public Integer getGridRows() { return gridRows; }
    public void setGridRows(Integer gridRows) { this.gridRows = gridRows; }
    public Integer getGridCols() { return gridCols; }
    public void setGridCols(Integer gridCols) { this.gridCols = gridCols; }
    public String getGridCellsJson() { return gridCellsJson; }
    public void setGridCellsJson(String gridCellsJson) { this.gridCellsJson = gridCellsJson; }
    public List<ColorRequirement> getRequiredColors() { return requiredColors; }
    public void setRequiredColors(List<ColorRequirement> requiredColors) { this.requiredColors = requiredColors; }
}
