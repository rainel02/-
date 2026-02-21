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
    public List<ColorRequirement> extractColors(@RequestBody Map<String, Object> request) {
        String imageBase64 = resolveImageBase64(request);

        CropRect cropRect = parseCropRect(request.get("cropRect"));
        if (cropRect == null) {
            return beadService.extractColorsFromBaidu(imageBase64);
        }
        return beadService.extractColorsFromBaidu(imageBase64, cropRect.x(), cropRect.y(), cropRect.width(), cropRect.height());
    }

    @PostMapping("/extract-colors-debug")
    public ColorExtractionDebugResult extractColorsDebug(@RequestBody Map<String, Object> request) {
        String imageBase64 = resolveImageBase64(request);

        CropRect cropRect = parseCropRect(request.get("cropRect"));
        if (cropRect == null) {
            return beadService.extractColorsDebugFromBaidu(imageBase64);
        }
        return beadService.extractColorsDebugFromBaidu(imageBase64, cropRect.x(), cropRect.y(), cropRect.width(), cropRect.height());
    }

    @PostMapping("/analyze-grid")
    public GridAnalysisResult analyzeGrid(@RequestBody Map<String, Object> request) {
        String imageBase64 = resolveImageBase64(request);
        Integer rows = request.get("rows") == null ? null : Integer.valueOf(String.valueOf(request.get("rows")));
        Integer cols = request.get("cols") == null ? null : Integer.valueOf(String.valueOf(request.get("cols")));
        Integer imageWidth = request.get("imageWidth") == null ? null : Integer.valueOf(String.valueOf(request.get("imageWidth")));
        Integer imageHeight = request.get("imageHeight") == null ? null : Integer.valueOf(String.valueOf(request.get("imageHeight")));
        CropRect cropRect = parseCropRect(request.get("cropRect"));

        @SuppressWarnings("unchecked")
        List<String> candidateCodes = (List<String>) request.get("candidateCodes");

        @SuppressWarnings("unchecked")
        Map<String, Object> rawQuantities = (Map<String, Object>) request.get("candidateQuantities");
        Map<String, Integer> candidateQuantities = new java.util.HashMap<>();
        if (rawQuantities != null) {
            for (Map.Entry<String, Object> entry : rawQuantities.entrySet()) {
                if (entry.getKey() == null || entry.getValue() == null) {
                    continue;
                }
                try {
                    candidateQuantities.put(entry.getKey(), Integer.parseInt(String.valueOf(entry.getValue())));
                } catch (Exception ignored) {
                }
            }
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> rawColorHex = (Map<String, Object>) request.get("candidateColorHex");
        Map<String, String> candidateColorHex = new java.util.HashMap<>();
        if (rawColorHex != null) {
            for (Map.Entry<String, Object> entry : rawColorHex.entrySet()) {
                if (entry.getKey() == null || entry.getValue() == null) {
                    continue;
                }
                String code = String.valueOf(entry.getKey()).trim().toUpperCase();
                String hex = String.valueOf(entry.getValue()).trim();
                if (!code.isBlank() && !hex.isBlank()) {
                    candidateColorHex.put(code, hex);
                }
            }
        }

        if (cropRect == null) {
            return beadService.analyzeGridFromBaidu(imageBase64, rows, cols, imageWidth, imageHeight, candidateCodes, candidateQuantities, candidateColorHex);
        }
        return beadService.analyzeGridFromBaidu(
                imageBase64,
                rows,
                cols,
                imageWidth,
                imageHeight,
                candidateCodes,
                candidateQuantities,
                candidateColorHex,
                cropRect.x(),
                cropRect.y(),
                cropRect.width(),
                cropRect.height()
        );
    }

    @SuppressWarnings("unchecked")
    private String resolveImageBase64(Map<String, Object> request) {
        Object directOriginal = request.get("originalImageBase64");
        String resolved = coerceImageBase64(directOriginal);
        if (resolved != null && !resolved.isBlank()) {
            return resolved;
        }

        Object directImage = request.get("imageBase64");
        resolved = coerceImageBase64(directImage);
        if (resolved != null && !resolved.isBlank()) {
            return resolved;
        }

        Object payload = request.get("payload");
        if (payload instanceof Map<?, ?> rawPayload) {
            resolved = resolveImageBase64((Map<String, Object>) rawPayload);
            if (resolved != null && !resolved.isBlank()) {
                return resolved;
            }
        }

        Object data = request.get("data");
        if (data instanceof Map<?, ?> rawData) {
            resolved = resolveImageBase64((Map<String, Object>) rawData);
            if (resolved != null && !resolved.isBlank()) {
                return resolved;
            }
        }

        return coerceImageBase64(String.valueOf(request));
    }

    @SuppressWarnings("unchecked")
    private String coerceImageBase64(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String text) {
            return extractFromPossiblyWrappedText(text);
        }
        if (value instanceof Map<?, ?> rawMap) {
            Map<String, Object> map = (Map<String, Object>) rawMap;
            String resolved = coerceImageBase64(map.get("originalImageBase64"));
            if (resolved != null && !resolved.isBlank()) {
                return resolved;
            }
            resolved = coerceImageBase64(map.get("imageBase64"));
            if (resolved != null && !resolved.isBlank()) {
                return resolved;
            }
            return extractFromPossiblyWrappedText(String.valueOf(map));
        }
        return extractFromPossiblyWrappedText(String.valueOf(value));
    }

    private String extractFromPossiblyWrappedText(String text) {
        if (text == null) {
            return null;
        }
        String trimmed = text.trim();
        if (trimmed.isBlank()) {
            return null;
        }

        if (trimmed.startsWith("data:")) {
            return trimmed;
        }

        String fromMapOriginal = extractMapStyleValue(trimmed, "originalImageBase64=");
        if (fromMapOriginal != null && !fromMapOriginal.isBlank()) {
            return fromMapOriginal;
        }
        String fromMapImage = extractMapStyleValue(trimmed, "imageBase64=");
        if (fromMapImage != null && !fromMapImage.isBlank()) {
            return fromMapImage;
        }

        String jsonOriginalToken = "\"originalImageBase64\":\"";
        int jsonOriginalIndex = trimmed.indexOf(jsonOriginalToken);
        if (jsonOriginalIndex >= 0) {
            int start = jsonOriginalIndex + jsonOriginalToken.length();
            int end = trimmed.indexOf('"', start);
            if (end > start) {
                return trimmed.substring(start, end);
            }
        }

        String jsonImageToken = "\"imageBase64\":\"";
        int jsonImageIndex = trimmed.indexOf(jsonImageToken);
        if (jsonImageIndex >= 0) {
            int start = jsonImageIndex + jsonImageToken.length();
            int end = trimmed.indexOf('"', start);
            if (end > start) {
                return trimmed.substring(start, end);
            }
        }

        return trimmed;
    }

    private String extractMapStyleValue(String text, String keyToken) {
        int keyIndex = text.indexOf(keyToken);
        if (keyIndex < 0) {
            return null;
        }
        int start = keyIndex + keyToken.length();
        int end = text.indexOf(", ", start);
        if (end < 0) {
            end = text.lastIndexOf('}');
        }
        if (end < 0 || end <= start) {
            end = text.length();
        }
        String value = text.substring(start, end).trim();
        if ((value.startsWith("\"") && value.endsWith("\"")) || (value.startsWith("'") && value.endsWith("'"))) {
            value = value.substring(1, value.length() - 1);
        }
        return value;
    }

    @SuppressWarnings("unchecked")
    private CropRect parseCropRect(Object cropRectObj) {
        if (!(cropRectObj instanceof Map<?, ?> raw)) {
            return null;
        }
        try {
            Map<String, Object> map = (Map<String, Object>) raw;
            int x = Integer.parseInt(String.valueOf(map.get("x")));
            int y = Integer.parseInt(String.valueOf(map.get("y")));
            int width = Integer.parseInt(String.valueOf(map.get("width")));
            int height = Integer.parseInt(String.valueOf(map.get("height")));
            if (width <= 0 || height <= 0) {
                return null;
            }
            return new CropRect(x, y, width, height);
        } catch (Exception ignored) {
            return null;
        }
    }

    private record CropRect(int x, int y, int width, int height) {}

    @GetMapping("/tags")
    public List<String> tags() {
        return beadService.allTags();
    }

    @PostMapping("/tags")
    public Map<String, String> addTag(@RequestBody Map<String, Object> request) {
        String tag = request.get("tag") == null ? null : String.valueOf(request.get("tag"));
        String created = beadService.addTagOption(tag);
        return Map.of("tag", created);
    }

    @DeleteMapping("/tags/{tag}")
    public Map<String, Integer> deleteTag(@PathVariable String tag) {
        int affectedProjects = beadService.removeTagFromAllProjects(tag);
        return Map.of("affectedProjects", affectedProjects);
    }

    @GetMapping("/ocr-settings")
    public Map<String, String> getOcrSettings() {
        return beadService.getOcrSettings();
    }

    @PutMapping("/ocr-settings")
    public Map<String, String> saveOcrSettings(@RequestBody Map<String, Object> request) {
        return beadService.saveOcrSettings(request);
    }
}
