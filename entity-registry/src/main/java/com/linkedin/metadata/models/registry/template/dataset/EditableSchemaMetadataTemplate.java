package com.linkedin.metadata.models.registry.template.dataset;

import com.linkedin.common.AuditStamp;
import com.linkedin.common.GlobalTags;
import com.linkedin.common.GlossaryTermAssociation;
import com.linkedin.common.GlossaryTermAssociationArray;
import com.linkedin.common.GlossaryTerms;
import com.linkedin.common.TagAssociation;
import com.linkedin.common.TagAssociationArray;
import com.linkedin.common.urn.Urn;
import com.linkedin.common.urn.UrnUtils;
import com.linkedin.data.template.RecordTemplate;
import com.linkedin.metadata.models.registry.template.ArrayMergingTemplate;
import com.linkedin.schema.EditableSchemaFieldInfo;
import com.linkedin.schema.EditableSchemaFieldInfoArray;
import com.linkedin.schema.EditableSchemaMetadata;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;

import static com.linkedin.metadata.Constants.*;


public class EditableSchemaMetadataTemplate implements ArrayMergingTemplate<EditableSchemaMetadata> {
  @Override
  public EditableSchemaMetadata getSubtype(RecordTemplate recordTemplate) throws ClassCastException {
    if (recordTemplate instanceof EditableSchemaMetadata) {
      return (EditableSchemaMetadata) recordTemplate;
    }
    throw new ClassCastException("Unable to cast RecordTemplate to EditableSchemaMetadata");
  }

  @Nonnull
  @Override
  public EditableSchemaMetadata getDefault() {
    AuditStamp auditStamp = new AuditStamp().setActor(UrnUtils.getUrn(SYSTEM_ACTOR)).setTime(System.currentTimeMillis());
    return new EditableSchemaMetadata()
        .setCreated(auditStamp)
        .setLastModified(auditStamp)
        .setEditableSchemaFieldInfo(new EditableSchemaFieldInfoArray());
  }

  @Override
  public EditableSchemaMetadata mergeArrayFields(EditableSchemaMetadata original, EditableSchemaMetadata newValue) {
    EditableSchemaFieldInfoArray originalSchemaFields = original.getEditableSchemaFieldInfo();
    Map<String, EditableSchemaFieldInfo> originalSchemaFieldMap = originalSchemaFields.stream()
        .collect(Collectors.toMap(EditableSchemaFieldInfo::getFieldPath, Function.identity()));

    // Merge duplicate entries, using fieldPath for key, shouldn't be multiple equivalent fieldPaths
    EditableSchemaFieldInfoArray newSchemaFields = newValue.getEditableSchemaFieldInfo();
    Map<String, EditableSchemaFieldInfo> newSchemaFieldMap = new HashMap<>();
    for (EditableSchemaFieldInfo schemaField : newSchemaFields) {
      if (!newSchemaFieldMap.containsKey(schemaField.getFieldPath())) {
        newSchemaFieldMap.put(schemaField.getFieldPath(), schemaField);
      } else {
        // Current obj in map is equivalent to what was in old, take new
        if (originalSchemaFieldMap.containsKey(schemaField.getFieldPath())
            && originalSchemaFieldMap.get(schemaField.getFieldPath()).equals(newSchemaFieldMap.get(schemaField.getFieldPath()))) {
          newSchemaFieldMap.put(schemaField.getFieldPath(), schemaField);
        }
        // Ignore duplicate entries in patch, i.e. add obj1, add obj1 will only result in one obj1 entry in list,
        // first one wins out
      }
    }
    // Dedupe lower level array fields for each schema field, we pick first field since we're always patching to start of array
    for (EditableSchemaFieldInfo schemaFieldInfo : newSchemaFieldMap.values()) {
      // Dedupe terms
      GlossaryTerms glossaryTerms = schemaFieldInfo.getGlossaryTerms();
      if (glossaryTerms != null) {
        GlossaryTermAssociationArray termAssociations = glossaryTerms.getTerms();
        Map<Urn, GlossaryTermAssociation> termAssociationMap = termAssociations.stream()
            .collect(Collectors.toMap(GlossaryTermAssociation::getUrn, Function.identity(), (term1, term2) -> term1));
        schemaFieldInfo.setGlossaryTerms(glossaryTerms.setTerms(new GlossaryTermAssociationArray(termAssociationMap.values())));
      }
      // Dedupe tags
      GlobalTags globalTags = schemaFieldInfo.getGlobalTags();
      if (globalTags != null) {
        TagAssociationArray tagAssociations = globalTags.getTags();
        Map<Urn, TagAssociation> tagAssociationMap = tagAssociations.stream()
            .collect(Collectors.toMap(TagAssociation::getTag, Function.identity(), (tag1, tag2) -> tag1));
        schemaFieldInfo.setGlobalTags(globalTags.setTags(new TagAssociationArray(tagAssociationMap.values())));
      }
    }

    return newValue.setEditableSchemaFieldInfo(new EditableSchemaFieldInfoArray(newSchemaFieldMap.values()));
  }
}
