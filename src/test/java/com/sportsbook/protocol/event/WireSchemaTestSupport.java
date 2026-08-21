package com.sportsbook.protocol.event;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.avro.Schema;

final class WireSchemaTestSupport {

  private static final Path SCHEMA_ROOT = Path.of("src/main/avro");

  private WireSchemaTestSupport() {}

  static Map<String, Schema> loadSchemas() throws IOException {
    List<Path> files;
    try (var paths = Files.walk(SCHEMA_ROOT)) {
      files =
          paths
              .filter(path -> path.toString().endsWith(".avsc"))
              .sorted(
                  Comparator.comparingInt(WireSchemaTestSupport::schemaOrder)
                      .thenComparing(Path::toString))
              .toList();
    }
    Schema.Parser parser = new Schema.Parser();
    Map<String, Schema> schemas = new LinkedHashMap<>();
    for (Path file : files) {
      Schema schema = parser.parse(new File(file.toString()));
      schemas.put(schema.getFullName(), schema);
    }
    return schemas;
  }

  private static int schemaOrder(Path path) {
    return switch (path.getFileName().toString()) {
      case "Money.avsc" -> 0;
      case "BetSettled.avsc" -> 1;
      default -> 2;
    };
  }
}
