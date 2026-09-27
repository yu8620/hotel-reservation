package org.example.hotelreservation.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.example.hotelreservation.common.BizException;
import org.example.hotelreservation.common.ResultCode;
import org.example.hotelreservation.dto.CommentResponse;
import org.example.hotelreservation.dto.CreateCommentRequest;
import org.example.hotelreservation.dto.CreateNoteRequest;
import org.example.hotelreservation.dto.NoteCardResponse;
import org.example.hotelreservation.dto.PageResponse;
import org.example.hotelreservation.dto.RateNoteRequest;
import org.example.hotelreservation.entity.BlogNote;
import org.example.hotelreservation.entity.Hotel;
import org.example.hotelreservation.entity.NoteComment;
import org.example.hotelreservation.entity.NoteLike;
import org.example.hotelreservation.entity.NoteRating;
import org.example.hotelreservation.entity.User;
import org.example.hotelreservation.mapper.BlogNoteMapper;
import org.example.hotelreservation.mapper.HotelMapper;
import org.example.hotelreservation.mapper.NoteCommentMapper;
import org.example.hotelreservation.mapper.NoteLikeMapper;
import org.example.hotelreservation.mapper.NoteRatingMapper;
import org.example.hotelreservation.mapper.UserMapper;
import org.example.hotelreservation.security.SecurityUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
/**
 * 旅居博主探店笔记：发布、列表、点赞、评论、读者评分。
 */
public class BlogNoteService {

    private final BlogNoteMapper noteMapper;
    private final NoteLikeMapper likeMapper;
    private final NoteCommentMapper commentMapper;
    private final NoteRatingMapper ratingMapper;
    private final HotelMapper hotelMapper;
    private final UserMapper userMapper;

    @Transactional
    public NoteCardResponse create(CreateNoteRequest req) {
        Long me = SecurityUtils.currentUserId();
        Hotel hotel = hotelMapper.selectById(req.getHotelId());
        if (hotel == null) {
            throw new BizException(ResultCode.NOT_FOUND, "酒店不存在");
        }
        User author = userMapper.selectById(me);
        if (author.getIsBlogger() == null || author.getIsBlogger() != 1) {
            author.setIsBlogger(1);
            if (author.getNickname() == null || author.getNickname().isBlank()) {
                author.setNickname(author.getUsername());
            }
            userMapper.updateById(author);
        }
        BlogNote note = new BlogNote();
        note.setAuthorId(me);
        note.setHotelId(req.getHotelId());
        note.setTitle(req.getTitle().trim());
        note.setContent(req.getContent().trim());
        note.setCoverUrl(req.getCoverUrl());
        note.setAuthorScore(req.getAuthorScore());
        note.setLikeCount(0);
        note.setCommentCount(0);
        note.setRatingSum(0);
        note.setRatingCount(0);
        note.setCreatedAt(LocalDateTime.now());
        note.setUpdatedAt(LocalDateTime.now());
        noteMapper.insert(note);
        return toCard(note, me);
    }

    public PageResponse<NoteCardResponse> listByHotel(Long hotelId, int page, int size) {
        Page<BlogNote> p = noteMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<BlogNote>()
                        .eq(BlogNote::getHotelId, hotelId)
                        .orderByDesc(BlogNote::getCreatedAt));
        return toPage(p);
    }

    public PageResponse<NoteCardResponse> listByAuthor(Long authorId, int page, int size) {
        Page<BlogNote> p = noteMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<BlogNote>()
                        .eq(BlogNote::getAuthorId, authorId)
                        .orderByDesc(BlogNote::getCreatedAt));
        return toPage(p);
    }

    public PageResponse<NoteCardResponse> feed(int page, int size) {
        Page<BlogNote> p = noteMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<BlogNote>().orderByDesc(BlogNote::getCreatedAt));
        return toPage(p);
    }

    public NoteCardResponse detail(Long noteId) {
        BlogNote note = requireNote(noteId);
        Long me = currentUserIdOrNull();
        return toCard(note, me);
    }

    @Transactional
    public NoteCardResponse like(Long noteId) {
        Long me = SecurityUtils.currentUserId();
        BlogNote note = requireNote(noteId);
        Long exists = likeMapper.selectCount(new LambdaQueryWrapper<NoteLike>()
                .eq(NoteLike::getNoteId, noteId)
                .eq(NoteLike::getUserId, me));
        if (exists == null || exists == 0) {
            NoteLike like = new NoteLike();
            like.setNoteId(noteId);
            like.setUserId(me);
            like.setCreatedAt(LocalDateTime.now());
            likeMapper.insert(like);
            note.setLikeCount(note.getLikeCount() + 1);
            note.setUpdatedAt(LocalDateTime.now());
            noteMapper.updateById(note);
        }
        return toCard(note, me);
    }

    @Transactional
    public NoteCardResponse unlike(Long noteId) {
        Long me = SecurityUtils.currentUserId();
        BlogNote note = requireNote(noteId);
        int deleted = likeMapper.delete(new LambdaQueryWrapper<NoteLike>()
                .eq(NoteLike::getNoteId, noteId)
                .eq(NoteLike::getUserId, me));
        if (deleted > 0 && note.getLikeCount() > 0) {
            note.setLikeCount(note.getLikeCount() - 1);
            note.setUpdatedAt(LocalDateTime.now());
            noteMapper.updateById(note);
        }
        return toCard(note, me);
    }

    @Transactional
    public CommentResponse comment(Long noteId, CreateCommentRequest req) {
        Long me = SecurityUtils.currentUserId();
        BlogNote note = requireNote(noteId);
        NoteComment c = new NoteComment();
        c.setNoteId(noteId);
        c.setUserId(me);
        c.setContent(req.getContent().trim());
        c.setCreatedAt(LocalDateTime.now());
        commentMapper.insert(c);
        note.setCommentCount(note.getCommentCount() + 1);
        note.setUpdatedAt(LocalDateTime.now());
        noteMapper.updateById(note);
        User u = userMapper.selectById(me);
        return CommentResponse.builder()
                .id(c.getId())
                .noteId(noteId)
                .userId(me)
                .username(u.getUsername())
                .nickname(u.getNickname() != null ? u.getNickname() : u.getUsername())
                .content(c.getContent())
                .createdAt(c.getCreatedAt())
                .build();
    }

    public List<CommentResponse> listComments(Long noteId) {
        requireNote(noteId);
        List<NoteComment> rows = commentMapper.selectList(new LambdaQueryWrapper<NoteComment>()
                .eq(NoteComment::getNoteId, noteId)
                .orderByDesc(NoteComment::getCreatedAt));
        Set<Long> uids = rows.stream().map(NoteComment::getUserId).collect(Collectors.toSet());
        Map<Long, User> users = loadUsers(uids);
        List<CommentResponse> list = new ArrayList<>();
        for (NoteComment c : rows) {
            User u = users.get(c.getUserId());
            list.add(CommentResponse.builder()
                    .id(c.getId())
                    .noteId(c.getNoteId())
                    .userId(c.getUserId())
                    .username(u != null ? u.getUsername() : "unknown")
                    .nickname(u != null && u.getNickname() != null ? u.getNickname() : (u != null ? u.getUsername() : "unknown"))
                    .content(c.getContent())
                    .createdAt(c.getCreatedAt())
                    .build());
        }
        return list;
    }

    @Transactional
    public NoteCardResponse rate(Long noteId, RateNoteRequest req) {
        Long me = SecurityUtils.currentUserId();
        BlogNote note = requireNote(noteId);
        NoteRating existing = ratingMapper.selectOne(new LambdaQueryWrapper<NoteRating>()
                .eq(NoteRating::getNoteId, noteId)
                .eq(NoteRating::getUserId, me));
        if (existing == null) {
            NoteRating r = new NoteRating();
            r.setNoteId(noteId);
            r.setUserId(me);
            r.setScore(req.getScore());
            r.setCreatedAt(LocalDateTime.now());
            r.setUpdatedAt(LocalDateTime.now());
            ratingMapper.insert(r);
            note.setRatingSum(note.getRatingSum() + req.getScore());
            note.setRatingCount(note.getRatingCount() + 1);
        } else {
            int old = existing.getScore();
            existing.setScore(req.getScore());
            existing.setUpdatedAt(LocalDateTime.now());
            ratingMapper.updateById(existing);
            note.setRatingSum(note.getRatingSum() - old + req.getScore());
        }
        note.setUpdatedAt(LocalDateTime.now());
        noteMapper.updateById(note);
        return toCard(note, me);
    }

    private PageResponse<NoteCardResponse> toPage(Page<BlogNote> p) {
        Long me = currentUserIdOrNull();
        List<NoteCardResponse> records = new ArrayList<>();
        for (BlogNote n : p.getRecords()) {
            records.add(toCard(n, me));
        }
        return PageResponse.<NoteCardResponse>builder()
                .total(p.getTotal())
                .page((int) p.getCurrent())
                .size((int) p.getSize())
                .records(records)
                .build();
    }

    private NoteCardResponse toCard(BlogNote note, Long me) {
        Hotel hotel = hotelMapper.selectById(note.getHotelId());
        User author = userMapper.selectById(note.getAuthorId());
        boolean liked = false;
        Integer myScore = null;
        if (me != null) {
            liked = likeMapper.selectCount(new LambdaQueryWrapper<NoteLike>()
                    .eq(NoteLike::getNoteId, note.getId())
                    .eq(NoteLike::getUserId, me)) > 0;
            NoteRating r = ratingMapper.selectOne(new LambdaQueryWrapper<NoteRating>()
                    .eq(NoteRating::getNoteId, note.getId())
                    .eq(NoteRating::getUserId, me));
            if (r != null) {
                myScore = r.getScore();
            }
        }
        Double avg = null;
        if (note.getRatingCount() != null && note.getRatingCount() > 0) {
            avg = Math.round(note.getRatingSum() * 10.0 / note.getRatingCount()) / 10.0;
        }
        return NoteCardResponse.builder()
                .id(note.getId())
                .hotelId(note.getHotelId())
                .hotelName(hotel != null ? hotel.getName() : null)
                .authorId(note.getAuthorId())
                .authorName(author != null ? author.getUsername() : null)
                .authorNickname(author != null
                        ? (author.getNickname() != null ? author.getNickname() : author.getUsername())
                        : null)
                .title(note.getTitle())
                .content(note.getContent())
                .coverUrl(note.getCoverUrl())
                .authorScore(note.getAuthorScore())
                .likeCount(note.getLikeCount() == null ? 0 : note.getLikeCount())
                .commentCount(note.getCommentCount() == null ? 0 : note.getCommentCount())
                .avgReaderScore(avg)
                .ratingCount(note.getRatingCount() == null ? 0 : note.getRatingCount())
                .likedByMe(liked)
                .myScore(myScore)
                .createdAt(note.getCreatedAt())
                .build();
    }

    private BlogNote requireNote(Long noteId) {
        BlogNote note = noteMapper.selectById(noteId);
        if (note == null) {
            throw new BizException(ResultCode.NOT_FOUND, "笔记不存在");
        }
        return note;
    }

    private Long currentUserIdOrNull() {
        try {
            return SecurityUtils.currentUserId();
        } catch (BizException ex) {
            return null;
        }
    }

    private Map<Long, User> loadUsers(Set<Long> ids) {
        Map<Long, User> map = new HashMap<>();
        if (ids.isEmpty()) {
            return map;
        }
        List<User> users = userMapper.selectByIds(ids);
        for (User u : users) {
            map.put(u.getId(), u);
        }
        return map;
    }
}
