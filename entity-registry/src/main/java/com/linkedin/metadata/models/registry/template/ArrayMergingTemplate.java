package com.linkedin.metadata.models.registry.template;

import com.linkedin.data.template.RecordTemplate;


public interface ArrayMergingTemplate<T extends RecordTemplate> extends Template<T> {
  /**
   * Performs the default merge behavior for array based fields, this is required as some array fields in the model are treated like maps
   * @param original original template
   * @param updated updated template with modifications applied
   * @return a {@link RecordTemplate} subtype with any array based fields merged if needed
   */
  T mergeArrayFields(T original, T updated);
}
