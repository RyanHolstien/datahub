package com.linkedin.metadata.models.registry.template.common;

import com.fasterxml.jackson.databind.JsonNode;
import com.linkedin.common.AuditStamp;
import com.linkedin.common.GlossaryTermAssociationArray;
import com.linkedin.common.GlossaryTerms;
import com.linkedin.common.urn.UrnUtils;
import com.linkedin.data.template.RecordTemplate;
import com.linkedin.metadata.models.registry.template.ArrayMergingTemplate;
import java.util.Collections;
import javax.annotation.Nonnull;

import static com.linkedin.metadata.Constants.*;


public class GlossaryTermsTemplate implements ArrayMergingTemplate<GlossaryTerms> {

  private static final String TERMS_FIELD_NAME = "terms";
  private static final String URN_FIELD_NAME = "urn";

  @Override
  public GlossaryTerms getSubtype(RecordTemplate recordTemplate) throws ClassCastException {
    if (recordTemplate instanceof GlossaryTerms) {
      return (GlossaryTerms) recordTemplate;
    }
    throw new ClassCastException("Unable to cast RecordTemplate to GlossaryTerms");
  }

  @Override
  public Class<GlossaryTerms> getTemplateType() {
    return GlossaryTerms.class;
  }

  @Nonnull
  @Override
  public GlossaryTerms getDefault() {
    GlossaryTerms glossaryTerms = new GlossaryTerms();
    glossaryTerms.setTerms(new GlossaryTermAssociationArray())
        .setAuditStamp(new AuditStamp().setActor(UrnUtils.getUrn(SYSTEM_ACTOR)).setTime(System.currentTimeMillis()));

    return glossaryTerms;
  }

  @Nonnull
  @Override
  public JsonNode transformFields(JsonNode baseNode) {
    return arrayFieldToMap(baseNode, TERMS_FIELD_NAME, Collections.singletonList(URN_FIELD_NAME));
  }

  @Nonnull
  @Override
  public JsonNode rebaseFields(JsonNode patched) {
    return transformedMapToArray(patched, TERMS_FIELD_NAME, Collections.singletonList(URN_FIELD_NAME));
  }
}
