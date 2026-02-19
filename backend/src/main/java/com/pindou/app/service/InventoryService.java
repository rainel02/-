package com.pindou.app.service;

import com.pindou.app.model.*;
import com.pindou.app.repository.BeadProjectRepository;
import com.pindou.app.repository.InventoryRowRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class InventoryService {
    private static final List<String> CANONICAL_CODES = List.of(
            "A1", "A2", "A3", "A4", "A5", "A6", "A7", "A8", "A9", "A10", "A11", "A12", "A13", "A14", "A15", "A16", "A17", "A18", "A19", "A20", "A21", "A22", "A23", "A24", "A25", "A26",
            "B1", "B2", "B3", "B4", "B5", "B6", "B7", "B8", "B9", "B10", "B11", "B12", "B13", "B14", "B15", "B16", "B17", "B18", "B19", "B20", "B21", "B22", "B23", "B24", "B25", "B26", "B27", "B28", "B29", "B30", "B31", "B32",
            "C1", "C2", "C3", "C4", "C5", "C6", "C7", "C8", "C9", "C10", "C11", "C12", "C13", "C14", "C15", "C16", "C17", "C18", "C19", "C20", "C21", "C22", "C23", "C24", "C25", "C26", "C27", "C28", "C29",
            "D1", "D2", "D3", "D4", "D5", "D6", "D7", "D8", "D9", "D10", "D11", "D12", "D13", "D14", "D15", "D16", "D17", "D18", "D19", "D20", "D21", "D22", "D23", "D24", "D25", "D26",
            "E1", "E2", "E3", "E4", "E5", "E6", "E7", "E8", "E9", "E10", "E11", "E12", "E13", "E14", "E15", "E16", "E17", "E18", "E19", "E20", "E21", "E22", "E23", "E24",
            "F1", "F2", "F3", "F4", "F5", "F6", "F7", "F8", "F9", "F10", "F11", "F12", "F13", "F14", "F15", "F16", "F17", "F18", "F19", "F20", "F21", "F22", "F23", "F24", "F25",
            "G1", "G2", "G3", "G4", "G5", "G6", "G7", "G8", "G9", "G10", "G11", "G12", "G13", "G14", "G15", "G16", "G17", "G18", "G19", "G20", "G21",
            "H1", "H2", "H3", "H4", "H5", "H6", "H7", "H8", "H9", "H10", "H11", "H12", "H13", "H14", "H15", "H16", "H17", "H18", "H19", "H20", "H21", "H22", "H23",
            "M1", "M2", "M3", "M4", "M5", "M6", "M7", "M8", "M9", "M10", "M11", "M12", "M13", "M14", "M15"
    );
    private static final Set<String> SPECIAL_THRESHOLD_CODES = Set.of("H1", "H2", "H7");
    private static final Pattern CODE_PATTERN = Pattern.compile("^([A-Z]+)(\\d+)$");

    private final InventoryRowRepository inventoryRowRepository;
    private final BeadProjectRepository beadProjectRepository;

    public InventoryService(InventoryRowRepository inventoryRowRepository, BeadProjectRepository beadProjectRepository) {
        this.inventoryRowRepository = inventoryRowRepository;
        this.beadProjectRepository = beadProjectRepository;
    }

    public List<InventoryRow> stock() {
        List<InventoryRow> persistedRows = ensureCanonicalRows();
        Map<String, Integer> usedMap = buildColorTotalMapByStatus(Set.of(BeadStatus.IN_PROGRESS, BeadStatus.DONE), true);

        List<InventoryRow> rows = new ArrayList<>();
        for (InventoryRow persisted : persistedRows) {
            String code = normalizeCode(persisted.getCode());
            int inTotal = safeInt(persisted.getInTotal());
            int used = usedMap.getOrDefault(code, 0);
            int remain = Math.max(0, inTotal - used);
            int threshold = safeThreshold(code, persisted.getAlertThreshold());
            String warning = remain < threshold ? "急需补货" : "充足";

            rows.add(new InventoryRow(code, inTotal, remain, used, warning, threshold));
        }

        rows.sort(this::compareInventoryRows);
        return rows;
    }

    public List<UsageRow> usage() {
        Map<String, List<UsageProjectItem>> detailMap = new HashMap<>();
        Map<String, Integer> totalMap = new HashMap<>();

        for (BeadProject project : beadProjectRepository.findAll()) {
            if (project.getStatus() != BeadStatus.IN_PROGRESS && project.getStatus() != BeadStatus.DONE) {
                continue;
            }

            int multiplier = usageMultiplier(project);
            if (multiplier <= 0) {
                continue;
            }

            for (ColorRequirement color : safeColors(project.getRequiredColors())) {
                String code = normalizeCode(color.getCode());
                if (code.isBlank()) {
                    continue;
                }

                int used = safeInt(color.getQuantity()) * multiplier;
                totalMap.put(code, totalMap.getOrDefault(code, 0) + used);
                detailMap.computeIfAbsent(code, key -> new ArrayList<>()).add(
                        new UsageProjectItem(
                                project.getId(),
                                project.getName(),
                                project.getPatternImage(),
                                project.getSourceUrl(),
                                used
                        )
                );
            }
        }

        List<UsageRow> rows = new ArrayList<>();
        for (String code : CANONICAL_CODES) {
            rows.add(new UsageRow(code, totalMap.getOrDefault(code, 0), detailMap.getOrDefault(code, List.of())));
        }
        rows.sort((a, b) -> compareCodes(a.getCode(), b.getCode()));
        return rows;
    }

    public List<DemandRow> demand() {
        Map<String, List<UsageProjectItem>> detailMap = new HashMap<>();
        Map<String, Integer> totalMap = new HashMap<>();

        for (BeadProject project : beadProjectRepository.findAll()) {
            if (project.getStatus() != BeadStatus.TODO) {
                continue;
            }

            int multiplier = planMultiplier(project);
            for (ColorRequirement color : safeColors(project.getRequiredColors())) {
                String code = normalizeCode(color.getCode());
                if (code.isBlank()) {
                    continue;
                }

                int need = safeInt(color.getQuantity()) * multiplier;
                totalMap.put(code, totalMap.getOrDefault(code, 0) + need);
                detailMap.computeIfAbsent(code, key -> new ArrayList<>()).add(
                        new UsageProjectItem(
                                project.getId(),
                                project.getName(),
                                project.getPatternImage(),
                                project.getSourceUrl(),
                                need
                        )
                );
            }
        }

        Map<String, InventoryRow> stockMap = new HashMap<>();
        for (InventoryRow row : stock()) {
            stockMap.put(normalizeCode(row.getCode()), row);
        }

        List<DemandRow> rows = new ArrayList<>();
        for (String code : CANONICAL_CODES) {
            int totalNeed = totalMap.getOrDefault(code, 0);
            InventoryRow stock = stockMap.get(code);
            int remain = stock == null ? 0 : safeInt(stock.getRemain());
            int need = Math.max(0, totalNeed - remain);
            rows.add(new DemandRow(code, remain, need, detailMap.getOrDefault(code, List.of())));
        }

        rows.sort((a, b) -> compareCodes(a.getCode(), b.getCode()));
        return rows;
    }

    public List<TodoProjectRow> todoProjects() {
        List<TodoProjectRow> rows = new ArrayList<>();
        for (BeadProject project : beadProjectRepository.findAll()) {
            if (project.getStatus() != BeadStatus.TODO) {
                continue;
            }

            int multiplier = planMultiplier(project);
            List<ColorRequirement> scaledColors = new ArrayList<>();
            for (ColorRequirement color : safeColors(project.getRequiredColors())) {
                String code = normalizeCode(color.getCode());
                if (code.isBlank()) {
                    continue;
                }
                int quantity = safeInt(color.getQuantity()) * multiplier;
                scaledColors.add(new ColorRequirement(code, quantity));
            }
            scaledColors.sort((a, b) -> compareCodes(a.getCode(), b.getCode()));

            rows.add(new TodoProjectRow(
                    project.getId(),
                    project.getName(),
                    project.getPatternImage(),
                    project.getSourceUrl(),
                    multiplier,
                    scaledColors
            ));
        }

        rows.sort(Comparator.comparing(TodoProjectRow::getProjectName, Comparator.nullsLast(String::compareToIgnoreCase)));
        return rows;
    }

    public List<InventoryRow> restock(Map<String, Integer> increments) {
        List<InventoryRow> rows = ensureCanonicalRows();
        Map<String, InventoryRow> rowMap = toRowMap(rows);

        for (Map.Entry<String, Integer> entry : safeMap(increments).entrySet()) {
            String code = normalizeCode(entry.getKey());
            int add = Math.max(0, safeInt(entry.getValue()));
            if (code.isBlank() || add <= 0) {
                continue;
            }

            InventoryRow row = rowMap.computeIfAbsent(code, key -> {
                InventoryRow created = new InventoryRow(key, 0, 0, 0, "充足", defaultThreshold(key));
                rows.add(created);
                return created;
            });
            row.setInTotal(safeInt(row.getInTotal()) + add);
            if (row.getAlertThreshold() == null) {
                row.setAlertThreshold(defaultThreshold(code));
            }
        }

        inventoryRowRepository.saveAll(rows);
        return stock();
    }

    public List<InventoryRow> updateThresholds(Map<String, Integer> thresholds) {
        List<InventoryRow> rows = ensureCanonicalRows();
        Map<String, InventoryRow> rowMap = toRowMap(rows);

        for (Map.Entry<String, Integer> entry : safeMap(thresholds).entrySet()) {
            String code = normalizeCode(entry.getKey());
            int threshold = Math.max(0, safeInt(entry.getValue()));
            if (code.isBlank()) {
                continue;
            }
            InventoryRow row = rowMap.get(code);
            if (row != null) {
                row.setAlertThreshold(threshold);
            }
        }

        inventoryRowRepository.saveAll(rows);
        return stock();
    }

    public List<InventoryRow> updateInTotals(Map<String, Integer> totals) {
        List<InventoryRow> rows = ensureCanonicalRows();
        Map<String, InventoryRow> rowMap = toRowMap(rows);

        for (Map.Entry<String, Integer> entry : safeMap(totals).entrySet()) {
            String code = normalizeCode(entry.getKey());
            int total = Math.max(0, safeInt(entry.getValue()));
            if (code.isBlank()) {
                continue;
            }
            InventoryRow row = rowMap.get(code);
            if (row != null) {
                row.setInTotal(total);
            }
        }

        inventoryRowRepository.saveAll(rows);
        return stock();
    }

    private List<InventoryRow> ensureCanonicalRows() {
        List<InventoryRow> existingRows = inventoryRowRepository.findAll();
        Map<String, Integer> inTotalMap = new HashMap<>();
        Map<String, Integer> thresholdMap = new HashMap<>();
        Set<String> normalizedSeen = new HashSet<>();
        boolean requiresRewrite = false;

        for (InventoryRow row : existingRows) {
            String rawCode = row.getCode() == null ? "" : row.getCode().trim().toUpperCase();
            String code = normalizeCode(rawCode);
            if (code.isBlank()) {
                requiresRewrite = true;
                continue;
            }
            if (!code.equals(rawCode)) {
                requiresRewrite = true;
            }
            if (!normalizedSeen.add(code)) {
                requiresRewrite = true;
            }

            inTotalMap.merge(code, safeInt(row.getInTotal()), Integer::sum);
            if (row.getAlertThreshold() != null) {
                thresholdMap.merge(code, Math.max(0, row.getAlertThreshold()), Math::max);
            }
        }

        LinkedHashSet<String> finalCodes = new LinkedHashSet<>(CANONICAL_CODES);
        finalCodes.addAll(inTotalMap.keySet());

        List<InventoryRow> normalizedRows = new ArrayList<>();
        for (String code : finalCodes) {
            int inTotal = inTotalMap.getOrDefault(code, 0);
            int threshold = thresholdMap.getOrDefault(code, defaultThreshold(code));
            normalizedRows.add(new InventoryRow(code, inTotal, 0, 0, "充足", threshold));
        }

        if (existingRows.size() != normalizedRows.size()) {
            requiresRewrite = true;
        }

        if (!requiresRewrite) {
            Map<String, InventoryRow> existingMap = new HashMap<>();
            for (InventoryRow row : existingRows) {
                existingMap.put(normalizeCode(row.getCode()), row);
            }
            for (InventoryRow normalized : normalizedRows) {
                InventoryRow existing = existingMap.get(normalized.getCode());
                if (existing == null) {
                    requiresRewrite = true;
                    break;
                }
                int existingInTotal = safeInt(existing.getInTotal());
                int existingThreshold = safeThreshold(normalized.getCode(), existing.getAlertThreshold());
                if (existingInTotal != safeInt(normalized.getInTotal())
                        || existingThreshold != safeInt(normalized.getAlertThreshold())) {
                    requiresRewrite = true;
                    break;
                }
            }
        }

        if (requiresRewrite) {
            inventoryRowRepository.deleteAllInBatch();
            inventoryRowRepository.saveAll(normalizedRows);
            return normalizedRows;
        }

        return existingRows;
    }

    private Map<String, Integer> buildColorTotalMapByStatus(Set<BeadStatus> statuses, boolean usageMode) {
        Map<String, Integer> totalMap = new HashMap<>();

        for (BeadProject project : beadProjectRepository.findAll()) {
            if (!statuses.contains(project.getStatus())) {
                continue;
            }
            int multiplier = usageMode ? usageMultiplier(project) : planMultiplier(project);
            if (multiplier <= 0) {
                continue;
            }
            for (ColorRequirement color : safeColors(project.getRequiredColors())) {
                String code = normalizeCode(color.getCode());
                if (code.isBlank()) {
                    continue;
                }
                int quantity = safeInt(color.getQuantity()) * multiplier;
                totalMap.put(code, totalMap.getOrDefault(code, 0) + quantity);
            }
        }

        return totalMap;
    }

    private Map<String, InventoryRow> toRowMap(List<InventoryRow> rows) {
        Map<String, InventoryRow> map = new HashMap<>();
        for (InventoryRow row : rows) {
            map.put(normalizeCode(row.getCode()), row);
        }
        return map;
    }

    private int usageMultiplier(BeadProject project) {
        if (project.getStatus() == BeadStatus.DONE || project.getStatus() == BeadStatus.IN_PROGRESS) {
            return planMultiplier(project);
        }
        return 0;
    }

    private int planMultiplier(BeadProject project) {
        return Math.max(1, safeInt(project.getQuantityPlan()));
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    private int safeThreshold(String code, Integer threshold) {
        return threshold == null ? defaultThreshold(code) : Math.max(0, threshold);
    }

    private int defaultThreshold(String code) {
        return SPECIAL_THRESHOLD_CODES.contains(normalizeCode(code)) ? 500 : 200;
    }

    private String normalizeCode(String code) {
        if (code == null) {
            return "";
        }
        String upper = code.trim().toUpperCase();
        if (upper.isBlank()) {
            return "";
        }
        Matcher matcher = CODE_PATTERN.matcher(upper);
        if (!matcher.matches()) {
            return upper;
        }
        String prefix = matcher.group(1);
        String number = matcher.group(2).replaceFirst("^0+(?=\\d)", "");
        return prefix + number;
    }

    private List<ColorRequirement> safeColors(List<ColorRequirement> colors) {
        return colors == null ? List.of() : colors;
    }

    private Map<String, Integer> safeMap(Map<String, Integer> raw) {
        return raw == null ? Map.of() : raw;
    }

    private int compareInventoryRows(InventoryRow left, InventoryRow right) {
        return compareCodes(left.getCode(), right.getCode());
    }

    private int compareCodes(String leftCode, String rightCode) {
        String left = normalizeCode(leftCode);
        String right = normalizeCode(rightCode);

        Matcher leftMatcher = CODE_PATTERN.matcher(left);
        Matcher rightMatcher = CODE_PATTERN.matcher(right);

        if (leftMatcher.matches() && rightMatcher.matches()) {
            String leftGroup = leftMatcher.group(1);
            String rightGroup = rightMatcher.group(1);
            int letterCompare = leftGroup.compareTo(rightGroup);
            if (letterCompare != 0) {
                return letterCompare;
            }
            int leftNumber = Integer.parseInt(leftMatcher.group(2));
            int rightNumber = Integer.parseInt(rightMatcher.group(2));
            return Integer.compare(leftNumber, rightNumber);
        }

        return left.compareTo(right);
    }
}
