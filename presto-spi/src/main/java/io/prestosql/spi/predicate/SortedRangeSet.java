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
package io.prestosql.spi.predicate;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.prestosql.spi.connector.ConnectorSession;
import io.prestosql.spi.type.Type;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.lang.String.format;
import static java.util.Arrays.asList;
import static java.util.Collections.unmodifiableList;
import static java.util.Objects.requireNonNull;

/**
 * A set containing zero or more Ranges of the same type over a continuous space of possible values.
 * Ranges are coalesced into the most compact representation of non-overlapping Ranges. This structure
 * allows iteration across these compacted Ranges in increasing order, as well as other common
 * set-related operation.
 */
public final class SortedRangeSet
        implements ValueSet
{
    private final Type type;
    private final NavigableMap<Marker, Range> lowIndexedRanges;

    private SortedRangeSet(Type type, NavigableMap<Marker, Range> lowIndexedRanges)
    {
        requireNonNull(type, "type is null");
        requireNonNull(lowIndexedRanges, "lowIndexedRanges is null");

        if (!type.isOrderable()) {
            throw new IllegalArgumentException("Type is not orderable: " + type);
        }
        this.type = type;
        this.lowIndexedRanges = lowIndexedRanges;
    }

    static SortedRangeSet none(Type type)
    {
        return copyOf(type, Collections.emptyList());
    }

    static SortedRangeSet all(Type type)
    {
        return copyOf(type, Collections.singletonList(Range.all(type)));
    }

    /**
     * Provided discrete values that are unioned together to form the SortedRangeSet
     */
    static SortedRangeSet of(Type type, Object first, Object... rest)
    {
        List<Range> ranges = new ArrayList<>(rest.length + 1);
        ranges.add(Range.equal(type, first));
        for (Object value : rest) {
            ranges.add(Range.equal(type, value));
        }
        return copyOf(type, ranges);
    }

    /**
     * Provided Ranges are unioned together to form the SortedRangeSet
     */
    static SortedRangeSet of(Range first, Range... rest)
    {
        List<Range> rangeList = new ArrayList<>(rest.length + 1);
        rangeList.add(first);
        rangeList.addAll(asList(rest));
        return copyOf(first.getType(), rangeList);
    }

    static SortedRangeSet of(List<Range> rangeList)
    {
        if (rangeList.isEmpty()) {
            throw new IllegalArgumentException("cannot use empty rangeList");
        }
        return copyOf(rangeList.get(0).getType(), rangeList);
    }

    /**
     * Provided Ranges are unioned together to form the SortedRangeSet
     */
    static SortedRangeSet copyOf(Type type, Iterable<Range> ranges)
    {
        return new Builder(type).addAll(ranges).build();
    }

    @JsonCreator
    public static SortedRangeSet copyOf(
            @JsonProperty("type") Type type,
            @JsonProperty("ranges") List<Range> ranges)
    {
        return copyOf(type, (Iterable<Range>) ranges);
    }

    @Override
    @JsonProperty
    public Type getType()
    {
        return type;
    }

    @JsonProperty("ranges")
    public List<Range> getOrderedRanges()
    {
        return new ArrayList<>(lowIndexedRanges.values());
    }

    public int getRangeCount()
    {
        return lowIndexedRanges.size();
    }

    @Override
    public boolean isNone()
    {
        return lowIndexedRanges.isEmpty();
    }

    @Override
    public boolean isAll()
    {
        return lowIndexedRanges.size() == 1 && lowIndexedRanges.values().iterator().next().isAll();
    }

    @Override
    public boolean isSingleValue()
    {
        return lowIndexedRanges.size() == 1 && lowIndexedRanges.values().iterator().next().isSingleValue();
    }

    @Override
    public Object getSingleValue()
    {
        if (!isSingleValue()) {
            throw new IllegalStateException("SortedRangeSet does not have just a single value");
        }
        return lowIndexedRanges.values().iterator().next().getSingleValue();
    }

    @Override
    public boolean isDiscreteSet()
    {
        for (Range range : lowIndexedRanges.values()) {
            if (!range.isSingleValue()) {
                return false;
            }
        }
        return !isNone();
    }

    @Override
    public List<Object> getDiscreteSet()
    {
        if (!isDiscreteSet()) {
            throw new IllegalStateException("SortedRangeSet is not a discrete set");
        }
        return unmodifiableList(lowIndexedRanges.values().stream()
                .map(Range::getSingleValue)
                .collect(Collectors.toList()));
    }

    @Override
    public boolean containsValue(Object value)
    {
        return includesMarker(Marker.exactly(type, value));
    }

    boolean includesMarker(Marker marker)
    {
        requireNonNull(marker, "marker is null");
        checkTypeCompatibility(marker);

        Map.Entry<Marker, Range> floorEntry = lowIndexedRanges.floorEntry(marker);
        return floorEntry != null && floorEntry.getValue().includes(marker);
    }

    public Range getSpan()
    {
        if (lowIndexedRanges.isEmpty()) {
            throw new IllegalStateException("Can not get span if no ranges exist");
        }
        return lowIndexedRanges.firstEntry().getValue().span(lowIndexedRanges.lastEntry().getValue());
    }

    @Override
    public Ranges getRanges()
    {
        return new Ranges()
        {
            @Override
            public int getRangeCount()
            {
                return SortedRangeSet.this.getRangeCount();
            }

            @Override
            public List<Range> getOrderedRanges()
            {
                return SortedRangeSet.this.getOrderedRanges();
            }

            @Override
            public Range getSpan()
            {
                return SortedRangeSet.this.getSpan();
            }
        };
    }

    @Override
    public ValuesProcessor getValuesProcessor()
    {
        return new ValuesProcessor()
        {
            @Override
            public <T> T transform(Function<Ranges, T> rangesFunction, Function<DiscreteValues, T> valuesFunction, Function<AllOrNone, T> allOrNoneFunction)
            {
                return rangesFunction.apply(getRanges());
            }

            @Override
            public void consume(Consumer<Ranges> rangesConsumer, Consumer<DiscreteValues> valuesConsumer, Consumer<AllOrNone> allOrNoneConsumer)
            {
                rangesConsumer.accept(getRanges());
            }
        };
    }

    @Override
    public SortedRangeSet intersect(ValueSet other)
    {
        SortedRangeSet otherRangeSet = checkCompatibility(other);

        Builder builder = new Builder(type);

        Iterator<Range> iterator1 = lowIndexedRanges.values().iterator();
        Iterator<Range> iterator2 = otherRangeSet.lowIndexedRanges.values().iterator();

        if (iterator1.hasNext() && iterator2.hasNext()) {
            Range range1 = iterator1.next();
            Range range2 = iterator2.next();

            while (true) {
                if (range1.overlaps(range2)) {
                    builder.add(range1.intersect(range2));
                }

                if (range1.getHigh().compareTo(range2.getHigh()) <= 0) {
                    if (!iterator1.hasNext()) {
                        break;
                    }
                    range1 = iterator1.next();
                }
                else {
                    if (!iterator2.hasNext()) {
                        break;
                    }
                    range2 = iterator2.next();
                }
            }
        }

        return builder.build();
    }

    @Override
    public boolean overlaps(ValueSet other)
    {
        SortedRangeSet otherRangeSet = checkCompatibility(other);

        Iterator<Range> iterator1 = lowIndexedRanges.values().iterator();
        Iterator<Range> iterator2 = otherRangeSet.lowIndexedRanges.values().iterator();

        if (iterator1.hasNext() && iterator2.hasNext()) {
            Range range1 = iterator1.next();
            Range range2 = iterator2.next();

            while (true) {
                if (range1.overlaps(range2)) {
                    return true;
                }

                if (range1.getHigh().compareTo(range2.getHigh()) <= 0) {
                    if (!iterator1.hasNext()) {
                        break;
                    }
                    range1 = iterator1.next();
                }
                else {
                    if (!iterator2.hasNext()) {
                        break;
                    }
                    range2 = iterator2.next();
                }
            }
        }

        return false;
    }

    @Override
    public SortedRangeSet union(ValueSet other)
    {
        SortedRangeSet otherRangeSet = checkCompatibility(other);
        return new Builder(type)
                .addAll(this.lowIndexedRanges.values())
                .addAll(otherRangeSet.lowIndexedRanges.values())
                .build();
    }

    @Override
    public SortedRangeSet union(Collection<ValueSet> valueSets)
    {
        Builder builder = new Builder(type);
        builder.addAll(this.lowIndexedRanges.values());
        for (ValueSet valueSet : valueSets) {
            builder.addAll(checkCompatibility(valueSet).lowIndexedRanges.values());
        }
        return builder.build();
    }

    @Override
    public SortedRangeSet complement()
    {
        Builder builder = new Builder(type);

        if (lowIndexedRanges.isEmpty()) {
            return builder.add(Range.all(type)).build();
        }

        Iterator<Range> rangeIterator = lowIndexedRanges.values().iterator();

        Range firstRange = rangeIterator.next();
        if (!firstRange.getLow().isLowerUnbounded()) {
            builder.add(new Range(Marker.lowerUnbounded(type), firstRange.getLow().lesserAdjacent()));
        }

        Range previousRange = firstRange;
        while (rangeIterator.hasNext()) {
            Range currentRange = rangeIterator.next();

            Marker lowMarker = previousRange.getHigh().greaterAdjacent();
            Marker highMarker = currentRange.getLow().lesserAdjacent();
            builder.add(new Range(lowMarker, highMarker));

            previousRange = currentRange;
        }

        Range lastRange = previousRange;
        if (!lastRange.getHigh().isUpperUnbounded()) {
            builder.add(new Range(lastRange.getHigh().greaterAdjacent(), Marker.upperUnbounded(type)));
        }

        return builder.build();
    }

    private SortedRangeSet checkCompatibility(ValueSet other)
    {
        if (!getType().equals(other.getType())) {
            throw new IllegalStateException(format("Mismatched types: %s vs %s", getType(), other.getType()));
        }
        if (!(other instanceof SortedRangeSet)) {
            throw new IllegalStateException(format("ValueSet is not a SortedRangeSet: %s", other.getClass()));
        }
        return (SortedRangeSet) other;
    }

    private void checkTypeCompatibility(Marker marker)
    {
        if (!getType().equals(marker.getType())) {
            throw new IllegalStateException(format("Marker of %s does not match SortedRangeSet of %s", marker.getType(), getType()));
        }
    }

    @Override
    public Optional<Collection<Object>> tryExpandRanges(int valuesLimit)
    {
        List<Range> ranges = getRanges().getOrderedRanges();
        Type typeRangeSet = getType();

        Range typeRange = typeRangeSet.getRange().map(range -> Range.range(typeRangeSet, range.getMin(), true, range.getMax(), true)).orElse(Range.all(typeRangeSet));

        List<Object> result = new ArrayList<>();
        for (Range range : ranges) {
            if (range.isLowUnbounded() || range.isHighUnbounded()) {
                // Try to restrict the current unbounded range with the type min-max values.
                range = range.intersect(typeRange);
                if (range.isLowUnbounded() || range.isHighUnbounded()) {
                    return Optional.empty();
                }
            }
            Optional<Stream<?>> discreteValues = typeRangeSet.getDiscreteValues(new Type.Range(range.getLowBoundedValue(), range.getHighBoundedValue()));
            if (!discreteValues.isPresent()) {
                return Optional.empty();
            }
            Iterator<?> iterator = discreteValues.get().iterator();
            if (!iterator.hasNext()) {
                throw new IllegalStateException("discreteValues iterator is empty");
            }
            if (!range.isLowInclusive()) {
                iterator.next();
            }
            while (iterator.hasNext()) {
                Object current = iterator.next();
                // Don't add the highest value in the range (if it's not included).
                if (range.isHighInclusive() || iterator.hasNext()) {
                    if (result.size() >= valuesLimit) {
                        return Optional.empty();
                    }
                    result.add(current);
                }
            }
        }
        return Optional.of(unmodifiableList(result));
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(lowIndexedRanges);
    }

    @Override
    public boolean equals(Object obj)
    {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        final SortedRangeSet other = (SortedRangeSet) obj;
        return Objects.equals(this.lowIndexedRanges, other.lowIndexedRanges);
    }

    @Override
    public String toString(ConnectorSession session)
    {
        return "[" + lowIndexedRanges.values().stream()
                .map(range -> range.toString(session))
                .collect(Collectors.joining(", ")) + "]";
    }

    static class Builder
    {
        private final Type type;
        private final List<Range> ranges = new ArrayList<>();

        Builder(Type type)
        {
            requireNonNull(type, "type is null");

            if (!type.isOrderable()) {
                throw new IllegalArgumentException("Type is not orderable: " + type);
            }
            this.type = type;
        }

        Builder add(Range range)
        {
            if (!type.equals(range.getType())) {
                throw new IllegalArgumentException(format("Range type %s does not match builder type %s", range.getType(), type));
            }

            ranges.add(range);
            return this;
        }

        Builder addAll(Iterable<Range> ranges)
        {
            for (Range range : ranges) {
                add(range);
            }
            return this;
        }

        SortedRangeSet build()
        {
            Collections.sort(ranges, Comparator.comparing(Range::getLow));

            NavigableMap<Marker, Range> result = new TreeMap<>();

            Range current = null;
            for (Range next : ranges) {
                if (current == null) {
                    current = next;
                    continue;
                }

                if (current.overlaps(next) || current.getHigh().isAdjacent(next.getLow())) {
                    current = current.span(next);
                }
                else {
                    result.put(current.getLow(), current);
                    current = next;
                }
            }

            if (current != null) {
                result.put(current.getLow(), current);
            }

            return new SortedRangeSet(type, result);
        }
    }
}
