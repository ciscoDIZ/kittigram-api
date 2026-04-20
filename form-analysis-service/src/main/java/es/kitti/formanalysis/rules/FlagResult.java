package es.kitti.formanalysis.rules;

import es.kitti.formanalysis.entity.FlagSeverity;

public record FlagResult(
        FlagSeverity severity,
        String code,
        String description
) {}