package com.rockburger.cartservice.adapters.driven.jpa.mysql.mapper;

import com.rockburger.cartservice.adapters.driven.jpa.mysql.entity.CartItemEntity;
import com.rockburger.cartservice.domain.model.CartItemModel;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(
        componentModel = "spring",
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE
)
public interface ICartItemEntityMapper {
    @Mapping(target = "cart", ignore = true)
    @Mapping(target = "version", ignore = true)
    CartItemEntity toEntity(CartItemModel model);

    @Mapping(target = "id", source = "id")
    CartItemModel toModel(CartItemEntity entity);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "cart", ignore = true)
    @Mapping(target = "version", ignore = true)
    void updateEntity(@MappingTarget CartItemEntity entity, CartItemModel model);
}