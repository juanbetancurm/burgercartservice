package com.rockburger.cartservice.adapters.driven.jpa.mysql.mapper;

import com.rockburger.cartservice.adapters.driven.jpa.mysql.entity.CartEntity;
import com.rockburger.cartservice.domain.model.CartModel;
import org.mapstruct.*;

@Mapper(
        componentModel = "spring",
        uses = {ICartItemEntityMapper.class},
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE
)
public interface ICartEntityMapper {

    @Mapping(target = "version", ignore = true)
    CartEntity toEntity(CartModel model);

    CartModel toModel(CartEntity entity);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "items", ignore = true) // Items are handled separately
    void updateEntity(@MappingTarget CartEntity entity, CartModel model);
}