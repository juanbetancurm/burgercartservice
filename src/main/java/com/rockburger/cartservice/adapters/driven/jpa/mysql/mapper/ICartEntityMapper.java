package com.rockburger.cartservice.adapters.driven.jpa.mysql.mapper;

import com.rockburger.cartservice.adapters.driven.jpa.mysql.entity.CartEntity;
import com.rockburger.cartservice.domain.model.CartModel;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface ICartEntityMapper {

    // Entity to Model mapping - only map fields that exist in both
    @Mapping(target = "items", ignore = true) // Items handled separately
    CartModel toModel(CartEntity entity);

    // Model to Entity mapping - only map fields that exist in both
    @Mapping(target = "items", ignore = true) // Items handled separately
    @Mapping(target = "expiryWarningSent", ignore = true) // Not in CartModel yet
    CartEntity toEntity(CartModel model);

    // Update entity from model
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "items", ignore = true)
    @Mapping(target = "expiryWarningSent", ignore = true)
    void updateEntity(@MappingTarget CartEntity entity, CartModel model);
}