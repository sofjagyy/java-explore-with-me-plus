package ru.practicum.comment.service;

import ru.practicum.comment.dto.CommentDto;
import ru.practicum.comment.dto.NewCommentDto;
import ru.practicum.comment.dto.UpdateCommentDto;

import java.util.List;

public interface CommentService {
    CommentDto addComment(Long userId, Long eventId, NewCommentDto newCommentDto);

    List<CommentDto> getCommentsByEvent(Long eventId, Integer from, Integer size);

    List<CommentDto> getCommentsByUser(Long userId, Integer from, Integer size);

    void deleteComment(Long userId, Long commentId);

    void deleteCommentByAdmin(Long commentId);

    CommentDto updateComment(Long userId, Long commentId, UpdateCommentDto updateCommentDto);
}
