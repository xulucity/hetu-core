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
package io.prestosql.orc;

import io.airlift.slice.Slice;
import io.prestosql.orc.stream.OrcDataReader;

import java.io.Closeable;
import java.io.IOException;
import java.util.Map;

public interface OrcDataSource
        extends Closeable
{
    OrcDataSourceId getId();

    long getLastModifiedTime();

    long getReadBytes();

    long getReadTimeNanos();

    long getSize();

    Slice readFully(long position, int length)
            throws IOException;

    <K> Map<K, OrcDataReader> readFully(Map<K, DiskRange> diskRanges)
            throws IOException;

    @Override
    default void close()
            throws IOException
    {
    }

    default long getRetainedSize()
    {
        return -1L;
    }

    default Slice readTail(int length) throws IOException
    {
        return null;
    }

    default long getEstimatedSize()
    {
        return -1L;
    }
}
