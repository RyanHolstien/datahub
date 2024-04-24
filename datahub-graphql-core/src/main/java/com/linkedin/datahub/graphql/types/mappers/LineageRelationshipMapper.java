package com.linkedin.datahub.graphql.types.mappers;

import com.linkedin.common.UrnArrayArray;
import com.linkedin.common.urn.Urn;
import com.linkedin.datahub.graphql.QueryContext;
import com.linkedin.datahub.graphql.generated.Entity;
import com.linkedin.datahub.graphql.generated.EntityType;
import com.linkedin.datahub.graphql.generated.LineageRelationship;
import com.linkedin.datahub.graphql.generated.Restricted;
import com.linkedin.datahub.graphql.types.common.mappers.UrnToEntityMapper;
import io.datahubproject.metadata.services.RestrictedService;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

import static com.linkedin.datahub.graphql.types.mappers.MapperUtils.*;


public class LineageRelationshipMapper {

  private LineageRelationshipMapper() { }

  public static LineageRelationship mapEntityRelationship(
      @Nullable final QueryContext context,
      final com.linkedin.metadata.graph.LineageRelationship lineageRelationship,
      final Set<Urn> restrictedUrns,
      RestrictedService restrictedService) {
    final LineageRelationship result = new LineageRelationship();
    if (restrictedUrns.contains(lineageRelationship.getEntity())) {
      final Restricted restrictedEntity = new Restricted();
      restrictedEntity.setType(EntityType.RESTRICTED);
      String restrictedUrnString =
          restrictedService.encryptRestrictedUrn(lineageRelationship.getEntity()).toString();

      restrictedEntity.setUrn(restrictedUrnString);
      result.setEntity(restrictedEntity);
    } else {
      final Entity partialEntity = UrnToEntityMapper.map(context, lineageRelationship.getEntity());
      if (partialEntity != null) {
        result.setEntity(partialEntity);
      }
    }
    result.setType(lineageRelationship.getType());
    result.setDegree(lineageRelationship.getDegree());
    if (lineageRelationship.hasCreatedOn()) {
      result.setCreatedOn(lineageRelationship.getCreatedOn());
    }
    if (lineageRelationship.hasCreatedActor()) {
      final Urn createdActor = lineageRelationship.getCreatedActor();
      result.setCreatedActor(UrnToEntityMapper.map(context, createdActor));
    }
    if (lineageRelationship.hasUpdatedOn()) {
      result.setUpdatedOn(lineageRelationship.getUpdatedOn());
    }
    if (lineageRelationship.hasUpdatedActor()) {
      final Urn updatedActor = lineageRelationship.getUpdatedActor();
      result.setUpdatedActor(UrnToEntityMapper.map(context, updatedActor));
    }
    result.setIsManual(lineageRelationship.hasIsManual() && lineageRelationship.isIsManual());
    if (lineageRelationship.getPaths() != null) {
      UrnArrayArray paths = lineageRelationship.getPaths();
      result.setPaths(
          paths.stream().map(path -> mapPath(context, path)).collect(Collectors.toList()));
    }

    return result;
  }
}
