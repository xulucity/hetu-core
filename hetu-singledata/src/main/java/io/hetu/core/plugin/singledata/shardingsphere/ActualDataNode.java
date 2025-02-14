/*
 * Copyright (C) 2018-2022. Huawei Technologies Co., Ltd. All rights reserved.
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

package io.hetu.core.plugin.singledata.shardingsphere;

import javax.annotation.Nullable;

import static java.util.Objects.requireNonNull;

public class ActualDataNode
{
    private final String resourceName;
    private final String schemaName;
    private final String tableName;

    public ActualDataNode(String resourceName, @Nullable String schemaName, String tableName)
    {
        this.resourceName = requireNonNull(resourceName, "resourceName is null");
        this.schemaName = schemaName;
        this.tableName = requireNonNull(tableName, "tableName is null");
    }

    public String getResourceName()
    {
        return resourceName;
    }

    @Nullable
    public String getSchemaName()
    {
        return schemaName;
    }

    public String getTableName()
    {
        return tableName;
    }
}
