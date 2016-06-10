/**
 * Copyright (C) 2013 Antonin Stefanutti (antonin.stefanutti@gmail.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.astefanutti.metrics.cdi;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Gauge;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;

import javax.annotation.Priority;
import javax.enterprise.context.Dependent;
import javax.enterprise.inject.Alternative;
import javax.enterprise.inject.Produces;
import javax.enterprise.inject.spi.InjectionPoint;
import javax.interceptor.Interceptor;

@Alternative
@Dependent
@Priority(Interceptor.Priority.LIBRARY_BEFORE)
/* package-private */ final class MetricProducer {

    @Produces
    private static Counter counter(InjectionPoint ip, MetricRegistry registry, MetricName metricName) {
        return registry.counter(metricName.of(ip));
    }

    @Produces
    private static <T> Gauge<T> gauge(final InjectionPoint ip, final MetricRegistry registry, final MetricName metricName) {
        // A forwarding Gauge must be returned as the Gauge creation happens when the declaring bean gets instantiated and the corresponding Gauge can be injected before which leads to producing a null value
        return new Gauge<T>() {
            @Override
            @SuppressWarnings("unchecked")
            public T getValue() {
                // TODO: better error report when the gauge doesn't exist
                return ((Gauge<T>) registry.getGauges().get(metricName.of(ip))).getValue();
            }
        };
    }

    @Produces
    private static Histogram histogram(InjectionPoint ip, MetricRegistry registry, MetricName metricName) {
        return registry.histogram(metricName.of(ip));
    }

    @Produces
    private static Meter meter(InjectionPoint ip, MetricRegistry registry, MetricName metricName) {
        return registry.meter(metricName.of(ip));
    }

    @Produces
    private static Timer timer(InjectionPoint ip, MetricRegistry registry, MetricName metricName) {
        return registry.timer(metricName.of(ip));
    }
}