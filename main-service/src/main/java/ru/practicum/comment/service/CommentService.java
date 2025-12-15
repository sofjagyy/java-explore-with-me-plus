package ru.practicum.comment.service;

import ru.practicum.comment.dto.CommentDto;
import ru.practicum.comment.dto.NewCommentDto;

import java.util.List;

public interface CommentService {
    CommentDto addComment(Long userId, Long eventId, NewCommentDto newCommentDto);

    List<CommentDto> getCommentsByEvent(Long eventId);

    List<CommentDto> getCommentsByUser(Long userId);

    void deleteComment(Long userId, Long commentId);
}