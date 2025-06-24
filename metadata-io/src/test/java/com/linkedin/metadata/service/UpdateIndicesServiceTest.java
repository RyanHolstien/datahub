package com.linkedin.metadata.service;

import static com.linkedin.metadata.Constants.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.testng.Assert.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.linkedin.common.AuditStamp;
import com.linkedin.common.Status;
import com.linkedin.common.UrnArray;
import com.linkedin.common.urn.Urn;
import com.linkedin.common.urn.UrnUtils;
import com.linkedin.container.Container;
import com.linkedin.data.template.RecordTemplate;
import com.linkedin.data.template.StringMap;
import com.linkedin.dataset.EditableDatasetProperties;
import com.linkedin.events.metadata.ChangeType;
import com.linkedin.metadata.aspect.batch.MCLItem;
import com.linkedin.metadata.entity.ebean.batch.MCLItemImpl;
import com.linkedin.metadata.models.AspectSpec;
import com.linkedin.metadata.models.EntitySpec;
import com.linkedin.metadata.search.elasticsearch.ElasticSearchService;
import com.linkedin.metadata.search.transformer.SearchDocumentTransformer;
import com.linkedin.metadata.systemmetadata.SystemMetadataService;
import com.linkedin.metadata.timeseries.TimeseriesAspectService;
import com.linkedin.metadata.utils.AuditStampUtils;
import com.linkedin.metadata.utils.GenericRecordUtils;
import com.linkedin.metadata.utils.SystemMetadataUtils;
import com.linkedin.mxe.GenericAspect;
import com.linkedin.mxe.MetadataChangeLog;
import com.linkedin.mxe.SystemMetadata;
import com.linkedin.structured.StructuredPropertyDefinition;
import io.datahubproject.metadata.context.OperationContext;
import io.datahubproject.test.metadata.context.TestOperationContexts;
import java.util.Map;
import java.util.Optional;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class UpdateIndicesServiceTest {

  @Mock private UpdateGraphIndicesService updateGraphIndicesService;
  @Mock private ElasticSearchService entitySearchService;
  @Mock private TimeseriesAspectService timeseriesAspectService;
  @Mock private SystemMetadataService systemMetadataService;
  @Mock private SearchDocumentTransformer searchDocumentTransformer;

  private OperationContext operationContext;
  private UpdateIndicesService updateIndicesService;
  private ObjectMapper objectMapper = new ObjectMapper();

  @BeforeMethod
  public void setup() {
    MockitoAnnotations.openMocks(this);
    operationContext = TestOperationContexts.systemContextNoSearchAuthorization();
    updateIndicesService =
        new UpdateIndicesService(
            updateGraphIndicesService,
            entitySearchService,
            timeseriesAspectService,
            systemMetadataService,
            searchDocumentTransformer,
            "MD5");
  }

  @Test
  public void testContainerHandleDeleteEvent() throws Exception {
    Urn urn = UrnUtils.getUrn("urn:li:dataset:(urn:li:dataPlatform:hdfs,SampleHdfsDataset,PROD)");
    EntitySpec entitySpec = operationContext.getEntityRegistry().getEntitySpec(DATASET_ENTITY_NAME);
    AspectSpec aspectSpec = entitySpec.getAspectSpec(CONTAINER_ASPECT_NAME);

    // Create test data
    MetadataChangeLog event = new MetadataChangeLog();
    event.setChangeType(ChangeType.DELETE);
    event.setEntityUrn(urn);
    event.setAspectName(CONTAINER_ASPECT_NAME);
    event.setEntityType(urn.getEntityType());
    event.setSystemMetadata(SystemMetadataUtils.createDefaultSystemMetadata());
    event.setCreated(AuditStampUtils.createDefaultAuditStamp());
    // Execute Delete
    updateIndicesService.handleChangeEvent(operationContext, event);

    // Verify
    verify(systemMetadataService).deleteAspect(urn.toString(), CONTAINER_ASPECT_NAME);
    verify(searchDocumentTransformer)
        .transformAspect(
            eq(operationContext),
            eq(urn),
            nullable(RecordTemplate.class),
            eq(aspectSpec),
            eq(true),
            eq(event.getCreated()));
    verify(updateGraphIndicesService).handleChangeEvent(operationContext, event);
  }

  @Test(expectedExceptions = RuntimeException.class)
  public void testHandleChangeEventWithNullUrn() throws Exception {
    // Create test data with no URN and no entity key
    MetadataChangeLog event = new MetadataChangeLog();
    event.setChangeType(ChangeType.UPSERT);
    event.setAspectName(CONTAINER_ASPECT_NAME);
    event.setEntityType(DATASET_ENTITY_NAME);
    event.setSystemMetadata(SystemMetadataUtils.createDefaultSystemMetadata());
    event.setCreated(AuditStampUtils.createDefaultAuditStamp());

    // This should throw a RuntimeException when trying to process the event
    updateIndicesService.handleChangeEvent(operationContext, event);
  }

  @Test
  public void testHandleUpsertEvent() throws Exception {
    Urn urn = UrnUtils.getUrn("urn:li:dataset:(urn:li:dataPlatform:hdfs,SampleHdfsDataset,PROD)");
    EntitySpec entitySpec = operationContext.getEntityRegistry().getEntitySpec(DATASET_ENTITY_NAME);
    AspectSpec aspectSpec = entitySpec.getAspectSpec(CONTAINER_ASPECT_NAME);

    Container container = new Container();
    ObjectNode searchDocument = objectMapper.createObjectNode();
    searchDocument.put("urn", urn.toString());
    searchDocument.put("aspect", CONTAINER_ASPECT_NAME);


    // Create test data
    MetadataChangeLog event = new MetadataChangeLog();
    event.setChangeType(ChangeType.UPSERT);
    event.setEntityUrn(urn);
    event.setAspectName(CONTAINER_ASPECT_NAME);
    event.setEntityType(urn.getEntityType());
    event.setAspect(GenericRecordUtils.serializeAspect(container));
    event.setSystemMetadata(SystemMetadataUtils.createDefaultSystemMetadata());
    event.setCreated(AuditStampUtils.createDefaultAuditStamp());

    // Mock search document transformer
    when(searchDocumentTransformer.transformAspect(
        eq(operationContext), eq(urn), any(RecordTemplate.class), eq(aspectSpec), eq(false), any(AuditStamp.class)))
        .thenReturn(Optional.of(searchDocument));

    // Execute
    updateIndicesService.handleChangeEvent(operationContext, event);

    // Verify search service was called
    verify(entitySearchService).upsertDocument(eq(operationContext), eq(DATASET_ENTITY_NAME), anyString(), anyString());
    verify(systemMetadataService).insert(any(SystemMetadata.class), eq(urn.toString()), eq(CONTAINER_ASPECT_NAME));
    verify(updateGraphIndicesService).handleChangeEvent(operationContext, event);
  }

  @Test
  public void testHandleCreateEventWithTimeseries() throws Exception {
    Urn urn = UrnUtils.getUrn("urn:li:dataset:(urn:li:dataPlatform:hdfs,SampleHdfsDataset,PROD)");
    EntitySpec entitySpec = operationContext.getEntityRegistry().getEntitySpec(DATASET_ENTITY_NAME);

    // Use a timeseries aspect - let's assume we have one
    AspectSpec timeseriesAspectSpec = mock(AspectSpec.class);
    when(timeseriesAspectSpec.isTimeseries()).thenReturn(true);
    when(timeseriesAspectSpec.getName()).thenReturn("datasetProfile");
    when(entitySpec.getAspectSpec(eq("datasetProfile"))).thenReturn(timeseriesAspectSpec);

    GenericAspect timeseriesAspect = mock(GenericAspect.class);

    MetadataChangeLog event = new MetadataChangeLog();
    event.setChangeType(ChangeType.CREATE);
    event.setEntityUrn(urn);
    event.setAspectName("datasetProfile");
    event.setEntityType(urn.getEntityType());
    event.setAspect(timeseriesAspect);
    event.setSystemMetadata(SystemMetadataUtils.createDefaultSystemMetadata());
    event.setCreated(AuditStampUtils.createDefaultAuditStamp());

    // Mock timeseries transformation
    JsonNode timeseriesDoc = objectMapper.createObjectNode();
    Map<String, JsonNode> timeseriesDocuments = Map.of("doc1", timeseriesDoc);

    try (MockedStatic<com.linkedin.metadata.timeseries.transformer.TimeseriesAspectTransformer> mockedTransformer =
        mockStatic(com.linkedin.metadata.timeseries.transformer.TimeseriesAspectTransformer.class)) {

      mockedTransformer.when(() -> com.linkedin.metadata.timeseries.transformer.TimeseriesAspectTransformer.transform(
              eq(urn), any(RecordTemplate.class), eq(timeseriesAspectSpec), any(SystemMetadata.class), eq("MD5")))
          .thenReturn(timeseriesDocuments);

      // Execute
      updateIndicesService.handleChangeEvent(operationContext, event);

      // Verify timeseries service was called
      verify(timeseriesAspectService).upsertDocument(
          eq(operationContext), eq(urn.getEntityType()), eq("datasetProfile"), eq("doc1"), eq(timeseriesDoc));
    }
  }

  @Test
  public void testHandleDeleteKeyAspectEvent() throws Exception {
    Urn urn = UrnUtils.getUrn("urn:li:dataset:(urn:li:dataPlatform:hdfs,SampleHdfsDataset,PROD)");
    EntitySpec entitySpec = operationContext.getEntityRegistry().getEntitySpec(DATASET_ENTITY_NAME);
    String keyAspectName = entitySpec.getKeyAspectName();

    MetadataChangeLog event = new MetadataChangeLog();
    event.setChangeType(ChangeType.DELETE);
    event.setEntityUrn(urn);
    event.setAspectName(keyAspectName);
    event.setEntityType(urn.getEntityType());
    event.setSystemMetadata(SystemMetadataUtils.createDefaultSystemMetadata());
    event.setCreated(AuditStampUtils.createDefaultAuditStamp());

    // Execute
    updateIndicesService.handleChangeEvent(operationContext, event);

    // Verify entire entity is deleted
    verify(systemMetadataService).deleteUrn(urn.toString());
    verify(entitySearchService).deleteDocument(eq(operationContext), eq(DATASET_ENTITY_NAME), anyString());
    verify(updateGraphIndicesService).handleChangeEvent(operationContext, event);
  }

  @Test
  public void testHandleStatusAspectUpdate() throws Exception {
    Urn urn = UrnUtils.getUrn("urn:li:dataset:(urn:li:dataPlatform:hdfs,SampleHdfsDataset,PROD)");
    EntitySpec entitySpec = operationContext.getEntityRegistry().getEntitySpec(DATASET_ENTITY_NAME);

    Status status = new Status();
    status.setRemoved(true);

    MetadataChangeLog event = new MetadataChangeLog();
    event.setChangeType(ChangeType.UPSERT);
    event.setEntityUrn(urn);
    event.setAspectName(STATUS_ASPECT_NAME);
    event.setEntityType(urn.getEntityType());
    event.setAspect(GenericRecordUtils.serializeAspect(status));
    event.setSystemMetadata(SystemMetadataUtils.createDefaultSystemMetadata());
    event.setCreated(AuditStampUtils.createDefaultAuditStamp());

    // Execute
    updateIndicesService.handleChangeEvent(operationContext, event);

    // Verify status aspect handling
    verify(systemMetadataService).insert(any(SystemMetadata.class), eq(urn.toString()), eq(STATUS_ASPECT_NAME));
    verify(systemMetadataService).setDocStatus(eq(urn.toString()), eq(true));
  }

  @Test
  public void testHandleStructuredPropertyEvent() throws Exception {
    Urn structuredPropertyUrn = UrnUtils.getUrn("urn:li:structuredProperty:testProperty");
    EntitySpec entitySpec = operationContext.getEntityRegistry().getEntitySpec(STRUCTURED_PROPERTY_ENTITY_NAME);
    AspectSpec aspectSpec = entitySpec.getAspectSpec(STRUCTURED_PROPERTY_DEFINITION_ASPECT_NAME);

    StructuredPropertyDefinition definition = new StructuredPropertyDefinition();
    UrnArray entityTypes = new UrnArray();
    entityTypes.add(UrnUtils.getUrn("urn:li:entityType:dataset"));
    definition.setEntityTypes(entityTypes);

    MetadataChangeLog event = new MetadataChangeLog();
    event.setChangeType(ChangeType.CREATE);
    event.setEntityUrn(structuredPropertyUrn);
    event.setAspectName(STRUCTURED_PROPERTY_DEFINITION_ASPECT_NAME);
    event.setEntityType(structuredPropertyUrn.getEntityType());
    event.setAspect(GenericRecordUtils.serializeAspect(definition));
    event.setSystemMetadata(SystemMetadataUtils.createDefaultSystemMetadata());
    event.setCreated(AuditStampUtils.createDefaultAuditStamp());

    // Execute
    updateIndicesService.handleChangeEvent(operationContext, event);

    // Verify structured property handling
    verify(entitySearchService).buildReindexConfigsWithNewStructProp(eq(structuredPropertyUrn), any(StructuredPropertyDefinition.class));
  }

  @Test
  public void testSearchDiffModeSkipsNoChanges() throws Exception {
    Urn urn = UrnUtils.getUrn("urn:li:dataset:(urn:li:dataPlatform:hdfs,SampleHdfsDataset,PROD)");
    EntitySpec entitySpec = operationContext.getEntityRegistry().getEntitySpec(DATASET_ENTITY_NAME);
    AspectSpec aspectSpec = entitySpec.getAspectSpec(CONTAINER_ASPECT_NAME);

    Container container = new Container();
    Container previousContainer = new Container(); // Same as current
    ObjectNode searchDocument = objectMapper.createObjectNode();
    searchDocument.put("urn", urn.toString());

    // Create service with search diff mode enabled
    UpdateIndicesService diffModeService = new UpdateIndicesService(
        updateGraphIndicesService,
        entitySearchService,
        timeseriesAspectService,
        systemMetadataService,
        searchDocumentTransformer,
        "MD5",
        true, // searchDiffMode enabled
        true,
        true);

    MetadataChangeLog event = new MetadataChangeLog();
    event.setChangeType(ChangeType.UPSERT);
    event.setEntityUrn(urn);
    event.setAspectName(CONTAINER_ASPECT_NAME);
    event.setEntityType(urn.getEntityType());
    event.setAspect(GenericRecordUtils.serializeAspect(container));
    event.setPreviousAspectValue(GenericRecordUtils.serializeAspect(previousContainer));
    event.setSystemMetadata(SystemMetadataUtils.createDefaultSystemMetadata());
    event.setCreated(AuditStampUtils.createDefaultAuditStamp());

    // Mock transformer to return same document for both current and previous
    when(searchDocumentTransformer.transformAspect(
        eq(operationContext), eq(urn), any(RecordTemplate.class), eq(aspectSpec), eq(false), any(AuditStamp.class)))
        .thenReturn(Optional.of(searchDocument));

    // Execute
    diffModeService.handleChangeEvent(operationContext, event);

    // Verify no upsert happened due to no changes detected
    verify(entitySearchService, never()).upsertDocument(any(), any(), any(), any());
  }

  @Test
  public void testSearchDocumentTransformFailure() throws Exception {
    Urn urn = UrnUtils.getUrn("urn:li:dataset:(urn:li:dataPlatform:hdfs,SampleHdfsDataset,PROD)");
    EntitySpec entitySpec = operationContext.getEntityRegistry().getEntitySpec(DATASET_ENTITY_NAME);
    AspectSpec aspectSpec = entitySpec.getAspectSpec(CONTAINER_ASPECT_NAME);

    Container container = new Container();

    MetadataChangeLog event = new MetadataChangeLog();
    event.setChangeType(ChangeType.UPSERT);
    event.setEntityUrn(urn);
    event.setAspectName(CONTAINER_ASPECT_NAME);
    event.setEntityType(urn.getEntityType());
    event.setAspect(GenericRecordUtils.serializeAspect(container));
    event.setSystemMetadata(SystemMetadataUtils.createDefaultSystemMetadata());
    event.setCreated(AuditStampUtils.createDefaultAuditStamp());

    // Mock transformer to throw exception
    when(searchDocumentTransformer.transformAspect(
        eq(operationContext), eq(urn), any(RecordTemplate.class), eq(aspectSpec), eq(false), any(AuditStamp.class)))
        .thenThrow(new RuntimeException("Transformation failed"));

    // Execute - should not throw exception but handle gracefully
    updateIndicesService.handleChangeEvent(operationContext, event);

    // Verify no upsert happened due to transformation failure
    verify(entitySearchService, never()).upsertDocument(any(), any(), any(), any());
  }

  @Test
  public void testHandleEventWithForceIndexingFlag() throws Exception {
    Urn urn = UrnUtils.getUrn("urn:li:dataset:(urn:li:dataPlatform:hdfs,SampleHdfsDataset,PROD)");
    EntitySpec entitySpec = operationContext.getEntityRegistry().getEntitySpec(DATASET_ENTITY_NAME);
    AspectSpec aspectSpec = entitySpec.getAspectSpec(CONTAINER_ASPECT_NAME);

    Container container = new Container();
    ObjectNode searchDocument = objectMapper.createObjectNode();
    searchDocument.put("urn", urn.toString());

    // Create system metadata with FORCE_INDEXING_KEY
    SystemMetadata systemMetadata = SystemMetadataUtils.createDefaultSystemMetadata();
    StringMap stringMap = new StringMap();
    stringMap.put(FORCE_INDEXING_KEY, "true");
    systemMetadata.setProperties(stringMap);

    MetadataChangeLog event = new MetadataChangeLog();
    event.setChangeType(ChangeType.UPSERT);
    event.setEntityUrn(urn);
    event.setAspectName(CONTAINER_ASPECT_NAME);
    event.setEntityType(urn.getEntityType());
    event.setAspect(GenericRecordUtils.serializeAspect(container));
    event.setSystemMetadata(systemMetadata);
    event.setCreated(AuditStampUtils.createDefaultAuditStamp());

    when(searchDocumentTransformer.transformAspect(
        eq(operationContext), eq(urn), any(RecordTemplate.class), eq(aspectSpec), eq(false), any(AuditStamp.class)))
        .thenReturn(Optional.of(searchDocument));

    // Execute
    updateIndicesService.handleChangeEvent(operationContext, event);

    // Verify search service was called (force indexing bypasses diff mode)
    verify(entitySearchService).upsertDocument(eq(operationContext), eq(DATASET_ENTITY_NAME), anyString(), anyString());
  }

  @Test
  public void testHandleEventWithEmptySearchDocument() throws Exception {
    Urn urn = UrnUtils.getUrn("urn:li:dataset:(urn:li:dataPlatform:hdfs,SampleHdfsDataset,PROD)");
    EntitySpec entitySpec = operationContext.getEntityRegistry().getEntitySpec(DATASET_ENTITY_NAME);
    AspectSpec aspectSpec = entitySpec.getAspectSpec(CONTAINER_ASPECT_NAME);

    EditableDatasetProperties editableDatasetProperties = new EditableDatasetProperties();

    MetadataChangeLog event = new MetadataChangeLog();
    event.setChangeType(ChangeType.UPSERT);
    event.setEntityUrn(urn);
    event.setAspectName(CONTAINER_ASPECT_NAME);
    event.setEntityType(urn.getEntityType());
    event.setAspect(GenericRecordUtils.serializeAspect(editableDatasetProperties));
    event.setSystemMetadata(SystemMetadataUtils.createDefaultSystemMetadata());
    event.setCreated(AuditStampUtils.createDefaultAuditStamp());

    // Mock transformer to return empty document
    when(searchDocumentTransformer.transformAspect(
        eq(operationContext), eq(urn), any(RecordTemplate.class), eq(aspectSpec), eq(false), any(AuditStamp.class)))
        .thenReturn(Optional.empty());

    // Execute
    updateIndicesService.handleChangeEvent(operationContext, event);

    // Verify no upsert happened due to empty search document
    verify(entitySearchService, never()).upsertDocument(any(), any(), any(), any());
  }
}