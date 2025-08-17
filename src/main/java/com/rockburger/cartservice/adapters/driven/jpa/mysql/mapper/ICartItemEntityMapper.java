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

    /**
     * Maps from domain model to entity.
     * Ignores cart relationship and version (managed by JPA).
     * Timestamps and subtotal will be set by JPA lifecycle callbacks.
     */
    @Mapping(target = "cart", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    CartItemEntity toEntity(CartItemModel model);

    /**
     * Maps from entity to domain model.
     * All fields are mapped directly.
     */
    CartItemModel toModel(CartItemEntity entity);

    /**
     * Update existing entity from model.
     * Preserves id, cart relationship, version and timestamps.
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "cart", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateEntity(@MappingTarget CartItemEntity entity, CartItemModel model);

    /**
     * Maps from domain model to entity with cart relationship.
     * Convenience method for creating entities with cart context.
     */
    default CartItemEntity toEntity(CartItemModel model, CartEntity cart) {
        CartItemEntity entity = toEntity(model);
        entity.setCart(cart);
        return entity;
    }

    /**
     * After mapping callback to ensure data consistency.
     * Recalculates subtotal after mapping to ensure accuracy.
     */
    @AfterMapping
    default void ensureDataConsistency(@MappingTarget CartItemEntity entity) {
        // Ensure subtotal is correctly calculated after mapping
        if (entity.getQuantity() > 0 && entity.getPrice() >= 0) {
            entity.calculateSubtotal();
        }
    }
}