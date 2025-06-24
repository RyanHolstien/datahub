package com.linkedin.metadata.search.transformer;

import static com.linkedin.metadata.Constants.*;
import static org.mockito.Mockito.*;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import com.datahub.test.TestEntitySnapshot;
import com.datahub.test.TestEntityAspect;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.JsonNodeType;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.base.CaseFormat;
import com.linkedin.common.urn.Urn;
import com.linkedin.common.urn.UrnUtils;
import com.linkedin.data.DataMapBuilder;
import com.linkedin.data.template.RecordTemplate;
import com.linkedin.dataset.DatasetProperties;
import com.linkedin.dataset.EditableDatasetProperties;
import com.linkedin.entity.Aspect;
import com.linkedin.metadata.TestEntitySpecBuilder;
import com.linkedin.metadata.TestEntityUtil;
import com.linkedin.metadata.aspect.AspectRetriever;
import com.linkedin.metadata.aspect.GraphRetriever;
import com.linkedin.metadata.entity.SearchRetriever;
import com.linkedin.metadata.models.EntitySpec;
import com.linkedin.metadata.models.AspectSpec;
import com.linkedin.metadata.models.SearchableRefFieldSpec;
import com.linkedin.metadata.models.registry.ConfigEntityRegistry;
import com.linkedin.metadata.models.registry.EntityRegistry;
import com.linkedin.metadata.search.query.request.TestSearchFieldConfig;
import com.linkedin.metadata.utils.AuditStampUtils;
import com.linkedin.r2.RemoteInvocationException;
import io.datahubproject.metadata.context.OperationContext;
import io.datahubproject.metadata.context.RetrieverContext;
import io.datahubproject.test.metadata.context.TestOperationContexts;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.*;
import org.mockito.Mockito;
import org.testng.annotations.Test;

public class SearchDocumentTransformerTest {
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private static final EntityRegistry ENTITY_REGISTRY =
      TestOperationContexts.defaultEntityRegistry();
  private static final EntityRegistry TEST_ENTITY_REGISTRY;

  static {
    TEST_ENTITY_REGISTRY =
        new ConfigEntityRegistry(
            TestSearchFieldConfig.class
                .getClassLoader()
                .getResourceAsStream("test-entity-registry.yaml"));
  }

  static {
    int maxSize =
        Integer.parseInt(
            System.getenv()
                .getOrDefault(INGESTION_MAX_SERIALIZED_STRING_LENGTH, MAX_JACKSON_STRING_SIZE));
    OBJECT_MAPPER
        .getFactory()
        .setStreamReadConstraints(StreamReadConstraints.builder().maxStringLength(maxSize).build());
  }

  @Test
  public void testTransform() throws IOException, RemoteInvocationException, URISyntaxException {
    SearchDocumentTransformer searchDocumentTransformer =
        new SearchDocumentTransformer(1000, 1000, 1000);
    TestEntitySnapshot snapshot = TestEntityUtil.getSnapshot();
    EntitySpec testEntitySpec = TestEntitySpecBuilder.getSpec();
    OperationContext opContext = TestOperationContexts.systemContextNoSearchAuthorization();

    // Transform each aspect individually and merge results
    ObjectNode mergedResult = JsonNodeFactory.instance.objectNode();

    for (TestEntityAspect aspect : snapshot.getAspects()) {
      RecordTemplate aspectRecord = getAspectValue(aspect);
      String aspectName = CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_CAMEL, aspectRecord.schema().getName());
      AspectSpec aspectSpec = testEntitySpec.getAspectSpec(aspectName);

      if (aspectSpec != null) {
        Optional<ObjectNode> aspectResult = searchDocumentTransformer.transformAspect(
            opContext, snapshot.getUrn(), aspectRecord, aspectSpec, false, AuditStampUtils.createDefaultAuditStamp());

        // Merge the aspect result into the main document
        aspectResult.ifPresent(jsonNodes -> mergeObjectNodes(mergedResult, jsonNodes));
      }
    }

    assertFalse(mergedResult.isEmpty());

    assertEquals(mergedResult.get("urn").asText(), snapshot.getUrn().toString());
    assertEquals(mergedResult.get("doubleField").asDouble(), 100.456);
    assertEquals(mergedResult.get("keyPart1").asText(), "key");
    assertFalse(mergedResult.has("keyPart2"));
    assertEquals(mergedResult.get("keyPart3").asText(), "VALUE_1");
    assertFalse(mergedResult.has("textField"));
    assertEquals(mergedResult.get("textFieldOverride").asText(), "test");
    ArrayNode textArrayField = (ArrayNode) mergedResult.get("textArrayField");
    assertEquals(textArrayField.size(), 2);
    assertEquals(textArrayField.get(0).asText(), "testArray1");
    assertEquals(textArrayField.get(1).asText(), "testArray2");
    assertEquals(mergedResult.get("nestedIntegerField").asInt(), 1);
    assertEquals(mergedResult.get("nestedForeignKey").asText(), snapshot.getUrn().toString());
    ArrayNode nextedArrayField = (ArrayNode) mergedResult.get("nestedArrayStringField");
    assertEquals(nextedArrayField.size(), 2);
    assertEquals(nextedArrayField.get(0).asText(), "nestedArray1");
    assertEquals(nextedArrayField.get(1).asText(), "nestedArray2");
    ArrayNode browsePaths = (ArrayNode) mergedResult.get("browsePaths");
    assertEquals(browsePaths.size(), 2);
    assertEquals(browsePaths.get(0).asText(), "/a/b/c");
    assertEquals(browsePaths.get(1).asText(), "d/e/f");
    assertEquals(mergedResult.get("feature1").asInt(), 2);
    assertEquals(mergedResult.get("feature2").asInt(), 1);
    JsonNode browsePathV2 = (JsonNode) mergedResult.get("browsePathV2");
    assertEquals(browsePathV2.asText(), "␟levelOne␟levelTwo");
    assertEquals(
        mergedResult.get("esObjectFieldBoolean").get("key1").getNodeType(),
        JsonNodeFactory.instance.booleanNode(true).getNodeType());
    assertEquals(
        mergedResult.get("esObjectFieldLong").get("key1").getNodeType(),
        JsonNodeFactory.instance.numberNode(1L).getNodeType());
    assertEquals(
        mergedResult.get("esObjectFieldFloat").get("key2").getNodeType(),
        JsonNodeFactory.instance.numberNode(2.0f).getNodeType());
    assertEquals(
        mergedResult.get("esObjectFieldDouble").get("key1").getNodeType(),
        JsonNodeFactory.instance.numberNode(1.2).getNodeType());
    assertEquals(
        mergedResult.get("esObjectFieldInteger").get("key2").getNodeType(),
        JsonNodeFactory.instance.numberNode(456).getNodeType());
    assertEquals(
        mergedResult.get("esObjectFieldBoolean").get("key2").getNodeType(),
        JsonNodeFactory.instance.booleanNode(false).getNodeType());
    assertEquals(
        mergedResult.get("esObjectFieldLong").get("key2").getNodeType(),
        JsonNodeFactory.instance.numberNode(2L).getNodeType());
    assertEquals(
        mergedResult.get("esObjectFieldFloat").get("key1").getNodeType(),
        JsonNodeFactory.instance.numberNode(1.0f).getNodeType());
    assertEquals(
        mergedResult.get("esObjectFieldDouble").get("key2").getNodeType(),
        JsonNodeFactory.instance.numberNode(2.4).getNodeType());
    assertEquals(
        mergedResult.get("esObjectFieldInteger").get("key1").getNodeType(),
        JsonNodeFactory.instance.numberNode(123).getNodeType());
    assertEquals(mergedResult.get("esObjectField").get("key3").asText(), "");
    assertEquals(
        mergedResult.get("esObjectFieldBoolean").get("key2").getNodeType(),
        JsonNodeFactory.instance.booleanNode(false).getNodeType());
    assertEquals(
        mergedResult.get("esObjectFieldLong").get("key2").getNodeType(),
        JsonNodeFactory.instance.numberNode(2L).getNodeType());
    assertEquals(
        mergedResult.get("esObjectFieldFloat").get("key1").getNodeType(),
        JsonNodeFactory.instance.numberNode(1.0f).getNodeType());
    assertEquals(
        mergedResult.get("esObjectFieldDouble").get("key2").getNodeType(),
        JsonNodeFactory.instance.numberNode(2.4).getNodeType());
    assertEquals(
        mergedResult.get("esObjectFieldInteger").get("key1").getNodeType(),
        JsonNodeFactory.instance.numberNode(123).getNodeType());
  }

  @Test
  public void testTransformForDelete() throws IOException, RemoteInvocationException, URISyntaxException {
    SearchDocumentTransformer searchDocumentTransformer =
        new SearchDocumentTransformer(1000, 1000, 1000);
    TestEntitySnapshot snapshot = TestEntityUtil.getSnapshot();
    EntitySpec testEntitySpec = TestEntitySpecBuilder.getSpec();
    OperationContext opContext = TestOperationContexts.systemContextNoSearchAuthorization();

    // Transform each aspect individually with forDelete=true and merge results
    ObjectNode mergedResult = JsonNodeFactory.instance.objectNode();

    for (TestEntityAspect aspect : snapshot.getAspects()) {
      RecordTemplate aspectRecord = getAspectValue(aspect);
      String aspectName = CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_CAMEL, aspectRecord.schema().getName());
      AspectSpec aspectSpec = testEntitySpec.getAspectSpec(aspectName);

      if (aspectSpec != null) {
        Optional<ObjectNode> aspectResult = searchDocumentTransformer.transformAspect(
            opContext, snapshot.getUrn(), aspectRecord, aspectSpec, true, AuditStampUtils.createDefaultAuditStamp());

        // Merge the aspect result into the main document
        aspectResult.ifPresent(jsonNodes -> mergeObjectNodes(mergedResult, jsonNodes));
      }
    }

    assertFalse(mergedResult.isEmpty());

    assertEquals(mergedResult.get("urn").asText(), snapshot.getUrn().toString());
    assertEquals(JsonNodeType.NULL, mergedResult.get("keyPart1").getNodeType());
    assertEquals(JsonNodeType.NULL, mergedResult.get("keyPart3").getNodeType());
    assertEquals(JsonNodeType.NULL, mergedResult.get("textFieldOverride").getNodeType());
    assertEquals(JsonNodeType.NULL, mergedResult.get("nestedForeignKey").getNodeType());
    assertEquals(JsonNodeType.NULL, mergedResult.get("textArrayField").getNodeType());
    assertEquals(JsonNodeType.NULL, mergedResult.get("browsePaths").getNodeType());
    assertEquals(JsonNodeType.NULL, mergedResult.get("nestedArrayStringField").getNodeType());
    assertEquals(JsonNodeType.NULL, mergedResult.get("nestedIntegerField").getNodeType());
    assertEquals(JsonNodeType.NULL, mergedResult.get("feature1").getNodeType());
    assertEquals(JsonNodeType.NULL, mergedResult.get("feature2").getNodeType());
    assertEquals(JsonNodeType.NULL, mergedResult.get("doubleField").getNodeType());
  }

  @Test
  public void testTransformMaxFieldValue() throws IOException, RemoteInvocationException, URISyntaxException {
    SearchDocumentTransformer searchDocumentTransformer =
        new SearchDocumentTransformer(1000, 1000, 5);
    TestEntitySnapshot snapshot = TestEntityUtil.getSnapshot();
    EntitySpec testEntitySpec = TestEntitySpecBuilder.getSpec();
    OperationContext opContext = TestOperationContexts.systemContextNoSearchAuthorization();

    // Transform each aspect individually and merge results
    ObjectNode mergedResult = JsonNodeFactory.instance.objectNode();

    for (TestEntityAspect aspect : snapshot.getAspects()) {
      RecordTemplate aspectRecord = getAspectValue(aspect);
      String aspectName = CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_CAMEL, aspectRecord.schema().getName());
      AspectSpec aspectSpec = testEntitySpec.getAspectSpec(aspectName);

      if (aspectSpec != null) {
        Optional<ObjectNode> aspectResult = searchDocumentTransformer.transformAspect(
            opContext, snapshot.getUrn(), aspectRecord, aspectSpec, false, AuditStampUtils.createDefaultAuditStamp());

        if (aspectResult.isPresent()) {
          // Merge the aspect result into the main document
          mergeObjectNodes(mergedResult, aspectResult.get());
        }
      }
    }

    assertTrue(mergedResult.size() > 0);
    ObjectNode parsedJson = mergedResult;

    assertEquals(
        parsedJson.get("customProperties"),
        JsonNodeFactory.instance.arrayNode().add("shortValue=123"));
    assertEquals(
        parsedJson.get("esObjectField"), JsonNodeFactory.instance.arrayNode().add("123").add(""));

    // Test with higher max value length
    searchDocumentTransformer = new SearchDocumentTransformer(1000, 1000, 20);
    snapshot = TestEntityUtil.getSnapshot();
    testEntitySpec = TestEntitySpecBuilder.getSpec();

    mergedResult = JsonNodeFactory.instance.objectNode();

    for (TestEntityAspect aspect : snapshot.getAspects()) {
      RecordTemplate aspectRecord = getAspectValue(aspect);
      String aspectName = CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_CAMEL, aspectRecord.schema().getName());
      AspectSpec aspectSpec = testEntitySpec.getAspectSpec(aspectName);

      if (aspectSpec != null) {
        Optional<ObjectNode> aspectResult = searchDocumentTransformer.transformAspect(
            opContext, snapshot.getUrn(), aspectRecord, aspectSpec, false, AuditStampUtils.createDefaultAuditStamp());

        if (aspectResult.isPresent()) {
          // Merge the aspect result into the main document
          mergeObjectNodes(mergedResult, aspectResult.get());
        }
      }
    }

    assertTrue(mergedResult.size() > 0);
    parsedJson = mergedResult;

    assertEquals(
        parsedJson.get("customProperties"),
        JsonNodeFactory.instance
            .arrayNode()
            .add("key1=value1")
            .add("key2=value2")
            .add("shortValue=123")
            .add("longValue=0123456789"));
    assertEquals(
        parsedJson.get("esObjectField"),
        JsonNodeFactory.instance
            .arrayNode()
            .add("value1")
            .add("value2")
            .add("123")
            .add("")
            .add("0123456789"));
  }

  private RecordTemplate getAspectValue(TestEntityAspect aspect) {
    RecordTemplate aspectRecord;
    if (aspect.isSearchFeatures()) {
      aspectRecord = aspect.getSearchFeatures();
    } else if (aspect.isTestBrowsePaths()) {
      aspectRecord = aspect.getTestBrowsePaths();
    } else if (aspect.isTestEntityInfo()) {
      aspectRecord = aspect.getTestEntityInfo();
    } else if (aspect.isTestEntityKey()) {
      aspectRecord = aspect.getTestEntityKey();
    } else if (aspect.isTestBrowsePathsV2()) {
      aspectRecord = aspect.getTestBrowsePathsV2();
    } else {
      throw new RuntimeException();
    }
    return aspectRecord;
  }

  /**
   * Helper method to merge two ObjectNodes
   */
  private void mergeObjectNodes(ObjectNode target, ObjectNode source) {
    source.fields().forEachRemaining(entry -> {
      String key = entry.getKey();
      JsonNode value = entry.getValue();

      if (target.has(key)) {
        // If both nodes have the same key, we need to merge them
        JsonNode targetValue = target.get(key);
        if (targetValue.isObject() && value.isObject()) {
          mergeObjectNodes((ObjectNode) targetValue, (ObjectNode) value);
        } else {
          // For non-object values, the source value overwrites the target
          target.set(key, value);
        }
      } else {
        target.set(key, value);
      }
    });
  }

  /**
   *
   *
   * <ul>
   *   <li>{@link SearchDocumentTransformer#setSearchableRefValue(OperationContext,
   *       SearchableRefFieldSpec, List, ObjectNode, Boolean, com.linkedin.common.AuditStamp ) }
   * </ul>
   */
  @Test
  public void testSetSearchableRefValue() throws URISyntaxException, RemoteInvocationException {
    AspectRetriever aspectRetriever = Mockito.mock(AspectRetriever.class);
    SearchDocumentTransformer searchDocumentTransformer =
        new SearchDocumentTransformer(1000, 1000, 1000);

    List<Object> urnList = List.of(Urn.createFromString("urn:li:refEntity:1"));

    DataMapBuilder dataMapBuilder = new DataMapBuilder();
    dataMapBuilder.addKVPair("fieldPath", "refEntityUrn");
    dataMapBuilder.addKVPair("name", "refEntityUrnName");
    dataMapBuilder.addKVPair("description", "refEntityUrn1 description details");
    Aspect aspect = new Aspect(dataMapBuilder.convertToDataMap());

    ObjectNode searchDocument = JsonNodeFactory.instance.objectNode();
    SearchableRefFieldSpec searchableRefFieldSpec =
        TEST_ENTITY_REGISTRY.getEntitySpec("testRefEntity").getSearchableRefFieldSpecs().get(0);

    // Mock Behaviour
    Mockito.when(aspectRetriever.getEntityRegistry()).thenReturn(TEST_ENTITY_REGISTRY);
    Mockito.when(aspectRetriever.getLatestAspectObject(any(), anyString())).thenReturn(aspect);
    OperationContext opContext =
        TestOperationContexts.systemContextNoSearchAuthorization(
            RetrieverContext.builder()
                .aspectRetriever(aspectRetriever)
                .cachingAspectRetriever(
                    TestOperationContexts.emptyActiveUsersAspectRetriever(
                        () -> TEST_ENTITY_REGISTRY))
                .graphRetriever(mock(GraphRetriever.class))
                .searchRetriever(mock(SearchRetriever.class))
                .build());

    searchDocumentTransformer.setSearchableRefValue(
        opContext,
        searchableRefFieldSpec,
        urnList,
        searchDocument,
        false,
        AuditStampUtils.createDefaultAuditStamp());
    assertTrue(searchDocument.has("refEntityUrns"));
    assertEquals(searchDocument.get("refEntityUrns").size(), 3);
    assertTrue(searchDocument.get("refEntityUrns").has("urn"));
    assertTrue(searchDocument.get("refEntityUrns").has("editedFieldDescriptions"));
    assertTrue(searchDocument.get("refEntityUrns").has("displayName"));
    assertEquals(searchDocument.get("refEntityUrns").get("urn").asText(), "urn:li:refEntity:1");
    assertEquals(
        searchDocument.get("refEntityUrns").get("editedFieldDescriptions").asText(),
        "refEntityUrn1 description details");
    assertEquals(
        searchDocument.get("refEntityUrns").get("displayName").asText(), "refEntityUrnName");
  }

  @Test
  public void testSetSearchableRefValue_WithNonURNField() throws URISyntaxException {
    SearchDocumentTransformer searchDocumentTransformer =
        new SearchDocumentTransformer(1000, 1000, 1000);

    OperationContext opContext =
        TestOperationContexts.systemContextNoSearchAuthorization(TEST_ENTITY_REGISTRY);
    List<Object> urnList = List.of(Urn.createFromString("urn:li:refEntity:1"));

    ObjectNode searchDocument = JsonNodeFactory.instance.objectNode();
    SearchableRefFieldSpec searchableRefFieldSpecText =
        TEST_ENTITY_REGISTRY.getEntitySpec("testRefEntity").getSearchableRefFieldSpecs().get(1);
    searchDocumentTransformer.setSearchableRefValue(
        opContext,
        searchableRefFieldSpecText,
        urnList,
        searchDocument,
        false,
        AuditStampUtils.createDefaultAuditStamp());
    assertTrue(searchDocument.isEmpty());
  }

  @Test
  public void testSetSearchableRefValue_RuntimeException()
      throws URISyntaxException, RemoteInvocationException {
    AspectRetriever aspectRetriever = Mockito.mock(AspectRetriever.class);
    SearchDocumentTransformer searchDocumentTransformer =
        new SearchDocumentTransformer(1000, 1000, 1000);

    List<Object> urnList = List.of(Urn.createFromString("urn:li:refEntity:1"));

    Mockito.when(aspectRetriever.getEntityRegistry()).thenReturn(TEST_ENTITY_REGISTRY);
    Mockito.when(
            aspectRetriever.getLatestAspectObject(
                eq(Urn.createFromString("urn:li:refEntity:1")), anyString()))
        .thenThrow(new RuntimeException("Error"));
    OperationContext opContext =
        TestOperationContexts.systemContextNoSearchAuthorization(
            RetrieverContext.builder()
                .aspectRetriever(aspectRetriever)
                .cachingAspectRetriever(
                    TestOperationContexts.emptyActiveUsersAspectRetriever(
                        () -> TEST_ENTITY_REGISTRY))
                .graphRetriever(mock(GraphRetriever.class))
                .searchRetriever(mock(SearchRetriever.class))
                .build());

    ObjectNode searchDocument = JsonNodeFactory.instance.objectNode();
    SearchableRefFieldSpec searchableRefFieldSpec =
        TEST_ENTITY_REGISTRY.getEntitySpec("testRefEntity").getSearchableRefFieldSpecs().get(0);
    searchDocumentTransformer.setSearchableRefValue(
        opContext,
        searchableRefFieldSpec,
        urnList,
        searchDocument,
        false,
        AuditStampUtils.createDefaultAuditStamp());
    assertTrue(searchDocument.isEmpty());
  }

  @Test
  public void testSetSearchableRefValue_RuntimeException_URNExist()
      throws URISyntaxException, RemoteInvocationException {
    AspectRetriever aspectRetriever = Mockito.mock(AspectRetriever.class);
    SearchDocumentTransformer searchDocumentTransformer =
        new SearchDocumentTransformer(1000, 1000, 1000);

    List<Object> urnList = List.of(Urn.createFromString("urn:li:refEntity:1"));
    DataMapBuilder dataMapBuilder = new DataMapBuilder();
    dataMapBuilder.addKVPair("fieldPath", "refEntityUrn");
    dataMapBuilder.addKVPair("name", "refEntityUrnName");
    dataMapBuilder.addKVPair("description", "refEntityUrn1 description details");

    Aspect aspect = new Aspect(dataMapBuilder.convertToDataMap());
    Mockito.when(aspectRetriever.getEntityRegistry()).thenReturn(TEST_ENTITY_REGISTRY);
    Mockito.when(
            aspectRetriever.getLatestAspectObject(
                eq(Urn.createFromString("urn:li:refEntity:1")), anyString()))
        .thenReturn(aspect)
        .thenThrow(new RuntimeException("Error"));
    OperationContext opContext =
        TestOperationContexts.systemContextNoSearchAuthorization(
            RetrieverContext.builder()
                .aspectRetriever(aspectRetriever)
                .cachingAspectRetriever(
                    TestOperationContexts.emptyActiveUsersAspectRetriever(
                        () -> TEST_ENTITY_REGISTRY))
                .graphRetriever(mock(GraphRetriever.class))
                .searchRetriever(mock(SearchRetriever.class))
                .build());

    ObjectNode searchDocument = JsonNodeFactory.instance.objectNode();
    SearchableRefFieldSpec searchableRefFieldSpec =
        TEST_ENTITY_REGISTRY.getEntitySpec("testRefEntity").getSearchableRefFieldSpecs().get(0);
    searchDocumentTransformer.setSearchableRefValue(
        opContext,
        searchableRefFieldSpec,
        urnList,
        searchDocument,
        false,
        AuditStampUtils.createDefaultAuditStamp());
    assertTrue(searchDocument.has("refEntityUrns"));
    assertEquals(searchDocument.get("refEntityUrns").size(), 1);
    assertTrue(searchDocument.get("refEntityUrns").has("urn"));
    assertEquals(searchDocument.get("refEntityUrns").get("urn").asText(), "urn:li:refEntity:1");
  }

  @Test
  void testSetSearchableRefValue_WithInvalidURN()
      throws URISyntaxException, RemoteInvocationException {
    AspectRetriever aspectRetriever = Mockito.mock(AspectRetriever.class);
    SearchDocumentTransformer searchDocumentTransformer =
        new SearchDocumentTransformer(1000, 1000, 1000);

    List<Object> urnList = List.of(Urn.createFromString("urn:li:refEntity:1"));

    Mockito.when(aspectRetriever.getEntityRegistry()).thenReturn(TEST_ENTITY_REGISTRY);
    Mockito.when(aspectRetriever.getLatestAspectObject(any(), anyString())).thenReturn(null);
    SearchableRefFieldSpec searchableRefFieldSpec =
        TEST_ENTITY_REGISTRY.getEntitySpec("testRefEntity").getSearchableRefFieldSpecs().get(0);
    OperationContext opContext =
        TestOperationContexts.systemContextNoSearchAuthorization(
            RetrieverContext.builder()
                .aspectRetriever(aspectRetriever)
                .cachingAspectRetriever(
                    TestOperationContexts.emptyActiveUsersAspectRetriever(
                        () -> TEST_ENTITY_REGISTRY))
                .graphRetriever(mock(GraphRetriever.class))
                .searchRetriever(mock(SearchRetriever.class))
                .build());

    ObjectNode searchDocument = JsonNodeFactory.instance.objectNode();
    searchDocumentTransformer.setSearchableRefValue(
        opContext,
        searchableRefFieldSpec,
        urnList,
        searchDocument,
        false,
        AuditStampUtils.createDefaultAuditStamp());
    assertTrue(searchDocument.has("refEntityUrns"));
    assertEquals(JsonNodeType.NULL, searchDocument.get("refEntityUrns").getNodeType());
  }

  @Test
  public void testEmptyDescription() throws RemoteInvocationException, URISyntaxException {
    String entityUrn = "urn:li:dataset:(urn:li:dataPlatform:hive,fct_users_created,PROD)";
    SearchDocumentTransformer test = new SearchDocumentTransformer(1000, 1000, 1000);

    // editedDescription - empty string
    Optional<ObjectNode> transformed =
        test.transformAspect(
            mock(OperationContext.class),
            UrnUtils.getUrn(entityUrn),
            new EditableDatasetProperties().setDescription(""),
            ENTITY_REGISTRY
                .getEntitySpec(DATASET_ENTITY_NAME)
                .getAspectSpec(EDITABLE_DATASET_PROPERTIES_ASPECT_NAME),
            false,
            AuditStampUtils.createDefaultAuditStamp());

    assertTrue(transformed.isPresent());
    assertEquals(transformed.get().get("urn").asText(), entityUrn);
    assertTrue(transformed.get().has("editedDescription"));
    assertTrue(transformed.get().get("editedDescription").isNull());

    // description - empty string
    transformed =
        test.transformAspect(
            mock(OperationContext.class),
            UrnUtils.getUrn(entityUrn),
            new DatasetProperties().setDescription(""),
            ENTITY_REGISTRY
                .getEntitySpec(DATASET_ENTITY_NAME)
                .getAspectSpec(DATASET_PROPERTIES_ASPECT_NAME),
            false,
            AuditStampUtils.createDefaultAuditStamp());

    assertTrue(transformed.isPresent());
    assertEquals(transformed.get().get("urn").asText(), entityUrn);
    assertTrue(transformed.get().has("description"));
    assertTrue(transformed.get().get("description").isNull());
    assertFalse(transformed.get().get("hasDescription").asBoolean());
  }

  @Test
  public void testHandleRemoveFieldsWithStructuredProperties() throws IOException {
    ObjectNode previousDoc = JsonNodeFactory.instance.objectNode();
    previousDoc.put("structuredProperties.prop1", "value1");
    previousDoc.put("structuredProperties.prop2", "value2");
    previousDoc.put("otherField", "value3");

    ObjectNode newDoc = JsonNodeFactory.instance.objectNode();
    newDoc.put("structuredProperties.prop1", "updatedValue1");
    newDoc.put("otherField", "updatedValue3");

    ObjectNode result = SearchDocumentTransformer.handleRemoveFields(newDoc, previousDoc);

    assertEquals(result.get("structuredProperties.prop1").asText(), "updatedValue1");
    assertTrue(result.has("structuredProperties.prop2"));
    assertTrue(result.get("structuredProperties.prop2").isNull());
    assertEquals(result.get("otherField").asText(), "updatedValue3");
  }
}