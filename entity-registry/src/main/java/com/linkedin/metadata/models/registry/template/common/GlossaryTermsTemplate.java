package com.linkedin.metadata.models.registry.template.common;

import com.linkedin.common.AuditStamp;
import com.linkedin.common.GlossaryTermAssociation;
import com.linkedin.common.GlossaryTermAssociationArray;
import com.linkedin.common.GlossaryTerms;
import com.linkedin.common.urn.Urn;
import com.linkedin.common.urn.UrnUtils;
import com.linkedin.data.template.RecordTemplate;
import com.linkedin.metadata.models.registry.template.ArrayMergingTemplate;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;

import static com.linkedin.metadata.Constants.*;


public class GlossaryTermsTemplate implements ArrayMergingTemplate<GlossaryTerms> {

  @Override
  public GlossaryTerms getSubtype(RecordTemplate recordTemplate) throws ClassCastException {
    if (recordTemplate instanceof GlossaryTerms) {
      return (GlossaryTerms) recordTemplate;
    }
    throw new ClassCastException("Unable to cast RecordTemplate to GlossaryTerms");
  }

  @Nonnull
  @Override
  public GlossaryTerms getDefault() {
    GlossaryTerms glossaryTerms = new GlossaryTerms();
    glossaryTerms.setTerms(new GlossaryTermAssociationArray())
        .setAuditStamp(new AuditStamp().setActor(UrnUtils.getUrn(SYSTEM_ACTOR)).setTime(System.currentTimeMillis()));

    return glossaryTerms;
  }

  @Override
  public GlossaryTerms mergeArrayFields(GlossaryTerms original, GlossaryTerms newValue) {
    GlossaryTermAssociationArray originalTerms = original.getTerms();
    Map<Urn, GlossaryTermAssociation> originalTermMap = originalTerms.stream()
        .collect(Collectors.toMap(GlossaryTermAssociation::getUrn, Function.identity()));

    // Merge duplicate entries
    GlossaryTermAssociationArray newTerms = newValue.getTerms();
    Map<Urn, GlossaryTermAssociation> newTermMap = new HashMap<>();
    for (GlossaryTermAssociation termAssociation : newTerms) {
      if (!newTermMap.containsKey(termAssociation.getUrn())) {
        newTermMap.put(termAssociation.getUrn(), termAssociation);
      } else {
        // Current obj in map is equivalent to what was in old, take new
        if (originalTermMap.containsKey(termAssociation.getUrn())
            && originalTermMap.get(termAssociation.getUrn()).equals(newTermMap.get(termAssociation.getUrn()))) {
          newTermMap.put(termAssociation.getUrn(), termAssociation);
        }
        // Ignore duplicate entries in patch, i.e. add obj1, add obj1 will only result in one obj1 entry in list,
        // first one wins out
      }
    }

    return newValue.setTerms(new GlossaryTermAssociationArray(newTermMap.values()));
  }
}
