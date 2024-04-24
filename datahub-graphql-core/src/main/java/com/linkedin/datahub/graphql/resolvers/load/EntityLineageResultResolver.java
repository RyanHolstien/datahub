package com.linkedin.datahub.graphql.resolvers.load;

import static com.linkedin.datahub.graphql.resolvers.ResolverUtils.*;
import static com.linkedin.datahub.graphql.types.mappers.MapperUtils.*;

import com.datahub.authorization.AuthorizationConfiguration;
import com.linkedin.common.UrnArrayArray;
import com.linkedin.common.urn.Urn;
import com.linkedin.common.urn.UrnUtils;
import com.linkedin.data.template.SetMode;
import com.linkedin.datahub.graphql.QueryContext;
import com.linkedin.datahub.graphql.authorization.AuthorizationUtils;
import com.linkedin.datahub.graphql.generated.Entity;
import com.linkedin.datahub.graphql.generated.EntityLineageResult;
import com.linkedin.datahub.graphql.generated.EntityType;
import com.linkedin.datahub.graphql.generated.LineageDirection;
import com.linkedin.datahub.graphql.generated.LineageInput;
import com.linkedin.datahub.graphql.generated.LineageRelationship;
import com.linkedin.datahub.graphql.generated.Restricted;
import com.linkedin.datahub.graphql.types.common.mappers.UrnToEntityMapper;
import com.linkedin.datahub.graphql.types.mappers.LineageRelationshipMapper;
import com.linkedin.metadata.graph.SiblingGraphService;
import com.linkedin.metadata.query.LineageFlags;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import io.datahubproject.metadata.services.RestrictedService;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;

/**
 * GraphQL Resolver responsible for fetching lineage relationships between entities in the DataHub
 * graph. Lineage relationship denotes whether an entity is directly upstream or downstream of
 * another entity
 */
@Slf4j
public class EntityLineageResultResolver
    implements DataFetcher<CompletableFuture<EntityLineageResult>> {

  private final SiblingGraphService _siblingGraphService;
  private final RestrictedService _restrictedService;
  private final AuthorizationConfiguration _authorizationConfiguration;

  public EntityLineageResultResolver(
      final SiblingGraphService siblingGraphService,
      final RestrictedService restrictedService,
      final AuthorizationConfiguration authorizationConfiguration) {
    _siblingGraphService = siblingGraphService;
    _restrictedService = restrictedService;
    _authorizationConfiguration = authorizationConfiguration;
  }

  @Override
  public CompletableFuture<EntityLineageResult> get(DataFetchingEnvironment environment) {
    final QueryContext context = environment.getContext();
    Urn urn = UrnUtils.getUrn(((Entity) environment.getSource()).getUrn());
    final LineageInput input = bindArgument(environment.getArgument("input"), LineageInput.class);

    final LineageDirection lineageDirection = input.getDirection();
    @Nullable final Integer start = input.getStart(); // Optional!
    @Nullable final Integer count = input.getCount(); // Optional!
    @Nullable final Boolean separateSiblings = input.getSeparateSiblings(); // Optional!
    @Nullable final Long startTimeMillis = input.getStartTimeMillis(); // Optional!
    @Nullable final Long endTimeMillis = input.getEndTimeMillis(); // Optional!

    com.linkedin.metadata.graph.LineageDirection resolvedDirection =
        com.linkedin.metadata.graph.LineageDirection.valueOf(lineageDirection.toString());

    final Urn finalUrn = urn;
    return CompletableFuture.supplyAsync(
        () -> {
          try {
            com.linkedin.metadata.graph.EntityLineageResult entityLineageResult =
                _siblingGraphService.getLineage(
                    finalUrn,
                    resolvedDirection,
                    start != null ? start : 0,
                    count != null ? count : 100,
                    1,
                    separateSiblings != null ? input.getSeparateSiblings() : false,
                    new HashSet<>(),
                    new LineageFlags()
                        .setStartTimeMillis(startTimeMillis, SetMode.REMOVE_IF_NULL)
                        .setEndTimeMillis(endTimeMillis, SetMode.REMOVE_IF_NULL));

            Set<Urn> restrictedUrns = AuthorizationUtils.getRestrictedUrns(context, _authorizationConfiguration,
                entityLineageResult.getRelationships(), urn);

            return mapEntityRelationships(context, entityLineageResult, restrictedUrns);
          } catch (Exception e) {
            log.error("Failed to fetch lineage for {}", finalUrn);
            throw new RuntimeException(
                String.format("Failed to fetch lineage for %s", finalUrn), e);
          }
        });
  }

  private EntityLineageResult mapEntityRelationships(
      @Nullable final QueryContext context,
      final com.linkedin.metadata.graph.EntityLineageResult entityLineageResult,
      final Set<Urn> restrictedUrns) {
    final EntityLineageResult result = new EntityLineageResult();
    result.setStart(entityLineageResult.getStart());
    result.setCount(entityLineageResult.getCount());
    result.setTotal(entityLineageResult.getTotal());
    result.setFiltered(entityLineageResult.getFiltered());
    result.setRelationships(
        entityLineageResult.getRelationships().stream()
            .map(r -> LineageRelationshipMapper.mapEntityRelationship(context, r, restrictedUrns, _restrictedService))
            .collect(Collectors.toList()));
    return result;
  }
}
