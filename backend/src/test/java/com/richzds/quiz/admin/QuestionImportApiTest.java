package com.richzds.quiz.admin;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

import com.richzds.quiz.entity.Category;
import com.richzds.quiz.support.ApiTestBase;

/** Excel 模板下载和题目导入：正确行导入，错误行逐行报原因。 */
class QuestionImportApiTest extends ApiTestBase {

    private static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    /** 生成一个 xlsx：第一行表头，之后每个 List 一行（null 表示整行留空）。 */
    private static byte[] workbook(List<String> header, List<List<String>> rows) throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("题目");
            Row h = sheet.createRow(0);
            for (int i = 0; i < header.size(); i++) {
                h.createCell(i).setCellValue(header.get(i));
            }
            for (int r = 0; r < rows.size(); r++) {
                Row row = sheet.createRow(r + 1);
                if (rows.get(r) == null) {
                    continue;
                }
                for (int i = 0; i < rows.get(r).size(); i++) {
                    String v = rows.get(r).get(i);
                    if (v.matches("\\d+") && i == 11) {
                        row.createCell(i).setCellValue(Double.parseDouble(v)); // 难度写成数字单元格
                    } else {
                        row.createCell(i).setCellValue(v);
                    }
                }
            }
            wb.write(out);
            return out.toByteArray();
        }
    }

    /** 一行题目：分类路径, 题型, 题干, 选项 A–F, 答案, 解析, 难度, 标签, 来源。 */
    private static List<String> row(String path, String type, String stem, List<String> options, String answer,
                                    String analysis, String difficulty) {
        List<String> r = new ArrayList<>(List.of(path, type, stem));
        for (int i = 0; i < 6; i++) {
            r.add(i < options.size() ? options.get(i) : "");
        }
        r.addAll(List.of(answer, analysis, difficulty, "标签1，标签2", "原创"));
        return r;
    }

    @Test
    void templateHasHeaderAndValidExamples() throws Exception {
        Login admin = admin();
        tree("408");
        MockHttpServletResponse res = mvc.perform(get("/api/admin/questions/import/template", admin))
                .andReturn().getResponse();
        assertThat(res.getStatus()).isEqualTo(200);
        assertThat(res.getContentType()).isEqualTo(XLSX);
        assertThat(res.getHeader("Content-Disposition")).contains("attachment").contains("filename*=UTF-8''");
        byte[] bytes = res.getContentAsByteArray();
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Row header = wb.getSheetAt(0).getRow(0);
            List<String> got = new ArrayList<>();
            header.forEach(c -> got.add(c.getStringCellValue()));
            assertThat(got).containsExactlyElementsOf(QuestionImportService.HEADERS);
            assertThat(wb.getSheetAt(0).getRow(1).getCell(0).getStringCellValue())
                    .as("示例行用库里实际存在的章节").isEqualTo("408/408-科目/408-章节");
            assertThat(wb.getSheetName(1)).isEqualTo("填写说明");
        }

        // 模板原样导入：四道示例题都能导入
        JsonNode r = data(call(upload("/api/admin/questions/import", admin, "模板.xlsx", XLSX, bytes)));
        assertThat(r.get("total").asInt()).isEqualTo(4);
        assertThat(r.get("success").asInt()).isEqualTo(4);
        assertThat(r.get("failed")).isEmpty();
        assertThat(call(get("/api/admin/questions/import/template", register("imp_student"))).status()).isEqualTo(403);
    }

    @Test
    void importsGoodRowsAndReportsBadRowsOneByOne() throws Exception {
        Login admin = admin();
        Category[] t = tree("408");
        String path = " 408 / 408-科目 / 408-章节 ";
        List<List<String>> rows = new ArrayList<>();
        rows.add(row(path, "单选", "单选题干", List.of("甲", "乙", "丙"), "b", "解析", "2"));         // 行 2 ✓
        rows.add(row(path, "多选", "多选题干", List.of("甲", "乙", "丙", "丁"), "A,C", "解析", ""));   // 行 3 ✓
        rows.add(row(path, "判断", "判断题干", List.of(), "对", "解析", "1"));                         // 行 4 ✓
        rows.add(row(path, "填空", "填空 ____ ____", List.of(), "int|Integer||5", "解析", "3"));      // 行 5 ✓
        rows.add(null);                                                                               // 行 6 空行跳过
        rows.add(row("408/不存在/章节", "单选", "x", List.of("a", "b"), "A", "解析", "3"));            // 行 7
        rows.add(row(path, "问答", "x", List.of(), "A", "解析", "3"));                                // 行 8
        rows.add(row(path, "单选", "x", List.of("a", "b"), "AB", "解析", "3"));                       // 行 9
        rows.add(row(path, "多选", "x", List.of("a", "b", "c"), "A", "解析", "3"));                   // 行 10
        rows.add(row(path, "单选", "x", List.of("a", "", "c"), "A", "解析", "3"));                    // 行 11
        rows.add(row(path, "判断", "x", List.of(), "也许", "解析", "3"));                             // 行 12
        rows.add(row(path, "填空", "x", List.of(), "int||", "解析", "3"));                            // 行 13
        rows.add(row(path, "单选", "x", List.of("a", "b"), "A", "", "3"));                            // 行 14
        rows.add(row(path, "单选", "x", List.of("a", "b"), "A", "解析", "9"));                        // 行 15
        rows.add(row(path, "判断", "x", List.of("a"), "对", "解析", "3"));                            // 行 16
        rows.add(row(path, "单选", "", List.of("a", "b"), "A", "解析", "3"));                         // 行 17

        JsonNode r = data(call(upload("/api/admin/questions/import", admin, "q.xlsx", XLSX,
                workbook(QuestionImportService.HEADERS, rows))));
        assertThat(r.get("total").asInt()).isEqualTo(15);
        assertThat(r.get("success").asInt()).isEqualTo(4);
        JsonNode failed = r.get("failed");
        assertThat(ids(failed, "row")).containsExactly(7L, 8L, 9L, 10L, 11L, 12L, 13L, 14L, 15L, 16L, 17L);
        Map<Integer, String> reasons = new HashMap<>();
        failed.forEach(f -> reasons.put(f.get("row").asInt(), f.get("reason").asText()));
        assertThat(reasons.get(7)).contains("分类路径不存在");
        assertThat(reasons.get(8)).contains("题型");
        assertThat(reasons.get(9)).contains("不是已有的选项");
        assertThat(reasons.get(10)).contains("至少要有 2 个正确答案");
        assertThat(reasons.get(11)).contains("选项B为空");
        assertThat(reasons.get(12)).contains("对").contains("错");
        assertThat(reasons.get(13)).contains("第 2 空没有答案");
        assertThat(reasons.get(14)).contains("解析不能为空");
        assertThat(reasons.get(15)).contains("难度");
        assertThat(reasons.get(16)).contains("不要填选项");
        assertThat(reasons.get(17)).contains("题干不能为空");

        List<Map<String, Object>> saved = jdbc.queryForList(
                "SELECT type, answer_json, status, difficulty, tags, source_note, sort FROM question ORDER BY id");
        assertThat(saved).hasSize(4);
        assertThat(saved).allMatch(m -> "PUBLISHED".equals(m.get("status")));
        assertThat(saved.get(0).get("answer_json")).isEqualTo("[\"B\"]");
        assertThat(((Number) saved.get(0).get("difficulty")).intValue()).isEqualTo(2);
        assertThat(saved.get(0).get("tags")).isEqualTo("标签1,标签2");
        assertThat(saved.get(1).get("answer_json")).isEqualTo("[\"A\",\"C\"]");
        assertThat(((Number) saved.get(1).get("difficulty")).intValue()).as("不填难度为 3").isEqualTo(3);
        assertThat(saved.get(2).get("answer_json")).isEqualTo("[\"T\"]");
        assertThat(saved.get(3).get("answer_json")).isEqualTo("[[\"int\",\"Integer\"],[\"5\"]]");
        assertThat(saved.stream().map(m -> ((Number) m.get("sort")).intValue()).toList()).containsExactly(1, 2, 3, 4);
        assertThat(t).hasSize(3);
    }

    @Test
    void rejectsWrongFiles() throws Exception {
        Login admin = admin();
        List<String> wrongHeader = new ArrayList<>(QuestionImportService.HEADERS);
        wrongHeader.set(2, "题目");
        Resp header = call(upload("/api/admin/questions/import", admin, "q.xlsx", XLSX,
                workbook(wrongHeader, List.of())));
        assertThat(header.status()).isEqualTo(400);
        assertThat(header.data().get("fields").get(0).get("field").asText()).isEqualTo("file");
        assertThat(header.body().get("message").asText()).contains("表头");

        assertThat(call(upload("/api/admin/questions/import", admin, "q.xlsx", XLSX, "not excel".getBytes())).status())
                .isEqualTo(400);
        assertThat(call(upload("/api/admin/questions/import", admin, "q.csv", "text/csv", "a,b".getBytes())).status())
                .isEqualTo(400);
        JsonNode empty = data(call(upload("/api/admin/questions/import", admin, "q.xlsx", XLSX,
                workbook(QuestionImportService.HEADERS, List.of()))));
        assertThat(empty.get("total").asInt()).isZero();
    }
}
