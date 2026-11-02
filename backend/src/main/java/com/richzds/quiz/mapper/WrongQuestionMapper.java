package com.richzds.quiz.mapper;

import java.util.Collection;
import java.util.List;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.richzds.quiz.entity.WrongQuestion;

public interface WrongQuestionMapper extends BaseMapper<WrongQuestion> {

    /** 本人错题本（未移出），可按章节过滤；sort = RECENT 按最近做错，MOST 按错误次数。 */
    @Select("""
            <script>
            SELECT w.* FROM wrong_question w JOIN question q ON q.id = w.question_id
            WHERE w.user_id = #{userId} AND w.removed = FALSE
            <if test="chapterIds != null">
              AND q.category_id IN
              <foreach collection="chapterIds" item="c" open="(" separator="," close=")">#{c}</foreach>
            </if>
            <choose>
              <when test="sort == 'MOST'">ORDER BY w.wrong_count DESC, w.last_wrong_at DESC, w.id DESC</when>
              <otherwise>ORDER BY w.last_wrong_at DESC, w.id DESC</otherwise>
            </choose>
            </script>
            """)
    IPage<WrongQuestion> selectActivePage(IPage<WrongQuestion> page, @Param("userId") long userId,
                                          @Param("chapterIds") Collection<Long> chapterIds,
                                          @Param("sort") String sort);

    /** 本人错题本按章节（题目当前所属）计数。 */
    @Select("""
            SELECT q.category_id AS group_id, COUNT(*) AS total_count
            FROM wrong_question w JOIN question q ON q.id = w.question_id
            WHERE w.user_id = #{userId} AND w.removed = FALSE
            GROUP BY q.category_id
            """)
    List<AggRow> countActiveByChapter(@Param("userId") long userId);
}
