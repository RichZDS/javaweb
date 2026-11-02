package com.richzds.quiz.mapper;

import java.util.List;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Select;

import com.richzds.quiz.entity.ExamRecord;

public interface ExamRecordMapper extends BaseMapper<ExamRecord> {

    /** 每份试卷的开考次数（含进行中），用来判断“有人考过”。 */
    @Select("SELECT paper_id AS group_id, COUNT(*) AS total_count FROM exam_record GROUP BY paper_id")
    List<AggRow> countByPaper();

    /** 每份试卷已结算的场次、总分之和、及格场次（看板用）。 */
    @Select("""
            SELECT paper_id AS group_id, COUNT(*) AS total_count, SUM(score) AS score_sum,
                   SUM(CASE WHEN passed = TRUE THEN 1 ELSE 0 END) AS passed_count
            FROM exam_record WHERE status = 'FINISHED'
            GROUP BY paper_id
            """)
    List<AggRow> finishedStatsByPaper();
}
