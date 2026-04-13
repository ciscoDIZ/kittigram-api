package org.ciscoadiz.cat.mapper;

import jakarta.enterprise.context.ApplicationScoped;
import org.ciscoadiz.cat.dto.*;
import org.ciscoadiz.cat.entity.Cat;
import org.ciscoadiz.cat.entity.CatImage;
import org.ciscoadiz.cat.entity.CatSex;

import java.util.List;

@ApplicationScoped
public class CatMapper {

    public Cat toEntity(CatCreateRequest request, Long userId) {
        Cat cat = new Cat();
        cat.name = request.name();
        cat.age = request.age();
        cat.sex = CatSex.valueOf(request.sex());
        cat.description = request.description();
        cat.neutered = request.neutered() != null ? request.neutered() : false;
        cat.userId = userId;
        cat.city = request.city();
        cat.region = request.region();
        cat.country = request.country();
        cat.latitude = request.latitude();
        cat.longitude = request.longitude();
        return cat;
    }

    public void updateEntity(Cat cat, CatUpdateRequest request) {
        cat.name = request.name();
        cat.age = request.age();
        cat.description = request.description();
        cat.neutered = request.neutered() != null ? request.neutered() : cat.neutered;
        cat.city = request.city();
        cat.region = request.region();
        cat.country = request.country();
        cat.latitude = request.latitude();
        cat.longitude = request.longitude();
    }

    public CatResponse toResponse(Cat cat, List<CatImage> images) {
        return new CatResponse(
                cat.id,
                cat.name,
                cat.age,
                cat.sex.name(),
                cat.description,
                cat.neutered,
                cat.status.name(),
                cat.city,
                cat.region,
                cat.country,
                cat.latitude,
                cat.longitude,
                cat.userId,
                images.stream().map(this::toImageResponse).toList(),
                cat.createdAt,
                cat.updatedAt
        );
    }
    public CatImageResponse toImageResponse(CatImage image) {
        return new CatImageResponse(
                image.id,
                image.url,
                image.imageOrder
        );
    }

    public CatSummaryResponse toSummaryResponse(Cat cat) {
        return new CatSummaryResponse(
                cat.id,
                cat.name,
                cat.profileImageUrl,
                cat.age,
                cat.sex.name(),
                cat.neutered,
                cat.status.name(),
                cat.city,
                cat.region,
                cat.country,
                cat.createdAt
        );
    }
}