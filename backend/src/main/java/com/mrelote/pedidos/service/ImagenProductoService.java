package com.mrelote.pedidos.service;

import com.mrelote.pedidos.dto.response.ImagenProductoResponse;
import com.mrelote.pedidos.entity.ImagenProducto;
import com.mrelote.pedidos.entity.Producto;
import com.mrelote.pedidos.exception.BusinessRuleException;
import com.mrelote.pedidos.exception.NotFoundException;
import com.mrelote.pedidos.repository.ImagenProductoRepository;
import com.mrelote.pedidos.repository.ProductoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ImagenProductoService {

    private final ImagenProductoRepository imagenProductoRepository;
    private final ProductoRepository productoRepository;

    @Value("${mrelote.storage.uploads-dir:./uploads}")
    private String uploadsDir;

    @Transactional
    public ImagenProductoResponse agregar(Long productoId, MultipartFile archivo) {
        if (archivo == null || archivo.isEmpty()) {
            throw new BusinessRuleException("El archivo de imagen es obligatorio");
        }
        Producto producto = productoRepository.findById(productoId)
                .orElseThrow(() -> new NotFoundException("No existe el producto " + productoId));

        String extension = extensionDe(archivo.getOriginalFilename());
        String nombreArchivo = UUID.randomUUID() + extension;
        try {
            Path carpeta = Path.of(uploadsDir, "productos", productoId.toString());
            Files.createDirectories(carpeta);
            Path destino = carpeta.resolve(nombreArchivo);
            archivo.transferTo(destino);
        } catch (IOException e) {
            throw new BusinessRuleException("No se pudo guardar la imagen: " + e.getMessage());
        }

        ImagenProducto imagen = ImagenProducto.builder()
                .producto(producto)
                .url("/uploads/productos/" + productoId + "/" + nombreArchivo)
                .orden(0)
                .build();
        return ImagenProductoResponse.from(imagenProductoRepository.save(imagen));
    }

    private String extensionDe(String nombreOriginal) {
        if (nombreOriginal == null || !nombreOriginal.contains(".")) {
            return "";
        }
        return nombreOriginal.substring(nombreOriginal.lastIndexOf('.'));
    }
}
