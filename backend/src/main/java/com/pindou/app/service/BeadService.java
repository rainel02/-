package com.pindou.app.service;

import com.pindou.app.model.*;
import com.pindou.app.repository.AppSettingRepository;
import com.pindou.app.repository.BeadProjectRepository;
import com.pindou.app.repository.TagOptionRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
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
    private static final Logger LOGGER = LoggerFactory.getLogger(BeadService.class);
    private static final Object OCR_RATE_LIMIT_LOCK = new Object();
    private static final long OCR_MIN_INTERVAL_NANOS = 500_000_000L;
    private static long lastOcrRequestNanos = 0L;

    private final BeadProjectRepository projectRepository;
    private final AppSettingRepository appSettingRepository;
    private final TagOptionRepository tagOptionRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ThreadLocal<LinkedHashSet<String>> ocrServicesUsed = ThreadLocal.withInitial(LinkedHashSet::new);

    @Value("${baidu.ocr.ak:}")
    private String baiduApiKey;

    @Value("${baidu.ocr.sk:}")
    private String baiduSecretKey;

    @Value("${baidu.ocr.ak2:}")
    private String baiduApiKey2;

    @Value("${baidu.ocr.sk2:}")
    private String baiduSecretKey2;

    public BeadService(
            BeadProjectRepository projectRepository,
            AppSettingRepository appSettingRepository,
            TagOptionRepository tagOptionRepository
    ) {
        this.projectRepository = projectRepository;
        this.appSettingRepository = appSettingRepository;
        this.tagOptionRepository = tagOptionRepository;
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
        logImagePayloadDiagnostics("extract-colors", imageBase64, base64);
        if (base64 == null || base64.isBlank()) {
            return List.of();
        }
        base64 = normalizeImageBase64ForOcr(base64);

        List<String> variants = buildGridOcrVariants(base64);
        if (variants == null || variants.isEmpty()) {
            variants = List.of(base64);
        }

        List<KeyPair> keyPairs = buildKeyPairs();
        if (keyPairs.isEmpty()) {
            throw new IllegalStateException("百度 OCR 未配置，请在 backend/application.yml 或环境变量中设置 baidu.ocr.ak / baidu.ocr.sk");
        }

        RuntimeException lastError = null;
        for (KeyPair keyPair : keyPairs) {
            try {
                String accessToken = fetchBaiduAccessToken(keyPair.ak(), keyPair.sk());

                List<ColorRequirement> bestByLocation = List.of();
                List<ColorRequirement> bestByText = List.of();

                for (String variant : variants) {
                    boolean usedTileFallback = false;
                    try {
                        List<OcrWordBox> words = fetchBaiduOcrWordsWithLocation(accessToken, variant);
                        List<ColorRequirement> byLocation = parseColorRequirementsByLocation(words);
                        if (isBetterColorRequirements(byLocation, bestByLocation)) {
                            bestByLocation = byLocation;
                        }
                    } catch (RuntimeException locationException) {
                        if (!isImageSizeOrFormatError(locationException.getMessage())) {
                            LOGGER.warn("OCR location failed, continue with raw-text parsing. reason={}", locationException.getMessage());
                        } else {
                            ColorExtractionBundle tiled = extractColorsByTiledOcr(accessToken, variant);
                            if (isBetterColorRequirements(tiled.byLocation(), bestByLocation)) {
                                bestByLocation = tiled.byLocation();
                            }
                            if (isBetterColorRequirements(tiled.byText(), bestByText)) {
                                bestByText = tiled.byText();
                            }
                            usedTileFallback = true;
                        }
                    }

                    if (!usedTileFallback) {
                        try {
                            String text = fetchBaiduOcrRawText(accessToken, variant);
                            List<ColorRequirement> byText = parseColorRequirements(text);
                            if (isBetterColorRequirements(byText, bestByText)) {
                                bestByText = byText;
                            }
                        } catch (RuntimeException imageSizeException) {
                            if (!isImageSizeOrFormatError(imageSizeException.getMessage())) {
                                throw imageSizeException;
                            }
                            ColorExtractionBundle tiled = extractColorsByTiledOcr(accessToken, variant);
                            if (isBetterColorRequirements(tiled.byLocation(), bestByLocation)) {
                                bestByLocation = tiled.byLocation();
                            }
                            if (isBetterColorRequirements(tiled.byText(), bestByText)) {
                                bestByText = tiled.byText();
                            }
                        }
                    }
                }

                List<ColorRequirement> preferred = pickPreferredColorRequirements(bestByLocation, bestByText);
                if (!preferred.isEmpty()) {
                    return preferred;
                }
                return List.of();
            } catch (RuntimeException exception) {
                lastError = exception;
                if (!isQuotaError(exception.getMessage())) {
                    break;
                }
            }
        }

        if (lastError != null) {
            if (isQuotaError(lastError.getMessage())) {
                LOGGER.warn("百度OCR额度不足，extract-colors返回空结果：{}", lastError.getMessage());
                return List.of();
            }
            throw lastError;
        }
        return List.of();
    }

    private boolean isBetterColorRequirements(List<ColorRequirement> current, List<ColorRequirement> baseline) {
        if (current == null || current.isEmpty()) {
            return false;
        }
        if (baseline == null || baseline.isEmpty()) {
            return true;
        }

        return colorRequirementsConfidenceScore(current) > colorRequirementsConfidenceScore(baseline);
    }

    private List<ColorRequirement> pickPreferredColorRequirements(
            List<ColorRequirement> byLocation,
            List<ColorRequirement> byText
    ) {
        if ((byLocation == null || byLocation.isEmpty()) && (byText == null || byText.isEmpty())) {
            return List.of();
        }
        if (byLocation == null || byLocation.isEmpty()) {
            return byText;
        }
        if (byText == null || byText.isEmpty()) {
            return byLocation;
        }

        double locationScore = colorRequirementsConfidenceScore(byLocation);
        double textScore = colorRequirementsConfidenceScore(byText);
        return textScore > locationScore ? byText : byLocation;
    }

    private double colorRequirementsConfidenceScore(List<ColorRequirement> requirements) {
        if (requirements == null || requirements.isEmpty()) {
            return Double.NEGATIVE_INFINITY;
        }

        Pattern codePattern = Pattern.compile("^[A-Z]{1,2}\\d{1,4}$");
        int itemCount = 0;
        int totalQuantity = 0;
        int maxQuantity = 0;
        int validCodeCount = 0;
        int singleQuantityCount = 0;

        for (ColorRequirement item : requirements) {
            if (item == null) {
                continue;
            }
            String code = normalizeColorCode(item.getCode());
            int quantity = item.getQuantity() == null ? 0 : Math.max(0, item.getQuantity());
            if (code.isBlank() || quantity <= 0) {
                continue;
            }

            itemCount++;
            totalQuantity += quantity;
            maxQuantity = Math.max(maxQuantity, quantity);
            if (codePattern.matcher(code).matches()) {
                validCodeCount++;
            }
            if (quantity <= 1) {
                singleQuantityCount++;
            }
        }

        if (itemCount <= 0) {
            return Double.NEGATIVE_INFINITY;
        }

        double avgQuantity = totalQuantity / (double) itemCount;
        double singleRatio = singleQuantityCount / (double) itemCount;

        double score = 0.0;
        score += itemCount * 4.0;
        score += Math.min(totalQuantity, 3000) * 0.08;
        score += Math.min(maxQuantity, 500) * 0.12;
        score += validCodeCount * 1.6;

        if (totalQuantity >= itemCount * 2) {
            score += 8.0;
        }

        if (itemCount >= 8 && singleRatio >= 0.85) {
            score -= 18.0;
        } else if (itemCount >= 6 && singleRatio >= 0.75) {
            score -= 8.0;
        }

        if (avgQuantity < 1.25 && itemCount >= 6) {
            score -= 6.0;
        }

        return score;
    }

    public List<ColorRequirement> extractColorsFromBaidu(
            String originalImageBase64,
            Integer cropX,
            Integer cropY,
            Integer cropWidth,
            Integer cropHeight
    ) {
        String cropped = cropImageByRect(originalImageBase64, cropX, cropY, cropWidth, cropHeight);
        if (cropped == null || cropped.isBlank()) {
            return List.of();
        }
        return extractColorsFromBaidu(cropped);
    }

    public ColorExtractionDebugResult extractColorsDebugFromBaidu(String imageBase64) {
        resetOcrServiceUsage();
        String base64 = stripDataUrlPrefix(imageBase64);
        logImagePayloadDiagnostics("extract-colors-debug", imageBase64, base64);
        ColorExtractionDebugResult result = new ColorExtractionDebugResult();
        result.setOcrServiceSummary("未调用OCR服务");
        if (base64 == null || base64.isBlank()) {
            result.setStrategy("empty-image");
            result.setColors(List.of());
            result.setOcrServiceSummary(buildOcrServiceSummary());
            return result;
        }
        base64 = normalizeImageBase64ForOcr(base64);

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
                pairLogs.add("payload诊断：" + diagnoseBase64Payload(base64));
                List<ColorRequirement> byLocation = List.of();
                String rawText = "";
                RuntimeException locationException = null;
                try {
                    List<OcrWordBox> words = fetchBaiduOcrWordsWithLocation(accessToken, base64);
                    byLocation = parseColorRequirementsByLocation(words, locationLinesDebug, pairLogs);
                } catch (RuntimeException imageSizeException) {
                    if (!isImageSizeOrFormatError(imageSizeException.getMessage())) {
                        locationException = imageSizeException;
                    } else {
                        pairLogs.add("触发分块识别：原图格式/尺寸触发 OCR 限制，改为分块识别并合并结果");
                        ColorExtractionBundle tiled = extractColorsByTiledOcr(accessToken, base64);
                        byLocation = tiled.byLocation();
                        rawText = "[tiled-ocr]";
                        if (tiled.tileLogs() != null && !tiled.tileLogs().isEmpty()) {
                            pairLogs.addAll(tiled.tileLogs());
                        }
                    }
                }

                try {
                    rawText = fetchBaiduOcrRawText(accessToken, base64);
                } catch (RuntimeException rawTextException) {
                    if (locationException != null) {
                        throw locationException;
                    }
                    throw rawTextException;
                }

                if (locationException != null) {
                    pairLogs.add("坐标识别失败，已降级使用文本识别：" + locationException.getMessage());
                }

                result.setLocationLines(locationLinesDebug);
                result.setPairLogs(pairLogs);
                result.setRawText(rawText);

                List<String> fallbackLogs = new ArrayList<>();
                List<ColorRequirement> byText = parseColorRequirements(rawText, fallbackLogs);
                result.setFallbackLogs(fallbackLogs);

                List<ColorRequirement> preferred = pickPreferredColorRequirements(byLocation, byText);
                if (preferred == byText && !byText.isEmpty()) {
                    if (byLocation != null && !byLocation.isEmpty()) {
                        pairLogs.add("location结果疑似噪声：已切换到文本解析结果");
                    }
                    result.setStrategy("text-" + resolvePairingStrategy(fallbackLogs));
                    result.setColors(byText);
                    result.setOcrServiceSummary(buildOcrServiceSummary());
                    return result;
                }

                if (byLocation != null && !byLocation.isEmpty()) {
                    result.setStrategy("location-" + resolvePairingStrategy(pairLogs));
                    result.setColors(byLocation);
                    result.setOcrServiceSummary(buildOcrServiceSummary());
                    return result;
                }

                result.setStrategy("text-" + resolvePairingStrategy(fallbackLogs));
                result.setColors(byText);
                result.setOcrServiceSummary(buildOcrServiceSummary());
                return result;
            } catch (RuntimeException exception) {
                lastError = exception;
                if (!isQuotaError(exception.getMessage())) {
                    break;
                }
            }
        }

        if (lastError != null) {
            if (isQuotaError(lastError.getMessage())) {
                result.setStrategy("quota-limit");
                result.setPairLogs(List.of("百度OCR当日请求额度已用完，请切换AK/SK或次日重试"));
                result.setColors(List.of());
                result.setOcrServiceSummary(buildOcrServiceSummary());
                return result;
            }
            throw lastError;
        }

        result.setStrategy("no-result");
        result.setColors(List.of());
        result.setOcrServiceSummary(buildOcrServiceSummary());
        return result;
    }

    public ColorExtractionDebugResult extractColorsDebugFromBaidu(
            String originalImageBase64,
            Integer cropX,
            Integer cropY,
            Integer cropWidth,
            Integer cropHeight
    ) {
        String cropped = cropImageByRect(originalImageBase64, cropX, cropY, cropWidth, cropHeight);
        if (cropped == null || cropped.isBlank()) {
            ColorExtractionDebugResult result = new ColorExtractionDebugResult();
            result.setStrategy("crop-failed");
            result.setColors(List.of());
            result.setOcrServiceSummary("未调用OCR服务");
            result.setPairLogs(List.of(
                "裁切失败：已阻止回退整图识别，避免识别范围超出选区",
                "payload诊断：" + diagnoseBase64Payload(originalImageBase64)
            ));
            result.setRawText("");
            return result;
        }
        return extractColorsDebugFromBaidu(cropped);
    }

    public GridAnalysisResult analyzeGridFromBaidu(
            String imageBase64,
            Integer rows,
            Integer cols,
            Integer imageWidth,
            Integer imageHeight,
            List<String> candidateCodes
    ) {
        resetOcrServiceUsage();
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
            empty.setOcrServiceSummary(buildOcrServiceSummary());
            return empty;
        }
        base64 = normalizeImageBase64ForOcr(base64);

        List<KeyPair> keyPairs = buildKeyPairs();
        if (keyPairs.isEmpty()) {
            throw new IllegalStateException("百度 OCR 未配置，请在 backend/application.yml 或环境变量中设置 baidu.ocr.ak / baidu.ocr.sk");
        }

        RuntimeException lastError = null;
        for (KeyPair keyPair : keyPairs) {
            try {
                String accessToken = fetchBaiduAccessToken(keyPair.ak(), keyPair.sk());
                int splitCount = Math.max(resolveGridSplitCount(gridRows, gridCols), resolveImageSizeSplitCount(base64));
                if (splitCount <= 1) {
                    return withOcrServiceSummary(analyzeSingleImageWithVariants(accessToken, base64, gridRows, gridCols, width, height, candidateCodes));
                }
                return withOcrServiceSummary(analyzeGridByTiles(accessToken, base64, gridRows, gridCols, width, height, candidateCodes, splitCount));
            } catch (RuntimeException exception) {
                lastError = exception;
                if (!isQuotaError(exception.getMessage())) {
                    break;
                }
            }
        }

        if (lastError != null) {
            if (isQuotaError(lastError.getMessage())) {
                GridAnalysisResult empty = new GridAnalysisResult();
                empty.setRows(gridRows);
                empty.setCols(gridCols);
                empty.setOcrCount(0);
                empty.setFilledCount(0);
                empty.setCells(List.of());
                empty.setOcrServiceSummary(buildOcrServiceSummary());
                return empty;
            }
            throw lastError;
        }
        GridAnalysisResult empty = new GridAnalysisResult();
        empty.setRows(gridRows);
        empty.setCols(gridCols);
        empty.setOcrServiceSummary(buildOcrServiceSummary());
        return empty;
    }

    public GridAnalysisResult analyzeGridFromBaidu(
            String originalImageBase64,
            Integer rows,
            Integer cols,
            Integer imageWidth,
            Integer imageHeight,
            List<String> candidateCodes,
            Map<String, Integer> candidateQuantities,
            Map<String, String> candidateColorHex,
            Integer cropX,
            Integer cropY,
            Integer cropWidth,
            Integer cropHeight
    ) {
        String cropped = cropImageByRect(originalImageBase64, cropX, cropY, cropWidth, cropHeight);
        if (cropped == null || cropped.isBlank()) {
            throw new IllegalArgumentException("裁切失败：未执行整图回退，请重新框选后重试");
        }
        int safeWidth = cropWidth == null ? (imageWidth == null ? 0 : imageWidth) : cropWidth;
        int safeHeight = cropHeight == null ? (imageHeight == null ? 0 : imageHeight) : cropHeight;
        return analyzeGridFromBaidu(
                cropped,
                rows,
                cols,
                safeWidth,
                safeHeight,
                candidateCodes,
                candidateQuantities,
                candidateColorHex
        );
    }

    public GridAnalysisResult analyzeGridFromBaidu(
            String imageBase64,
            Integer rows,
            Integer cols,
            Integer imageWidth,
            Integer imageHeight,
            List<String> candidateCodes,
            Map<String, Integer> candidateQuantities
    ) {
        return analyzeGridFromBaidu(imageBase64, rows, cols, imageWidth, imageHeight, candidateCodes, candidateQuantities, null);
    }

    public GridAnalysisResult analyzeGridFromBaidu(
            String imageBase64,
            Integer rows,
            Integer cols,
            Integer imageWidth,
            Integer imageHeight,
            List<String> candidateCodes,
            Map<String, Integer> candidateQuantities,
            Map<String, String> candidateColorHex
    ) {
        GridAnalysisResult result = analyzeGridFromBaidu(imageBase64, rows, cols, imageWidth, imageHeight, candidateCodes);

        String base64 = stripDataUrlPrefix(imageBase64);
        if (base64 == null || base64.isBlank()) {
            return result;
        }
        if (rows == null || cols == null || rows <= 0 || cols <= 0) {
            return result;
        }

        return fillMissingCellsByColor(
                base64,
                rows,
                cols,
                result,
                candidateCodes,
                candidateQuantities,
                candidateColorHex
        );
    }

    private int resolveGridSplitCount(int gridRows, int gridCols) {
        int maxSize = Math.max(gridRows, gridCols);
        if (maxSize <= 35) {
            return 1;
        }
        return Math.max(2, (int) Math.ceil(maxSize / 35.0));
    }

    private GridAnalysisResult analyzeSingleImageWithVariants(
            String accessToken,
            String base64,
            int rows,
            int cols,
            int imageWidth,
            int imageHeight,
            List<String> candidateCodes
    ) {
        PreparedGridImage preparedImage = prepareImageForGridRecognition(base64, rows, cols, imageWidth, imageHeight);
        GridAnalysisResult preparedResult = runSingleImagePipeline(
                accessToken,
                preparedImage.base64(),
                rows,
                cols,
                preparedImage.geometry(),
                candidateCodes
        );

        int totalCells = rows * cols;
        int enoughFilled = Math.max(1, (int) Math.floor(totalCells * 0.72));
        if (preparedResult.getFilledCount() >= enoughFilled) {
            return preparedResult;
        }

        boolean shouldTryBaseline = preparedImage.preprocessed();
        if (!shouldTryBaseline) {
            return preparedResult;
        }

        PreparedGridImage baselineImage = buildBaselineGridImage(base64, rows, cols, imageWidth, imageHeight);
        GridAnalysisResult baselineResult = runSingleImagePipeline(
                accessToken,
                baselineImage.base64(),
                rows,
                cols,
                baselineImage.geometry(),
                candidateCodes
        );

        return baselineResult.getFilledCount() > preparedResult.getFilledCount() ? baselineResult : preparedResult;
    }

    private GridAnalysisResult runSingleImagePipeline(
            String accessToken,
            String base64,
            int rows,
            int cols,
            GridGeometry geometry,
            List<String> candidateCodes
    ) {
        List<OcrWordBox> words = fetchBaiduOcrWordsWithLocation(accessToken, base64);
        GridAnalysisResult primaryResult = mapWordsToGrid(words, rows, cols, geometry, candidateCodes);
        int totalCells = rows * cols;
        int enoughFilled = Math.max(1, (int) Math.floor(totalCells * 0.72));
        if (primaryResult.getFilledCount() >= enoughFilled) {
            return primaryResult;
        }

        List<String> variants = buildGridOcrVariants(base64);
        if (variants.size() <= 1) {
            return primaryResult;
        }

        List<OcrWordBox> mergedWords = new ArrayList<>(words);
        for (int i = 1; i < variants.size(); i++) {
            String variantBase64 = variants.get(i);
            try {
                List<OcrWordBox> variantWords = fetchBaiduOcrWordsWithLocation(accessToken, variantBase64);
                if (variantWords != null && !variantWords.isEmpty()) {
                    mergedWords.addAll(variantWords);
                }
            } catch (RuntimeException variantException) {
                if (isQuotaError(variantException.getMessage())) {
                    throw variantException;
                }
            }
        }

        GridAnalysisResult fusedResult = mapWordsToGrid(mergedWords, rows, cols, geometry, candidateCodes);
        return fusedResult.getFilledCount() >= primaryResult.getFilledCount() ? fusedResult : primaryResult;
    }

    private GridAnalysisResult analyzeGridByTiles(
            String accessToken,
            String base64,
            int rows,
            int cols,
            int imageWidth,
            int imageHeight,
            List<String> candidateCodes,
            int splitCount
    ) {
        BufferedImage sourceImage = decodeBase64Image(base64);
        if (sourceImage == null) {
            return analyzeSingleImageWithVariants(accessToken, base64, rows, cols, imageWidth, imageHeight, candidateCodes);
        }

        int sourceWidth = sourceImage.getWidth();
        int sourceHeight = sourceImage.getHeight();
        if (sourceWidth <= 0 || sourceHeight <= 0) {
            return analyzeSingleImageWithVariants(accessToken, base64, rows, cols, imageWidth, imageHeight, candidateCodes);
        }

        Map<String, Map<String, CodeHit>> mergedVotes = new HashMap<>();
        int mergedOcrCount = 0;
        int overlapRows = splitCount > 1 ? 1 : 0;
        int overlapCols = splitCount > 1 ? 1 : 0;

        for (int tileRow = 0; tileRow < splitCount; tileRow++) {
            int rowStart = tileRow * rows / splitCount;
            int rowEnd = (tileRow + 1) * rows / splitCount;
            if (rowEnd <= rowStart) {
                continue;
            }

            int extRowStart = Math.max(0, rowStart - overlapRows);
            int extRowEnd = Math.min(rows, rowEnd + overlapRows);
            int sy = (int) Math.round(extRowStart * (double) sourceHeight / rows);
            int ey = (int) Math.round(extRowEnd * (double) sourceHeight / rows);
            sy = Math.max(0, Math.min(sy, sourceHeight - 1));
            ey = Math.max(sy + 1, Math.min(ey, sourceHeight));

            for (int tileCol = 0; tileCol < splitCount; tileCol++) {
                int colStart = tileCol * cols / splitCount;
                int colEnd = (tileCol + 1) * cols / splitCount;
                if (colEnd <= colStart) {
                    continue;
                }

                int extColStart = Math.max(0, colStart - overlapCols);
                int extColEnd = Math.min(cols, colEnd + overlapCols);
                int sx = (int) Math.round(extColStart * (double) sourceWidth / cols);
                int ex = (int) Math.round(extColEnd * (double) sourceWidth / cols);
                sx = Math.max(0, Math.min(sx, sourceWidth - 1));
                ex = Math.max(sx + 1, Math.min(ex, sourceWidth));

                int tileWidth = ex - sx;
                int tileHeight = ey - sy;
                if (tileWidth <= 0 || tileHeight <= 0) {
                    continue;
                }

                BufferedImage tileImage = new BufferedImage(tileWidth, tileHeight, BufferedImage.TYPE_INT_RGB);
                Graphics2D graphics = tileImage.createGraphics();
                graphics.drawImage(sourceImage, 0, 0, tileWidth, tileHeight, sx, sy, ex, ey, null);
                graphics.dispose();

                String tileBase64 = encodeImageToBase64Png(tileImage);
                if (tileBase64 == null || tileBase64.isBlank()) {
                    continue;
                }

                int tileRows = extRowEnd - extRowStart;
                int tileCols = extColEnd - extColStart;
                GridAnalysisResult tileResult = analyzeSingleImageWithVariants(
                        accessToken,
                        tileBase64,
                        tileRows,
                        tileCols,
                        tileWidth,
                        tileHeight,
                        candidateCodes
                );

                mergedOcrCount += tileResult.getOcrCount();
                if (tileResult.getCells() == null) {
                    continue;
                }

                for (GridAnalysisCell cell : tileResult.getCells()) {
                    int globalRow = extRowStart + cell.getRow();
                    int globalCol = extColStart + cell.getCol();
                    if (globalRow < 0 || globalRow >= rows || globalCol < 0 || globalCol >= cols) {
                        continue;
                    }
                    String key = globalRow + "," + globalCol;
                    String code = normalizeColorCode(cell.getCode());
                    if (code.isBlank()) {
                        continue;
                    }
                    Map<String, CodeHit> codeHits = mergedVotes.computeIfAbsent(key, ignored -> new HashMap<>());
                    CodeHit previous = codeHits.get(code);
                    if (previous == null) {
                        codeHits.put(code, new CodeHit(1, 0.0));
                    } else {
                        codeHits.put(code, new CodeHit(previous.count() + 1, 0.0));
                    }
                }
            }
        }

        List<GridAnalysisCell> cells = new ArrayList<>();
        for (Map.Entry<String, Map<String, CodeHit>> entry : mergedVotes.entrySet()) {
            Map<String, CodeHit> codeHits = entry.getValue();
            if (codeHits == null || codeHits.isEmpty()) {
                continue;
            }
            String bestCode = codeHits.entrySet().stream()
                    .sorted((left, right) -> {
                        int countCompare = Integer.compare(right.getValue().count(), left.getValue().count());
                        if (countCompare != 0) {
                            return countCompare;
                        }
                        return compareColorCodes(left.getKey(), right.getKey());
                    })
                    .map(Map.Entry::getKey)
                    .findFirst()
                    .orElse(null);
            if (bestCode == null || bestCode.isBlank()) {
                continue;
            }

            String[] rc = entry.getKey().split(",");
            int r = Integer.parseInt(rc[0]);
            int c = Integer.parseInt(rc[1]);
            cells.add(new GridAnalysisCell(r, c, bestCode));
        }
        cells.sort(Comparator.comparingInt(GridAnalysisCell::getRow).thenComparingInt(GridAnalysisCell::getCol));

        GridAnalysisResult result = new GridAnalysisResult();
        result.setRows(rows);
        result.setCols(cols);
        result.setOcrCount(mergedOcrCount);
        result.setFilledCount(cells.size());
        result.setCells(cells);
        return result;
    }

    private GridAnalysisResult fillMissingCellsByColor(
            String base64,
            int rows,
            int cols,
            GridAnalysisResult current,
            List<String> candidateCodes,
            Map<String, Integer> candidateQuantities,
            Map<String, String> candidateColorHex
    ) {
        if (current == null || rows <= 0 || cols <= 0) {
            return current;
        }
        if (candidateCodes == null || candidateCodes.isEmpty()) {
            return current;
        }
        if (candidateQuantities == null || candidateQuantities.isEmpty()) {
            return current;
        }

        Map<String, LabColor> candidateLab = buildCandidateLabMap(candidateCodes, candidateColorHex);
        if (candidateLab.isEmpty()) {
            return current;
        }

        Map<String, Integer> remaining = new LinkedHashMap<>();
        for (String rawCode : candidateCodes) {
            String code = normalizeColorCode(rawCode);
            if (code.isBlank()) {
                continue;
            }
            int quantity = candidateQuantities.getOrDefault(code, 0);
            if (quantity > 0) {
                remaining.put(code, quantity);
            }
        }
        if (remaining.isEmpty()) {
            return current;
        }

        List<GridAnalysisCell> currentCells = current.getCells() == null ? new ArrayList<>() : new ArrayList<>(current.getCells());
        Set<String> occupied = new HashSet<>();
        for (GridAnalysisCell cell : currentCells) {
            if (cell == null) {
                continue;
            }
            int row = cell.getRow();
            int col = cell.getCol();
            if (row < 0 || row >= rows || col < 0 || col >= cols) {
                continue;
            }
            String code = normalizeColorCode(cell.getCode());
            if (!code.isBlank()) {
                occupied.add(row + "," + col);
                if (remaining.containsKey(code)) {
                    remaining.put(code, Math.max(0, remaining.get(code) - 1));
                }
            }
        }

        if (remaining.values().stream().allMatch(value -> value <= 0)) {
            current.setCells(currentCells);
            current.setFilledCount(currentCells.size());
            return current;
        }

        BufferedImage source = decodeBase64Image(base64);
        if (source == null || source.getWidth() <= 0 || source.getHeight() <= 0) {
            return current;
        }

        int width = source.getWidth();
        int height = source.getHeight();
        double maxDistance = 32.0;

        List<MissingCellScore> scores = new ArrayList<>();
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                String key = row + "," + col;
                if (occupied.contains(key)) {
                    continue;
                }

                CellColor cellColor = sampleCellColor(source, row, col, rows, cols, width, height);
                if (cellColor == null) {
                    continue;
                }

                String bestCode = null;
                String secondCode = null;
                double bestDistance = Double.MAX_VALUE;
                double secondDistance = Double.MAX_VALUE;

                for (Map.Entry<String, Integer> candidate : remaining.entrySet()) {
                    if (candidate.getValue() == null || candidate.getValue() <= 0) {
                        continue;
                    }
                    LabColor lab = candidateLab.get(candidate.getKey());
                    if (lab == null) {
                        continue;
                    }
                    double distance = colorDistance(cellColor.lab(), lab);
                    if (distance < bestDistance) {
                        secondDistance = bestDistance;
                        secondCode = bestCode;
                        bestDistance = distance;
                        bestCode = candidate.getKey();
                    } else if (distance < secondDistance) {
                        secondDistance = distance;
                        secondCode = candidate.getKey();
                    }
                }

                if (bestCode == null || bestDistance > maxDistance) {
                    continue;
                }

                double confidenceGap = secondCode == null ? 100.0 : Math.max(0.0, secondDistance - bestDistance);
                scores.add(new MissingCellScore(row, col, bestCode, bestDistance, confidenceGap));
            }
        }

        if (scores.isEmpty()) {
            return current;
        }

        scores.sort((left, right) -> {
            int gapCompare = Double.compare(right.confidenceGap(), left.confidenceGap());
            if (gapCompare != 0) {
                return gapCompare;
            }
            int distanceCompare = Double.compare(left.distance(), right.distance());
            if (distanceCompare != 0) {
                return distanceCompare;
            }
            int rowCompare = Integer.compare(left.row(), right.row());
            if (rowCompare != 0) {
                return rowCompare;
            }
            return Integer.compare(left.col(), right.col());
        });

        int maxFill = Math.max(1, (int) Math.round(rows * cols * 0.35));
        int filledByColor = 0;
        for (MissingCellScore score : scores) {
            if (filledByColor >= maxFill) {
                break;
            }
            String code = score.code();
            int remain = remaining.getOrDefault(code, 0);
            if (remain <= 0) {
                continue;
            }
            String key = score.row() + "," + score.col();
            if (occupied.contains(key)) {
                continue;
            }

            currentCells.add(new GridAnalysisCell(score.row(), score.col(), code));
            occupied.add(key);
            remaining.put(code, remain - 1);
            filledByColor++;
        }

        currentCells.sort(Comparator.comparingInt(GridAnalysisCell::getRow).thenComparingInt(GridAnalysisCell::getCol));
        current.setCells(currentCells);
        current.setFilledCount(currentCells.size());
        return current;
    }

    private Map<String, LabColor> buildCandidateLabMap(List<String> candidateCodes, Map<String, String> candidateColorHex) {
        Map<String, LabColor> result = new LinkedHashMap<>();
        if (candidateCodes == null || candidateCodes.isEmpty()) {
            return result;
        }

        for (String rawCode : candidateCodes) {
            String code = normalizeColorCode(rawCode);
            if (code.isBlank()) {
                continue;
            }
            String hex = candidateColorHex == null ? null : candidateColorHex.get(code);
            if (hex == null || hex.isBlank()) {
                continue;
            }

            RgbColor rgb = parseHexColor(hex);
            if (rgb == null) {
                continue;
            }
            result.put(code, rgbToLab(rgb.red(), rgb.green(), rgb.blue()));
        }

        return result;
    }

    private CellColor sampleCellColor(
            BufferedImage image,
            int row,
            int col,
            int rows,
            int cols,
            int width,
            int height
    ) {
        int xStart = (int) Math.floor(col * (double) width / cols);
        int xEnd = (int) Math.ceil((col + 1) * (double) width / cols);
        int yStart = (int) Math.floor(row * (double) height / rows);
        int yEnd = (int) Math.ceil((row + 1) * (double) height / rows);

        int xMargin = Math.max(1, (xEnd - xStart) / 5);
        int yMargin = Math.max(1, (yEnd - yStart) / 5);
        int sx = Math.max(0, xStart + xMargin);
        int ex = Math.min(width, xEnd - xMargin);
        int sy = Math.max(0, yStart + yMargin);
        int ey = Math.min(height, yEnd - yMargin);

        if (ex <= sx || ey <= sy) {
            sx = Math.max(0, xStart);
            ex = Math.min(width, xEnd);
            sy = Math.max(0, yStart);
            ey = Math.min(height, yEnd);
            if (ex <= sx || ey <= sy) {
                return null;
            }
        }

        long sumR = 0;
        long sumG = 0;
        long sumB = 0;
        int count = 0;

        for (int y = sy; y < ey; y++) {
            for (int x = sx; x < ex; x++) {
                int rgb = image.getRGB(x, y);
                int red = (rgb >> 16) & 0xFF;
                int green = (rgb >> 8) & 0xFF;
                int blue = rgb & 0xFF;
                int max = Math.max(red, Math.max(green, blue));
                int min = Math.min(red, Math.min(green, blue));
                int lum = (red * 299 + green * 587 + blue * 114) / 1000;

                if (lum < 28 && (max - min) < 40) {
                    continue;
                }

                sumR += red;
                sumG += green;
                sumB += blue;
                count++;
            }
        }

        if (count <= 0) {
            return null;
        }

        int avgR = (int) Math.round(sumR / (double) count);
        int avgG = (int) Math.round(sumG / (double) count);
        int avgB = (int) Math.round(sumB / (double) count);
        LabColor lab = rgbToLab(avgR, avgG, avgB);
        return new CellColor(avgR, avgG, avgB, lab);
    }

    private RgbColor parseHexColor(String hex) {
        if (hex == null) {
            return null;
        }
        String value = hex.trim();
        if (value.startsWith("#")) {
            value = value.substring(1);
        }
        if (value.length() == 3) {
            value = "" + value.charAt(0) + value.charAt(0)
                    + value.charAt(1) + value.charAt(1)
                    + value.charAt(2) + value.charAt(2);
        }
        if (!value.matches("^[0-9A-Fa-f]{6}$")) {
            return null;
        }

        int red = Integer.parseInt(value.substring(0, 2), 16);
        int green = Integer.parseInt(value.substring(2, 4), 16);
        int blue = Integer.parseInt(value.substring(4, 6), 16);
        return new RgbColor(red, green, blue);
    }

    private double colorDistance(LabColor left, LabColor right) {
        double dL = left.l() - right.l();
        double dA = left.a() - right.a();
        double dB = left.b() - right.b();
        return Math.sqrt(dL * dL + dA * dA + dB * dB);
    }

    private LabColor rgbToLab(int red, int green, int blue) {
        double r = pivotRgb(red / 255.0);
        double g = pivotRgb(green / 255.0);
        double b = pivotRgb(blue / 255.0);

        double x = r * 0.4124 + g * 0.3576 + b * 0.1805;
        double y = r * 0.2126 + g * 0.7152 + b * 0.0722;
        double z = r * 0.0193 + g * 0.1192 + b * 0.9505;

        double xr = x / 0.95047;
        double yr = y / 1.00000;
        double zr = z / 1.08883;

        double fx = pivotXyz(xr);
        double fy = pivotXyz(yr);
        double fz = pivotXyz(zr);

        double l = Math.max(0.0, 116.0 * fy - 16.0);
        double a = 500.0 * (fx - fy);
        double bb = 200.0 * (fy - fz);
        return new LabColor(l, a, bb);
    }

    private double pivotRgb(double value) {
        return value <= 0.04045 ? value / 12.92 : Math.pow((value + 0.055) / 1.055, 2.4);
    }

    private double pivotXyz(double value) {
        return value > 0.008856 ? Math.cbrt(value) : (7.787 * value) + (16.0 / 116.0);
    }

    public List<String> allTags() {
        Set<String> tags = new LinkedHashSet<>();
        for (TagOption option : tagOptionRepository.findAll()) {
            String normalized = normalizeTag(option.getTag());
            if (normalized != null && !normalized.isBlank()) {
                tags.add(normalized);
            }
        }
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

        tagOptionRepository.deleteById(targetTag);

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

    public String addTagOption(String rawTag) {
        String tag = normalizeTag(rawTag);
        if (tag == null || tag.isBlank()) {
            throw new IllegalArgumentException("tag不能为空");
        }
        if (!tagOptionRepository.existsById(tag)) {
            tagOptionRepository.save(new TagOption(tag));
        }
        return tag;
    }

    public Map<String, String> getOcrSettings() {
        String ak = resolveSettingValue("baidu.ocr.ak", baiduApiKey);
        String sk = resolveSettingValue("baidu.ocr.sk", baiduSecretKey);
        String ak2 = resolveSettingValue("baidu.ocr.ak2", baiduApiKey2);
        String sk2 = resolveSettingValue("baidu.ocr.sk2", baiduSecretKey2);
        Map<String, String> result = new LinkedHashMap<>();
        result.put("ak", ak == null ? "" : ak);
        result.put("sk", sk == null ? "" : sk);
        result.put("ak2", ak2 == null ? "" : ak2);
        result.put("sk2", sk2 == null ? "" : sk2);
        return result;
    }

    public Map<String, String> saveOcrSettings(Map<String, Object> payload) {
        String ak = normalizeOptionalText(payload == null ? null : String.valueOf(payload.get("ak")));
        String sk = normalizeOptionalText(payload == null ? null : String.valueOf(payload.get("sk")));
        String ak2 = normalizeOptionalText(payload == null ? null : String.valueOf(payload.get("ak2")));
        String sk2 = normalizeOptionalText(payload == null ? null : String.valueOf(payload.get("sk2")));

        saveSettingValue("baidu.ocr.ak", ak);
        saveSettingValue("baidu.ocr.sk", sk);
        saveSettingValue("baidu.ocr.ak2", ak2);
        saveSettingValue("baidu.ocr.sk2", sk2);

        return getOcrSettings();
    }

    private String resolveSettingValue(String key, String fallback) {
        String dbValue = appSettingRepository.findById(key)
                .map(AppSetting::getValue)
                .map(this::normalizeOptionalText)
                .orElse(null);
        if (dbValue != null) {
            return dbValue;
        }
        return normalizeOptionalText(fallback);
    }

    private void saveSettingValue(String key, String value) {
        if (value == null || value.isBlank()) {
            appSettingRepository.deleteById(key);
            return;
        }
        appSettingRepository.save(new AppSetting(key, value));
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
        String ak = resolveSettingValue("baidu.ocr.ak", baiduApiKey);
        String sk = resolveSettingValue("baidu.ocr.sk", baiduSecretKey);
        String ak2 = resolveSettingValue("baidu.ocr.ak2", baiduApiKey2);
        String sk2 = resolveSettingValue("baidu.ocr.sk2", baiduSecretKey2);

        if (isNonBlank(ak) && isNonBlank(sk)) {
            keyPairs.add(new KeyPair(ak.trim(), sk.trim()));
        }
        if (isNonBlank(ak2) && isNonBlank(sk2)) {
            keyPairs.add(new KeyPair(ak2.trim(), sk2.trim()));
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
        String trimmed = imageBase64.trim();

        if (!trimmed.startsWith("data:")) {
            String mapOriginal = extractWrappedImageField(trimmed, "originalImageBase64=");
            if (mapOriginal != null && !mapOriginal.isBlank()) {
                trimmed = mapOriginal;
            } else {
                String mapImage = extractWrappedImageField(trimmed, "imageBase64=");
                if (mapImage != null && !mapImage.isBlank()) {
                    trimmed = mapImage;
                }
            }
        }

        int commaIndex = trimmed.indexOf(',');
        if (trimmed.startsWith("data:") && commaIndex >= 0) {
            trimmed = trimmed.substring(commaIndex + 1);
        }
        return sanitizeBase64Payload(trimmed);
    }

    private String extractWrappedImageField(String text, String keyToken) {
        if (text == null || text.isBlank()) {
            return null;
        }
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

    private String sanitizeBase64Payload(String base64) {
        if (base64 == null) {
            return null;
        }
        String cleaned = base64.trim();
        if (cleaned.contains("%2B") || cleaned.contains("%2F") || cleaned.contains("%3D")
                || cleaned.contains("%2b") || cleaned.contains("%2f") || cleaned.contains("%3d")) {
            try {
                cleaned = URLDecoder.decode(cleaned, StandardCharsets.UTF_8);
            } catch (Exception ignored) {
            }
        }
        cleaned = cleaned.replaceAll("\\s+", "");
        cleaned = cleaned.replace('-', '+').replace('_', '/');
        return cleaned;
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
        RuntimeException firstError = null;
        try {
            return fetchBaiduOcrRawTextByEndpoint(accessToken, base64, "accurate_basic", "通用文字识别（高精度版）");
        } catch (RuntimeException exception) {
            firstError = exception;
            LOGGER.warn("OCR raw fallback: accurate_basic failed, try general_basic. reason={}", exception.getMessage());
        }

        try {
            return fetchBaiduOcrRawTextByEndpoint(accessToken, base64, "general_basic", "通用文字识别（标准版）");
        } catch (RuntimeException exception) {
            LOGGER.warn("OCR raw fallback: general_basic failed, try webimage. reason={}", exception.getMessage());
        }

        try {
            return fetchBaiduOcrRawTextByEndpoint(accessToken, base64, "webimage", "网络图片文字识别");
        } catch (RuntimeException exception) {
            if (firstError != null) {
                throw firstError;
            }
            throw exception;
        }
    }

    private List<OcrWordBox> fetchBaiduOcrWordsWithLocation(String accessToken, String base64) {
        RuntimeException accurateError = null;
        try {
            return fetchBaiduOcrWordsWithLocationByEndpoint(accessToken, base64, "accurate", "通用文字识别（高精度含位置版）");
        } catch (RuntimeException exception) {
            accurateError = exception;
            LOGGER.warn("OCR location fallback: accurate failed, try general. reason={}", exception.getMessage());
        }

        try {
            return fetchBaiduOcrWordsWithLocationByEndpoint(accessToken, base64, "general", "通用文字识别（标准含位置版）");
        } catch (RuntimeException exception) {
            if (accurateError != null) {
                throw accurateError;
            }
            throw exception;
        }
    }

    private String fetchBaiduOcrRawTextByEndpoint(String accessToken, String base64, String endpoint, String serviceName) {
        JsonNode root = callBaiduOcr(accessToken, base64, endpoint, true, false, serviceName);
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
    }

    private List<OcrWordBox> fetchBaiduOcrWordsWithLocationByEndpoint(String accessToken, String base64, String endpoint, String serviceName) {
        JsonNode root = callBaiduOcr(accessToken, base64, endpoint, true, true, serviceName);
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
    }

    private JsonNode callBaiduOcr(
            String accessToken,
            String base64,
            String endpoint,
            boolean detectDirection,
            boolean needLocation,
            String serviceName
    ) {
        try {
            acquireGlobalOcrPermit();
            markOcrServiceUsed(endpoint);
            String url = "https://aip.baidubce.com/rest/2.0/ocr/v1/" + endpoint + "?access_token="
                    + URLEncoder.encode(accessToken, StandardCharsets.UTF_8);

            StringBuilder body = new StringBuilder("image=")
                    .append(URLEncoder.encode(base64, StandardCharsets.UTF_8))
                    .append("&detect_direction=")
                    .append(detectDirection)
                    .append("&probability=false");

            if (needLocation) {
                body.append("&vertexes_location=false");
            }

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException(serviceName + " 调用失败，HTTP " + response.statusCode());
            }

            JsonNode root = objectMapper.readTree(response.body());
            if (root.hasNonNull("error_code")) {
                int code = root.path("error_code").asInt();
                String message = root.path("error_msg").asText("unknown");
                throw new IllegalStateException(serviceName + " 错误[" + code + "]: " + message);
            }
            return root;
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException(serviceName + " 调用异常: " + exception.getMessage(), exception);
        }
    }

    private void acquireGlobalOcrPermit() {
        synchronized (OCR_RATE_LIMIT_LOCK) {
            while (true) {
                long now = System.nanoTime();
                long waitNanos = OCR_MIN_INTERVAL_NANOS - (now - lastOcrRequestNanos);
                if (waitNanos <= 0) {
                    lastOcrRequestNanos = now;
                    return;
                }

                long waitMillis = waitNanos / 1_000_000L;
                int waitNanoPart = (int) (waitNanos % 1_000_000L);
                try {
                    OCR_RATE_LIMIT_LOCK.wait(waitMillis, waitNanoPart);
                } catch (InterruptedException interruptedException) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("OCR调用被中断", interruptedException);
                }
            }
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
        GridGeometry geometry = buildUniformGridGeometry(rows, cols, imageWidth, imageHeight);
        return mapWordsToGrid(words, rows, cols, geometry, candidateCodes);
        }

        private GridAnalysisResult mapWordsToGrid(
            List<OcrWordBox> words,
            int rows,
            int cols,
            GridGeometry geometry,
            List<String> candidateCodes
        ) {
        GridGeometry workingGeometry = sanitizeGeometry(geometry, rows, cols);
        int[] xBoundaries = workingGeometry.xBoundaries();
        int[] yBoundaries = workingGeometry.yBoundaries();
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

        Map<String, Map<String, CodeHit>> gridVotes = new HashMap<>();
        int ocrCount = 0;

        for (OcrWordBox word : words) {
            String text = cleanOcrLine(word.words());
            if (text.isBlank()) {
                continue;
            }

            List<TokenHit> tokenHits = extractTokenHitsFromWord(word, text, codePattern, normalizedCandidates);
            for (TokenHit tokenHit : tokenHits) {
                int col = locateGridIndex(xBoundaries, tokenHit.centerX());
                int row = locateGridIndex(yBoundaries, tokenHit.centerY());
                if (row < 0 || row >= rows || col < 0 || col >= cols) {
                    continue;
                }

                double cellCenterX = (xBoundaries[col] + xBoundaries[col + 1]) / 2.0;
                double cellCenterY = (yBoundaries[row] + yBoundaries[row + 1]) / 2.0;
                double distance = Math.hypot(tokenHit.centerX() - cellCenterX, tokenHit.centerY() - cellCenterY);
                String key = row + "," + col;
                Map<String, CodeHit> codeHits = gridVotes.computeIfAbsent(key, ignored -> new HashMap<>());
                CodeHit previous = codeHits.get(tokenHit.code());
                if (previous == null) {
                    codeHits.put(tokenHit.code(), new CodeHit(1, distance));
                } else {
                    codeHits.put(tokenHit.code(), new CodeHit(previous.count() + 1, Math.min(previous.minDistance(), distance)));
                }
                ocrCount++;
            }
        }

        List<GridAnalysisCell> cells = new ArrayList<>();
        for (Map.Entry<String, Map<String, CodeHit>> entry : gridVotes.entrySet()) {
            Map<String, CodeHit> codeHits = entry.getValue();
            if (codeHits == null || codeHits.isEmpty()) {
                continue;
            }
            String bestCode = codeHits.entrySet().stream()
                    .sorted((left, right) -> {
                        int countCompare = Integer.compare(right.getValue().count(), left.getValue().count());
                        if (countCompare != 0) {
                            return countCompare;
                        }
                        int distanceCompare = Double.compare(left.getValue().minDistance(), right.getValue().minDistance());
                        if (distanceCompare != 0) {
                            return distanceCompare;
                        }
                        return compareColorCodes(left.getKey(), right.getKey());
                    })
                    .map(Map.Entry::getKey)
                    .findFirst()
                    .orElse(null);
            if (bestCode == null || bestCode.isBlank()) {
                continue;
            }

            String[] rc = entry.getKey().split(",");
            int r = Integer.parseInt(rc[0]);
            int c = Integer.parseInt(rc[1]);
            cells.add(new GridAnalysisCell(r, c, bestCode));
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

    private PreparedGridImage prepareImageForGridRecognition(
            String base64,
            int rows,
            int cols,
            int fallbackWidth,
            int fallbackHeight
    ) {
        BufferedImage source = decodeBase64Image(base64);
        if (source == null) {
            GridGeometry fallbackGeometry = buildUniformGridGeometry(rows, cols, fallbackWidth, fallbackHeight);
            return new PreparedGridImage(base64, fallbackGeometry, false);
        }

        BufferedImage rgb = ensureRgbImage(source);
        PerspectiveCorrectionResult corrected = correctPerspectiveIfPossible(rgb);
        BufferedImage aligned = corrected.correctedImage() == null ? rgb : corrected.correctedImage();

        GridGeometry correctedGeometry = detectGridGeometry(aligned, rows, cols);
        GridGeometry originalGeometry = detectGridGeometry(rgb, rows, cols);

        BufferedImage finalImage = rgb;
        GridGeometry finalGeometry = buildUniformGridGeometry(rows, cols, rgb.getWidth(), rgb.getHeight());
        boolean preprocessed = false;

        if (corrected.corrected() && correctedGeometry != null) {
            finalImage = aligned;
            finalGeometry = correctedGeometry;
            preprocessed = true;
        } else if (originalGeometry != null) {
            finalImage = rgb;
            finalGeometry = originalGeometry;
            preprocessed = true;
        }

        String encoded = encodeImageToBase64Png(finalImage);
        if (encoded == null || encoded.isBlank()) {
            encoded = base64;
        }

        return new PreparedGridImage(encoded, finalGeometry, preprocessed);
    }

    private PreparedGridImage buildBaselineGridImage(
            String base64,
            int rows,
            int cols,
            int fallbackWidth,
            int fallbackHeight
    ) {
        BufferedImage source = decodeBase64Image(base64);
        if (source == null) {
            GridGeometry fallbackGeometry = buildUniformGridGeometry(rows, cols, fallbackWidth, fallbackHeight);
            return new PreparedGridImage(base64, fallbackGeometry, false);
        }
        BufferedImage rgb = ensureRgbImage(source);
        GridGeometry geometry = buildUniformGridGeometry(rows, cols, rgb.getWidth(), rgb.getHeight());
        String encoded = encodeImageToBase64Png(rgb);
        if (encoded == null || encoded.isBlank()) {
            encoded = base64;
        }
        return new PreparedGridImage(encoded, geometry, false);
    }

    private GridGeometry detectGridGeometry(BufferedImage image, int rows, int cols) {
        if (image == null || rows <= 0 || cols <= 0) {
            return null;
        }

        BufferedImage gray = toGrayscale(image);
        int threshold = computeOtsuThreshold(gray);
        BufferedImage binary = toBinary(gray, threshold, true);

        int[] xBoundaries = detectGridBoundaries(binary, cols + 1, true);
        int[] yBoundaries = detectGridBoundaries(binary, rows + 1, false);

        if (!isValidBoundaries(xBoundaries, image.getWidth()) || !isValidBoundaries(yBoundaries, image.getHeight())) {
            return null;
        }

        if (!isBoundaryCloseToUniform(xBoundaries, image.getWidth()) || !isBoundaryCloseToUniform(yBoundaries, image.getHeight())) {
            return null;
        }

        return new GridGeometry(image.getWidth(), image.getHeight(), xBoundaries, yBoundaries);
    }

    private boolean isBoundaryCloseToUniform(int[] boundaries, int size) {
        if (boundaries == null || boundaries.length < 2 || size <= 1) {
            return false;
        }
        int segments = boundaries.length - 1;
        double uniformStep = (size - 1.0) / segments;
        double maxAllowedShift = Math.max(4.0, uniformStep * 0.55);
        double sumShift = 0.0;

        for (int i = 1; i < boundaries.length - 1; i++) {
            double expected = i * uniformStep;
            double shift = Math.abs(boundaries[i] - expected);
            if (shift > maxAllowedShift) {
                return false;
            }
            sumShift += shift;
        }

        double avgShift = sumShift / Math.max(1, boundaries.length - 2);
        return avgShift <= Math.max(2.5, uniformStep * 0.22);
    }

    private int[] detectGridBoundaries(BufferedImage binary, int boundaryCount, boolean vertical) {
        int length = vertical ? binary.getWidth() : binary.getHeight();
        int orthogonal = vertical ? binary.getHeight() : binary.getWidth();
        if (boundaryCount <= 1 || length <= 2) {
            return null;
        }

        double[] score = new double[length];
        for (int primary = 0; primary < length; primary++) {
            int dark = 0;
            for (int secondary = 0; secondary < orthogonal; secondary++) {
                int x = vertical ? primary : secondary;
                int y = vertical ? secondary : primary;
                int lum = binary.getRGB(x, y) & 0xFF;
                if (lum <= 110) {
                    dark++;
                }
            }
            score[primary] = dark;
        }

        double[] smooth = smoothArray(score, Math.max(2, length / Math.max(16, boundaryCount * 2)));
        int[] boundaries = new int[boundaryCount];
        boundaries[0] = 0;
        boundaries[boundaryCount - 1] = length - 1;

        for (int i = 1; i < boundaryCount - 1; i++) {
            int expected = (int) Math.round(i * (length - 1.0) / (boundaryCount - 1));
            int searchWindow = Math.max(4, length / Math.max(boundaryCount * 3, 10));
            int start = Math.max(boundaries[i - 1] + 1, expected - searchWindow);
            int end = Math.min(length - (boundaryCount - i), expected + searchWindow);

            int bestIndex = expected;
            double bestScore = Double.NEGATIVE_INFINITY;
            for (int p = start; p <= end; p++) {
                double distancePenalty = Math.abs(p - expected) * 0.6;
                double candidate = smooth[p] - distancePenalty;
                if (candidate > bestScore) {
                    bestScore = candidate;
                    bestIndex = p;
                }
            }
            boundaries[i] = bestIndex;
        }

        for (int i = 1; i < boundaryCount; i++) {
            if (boundaries[i] <= boundaries[i - 1]) {
                boundaries[i] = Math.min(length - (boundaryCount - i), boundaries[i - 1] + 1);
            }
        }

        return boundaries;
    }

    private double[] smoothArray(double[] values, int radius) {
        double[] smooth = new double[values.length];
        double running = 0.0;
        int window = radius * 2 + 1;

        for (int i = 0; i < values.length; i++) {
            running += values[i];
            if (i - window >= 0) {
                running -= values[i - window];
            }
            int left = Math.max(0, i - radius);
            int right = i;
            if (i >= radius) {
                right = Math.min(values.length - 1, i + radius);
                double sum = 0.0;
                for (int p = left; p <= right; p++) {
                    sum += values[p];
                }
                smooth[i] = sum / Math.max(1, right - left + 1);
            } else {
                smooth[i] = running / Math.max(1, i + 1);
            }
        }
        return smooth;
    }

    private boolean isValidBoundaries(int[] boundaries, int size) {
        if (boundaries == null || boundaries.length < 2 || size <= 1) {
            return false;
        }
        if (boundaries[0] != 0 || boundaries[boundaries.length - 1] != size - 1) {
            return false;
        }
        int minGap = Math.max(1, size / Math.max(500, boundaries.length * 20));
        for (int i = 1; i < boundaries.length; i++) {
            if (boundaries[i] - boundaries[i - 1] < minGap) {
                return false;
            }
        }
        return true;
    }

    private int locateGridIndex(int[] boundaries, double value) {
        if (boundaries == null || boundaries.length < 2) {
            return -1;
        }
        if (value < boundaries[0] || value > boundaries[boundaries.length - 1]) {
            return -1;
        }

        int low = 0;
        int high = boundaries.length - 2;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            if (value < boundaries[mid]) {
                high = mid - 1;
            } else if (value >= boundaries[mid + 1]) {
                low = mid + 1;
            } else {
                return mid;
            }
        }
        return -1;
    }

    private GridGeometry buildUniformGridGeometry(int rows, int cols, int imageWidth, int imageHeight) {
        int safeWidth = Math.max(1, imageWidth);
        int safeHeight = Math.max(1, imageHeight);
        int[] xBoundaries = new int[Math.max(2, cols + 1)];
        int[] yBoundaries = new int[Math.max(2, rows + 1)];

        for (int i = 0; i < xBoundaries.length; i++) {
            xBoundaries[i] = (int) Math.round(i * (safeWidth - 1.0) / (xBoundaries.length - 1));
        }
        for (int i = 0; i < yBoundaries.length; i++) {
            yBoundaries[i] = (int) Math.round(i * (safeHeight - 1.0) / (yBoundaries.length - 1));
        }

        return new GridGeometry(safeWidth, safeHeight, xBoundaries, yBoundaries);
    }

    private GridGeometry sanitizeGeometry(GridGeometry geometry, int rows, int cols) {
        if (geometry == null) {
            return buildUniformGridGeometry(rows, cols, 1, 1);
        }
        if (geometry.xBoundaries() == null || geometry.yBoundaries() == null) {
            return buildUniformGridGeometry(rows, cols, geometry.width(), geometry.height());
        }
        if (geometry.xBoundaries().length != cols + 1 || geometry.yBoundaries().length != rows + 1) {
            return buildUniformGridGeometry(rows, cols, geometry.width(), geometry.height());
        }
        if (!isValidBoundaries(geometry.xBoundaries(), Math.max(1, geometry.width()))
                || !isValidBoundaries(geometry.yBoundaries(), Math.max(1, geometry.height()))) {
            return buildUniformGridGeometry(rows, cols, geometry.width(), geometry.height());
        }
        return geometry;
    }

    private PerspectiveCorrectionResult correctPerspectiveIfPossible(BufferedImage image) {
        if (image == null || image.getWidth() < 40 || image.getHeight() < 40) {
            return new PerspectiveCorrectionResult(image, false);
        }

        BufferedImage gray = toGrayscale(image);
        int threshold = computeOtsuThreshold(gray);
        BufferedImage binary = toBinary(gray, threshold, true);

        EdgeQuadrilateral quad = detectOuterQuadrilateral(binary);
        if (quad == null) {
            return new PerspectiveCorrectionResult(image, false);
        }

        BufferedImage warped = warpPerspective(image, quad);
        if (warped == null) {
            return new PerspectiveCorrectionResult(image, false);
        }
        return new PerspectiveCorrectionResult(warped, true);
    }

    private EdgeQuadrilateral detectOuterQuadrilateral(BufferedImage binary) {
        int width = binary.getWidth();
        int height = binary.getHeight();

        List<PointD> leftPoints = new ArrayList<>();
        List<PointD> rightPoints = new ArrayList<>();
        for (int y = 0; y < height; y++) {
            int left = findFirstDarkPixelOnRow(binary, y, true);
            int right = findFirstDarkPixelOnRow(binary, y, false);
            if (left >= 0) {
                leftPoints.add(new PointD(left, y));
            }
            if (right >= 0) {
                rightPoints.add(new PointD(right, y));
            }
        }

        List<PointD> topPoints = new ArrayList<>();
        List<PointD> bottomPoints = new ArrayList<>();
        for (int x = 0; x < width; x++) {
            int top = findFirstDarkPixelOnCol(binary, x, true);
            int bottom = findFirstDarkPixelOnCol(binary, x, false);
            if (top >= 0) {
                topPoints.add(new PointD(x, top));
            }
            if (bottom >= 0) {
                bottomPoints.add(new PointD(x, bottom));
            }
        }

        if (leftPoints.size() < 20 || rightPoints.size() < 20 || topPoints.size() < 20 || bottomPoints.size() < 20) {
            return null;
        }

        Line leftLine = fitLineXAsFunctionOfY(leftPoints);
        Line rightLine = fitLineXAsFunctionOfY(rightPoints);
        Line topLine = fitLineYAsFunctionOfX(topPoints);
        Line bottomLine = fitLineYAsFunctionOfX(bottomPoints);

        if (leftLine == null || rightLine == null || topLine == null || bottomLine == null) {
            return null;
        }

        PointD topLeft = intersectLines(leftLine, topLine);
        PointD topRight = intersectLines(rightLine, topLine);
        PointD bottomRight = intersectLines(rightLine, bottomLine);
        PointD bottomLeft = intersectLines(leftLine, bottomLine);

        if (topLeft == null || topRight == null || bottomRight == null || bottomLeft == null) {
            return null;
        }

        if (!isPointWithinExtendedBounds(topLeft, width, height)
                || !isPointWithinExtendedBounds(topRight, width, height)
                || !isPointWithinExtendedBounds(bottomRight, width, height)
                || !isPointWithinExtendedBounds(bottomLeft, width, height)) {
            return null;
        }

        double area = polygonArea(List.of(topLeft, topRight, bottomRight, bottomLeft));
        if (area < width * height * 0.45) {
            return null;
        }

        double topWidth = distance(topLeft, topRight);
        double bottomWidth = distance(bottomLeft, bottomRight);
        double leftHeight = distance(topLeft, bottomLeft);
        double rightHeight = distance(topRight, bottomRight);

        if (!isSideRatioReasonable(topWidth, bottomWidth) || !isSideRatioReasonable(leftHeight, rightHeight)) {
            return null;
        }

        return new EdgeQuadrilateral(topLeft, topRight, bottomRight, bottomLeft);
    }

    private boolean isSideRatioReasonable(double left, double right) {
        if (left <= 1e-6 || right <= 1e-6) {
            return false;
        }
        double ratio = left > right ? left / right : right / left;
        return ratio <= 2.0;
    }

    private int findFirstDarkPixelOnRow(BufferedImage binary, int y, boolean fromLeft) {
        if (fromLeft) {
            for (int x = 0; x < binary.getWidth(); x++) {
                int lum = binary.getRGB(x, y) & 0xFF;
                if (lum <= 110) {
                    return x;
                }
            }
        } else {
            for (int x = binary.getWidth() - 1; x >= 0; x--) {
                int lum = binary.getRGB(x, y) & 0xFF;
                if (lum <= 110) {
                    return x;
                }
            }
        }
        return -1;
    }

    private int findFirstDarkPixelOnCol(BufferedImage binary, int x, boolean fromTop) {
        if (fromTop) {
            for (int y = 0; y < binary.getHeight(); y++) {
                int lum = binary.getRGB(x, y) & 0xFF;
                if (lum <= 110) {
                    return y;
                }
            }
        } else {
            for (int y = binary.getHeight() - 1; y >= 0; y--) {
                int lum = binary.getRGB(x, y) & 0xFF;
                if (lum <= 110) {
                    return y;
                }
            }
        }
        return -1;
    }

    private Line fitLineXAsFunctionOfY(List<PointD> points) {
        if (points == null || points.size() < 2) {
            return null;
        }
        double meanY = points.stream().mapToDouble(PointD::y).average().orElse(0.0);
        double meanX = points.stream().mapToDouble(PointD::x).average().orElse(0.0);

        double numerator = 0.0;
        double denominator = 0.0;
        for (PointD point : points) {
            double dy = point.y() - meanY;
            numerator += dy * (point.x() - meanX);
            denominator += dy * dy;
        }
        if (Math.abs(denominator) < 1e-6) {
            return null;
        }

        double slope = numerator / denominator;
        double intercept = meanX - slope * meanY;
        return new Line(1.0, -slope, -intercept);
    }

    private Line fitLineYAsFunctionOfX(List<PointD> points) {
        if (points == null || points.size() < 2) {
            return null;
        }
        double meanX = points.stream().mapToDouble(PointD::x).average().orElse(0.0);
        double meanY = points.stream().mapToDouble(PointD::y).average().orElse(0.0);

        double numerator = 0.0;
        double denominator = 0.0;
        for (PointD point : points) {
            double dx = point.x() - meanX;
            numerator += dx * (point.y() - meanY);
            denominator += dx * dx;
        }
        if (Math.abs(denominator) < 1e-6) {
            return null;
        }

        double slope = numerator / denominator;
        double intercept = meanY - slope * meanX;
        return new Line(slope, -1.0, intercept);
    }

    private PointD intersectLines(Line first, Line second) {
        double determinant = first.a() * second.b() - second.a() * first.b();
        if (Math.abs(determinant) < 1e-8) {
            return null;
        }
        double x = (first.b() * second.c() - second.b() * first.c()) / determinant;
        double y = (second.a() * first.c() - first.a() * second.c()) / determinant;
        return new PointD(x, y);
    }

    private boolean isPointWithinExtendedBounds(PointD point, int width, int height) {
        double marginX = width * 0.08;
        double marginY = height * 0.08;
        return point.x() >= -marginX
                && point.x() <= width - 1 + marginX
                && point.y() >= -marginY
                && point.y() <= height - 1 + marginY;
    }

    private double polygonArea(List<PointD> points) {
        if (points == null || points.size() < 3) {
            return 0.0;
        }
        double sum = 0.0;
        for (int i = 0; i < points.size(); i++) {
            PointD current = points.get(i);
            PointD next = points.get((i + 1) % points.size());
            sum += current.x() * next.y() - next.x() * current.y();
        }
        return Math.abs(sum) * 0.5;
    }

    private BufferedImage warpPerspective(BufferedImage source, EdgeQuadrilateral quad) {
        double topWidth = distance(quad.topLeft(), quad.topRight());
        double bottomWidth = distance(quad.bottomLeft(), quad.bottomRight());
        double leftHeight = distance(quad.topLeft(), quad.bottomLeft());
        double rightHeight = distance(quad.topRight(), quad.bottomRight());

        int targetWidth = Math.max(20, (int) Math.round((topWidth + bottomWidth) / 2.0));
        int targetHeight = Math.max(20, (int) Math.round((leftHeight + rightHeight) / 2.0));

        List<PointD> dstPoints = List.of(
                new PointD(0, 0),
                new PointD(targetWidth - 1.0, 0),
                new PointD(targetWidth - 1.0, targetHeight - 1.0),
                new PointD(0, targetHeight - 1.0)
        );
        List<PointD> srcPoints = List.of(quad.topLeft(), quad.topRight(), quad.bottomRight(), quad.bottomLeft());

        double[] homography = computeHomography(dstPoints, srcPoints);
        if (homography == null) {
            return null;
        }

        BufferedImage output = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < targetHeight; y++) {
            for (int x = 0; x < targetWidth; x++) {
                PointD mapped = applyHomography(homography, x, y);
                int rgb = sampleBilinearRgb(source, mapped.x(), mapped.y());
                output.setRGB(x, y, rgb);
            }
        }

        return output;
    }

    private double distance(PointD first, PointD second) {
        return Math.hypot(first.x() - second.x(), first.y() - second.y());
    }

    private double[] computeHomography(List<PointD> fromPoints, List<PointD> toPoints) {
        if (fromPoints == null || toPoints == null || fromPoints.size() != 4 || toPoints.size() != 4) {
            return null;
        }

        double[][] matrix = new double[8][8];
        double[] values = new double[8];

        for (int i = 0; i < 4; i++) {
            double x = fromPoints.get(i).x();
            double y = fromPoints.get(i).y();
            double u = toPoints.get(i).x();
            double v = toPoints.get(i).y();

            int row1 = i * 2;
            int row2 = row1 + 1;

            matrix[row1][0] = x;
            matrix[row1][1] = y;
            matrix[row1][2] = 1.0;
            matrix[row1][3] = 0.0;
            matrix[row1][4] = 0.0;
            matrix[row1][5] = 0.0;
            matrix[row1][6] = -u * x;
            matrix[row1][7] = -u * y;
            values[row1] = u;

            matrix[row2][0] = 0.0;
            matrix[row2][1] = 0.0;
            matrix[row2][2] = 0.0;
            matrix[row2][3] = x;
            matrix[row2][4] = y;
            matrix[row2][5] = 1.0;
            matrix[row2][6] = -v * x;
            matrix[row2][7] = -v * y;
            values[row2] = v;
        }

        double[] solved = solveLinearSystem(matrix, values);
        if (solved == null) {
            return null;
        }

        return new double[]{
                solved[0], solved[1], solved[2],
                solved[3], solved[4], solved[5],
                solved[6], solved[7], 1.0
        };
    }

    private double[] solveLinearSystem(double[][] matrix, double[] values) {
        int n = values.length;
        double[][] a = new double[n][n + 1];
        for (int i = 0; i < n; i++) {
            System.arraycopy(matrix[i], 0, a[i], 0, n);
            a[i][n] = values[i];
        }

        for (int pivot = 0; pivot < n; pivot++) {
            int maxRow = pivot;
            for (int row = pivot + 1; row < n; row++) {
                if (Math.abs(a[row][pivot]) > Math.abs(a[maxRow][pivot])) {
                    maxRow = row;
                }
            }

            if (Math.abs(a[maxRow][pivot]) < 1e-10) {
                return null;
            }

            if (maxRow != pivot) {
                double[] swap = a[pivot];
                a[pivot] = a[maxRow];
                a[maxRow] = swap;
            }

            double pivotValue = a[pivot][pivot];
            for (int col = pivot; col <= n; col++) {
                a[pivot][col] /= pivotValue;
            }

            for (int row = 0; row < n; row++) {
                if (row == pivot) {
                    continue;
                }
                double factor = a[row][pivot];
                if (Math.abs(factor) < 1e-12) {
                    continue;
                }
                for (int col = pivot; col <= n; col++) {
                    a[row][col] -= factor * a[pivot][col];
                }
            }
        }

        double[] result = new double[n];
        for (int i = 0; i < n; i++) {
            result[i] = a[i][n];
        }
        return result;
    }

    private PointD applyHomography(double[] h, double x, double y) {
        double denominator = h[6] * x + h[7] * y + h[8];
        if (Math.abs(denominator) < 1e-10) {
            return new PointD(-1, -1);
        }
        double mappedX = (h[0] * x + h[1] * y + h[2]) / denominator;
        double mappedY = (h[3] * x + h[4] * y + h[5]) / denominator;
        return new PointD(mappedX, mappedY);
    }

    private int sampleBilinearRgb(BufferedImage image, double x, double y) {
        int width = image.getWidth();
        int height = image.getHeight();

        if (x < 0 || y < 0 || x >= width - 1 || y >= height - 1) {
            int clampedX = (int) Math.max(0, Math.min(width - 1, Math.round(x)));
            int clampedY = (int) Math.max(0, Math.min(height - 1, Math.round(y)));
            return image.getRGB(clampedX, clampedY);
        }

        int x0 = (int) Math.floor(x);
        int y0 = (int) Math.floor(y);
        int x1 = x0 + 1;
        int y1 = y0 + 1;
        double dx = x - x0;
        double dy = y - y0;

        int rgb00 = image.getRGB(x0, y0);
        int rgb10 = image.getRGB(x1, y0);
        int rgb01 = image.getRGB(x0, y1);
        int rgb11 = image.getRGB(x1, y1);

        int red = bilinearChannel((rgb00 >> 16) & 0xFF, (rgb10 >> 16) & 0xFF, (rgb01 >> 16) & 0xFF, (rgb11 >> 16) & 0xFF, dx, dy);
        int green = bilinearChannel((rgb00 >> 8) & 0xFF, (rgb10 >> 8) & 0xFF, (rgb01 >> 8) & 0xFF, (rgb11 >> 8) & 0xFF, dx, dy);
        int blue = bilinearChannel(rgb00 & 0xFF, rgb10 & 0xFF, rgb01 & 0xFF, rgb11 & 0xFF, dx, dy);

        return (red << 16) | (green << 8) | blue;
    }

    private int bilinearChannel(int c00, int c10, int c01, int c11, double dx, double dy) {
        double top = c00 * (1.0 - dx) + c10 * dx;
        double bottom = c01 * (1.0 - dx) + c11 * dx;
        int value = (int) Math.round(top * (1.0 - dy) + bottom * dy);
        return Math.max(0, Math.min(255, value));
    }

    private List<TokenHit> extractTokenHitsFromWord(
            OcrWordBox word,
            String text,
            Pattern codePattern,
            Set<String> normalizedCandidates
    ) {
        Matcher matcher = codePattern.matcher(text);
        List<TokenHit> hits = new ArrayList<>();
        int textLength = Math.max(1, text.length());
        double centerY = word.top() + word.height() / 2.0;

        while (matcher.find()) {
            String token = normalizeOcrToken(matcher.group());
            String finalCode = normalizeWithCandidates(token, normalizedCandidates);
            if (finalCode == null || finalCode.isBlank()) {
                continue;
            }

            double tokenCenter = (matcher.start() + matcher.end()) / 2.0;
            double centerX = word.left() + (tokenCenter / textLength) * Math.max(1, word.width());
            hits.add(new TokenHit(finalCode, centerX, centerY));
        }

        return hits;
    }

    private String normalizeWithCandidates(String code, Set<String> candidates) {
        if (code == null || code.isBlank()) {
            return null;
        }
        String normalized = normalizeColorCode(code);
        if (normalized.isBlank()) {
            return null;
        }

        if (candidates == null || candidates.isEmpty()) {
            return normalized;
        }
        if (candidates.contains(normalized)) {
            return normalized;
        }

        for (String variant : generateCharConfusionVariants(normalized, 24)) {
            if (candidates.contains(variant)) {
                return variant;
            }
        }

        for (String candidate : candidates) {
            if (candidate == null || candidate.isBlank()) {
                continue;
            }
            if (candidate.startsWith(normalized) && candidate.length() - normalized.length() <= 1) {
                return candidate;
            }
            if (normalized.startsWith(candidate) && normalized.length() - candidate.length() <= 1) {
                return candidate;
            }
        }

        String best = null;
        double bestDistance = Double.MAX_VALUE;
        for (String candidate : candidates) {
            if (candidate == null || candidate.isBlank()) {
                continue;
            }
            double distance = confusionAwareDistance(normalized, candidate);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = candidate;
            }
        }

        double allowedDistance = normalized.length() <= 3 ? 0.75 : 1.25;
        if (best != null && bestDistance <= allowedDistance) {
            return best;
        }

        if (normalized.matches("^[A-Z]{1,2}\\d{1,4}$")) {
            String prefix = normalized.replaceAll("\\d+", "");
            for (String candidate : candidates) {
                if (candidate != null && candidate.startsWith(prefix)) {
                    return candidate;
                }
            }
        }

        return null;
    }

    private List<String> generateCharConfusionVariants(String normalized, int maxVariants) {
        if (normalized == null || normalized.isBlank() || maxVariants <= 0) {
            return List.of();
        }

        List<String> variants = new ArrayList<>();
        variants.add(normalized);

        for (int i = 0; i < normalized.length() && variants.size() < maxVariants; i++) {
            char current = normalized.charAt(i);
            List<Character> substitutions = getConfusableChars(current);
            if (substitutions.isEmpty()) {
                continue;
            }

            int existingSize = variants.size();
            for (int baseIndex = 0; baseIndex < existingSize && variants.size() < maxVariants; baseIndex++) {
                String base = variants.get(baseIndex);
                for (char replacement : substitutions) {
                    if (replacement == current) {
                        continue;
                    }
                    String next = base.substring(0, i) + replacement + base.substring(i + 1);
                    if (!variants.contains(next)) {
                        variants.add(next);
                    }
                    if (variants.size() >= maxVariants) {
                        break;
                    }
                }
            }
        }

        return variants;
    }

    private List<Character> getConfusableChars(char ch) {
        return switch (ch) {
            case '0' -> List.of('0', 'O', 'Q', 'D');
            case '1' -> List.of('1', 'I', 'L', 'T');
            case '2' -> List.of('2', 'Z');
            case '5' -> List.of('5', 'S');
            case '6' -> List.of('6', 'G');
            case '8' -> List.of('8', 'B');
            case 'O' -> List.of('O', '0', 'Q', 'D');
            case 'Q' -> List.of('Q', '0', 'O');
            case 'D' -> List.of('D', '0', 'O');
            case 'I' -> List.of('I', '1', 'L', 'T');
            case 'L' -> List.of('L', '1', 'I');
            case 'T' -> List.of('T', '1', 'I');
            case 'Z' -> List.of('Z', '2');
            case 'S' -> List.of('S', '5');
            case 'G' -> List.of('G', '6');
            case 'B' -> List.of('B', '8');
            default -> List.of(ch);
        };
    }

    private double confusionAwareDistance(String left, String right) {
        if (left == null || right == null) {
            return Double.MAX_VALUE / 4;
        }
        if (left.equals(right)) {
            return 0.0;
        }

        int leftLength = left.length();
        int rightLength = right.length();
        if (leftLength == 0) {
            return rightLength;
        }
        if (rightLength == 0) {
            return leftLength;
        }

        double[] previous = new double[rightLength + 1];
        double[] current = new double[rightLength + 1];
        for (int j = 0; j <= rightLength; j++) {
            previous[j] = j;
        }

        for (int i = 1; i <= leftLength; i++) {
            current[0] = i;
            char leftChar = left.charAt(i - 1);
            for (int j = 1; j <= rightLength; j++) {
                char rightChar = right.charAt(j - 1);
                double substitution = previous[j - 1] + substitutionCost(leftChar, rightChar);
                double insertion = current[j - 1] + 1.0;
                double deletion = previous[j] + 1.0;
                current[j] = Math.min(substitution, Math.min(insertion, deletion));
            }
            double[] swap = previous;
            previous = current;
            current = swap;
        }

        double distance = previous[rightLength];
        if (leftLength > 0 && rightLength > 0) {
            char leftHead = left.charAt(0);
            char rightHead = right.charAt(0);
            if (leftHead == rightHead) {
                distance -= 0.15;
            } else if (isConfusablePair(leftHead, rightHead)) {
                distance -= 0.08;
            }
        }

        return Math.max(0.0, distance);
    }

    private double substitutionCost(char left, char right) {
        if (left == right) {
            return 0.0;
        }
        if (isConfusablePair(left, right)) {
            return 0.35;
        }
        return 1.0;
    }

    private boolean isConfusablePair(char left, char right) {
        List<Character> options = getConfusableChars(left);
        return options.contains(right);
    }

    private List<String> buildGridOcrVariants(String originalBase64) {
        List<String> variants = new ArrayList<>();
        if (originalBase64 == null || originalBase64.isBlank()) {
            return variants;
        }
        variants.add(originalBase64);

        try {
            BufferedImage source = decodeBase64Image(originalBase64);
            if (source == null) {
                return variants;
            }

            BufferedImage rgb = ensureRgbImage(source);
            BufferedImage gray = toGrayscale(rgb);
            int threshold = computeOtsuThreshold(gray);

            List<BufferedImage> images = new ArrayList<>();
            images.add(scaleImage(applyContrast(gray, 1.45), 2.0));
            images.add(scaleImage(toBinary(gray, threshold, true), 2.0));
            images.add(scaleImage(toBinary(gray, threshold, false), 2.0));

            for (BufferedImage image : images) {
                String encoded = encodeImageToBase64Png(image);
                if (encoded != null && !encoded.isBlank() && !variants.contains(encoded)) {
                    variants.add(encoded);
                }
                String jpegEncoded = encodeImageToBase64Jpeg(image);
                if (jpegEncoded != null && !jpegEncoded.isBlank() && !variants.contains(jpegEncoded)) {
                    variants.add(jpegEncoded);
                }
            }
        } catch (Exception ignored) {
            return variants;
        }

        return variants;
    }

    private BufferedImage decodeBase64Image(String base64) {
        try {
            String sanitized = sanitizeBase64Payload(base64);
            byte[] bytes;
            try {
                bytes = Base64.getDecoder().decode(sanitized);
            } catch (IllegalArgumentException decodeException) {
                bytes = Base64.getMimeDecoder().decode(sanitized);
            }
            return ImageIO.read(new ByteArrayInputStream(bytes));
        } catch (Exception exception) {
            return null;
        }
    }

    private String encodeImageToBase64Jpeg(BufferedImage image) {
        try {
            BufferedImage rgb = ensureRgbImage(image);
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            ImageIO.write(rgb, "jpg", outputStream);
            return Base64.getEncoder().encodeToString(outputStream.toByteArray());
        } catch (Exception exception) {
            return null;
        }
    }

    private String cropImageByRect(
            String originalImageBase64,
            Integer cropX,
            Integer cropY,
            Integer cropWidth,
            Integer cropHeight
    ) {
        String sourceBase64 = stripDataUrlPrefix(originalImageBase64);
        LOGGER.info("crop-image: rect=({}, {}, {}, {}), hasInput={}, strippedLength={}",
                cropX,
                cropY,
                cropWidth,
                cropHeight,
                originalImageBase64 != null,
                sourceBase64 == null ? 0 : sourceBase64.length());
        if (sourceBase64 == null || sourceBase64.isBlank()) {
            LOGGER.warn("crop-image: source base64 empty after strip/sanitize");
            return null;
        }
        if (cropX == null || cropY == null || cropWidth == null || cropHeight == null || cropWidth <= 0 || cropHeight <= 0) {
            return normalizeImageBase64ForOcr(sourceBase64);
        }

        BufferedImage source = decodeBase64Image(sourceBase64);
        if (source == null) {
            LOGGER.warn("crop-image: decode failed before cropping");
            return null;
        }

        int imageWidth = source.getWidth();
        int imageHeight = source.getHeight();
        if (imageWidth <= 0 || imageHeight <= 0) {
            LOGGER.warn("crop-image: invalid source image size {}x{}", imageWidth, imageHeight);
            return null;
        }

        int sx = Math.max(0, Math.min(cropX, imageWidth - 1));
        int sy = Math.max(0, Math.min(cropY, imageHeight - 1));
        int ex = Math.max(sx + 1, Math.min(imageWidth, sx + cropWidth));
        int ey = Math.max(sy + 1, Math.min(imageHeight, sy + cropHeight));

        int targetWidth = ex - sx;
        int targetHeight = ey - sy;
        if (targetWidth <= 0 || targetHeight <= 0) {
            LOGGER.warn("crop-image: invalid crop result size {}x{} with source {}x{}", targetWidth, targetHeight, imageWidth, imageHeight);
            return null;
        }

        BufferedImage cropped = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = cropped.createGraphics();
        graphics.drawImage(source, 0, 0, targetWidth, targetHeight, sx, sy, ex, ey, null);
        graphics.dispose();

        String encoded = encodeImageToBase64Png(cropped);
        if (encoded == null || encoded.isBlank()) {
            LOGGER.warn("crop-image: re-encode png failed after crop {}x{}", targetWidth, targetHeight);
        }
        return (encoded == null || encoded.isBlank()) ? null : encoded;
    }

    private String normalizeImageBase64ForOcr(String base64) {
        if (base64 == null || base64.isBlank()) {
            return base64;
        }
        String sanitized = sanitizeBase64Payload(base64);
        BufferedImage decoded = decodeBase64Image(sanitized);
        if (decoded == null) {
            LOGGER.warn("normalize-image: decode failed, keep sanitized payload. length={}", sanitized.length());
            return sanitized;
        }
        BufferedImage rgb = ensureRgbImage(decoded);
        String encoded = encodeImageToBase64Png(rgb);
        return (encoded == null || encoded.isBlank()) ? sanitized : encoded;
    }

    private void logImagePayloadDiagnostics(String scene, String originalPayload, String strippedPayload) {
        try {
            boolean hasDataPrefix = originalPayload != null && originalPayload.startsWith("data:");
            String mimeHint = "unknown";
            if (hasDataPrefix) {
                int sep = originalPayload.indexOf(';');
                int start = originalPayload.indexOf(':');
                if (start >= 0 && sep > start) {
                    mimeHint = originalPayload.substring(start + 1, sep);
                }
            }

            String payload = strippedPayload == null ? "" : strippedPayload;
            String sample = payload.isBlank() ? "" : payload.substring(0, Math.min(24, payload.length()));

            byte[] decodedBytes = null;
            boolean decoderOk = false;
            boolean mimeDecoderOk = false;
            String decoderError = "";
            try {
                decodedBytes = Base64.getDecoder().decode(payload);
                decoderOk = true;
            } catch (Exception ex) {
                decoderError = ex.getClass().getSimpleName() + ":" + ex.getMessage();
                try {
                    decodedBytes = Base64.getMimeDecoder().decode(payload);
                    mimeDecoderOk = true;
                } catch (Exception ex2) {
                    decoderError = decoderError + " | mime=" + ex2.getClass().getSimpleName() + ":" + ex2.getMessage();
                }
            }

            boolean imageReadable = false;
            int width = -1;
            int height = -1;
            if (decodedBytes != null) {
                try {
                    BufferedImage image = ImageIO.read(new ByteArrayInputStream(decodedBytes));
                    imageReadable = image != null;
                    if (image != null) {
                        width = image.getWidth();
                        height = image.getHeight();
                    }
                } catch (Exception imageEx) {
                    decoderError = decoderError + " | imageio=" + imageEx.getClass().getSimpleName() + ":" + imageEx.getMessage();
                }
            }

            LOGGER.info(
                    "ocr-payload[{}]: hasDataPrefix={}, mimeHint={}, strippedLength={}, sampleHead={}, base64DecoderOk={}, mimeDecoderOk={}, imageReadable={}, imageSize={}x{}, decodeError={}",
                    scene,
                    hasDataPrefix,
                    mimeHint,
                    payload.length(),
                    sample,
                    decoderOk,
                    mimeDecoderOk,
                    imageReadable,
                    width,
                    height,
                    decoderError
            );
        } catch (Exception ignored) {
            LOGGER.warn("ocr-payload[{}]: diagnostics failed", scene);
        }
    }

    private BufferedImage ensureRgbImage(BufferedImage input) {
        if (input.getType() == BufferedImage.TYPE_INT_RGB) {
            return input;
        }
        BufferedImage rgb = new BufferedImage(input.getWidth(), input.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = rgb.createGraphics();
        graphics.setComposite(AlphaComposite.Src);
        graphics.drawImage(input, 0, 0, null);
        graphics.dispose();
        return rgb;
    }

    private BufferedImage toGrayscale(BufferedImage input) {
        BufferedImage gray = new BufferedImage(input.getWidth(), input.getHeight(), BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D graphics = gray.createGraphics();
        graphics.drawImage(input, 0, 0, null);
        graphics.dispose();
        return gray;
    }

    private BufferedImage applyContrast(BufferedImage input, double factor) {
        BufferedImage output = new BufferedImage(input.getWidth(), input.getHeight(), BufferedImage.TYPE_BYTE_GRAY);
        for (int y = 0; y < input.getHeight(); y++) {
            for (int x = 0; x < input.getWidth(); x++) {
                int rgb = input.getRGB(x, y);
                int lum = rgb & 0xFF;
                int enhanced = (int) Math.round((lum - 128) * factor + 128);
                int clamped = Math.max(0, Math.min(255, enhanced));
                int value = (clamped << 16) | (clamped << 8) | clamped;
                output.setRGB(x, y, value);
            }
        }
        return output;
    }

    private BufferedImage toBinary(BufferedImage gray, int threshold, boolean darkText) {
        BufferedImage output = new BufferedImage(gray.getWidth(), gray.getHeight(), BufferedImage.TYPE_BYTE_GRAY);
        for (int y = 0; y < gray.getHeight(); y++) {
            for (int x = 0; x < gray.getWidth(); x++) {
                int lum = gray.getRGB(x, y) & 0xFF;
                boolean textPixel = darkText ? lum <= threshold : lum >= threshold;
                int value = textPixel ? 0 : 255;
                int rgb = (value << 16) | (value << 8) | value;
                output.setRGB(x, y, rgb);
            }
        }
        return output;
    }

    private int computeOtsuThreshold(BufferedImage gray) {
        int[] histogram = new int[256];
        int width = gray.getWidth();
        int height = gray.getHeight();
        int total = width * height;

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int lum = gray.getRGB(x, y) & 0xFF;
                histogram[lum]++;
            }
        }

        double sum = 0;
        for (int i = 0; i < 256; i++) {
            sum += i * histogram[i];
        }

        double sumBackground = 0;
        int weightBackground = 0;
        double maxVariance = -1;
        int threshold = 128;

        for (int i = 0; i < 256; i++) {
            weightBackground += histogram[i];
            if (weightBackground == 0) {
                continue;
            }
            int weightForeground = total - weightBackground;
            if (weightForeground == 0) {
                break;
            }

            sumBackground += (double) i * histogram[i];
            double meanBackground = sumBackground / weightBackground;
            double meanForeground = (sum - sumBackground) / weightForeground;
            double variance = (double) weightBackground * weightForeground * Math.pow(meanBackground - meanForeground, 2);

            if (variance > maxVariance) {
                maxVariance = variance;
                threshold = i;
            }
        }

        return threshold;
    }

    private BufferedImage scaleImage(BufferedImage input, double factor) {
        int width = Math.max(1, (int) Math.round(input.getWidth() * factor));
        int height = Math.max(1, (int) Math.round(input.getHeight() * factor));
        BufferedImage output = new BufferedImage(width, height, input.getType() == 0 ? BufferedImage.TYPE_INT_RGB : input.getType());
        Graphics2D graphics = output.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        graphics.drawImage(input, 0, 0, width, height, null);
        graphics.dispose();
        return output;
    }

    private String encodeImageToBase64Png(BufferedImage image) {
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            ImageIO.write(image, "png", outputStream);
            return Base64.getEncoder().encodeToString(outputStream.toByteArray());
        } catch (Exception exception) {
            return null;
        }
    }

    private int levenshteinDistance(String left, String right) {
        if (left == null || right == null) {
            return Integer.MAX_VALUE / 4;
        }
        if (left.equals(right)) {
            return 0;
        }
        int leftLength = left.length();
        int rightLength = right.length();
        if (leftLength == 0) {
            return rightLength;
        }
        if (rightLength == 0) {
            return leftLength;
        }

        int[] previous = new int[rightLength + 1];
        int[] current = new int[rightLength + 1];

        for (int j = 0; j <= rightLength; j++) {
            previous[j] = j;
        }

        for (int i = 1; i <= leftLength; i++) {
            current[0] = i;
            char leftChar = left.charAt(i - 1);
            for (int j = 1; j <= rightLength; j++) {
                int cost = leftChar == right.charAt(j - 1) ? 0 : 1;
                current[j] = Math.min(
                        Math.min(current[j - 1] + 1, previous[j] + 1),
                        previous[j - 1] + cost
                );
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }

        return previous[rightLength];
    }

    private boolean isQuotaError(String message) {
        if (message == null) {
            return false;
        }
        return message.contains("[17]") || message.contains("[19]") || message.contains("daily request limit") || message.contains("request limit");
    }

    private boolean isImageSizeError(String message) {
        if (message == null) {
            return false;
        }
        return message.contains("[216202]") || message.toLowerCase().contains("image size error");
    }

    private boolean isImageFormatError(String message) {
        if (message == null) {
            return false;
        }
        return message.contains("[216201]") || message.toLowerCase().contains("image format error");
    }

    private boolean isImageSizeOrFormatError(String message) {
        return isImageSizeError(message) || isImageFormatError(message);
    }

    private int resolveImageSizeSplitCount(String base64) {
        BufferedImage image = decodeBase64Image(base64);
        if (image == null) {
            return 1;
        }
        int maxSide = Math.max(image.getWidth(), image.getHeight());
        if (maxSide <= 3400) {
            return 1;
        }
        return Math.max(2, (int) Math.ceil(maxSide / 3000.0));
    }

    private ColorExtractionBundle extractColorsByTiledOcr(String accessToken, String base64) {
        BufferedImage sourceImage = decodeBase64Image(base64);
        if (sourceImage == null) {
            return new ColorExtractionBundle(
                    List.of(),
                    List.of(),
                    List.of("分块兜底失败：图片解码失败，无法切块；" + diagnoseBase64Payload(base64))
            );
        }

        int splitCount = Math.max(2, resolveImageSizeSplitCount(base64));

        int width = sourceImage.getWidth();
        int height = sourceImage.getHeight();
        int overlapX = Math.max(12, width / 220);
        int overlapY = Math.max(12, height / 220);

        Map<String, Integer> mergedLocation = new LinkedHashMap<>();
        Map<String, Integer> mergedText = new LinkedHashMap<>();
        List<String> tileLogs = new ArrayList<>();
        tileLogs.add("分块兜底执行：split=" + splitCount + "x" + splitCount + "，原图尺寸=" + width + "x" + height);

        for (int tileRow = 0; tileRow < splitCount; tileRow++) {
            int yStart = tileRow * height / splitCount;
            int yEnd = (tileRow + 1) * height / splitCount;
            int sy = Math.max(0, yStart - overlapY);
            int ey = Math.min(height, yEnd + overlapY);

            for (int tileCol = 0; tileCol < splitCount; tileCol++) {
                int xStart = tileCol * width / splitCount;
                int xEnd = (tileCol + 1) * width / splitCount;
                int sx = Math.max(0, xStart - overlapX);
                int ex = Math.min(width, xEnd + overlapX);

                int tileWidth = Math.max(1, ex - sx);
                int tileHeight = Math.max(1, ey - sy);
                BufferedImage tileImage = new BufferedImage(tileWidth, tileHeight, BufferedImage.TYPE_INT_RGB);
                Graphics2D graphics = tileImage.createGraphics();
                graphics.drawImage(sourceImage, 0, 0, tileWidth, tileHeight, sx, sy, ex, ey, null);
                graphics.dispose();

                String tileBase64 = encodeImageToBase64Png(tileImage);
                if (tileBase64 == null || tileBase64.isBlank()) {
                    continue;
                }

                String tileName = "tile(" + (tileRow + 1) + "/" + splitCount + "," + (tileCol + 1) + "/" + splitCount + ")";
                tileLogs.add("识别 " + tileName + "，尺寸=" + tileWidth + "x" + tileHeight);

                try {
                    List<OcrWordBox> words = fetchBaiduOcrWordsWithLocation(accessToken, tileBase64);
                    List<ColorRequirement> byLocation = parseColorRequirementsByLocation(words);
                    mergeColorRequirements(mergedLocation, byLocation);
                } catch (RuntimeException tileException) {
                    if (isQuotaError(tileException.getMessage())) {
                        throw tileException;
                    }
                    tileLogs.add(tileName + " 坐标识别失败：" + tileException.getMessage());
                }

                try {
                    String text = fetchBaiduOcrRawText(accessToken, tileBase64);
                    List<ColorRequirement> byText = parseColorRequirements(text);
                    mergeColorRequirements(mergedText, byText);
                } catch (RuntimeException tileException) {
                    if (isQuotaError(tileException.getMessage())) {
                        throw tileException;
                    }
                    tileLogs.add(tileName + " 文本识别失败：" + tileException.getMessage());
                }
            }
        }

        List<ColorRequirement> byLocation = toSortedColorRequirements(mergedLocation);
        List<ColorRequirement> byText = toSortedColorRequirements(mergedText);
        return new ColorExtractionBundle(byLocation, byText, tileLogs);
    }

    private String diagnoseBase64Payload(String base64) {
        String payload = sanitizeBase64Payload(base64);
        if (payload == null || payload.isBlank()) {
            return "base64为空";
        }

        String head = payload.substring(0, Math.min(24, payload.length()));
        byte[] decoded = null;
        String decoder = "none";
        String decodeError = "";
        try {
            decoded = Base64.getDecoder().decode(payload);
            decoder = "std";
        } catch (Exception ex) {
            decodeError = ex.getClass().getSimpleName() + ":" + ex.getMessage();
            try {
                decoded = Base64.getMimeDecoder().decode(payload);
                decoder = "mime";
            } catch (Exception ex2) {
                decodeError = decodeError + " | mime=" + ex2.getClass().getSimpleName() + ":" + ex2.getMessage();
            }
        }

        if (decoded == null || decoded.length == 0) {
            return "len=" + payload.length() + ", head=" + head + ", decode失败=" + decodeError;
        }

        String formatHint = detectImageFormat(decoded);
        BufferedImage image;
        try {
            image = ImageIO.read(new ByteArrayInputStream(decoded));
        } catch (Exception ex) {
            return "len=" + payload.length() + ", head=" + head + ", decoder=" + decoder + ", formatHint=" + formatHint + ", imageio异常=" + ex.getMessage();
        }

        if (image == null) {
            return "len=" + payload.length() + ", head=" + head + ", decoder=" + decoder + ", formatHint=" + formatHint + ", imageio=unreadable";
        }

        return "len=" + payload.length()
                + ", head=" + head
                + ", decoder=" + decoder
                + ", formatHint=" + formatHint
                + ", size=" + image.getWidth() + "x" + image.getHeight();
    }

    private String detectImageFormat(byte[] decoded) {
        if (decoded == null || decoded.length < 12) {
            return "unknown";
        }

        int b0 = decoded[0] & 0xFF;
        int b1 = decoded[1] & 0xFF;
        int b2 = decoded[2] & 0xFF;
        int b3 = decoded[3] & 0xFF;
        if (b0 == 0x89 && b1 == 0x50 && b2 == 0x4E && b3 == 0x47) {
            return "png";
        }
        if (b0 == 0xFF && b1 == 0xD8) {
            return "jpeg";
        }
        if (b0 == 0x47 && b1 == 0x49 && b2 == 0x46) {
            return "gif";
        }
        if (b0 == 0x42 && b1 == 0x4D) {
            return "bmp";
        }

        String riff = new String(decoded, 0, 4, StandardCharsets.US_ASCII);
        String webp = new String(decoded, 8, 4, StandardCharsets.US_ASCII);
        if ("RIFF".equals(riff) && "WEBP".equals(webp)) {
            return "webp";
        }
        return "unknown";
    }

    private void mergeColorRequirements(Map<String, Integer> target, List<ColorRequirement> source) {
        if (target == null || source == null || source.isEmpty()) {
            return;
        }
        for (ColorRequirement item : source) {
            if (item == null) {
                continue;
            }
            String code = normalizeColorCode(item.getCode());
            int quantity = item.getQuantity() == null ? 0 : Math.max(0, item.getQuantity());
            if (code.isBlank() || quantity <= 0) {
                continue;
            }
            target.merge(code, quantity, Integer::sum);
        }
    }

    private List<ColorRequirement> toSortedColorRequirements(Map<String, Integer> merged) {
        if (merged == null || merged.isEmpty()) {
            return List.of();
        }
        return merged.entrySet().stream()
                .filter(entry -> entry.getKey() != null && !entry.getKey().isBlank() && entry.getValue() != null && entry.getValue() > 0)
                .sorted((left, right) -> compareColorCodes(left.getKey(), right.getKey()))
                .map(entry -> new ColorRequirement(entry.getKey(), entry.getValue()))
                .toList();
    }

    private List<ColorRequirement> parseColorRequirements(String rawText) {
        return parseColorRequirements(rawText, null);
    }

    private void resetOcrServiceUsage() {
        ocrServicesUsed.set(new LinkedHashSet<>());
    }

    private void markOcrServiceUsed(String endpoint) {
        if (endpoint == null || endpoint.isBlank()) {
            return;
        }
        LinkedHashSet<String> used = ocrServicesUsed.get();
        used.add(mapEndpointToDisplayName(endpoint));
    }

    private String buildOcrServiceSummary() {
        LinkedHashSet<String> used = ocrServicesUsed.get();
        if (used == null || used.isEmpty()) {
            return "未调用OCR服务";
        }
        return String.join(" → ", used);
    }

    private GridAnalysisResult withOcrServiceSummary(GridAnalysisResult result) {
        if (result == null) {
            return null;
        }
        result.setOcrServiceSummary(buildOcrServiceSummary());
        return result;
    }

    private String mapEndpointToDisplayName(String endpoint) {
        return switch (endpoint) {
            case "accurate" -> "高精度含位置版";
            case "general" -> "标准含位置版";
            case "accurate_basic" -> "高精度版";
            case "general_basic" -> "标准版";
            case "webimage" -> "网络图片版";
            default -> endpoint;
        };
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

        applyBlockPairing(lines, consumedLineIndexes, codeQuantity, codePattern, debugLogs, "文本块配对");

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
                String token = normalizeRequirementCodeToken(tokens[i]);
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

        List<String> textLines = lines.stream().map(OcrLine::text).toList();
        applyBlockPairing(textLines, consumed, codeQuantity, codePattern, pairLogs, "坐标块配对");

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
                String token = normalizeRequirementCodeToken(tokens[i]);
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

    private void applyBlockPairing(
            List<String> lines,
            Set<Integer> consumed,
            Map<String, Integer> codeQuantity,
            Pattern codePattern,
            List<String> logs,
            String logPrefix
    ) {
        if (lines == null || lines.isEmpty()) {
            return;
        }

        int index = 0;
        while (index < lines.size()) {
            if (consumed.contains(index)) {
                index++;
                continue;
            }

            String line = lines.get(index);
            List<String> startCodes = extractCodes(line, codePattern);
            List<Integer> startQuantities = extractQuantities(line);
            boolean startIsCodeOnly = !startCodes.isEmpty() && startQuantities.isEmpty();
            if (!startIsCodeOnly) {
                index++;
                continue;
            }

            int codeStart = index;
            int codeEnd = index;
            List<String> allCodes = new ArrayList<>();
            while (codeEnd < lines.size() && !consumed.contains(codeEnd)) {
                String codeLine = lines.get(codeEnd);
                List<String> codes = extractCodes(codeLine, codePattern);
                List<Integer> quantities = extractQuantities(codeLine);
                boolean codeOnly = !codes.isEmpty() && quantities.isEmpty();
                if (!codeOnly) {
                    break;
                }
                allCodes.addAll(codes);
                codeEnd++;
            }
            codeEnd--;

            int quantityStart = codeEnd + 1;
            if (quantityStart >= lines.size() || consumed.contains(quantityStart)) {
                index = codeEnd + 1;
                continue;
            }

            List<String> quantityStartCodes = extractCodes(lines.get(quantityStart), codePattern);
            List<Integer> quantityStartValues = extractQuantities(lines.get(quantityStart));
            boolean quantityStartIsQuantityOnly = quantityStartCodes.isEmpty() && !quantityStartValues.isEmpty();
            if (!quantityStartIsQuantityOnly) {
                index = codeEnd + 1;
                continue;
            }

            int quantityEnd = quantityStart;
            List<Integer> allQuantities = new ArrayList<>();
            while (quantityEnd < lines.size() && !consumed.contains(quantityEnd)) {
                String quantityLine = lines.get(quantityEnd);
                List<String> quantityCodes = extractCodes(quantityLine, codePattern);
                List<Integer> quantities = extractQuantities(quantityLine);
                boolean quantityOnly = quantityCodes.isEmpty() && !quantities.isEmpty();
                if (!quantityOnly) {
                    break;
                }
                allQuantities.addAll(quantities);
                quantityEnd++;
            }
            quantityEnd--;

            int totalCodes = allCodes.size();
            int totalQuantities = allQuantities.size();
            int diff = Math.abs(totalCodes - totalQuantities);
            int pairCount = Math.min(totalCodes, totalQuantities);
            boolean looksLikeBlockPair = totalCodes >= 3
                    && totalQuantities >= 3
                    && pairCount >= 3
                    && diff <= Math.max(2, totalCodes / 2);

            if (!looksLikeBlockPair) {
                index = codeEnd + 1;
                continue;
            }

            for (int pairIndex = 0; pairIndex < pairCount; pairIndex++) {
                String code = allCodes.get(pairIndex);
                int quantity = allQuantities.get(pairIndex);
                if (quantity <= 0) {
                    continue;
                }
                codeQuantity.merge(code, quantity, Integer::sum);
            }

            for (int i = codeStart; i <= quantityEnd; i++) {
                consumed.add(i);
            }

            if (logs != null) {
                logs.add(logPrefix
                        + "：line " + (codeStart + 1) + "-" + (codeEnd + 1)
                        + " codes=" + totalCodes
                        + "；line " + (quantityStart + 1) + "-" + (quantityEnd + 1)
                        + " qty=" + totalQuantities
                        + "；配对=" + pairCount);
            }

            index = quantityEnd + 1;
        }
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
            String normalized = normalizeRequirementCodeToken(token);
            if (normalized.isBlank()) {
                continue;
            }
            if (codePattern.matcher(normalized).matches()) {
                result.add(normalized);
            }
        }
        return result;
    }

    private String normalizeRequirementCodeToken(String token) {
        if (token == null) {
            return "";
        }
        String upper = token.toUpperCase().replaceAll("[^A-Z0-9]", "").trim();
        if (upper.isEmpty()) {
            return "";
        }
        if (!upper.matches(".*[A-Z].*")) {
            return "";
        }
        return normalizeOcrToken(upper);
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
        String upper = token.toUpperCase().replaceAll("[^A-Z0-9]", "").trim();
        if (upper.isEmpty()) {
            return "";
        }

        int split = inferLetterDigitSplit(upper);
        StringBuilder normalizedBuilder = new StringBuilder(upper.length());
        for (int i = 0; i < upper.length(); i++) {
            char current = upper.charAt(i);
            if (i < split) {
                normalizedBuilder.append(normalizeLetterChar(current));
            } else {
                normalizedBuilder.append(normalizeDigitChar(current));
            }
        }

        String normalized = normalizedBuilder.toString();
        return normalizeColorCode(normalized);
    }

    private int inferLetterDigitSplit(String token) {
        if (token == null || token.isBlank()) {
            return 1;
        }
        int maxPrefix = Math.min(2, Math.max(1, token.length() - 1));
        int bestSplit = 1;
        double bestScore = Double.NEGATIVE_INFINITY;

        for (int split = 1; split <= maxPrefix; split++) {
            double score = 0.0;
            for (int i = 0; i < token.length(); i++) {
                char current = token.charAt(i);
                if (i < split) {
                    score += isLikelyLetter(current) ? 1.0 : -0.8;
                } else {
                    score += isLikelyDigit(current) ? 1.0 : -0.8;
                }
            }
            if (score > bestScore) {
                bestScore = score;
                bestSplit = split;
            }
        }

        return bestSplit;
    }

    private char normalizeLetterChar(char ch) {
        if (ch >= 'A' && ch <= 'Z') {
            return ch;
        }
        return switch (ch) {
            case '0' -> 'O';
            case '1' -> 'I';
            case '2' -> 'Z';
            case '5' -> 'S';
            case '6' -> 'G';
            case '8' -> 'B';
            default -> ch;
        };
    }

    private char normalizeDigitChar(char ch) {
        if (ch >= '0' && ch <= '9') {
            return ch;
        }
        return switch (ch) {
            case 'O', 'Q', 'D' -> '0';
            case 'I', 'L', 'T' -> '1';
            case 'Z' -> '2';
            case 'S' -> '5';
            case 'G' -> '6';
            case 'B' -> '8';
            default -> ch;
        };
    }

    private boolean isLikelyLetter(char ch) {
        return (ch >= 'A' && ch <= 'Z') || ch == '0' || ch == '1' || ch == '2' || ch == '5' || ch == '6' || ch == '8';
    }

    private boolean isLikelyDigit(char ch) {
        return (ch >= '0' && ch <= '9') || ch == 'O' || ch == 'Q' || ch == 'D' || ch == 'I' || ch == 'L' || ch == 'T' || ch == 'Z' || ch == 'S' || ch == 'G' || ch == 'B';
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
    private record TokenHit(String code, double centerX, double centerY) {}
    private record CodeHit(int count, double minDistance) {}
    private record OcrLine(String text) {}
    private record KeyPair(String ak, String sk) {}
    private record GridGeometry(int width, int height, int[] xBoundaries, int[] yBoundaries) {}
    private record PreparedGridImage(String base64, GridGeometry geometry, boolean preprocessed) {}
    private record PointD(double x, double y) {}
    private record Line(double a, double b, double c) {}
    private record EdgeQuadrilateral(PointD topLeft, PointD topRight, PointD bottomRight, PointD bottomLeft) {}
    private record PerspectiveCorrectionResult(BufferedImage correctedImage, boolean corrected) {}
    private record RgbColor(int red, int green, int blue) {}
    private record LabColor(double l, double a, double b) {}
    private record CellColor(int red, int green, int blue, LabColor lab) {}
    private record MissingCellScore(int row, int col, String code, double distance, double confidenceGap) {}
    private record ColorExtractionBundle(List<ColorRequirement> byLocation, List<ColorRequirement> byText, List<String> tileLogs) {}
}
