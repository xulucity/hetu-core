/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.iceberg.orc;

import com.google.common.collect.Lists;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.hive.ql.exec.vector.VectorizedRowBatch;
import org.apache.iceberg.Metrics;
import org.apache.iceberg.MetricsConfig;
import org.apache.iceberg.Schema;
import org.apache.iceberg.exceptions.RuntimeIOException;
import org.apache.iceberg.hadoop.HadoopOutputFile;
import org.apache.iceberg.io.FileAppender;
import org.apache.iceberg.io.OutputFile;
import org.apache.orc.OrcFile;
import org.apache.orc.Reader;
import org.apache.orc.StripeInformation;
import org.apache.orc.TypeDescription;
import org.apache.orc.Writer;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

import static io.hetu.core.plugin.iceberg.IcebergUtil.checkNotNull;

/**
 * Create a file appender for ORC.
 */
class OrcFileAppender<D>
        implements FileAppender<D>
{
    private final int batchSize;
    private final OutputFile file;
    private final Writer writer;
    private final VectorizedRowBatch batch;
    private final OrcRowWriter<D> valueWriter;
    @SuppressWarnings("checkstyle:ExplicitInitialization")
    private boolean isClosed = false;
    private final Configuration conf;
    private final MetricsConfig metricsConfig;

    OrcFileAppender(Schema schema, OutputFile file,
                    BiFunction<Schema, TypeDescription, OrcRowWriter<?>> createWriterFunc,
                    Configuration conf, Map<String, byte[]> metadata,
                    int batchSize, MetricsConfig metricsConfig)
    {
        this.conf = conf;
        this.file = file;
        this.batchSize = batchSize;
        this.metricsConfig = metricsConfig;

        TypeDescription orcSchema = ORCSchemaUtil.convert(schema);
        this.batch = orcSchema.createRowBatch(this.batchSize);

        OrcFile.WriterOptions options = OrcFile.writerOptions(conf).useUTCTimestamp(true);
        if (file instanceof HadoopOutputFile) {
            options.fileSystem(((HadoopOutputFile) file).getFileSystem());
        }
        options.setSchema(orcSchema);
        this.writer = newOrcWriter(file, options, metadata);
        this.valueWriter = newOrcRowWriter(schema, orcSchema, createWriterFunc);
    }

    @Override
    public void add(D datum)
    {
        try {
            valueWriter.write(datum, batch);
            if (batch.size == this.batchSize) {
                writer.addRowBatch(batch);
                batch.reset();
            }
        }
        catch (IOException ioe) {
            throw new RuntimeIOException(ioe, "Problem writing to ORC file %s", file.location());
        }
    }

    @Override
    public Metrics metrics()
    {
        checkNotNull(isClosed, "Cannot return metrics while appending to an open file.");
        return OrcMetrics.fromWriter(writer, valueWriter.metrics(), metricsConfig);
    }

    @Override
    public long length()
    {
        checkNotNull(isClosed,
                "Cannot return length while appending to an open file.");
        return file.toInputFile().getLength();
    }

    @Override
    public List<Long> splitOffsets()
    {
        checkNotNull(isClosed, "File is not yet closed");
        Reader reader = null;
        try {
            reader = ORC.newFileReader(file.toInputFile(), conf);
            List<StripeInformation> stripes = reader.getStripes();
            return Collections.unmodifiableList(Lists.transform(stripes, StripeInformation::getOffset));
        }
        catch (Exception e) {
            throw new RuntimeIOException((IOException) e, "Can't close ORC reader %s", file.location());
        }
    }

    @Override
    public void close() throws IOException
    {
        if (!isClosed) {
            try {
                if (batch.size > 0) {
                    writer.addRowBatch(batch);
                    batch.reset();
                }
            }
            finally {
                writer.close();
                this.isClosed = true;
            }
        }
    }

    private static Writer newOrcWriter(OutputFile file, OrcFile.WriterOptions options, Map<String, byte[]> metadata)
    {
        final Path locPath = new Path(file.location());
        final Writer write;

        try {
            write = OrcFile.createWriter(locPath, options);
        }
        catch (IOException ioe) {
            throw new RuntimeIOException(ioe, "Can't create file %s", locPath);
        }

        metadata.forEach((key, value) -> write.addUserMetadata(key, ByteBuffer.wrap(value)));

        return write;
    }

    @SuppressWarnings("unchecked")
    private static <D> OrcRowWriter<D> newOrcRowWriter(Schema schema,
                                                       TypeDescription orcSchema,
                                                       BiFunction<Schema, TypeDescription, OrcRowWriter<?>>
                                                               createWriterFunc)
    {
        return (OrcRowWriter<D>) createWriterFunc.apply(schema, orcSchema);
    }
}
