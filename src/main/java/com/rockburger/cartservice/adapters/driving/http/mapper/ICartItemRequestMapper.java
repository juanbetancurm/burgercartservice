package com.rockburger.cartservice.adapters.driving.http.mapper;

import com.rockburger.cartservice.adapters.driving.http.dto.request.AddCartItemRequest;
import com.rockburger.cartservice.adapters.driving.http.dto.response.CartItemResponse;
import com.rockburger.cartservice.domain.model.CartItemModel;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface ICartItemRequestMapper {
    @Mapping(target = "id", ignore = true)
    CartItemModel toModel(AddCartItemRequest request);

    CartItemResponse toResponse(CartItemModel model);

    List<CartItemResponse> toResponseList(List<CartItemModel> models);
}