package ru.practicum.comment.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import ru.practicum.comment.dto.CommentDto;
import ru.practicum.comment.dto.NewCommentDto;
import ru.practicum.comment.dto.UpdateCommentDto;
import ru.practicum.comment.service.CommentService;

import java.util.List;

@RestController
@RequestMapping("/users/{userId}")
@RequiredArgsConstructor
@Validated
@Slf4j
public class PrivateCommentController {
    private final CommentService commentService;

    @PostMapping("/events/{eventId}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    public CommentDto addComment(@PathVariable Long userId,
                                 @PathVariable Long eventId,
                                 @RequestBody @Valid NewCommentDto newCommentDto) {
        log.info("Add comment user={} event={} dto={}", userId, eventId, newCommentDto);
        return commentService.addComment(userId, eventId, newCommentDto);
    }

    @PatchMapping("/comments/{commentId}")
    public CommentDto updateComment(@PathVariable Long userId,
                                    @PathVariable Long commentId,
                                    @RequestBody @Valid UpdateCommentDto updateCommentDto) {
        log.info("Update comment user={} comment={} dto={}", userId, commentId, updateCommentDto);
        return commentService.updateComment(userId, commentId, updateCommentDto);
    }

    @GetMapping("/comments")
    public List<CommentDto> getCommentsByUser(@PathVariable Long userId,
                                              @RequestParam(defaultValue = "0") @PositiveOrZero Integer from,
                                              @RequestParam(defaultValue = "10") @Positive Integer size) {
        log.info("Get comments user={} from={} size={}", userId, from, size);
        return commentService.getCommentsByUser(userId, from, size);
    }

    @DeleteMapping("/comments/{commentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteComment(@PathVariable Long userId,
                              @PathVariable Long commentId) {
        log.info("Delete comment user={} comment={}", userId, commentId);
        commentService.deleteComment(userId, commentId);
    }
}
