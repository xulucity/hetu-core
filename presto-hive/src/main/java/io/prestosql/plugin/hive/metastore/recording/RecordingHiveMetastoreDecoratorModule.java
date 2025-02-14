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
package io.prestosql.plugin.hive.metastore.recording;

import com.google.inject.Binder;
import com.google.inject.Scopes;
import io.airlift.configuration.AbstractConfigurationAwareModule;
import io.prestosql.plugin.hive.RecordingMetastoreConfig;
import io.prestosql.plugin.hive.metastore.HiveMetastoreDecorator;
import io.prestosql.plugin.hive.metastore.RecordingHiveMetastore;
import io.prestosql.plugin.hive.metastore.WriteHiveMetastoreRecordingProcedure;
import io.prestosql.plugin.hive.util.HiveBlockEncodingSerde;
import io.prestosql.spi.procedure.Procedure;

import static com.google.inject.multibindings.Multibinder.newSetBinder;
import static io.airlift.json.JsonCodecBinder.jsonCodecBinder;
import static org.weakref.jmx.guice.ExportBinder.newExporter;

public class RecordingHiveMetastoreDecoratorModule
        extends AbstractConfigurationAwareModule
{
    @Override
    protected void setup(Binder binder)
    {
        if (buildConfigObject(RecordingMetastoreConfig.class).getRecordingPath() != null) {
            newSetBinder(binder, HiveMetastoreDecorator.class).addBinding().to(RecordingHiveMetastoreDecorator.class).in(Scopes.SINGLETON);
            binder.bind(HiveBlockEncodingSerde.class).in(Scopes.SINGLETON);

            binder.bind(HiveMetastoreRecording.class).in(Scopes.SINGLETON);
            jsonCodecBinder(binder).bindJsonCodec(HiveMetastoreRecording.Recording.class);
            //Todo: add json SerDe

            // export under the old name, for backwards compatibility
            newExporter(binder).export(HiveMetastoreRecording.class).as(generator -> generator.generatedNameOf(RecordingHiveMetastore.class));

            newSetBinder(binder, Procedure.class).addBinding().toProvider(WriteHiveMetastoreRecordingProcedure.class).in(Scopes.SINGLETON);
        }
    }
}
