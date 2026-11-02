package com.richzds.quiz.mapper;

import java.util.Collection;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.richzds.quiz.entity.Favorite;

public interface FavoriteMapper extends BaseMapper<Favorite> {

    /** 本人收藏，可按章节过滤，最近收藏的在前。 */
    @Select("""
            <script>
            SELECT f.* FROM favorite f JOIN question q ON q.id = f.question_id
            WHERE f.user_id = #{userId}
            <if test="chapterIds != null">
              AND q.category_id IN
              <foreach collection="chapterIds" item="c" open="(" separator="," close=")">#{c}</foreach>
            </if>
            ORDER BY f.created_at DESC, f.id DESC
            </script>
            """)
    IPage<Favorite> selectUserPage(IPage<Favorite> page, @Param("userId") long userId,
                                   @Param("chapterIds") Collection<Long> chapterIds);
}
