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
package io.prestosql.type;

import com.google.common.collect.ImmutableList;
import io.prestosql.metadata.FunctionAndTypeManager;
import io.prestosql.metadata.TypeRegistry;
import io.prestosql.spi.function.FunctionHandle;
import io.prestosql.spi.function.OperatorType;
import io.prestosql.spi.type.ParametricType;
import io.prestosql.spi.type.Type;
import io.prestosql.spi.type.TypeManager;
import io.prestosql.spi.type.TypeOperators;
import io.prestosql.spi.type.TypeSignature;
import io.prestosql.spi.type.TypeSignatureParameter;
import io.prestosql.sql.analyzer.FeaturesConfig;

import javax.inject.Inject;

import java.lang.invoke.MethodHandle;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static io.prestosql.sql.analyzer.TypeSignatureProvider.fromTypes;
import static java.util.Objects.requireNonNull;

public final class InternalTypeManager
        implements TypeManager
{
    private final FunctionAndTypeManager metadata;
    private final TypeCoercion typeCoercion;
    public static final TypeManager TESTING_TYPE_MANAGER = new InternalTypeManager(new TypeRegistry(new TypeOperators(), new FeaturesConfig()));
    private final TypeRegistry typeRegistry;

    @Inject
    public InternalTypeManager(FunctionAndTypeManager metadata)
    {
        this.metadata = requireNonNull(metadata, "metadata is null");
        this.typeCoercion = new TypeCoercion(this::getType);
        this.typeRegistry = null;
    }

    @Inject
    public InternalTypeManager(TypeRegistry typeRegistry)
    {
        this.typeRegistry = typeRegistry;
        this.metadata = null;
        this.typeCoercion = null;
    }

    @Override
    public Type getType(TypeSignature signature)
    {
        return metadata.getType(signature);
    }

    @Override
    public Type getParameterizedType(String baseTypeName, List<TypeSignatureParameter> typeParameters)
    {
        return getType(new TypeSignature(baseTypeName, typeParameters));
    }

    @Override
    public MethodHandle resolveOperator(OperatorType operatorType, List<? extends Type> argumentTypes)
    {
        FunctionHandle operatorHandle = metadata.resolveOperatorFunctionHandle(operatorType, fromTypes(argumentTypes));
        return metadata.getBuiltInScalarFunctionImplementation(operatorHandle).getMethodHandle();
    }

    @Override
    public List<Type> getTypes()
    {
        return ImmutableList.copyOf(metadata.getTypes());
    }

    @Override
    public Collection<ParametricType> getParametricTypes()
    {
        return metadata.getParametricTypes();
    }

    @Override
    public boolean isTypeOnlyCoercion(Type source, Type result)
    {
        return typeCoercion.isTypeOnlyCoercion(source, result);
    }

    @Override
    public Optional<Type> getCommonSuperType(Type firstType, Type secondType)
    {
        return typeCoercion.getCommonSuperType(firstType, secondType);
    }

    @Override
    public boolean canCoerce(Type fromType, Type toType)
    {
        return typeCoercion.canCoerce(fromType, toType);
    }

    @Override
    public Optional<Type> coerceTypeBase(Type sourceType, String resultTypeBase)
    {
        return typeCoercion.coerceTypeBase(sourceType, resultTypeBase);
    }

    @Override
    public TypeOperators getTypeOperators()
    {
        return new TypeOperators();
    }
}
