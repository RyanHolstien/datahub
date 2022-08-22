package com.linkedin.metadata.models.registry.template.common;

import com.linkedin.common.GlobalTags;
import com.linkedin.common.TagAssociation;
import com.linkedin.common.TagAssociationArray;
import com.linkedin.common.urn.Urn;
import com.linkedin.data.template.RecordTemplate;
import com.linkedin.metadata.models.registry.template.ArrayMergingTemplate;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;


public class GlobalTagsTemplate implements ArrayMergingTemplate<GlobalTags> {
  @Override
  public GlobalTags getSubtype(RecordTemplate recordTemplate) throws ClassCastException {
    if (recordTemplate instanceof GlobalTags) {
      return (GlobalTags) recordTemplate;
    }
    throw new ClassCastException("Unable to cast RecordTemplate to GlobalTags");
  }

  @Nonnull
  @Override
  public GlobalTags getDefault() {
    GlobalTags globalTags = new GlobalTags();
    globalTags.setTags(new TagAssociationArray());

    return globalTags;
  }

  @Override
  public GlobalTags mergeArrayFields(GlobalTags original, GlobalTags newValue) {
    TagAssociationArray tagAssociations = original.getTags();
    Map<Urn, TagAssociation> originalTagMap = tagAssociations.stream()
        .collect(Collectors.toMap(TagAssociation::getTag, Function.identity()));

    // Merge duplicate entries
    TagAssociationArray newTags = newValue.getTags();
    Map<Urn, TagAssociation> newTagMap = new HashMap<>();
    for (TagAssociation tagAssociation : newTags) {
      if (!newTagMap.containsKey(tagAssociation.getTag())) {
        newTagMap.put(tagAssociation.getTag(), tagAssociation);
      } else {
        // Current obj in map is equivalent to what was in old, take new
        if (originalTagMap.containsKey(tagAssociation.getTag())
            && originalTagMap.get(tagAssociation.getTag()).equals(newTagMap.get(tagAssociation.getTag()))) {
          newTagMap.put(tagAssociation.getTag(), tagAssociation);
        }
        // Ignore duplicate entries in patch, i.e. add obj1, add obj1 will only result in one obj1 entry in list,
        // first one wins out
      }
    }

    return newValue.setTags(new TagAssociationArray(newTagMap.values()));
  }
}
