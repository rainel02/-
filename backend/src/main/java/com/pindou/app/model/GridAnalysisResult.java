package com.pindou.app.model;

import java.util.ArrayList;
import java.util.List;

public class GridAnalysisResult {
    private int rows;
    private int cols;
    private int ocrCount;
    private int filledCount;
    private String ocrServiceSummary;
    private List<GridAnalysisCell> cells = new ArrayList<>();

    public int getRows() {
        return rows;
    }

    public void setRows(int rows) {
        this.rows = rows;
    }

    public int getCols() {
        return cols;
    }

    public void setCols(int cols) {
        this.cols = cols;
    }

    public int getOcrCount() {
        return ocrCount;
    }

    public void setOcrCount(int ocrCount) {
        this.ocrCount = ocrCount;
    }

    public int getFilledCount() {
        return filledCount;
    }

    public void setFilledCount(int filledCount) {
        this.filledCount = filledCount;
    }

    public String getOcrServiceSummary() {
        return ocrServiceSummary;
    }

    public void setOcrServiceSummary(String ocrServiceSummary) {
        this.ocrServiceSummary = ocrServiceSummary;
    }

    public List<GridAnalysisCell> getCells() {
        return cells;
    }

    public void setCells(List<GridAnalysisCell> cells) {
        this.cells = cells == null ? new ArrayList<>() : cells;
    }
}
