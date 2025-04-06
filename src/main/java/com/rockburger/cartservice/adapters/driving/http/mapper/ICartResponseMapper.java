package com.rockburger.cartservice.adapters.driving.http.mapper;

import com.rockburger.cartservice.adapters.driving.http.dto.response.CartResponse;
import com.rockburger.cartservice.domain.model.CartModel;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", uses = {ICartItemRequestMapper.class})
public interface ICartResponseMapper {
    CartResponse toResponse(CartModel model);
}