package es.kitti.cat.mapper;

import jakarta.enterprise.context.ApplicationScoped;
import es.kitti.cat.dto.*;
import es.kitti.cat.entity.Cat;
import es.kitti.cat.entity.CatImage;
import es.kitti.cat.entity.CatSex;

import java.util.List;

@ApplicationScoped
public class CatMapper {

    public Cat toEntity(CatCreateRequest request) {
        Cat cat = new Cat();
        cat.name = request.name();
        cat.age = request.age();
        cat.sex = CatSex.valueOf(request.sex());
        cat.description = request.description();
        cat.neutered = request.neutered() != null ? request.neutered() : false;
        cat.city = request.city();
        cat.region = request.region();
        cat.country = request.country();
        cat.latitude = request.latitude();
        cat.longitude = request.longitude();
        return cat;
    }

    public void updateEntity(Cat cat, CatUpdateRequest request) {
        if (request.name() != null) cat.name = request.name();
        if (request.age() != null) cat.age = request.age();
        if (request.description() != null) cat.description = request.description();
        if (request.neutered() != null) cat.neutered = request.neutered();
        if (request.city() != null) cat.city = request.city();
        if (request.region() != null) cat.region = request.region();
        if (request.country() != null) cat.country = request.country();
        if (request.latitude() != null) cat.latitude = request.latitude();
        if (request.longitude() != null) cat.longitude = request.longitude();
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
                cat.organizationId,
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