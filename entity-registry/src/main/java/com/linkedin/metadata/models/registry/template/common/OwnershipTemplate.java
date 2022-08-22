package com.linkedin.metadata.models.registry.template.common;

import com.linkedin.common.AuditStamp;
import com.linkedin.common.Owner;
import com.linkedin.common.OwnerArray;
import com.linkedin.common.Ownership;
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


public class OwnershipTemplate implements ArrayMergingTemplate<Ownership> {

  @Override
  public Ownership getSubtype(RecordTemplate recordTemplate) throws ClassCastException {
    if (recordTemplate instanceof Ownership) {
      return (Ownership) recordTemplate;
    }
    throw new ClassCastException("Unable to cast RecordTemplate to Ownership");
  }

  @Nonnull
  @Override
  public Ownership getDefault() {
    Ownership ownership = new Ownership();
    ownership.setOwners(new OwnerArray());
    ownership.setLastModified(new AuditStamp()
        .setTime(System.currentTimeMillis())
        .setActor(UrnUtils.getUrn(SYSTEM_ACTOR)));

    return ownership;
  }

  @Override
  public Ownership mergeArrayFields(Ownership original, Ownership newValue) {
    OwnerArray owners = original.getOwners();
    Map<Urn, Owner> originalOwnerMap = owners.stream()
        .collect(Collectors.toMap(Owner::getOwner, Function.identity()));

    // Merge duplicate entries
    OwnerArray newOwners = newValue.getOwners();
    Map<Urn, Owner> newOwnerMap = new HashMap<>();
    for (Owner owner : newOwners) {
      if (!newOwnerMap.containsKey(owner.getOwner())) {
        newOwnerMap.put(owner.getOwner(), owner);
      } else {
        // Current obj in map is equivalent to what was in old, take new
        if (originalOwnerMap.containsKey(owner.getOwner())
            && originalOwnerMap.get(owner.getOwner()).equals(newOwnerMap.get(owner.getOwner()))) {
          newOwnerMap.put(owner.getOwner(), owner);
        }
        // Ignore duplicate entries in patch, i.e. add obj1, add obj1 will only result in one obj1 entry in list,
        // first one wins out
      }
    }

    return newValue.setOwners(new OwnerArray(newOwnerMap.values()));
  }
}
