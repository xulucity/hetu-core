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

package io.prestosql.sql.planner;

import com.google.common.collect.ImmutableMap;
import io.prestosql.Session.SessionBuilder;
import io.prestosql.sql.analyzer.FeaturesConfig.JoinDistributionType;
import io.prestosql.sql.analyzer.FeaturesConfig.JoinReorderingStrategy;
import io.prestosql.testing.LocalQueryRunner;

import java.util.stream.IntStream;
import java.util.stream.Stream;

import static io.prestosql.SystemSessionProperties.JOIN_DISTRIBUTION_TYPE;
import static io.prestosql.SystemSessionProperties.JOIN_REORDERING_STRATEGY;
import static io.prestosql.SystemSessionProperties.PUSH_TABLE_THROUGH_SUBQUERY;
import static io.prestosql.testing.TestingSession.testSessionBuilder;
import static java.lang.String.format;

/**
 * This class tests cost-based optimization rules related to joins. It contains unmodified TPCH queries.
 * This class is using TPCH connector configured in way to mock Hive connector with unpartitioned TPCH tables.
 */
public class TestTpchCostBasedPlan
        extends AbstractCostBasedPlanTest
{
    /*
     * CAUTION: The expected plans here are not necessarily optimal yet. Their role is to prevent
     * inadvertent regressions. A conscious improvement to the planner may require changing some
     * of the expected plans, but any such change should be verified on an actual cluster with
     * large amount of data.
     */

    public static final String TPCDS_METADATA_DIR = "/hive_metadata/unpartitioned_tpch";

    public TestTpchCostBasedPlan()
    {
        super(false, false);
    }

    @Override
    protected LocalQueryRunner createQueryRunner()
    {
        String catalog = "local";
        SessionBuilder sessionBuilder = testSessionBuilder()
                .setCatalog(catalog)
                .setSchema(getSchema())
                .setSystemProperty("task_concurrency", "1") // these tests don't handle exchanges from local parallel
                .setSystemProperty(PUSH_TABLE_THROUGH_SUBQUERY, "false")
                .setSystemProperty(JOIN_REORDERING_STRATEGY, JoinReorderingStrategy.AUTOMATIC.name())
                .setSystemProperty(JOIN_DISTRIBUTION_TYPE, JoinDistributionType.AUTOMATIC.name());

        LocalQueryRunner queryRunner = LocalQueryRunner.queryRunnerWithFakeNodeCountForStats(sessionBuilder.build(), 8);
        queryRunner.createCatalog(
                catalog,
                createConnectorFactory(),
                ImmutableMap.of());
        return queryRunner;
    }

    @Override
    protected Stream<String> getQueryResourcePaths()
    {
        return IntStream.range(1, 23)
                .boxed()
                .flatMap(i -> {
                    String queryId = format("q%02d", i);
                    if (i == 17) {
                        return Stream.of(queryId + "_1", queryId + "_2");
                    }
                    return Stream.of(queryId);
                })
                .map(queryId -> format("/sql/presto/tpch/%s.sql", queryId));
    }

    @Override
    protected String getMetadataDir()
    {
        return TPCDS_METADATA_DIR;
    }

    @Override
    protected boolean isPartitioned()
    {
        return false;
    }

    @SuppressWarnings("unused")
    public static final class UpdateTestFiles
    {
        // Intellij doesn't handle well situation when test class has main(), hence inner class.

        private UpdateTestFiles() {}

        public static void main(String[] args)
                throws Exception
        {
            new TestTpchCostBasedPlan().generate();
        }
    }
}
