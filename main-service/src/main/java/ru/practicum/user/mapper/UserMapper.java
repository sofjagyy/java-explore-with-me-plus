package ru.practicum.user.mapper;

import org.mapstruct.Mapper;
import ru.practicum.user.User;
import ru.practicum.user.dto.UserDto;
import ru.practicum.user.dto.UserRequestDto;

@Mapper(componentModel = "spring")
public interface UserMapper {
    User toUser(UserRequestDto userRequestDto);
    UserDto toUserDto(User user);
}

