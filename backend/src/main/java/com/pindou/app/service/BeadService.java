package com.pindou.app.service;

import com.pindou.app.model.*;
import com.pindou.app.repository.BeadProjectRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class BeadService {
    private final BeadProjectRepository projectRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Value("${baidu.ocr.ak:}")
    private String baiduApiKey;

    @Value("${baidu.ocr.sk:}")
    private String baiduSecretKey;

    @Value("${baidu.ocr.ak2:}")
    private String baiduApiKey2;

    @Value("${baidu.ocr.sk2:}")
    private String baiduSecretKey2;

    public BeadService(BeadProjectRepository projectRepository) {
        this.projectRepository = projectRepository;
    }

    public List<BeadProject> list() {
        return projectRepository.findAll();
    }

    public List<BeadProjectSummary> listSummary() {
        List<BeadProjectSummary> result = new ArrayList<>();
        for (BeadProject project : projectRepository.findAll()) {
            result.add(toSummary(project));
        }
        return result;
    }

    public BeadProject find(Long id) {
        return projectRepository.findById(id).orElseThrow();
    }

    public BeadProject create(BeadProject input) {
        BeadProject p = new BeadProject();
        p.setName(input.getName());
        p.setTags(normalizeTags(input.getTags()));
        p.setStatus(input.getStatus() == null ? BeadStatus.TODO : input.getStatus());
        p.setSourceUrl(input.getSourceUrl());
        p.setPatternImage(input.getPatternImage());
        p.setWorkImage(input.getWorkImage());
        p.setQuantityDone(input.getQuantityDone() == null ? 0 : input.getQuantityDone());
        p.setQuantityPlan(input.getQuantityPlan() == null ? 1 : input.getQuantityPlan());
        p.setRequiredColors(normalizeRequiredColors(input.getRequiredColors()));
        return projectRepository.save(p);
    }

    public BeadProject updateProject(Long id, BeadProject input) {
        BeadProject p = find(id);
        if (input.getName() != null && !input.getName().trim().isBlank()) {
            p.setName(input.getName().trim());
        }
        if (input.getTags() != null) {
            p.setTags(normalizeTags(input.getTags()));
        }
        if (input.getSourceUrl() != null) {
            p.setSourceUrl(normalizeOptionalText(input.getSourceUrl()));
        }
        if (input.getPatternImage() != null) {
            p.setPatternImage(normalizeOptionalText(input.getPatternImage()));
        }
        if (input.getWorkImage() != null) {
            p.setWorkImage(normalizeOptionalText(input.getWorkImage()));
        }
        return projectRepository.save(p);
    }

    public void delete(Long id) {
        BeadProject p = find(id);
        projectRepository.delete(p);
    }

    public BeadProject updateStatus(Long id, BeadStatus status, Integer done, Integer plan) {
        BeadProject p = find(id);
        if (status != null) p.setStatus(status);
        if (done != null) p.setQuantityDone(done);
        if (plan != null) p.setQuantityPlan(plan);
        return projectRepository.save(p);
    }

    public BeadProjectSummary updateStatusSummary(Long id, BeadStatus status, Integer done, Integer plan) {
        return toSummary(updateStatus(id, status, done, plan));
    }

    public BeadProject saveRequiredColors(Long id, List<ColorRequirement> colors) {
        BeadProject p = find(id);
        p.setRequiredColors(normalizeRequiredColors(colors));
        return projectRepository.save(p);
    }

    public BeadProject saveGridResult(Long id, Integer rows, Integer cols, List<String> cells) {
        BeadProject p = find(id);
        p.setGridRows(rows);
        p.setGridCols(cols);
        List<String> safeCells = cells == null ? new ArrayList<>() : cells;
        List<String> normalizedCells = new ArrayList<>(safeCells.size());
        for (String cell : safeCells) {
            String normalized = normalizeColorCode(cell);
            normalizedCells.add(normalized);
        }
        try {
            p.setGridCellsJson(objectMapper.writeValueAsString(normalizedCells));
        } catch (Exception exception) {
            throw new IllegalStateException("保存网格结果失败: " + exception.getMessage(), exception);
        }
        return projectRepository.save(p);
    }

    public List<ColorRequirement> extractColorsFromBaidu(String imageBase64) {
        String base64 = stripDataUrlPrefix(imageBase64);
        if (base64 == null || base64.isBlank()) {
            return List.of();
        }

        List<KeyPair> keyPairs = buildKeyPairs();
        if (keyPairs.isEmpty()) {
            throw new IllegalStateException("百度 OCR 未配置，请在 backend/application.yml 或环境变量中设置 baidu.ocr.ak / baidu.ocr.sk");
        }

        RuntimeException lastError = null;
        for (KeyPair keyPair : keyPairs) {
            try {
                String accessToken = fetchBaiduAccessToken(keyPair.ak(), keyPair.sk());
                List<OcrWordBox> words = fetchBaiduOcrWordsWithLocation(accessToken, base64);
                List<ColorRequirement> byLocation = parseColorRequirementsByLocation(words);
                if (!byLocation.isEmpty()) {
                    return byLocation;
                }
                String text = fetchBaiduOcrRawText(accessToken, base64);
                return parseColorRequirements(text);
            } catch (RuntimeException exception) {
                lastError = exception;
                if (!isQuotaError(exception.getMessage())) {
                    break;
                }
            }
        }

        if (lastError != null) {
            throw lastError;
        }
        return List.of();
    }

    public ColorExtractionDebugResult extractColorsDebugFromBaidu(String imageBase64) {
        String base64 = stripDataUrlPrefix(imageBase64);
        ColorExtractionDebugResult result = new ColorExtractionDebugResult();
        if (base64 == null || base64.isBlank()) {
            result.setStrategy("empty-image");
            result.setColors(List.of());
            return result;
        }

        List<KeyPair> keyPairs = buildKeyPairs();
        if (keyPairs.isEmpty()) {
            throw new IllegalStateException("百度 OCR 未配置，请在 backend/application.yml 或环境变量中设置 baidu.ocr.ak / baidu.ocr.sk");
        }

        RuntimeException lastError = null;
        for (KeyPair keyPair : keyPairs) {
            try {
                String accessToken = fetchBaiduAccessToken(keyPair.ak(), keyPair.sk());

                List<String> locationLinesDebug = new ArrayList<>();
                List<String> pairLogs = new ArrayList<>();
                List<OcrWordBox> words = fetchBaiduOcrWordsWithLocation(accessToken, base64);
                List<ColorRequirement> byLocation = parseColorRequirementsByLocation(words, locationLinesDebug, pairLogs);
                result.setLocationLines(locationLinesDebug);
                result.setPairLogs(pairLogs);

                String rawText = fetchBaiduOcrRawText(accessToken, base64);
                result.setRawText(rawText);

                if (!byLocation.isEmpty()) {
                    result.setStrategy("location-" + resolvePairingStrategy(pairLogs));
                    result.setColors(byLocation);
                    return result;
                }

                List<String> fallbackLogs = new ArrayList<>();
                List<ColorRequirement> byText = parseColorRequirements(rawText, fallbackLogs);
                result.setFallbackLogs(fallbackLogs);
                result.setStrategy("text-" + resolvePairingStrategy(fallbackLogs));
                result.setColors(byText);
                return result;
            } catch (RuntimeException exception) {
                lastError = exception;
                if (!isQuotaError(exception.getMessage())) {
                    break;
                }
            }
        }

        if (lastError != null) {
            throw lastError;
        }

        result.setStrategy("no-result");
        result.setColors(List.of());
        return result;
    }

    public GridAnalysisResult analyzeGridFromBaidu(
            String imageBase64,
            Integer rows,
            Integer cols,
            Integer imageWidth,
            Integer imageHeight,
            List<String> candidateCodes
    ) {
        int gridRows = rows == null ? 0 : rows;
        int gridCols = cols == null ? 0 : cols;
        int width = imageWidth == null ? 0 : imageWidth;
        int height = imageHeight == null ? 0 : imageHeight;

        if (gridRows <= 0 || gridCols <= 0 || width <= 0 || height <= 0) {
            throw new IllegalArgumentException("网格参数无效，请检查 rows/cols/imageWidth/imageHeight");
        }

        String base64 = stripDataUrlPrefix(imageBase64);
        if (base64 == null || base64.isBlank()) {
            GridAnalysisResult empty = new GridAnalysisResult();
            empty.setRows(gridRows);
            empty.setCols(gridCols);
            return empty;
        }

        List<KeyPair> keyPairs = buildKeyPairs();
        if (keyPairs.isEmpty()) {
            throw new IllegalStateException("百度 OCR 未配置，请在 backend/application.yml 或环境变量中设置 baidu.ocr.ak / baidu.ocr.sk");
        }

        RuntimeException lastError = null;
        for (KeyPair keyPair : keyPairs) {
            try {
                String accessToken = fetchBaiduAccessToken(keyPair.ak(), keyPair.sk());
                List<OcrWordBox> words = fetchBaiduOcrWordsWithLocation(accessToken, base64);
                return mapWordsToGrid(words, gridRows, gridCols, width, height, candidateCodes);
            } catch (RuntimeException exception) {
                lastError = exception;
                if (!isQuotaError(exception.getMessage())) {
                    break;
                }
            }
        }

        if (lastError != null) {
            throw lastError;
        }
        GridAnalysisResult empty = new GridAnalysisResult();
        empty.setRows(gridRows);
        empty.setCols(gridCols);
        return empty;
    }

    public List<String> allTags() {
        Set<String> tags = new LinkedHashSet<>();
        for (BeadProject project : projectRepository.findAll()) {
            if (project.getTags() == null) {
                continue;
            }
            for (String tag : project.getTags()) {
                String normalized = normalizeTag(tag);
                if (normalized != null && !normalized.isBlank()) {
                    tags.add(normalized);
                }
            }
        }
        return new ArrayList<>(tags);
    }

    public int removeTagFromAllProjects(String rawTag) {
        String targetTag = normalizeTag(rawTag);
        if (targetTag == null || targetTag.isBlank()) {
            return 0;
        }

        int changedCount = 0;
        List<BeadProject> projects = projectRepository.findAll();
        for (BeadProject project : projects) {
            List<String> currentTags = normalizeTags(project.getTags());
            int beforeSize = currentTags.size();
            List<String> filteredTags = new ArrayList<>();
            for (String tag : currentTags) {
                if (!targetTag.equals(tag)) {
                    filteredTags.add(tag);
                }
            }
            if (filteredTags.size() != beforeSize) {
                project.setTags(filteredTags);
                projectRepository.save(project);
                changedCount++;
            }
        }
        return changedCount;
    }

    private BeadProjectSummary toSummary(BeadProject project) {
        BeadProjectSummary summary = new BeadProjectSummary();
        summary.setId(project.getId());
        summary.setName(project.getName());
        summary.setTags(normalizeTags(project.getTags()));
        summary.setStatus(project.getStatus());
        summary.setSourceUrl(project.getSourceUrl());
        summary.setPatternImage(project.getPatternImage());
        summary.setQuantityDone(project.getQuantityDone());
        summary.setQuantityPlan(project.getQuantityPlan());
        return summary;
    }

    private List<String> normalizeTags(List<String> rawTags) {
        List<String> result = new ArrayList<>();
        if (rawTags == null) {
            return result;
        }
        for (String tag : rawTags) {
            String normalized = normalizeTag(tag);
            if (normalized != null && !normalized.isBlank()) {
                result.add(normalized);
            }
        }
        return result;
    }

    private String normalizeTag(String rawTag) {
        if (rawTag == null) {
            return null;
        }
        String trimmed = rawTag.trim();
        if (trimmed.isBlank()) {
            return "";
        }
        if (!looksLikeMojibake(trimmed)) {
            return trimmed;
        }
        String repaired = new String(trimmed.getBytes(StandardCharsets.ISO_8859_1), StandardCharsets.UTF_8);
        if (repaired.contains("�")) {
            return trimmed;
        }
        return repaired;
    }

    private String normalizeOptionalText(String input) {
        if (input == null) {
            return null;
        }
        String trimmed = input.trim();
        return trimmed.isBlank() ? null : trimmed;
    }

    private boolean looksLikeMojibake(String value) {
        return value.contains("Ã")
                || value.contains("Â")
                || value.contains("å")
                || value.contains("ä")
                || value.contains("ç")
                || value.contains("é")
                || value.contains("è")
                || value.contains("ð");
    }

    private List<KeyPair> buildKeyPairs() {
        List<KeyPair> keyPairs = new ArrayList<>();
        if (isNonBlank(baiduApiKey) && isNonBlank(baiduSecretKey)) {
            keyPairs.add(new KeyPair(baiduApiKey.trim(), baiduSecretKey.trim()));
        }
        if (isNonBlank(baiduApiKey2) && isNonBlank(baiduSecretKey2)) {
            keyPairs.add(new KeyPair(baiduApiKey2.trim(), baiduSecretKey2.trim()));
        }
        return keyPairs;
    }

    private boolean isNonBlank(String value) {
        return value != null && !value.isBlank();
    }

    private String stripDataUrlPrefix(String imageBase64) {
        if (imageBase64 == null) {
            return null;
        }
        int commaIndex = imageBase64.indexOf(',');
        if (imageBase64.startsWith("data:") && commaIndex >= 0) {
            return imageBase64.substring(commaIndex + 1);
        }
        return imageBase64;
    }

    private String fetchBaiduAccessToken(String ak, String sk) {
        try {
            String url = "https://aip.baidubce.com/oauth/2.0/token"
                    + "?grant_type=client_credentials"
                    + "&client_id=" + URLEncoder.encode(ak, StandardCharsets.UTF_8)
                    + "&client_secret=" + URLEncoder.encode(sk, StandardCharsets.UTF_8);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("获取百度 OCR Token 失败，HTTP " + response.statusCode());
            }

            JsonNode root = objectMapper.readTree(response.body());
            if (root.hasNonNull("error")) {
                String message = root.path("error_description").asText(root.path("error").asText("unknown"));
                throw new IllegalStateException("百度 Token 错误: " + message);
            }

            String accessToken = root.path("access_token").asText();
            if (accessToken == null || accessToken.isBlank()) {
                throw new IllegalStateException("百度 Token 响应中缺少 access_token");
            }
            return accessToken;
        } catch (Exception exception) {
            throw new IllegalStateException("调用百度 Token 接口失败: " + exception.getMessage(), exception);
        }
    }

    private String fetchBaiduOcrRawText(String accessToken, String base64) {
        try {
            String url = "https://aip.baidubce.com/rest/2.0/ocr/v1/accurate_basic?access_token="
                    + URLEncoder.encode(accessToken, StandardCharsets.UTF_8);

            String body = "image=" + URLEncoder.encode(base64, StandardCharsets.UTF_8)
                    + "&detect_direction=false&probability=false";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("调用百度 OCR 失败，HTTP " + response.statusCode());
            }

            JsonNode root = objectMapper.readTree(response.body());
            if (root.hasNonNull("error_code")) {
                int code = root.path("error_code").asInt();
                String message = root.path("error_msg").asText("unknown");
                throw new IllegalStateException("百度 OCR 错误[" + code + "]: " + message);
            }

            JsonNode wordsResult = root.path("words_result");
            if (!wordsResult.isArray()) {
                return "";
            }

            List<String> lines = new ArrayList<>();
            for (JsonNode node : wordsResult) {
                String line = node.path("words").asText("").trim();
                if (!line.isBlank()) {
                    lines.add(line);
                }
            }
            return String.join("\n", lines);
        } catch (Exception exception) {
            throw new IllegalStateException("调用百度 OCR 接口失败: " + exception.getMessage(), exception);
        }
    }

    private List<OcrWordBox> fetchBaiduOcrWordsWithLocation(String accessToken, String base64) {
        try {
            String url = "https://aip.baidubce.com/rest/2.0/ocr/v1/accurate?access_token="
                    + URLEncoder.encode(accessToken, StandardCharsets.UTF_8);

            String body = "image=" + URLEncoder.encode(base64, StandardCharsets.UTF_8)
                    + "&detect_direction=false&probability=false&vertexes_location=false";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("调用百度 OCR 失败，HTTP " + response.statusCode());
            }

            JsonNode root = objectMapper.readTree(response.body());
            if (root.hasNonNull("error_code")) {
                int code = root.path("error_code").asInt();
                String message = root.path("error_msg").asText("unknown");
                throw new IllegalStateException("百度 OCR 错误[" + code + "]: " + message);
            }

            JsonNode wordsResult = root.path("words_result");
            if (!wordsResult.isArray()) {
                return List.of();
            }

            List<OcrWordBox> result = new ArrayList<>();
            for (JsonNode node : wordsResult) {
                String words = node.path("words").asText("").trim();
                JsonNode location = node.path("location");
                if (words.isBlank() || location.isMissingNode()) {
                    continue;
                }
                int left = location.path("left").asInt();
                int top = location.path("top").asInt();
                int width = location.path("width").asInt();
                int height = location.path("height").asInt();
                result.add(new OcrWordBox(words, left, top, width, height));
            }
            return result;
        } catch (Exception exception) {
            throw new IllegalStateException("调用百度 OCR 接口失败: " + exception.getMessage(), exception);
        }
    }

    private GridAnalysisResult mapWordsToGrid(
            List<OcrWordBox> words,
            int rows,
            int cols,
            int imageWidth,
            int imageHeight,
            List<String> candidateCodes
    ) {
        double cellWidth = (double) imageWidth / cols;
        double cellHeight = (double) imageHeight / rows;
        Pattern codePattern = Pattern.compile("[A-Z][0-9]{1,3}");

        Set<String> normalizedCandidates = new HashSet<>();
        if (candidateCodes != null) {
            for (String code : candidateCodes) {
                String normalized = normalizeOcrToken(code);
                if (!normalized.isBlank()) {
                    normalizedCandidates.add(normalized);
                }
            }
        }

        Map<String, String> gridMap = new HashMap<>();
        int ocrCount = 0;

        for (OcrWordBox word : words) {
            String text = word.words().toUpperCase().replace("O", "0").replace("I", "1");
            Matcher matcher = codePattern.matcher(text);
            List<String> matches = new ArrayList<>();
            while (matcher.find()) {
                String normalized = normalizeOcrToken(matcher.group());
                if (!normalized.isBlank()) {
                    matches.add(normalized);
                }
            }
            if (matches.isEmpty()) {
                continue;
            }

            int tokenCount = matches.size();
            double step = tokenCount <= 0 ? word.width() : (double) word.width() / tokenCount;
            double centerY = word.top() + word.height() / 2.0;

            for (int index = 0; index < matches.size(); index++) {
                String code = matches.get(index);
                String finalCode = normalizeWithCandidates(code, normalizedCandidates);
                if (finalCode == null || finalCode.isBlank()) {
                    continue;
                }
                double centerX = word.left() + step * index + step / 2.0;
                int col = (int) Math.floor(centerX / cellWidth);
                int row = (int) Math.floor(centerY / cellHeight);
                if (row < 0 || row >= rows || col < 0 || col >= cols) {
                    continue;
                }
                gridMap.put(row + "," + col, finalCode);
                ocrCount++;
            }
        }

        List<GridAnalysisCell> cells = new ArrayList<>();
        for (Map.Entry<String, String> entry : gridMap.entrySet()) {
            String[] rc = entry.getKey().split(",");
            int r = Integer.parseInt(rc[0]);
            int c = Integer.parseInt(rc[1]);
            cells.add(new GridAnalysisCell(r, c, entry.getValue()));
        }
        cells.sort(Comparator.comparingInt(GridAnalysisCell::getRow).thenComparingInt(GridAnalysisCell::getCol));

        GridAnalysisResult result = new GridAnalysisResult();
        result.setRows(rows);
        result.setCols(cols);
        result.setOcrCount(ocrCount);
        result.setFilledCount(cells.size());
        result.setCells(cells);
        return result;
    }

    private String normalizeWithCandidates(String code, Set<String> candidates) {
        if (code == null || code.isBlank()) {
            return null;
        }
        if (candidates == null || candidates.isEmpty()) {
            return code;
        }
        if (candidates.contains(code)) {
            return code;
        }
        for (String candidate : candidates) {
            if (candidate.contains(code) || code.contains(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private boolean isQuotaError(String message) {
        if (message == null) {
            return false;
        }
        return message.contains("[17]") || message.contains("[19]") || message.contains("daily request limit") || message.contains("request limit");
    }

    private List<ColorRequirement> parseColorRequirements(String rawText) {
        return parseColorRequirements(rawText, null);
    }

    private List<ColorRequirement> parseColorRequirements(String rawText, List<String> debugLogs) {
        if (rawText == null || rawText.isBlank()) {
            if (debugLogs != null) {
                debugLogs.add("文本解析：OCR 文本为空");
            }
            return List.of();
        }
        Pattern codePattern = Pattern.compile("^[A-Z]{1,2}\\d{1,4}$");

        List<String> lines = Arrays.stream(rawText.replace("\r", "\n").split("\\n+"))
                .map(this::cleanOcrLine)
                .filter(line -> !line.isBlank())
                .toList();

        if (lines.isEmpty()) {
            if (debugLogs != null) {
                debugLogs.add("文本解析：清洗后无有效行");
            }
            return List.of();
        }

        Map<String, Integer> codeQuantity = new LinkedHashMap<>();
        Set<Integer> consumedLineIndexes = new HashSet<>();

        for (int lineIndex = 0; lineIndex < lines.size() - 1; lineIndex++) {
            if (consumedLineIndexes.contains(lineIndex)) {
                continue;
            }

            String currentLine = lines.get(lineIndex);
            String nextLine = lines.get(lineIndex + 1);
            List<String> codes = extractCodes(currentLine, codePattern);
            List<Integer> currentQuantities = extractQuantities(currentLine);
            List<String> nextCodes = extractCodes(nextLine, codePattern);
            List<Integer> quantities = extractQuantities(nextLine);

            boolean currentLooksCodeOnly = !codes.isEmpty() && currentQuantities.isEmpty();
            boolean nextLooksQuantityOnly = !quantities.isEmpty() && nextCodes.isEmpty();

            if (currentLooksCodeOnly && nextLooksQuantityOnly) {
                int limit = Math.min(codes.size(), quantities.size());
                if (debugLogs != null) {
                    debugLogs.add("文本双行配对：line " + (lineIndex + 1) + " codes=" + codes + "；line " + (lineIndex + 2) + " qty=" + quantities + "；配对=" + limit);
                }
                for (int index = 0; index < limit; index++) {
                    Integer quantity = quantities.get(index);
                    if (quantity == null || quantity <= 0) {
                        continue;
                    }
                    codeQuantity.merge(codes.get(index), quantity, Integer::sum);
                }
                consumedLineIndexes.add(lineIndex);
                consumedLineIndexes.add(lineIndex + 1);
                lineIndex++;
            }
        }

        for (int lineIndex = 0; lineIndex < lines.size(); lineIndex++) {
            if (consumedLineIndexes.contains(lineIndex)) {
                continue;
            }

            String[] tokens = lines.get(lineIndex).split("[\\s,;，；、]+");
            for (int i = 0; i < tokens.length; i++) {
                String token = normalizeOcrToken(tokens[i]);
                if (token == null || token.isBlank() || token.length() > 6) {
                    continue;
                }

                Matcher matcher = codePattern.matcher(token);
                if (!matcher.matches()) {
                    continue;
                }

                int quantity = 1;
                if (i + 1 < tokens.length && tokens[i + 1].matches("^\\d{1,4}$")) {
                    quantity = Integer.parseInt(tokens[i + 1]);
                    i++;
                }

                if (quantity <= 0) {
                    continue;
                }
                codeQuantity.merge(token, quantity, Integer::sum);
                if (debugLogs != null) {
                    debugLogs.add("文本单行兜底：line " + (lineIndex + 1) + " -> " + token + " x " + quantity);
                }
            }
        }

        return codeQuantity.entrySet().stream()
            .sorted((left, right) -> compareColorCodes(left.getKey(), right.getKey()))
                .map(entry -> new ColorRequirement(entry.getKey(), entry.getValue()))
                .toList();
    }

    private List<ColorRequirement> parseColorRequirementsByLocation(List<OcrWordBox> words) {
        return parseColorRequirementsByLocation(words, null, null);
    }

    private List<ColorRequirement> parseColorRequirementsByLocation(
            List<OcrWordBox> words,
            List<String> locationLinesDebug,
            List<String> pairLogs
    ) {
        if (words == null || words.isEmpty()) {
            if (pairLogs != null) {
                pairLogs.add("坐标解析：OCR words 为空");
            }
            return List.of();
        }

        Pattern codePattern = Pattern.compile("^[A-Z]{1,2}\\d{1,4}$");
        List<OcrLine> lines = buildOcrLines(words);
        if (lines.isEmpty()) {
            if (pairLogs != null) {
                pairLogs.add("坐标解析：分组后无有效行");
            }
            return List.of();
        }

        if (locationLinesDebug != null) {
            for (int i = 0; i < lines.size(); i++) {
                locationLinesDebug.add((i + 1) + ": " + lines.get(i).text());
            }
        }

        Map<String, Integer> codeQuantity = new LinkedHashMap<>();
        Set<Integer> consumed = new HashSet<>();

        for (int index = 0; index < lines.size() - 1; index++) {
            if (consumed.contains(index)) {
                continue;
            }

            String currentLine = lines.get(index).text();
            String nextLine = lines.get(index + 1).text();

            List<String> codes = extractCodes(currentLine, codePattern);
            List<Integer> currentQuantities = extractQuantities(currentLine);
            List<String> nextCodes = extractCodes(nextLine, codePattern);
            List<Integer> quantities = extractQuantities(nextLine);

            boolean currentLooksCodeOnly = !codes.isEmpty() && currentQuantities.isEmpty();
            boolean nextLooksQuantityOnly = !quantities.isEmpty() && nextCodes.isEmpty();

            if (!currentLooksCodeOnly || !nextLooksQuantityOnly) {
                continue;
            }

            int diff = Math.abs(codes.size() - quantities.size());
            if (diff > Math.max(1, codes.size() / 2)) {
                if (pairLogs != null) {
                    pairLogs.add("跳过双行配对：line " + (index + 1) + " codes=" + codes + "；line " + (index + 2) + " qty=" + quantities + "（数量差过大）");
                }
                continue;
            }

            int pairCount = Math.min(codes.size(), quantities.size());
            if (pairLogs != null) {
                pairLogs.add("坐标双行配对：line " + (index + 1) + " codes=" + codes + "；line " + (index + 2) + " qty=" + quantities + "；配对=" + pairCount);
            }
            for (int pairIndex = 0; pairIndex < pairCount; pairIndex++) {
                int quantity = quantities.get(pairIndex);
                if (quantity <= 0) {
                    continue;
                }
                codeQuantity.merge(codes.get(pairIndex), quantity, Integer::sum);
            }
            consumed.add(index);
            consumed.add(index + 1);
            index++;
        }

        for (int index = 0; index < lines.size(); index++) {
            if (consumed.contains(index)) {
                continue;
            }
            String[] tokens = lines.get(index).text().split("[\\s,;，；、]+");
            for (int i = 0; i < tokens.length; i++) {
                String token = normalizeOcrToken(tokens[i]);
                if (token == null || token.isBlank() || token.length() > 6 || !codePattern.matcher(token).matches()) {
                    continue;
                }
                int quantity = 1;
                if (i + 1 < tokens.length && tokens[i + 1].matches("^\\d{1,4}$")) {
                    quantity = Integer.parseInt(tokens[i + 1]);
                    i++;
                }
                if (quantity > 0) {
                    codeQuantity.merge(token, quantity, Integer::sum);
                    if (pairLogs != null) {
                        pairLogs.add("坐标单行兜底：line " + (index + 1) + " -> " + token + " x " + quantity);
                    }
                }
            }
        }

        return codeQuantity.entrySet().stream()
                .sorted((left, right) -> compareColorCodes(left.getKey(), right.getKey()))
                .map(entry -> new ColorRequirement(entry.getKey(), entry.getValue()))
                .toList();
    }

    private int compareColorCodes(String leftCode, String rightCode) {
        String left = normalizeColorCode(leftCode);
        String right = normalizeColorCode(rightCode);
        Pattern codePattern = Pattern.compile("^([A-Z]+)(\\d+)$");
        Matcher leftMatcher = codePattern.matcher(left);
        Matcher rightMatcher = codePattern.matcher(right);

        if (leftMatcher.matches() && rightMatcher.matches()) {
            int letterCompare = leftMatcher.group(1).compareTo(rightMatcher.group(1));
            if (letterCompare != 0) {
                return letterCompare;
            }
            int leftNumber = Integer.parseInt(leftMatcher.group(2));
            int rightNumber = Integer.parseInt(rightMatcher.group(2));
            return Integer.compare(leftNumber, rightNumber);
        }

        return left.compareTo(right);
    }

    private String resolvePairingStrategy(List<String> logs) {
        if (logs == null || logs.isEmpty()) {
            return "unknown";
        }
        boolean hasDoubleLine = logs.stream().anyMatch(log -> log != null && log.contains("双行配对"));
        boolean hasSingleLine = logs.stream().anyMatch(log -> log != null && log.contains("单行"));

        if (hasDoubleLine && hasSingleLine) {
            return "mixed";
        }
        if (hasDoubleLine) {
            return "double-line";
        }
        if (hasSingleLine) {
            return "single-line";
        }
        return "unknown";
    }

    private List<OcrLine> buildOcrLines(List<OcrWordBox> words) {
        List<OcrWordBox> sorted = words.stream()
                .filter(word -> word.words() != null && !word.words().isBlank())
                .sorted(Comparator.comparingInt(OcrWordBox::top).thenComparingInt(OcrWordBox::left))
                .toList();
        if (sorted.isEmpty()) {
            return List.of();
        }

        double avgHeight = sorted.stream().mapToInt(OcrWordBox::height).average().orElse(16.0);
        int lineThreshold = Math.max(8, (int) Math.round(avgHeight * 0.65));

        List<List<OcrWordBox>> grouped = new ArrayList<>();
        for (OcrWordBox word : sorted) {
            if (grouped.isEmpty()) {
                List<OcrWordBox> first = new ArrayList<>();
                first.add(word);
                grouped.add(first);
                continue;
            }

            List<OcrWordBox> lastGroup = grouped.get(grouped.size() - 1);
            double lastCenterY = lastGroup.stream().mapToDouble(item -> item.top() + item.height() / 2.0).average().orElse(word.top());
            double currentCenterY = word.top() + word.height() / 2.0;

            if (Math.abs(currentCenterY - lastCenterY) <= lineThreshold) {
                lastGroup.add(word);
            } else {
                List<OcrWordBox> next = new ArrayList<>();
                next.add(word);
                grouped.add(next);
            }
        }

        List<OcrLine> lines = new ArrayList<>();
        for (List<OcrWordBox> group : grouped) {
            group.sort(Comparator.comparingInt(OcrWordBox::left));
            String text = group.stream()
                    .map(OcrWordBox::words)
                    .map(this::cleanOcrLine)
                    .filter(token -> !token.isBlank())
                    .reduce((left, right) -> left + " " + right)
                    .orElse("")
                    .trim();
            if (!text.isBlank()) {
                lines.add(new OcrLine(text));
            }
        }
        return lines;
    }

    private String cleanOcrLine(String line) {
        if (line == null) {
            return "";
        }
        return line
                .toUpperCase()
                .replaceAll("[()（）]", " ")
                .replaceAll("[：:]", " ")
                .replaceAll("[x×]\\s*(\\d+)", " $1")
                .replace('|', ' ')
                .trim();
    }

    private List<String> extractCodes(String line, Pattern codePattern) {
        if (line == null || line.isBlank()) {
            return List.of();
        }
        String[] tokens = line.split("[\\s,;，；、]+");
        List<String> result = new ArrayList<>();
        for (String token : tokens) {
            String normalized = normalizeOcrToken(token);
            if (normalized.isBlank()) {
                continue;
            }
            if (codePattern.matcher(normalized).matches()) {
                result.add(normalized);
            }
        }
        return result;
    }

    private List<Integer> extractQuantities(String line) {
        if (line == null || line.isBlank()) {
            return List.of();
        }
        String[] tokens = line.split("[\\s,;，；、]+");
        List<Integer> result = new ArrayList<>();
        for (String token : tokens) {
            String normalized = token == null ? "" : token.trim().replace("O", "0").replace("I", "1").replace("L", "1");
            if (!normalized.matches("^\\d{1,4}$")) {
                continue;
            }
            int quantity = Integer.parseInt(normalized);
            if (quantity > 0) {
                result.add(quantity);
            }
        }
        return result;
    }

    private String normalizeOcrToken(String token) {
        if (token == null) {
            return "";
        }
        String upper = token.toUpperCase().trim();
        if (upper.isEmpty()) {
            return upper;
        }
        String first = upper.substring(0, 1);
        String rest = upper.substring(1)
                .replace("O", "0")
                .replace("I", "1")
                .replace("L", "1");
        String normalized = first + rest;
        return normalizeColorCode(normalized);
    }

    private List<ColorRequirement> normalizeRequiredColors(List<ColorRequirement> colors) {
        if (colors == null || colors.isEmpty()) {
            return new ArrayList<>();
        }
        Map<String, Integer> merged = new LinkedHashMap<>();
        for (ColorRequirement color : colors) {
            if (color == null) {
                continue;
            }
            String code = normalizeColorCode(color.getCode());
            if (code.isBlank()) {
                continue;
            }
            int quantity = color.getQuantity() == null ? 0 : Math.max(0, color.getQuantity());
            if (quantity <= 0) {
                continue;
            }
            merged.merge(code, quantity, Integer::sum);
        }
        List<ColorRequirement> normalized = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : merged.entrySet()) {
            normalized.add(new ColorRequirement(entry.getKey(), entry.getValue()));
        }
        normalized.sort(Comparator.comparing(ColorRequirement::getCode));
        return normalized;
    }

    private String normalizeColorCode(String code) {
        if (code == null) {
            return "";
        }
        String upper = code.trim().toUpperCase();
        if (upper.isBlank()) {
            return "";
        }
        Matcher matcher = Pattern.compile("^([A-Z]+)(\\d+)$").matcher(upper);
        if (!matcher.matches()) {
            return upper;
        }
        String prefix = matcher.group(1);
        String number = matcher.group(2).replaceFirst("^0+(?=\\d)", "");
        return prefix + number;
    }

    private record OcrWordBox(String words, int left, int top, int width, int height) {}
    private record OcrLine(String text) {}
    private record KeyPair(String ak, String sk) {}
}
