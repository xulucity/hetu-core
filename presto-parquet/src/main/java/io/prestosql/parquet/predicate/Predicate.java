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
package io.prestosql.parquet.predicate;

import io.prestosql.parquet.ParquetCorruptionException;
import io.prestosql.parquet.ParquetDataSourceId;
import org.apache.parquet.column.ColumnDescriptor;
import org.apache.parquet.column.statistics.Statistics;
import org.apache.parquet.filter2.predicate.FilterPredicate;
import org.apache.parquet.internal.filter2.columnindex.ColumnIndexStore;
import org.joda.time.DateTimeZone;

import java.util.Map;
import java.util.Optional;

public interface Predicate
{
    Predicate TRUE = new Predicate()
    {
        @Override
        public boolean matches(long numberOfRows, Map<ColumnDescriptor, Statistics<?>> statistics, ParquetDataSourceId id, boolean failOnCorruptedParquetStatistics)
        {
            return true;
        }

        @Override
        public boolean matches(Map<ColumnDescriptor, DictionaryDescriptor> dictionaries)
        {
            return false;
        }

        @Override
        public boolean matches(long numberOfRows, Map<ColumnDescriptor, Statistics<?>> statistics, ParquetDataSourceId id) throws ParquetCorruptionException
        {
            return true;
        }

        @Override
        public boolean matches(long numberOfRows, ColumnIndexStore columnIndex, ParquetDataSourceId id) throws ParquetCorruptionException
        {
            return true;
        }

        @Override
        public boolean matches(DictionaryDescriptor dictionary)
        {
            return true;
        }

        @Override
        public Optional<FilterPredicate> toParquetFilter(DateTimeZone timeZone)
        {
            return Optional.empty();
        }
    };

    /**
     * Should the Parquet Reader process a file section with the specified dictionary based on that
     * single dictionary. This is safe to check repeatedly to avoid loading more parquet dictionaries
     * if the section can already be eliminated.
     *
     * @param numberOfRows the number of rows in the segment; this can be used with
     * Statistics to determine if a column is only null
     * @param statistics column statistics
     * @param id Parquet file name
     * @param failOnCorruptedParquetStatistics whether to fail query when scanning a Parquet file with corrupted statistics
     */
    boolean matches(long numberOfRows, Map<ColumnDescriptor, Statistics<?>> statistics, ParquetDataSourceId id, boolean failOnCorruptedParquetStatistics)
            throws ParquetCorruptionException;

    /**
     * Should the Parquet Reader process a file section with the specified dictionary.
     *
     * @param dictionary The single column dictionary
     */
    boolean matches(Map<ColumnDescriptor, DictionaryDescriptor> dictionaries);

    boolean matches(long numberOfRows, Map<ColumnDescriptor, Statistics<?>> statistics, ParquetDataSourceId id)
            throws ParquetCorruptionException;

    boolean matches(long numberOfRows, ColumnIndexStore columnIndex, ParquetDataSourceId id)
            throws ParquetCorruptionException;

    Optional<FilterPredicate> toParquetFilter(DateTimeZone timeZone);

    boolean matches(DictionaryDescriptor dictionary);
}
