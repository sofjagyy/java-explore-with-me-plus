package ru.practicum.comment.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import ru.practicum.comment.dto.CommentDto;
import ru.practicum.comment.dto.NewCommentDto;
import ru.practicum.comment.model.Comment;
import ru.practicum.user.mapper.UserMapper;

@Mapper(componentModel = "spring", uses = {UserMapper.class})
public interface CommentMapper {

    @Mapping(source = "event.id", target = "eventId")
    @Mapping(source = "author", target = "author")
    @Mapping(source = "createdOn", target = "createdOn")
    CommentDto toCommentDto(Comment comment);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "event", ignore = true)
    @Mapping(target = "author", ignore = true)
    @Mapping(target = "createdOn", ignore = true)
    Comment toComment(NewCommentDto newCommentDto);
}