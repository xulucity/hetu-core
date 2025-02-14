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
package io.prestosql.split;

import io.prestosql.Session;
import io.prestosql.execution.DriverPipelineTaskId;
import io.prestosql.metadata.DeletesAsInsertTableHandle;
import io.prestosql.metadata.InsertTableHandle;
import io.prestosql.metadata.OutputTableHandle;
import io.prestosql.metadata.UpdateTableHandle;
import io.prestosql.metadata.VacuumTableHandle;
import io.prestosql.spi.connector.ConnectorPageSink;
import io.prestosql.sql.planner.plan.TableExecuteHandle;

import java.util.Optional;

public interface PageSinkProvider
{
    ConnectorPageSink createPageSink(Session session, Optional<DriverPipelineTaskId> taskId, OutputTableHandle tableHandle);

    ConnectorPageSink createPageSink(Session session, Optional<DriverPipelineTaskId> taskId, InsertTableHandle tableHandle);

    ConnectorPageSink createPageSink(Session session, Optional<DriverPipelineTaskId> taskId, DeletesAsInsertTableHandle tableHandle);

    ConnectorPageSink createPageSink(Session session, Optional<DriverPipelineTaskId> taskId, UpdateTableHandle tableHandle);

    ConnectorPageSink createPageSink(Session session, Optional<DriverPipelineTaskId> taskId, VacuumTableHandle tableHandle);

    ConnectorPageSink createPageSink(Session session, TableExecuteHandle tableHandle);
}
