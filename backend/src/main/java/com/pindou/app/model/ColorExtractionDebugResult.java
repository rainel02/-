package com.pindou.app.model;

import java.util.ArrayList;
import java.util.List;

public class ColorExtractionDebugResult {
    private List<ColorRequirement> colors = new ArrayList<>();
    private String strategy;
    private String ocrServiceSummary;
    private String rawText;
    private List<String> locationLines = new ArrayList<>();
    private List<String> pairLogs = new ArrayList<>();
    private List<String> fallbackLogs = new ArrayList<>();

    public List<ColorRequirement> getColors() {
        return colors;
    }

    public void setColors(List<ColorRequirement> colors) {
        this.colors = colors == null ? new ArrayList<>() : colors;
    }

    public String getStrategy() {
        return strategy;
    }

    public void setStrategy(String strategy) {
        this.strategy = strategy;
    }

    public String getOcrServiceSummary() {
        return ocrServiceSummary;
    }

    public void setOcrServiceSummary(String ocrServiceSummary) {
        this.ocrServiceSummary = ocrServiceSummary;
    }

    public String getRawText() {
        return rawText;
    }

    public void setRawText(String rawText) {
        this.rawText = rawText;
    }

    public List<String> getLocationLines() {
        return locationLines;
    }

    public void setLocationLines(List<String> locationLines) {
        this.locationLines = locationLines == null ? new ArrayList<>() : locationLines;
    }

    public List<String> getPairLogs() {
        return pairLogs;
    }

    public void setPairLogs(List<String> pairLogs) {
        this.pairLogs = pairLogs == null ? new ArrayList<>() : pairLogs;
    }

    public List<String> getFallbackLogs() {
        return fallbackLogs;
    }

    public void setFallbackLogs(List<String> fallbackLogs) {
        this.fallbackLogs = fallbackLogs == null ? new ArrayList<>() : fallbackLogs;
    }
}
