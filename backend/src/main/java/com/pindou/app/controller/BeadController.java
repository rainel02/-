package com.pindou.app.controller;

import com.pindou.app.model.BeadProject;
import com.pindou.app.model.BeadProjectSummary;
import com.pindou.app.model.BeadStatus;
import com.pindou.app.model.ColorExtractionDebugResult;
import com.pindou.app.model.ColorRequirement;
import com.pindou.app.model.GridAnalysisResult;
import com.pindou.app.service.BeadService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/beads")
public class BeadController {
    private final BeadService beadService;

    public BeadController(BeadService beadService) {
        this.beadService = beadService;
    }

    @GetMapping
    public List<BeadProjectSummary> list() {
        return beadService.listSummary();
    }

    @GetMapping("/{id}")
    public BeadProject detail(@PathVariable Long id) {
        return beadService.find(id);
    }

    @PostMapping
    public BeadProject create(@RequestBody BeadProject request) {
        return beadService.create(request);
    }

    @PutMapping("/{id}")
    public BeadProject update(@PathVariable Long id, @RequestBody BeadProject request) {
        return beadService.updateProject(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        beadService.delete(id);
    }

    @PatchMapping("/{id}/status")
    public BeadProjectSummary updateStatus(@PathVariable Long id, @RequestBody Map<String, Object> request) {
        BeadStatus status = request.get("status") == null ? null : BeadStatus.valueOf(String.valueOf(request.get("status")));
        Integer quantityDone = request.get("quantityDone") == null ? null : Integer.valueOf(String.valueOf(request.get("quantityDone")));
        Integer quantityPlan = request.get("quantityPlan") == null ? null : Integer.valueOf(String.valueOf(request.get("quantityPlan")));
        return beadService.updateStatusSummary(id, status, quantityDone, quantityPlan);
    }

    @PutMapping("/{id}/required-colors")
    public BeadProject saveRequiredColors(@PathVariable Long id, @RequestBody List<ColorRequirement> colors) {
        return beadService.saveRequiredColors(id, colors);
    }

    @PutMapping("/{id}/grid-result")
    public BeadProject saveGridResult(@PathVariable Long id, @RequestBody Map<String, Object> request) {
        Integer rows = request.get("rows") == null ? null : Integer.valueOf(String.valueOf(request.get("rows")));
        Integer cols = request.get("cols") == null ? null : Integer.valueOf(String.valueOf(request.get("cols")));

        List<String> cells = List.of();
        Object cellsObj = request.get("cells");
        if (cellsObj instanceof List<?> rawCells) {
            cells = rawCells.stream().map(item -> item == null ? "" : String.valueOf(item)).toList();
        }

        return beadService.saveGridResult(id, rows, cols, cells);
    }

    @PostMapping("/extract-colors")
    public List<ColorRequirement> extractColors(@RequestBody Map<String, String> request) {
        return beadService.extractColorsFromBaidu(request.get("imageBase64"));
    }

    @PostMapping("/extract-colors-debug")
    public ColorExtractionDebugResult extractColorsDebug(@RequestBody Map<String, String> request) {
        return beadService.extractColorsDebugFromBaidu(request.get("imageBase64"));
    }

    @PostMapping("/analyze-grid")
    public GridAnalysisResult analyzeGrid(@RequestBody Map<String, Object> request) {
        String imageBase64 = request.get("imageBase64") == null ? null : String.valueOf(request.get("imageBase64"));
        Integer rows = request.get("rows") == null ? null : Integer.valueOf(String.valueOf(request.get("rows")));
        Integer cols = request.get("cols") == null ? null : Integer.valueOf(String.valueOf(request.get("cols")));
        Integer imageWidth = request.get("imageWidth") == null ? null : Integer.valueOf(String.valueOf(request.get("imageWidth")));
        Integer imageHeight = request.get("imageHeight") == null ? null : Integer.valueOf(String.valueOf(request.get("imageHeight")));

        @SuppressWarnings("unchecked")
        List<String> candidateCodes = (List<String>) request.get("candidateCodes");

        return beadService.analyzeGridFromBaidu(imageBase64, rows, cols, imageWidth, imageHeight, candidateCodes);
    }

    @GetMapping("/tags")
    public List<String> tags() {
        return beadService.allTags();
    }

    @DeleteMapping("/tags/{tag}")
    public Map<String, Integer> deleteTag(@PathVariable String tag) {
        int affectedProjects = beadService.removeTagFromAllProjects(tag);
        return Map.of("affectedProjects", affectedProjects);
    }
}
