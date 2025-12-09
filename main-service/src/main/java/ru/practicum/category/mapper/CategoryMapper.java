package ru.practicum.category.mapper;

import org.mapstruct.Mapper;
import ru.practicum.category.Category;
import ru.practicum.category.dto.CategoryDto;
import ru.practicum.category.dto.NewCategoryDto;

@Mapper(componentModel = "spring")
public interface CategoryMapper {
    Category toCategory(NewCategoryDto newCategoryDto);
    CategoryDto toCategoryDto(Category category);
}

