package org.example.hotelreservation.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("blog_note")
public class BlogNote {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long authorId;
    private Long hotelId;
    private String title;
    private String content;
    private String coverUrl;
    /** 作者自己的探店评分 1-5 */
    private Integer authorScore;
    private Integer likeCount;
    private Integer commentCount;
    private Integer ratingSum;
    private Integer ratingCount;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
    @TableLogic
    private Integer deleted;
}
