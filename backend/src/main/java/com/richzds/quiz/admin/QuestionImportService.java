package com.richzds.quiz.admin;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.richzds.quiz.common.BizException;
import com.richzds.quiz.common.JsonUtil;
import com.richzds.quiz.common.TimeUtil;
import com.richzds.quiz.domain.FillOptions;
import com.richzds.quiz.domain.OptionItem;
import com.richzds.quiz.domain.QuestionRules;
import com.richzds.quiz.domain.QuestionType;
import com.richzds.quiz.dto.AdminDtos.ImportFailure;
import com.richzds.quiz.dto.AdminDtos.ImportResult;
import com.richzds.quiz.entity.Category;
import com.richzds.quiz.entity.Question;
import com.richzds.quiz.mapper.QuestionMapper;
import com.richzds.quiz.service.CategoryService;
import com.richzds.quiz.service.CategoryTree;

/**
 * Excel 题目导入和模板（docs/api.md 第 10 节）。整表先逐行校验，再把正确的行一次性导入（状态为已发布），
 * 错误的行逐行返回原因；行号就是 Excel 里看到的行号（表头是第 1 行）。
 */
@Service
public class QuestionImportService {

    public static final List<String> HEADERS = List.of("分类路径", "题型", "题干", "选项A", "选项B", "选项C",
            "选项D", "选项E", "选项F", "答案", "解析", "难度", "标签", "来源");
    public static final long MAX_BYTES = 5L * 1024 * 1024;
    public static final int MAX_ROWS = 2000;

    private static final int COL_PATH = 0;
    private static final int COL_TYPE = 1;
    private static final int COL_STEM = 2;
    private static final int COL_OPTION_A = 3;
    private static final int COL_ANSWER = 9;
    private static final int COL_ANALYSIS = 10;
    private static final int COL_DIFFICULTY = 11;
    private static final int COL_TAGS = 12;
    private static final int COL_SOURCE = 13;
    private static final Map<String, QuestionType> TYPES = Map.of(
            "单选", QuestionType.SINGLE, "多选", QuestionType.MULTIPLE,
            "判断", QuestionType.JUDGE, "填空", QuestionType.FILL);

    private final QuestionMapper questionMapper;
    private final CategoryService categoryService;
    private final Clock clock;

    public QuestionImportService(QuestionMapper questionMapper, CategoryService categoryService, Clock clock) {
        this.questionMapper = questionMapper;
        this.categoryService = categoryService;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ 导入

    @Transactional
    public ImportResult importFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw BizException.invalid("file", "请选择 xlsx 文件");
        }
        if (file.getSize() > MAX_BYTES) {
            throw BizException.invalid("file", "文件不能超过 5 MB");
        }
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        if (!name.endsWith(".xlsx")) {
            throw BizException.invalid("file", "只支持 .xlsx 文件，请用下载的模板填写");
        }
        Workbook wb;
        try (InputStream in = file.getInputStream()) {
            wb = new XSSFWorkbook(in);
        } catch (IOException | RuntimeException e) {
            // 只有“读不成工作簿”算文件无效；后面导入时的数据库错误照常抛出
            throw BizException.invalid("file", "不是有效的 xlsx 文件");
        }
        try (wb) {
            return importWorkbook(wb);
        } catch (IOException e) {
            throw new IllegalStateException("关闭工作簿失败", e);
        }
    }

    private ImportResult importWorkbook(Workbook wb) {
        Sheet sheet = wb.getSheetAt(0);
        DataFormatter fmt = new DataFormatter(Locale.ROOT);
        FormulaEvaluator eval = wb.getCreationHelper().createFormulaEvaluator();
        Row header = sheet.getRow(0);
        for (int i = 0; i < HEADERS.size(); i++) {
            String got = header == null ? "" : text(header.getCell(i), fmt, eval);
            if (!HEADERS.get(i).equals(got)) {
                throw BizException.invalid("file", "第 1 行表头不对：第 " + (i + 1) + " 列应为“" + HEADERS.get(i)
                        + "”，请用下载的模板填写");
            }
        }
        if (sheet.getLastRowNum() > MAX_ROWS) {
            throw BizException.invalid("file", "一次最多导入 " + MAX_ROWS + " 行");
        }
        CategoryTree tree = categoryService.tree();
        Map<String, List<Category>> chaptersByPath = chaptersByPath(tree);
        List<ImportFailure> failed = new ArrayList<>();
        List<Question> ok = new ArrayList<>();
        int total = 0;
        for (int r = 1; r <= sheet.getLastRowNum(); r++) {
            String[] cells = new String[HEADERS.size()];
            Row row = sheet.getRow(r);
            boolean blank = true;
            for (int c = 0; c < cells.length; c++) {
                cells[c] = row == null ? "" : text(row.getCell(c), fmt, eval);
                blank &= cells[c].isEmpty();
            }
            if (blank) {
                continue;
            }
            total++;
            try {
                ok.add(parse(cells, chaptersByPath));
            } catch (RowError e) {
                failed.add(new ImportFailure(r + 1, e.getMessage()));
            }
        }
        LocalDateTime now = TimeUtil.nowUtc(clock);
        Map<Long, Integer> nextSort = new HashMap<>();
        for (Question q : ok) {
            int sort = nextSort.computeIfAbsent(q.getCategoryId(), id -> {
                Integer max = questionMapper.maxSort(id);
                return max == null ? 1 : max + 1;
            });
            nextSort.put(q.getCategoryId(), sort + 1);
            questionMapper.insert(q.setSort(sort).setStatus(AdminQuestionService.PUBLISHED).setVersion(1)
                    .setCreatedAt(now).setUpdatedAt(now));
        }
        return new ImportResult(total, ok.size(), failed);
    }

    /** 校验并解析一行；出错时抛 RowError，信息就是返回给前端的原因。 */
    private Question parse(String[] c, Map<String, List<Category>> chaptersByPath) {
        String path = normalizePath(c[COL_PATH]);
        if (path.isEmpty()) {
            throw new RowError("分类路径不能为空");
        }
        List<Category> matches = chaptersByPath.getOrDefault(path, List.of());
        if (matches.isEmpty()) {
            throw new RowError("分类路径不存在：" + c[COL_PATH] + "（要写到三级章节，如 考试/科目/章节）");
        }
        if (matches.size() > 1) {
            throw new RowError("分类路径有重名，无法确定是哪个章节：" + c[COL_PATH]);
        }
        QuestionType type = TYPES.get(c[COL_TYPE]);
        if (type == null) {
            throw new RowError("题型只能是 单选 / 多选 / 判断 / 填空");
        }
        String stem = c[COL_STEM];
        if (stem.isEmpty()) {
            throw new RowError("题干不能为空");
        }
        if (stem.length() > 10000) {
            throw new RowError("题干太长");
        }
        List<OptionItem> options = options(c, type);
        String answer = c[COL_ANSWER];
        if (answer.isEmpty()) {
            throw new RowError("答案不能为空");
        }
        List<String> correct = null;
        List<List<String>> accepted = null;
        switch (type) {
            case SINGLE -> correct = List.of(answer.toUpperCase(Locale.ROOT));
            case MULTIPLE -> correct = Arrays.stream(answer.toUpperCase(Locale.ROOT)
                    .replaceAll("[\\s,，、]", "").split("")).toList();
            case JUDGE -> correct = List.of(judge(answer));
            case FILL -> accepted = blanks(answer);
            default -> throw new RowError("题型不正确");
        }
        String error = QuestionRules.check(type, type.isChoice() ? options : null, correct, accepted);
        if (error != null) {
            throw new RowError(error);
        }
        if (c[COL_ANALYSIS].isEmpty()) {
            throw new RowError("解析不能为空");
        }
        int difficulty = 3;
        if (!c[COL_DIFFICULTY].isEmpty()) {
            try {
                difficulty = Integer.parseInt(c[COL_DIFFICULTY].replaceAll("\\.0+$", ""));
            } catch (NumberFormatException e) {
                throw new RowError("难度只能是 1–5 的整数");
            }
            if (difficulty < 1 || difficulty > 5) {
                throw new RowError("难度只能是 1–5 的整数");
            }
        }
        Set<String> tags = new LinkedHashSet<>();
        for (String t : c[COL_TAGS].split("[,，、]")) {
            if (!t.isBlank()) {
                if (t.strip().length() > 30) {
                    throw new RowError("标签最多 30 字：" + t.strip());
                }
                tags.add(t.strip());
            }
        }
        String tagCsv = tags.isEmpty() ? null : String.join(",", tags);
        if (tagCsv != null && (tags.size() > 10 || tagCsv.length() > 255)) {
            throw new RowError("标签太多");
        }
        if (c[COL_SOURCE].length() > 255) {
            throw new RowError("来源太长");
        }
        boolean fill = type == QuestionType.FILL;
        return new Question()
                .setCategoryId(matches.get(0).getId())
                .setType(type.name())
                .setStem(stem)
                .setOptionsJson(type.isChoice() ? JsonUtil.write(options) : null)
                .setAnswerJson(JsonUtil.write(fill ? accepted : correct))
                .setFillOptions(fill ? JsonUtil.write(FillOptions.DEFAULT) : null)
                .setAnalysis(c[COL_ANALYSIS])
                .setDifficulty(difficulty)
                .setTags(tagCsv)
                .setSourceNote(c[COL_SOURCE].isEmpty() ? null : c[COL_SOURCE]);
    }

    /** 选项 A–F：选择题必须从 A 起连续填写；判断题和填空题不能填。 */
    private static List<OptionItem> options(String[] c, QuestionType type) {
        int last = -1;
        for (int i = 0; i < 6; i++) {
            if (!c[COL_OPTION_A + i].isEmpty()) {
                last = i;
            }
        }
        if (!type.isChoice()) {
            if (last >= 0) {
                throw new RowError("判断题和填空题不要填选项");
            }
            return List.of();
        }
        List<OptionItem> options = new ArrayList<>();
        for (int i = 0; i <= last; i++) {
            String text = c[COL_OPTION_A + i];
            if (text.isEmpty()) {
                throw new RowError("选项" + QuestionRules.keyAt(i) + "为空，但后面还有选项；选项要从 A 起连续填写");
            }
            options.add(new OptionItem(QuestionRules.keyAt(i), text));
        }
        return options;
    }

    private static String judge(String answer) {
        return switch (answer.toUpperCase(Locale.ROOT)) {
            case "对", "正确", "T", "√" -> "T";
            case "错", "错误", "F", "×" -> "F";
            default -> throw new RowError("判断题答案只能是“对”或“错”");
        };
    }

    /** 填空答案：每空用 || 分隔，同一空的多个写法用 | 分隔，例如 int|Integer||5。 */
    private static List<List<String>> blanks(String answer) {
        List<List<String>> out = new ArrayList<>();
        String[] parts = answer.split("\\|\\|", -1);
        for (int i = 0; i < parts.length; i++) {
            List<String> alts = Arrays.stream(parts[i].split("\\|", -1)).map(String::strip)
                    .filter(s -> !s.isEmpty()).toList();
            if (alts.isEmpty()) {
                throw new RowError("第 " + (i + 1) + " 空没有答案");
            }
            out.add(alts);
        }
        return out;
    }

    /** “考试/科目/章节”路径 → 章节；同名路径可能对应多个章节（报重名）。 */
    private static Map<String, List<Category>> chaptersByPath(CategoryTree tree) {
        Map<String, List<Category>> map = new HashMap<>();
        for (Category root : tree.roots()) {
            for (Long id : tree.chapterIdsUnder(root.getId())) {
                Category chapter = tree.get(id);
                Category subject = tree.get(chapter.getParentId());
                String path = normalizePath(root.getName() + "/" + subject.getName() + "/" + chapter.getName());
                map.computeIfAbsent(path, k -> new ArrayList<>()).add(chapter);
            }
        }
        return map;
    }

    private static String normalizePath(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        return String.join("/", Arrays.stream(raw.split("/")).map(String::strip).toList());
    }

    private static String text(Cell cell, DataFormatter fmt, FormulaEvaluator eval) {
        if (cell == null) {
            return "";
        }
        return fmt.formatCellValue(cell, eval).strip();
    }

    /** 某一行不合格。 */
    private static final class RowError extends RuntimeException {
        RowError(String message) {
            super(message, null, false, false);
        }
    }

    // ------------------------------------------------------------------ 模板

    /** 生成导入模板：第一张表是表头加四种题型各一行示例，第二张表是填写说明。 */
    public byte[] template() {
        CategoryTree tree = categoryService.tree();
        String examplePath = "考试/科目/章节";
        outer:
        for (Category root : tree.roots()) {
            for (Long id : tree.chapterIdsUnder(root.getId())) {
                Category chapter = tree.get(id);
                examplePath = root.getName() + "/" + tree.get(chapter.getParentId()).getName() + "/" + chapter.getName();
                break outer;
            }
        }
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("题目");
            CellStyle bold = wb.createCellStyle();
            Font font = wb.createFont();
            font.setBold(true);
            bold.setFont(font);
            Row head = sheet.createRow(0);
            for (int i = 0; i < HEADERS.size(); i++) {
                Cell cell = head.createCell(i);
                cell.setCellValue(HEADERS.get(i));
                cell.setCellStyle(bold);
                sheet.setColumnWidth(i, (i == COL_STEM || i == COL_ANALYSIS ? 40 : i == COL_PATH ? 30 : 12) * 256);
            }
            List<List<String>> examples = List.of(
                    List.of(examplePath, "单选", "顺序表按下标访问第 i 个元素的时间复杂度是？", "O(1)", "O(n)", "O(log n)",
                            "O(n²)", "", "", "A", "顺序表连续存储，按下标能直接算出地址，所以是 O(1)。", "1", "顺序表", "原创"),
                    List.of(examplePath, "多选", "下列哪些属于线性结构？", "栈", "队列", "二叉树", "线性表", "", "",
                            "ABD", "栈、队列、线性表的元素一对一排列，是线性结构；二叉树是一对多，属于非线性结构。", "2",
                            "线性结构,栈", "原创"),
                    List.of(examplePath, "判断", "栈的特点是先进先出。", "", "", "", "", "", "", "错",
                            "栈是后进先出（LIFO），先进先出的是队列。", "1", "栈", "原创"),
                    List.of(examplePath, "填空", "Java 里声明整型变量的关键字是 ____，表达式 5 / 2 的值是 ____。", "", "",
                            "", "", "", "", "int||2", "int 是整型关键字；两个整数相除结果仍是整数，5 / 2 = 2。", "2",
                            "基本类型", "原创"));
            for (int r = 0; r < examples.size(); r++) {
                Row row = sheet.createRow(r + 1);
                for (int i = 0; i < examples.get(r).size(); i++) {
                    row.createCell(i).setCellValue(examples.get(r).get(i));
                }
            }
            Sheet help = wb.createSheet("填写说明");
            help.setColumnWidth(0, 100 * 256);
            List<String> lines = List.of(
                    "只读第一张表；第 1 行表头不要改。整行留空的行会跳过。",
                    "分类路径：考试/科目/章节，三级必须已经在后台建好，用 / 分隔。",
                    "题型：单选 / 多选 / 判断 / 填空。",
                    "选项A–F：单选、多选从 A 起连续填写 2–6 项；判断题和填空题不填。",
                    "答案：单选写一个字母如 A；多选写多个字母如 AC；判断写“对”或“错”；"
                            + "填空每空用 || 分隔，同一空多个可接受写法用 | 分隔，如 int|Integer||5。",
                    "解析：必填。难度：1–5 的整数，不填为 3。标签：用逗号分隔。来源：选填。",
                    "导入的题直接是已发布状态；有错的行不导入，并逐行列出原因，改好后可以只重新导入这些行。");
            for (int i = 0; i < lines.size(); i++) {
                help.createRow(i).createCell(0).setCellValue(lines.get(i));
            }
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("生成模板失败", e);
        }
    }
}
