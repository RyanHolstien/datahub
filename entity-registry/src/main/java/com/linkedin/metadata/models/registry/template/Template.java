package com.linkedin.metadata.models.registry.template;

import com.linkedin.data.template.RecordTemplate;
import javax.annotation.Nonnull;


public interface Template<T extends RecordTemplate> {

  /**
   * Cast method to get subtype of {@link RecordTemplate} for applying templating methods
   * @param recordTemplate generic record
   * @return specific type for this template
   * @throws {@link ClassCastException} when recordTemplate is not the correct type for the template
   */
  T getSubtype(RecordTemplate recordTemplate) throws ClassCastException;

  /**
   * Get a template aspect with defaults set
   * @return subtype of {@link RecordTemplate} that lines up with a predefined AspectSpec
   */
  @Nonnull
  T getDefault();
}
