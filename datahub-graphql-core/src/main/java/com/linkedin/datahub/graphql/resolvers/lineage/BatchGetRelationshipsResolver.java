package com.linkedin.datahub.graphql.resolvers.lineage;

import com.datahub.authorization.AuthorizationConfiguration;
import com.linkedin.common.urn.Urn;
import com.linkedin.common.urn.UrnUtils;
import com.linkedin.datahub.graphql.QueryContext;
import com.linkedin.datahub.graphql.authorization.AuthorizationUtils;
import com.linkedin.datahub.graphql.generated.BatchGetRelationshipsInput;
import com.linkedin.datahub.graphql.generated.BatchRelationshipsResult;
import com.linkedin.datahub.graphql.generated.Entity;
import com.linkedin.datahub.graphql.generated.EntityLineageRelationship;
import com.linkedin.datahub.graphql.generated.LineageDirection;
import com.linkedin.datahub.graphql.generated.LineageRelationship;
import com.linkedin.datahub.graphql.types.common.mappers.LineageFlagsInputMapper;
import com.linkedin.datahub.graphql.types.common.mappers.UrnToEntityMapper;
import com.linkedin.datahub.graphql.types.mappers.LineageRelationshipMapper;
import com.linkedin.metadata.graph.EntityLineageScrollResult;
import com.linkedin.metadata.graph.GraphService;
import com.linkedin.metadata.query.LineageFlags;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import io.datahubproject.metadata.services.RestrictedService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

import static com.linkedin.datahub.graphql.resolvers.ResolverUtils.*;


public class BatchGetRelationshipsResolver implements DataFetcher<CompletableFuture<BatchRelationshipsResult>> {

  private final GraphService graphService;
  private final AuthorizationConfiguration authorizationConfiguration;
  private final RestrictedService restrictedService;

  public BatchGetRelationshipsResolver(final GraphService graphService,
      final AuthorizationConfiguration authorizationConfiguration, RestrictedService restrictedService) {
    this.graphService = graphService;
    this.authorizationConfiguration = authorizationConfiguration;
    this.restrictedService = restrictedService;
  }

  @Override
  public CompletableFuture<BatchRelationshipsResult> get(DataFetchingEnvironment environment) throws Exception {
    final QueryContext context = environment.getContext();
    final BatchGetRelationshipsInput input =
        bindArgument(environment.getArgument("input"), BatchGetRelationshipsInput.class);

    final int pageSize = input.getPageSize() != null ? input.getPageSize() : 100;
    @Nullable final String scrollId = input.getScrollId();
    Map<String, Set<com.linkedin.metadata.graph.LineageDirection>> urnDirections = new HashMap<>();
    input.getRelationshipInputs().forEach(relationshipInput -> urnDirections.put(relationshipInput.getUrn(),
        relationshipInput.getDirections().stream().map(this::convertDirection).collect(Collectors.toSet())));
    com.linkedin.metadata.graph.LineageDirection defaultDirection = convertDirection(input.getDefaultDirection());
    final LineageFlags lineageFlags = LineageFlagsInputMapper.map(context, input.getLineageFlags());

    return CompletableFuture.supplyAsync(() -> {
      EntityLineageScrollResult lineageResult =
          graphService.batchGetLineage(urnDirections, defaultDirection, pageSize, scrollId,
              context.getOperationContext().withLineageFlags(flags -> lineageFlags));
      BatchRelationshipsResult result = new BatchRelationshipsResult();
      result.setCount(lineageResult.getCount());
      result.setNextScrollId(lineageResult.getNextScrollId());
      List<LineageRelationship> relationshipList = lineageResult.getRelationships().stream().map(relationship ->
          LineageRelationshipMapper.mapEntityRelationship(context, relationship,
              AuthorizationUtils.getRestrictedUrns(context, authorizationConfiguration, lineageResult.getRelationships(), null),
              restrictedService)).collect(Collectors.toList());
      List<EntityLineageRelationship> entityRelationships = mapToEntityRelationships(relationshipList, context);
      result.setRelationships(entityRelationships);
      return result;
    });
  }

  private com.linkedin.metadata.graph.LineageDirection convertDirection(@Nullable LineageDirection apiDirection) {
    com.linkedin.metadata.graph.LineageDirection direction = null;
    if (apiDirection != null) {
      direction = com.linkedin.metadata.graph.LineageDirection.valueOf(apiDirection.name());
    }
    return direction;
  }

  private List<EntityLineageRelationship> mapToEntityRelationships(List<LineageRelationship> lineageRelationships, QueryContext context) {
    Map<Entity, List<LineageRelationship>> entityRelationshipsMap = new HashMap<>();
    for(LineageRelationship lineageRelationship : lineageRelationships) {
      Urn sourceUrn = UrnUtils.getUrn(lineageRelationship.getPaths().get(0).getPath().get(0).getUrn());
      Entity sourceEntity = UrnToEntityMapper.map(context, sourceUrn);
      List<LineageRelationship> linkedRelationships = entityRelationshipsMap.computeIfAbsent(sourceEntity,
          key -> new ArrayList<>());
      linkedRelationships.add(lineageRelationship);
    }
    return entityRelationshipsMap.entrySet().stream().map(entry -> {
      EntityLineageRelationship entityLineageRelationship = new EntityLineageRelationship();
      entityLineageRelationship.setEntity(entry.getKey());
      entityLineageRelationship.setRelationships(entry.getValue());
      return entityLineageRelationship;
    }).collect(Collectors.toList());
  }
}
