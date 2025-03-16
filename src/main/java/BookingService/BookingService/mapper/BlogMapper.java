package BookingService.BookingService.mapper;

import BookingService.BookingService.dto.response.BlogResponse;
import BookingService.BookingService.entity.Blog;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Mapper(componentModel = "spring", uses = {UserMapper.class})
public interface BlogMapper {

    DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    @Mapping(source = "blogId", target = "blogId")
    @Mapping(source = "title", target = "title")
    @Mapping(source = "content", target = "content")
    @Mapping(source = "createdAt", target = "createdAt", qualifiedByName = "formatLocalDateTime")
    @Mapping(source = "updatedAt", target = "updatedAt", qualifiedByName = "formatLocalDateTime")
    @Mapping(source = "author", target = "author") // UserMapper sẽ xử lý author
    @Mapping(source = "images", target = "images")
    BlogResponse toResponse(Blog blog);

    @Named("formatLocalDateTime")
    default String formatLocalDateTime(LocalDateTime dateTime) {
        if (dateTime == null) return null;
        return dateTime.format(DATE_TIME_FORMATTER);
    }
}