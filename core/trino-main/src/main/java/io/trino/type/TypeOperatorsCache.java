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
package io.trino.type;

import com.google.common.cache.CacheBuilder;
import com.google.common.util.concurrent.UncheckedExecutionException;
import io.trino.collect.cache.NonKeyEvictableCache;
import org.weakref.jmx.Managed;

import java.util.concurrent.ExecutionException;
import java.util.function.BiFunction;
import java.util.function.Supplier;

import static com.google.common.base.Throwables.throwIfUnchecked;
import static io.trino.collect.cache.SafeCaches.buildNonEvictableCacheWithWeakInvalidateAll;

public class TypeOperatorsCache
        implements BiFunction<Object, Supplier<Object>, Object>
{
    private final NonKeyEvictableCache<Object, Object> cache = buildNonEvictableCacheWithWeakInvalidateAll(
            CacheBuilder.newBuilder()
                    .maximumSize(10_000));

    @Override
    public Object apply(Object operatorConvention, Supplier<Object> supplier)
    {
        try {
            return cache.get(operatorConvention, supplier::get);
        }
        catch (ExecutionException | UncheckedExecutionException e) {
            throwIfUnchecked(e.getCause());
            throw new RuntimeException(e.getCause());
        }
    }

    // stats
    @Managed
    public long cacheSize()
    {
        return cache.size();
    }

    @Managed
    public Double getCacheHitRate()
    {
        return cache.stats().hitRate();
    }

    @Managed
    public Double getCacheMissRate()
    {
        return cache.stats().missRate();
    }

    @Managed
    public long getCacheRequestCount()
    {
        return cache.stats().requestCount();
    }

    @Managed
    public void cacheReset()
    {
        // Note: this may not invalidate ongoing loads (https://github.com/trinodb/trino/issues/10512, https://github.com/google/guava/issues/1881)
        // This is acceptable, since this operation is invoked manually, and not relied upon for correctness.
        cache.invalidateAll();
    }
}
