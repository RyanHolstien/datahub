package com.linkedin.metadata.models.registry.template.dataset;

import com.linkedin.common.urn.Urn;
import com.linkedin.data.template.RecordTemplate;
import com.linkedin.dataset.FineGrainedLineageArray;
import com.linkedin.dataset.Upstream;
import com.linkedin.dataset.UpstreamArray;
import com.linkedin.dataset.UpstreamLineage;
import com.linkedin.metadata.models.registry.template.ArrayMergingTemplate;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;


public class UpstreamLineageTemplate implements ArrayMergingTemplate<UpstreamLineage> {

  @Override
  public UpstreamLineage getSubtype(RecordTemplate recordTemplate) throws ClassCastException {
    if (recordTemplate instanceof UpstreamLineage) {
      return (UpstreamLineage) recordTemplate;
    }
    throw new ClassCastException("Unable to cast RecordTemplate to UpstreamLineage");
  }

  @Nonnull
  @Override
  public UpstreamLineage getDefault() {
    UpstreamLineage upstreamLineage = new UpstreamLineage();
    upstreamLineage.setUpstreams(new UpstreamArray());
    upstreamLineage.setFineGrainedLineages(new FineGrainedLineageArray());

    return upstreamLineage;
  }

  @Override
  public UpstreamLineage mergeArrayFields(UpstreamLineage original, UpstreamLineage newValue) {
    UpstreamArray originalUpstreams = original.getUpstreams();
    Map<Urn, Upstream> originalUpstreamMap = originalUpstreams.stream()
        .collect(Collectors.toMap(Upstream::getDataset, Function.identity()));

    UpstreamArray newUpstreams = newValue.getUpstreams();
    Map<Urn, Upstream> newUpstreamMap = new HashMap<>();
    for (Upstream upstream : newUpstreams) {
      if (!newUpstreamMap.containsKey(upstream.getDataset())) {
        newUpstreamMap.put(upstream.getDataset(), upstream);
      } else {
        // Current obj in map is equivalent to what was in old, take new
        if (originalUpstreamMap.containsKey(upstream.getDataset())
            && originalUpstreamMap.get(upstream.getDataset()).equals(newUpstreamMap.get(upstream.getDataset()))) {
          newUpstreamMap.put(upstream.getDataset(), upstream);
        }
        // Ignore duplicate entries in patch, i.e. add obj1, add obj1 will only result in one obj1 entry in list,
        // first one wins out
      }
    }

    // We don't have a key to be able to dedupe fine grained lineages, so can't currently dedupe these in a simple way.
    // Since these are not treated as a simple map in the UI this shouldn't cause issues, but has potential for weird states.

    return newValue.setUpstreams(new UpstreamArray(newUpstreamMap.values()));
  }
}
