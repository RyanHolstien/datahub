package datahub.client.kafka;

import com.linkedin.mxe.MetadataChangeProposal;
import datahub.client.Callback;
import datahub.client.Emitter;
import datahub.client.MetadataWriteResponse;
import datahub.event.EventFormatter;
import datahub.event.MetadataChangeProposalWrapper;
import datahub.event.UpsertAspectRequest;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import javax.annotation.Nonnull;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;


public class KafkaEmitter implements Emitter {
  @Override
  public Future<MetadataWriteResponse> emit(@Nonnull MetadataChangeProposalWrapper mcpw, Callback callback)
      throws IOException {
    GenericRecord genericRecord = new EventFormatter().convertToGenericRecord(mcpw);
    // Submit to Kafka
    return null;
  }

  @Override
  public Future<MetadataWriteResponse> emit(@Nonnull MetadataChangeProposal mcp, Callback callback) throws IOException {
    return null;
  }

  @Override
  public boolean testConnection() throws IOException, ExecutionException, InterruptedException {
    return false;
  }

  @Override
  public Future<MetadataWriteResponse> emit(List<UpsertAspectRequest> request, Callback callback) throws IOException {
    return null;
  }

  @Override
  public void close() throws IOException {

  }
}
