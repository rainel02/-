package com.pindou.app.service;

import com.pindou.app.model.*;
import com.pindou.app.repository.BeadProjectRepository;
import com.pindou.app.repository.InventoryRowRepository;
import org.apache.poi.ss.usermodel.ClientAnchor;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.Drawing;
import org.apache.poi.ss.usermodel.Picture;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Base64;
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

    public byte[] exportStockExcel() {
        List<InventoryRow> stockRows = stock();
        List<UsageRow> usageRows = usage();
        List<DemandRow> demandRows = demand();
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet stockSheet = workbook.createSheet("库存表");
            Sheet usageSheet = workbook.createSheet("使用表");
            Sheet demandSheet = workbook.createSheet("需求表");

            Row stockHeader = stockSheet.createRow(0);
            stockHeader.createCell(0).setCellValue("色号");
            stockHeader.createCell(1).setCellValue("入库总量");
            stockHeader.createCell(2).setCellValue("库存余量");
            stockHeader.createCell(3).setCellValue("使用量");
            stockHeader.createCell(4).setCellValue("提醒阈值");
            stockHeader.createCell(5).setCellValue("库存预警");

            int stockRowIndex = 1;
            for (InventoryRow item : stockRows) {
                Row dataRow = stockSheet.createRow(stockRowIndex++);
                dataRow.createCell(0).setCellValue(item.getCode() == null ? "" : item.getCode());
                dataRow.createCell(1).setCellValue(safeInt(item.getInTotal()));
                dataRow.createCell(2).setCellValue(safeInt(item.getRemain()));
                dataRow.createCell(3).setCellValue(safeInt(item.getUsed()));
                dataRow.createCell(4).setCellValue(safeInt(item.getAlertThreshold()));
                dataRow.createCell(5).setCellValue(item.getWarning() == null ? "" : item.getWarning());
            }

            List<ExportProjectColumn> usageProjects = collectProjectsFromUsageRows(usageRows);
            Row usageHeader = usageSheet.createRow(0);
            usageHeader.createCell(0).setCellValue("色号");
            usageHeader.createCell(1).setCellValue("总使用");
            for (int i = 0; i < usageProjects.size(); i++) {
                usageHeader.createCell(2 + i).setCellValue(usageProjects.get(i).projectName());
            }
            Row usageImageRow = usageSheet.createRow(1);
            usageImageRow.setHeightInPoints(90f);
            usageImageRow.createCell(0).setCellValue("");
            usageImageRow.createCell(1).setCellValue("");
            for (int i = 0; i < usageProjects.size(); i++) {
                ExportProjectColumn project = usageProjects.get(i);
                int col = 2 + i;
                if (!insertProjectImage(workbook, usageSheet, 1, col, project.projectImage())) {
                    usageImageRow.createCell(col).setCellValue(project.projectUrl() == null ? "" : project.projectUrl());
                }
            }

            int usageRowIndex = 2;
            for (UsageRow item : usageRows) {
                Row dataRow = usageSheet.createRow(usageRowIndex++);
                dataRow.createCell(0).setCellValue(item.getCode() == null ? "" : item.getCode());
                dataRow.createCell(1).setCellValue(safeInt(item.getUsed()));
                for (int i = 0; i < usageProjects.size(); i++) {
                    ExportProjectColumn project = usageProjects.get(i);
                    dataRow.createCell(2 + i).setCellValue(findProjectUsed(item.getProjects(), project.projectId()));
                }
            }

            List<ExportProjectColumn> demandProjects = collectProjectsFromDemandRows(demandRows);
            Row demandHeader = demandSheet.createRow(0);
            demandHeader.createCell(0).setCellValue("色号");
            demandHeader.createCell(1).setCellValue("库存余量");
            demandHeader.createCell(2).setCellValue("总需求");
            demandHeader.createCell(3).setCellValue("需补库存量");
            for (int i = 0; i < demandProjects.size(); i++) {
                demandHeader.createCell(4 + i).setCellValue(demandProjects.get(i).projectName());
            }
            Row demandImageRow = demandSheet.createRow(1);
            demandImageRow.setHeightInPoints(90f);
            for (int i = 0; i < demandProjects.size(); i++) {
                ExportProjectColumn project = demandProjects.get(i);
                int col = 4 + i;
                if (!insertProjectImage(workbook, demandSheet, 1, col, project.projectImage())) {
                    demandImageRow.createCell(col).setCellValue(project.projectUrl() == null ? "" : project.projectUrl());
                }
            }

            int demandRowIndex = 2;
            for (DemandRow item : demandRows) {
                Row dataRow = demandSheet.createRow(demandRowIndex++);
                dataRow.createCell(0).setCellValue(item.getCode() == null ? "" : item.getCode());
                dataRow.createCell(1).setCellValue(safeInt(item.getRemain()));
                dataRow.createCell(2).setCellValue(sumProjectUsed(item.getProjects()));
                dataRow.createCell(3).setCellValue(safeInt(item.getNeed()));
                for (int i = 0; i < demandProjects.size(); i++) {
                    ExportProjectColumn project = demandProjects.get(i);
                    dataRow.createCell(4 + i).setCellValue(findProjectUsed(item.getProjects(), project.projectId()));
                }
            }

            for (int i = 0; i < 6; i++) {
                stockSheet.autoSizeColumn(i);
            }
            usageSheet.autoSizeColumn(0);
            usageSheet.autoSizeColumn(1);
            for (int i = 0; i < usageProjects.size(); i++) {
                usageSheet.setColumnWidth(2 + i, 5200);
            }
            demandSheet.autoSizeColumn(0);
            demandSheet.autoSizeColumn(1);
            demandSheet.autoSizeColumn(2);
            demandSheet.autoSizeColumn(3);
            for (int i = 0; i < demandProjects.size(); i++) {
                demandSheet.setColumnWidth(4 + i, 5200);
            }

            workbook.write(output);
            return output.toByteArray();
        } catch (Exception exception) {
            throw new IllegalStateException("导出库存 Excel 失败: " + exception.getMessage(), exception);
        }
    }

    private List<ExportProjectColumn> collectProjectsFromUsageRows(List<UsageRow> rows) {
        Map<Long, ExportProjectColumn> map = new HashMap<>();
        List<ExportProjectColumn> ordered = new ArrayList<>();
        for (UsageRow row : rows) {
            if (row == null || row.getProjects() == null) {
                continue;
            }
            for (UsageProjectItem item : row.getProjects()) {
                if (item == null || item.getProjectId() == null) {
                    continue;
                }
                if (!map.containsKey(item.getProjectId())) {
                    ExportProjectColumn project = new ExportProjectColumn(
                            item.getProjectId(),
                            item.getProjectName() == null ? "未命名" : item.getProjectName(),
                            item.getProjectImage(),
                            item.getProjectUrl()
                    );
                    map.put(item.getProjectId(), project);
                    ordered.add(project);
                }
            }
        }
        ordered.sort(Comparator.comparing(ExportProjectColumn::projectName, Comparator.nullsLast(String::compareToIgnoreCase)));
        return ordered;
    }

    private List<ExportProjectColumn> collectProjectsFromDemandRows(List<DemandRow> rows) {
        Map<Long, ExportProjectColumn> map = new HashMap<>();
        List<ExportProjectColumn> ordered = new ArrayList<>();
        for (DemandRow row : rows) {
            if (row == null || row.getProjects() == null) {
                continue;
            }
            for (UsageProjectItem item : row.getProjects()) {
                if (item == null || item.getProjectId() == null) {
                    continue;
                }
                if (!map.containsKey(item.getProjectId())) {
                    ExportProjectColumn project = new ExportProjectColumn(
                            item.getProjectId(),
                            item.getProjectName() == null ? "未命名" : item.getProjectName(),
                            item.getProjectImage(),
                            item.getProjectUrl()
                    );
                    map.put(item.getProjectId(), project);
                    ordered.add(project);
                }
            }
        }
        ordered.sort(Comparator.comparing(ExportProjectColumn::projectName, Comparator.nullsLast(String::compareToIgnoreCase)));
        return ordered;
    }

    private int findProjectUsed(List<UsageProjectItem> projects, Long projectId) {
        if (projects == null || projectId == null) {
            return 0;
        }
        for (UsageProjectItem project : projects) {
            if (project == null || project.getProjectId() == null) {
                continue;
            }
            if (projectId.equals(project.getProjectId())) {
                return safeInt(project.getUsed());
            }
        }
        return 0;
    }

    private int sumProjectUsed(List<UsageProjectItem> projects) {
        int total = 0;
        if (projects == null) {
            return total;
        }
        for (UsageProjectItem item : projects) {
            total += safeInt(item == null ? null : item.getUsed());
        }
        return total;
    }

    private boolean insertProjectImage(Workbook workbook, Sheet sheet, int rowIndex, int colIndex, String dataUrl) {
        try {
            if (dataUrl == null || dataUrl.isBlank()) {
                return false;
            }
            String trimmed = dataUrl.trim();
            int comma = trimmed.indexOf(',');
            if (!trimmed.startsWith("data:") || comma < 0) {
                return false;
            }

            String header = trimmed.substring(0, comma).toLowerCase();
            String payload = trimmed.substring(comma + 1);
            byte[] bytes = Base64.getDecoder().decode(payload);
            int pictureType = Workbook.PICTURE_TYPE_PNG;
            if (header.contains("image/jpeg") || header.contains("image/jpg")) {
                pictureType = Workbook.PICTURE_TYPE_JPEG;
            }

            try (InputStream ignored = new ByteArrayInputStream(bytes)) {
                int pictureIndex = workbook.addPicture(bytes, pictureType);
                CreationHelper helper = workbook.getCreationHelper();
                Drawing<?> drawing = sheet.createDrawingPatriarch();
                ClientAnchor anchor = helper.createClientAnchor();
                anchor.setCol1(colIndex);
                anchor.setRow1(rowIndex);
                anchor.setCol2(colIndex + 1);
                anchor.setRow2(rowIndex + 1);
                Picture picture = drawing.createPicture(anchor, pictureIndex);
                picture.resize(1.0, 1.0);
                return true;
            }
        } catch (Exception ignored) {
            return false;
        }
    }

    private record ExportProjectColumn(Long projectId, String projectName, String projectImage, String projectUrl) {
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
