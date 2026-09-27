package org.example.hotelreservation.web;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.hotelreservation.common.ApiResult;
import org.example.hotelreservation.dto.CommentResponse;
import org.example.hotelreservation.dto.CreateCommentRequest;
import org.example.hotelreservation.dto.CreateNoteRequest;
import org.example.hotelreservation.dto.NoteCardResponse;
import org.example.hotelreservation.dto.PageResponse;
import org.example.hotelreservation.dto.RateNoteRequest;
import org.example.hotelreservation.service.BlogNoteService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/notes")
@RequiredArgsConstructor
/**
 * 探店笔记：列表、详情、点赞、评论、评分。
 */
public class NoteController {

    private final BlogNoteService blogNoteService;

    @PostMapping
    public ApiResult<NoteCardResponse> create(@Valid @RequestBody CreateNoteRequest request) {
        return ApiResult.ok(blogNoteService.create(request));
    }

    @GetMapping("/feed")
    public ApiResult<PageResponse<NoteCardResponse>> feed(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ApiResult.ok(blogNoteService.feed(page, size));
    }

    @GetMapping("/hotel/{hotelId}")
    public ApiResult<PageResponse<NoteCardResponse>> byHotel(
            @PathVariable Long hotelId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ApiResult.ok(blogNoteService.listByHotel(hotelId, page, size));
    }

    @GetMapping("/author/{authorId}")
    public ApiResult<PageResponse<NoteCardResponse>> byAuthor(
            @PathVariable Long authorId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ApiResult.ok(blogNoteService.listByAuthor(authorId, page, size));
    }

    @GetMapping("/{noteId}")
    public ApiResult<NoteCardResponse> detail(@PathVariable Long noteId) {
        return ApiResult.ok(blogNoteService.detail(noteId));
    }

    @PostMapping("/{noteId}/like")
    public ApiResult<NoteCardResponse> like(@PathVariable Long noteId) {
        return ApiResult.ok(blogNoteService.like(noteId));
    }

    @DeleteMapping("/{noteId}/like")
    public ApiResult<NoteCardResponse> unlike(@PathVariable Long noteId) {
        return ApiResult.ok(blogNoteService.unlike(noteId));
    }

    @PostMapping("/{noteId}/comments")
    public ApiResult<CommentResponse> comment(
            @PathVariable Long noteId,
            @Valid @RequestBody CreateCommentRequest request) {
        return ApiResult.ok(blogNoteService.comment(noteId, request));
    }

    @GetMapping("/{noteId}/comments")
    public ApiResult<List<CommentResponse>> comments(@PathVariable Long noteId) {
        return ApiResult.ok(blogNoteService.listComments(noteId));
    }

    @PostMapping("/{noteId}/rate")
    public ApiResult<NoteCardResponse> rate(
            @PathVariable Long noteId,
            @Valid @RequestBody RateNoteRequest request) {
        return ApiResult.ok(blogNoteService.rate(noteId, request));
    }
}
