package com.mrelote.pedidos.dto.response;

import com.mrelote.pedidos.entity.ImagenProducto;

public record ImagenProductoResponse(Long id, String url, Integer orden) {
    public static ImagenProductoResponse from(ImagenProducto i) {
        return new ImagenProductoResponse(i.getId(), i.getUrl(), i.getOrden());
    }
}
