package com.linkedin.metadata.models.registry.template.dataset;

import com.linkedin.data.template.RecordTemplate;
import com.linkedin.data.template.StringArray;
import com.linkedin.data.template.StringMap;
import com.linkedin.dataset.DatasetProperties;
import com.linkedin.metadata.models.registry.template.ArrayMergingTemplate;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;


public class DatasetPropertiesTemplate implements ArrayMergingTemplate<DatasetProperties> {

  @Override
  public DatasetProperties getSubtype(RecordTemplate recordTemplate) throws ClassCastException {
    if (recordTemplate instanceof DatasetProperties) {
      return (DatasetProperties) recordTemplate;
    }
    throw new ClassCastException("Unable to cast RecordTemplate to DatasetProperties");
  }

  @Nonnull
  @Override
  public DatasetProperties getDefault() {
    DatasetProperties datasetProperties = new DatasetProperties();
    datasetProperties.setTags(new StringArray());
    datasetProperties.setCustomProperties(new StringMap());

    return datasetProperties;
  }

  @Override
  public DatasetProperties mergeArrayFields(DatasetProperties original, DatasetProperties newValue) {
    return newValue.setTags(new StringArray(newValue.getTags()
        .stream()
        .distinct()
        .collect(Collectors.toList())));
  }
}
