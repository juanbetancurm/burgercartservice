package com.rockburger.cartservice.adapters.driven.jpa.mysql.mapper;

import com.rockburger.cartservice.adapters.driven.jpa.mysql.entity.CartEntity;
import com.rockburger.cartservice.adapters.driven.jpa.mysql.entity.CartItemEntity;
import com.rockburger.cartservice.domain.model.CartItemModel;
import org.mapstruct.*;

@Mapper(
        componentModel = "spring",
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE
)
public interface ICartItemEntityMapper {

    @Mapping(target = "cart", ignore = true)
    @Mapping(target = "version", ignore = true)
    CartItemEntity toEntity(CartItemModel model);

    CartItemModel toModel(CartItemEntity entity);

    @AfterMapping
    default void linkCartToItems(@MappingTarget CartItemEntity entity, CartItemModel model) {
        // This will be filled in separately by the controller
    }

    default CartItemEntity toEntity(CartItemModel model, CartEntity cart) {
        CartItemEntity entity = toEntity(model);
        entity.setCart(cart);
        return entity;
    }
}