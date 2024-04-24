package com.linkedin.metadata.graph;

import java.util.List;
import lombok.Data;


@Data
public class LineageScrollResponse {
  List<LineageRelationship> lineageRelationships;
  int total;
  String nextScrollId;
}
