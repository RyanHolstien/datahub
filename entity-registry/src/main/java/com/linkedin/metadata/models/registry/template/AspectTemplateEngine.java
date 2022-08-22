package com.linkedin.metadata.models.registry.template;

import com.datahub.util.RecordUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.fge.jsonpatch.JsonPatch;
import com.github.fge.jsonpatch.JsonPatchException;
import com.linkedin.data.template.RecordTemplate;
import com.linkedin.metadata.models.AspectSpec;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import static com.linkedin.metadata.Constants.*;


/**
 * Holds connection between aspect specs and their templates and drives the generation from templates
 */
public class AspectTemplateEngine {

  public static final Set<String> SUPPORTED_TEMPLATES = Stream.of(
      DATASET_PROPERTIES_ASPECT_NAME,
      EDITABLE_SCHEMA_METADATA_ASPECT_NAME,
      GLOBAL_TAGS_ASPECT_NAME,
      OWNERSHIP_ASPECT_NAME,
      UPSTREAM_LINEAGE_ASPECT_NAME).collect(Collectors.toSet());

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private final Map<String, Template<? extends RecordTemplate>> _aspectTemplateMap;

  public AspectTemplateEngine() {
    _aspectTemplateMap = new HashMap<>();
  }

  public AspectTemplateEngine(Map<String, Template<? extends RecordTemplate>> aspectTemplateMap) {
    _aspectTemplateMap = aspectTemplateMap;
  }

  @Nullable
  public RecordTemplate getDefaultTemplate(AspectSpec aspectSpec) {
    return _aspectTemplateMap.containsKey(aspectSpec) ? _aspectTemplateMap.get(aspectSpec).getDefault() : null;
  }

  /**
   * Applies a json patch to a record, optionally merging array fields as necessary
   * @param recordTemplate original template to be updated
   * @param jsonPatch patch to apply
   * @param aspectSpec aspectSpec of the template
   * @return a {@link RecordTemplate} with the patch applied
   * @throws JsonProcessingException if there is an issue with processing the record template's json
   * @throws JsonPatchException if there is an issue with applying the json patch
   */
  @Nonnull
  public <T extends RecordTemplate> RecordTemplate applyPatch(RecordTemplate recordTemplate, JsonPatch jsonPatch, AspectSpec aspectSpec)
      throws JsonProcessingException, JsonPatchException {
    JsonNode patchedNode = jsonPatch.apply(OBJECT_MAPPER.readTree(RecordUtils.toJsonString(recordTemplate)));
    RecordTemplate patchedTemplate = RecordUtils.toRecordTemplate(aspectSpec.getDataTemplateClass(), patchedNode.toString());

    Template<T> template = getTemplateOrDefault(aspectSpec);
    if (template instanceof ArrayMergingTemplate) {
      patchedTemplate = ((ArrayMergingTemplate<T>) template).mergeArrayFields(template.getSubtype(recordTemplate),
          template.getSubtype(patchedTemplate));
    }
    return patchedTemplate;
  }

  // Get around lack of generics on AspectSpec data template class
  private <T extends RecordTemplate> Template<T> getTemplateOrDefault(AspectSpec aspectSpec) {
    return (Template<T>) _aspectTemplateMap.getOrDefault(aspectSpec, null);
  }
}
